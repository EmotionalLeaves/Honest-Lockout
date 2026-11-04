package com.honestlockout.app.service

import android.content.Context
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.data.db.entities.TamperEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Tamper detection for the accessibility service (spec, Tamper detection table):
 * - Turned off by the user (onUnbind / onDestroy) while rules exist and the guard is not paused:
 *   the process may die right after, so only a synchronous SharedPreferences flag is written then.
 *   On the next connect the flag becomes TamperEvent(SERVICE_OFF), which StrikeLadder.recordTamper
 *   turns into a TAMPER strike the user sees.
 * - Killed by the OS (no onUnbind): on connect, a last_service_seen gap over 10 min with no reboot
 *   in between is logged as SERVICE_KILLED, never punished.
 */
class TamperWatch(private val context: Context, private val scope: CoroutineScope) {

    @Volatile private var armed = false
    @Volatile private var guardPausedUntil = 0L

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Keeps the arming conditions current, so nothing touches the database at unbind time. */
    fun watchConditions() {
        scope.launch {
            combine(AppGraph.settings.data, AppGraph.blockRepo.rules()) { s, rules -> s to rules.isNotEmpty() }
                .collect { (s, hasRules) ->
                    armed = s.setupComplete && hasRules
                    guardPausedUntil = s.guardPausedUntil
                }
        }
    }

    /** Main thread, synchronous, no database. */
    fun onServiceOff(now: Long) {
        if (!armed || now < guardPausedUntil) return
        runCatching { prefs().edit().putLong(KEY_OFF_AT, now).commit() }
        AppLog.w(TAG, "Service turned off while rules exist: tamper flagged")
    }

    suspend fun onConnected(now: Long) {
        val prefs = prefs()
        val offAt = prefs.getLong(KEY_OFF_AT, 0L)
        if (offAt > 0L) {
            AppGraph.strikeRepo.recordTamperEvent(TamperEvent.SERVICE_OFF, offAt)
            prefs.edit().remove(KEY_OFF_AT).apply()
        }
        val s = AppGraph.settings.snapshot()
        val strikes = AppGraph.strikeRepo
        if (offAt <= 0L && s.setupComplete && s.lastServiceSeen > 0 && now - s.lastServiceSeen > KILLED_GAP_MS &&
            !strikes.rebootSince(s.lastServiceSeen)
        ) {
            strikes.recordTamperEvent(TamperEvent.SERVICE_KILLED, now)
            AppLog.w(TAG, "Service was killed while away; logged without a strike")
        }
        AppGraph.settings.setLastServiceSeen(now)
    }

    companion object {
        private const val TAG = "HL/Acc"
        private const val PREFS = "tamper_flag"
        private const val KEY_OFF_AT = "service_off_at"
        private const val KILLED_GAP_MS = 10 * 60_000L
    }
}
