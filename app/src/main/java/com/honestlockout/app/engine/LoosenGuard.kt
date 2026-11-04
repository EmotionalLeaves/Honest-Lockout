package com.honestlockout.app.engine

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.honestlockout.app.AppLog
import com.honestlockout.app.R
import com.honestlockout.app.data.db.AppDatabase
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.data.db.Strictness
import com.honestlockout.app.data.db.entities.PendingChange
import com.honestlockout.app.data.db.entities.Schedule
import com.honestlockout.app.data.repo.PendingChangeRepository
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.data.settings.LoosenDirection
import com.honestlockout.app.data.settings.SettingsStore
import com.honestlockout.app.service.LockDeviceAdminReceiver
import com.honestlockout.app.ui.Format
import com.honestlockout.app.work.PendingChangeWorker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

/*
 * Additions to the spec's SettingChange list (each is needed by another section of the spec):
 * - SetSchedule: editing a schedule a rule uses can loosen it ("fewer days or shorter hours").
 * - AllowDisable / BreakGlass: the {"type":"AllowDisable"} and {"type":"BreakGlass"} pending changes.
 */
@Serializable
sealed class SettingChange {
    @Serializable @SerialName("RemoveBlockRule")
    data class RemoveBlockRule(val packageName: String) : SettingChange()

    @Serializable @SerialName("SetBlockMode")
    data class SetBlockMode(val packageName: String, val mode: BlockMode, val budgetMin: Int, val scheduleId: Long?) : SettingChange()

    @Serializable @SerialName("SetSetting")
    data class SetSetting(val key: String, val value: String) : SettingChange()

    @Serializable @SerialName("SetTemplateMax")
    data class SetTemplateMax(val templateId: Long, val maxMinutes: Int) : SettingChange()

    @Serializable @SerialName("SetTemplateScreens")
    data class SetTemplateScreens(val templateId: Long, val labels: String) : SettingChange()

    @Serializable @SerialName("DisableDeviceAdmin")
    data class DisableDeviceAdmin(@Transient val dummy: Unit = Unit) : SettingChange()

    @Serializable @SerialName("SetSchedule")
    data class SetSchedule(val id: Long, val name: String, val days: String, val startMinute: Int, val endMinute: Int) : SettingChange()

    @Serializable @SerialName("AllowDisable")
    data object AllowDisable : SettingChange()

    @Serializable @SerialName("BreakGlass")
    data object BreakGlass : SettingChange()
}

sealed class GuardResult {
    object Applied : GuardResult()
    data class Queued(val applyAt: Long) : GuardResult()
}

