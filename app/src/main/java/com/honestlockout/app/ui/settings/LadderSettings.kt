package com.honestlockout.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.honestlockout.app.R
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.data.settings.Settings
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.Stepper
import com.honestlockout.app.ui.rangeOptions

/** Ladder lock values ordered by strength (Keys.lockStrength): 0 = rest of override, -1 = rest of day. */
val LADDER_LOCK_OPTIONS = listOf(15, 30, 0, 60, 120, 240, 480, 720, -1, 1440, 2880)
val LADDER_WAIT_OPTIONS = listOf(0, 5, 10, 15, 30, 60, 120, 240, 480, 1440)

fun ladderLockLabel(context: Context, v: Int): String = when (v) {
    0 -> context.getString(R.string.ladder_rest_of_declared)
    -1 -> context.getString(R.string.ladder_rest_of_day)
    else -> Format.minutes(context, v)
}

/** The 8 ladder steppers: app lock and wait for 1, 2, 3 and 4+ active strikes. */
@Composable
fun LadderSettings(s: Settings, onChange: (key: String, value: Int) -> Unit) {
    val context = LocalContext.current
    SectionCard(stringResource(R.string.settings_ladder)) {
        Text(stringResource(R.string.settings_ladder_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (n in 1..4) {
            val label = if (n == 4) stringResource(R.string.ladder_row_4) else stringResource(R.string.ladder_row_n, n)
            Text(label, style = MaterialTheme.typography.titleSmall)
            Stepper(
                label = stringResource(R.string.ladder_app_lock), value = s.int(Keys.ladderLock(n)),
                options = LADDER_LOCK_OPTIONS, format = { ladderLockLabel(context, it) },
                onChange = { onChange(Keys.ladderLock(n), it) },
            )
            Stepper(
                label = stringResource(R.string.ladder_wait), value = s.int(Keys.ladderWait(n)),
                options = LADDER_WAIT_OPTIONS, format = { Format.minutes(context, it) },
                onChange = { onChange(Keys.ladderWait(n), it) },
            )
        }
    }
}

/** Grace, countdown, check-in gaps and the other timing rules. */
@Composable
fun TimingSettings(s: Settings, onChange: (key: String, value: Int) -> Unit) {
    val context = LocalContext.current
    val sec: (Int) -> String = { context.getString(R.string.fmt_seconds, it) }
    val min: (Int) -> String = { Format.minutes(context, it) }
    SectionCard(stringResource(R.string.settings_timing)) {
        IntStepper(R.string.timing_grace, s, Keys.WARNING_GRACE_SEC, rangeOptions(10, 120, 5), sec, onChange)
        IntStepper(R.string.timing_countdown, s, Keys.OVERRIDE_COUNTDOWN_SEC, rangeOptions(5, 120, 5), sec, onChange)
        IntStepper(R.string.timing_checkin_min, s, Keys.CHECKIN_MIN_GAP_MIN, rangeOptions(1, 30), min, onChange)
        IntStepper(R.string.timing_checkin_max, s, Keys.CHECKIN_MAX_GAP_MIN, rangeOptions(1, 60), min, onChange)
        IntStepper(R.string.timing_checkin_answer, s, Keys.CHECKIN_ANSWER_SEC, rangeOptions(10, 120, 5), sec, onChange)
        IntStepper(R.string.timing_max_warnings, s, Keys.MAX_WARNINGS, rangeOptions(1, 6), { "$it" }, onChange)
        IntStepper(R.string.timing_strike_window, s, Keys.STRIKE_WINDOW_DAYS, rangeOptions(1, 30), { context.getString(R.string.fmt_days, it) }, onChange)
        IntStepper(R.string.timing_clean_runs, s, Keys.CLEAN_RUNS_TO_FORGIVE, rangeOptions(1, 10), { "$it" }, onChange)
        IntStepper(R.string.timing_loosen_delay, s, Keys.LOOSEN_DELAY_HOURS, rangeOptions(1, 72), { context.getString(R.string.fmt_hours, it) }, onChange)
        IntStepper(R.string.timing_break_glass_wait, s, Keys.BREAK_GLASS_WAIT_MIN, rangeOptions(1, 60), min, onChange)
        IntStepper(R.string.timing_break_glass_unlock, s, Keys.BREAK_GLASS_UNLOCK_MIN, rangeOptions(5, 180, 5), min, onChange)
    }
}

@Composable
private fun ColumnScope.IntStepper(
    label: Int, s: Settings, key: String, options: List<Int>, format: (Int) -> String, onChange: (String, Int) -> Unit,
) {
    Stepper(label = stringResource(label), value = s.int(key), options = options, format = format, onChange = { onChange(key, it) })
}
