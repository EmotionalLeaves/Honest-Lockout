package com.honestlockout.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.honestlockout.app.data.db.Strictness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Which direction of change makes blocking weaker. */
enum class LoosenDirection { HIGHER, LOWER, NONE }

data class IntSetting(val key: String, val default: Int, val loosens: LoosenDirection)

/**
 * Every DataStore key and default from the spec (DataStore settings table), plus the internal
 * bookkeeping keys other sections of the spec name (last_service_seen, clean_run_streak,
 * break_glass_until, guard_paused_until) and the round 8 shape thresholds.
 *
 * Rule: keys with a loosening direction ("guarded" keys) may only be written through
 * [writeGuarded], which only LoosenGuard and PendingChangeWorker call.
 */
object Keys {
    const val SETUP_COMPLETE = "setup_complete"
    const val STRICTNESS = "strictness"
    const val WARNING_GRACE_SEC = "warning_grace_sec"
    const val MAX_WARNINGS = "max_warnings_per_override"
    const val STRIKE_WINDOW_DAYS = "strike_window_days"
    const val CLEAN_RUNS_TO_FORGIVE = "clean_runs_to_forgive"
    fun ladderLock(n: Int) = "ladder_lock_min_$n"
    fun ladderWait(n: Int) = "ladder_wait_min_$n"
    const val LOOSEN_DELAY_HOURS = "loosen_delay_hours"
    const val OVERRIDE_COUNTDOWN_SEC = "override_countdown_sec"
    const val CHECKIN_MIN_GAP_MIN = "checkin_min_gap_min"
    const val CHECKIN_MAX_GAP_MIN = "checkin_max_gap_min"
    const val CHECKIN_ANSWER_SEC = "checkin_answer_sec"
    const val BREAK_GLASS_WAIT_MIN = "break_glass_wait_min"
    const val BREAK_GLASS_UNLOCK_MIN = "break_glass_unlock_min"
    const val DEVICE_ADMIN_WANTED = "device_admin_wanted"
    const val APPEALS_THIS_WEEK = "appeals_this_week"
    const val LAST_DAILY_RESET = "last_daily_reset"
    // Round 8: shape thresholds moved from OverrideMonitor constants into settings.
    const val TYPING_SCROLL_MAX = "typing_scroll_max"
    const val READING_SCROLL_MAX = "reading_scroll_max"
    const val PASSIVE_SCROLL_MAX = "passive_scroll_max"
    const val PASSIVE_CLICK_MAX = "passive_click_max"
    // Internal bookkeeping (not user settings, never loosening).
    const val LAST_SERVICE_SEEN = "last_service_seen"
    const val CLEAN_RUN_STREAK = "clean_run_streak"
    const val BREAK_GLASS_UNTIL = "break_glass_until"
    const val GUARD_PAUSED_UNTIL = "guard_paused_until"
    const val LAST_ALLOW_DISABLE_REQUEST = "last_allow_disable_request"
    const val EXTEND_CHAIN = "extend_chain"