/** Loosening waits loosen_delay_hours (24 h); tightening applies at once. */
class LoosenGuard(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val pending: PendingChangeRepository,
) {
    val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    suspend fun request(change: SettingChange, now: Long = System.currentTimeMillis()): GuardResult {
        if (!isLoosening(change)) {
            applyChange(change, now)
            return GuardResult.Applied
        }
        val applyAt = now + settings.snapshot().loosenDelayHours.coerceAtLeast(0) * 3_600_000L
        queue(change, describe(change), now, applyAt)
        return GuardResult.Queued(applyAt)
    }

    /** Inserts a PendingChange and schedules a one-time worker for its applyAt. */
    suspend fun queue(change: SettingChange, description: String, now: Long, applyAt: Long): Long {
        val id = pending.insert(
            PendingChange(
                description = description, changeJson = json.encodeToString(SettingChange.serializer(), change),
                createdAt = now, applyAt = applyAt,
            )
        )
        PendingChangeWorker.enqueueOnce(context, applyAt - now)
        AppLog.w("HL/Engine", "Queued change $id until $applyAt: $description")
        return id
    }

    suspend fun isLoosening(change: SettingChange): Boolean = when (change) {
        is SettingChange.RemoveBlockRule -> true
        is SettingChange.DisableDeviceAdmin -> true
        is SettingChange.AllowDisable, is SettingChange.BreakGlass -> true
        is SettingChange.SetTemplateMax -> (db.purposeTemplateDao().get(change.templateId)?.maxMinutes ?: Int.MAX_VALUE) < change.maxMinutes
        is SettingChange.SetTemplateScreens -> {
            val old = db.purposeTemplateDao().get(change.templateId)?.let { PurposeTemplates.split(it.blockedScreenLabels).toSet() } ?: emptySet()
            val new = PurposeTemplates.split(change.labels).toSet()
            // Spec: a strict subset is loosening. A swap (drop one, add another) also removes a
            // blocked screen, so any label removed counts as loosening.
            !new.containsAll(old)
        }
        is SettingChange.SetSetting -> settingLoosens(change.key, change.value)
        is SettingChange.SetBlockMode -> blockModeLoosens(change)
        is SettingChange.SetSchedule -> {
            val old = db.scheduleDao().get(change.id)
            old != null && db.blockRuleDao().countUsingSchedule(change.id) > 0 && !ScheduleLogic.covers(change.toSchedule(), old)
        }
    }

    private suspend fun settingLoosens(key: String, value: String): Boolean {
        val s = settings.snapshot()
        return when (key) {
            Keys.STRICTNESS -> {
                val new = runCatching { Strictness.valueOf(value) }.getOrNull() ?: return false
                new.ordinal < s.strictness.ordinal
            }
            Keys.DEVICE_ADMIN_WANTED -> s.deviceAdminWanted && !value.toBoolean()
            else -> {
                val spec = Keys.guardedIntMap[key] ?: return false
                val new = value.toIntOrNull() ?: return false
                val old = s.int(key)
                val isLadderLock = key.startsWith("ladder_lock_min_")
                val (o, n) = if (isLadderLock) Keys.lockStrength(old) to Keys.lockStrength(new) else old to new
                when (spec.loosens) {
                    LoosenDirection.HIGHER -> n > o
                    LoosenDirection.LOWER -> n < o
                    LoosenDirection.NONE -> false
                }
            }
        }
    }

    private suspend fun blockModeLoosens(c: SettingChange.SetBlockMode): Boolean {
        val old = db.blockRuleDao().get(c.packageName) ?: return false
        if (c.mode == BlockMode.ALWAYS) return false
        return when (old.mode) {
            BlockMode.ALWAYS -> true
            BlockMode.BUDGET -> c.mode != BlockMode.BUDGET || c.budgetMin > old.dailyBudgetMin
            BlockMode.SCHEDULE -> {
                if (c.mode != BlockMode.SCHEDULE) return true
                if (c.scheduleId == old.scheduleId) return false
                val oldS = old.scheduleId?.let { db.scheduleDao().get(it) } ?: return false
                val newS = c.scheduleId?.let { db.scheduleDao().get(it) } ?: return true
                !ScheduleLogic.covers(newS, oldS)
            }
        }
    }

    /** Applies a change stored as JSON (called by PendingChangeWorker). */
    suspend fun apply(changeJson: String, now: Long = System.currentTimeMillis()) {
        val change = runCatching { json.decodeFromString(SettingChange.serializer(), changeJson) }
            .onFailure { AppLog.w("HL/Work", "Bad pending change JSON: $changeJson", it) }
            .getOrNull() ?: return
        applyChange(change, now)
    }

    suspend fun applyChange(change: SettingChange, now: Long) {
        when (change) {
            is SettingChange.RemoveBlockRule -> db.blockRuleDao().deleteByPackage(change.packageName)
            is SettingChange.SetBlockMode -> db.blockRuleDao().get(change.packageName)?.let { r ->
                db.blockRuleDao().update(
                    r.copy(
                        mode = change.mode,
                        dailyBudgetMin = if (change.mode == BlockMode.BUDGET) change.budgetMin else r.dailyBudgetMin,
                        scheduleId = if (change.mode == BlockMode.SCHEDULE) change.scheduleId else r.scheduleId,
                    )
                )
            }
            is SettingChange.SetSetting -> settings.writeGuarded(change.key, change.value)
            is SettingChange.SetTemplateMax -> db.purposeTemplateDao().get(change.templateId)?.let {
                db.purposeTemplateDao().update(it.copy(maxMinutes = change.maxMinutes.coerceAtLeast(1)))
            }
            is SettingChange.SetTemplateScreens -> db.purposeTemplateDao().get(change.templateId)?.let {
                db.purposeTemplateDao().update(it.copy(blockedScreenLabels = change.labels))
            }
            is SettingChange.DisableDeviceAdmin -> {
                settings.writeGuarded(Keys.DEVICE_ADMIN_WANTED, "false")
                runCatching {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    dpm.removeActiveAdmin(ComponentName(context, LockDeviceAdminReceiver::class.java))
                }
            }
            is SettingChange.SetSchedule -> db.scheduleDao().get(change.id)?.let { db.scheduleDao().update(change.toSchedule()) }
            is SettingChange.AllowDisable -> settings.setGuardPausedUntil(now + 3_600_000L)
            is SettingChange.BreakGlass -> Unit // completed on the break-glass screen after the sentence
        }
        AppLog.w("HL/Engine", "Applied change: ${change.javaClass.simpleName}")
    }

    private fun SettingChange.SetSchedule.toSchedule() = Schedule(id, name, days, startMinute, endMinute)

    fun describe(change: SettingChange): String = when (change) {
        is SettingChange.RemoveBlockRule -> context.getString(R.string.change_remove_rule, Format.appLabel(context, change.packageName))
        is SettingChange.SetBlockMode -> context.getString(
            R.string.change_set_mode, Format.appLabel(context, change.packageName),
            when (change.mode) {
                BlockMode.ALWAYS -> context.getString(R.string.mode_always)
                BlockMode.BUDGET -> context.getString(R.string.mode_budget_summary, change.budgetMin)
                BlockMode.SCHEDULE -> context.getString(R.string.mode_schedule)
            },
        )
        is SettingChange.SetSetting -> context.getString(R.string.change_set_setting, change.key, change.value)
        is SettingChange.SetTemplateMax -> context.getString(R.string.change_template_max, change.maxMinutes)
        is SettingChange.SetTemplateScreens -> context.getString(R.string.change_template_screens, change.labels.ifEmpty { "—" })
        is SettingChange.DisableDeviceAdmin -> context.getString(R.string.change_disable_admin)
        is SettingChange.SetSchedule -> context.getString(R.string.change_schedule, change.name)
        is SettingChange.AllowDisable -> context.getString(R.string.change_allow_disable)
        is SettingChange.BreakGlass -> context.getString(R.string.change_break_glass)
    }
}
