package com.honestlockout.app.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.ui.cardBorder
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.data.db.StrikeReason
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.data.db.entities.Strike
import com.honestlockout.app.data.repo.AppealResult
import com.honestlockout.app.overlay.OverlayTexts
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.EmptyState
import com.honestlockout.app.ui.SegmentedControl
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.theme.Danger
import com.honestlockout.app.ui.theme.Primary
import com.honestlockout.app.ui.theme.TextMuted
import com.honestlockout.app.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class HistoryItem(val at: Long) {
    class OverrideItem(val o: Override, val strike: Strike?) : HistoryItem(o.startedAt)
    class StrikeItem(val s: Strike) : HistoryItem(s.createdAt)
}

/** Tabs Overrides and Screen time. Long-press the title to export the debug log. */
@Composable
fun HistoryScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(AppGraph.log.dump().toByteArray()) } }.isSuccess
            }
            snackbar.showSnackbar(context.getString(if (ok) R.string.history_export_done else R.string.history_export_failed))
        }
    }

    AppScaffold(
        title = stringResource(R.string.history_title),
        onBack = { nav.popBackStack() },
        snackbar = snackbar,
        titleModifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { exportLauncher.launch("honest-lockout-log.txt") })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedControl(
                listOf(stringResource(R.string.history_tab_overrides), stringResource(R.string.history_tab_screen_time)), tab,
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) { tab = it }
            if (tab == 0) OverridesTab(snackbar) else ScreenTimeTab()
        }
    }
}

@Composable
private fun OverridesTab(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overrides by AppGraph.overrideRepo.all().collectAsState(initial = null)
    val strikes by AppGraph.strikeRepo.strikes().collectAsState(initial = emptyList())
    val templates by AppGraph.overrideRepo.templates().collectAsState(initial = emptyList())
    var strikesOnly by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    val list = overrides ?: return
    val items = remember(list, strikes, strikesOnly) {
        val real = strikes.filter { it.reason != StrikeReason.APPEAL_REVERSED }
        val byOverride = real.filter { it.overrideId != null }.associateBy { it.overrideId }
        val overrideItems = list.map { HistoryItem.OverrideItem(it, byOverride[it.id]) }
            .filter { !strikesOnly || it.strike != null || it.o.endReason == EndReason.STRIKE }
        val standalone = strikes.filter { it.overrideId == null || it.reason == StrikeReason.APPEAL_REVERSED }.map { HistoryItem.StrikeItem(it) }
        (overrideItems + standalone).sortedByDescending { it.at }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.history_filter_label).uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1f))
        TextButton(onClick = { strikesOnly = !strikesOnly }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringResource(if (strikesOnly) R.string.history_filter_strikes else R.string.history_filter_all), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (items.isEmpty()) { EmptyState(stringResource(R.string.history_empty), icon = Icons.Outlined.History); return }

    fun appeal(s: Strike) = scope.launch {
        val msg = when (AppGraph.strikeRepo.appeal(s.id, System.currentTimeMillis())) {
            AppealResult.Forgiven -> R.string.appeal_done
            AppealResult.WeeklyLimit -> R.string.appeal_weekly_limit
            AppealResult.NotAllowed -> R.string.appeal_not_allowed
        }
        snackbar.showSnackbar(context.getString(msg))
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { if (it is HistoryItem.OverrideItem) "o${it.o.id}" else "s${(it as HistoryItem.StrikeItem).s.id}" }) { item ->
            when (item) {
                is HistoryItem.OverrideItem -> OverrideRow(item.o, item.strike, templates.firstOrNull { it.id == item.o.purposeId }?.name.orEmpty(), now) { appeal(it) }
                is HistoryItem.StrikeItem -> StrikeRow(item.s, now) { appeal(it) }
            }
        }
    }
}

@Composable
private fun OverrideRow(o: Override, strike: Strike?, purpose: String, now: Long, onAppeal: (Strike) -> Unit) {
    val context = LocalContext.current
    val forgiven = strike?.forgivenAt != null
    val actual = (((o.endedAt ?: now) - o.startedAt) / 60_000L).toInt()
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (forgiven) 0.5f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Format.dateTime(context, o.startedAt)} · ${Format.appLabel(context, o.packageName)}",
                    style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.weight(1f),
                )
                ResultChip(o.endReason, forgiven)
            }
            Text(o.statement, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_planned_actual, purpose, Format.minutes(context, o.plannedMin), Format.minutes(context, actual)), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (strike != null) {
                Text(OverlayTexts.reasonName(context, strike.reason), style = MaterialTheme.typography.bodySmall, color = Danger)
                StrikeFooter(strike, now, onAppeal)
            }
        }
    }
}

@Composable
private fun StrikeRow(s: Strike, now: Long, onAppeal: (Strike) -> Unit) {
    val context = LocalContext.current
    val greyed = s.forgivenAt != null || s.reason == StrikeReason.APPEAL_REVERSED
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (greyed) 0.5f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Format.dateTime(context, s.createdAt), style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Text(
                if (s.reason == StrikeReason.APPEAL_REVERSED) stringResource(R.string.history_appeal_row)
                else stringResource(R.string.history_strike_row, OverlayTexts.reasonName(context, s.reason)),
                style = MaterialTheme.typography.titleMedium,
            )
            if (s.reason != StrikeReason.APPEAL_REVERSED) StrikeFooter(s, now, onAppeal)
        }
    }
}

@Composable
private fun StrikeFooter(s: Strike, now: Long, onAppeal: (Strike) -> Unit) {
    when {
        s.appealed -> Text(stringResource(R.string.history_appealed), color = TextMuted)
        s.forgivenAt != null -> Text(stringResource(R.string.history_forgiven), color = TextMuted)
        AppGraph.strikeRepo.canAppeal(s, now) -> TextButton(onClick = { onAppeal(s) }, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(R.string.history_that_was_wrong), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ResultChip(reason: EndReason?, forgiven: Boolean) {
    val (label, color) = when (reason) {
        EndReason.DONE -> stringResource(R.string.result_clean) to Primary
        EndReason.CONFESSED -> stringResource(R.string.result_confessed) to Primary
        EndReason.TIMEOUT -> stringResource(R.string.result_timed_out) to Primary
        EndReason.STRIKE -> stringResource(if (forgiven) R.string.result_strike_forgiven else R.string.result_strike) to Danger
        EndReason.TAMPER -> stringResource(R.string.result_tamper) to Warning
        EndReason.BREAK_GLASS -> stringResource(R.string.result_break_glass) to Warning
        null -> stringResource(R.string.result_running) to MaterialTheme.colorScheme.onSurface
    }
    Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
}
