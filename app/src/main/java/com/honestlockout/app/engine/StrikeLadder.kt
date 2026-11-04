package com.honestlockout.app.engine

import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.data.db.StrikeReason
import com.honestlockout.app.data.db.entities.TamperEvent
import com.honestlockout.app.data.repo.OverrideRepository
import com.honestlockout.app.data.settings.SettingsStore

/** What a strike caused, for the lockout overlay. */
data class LadderResult(
    val strikeId: Long,
    val activeStrikes: Int,
    val packageName: String?,
    val reason: StrikeReason,
    val appLockedUntil: Long?,
    val overridesReturnAt: Long?,
)

/**
 * Records strikes and applies the ladder. Kept free of UI: the caller (the foreground service or
 * the accessibility service) shows the lockout overlay from the returned [LadderResult].
 */
class StrikeLadder(
    private val settings: SettingsStore,
    private val overrides: OverrideRepository,
) {
    suspend fun record(overrideId: Long?, packageName: String?, reason: StrikeReason, now: Long): LadderResult {
        val strikes = AppGraph.strikeRepo
        // 1. Strike row.
        val strikeId = strikes.insertStrike(overrideId, packageName, reason, now)
        // 2. End the override with STRIKE (needed for the "rest of declared" lock below).
        val active = overrideId?.let { overrides.get(it) }
        if (active != null && active.endedAt == null) overrides.end(EndReason.STRIKE, now)
        // 3–4. Ladder row from the active strike count (including this one).
        val n = strikes.activeCount(now)
        val row = n.coerceIn(1, 4)
        val s = settings.snapshot()
        val lockMin = s.ladderLockMin(row)
        val waitMin = s.ladderWaitMin(row)
        // 5. Lockouts. A strike with no app (tamper) only gets the wait.
        val appUntil: Long? = if (packageName == null) null else when {
            lockMin == -1 -> DayClock.nextMidnight(now)
            lockMin == 0 -> active?.let { it.startedAt + it.plannedMin * 60_000L }?.takeIf { it > now }
            else -> now + lockMin * 60_000L
        }
        if (packageName != null && appUntil != null) strikes.insertLockout(packageName, appUntil, reason.name)
        val waitUntil = if (waitMin > 0) now + waitMin * 60_000L else null
        if (waitUntil != null) strikes.insertLockout(null, waitUntil, reason.name)
        AppLog.w("HL/Engine", "Strike $reason on $packageName: n=$n lock=$lockMin wait=$waitMin")
        // 6. The caller shows the lockout overlay.
        return LadderResult(strikeId, n, packageName, reason, appUntil, waitUntil)
    }

    /** Punishes SERVICE_OFF tamper events not yet punished (called when the service connects). */
    suspend fun recordTamper(now: Long): LadderResult? {
        var last: LadderResult? = null
        for (event: TamperEvent in AppGraph.strikeRepo.unpunishedServiceOff()) {
            val result = record(null, null, StrikeReason.TAMPER, now)
            AppGraph.strikeRepo.markTamperPunished(event, result.strikeId)
            last = result
        }
        return last
    }

    /** Break-glass costs two strikes and no lockout (it unlocks everything). */
    suspend fun recordBreakGlass(now: Long) {
        repeat(2) { AppGraph.strikeRepo.insertStrike(null, null, StrikeReason.BREAK_GLASS, now) }
        settings.setCleanRunStreak(0)
        AppLog.w("HL/Engine", "Break-glass used: 2 strikes")
    }
}