    val guardedInts: List<IntSetting> = listOf(
        IntSetting(WARNING_GRACE_SEC, 30, LoosenDirection.HIGHER),
        IntSetting(MAX_WARNINGS, 3, LoosenDirection.HIGHER),
        IntSetting(STRIKE_WINDOW_DAYS, 7, LoosenDirection.LOWER),
        IntSetting(CLEAN_RUNS_TO_FORGIVE, 3, LoosenDirection.LOWER),
        IntSetting(ladderLock(1), 0, LoosenDirection.LOWER),
        IntSetting(ladderLock(2), 120, LoosenDirection.LOWER),
        IntSetting(ladderLock(3), -1, LoosenDirection.LOWER),
        IntSetting(ladderLock(4), 1440, LoosenDirection.LOWER),
        IntSetting(ladderWait(1), 5, LoosenDirection.LOWER),
        IntSetting(ladderWait(2), 15, LoosenDirection.LOWER),
        IntSetting(ladderWait(3), 60, LoosenDirection.LOWER),
        IntSetting(ladderWait(4), 240, LoosenDirection.LOWER),
        IntSetting(LOOSEN_DELAY_HOURS, 24, LoosenDirection.LOWER),
        IntSetting(OVERRIDE_COUNTDOWN_SEC, 20, LoosenDirection.LOWER),
        IntSetting(CHECKIN_MIN_GAP_MIN, 3, LoosenDirection.HIGHER),
        IntSetting(CHECKIN_MAX_GAP_MIN, 8, LoosenDirection.HIGHER),
        IntSetting(CHECKIN_ANSWER_SEC, 30, LoosenDirection.HIGHER),
        IntSetting(BREAK_GLASS_WAIT_MIN, 10, LoosenDirection.LOWER),
        IntSetting(BREAK_GLASS_UNLOCK_MIN, 60, LoosenDirection.HIGHER),
        IntSetting(TYPING_SCROLL_MAX, 15, LoosenDirection.HIGHER),
        IntSetting(READING_SCROLL_MAX, 40, LoosenDirection.HIGHER),
        IntSetting(PASSIVE_SCROLL_MAX, 30, LoosenDirection.HIGHER),
        IntSetting(PASSIVE_CLICK_MAX, 20, LoosenDirection.HIGHER),
    )
    val guardedIntMap: Map<String, IntSetting> = guardedInts.associateBy { it.key }

    /** Keys that only LoosenGuard / PendingChangeWorker may write. */
    val guardedKeys: Set<String> = guardedIntMap.keys + STRICTNESS + DEVICE_ADMIN_WANTED

    /**
     * Ladder lock values are not monotonic: -1 = rest of the local day, 0 = rest of the declared
     * override. For comparisons they are ranked by strength: 0 is the weakest, -1 counts as 720 min.
     */
    fun lockStrength(value: Int): Int = when (value) {
        0 -> 0
        -1 -> 720
        else -> value
    }
}

/** Immutable snapshot of every setting. */
data class Settings(
    val setupComplete: Boolean = false,
    val strictness: Strictness = Strictness.NORMAL,
    val ints: Map<String, Int> = emptyMap(),
    val deviceAdminWanted: Boolean = false,
    val appealsThisWeek: Int = 0,
    val lastDailyReset: String = "",
    val lastServiceSeen: Long = 0,
    val cleanRunStreak: Int = 0,
    val breakGlassUntil: Long = 0,
    val guardPausedUntil: Long = 0,
    val lastAllowDisableRequest: Long = 0,
    val extendChain: Int = 0,
) {
    fun int(key: String): Int = ints[key] ?: Keys.guardedIntMap[key]?.default ?: 0
    val warningGraceSec get() = int(Keys.WARNING_GRACE_SEC)
    val maxWarnings get() = int(Keys.MAX_WARNINGS)
    val strikeWindowDays get() = int(Keys.STRIKE_WINDOW_DAYS)
    val cleanRunsToForgive get() = int(Keys.CLEAN_RUNS_TO_FORGIVE)
    val loosenDelayHours get() = int(Keys.LOOSEN_DELAY_HOURS)
    val countdownSec get() = int(Keys.OVERRIDE_COUNTDOWN_SEC)
    val checkinMinGapMin get() = int(Keys.CHECKIN_MIN_GAP_MIN)
    val checkinMaxGapMin get() = int(Keys.CHECKIN_MAX_GAP_MIN)
    val checkinAnswerSec get() = int(Keys.CHECKIN_ANSWER_SEC)
    val breakGlassWaitMin get() = int(Keys.BREAK_GLASS_WAIT_MIN)
    val breakGlassUnlockMin get() = int(Keys.BREAK_GLASS_UNLOCK_MIN)
    fun ladderLockMin(row: Int) = int(Keys.ladderLock(row.coerceIn(1, 4)))
    fun ladderWaitMin(row: Int) = int(Keys.ladderWait(row.coerceIn(1, 4)))
}

class SettingsStore(private val context: Context) {

    val data: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun snapshot(): Settings = data.first()

