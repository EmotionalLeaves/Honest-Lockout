package com.honestlockout.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.honestlockout.app.R
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.data.settings.LoosenDirection
import com.honestlockout.app.data.settings.Settings
import com.honestlockout.app.ui.ExpandableSection
import com.honestlockout.app.ui.Format
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

private fun waitLabel(context: Context, v: Int): String =
    if (v == 0) context.getString(R.string.ladder_no_wait) else Format.minutes(context, v)

/** "[base] Raising it waits 24 h." — tells the user which direction is the slow one. */
fun guardedHint(context: Context, s: Settings, key: String, base: String): String {
    val dir = Keys.guardedIntMap[key]?.loosens ?: return base
    val suffix = when (dir) {
        LoosenDirection.HIGHER -> context.getString(R.string.hint_raise_waits, s.loosenDelayHours)
        LoosenDirection.LOWER -> context.getString(R.string.hint_lower_waits, s.loosenDelayHours)
        LoosenDirection.NONE -> return base
    }
    return "$base $suffix"
}

private val ROW_LABELS = listOf(R.string.ladder_row_1, R.string.ladder_row_2, R.string.ladder_row_3, R.string.ladder_row_4)

/** "What a strike costs": one readable sentence per step, with the two numbers editable below it. */
@Composable
fun LadderSettings(s: Settings, onChange: (key: String, value: Int) -> Unit) {
    val context = LocalContext.current
    val summary = stringResource(
        R.string.ladder_summary,
        ladderLockLabel(context, s.int(Keys.ladderLock(1))), waitLabel(context, s.int(Keys.ladderWait(1))),
    )
    ExpandableSection(stringResource(R.string.settings_ladder), summary) {
        Text(stringResource(R.string.settings_ladder_desc, s.strikeWindowDays), color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (n in 1..4) {
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(ROW_LABELS[n - 1]), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        R.string.ladder_sentence,
                        ladderLockLabel(context, s.int(Keys.ladderLock(n))), waitLabel(context, s.int(Keys.ladderWait(n))),
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Stepper(
                    label = stringResource(R.string.ladder_app_lock), value = s.int(Keys.ladderLock(n)),
                    options = LADDER_LOCK_OPTIONS, format = { ladderLockLabel(context, it) },
                    onChange = { onChange(Keys.ladderLock(n), it) },
                )
                Stepper(
                    label = stringResource(R.string.ladder_wait), value = s.int(Keys.ladderWait(n)),
                    options = LADDER_WAIT_OPTIONS, format = { waitLabel(context, it) },
                    onChange = { onChange(Keys.ladderWait(n), it) },
                )
            }
        }
        Text(stringResource(R.string.ladder_footer, s.loosenDelayHours), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Timers, grouped by what they affect, each with a one-line explanation. */
@Composable
fun TimingSettings(s: Settings, onChange: (key: String, value: Int) -> Unit) {
    val context = LocalContext.current
    val sec: (Int) -> String = { context.getString(R.string.fmt_seconds, it) }
    val min: (Int) -> String = { Format.minutes(context, it) }
    ExpandableSection(stringResource(R.string.settings_overrides_section), stringResource(R.string.settings_overrides_section_desc)) {
        IntStepper(R.string.timing_countdown, R.string.timing_countdown_hint, s, Keys.OVERRIDE_COUNTDOWN_SEC, rangeOptions(5, 120, 5), sec, onChange)
        IntStepper(R.string.timing_grace, R.string.timing_grace_hint, s, Keys.WARNING_GRACE_SEC, rangeOptions(10, 120, 5), sec, onChange)
        IntStepper(R.string.timing_max_warnings, R.string.timing_max_warnings_hint, s, Keys.MAX_WARNINGS, rangeOptions(1, 6), { "$it" }, onChange)
        IntStepper(R.string.timing_checkin_min, R.string.timing_checkin_gap_hint, s, Keys.CHECKIN_MIN_GAP_MIN, rangeOptions(1, 30), min, onChange)
        IntStepper(R.string.timing_checkin_max, R.string.timing_checkin_gap_hint, s, Keys.CHECKIN_MAX_GAP_MIN, rangeOptions(1, 60), min, onChange)
        IntStepper(R.string.timing_checkin_answer, R.string.timing_checkin_answer_hint, s, Keys.CHECKIN_ANSWER_SEC, rangeOptions(10, 120, 5), sec, onChange)
    }
    ExpandableSection(stringResource(R.string.settings_record_section), stringResource(R.string.settings_record_section_desc)) {
        IntStepper(R.string.timing_strike_window, R.string.timing_strike_window_hint, s, Keys.STRIKE_WINDOW_DAYS, rangeOptions(1, 30), { context.getString(R.string.fmt_days, it) }, onChange)
        IntStepper(R.string.timing_clean_runs, R.string.timing_clean_runs_hint, s, Keys.CLEAN_RUNS_TO_FORGIVE, rangeOptions(1, 10), { "$it" }, onChange)
        IntStepper(R.string.timing_loosen_delay, R.string.timing_loosen_delay_hint, s, Keys.LOOSEN_DELAY_HOURS, rangeOptions(1, 72), { context.getString(R.string.fmt_hours, it) }, onChange)
        IntStepper(R.string.timing_break_glass_wait, R.string.timing_break_glass_wait_hint, s, Keys.BREAK_GLASS_WAIT_MIN, rangeOptions(1, 60), min, onChange)
        IntStepper(R.string.timing_break_glass_unlock, R.string.timing_break_glass_unlock_hint, s, Keys.BREAK_GLASS_UNLOCK_MIN, rangeOptions(5, 180, 5), min, onChange)
    }
}

@Composable
private fun ColumnScope.IntStepper(
    label: Int, hint: Int, s: Settings, key: String, options: List<Int>, format: (Int) -> String, onChange: (String, Int) -> Unit,
) {
    val context = LocalContext.current
    Stepper(
        label = stringResource(label), value = s.int(key), options = options, format = format,
        onChange = { onChange(key, it) }, hint = guardedHint(context, s, key, stringResource(hint)),
    )
}
