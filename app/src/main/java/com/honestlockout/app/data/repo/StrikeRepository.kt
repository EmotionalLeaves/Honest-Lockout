package com.honestlockout.app.data.repo

import com.honestlockout.app.data.db.AppDatabase
import com.honestlockout.app.data.db.StrikeReason
import com.honestlockout.app.data.db.entities.Lockout
import com.honestlockout.app.data.db.entities.Strike
import com.honestlockout.app.data.db.entities.TamperEvent
import com.honestlockout.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow

sealed class AppealResult {
    object Forgiven : AppealResult()
    object WeeklyLimit : AppealResult()
    object NotAllowed : AppealResult()
}

/** Strikes, lockouts and tamper events. */
class StrikeRepository(private val db: AppDatabase, private val settings: SettingsStore) {
    private val strikes = db.strikeDao()
    private val lockouts = db.lockoutDao()
    private val tamper = db.tamperEventDao()

    fun strikes(): Flow<List<Strike>> = strikes.getAll()
    fun lockouts(): Flow<List<Lockout>> = lockouts.getAll()

    suspend fun activeCount(now: Long): Int = strikes.activeCount(now)
    suspend fun oldestActive(now: Long): Strike? = strikes.oldestActive(now)

    suspend fun insertStrike(overrideId: Long?, packageName: String?, reason: StrikeReason, now: Long): Long {
        val days = settings.snapshot().strikeWindowDays
        return strikes.insert(
            Strike(
                overrideId = overrideId, packageName = packageName, reason = reason,
                createdAt = now, expiresAt = now + days * DAY_MS,
            )
        )
    }

    /** Trust-back: forgives the oldest active strike. Returns true if one was forgiven. */
    suspend fun forgiveOldest(now: Long): Boolean {
        val oldest = strikes.oldestActive(now) ?: return false
        strikes.update(oldest.copy(forgivenAt = now))
        return true
    }

    /**
     * "That was wrong": once per week, on an active strike from the last 24 h. Forgives it and
     * writes an APPEAL_REVERSED row that expires immediately (visible in History, never active).
     */
    suspend fun appeal(strikeId: Long, now: Long): AppealResult {
        val strike = strikes.get(strikeId) ?: return AppealResult.NotAllowed
        if (!canAppeal(strike, now)) return AppealResult.NotAllowed
        val s = settings.snapshot()
        if (s.appealsThisWeek >= 1) return AppealResult.WeeklyLimit
        strikes.update(strike.copy(forgivenAt = now, appealed = true))
        strikes.insert(
            Strike(
                overrideId = strike.overrideId, packageName = strike.packageName,
                reason = StrikeReason.APPEAL_REVERSED, createdAt = now, expiresAt = now,
            )
        )
        settings.setAppealsThisWeek(s.appealsThisWeek + 1)
        return AppealResult.Forgiven
    }

    fun canAppeal(strike: Strike, now: Long): Boolean =
        strike.reason != StrikeReason.APPEAL_REVERSED && strike.forgivenAt == null &&
            strike.expiresAt > now && now - strike.createdAt < DAY_MS

    suspend fun insertLockout(packageName: String?, until: Long, reason: String) {
        lockouts.insert(Lockout(packageName = packageName, until = until, reason = reason))
    }

    suspend fun activeGlobalLockout(now: Long): Lockout? = lockouts.activeGlobal(now)
    suspend fun activeLockoutFor(packageName: String, now: Long): Lockout? = lockouts.activeFor(packageName, now)
    suspend fun anyLockoutActive(now: Long): Boolean = lockouts.activeCount(now) > 0
    suspend fun deleteExpiredLockouts(now: Long): Int = lockouts.deleteExpired(now)

    suspend fun recordTamperEvent(kind: String, now: Long, strikeId: Long? = null): Long =
        tamper.insert(TamperEvent(detectedAt = now, kind = kind, strikeId = strikeId))

    suspend fun unpunishedServiceOff(): List<TamperEvent> = tamper.unpunishedServiceOff()
    suspend fun markTamperPunished(event: TamperEvent, strikeId: Long) = tamper.update(event.copy(strikeId = strikeId))
    suspend fun rebootSince(sinceMs: Long): Boolean = tamper.rebootSince(sinceMs) != null

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
