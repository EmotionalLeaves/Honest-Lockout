package com.honestlockout.app.data.repo

import android.content.Context
import com.honestlockout.app.AppLog
import com.honestlockout.app.R
import com.honestlockout.app.data.db.ActivityShape
import com.honestlockout.app.data.db.AppDatabase
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.data.db.entities.ActivitySample
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.data.db.entities.PurposeTemplate
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.data.settings.SettingsStore
import com.honestlockout.app.ui.Format
import kotlinx.coroutines.flow.Flow

/** What the Declare screen hands to the Countdown screen. */
data class OverrideDraft(
    val packageName: String,
    val templateId: Long,
    val statement: String,
    val minutes: Int,
    val customLabels: List<String>? = null,
    val customShape: ActivityShape? = null,
    val isExtend: Boolean = false,
)

sealed class Eligibility {
    object Ok : Eligibility()
    data class Refused(val message: String) : Eligibility()
}

data class EndOutcome(val override: Override, val forgaveStrike: Boolean)

class OverrideRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val strikes: StrikeRepository,
) {
    private val dao = db.overrideDao()
    private val templates = db.purposeTemplateDao()

    /** Held in memory between Declare and Countdown. */
    @Volatile var draft: OverrideDraft? = null

    fun active(): Flow<Override?> = dao.getActiveFlow()
    fun all(): Flow<List<Override>> = dao.getAll()
    fun templates(): Flow<List<PurposeTemplate>> = templates.getAll()

    suspend fun getActive(): Override? = dao.getActive()
    suspend fun get(id: Long): Override? = dao.get(id)
    suspend fun template(id: Long): PurposeTemplate? = templates.get(id)
    suspend fun updateTemplate(t: PurposeTemplate) = templates.update(t)

    /**
     * Eligibility check before an override (spec, Starting an override, step 1). [packageName] and
     * [templateId] may be null when only the global conditions should be checked.
     */
    suspend fun eligibility(packageName: String?, templateId: Long?, now: Long, extending: Boolean = false): Eligibility {
        endIfExpired(now)
        strikes.activeGlobalLockout(now)?.let {
            return Eligibility.Refused(context.getString(R.string.refuse_global_lockout, Format.time(context, it.until)))
        }
        if (packageName != null) {
            strikes.activeLockoutFor(packageName, now)?.let {
                return Eligibility.Refused(context.getString(R.string.refuse_app_lockout, Format.time(context, it.until)))
            }
        }
        if (templateId != null && templateId !in PurposeTemplates.allowedAtFourStrikes && strikes.activeCount(now) >= 4) {
            return Eligibility.Refused(context.getString(R.string.refuse_four_strikes))
        }
        if (!extending && dao.getActive() != null) {
            return Eligibility.Refused(context.getString(R.string.refuse_override_active))
        }
        return Eligibility.Ok
    }

    /**
     * An active row past its planned end whose service never ran (e.g. the process died) is
     * ended as TIMEOUT so it cannot grant access or block new overrides forever.
     */
    suspend fun endIfExpired(now: Long): Boolean {
        val o = dao.getActive() ?: return false
        if (now < o.startedAt + o.plannedMin * 60_000L) return false
        return end(EndReason.TIMEOUT, now) != null
    }

    /** Extend is refused on the third Extend in a row. */
    suspend fun canExtend(): Boolean = settings.snapshot().extendChain < 2

    /** Inserts the Override row. Custom purpose picks are written to the Custom template row. */
    suspend fun start(d: OverrideDraft, now: Long): Override {
        if (d.templateId == PurposeTemplates.ID_CUSTOM && (d.customLabels != null || d.customShape != null)) {
            templates.get(d.templateId)?.let { t ->
                templates.update(
                    t.copy(
                        blockedScreenLabels = d.customLabels?.joinToString(",") ?: t.blockedScreenLabels,
                        expectedShape = d.customShape ?: t.expectedShape,
                    )
                )
            }
        }
        val chain = if (d.isExtend) settings.snapshot().extendChain + 1 else 0
        settings.setExtendChain(chain)
        if (!d.isExtend) settings.setExtendChainStartedAt(now)
        val id = dao.insert(
            Override(
                packageName = d.packageName, purposeId = d.templateId, statement = d.statement.trim(),
                startedAt = now, plannedMin = d.minutes,
            )
        )
        AppLog.w("HL/Engine", "Override $id started: ${d.packageName} for ${d.minutes} min")
        return dao.get(id)!!
    }

    /**
     * Ends the active override. DONE, CONFESSED and TIMEOUT are clean runs: they grow the
     * clean-run streak and may forgive the oldest strike. STRIKE resets the streak.
     * An override replaced by Extend ends DONE but does not count ([countsAsCleanRun] = false),
     * so Extend cannot be used to farm forgiveness.
     */
    suspend fun end(reason: EndReason, now: Long, countsAsCleanRun: Boolean = true): EndOutcome? {
        val active = dao.getActive() ?: return null
        // Atomic: when two paths race (service, boot, break-glass) only the first one ends it.
        if (dao.endIfActive(active.id, now, reason) != 1) return null
        val ended = active.copy(endedAt = now, endReason = reason)
        AppLog.w("HL/Engine", "Override ${active.id} ended: $reason")
        var forgave = false
        val s = settings.snapshot()
        when (reason) {
            EndReason.DONE, EndReason.CONFESSED, EndReason.TIMEOUT -> if (countsAsCleanRun) {
                val streak = s.cleanRunStreak + 1
                if (streak >= s.cleanRunsToForgive && strikes.forgiveOldest(now)) {
                    forgave = true
                    settings.setCleanRunStreak(0)
                } else {
                    settings.setCleanRunStreak(streak)
                }
            }
            EndReason.STRIKE -> settings.setCleanRunStreak(0)
            else -> Unit
        }
        return EndOutcome(ended, forgave)
    }

    suspend fun updateCounters(id: Long, warnings: Int, asked: Int, answered: Int) {
        val o = dao.get(id) ?: return
        if (o.warningCount != warnings || o.checkinsAsked != asked || o.checkinsAnswered != answered) {
            dao.update(o.copy(warningCount = warnings, checkinsAsked = asked, checkinsAnswered = answered))
        }
    }

    suspend fun insertSample(sample: ActivitySample) = db.activitySampleDao().insert(sample)

    /** Override milliseconds per package started since [sinceMs] (to keep them out of budgets). */
    suspend fun overrideMsSince(sinceMs: Long, now: Long): Map<String, Long> =
        dao.startedSince(sinceMs).groupBy { it.packageName }.mapValues { (_, list) ->
            list.sumOf { ((it.endedAt ?: now) - it.startedAt).coerceAtLeast(0) }
        }
}
