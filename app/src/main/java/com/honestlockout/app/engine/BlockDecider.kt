package com.honestlockout.app.engine

import com.honestlockout.app.data.db.AppDatabase
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.data.settings.SettingsStore

sealed class Decision {
    object Allow : Decision()
    data class AllowedByOverride(val overrideId: Long) : Decision()
    data class Lock(val reason: LockReason, val until: Long?) : Decision()
}

/** BREAK_GLASS_WAIT is used when a lock applies while an emergency unlock countdown is running. */
enum class LockReason { ALWAYS, BUDGET_SPENT, SCHEDULE, LOCKOUT, BREAK_GLASS_WAIT }

/**
 * Decides whether [packageName] may be in front right now. A pure function over the database,
 * settings and the in-memory live usage counter. Evaluated in the spec's order; first hit wins.
 */
class BlockDecider(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val live: LiveUsageTracker,
) {
    suspend fun decide(packageName: String, now: Long): Decision {
        // 1. Emergency apps are never blocked.
        if (EmergencyAllowlist.contains(packageName)) return Decision.Allow
        // 2. Not on the block list.
        val rule = db.blockRuleDao().get(packageName) ?: return Decision.Allow
        // 3. Break-glass unlock.
        val s = settings.snapshot()
        if (s.breakGlassUntil > now) return Decision.Allow
        // 4. Active override for this app, or for a package its purpose allows (never past its end).
        db.overrideDao().getActive()?.takeIf { now < it.startedAt + it.plannedMin * 60_000L }?.let { o ->
            if (o.packageName == packageName) return Decision.AllowedByOverride(o.id)
            val template = db.purposeTemplateDao().get(o.purposeId)
            if (template != null && packageName in PurposeTemplates.split(template.allowedPackages)) {
                return Decision.AllowedByOverride(o.id)
            }
        }
        // 5. Lockout for this app, or global.
        val lockout = db.lockoutDao().activeFor(packageName, now) ?: db.lockoutDao().activeGlobal(now)
        if (lockout != null) return Decision.Lock(LockReason.LOCKOUT, lockout.until)
        // 6. The rule's mode.
        val lock: Decision.Lock? = when (rule.mode) {
            BlockMode.ALWAYS -> Decision.Lock(LockReason.ALWAYS, null)
            BlockMode.BUDGET -> {
                val used = db.usageDayDao().minutesFor(DayClock.dateKey(now), packageName) +
                    live.minutesFor(packageName, now)
                if (used >= rule.dailyBudgetMin) Decision.Lock(LockReason.BUDGET_SPENT, DayClock.nextMidnight(now)) else null
            }
            BlockMode.SCHEDULE -> {
                val schedule = rule.scheduleId?.let { db.scheduleDao().get(it) }
                if (schedule != null && ScheduleLogic.isActive(schedule, now)) {
                    Decision.Lock(LockReason.SCHEDULE, ScheduleLogic.endOfActiveWindow(schedule, now))
                } else null
            }
        }
        if (lock == null) return Decision.Allow
        val breakGlass = db.pendingChangeDao().pendingOnce().firstOrNull { it.changeJson.contains("\"BreakGlass\"") }
        if (breakGlass != null && breakGlass.applyAt > now) {
            return Decision.Lock(LockReason.BREAK_GLASS_WAIT, breakGlass.applyAt)
        }
        return lock
    }
}
