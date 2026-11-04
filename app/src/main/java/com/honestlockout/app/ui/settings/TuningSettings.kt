package com.honestlockout.app.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.db.ActivityShape
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.data.db.Strictness
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.data.settings.Settings
import com.honestlockout.app.ui.Stepper
import com.honestlockout.app.ui.rangeOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** Strictness presets set grace, check-in gaps and shape thresholds together (round 8). */
object StrictnessPresets {
    fun values(level: Strictness): Map<String, Int> = when (level) {
        Strictness.LENIENT -> mapOf(
            Keys.WARNING_GRACE_SEC to 45, Keys.CHECKIN_MIN_GAP_MIN to 5, Keys.CHECKIN_MAX_GAP_MIN to 10,
            Keys.TYPING_SCROLL_MAX to 25, Keys.READING_SCROLL_MAX to 60, Keys.PASSIVE_SCROLL_MAX to 45, Keys.PASSIVE_CLICK_MAX to 30,
        )
        Strictness.NORMAL -> mapOf(
            Keys.WARNING_GRACE_SEC to 30, Keys.CHECKIN_MIN_GAP_MIN to 3, Keys.CHECKIN_MAX_GAP_MIN to 8,
            Keys.TYPING_SCROLL_MAX to 15, Keys.READING_SCROLL_MAX to 40, Keys.PASSIVE_SCROLL_MAX to 30, Keys.PASSIVE_CLICK_MAX to 20,
        )
        Strictness.STRICT -> mapOf(
            Keys.WARNING_GRACE_SEC to 20, Keys.CHECKIN_MIN_GAP_MIN to 2, Keys.CHECKIN_MAX_GAP_MIN to 5,
            Keys.TYPING_SCROLL_MAX to 10, Keys.READING_SCROLL_MAX to 30, Keys.PASSIVE_SCROLL_MAX to 20, Keys.PASSIVE_CLICK_MAX to 15,
        )
    }
}

private val THRESHOLD_KEYS = listOf(
    R.string.threshold_typing to Keys.TYPING_SCROLL_MAX,
    R.string.threshold_reading to Keys.READING_SCROLL_MAX,
    R.string.threshold_passive_scroll to Keys.PASSIVE_SCROLL_MAX,
    R.string.threshold_passive_click to Keys.PASSIVE_CLICK_MAX,
)

/**
 * 90th percentile of the last 14 days of clean overrides' per-minute samples, per threshold.
 * Null when there is no data for that purpose shape.
 */
suspend fun suggestThresholds(now: Long): Map<String, Int?> = withContext(Dispatchers.IO) {
    val since = now - 14L * 24 * 60 * 60 * 1000
    val db = AppGraph.db
    val clean = db.overrideDao().startedSince(since).filter {
        it.endReason == EndReason.DONE || it.endReason == EndReason.CONFESSED || it.endReason == EndReason.TIMEOUT
    }
    val shapes = clean.associate { o -> o.id to (db.purposeTemplateDao().get(o.purposeId)?.expectedShape ?: ActivityShape.ANY) }
    val samples = if (clean.isEmpty()) emptyList() else db.activitySampleDao().forOverrides(clean.map { it.id })
    fun p90(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[(ceil(0.9 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)] + 1
    }
    val typing = samples.filter { shapes[it.overrideId] == ActivityShape.TYPING && it.typingEvents == 0 }.map { it.scrollEvents }
    val reading = samples.filter { shapes[it.overrideId] == ActivityShape.READING }.map { it.scrollEvents }
    val passive = samples.filter { shapes[it.overrideId] == ActivityShape.PASSIVE }
    mapOf(
        Keys.TYPING_SCROLL_MAX to p90(typing),
        Keys.READING_SCROLL_MAX to p90(reading),
        Keys.PASSIVE_SCROLL_MAX to p90(passive.map { it.scrollEvents }),
        Keys.PASSIVE_CLICK_MAX to p90(passive.map { it.clickEvents }),
    )
}

/** Activity thresholds (per minute) plus Suggest thresholds; shown inside the Advanced group. */
@Composable
fun ColumnScope.ThresholdSettings(s: Settings, onChange: (key: String, value: Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<Map<String, Int?>?>(null) }
    Text(stringResource(R.string.settings_thresholds), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.settings_thresholds_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    THRESHOLD_KEYS.forEach { (label, key) ->
        Stepper(
            stringResource(label), s.int(key), rangeOptions(1, 200), { "$it" }, { onChange(key, it) },
            hint = guardedHint(context, s, key, stringResource(R.string.threshold_hint)),
        )
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = { scope.launch { suggestions = suggestThresholds(System.currentTimeMillis()) } },
    ) { Text(stringResource(R.string.settings_suggest)) }
    suggestions?.let { map ->
        Text(stringResource(R.string.suggest_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        THRESHOLD_KEYS.forEach { (label, key) ->
            val v = map[key]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (v == null) stringResource(R.string.suggest_none, stringResource(label))
                    else stringResource(R.string.suggest_value, stringResource(label), v, s.int(key)),
                    Modifier.weight(1f),
                )
                if (v != null && v != s.int(key)) TextButton(onClick = { onChange(key, v) }) { Text(stringResource(R.string.action_apply)) }
            }
        }
    }
}