    private fun Preferences.toSettings(): Settings {
        val ints = Keys.guardedInts.associate { s -> s.key to (this[intPreferencesKey(s.key)] ?: s.default) }
        return Settings(
            setupComplete = this[booleanPreferencesKey(Keys.SETUP_COMPLETE)] ?: false,
            strictness = runCatching { Strictness.valueOf(this[stringPreferencesKey(Keys.STRICTNESS)] ?: "NORMAL") }
                .getOrDefault(Strictness.NORMAL),
            ints = ints,
            deviceAdminWanted = this[booleanPreferencesKey(Keys.DEVICE_ADMIN_WANTED)] ?: false,
            appealsThisWeek = this[intPreferencesKey(Keys.APPEALS_THIS_WEEK)] ?: 0,
            lastDailyReset = this[stringPreferencesKey(Keys.LAST_DAILY_RESET)] ?: "",
            lastServiceSeen = this[longPreferencesKey(Keys.LAST_SERVICE_SEEN)] ?: 0L,
            cleanRunStreak = this[intPreferencesKey(Keys.CLEAN_RUN_STREAK)] ?: 0,
            breakGlassUntil = this[longPreferencesKey(Keys.BREAK_GLASS_UNTIL)] ?: 0L,
            guardPausedUntil = this[longPreferencesKey(Keys.GUARD_PAUSED_UNTIL)] ?: 0L,
            lastAllowDisableRequest = this[longPreferencesKey(Keys.LAST_ALLOW_DISABLE_REQUEST)] ?: 0L,
            extendChain = this[intPreferencesKey(Keys.EXTEND_CHAIN)] ?: 0,
        )
    }

    /**
     * Writes a guarded (loosening-capable) key. ONLY LoosenGuard and PendingChangeWorker may call this.
     * [value] is the string form: an Int, a Strictness name, or "true"/"false".
     */
    suspend fun writeGuarded(key: String, value: String) {
        context.dataStore.edit { p ->
            when {
                key == Keys.STRICTNESS -> p[stringPreferencesKey(key)] =
                    runCatching { Strictness.valueOf(value).name }.getOrDefault(Strictness.NORMAL.name)
                key == Keys.DEVICE_ADMIN_WANTED -> p[booleanPreferencesKey(key)] = value.toBoolean()
                Keys.guardedIntMap.containsKey(key) -> value.toIntOrNull()?.let { p[intPreferencesKey(key)] = it }
            }
        }
    }

    // ---- Non-loosening keys: written directly. ----

    suspend fun setSetupComplete(done: Boolean) = putBool(Keys.SETUP_COMPLETE, done)
    suspend fun setAppealsThisWeek(n: Int) = putInt(Keys.APPEALS_THIS_WEEK, n)
    suspend fun setLastDailyReset(date: String) = context.dataStore.edit { it[stringPreferencesKey(Keys.LAST_DAILY_RESET)] = date }
    suspend fun setLastServiceSeen(ms: Long) = putLong(Keys.LAST_SERVICE_SEEN, ms)
    suspend fun setCleanRunStreak(n: Int) = putInt(Keys.CLEAN_RUN_STREAK, n)
    suspend fun setBreakGlassUntil(ms: Long) = putLong(Keys.BREAK_GLASS_UNTIL, ms)
    suspend fun setGuardPausedUntil(ms: Long) = putLong(Keys.GUARD_PAUSED_UNTIL, ms)
    suspend fun setLastAllowDisableRequest(ms: Long) = putLong(Keys.LAST_ALLOW_DISABLE_REQUEST, ms)
    suspend fun setExtendChain(n: Int) = putInt(Keys.EXTEND_CHAIN, n)

    private suspend fun putInt(k: String, v: Int) { context.dataStore.edit { it[intPreferencesKey(k)] = v } }
    private suspend fun putLong(k: String, v: Long) { context.dataStore.edit { it[longPreferencesKey(k)] = v } }
    private suspend fun putBool(k: String, v: Boolean) { context.dataStore.edit { it[booleanPreferencesKey(k)] = v } }
}
