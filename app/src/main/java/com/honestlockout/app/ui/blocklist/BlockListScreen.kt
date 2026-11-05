package com.honestlockout.app.ui.blocklist

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.honestlockout.app.ui.cardBorder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.data.db.entities.BlockRule
import com.honestlockout.app.data.db.entities.Schedule
import com.honestlockout.app.engine.ScheduleLogic
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.EmptyState
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.Stat
import com.honestlockout.app.ui.UsageBar
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.theme.Primary
import com.honestlockout.app.ui.theme.TextMuted
import com.honestlockout.app.ui.theme.Warning
import kotlinx.coroutines.delay

/** Every BlockRule as a row with a mode chip; budgets show a bar. Tap to edit. */
@Composable
fun BlockListScreen(nav: NavController) {
    val context = LocalContext.current
    val rules by AppGraph.blockRepo.rules().collectAsState(initial = null)
    val schedules by AppGraph.blockRepo.schedules().collectAsState(initial = emptyList())
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000L); now.longValue = System.currentTimeMillis() } }

    AppScaffold(
        title = stringResource(R.string.tab_apps),
        onBack = null,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.APPPICKER) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.blocklist_add), tint = MaterialTheme.colorScheme.primary)
            }
        },
    ) { padding ->
        val list = rules ?: return@AppScaffold
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(stringResource(R.string.blocklist_empty), icon = Icons.Outlined.Block, padding = PaddingValues(bottom = 80.dp))
            }
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                PrimaryButton(stringResource(R.string.blocklist_add)) { nav.navigate(Routes.APPPICKER) }
            }
            return@AppScaffold
        }
        val sorted = list.sortedBy { Format.appLabel(context, it.packageName).lowercase() }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                Text(
                    context.resources.getQuantityString(R.plurals.apps_summary, list.size, list.size),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = cardBorder()) {
                    Column {
                        sorted.forEachIndexed { i, rule ->
                            if (i > 0) RowDivider(inset = 68)
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.navigate(Routes.blockRule(rule.packageName)) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                AppIcon(rule.packageName, 38)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(Format.appLabel(context, rule.packageName), style = MaterialTheme.typography.bodyLarge)
                                    Text(modeSummary(context, rule, schedules, now.longValue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (rule.mode == BlockMode.BUDGET && rule.dailyBudgetMin > 0) {
                                        val used = AppGraph.liveUsage.minutesFor(rule.packageName, now.longValue)
                                        UsageBar(used / rule.dailyBudgetMin.toFloat(), color = if (used >= rule.dailyBudgetMin) Warning else Primary)
                                    }
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeChip(mode: BlockMode) {
    val (label, color) = when (mode) {
        BlockMode.ALWAYS -> stringResource(R.string.mode_always) to Primary
        BlockMode.BUDGET -> stringResource(R.string.mode_budget) to Warning
        BlockMode.SCHEDULE -> stringResource(R.string.mode_schedule) to TextMuted
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = color)
}

/** "Always", "30 min/day, 12 left", "Bedtime 22:00–07:00". */
fun modeSummary(context: Context, rule: BlockRule, schedules: List<Schedule>, now: Long): String = when (rule.mode) {
    BlockMode.ALWAYS -> context.getString(R.string.rule_always_short)
    BlockMode.BUDGET -> {
        val used = AppGraph.liveUsage.minutesFor(rule.packageName, now)
        context.getString(R.string.mode_budget_left, rule.dailyBudgetMin, (rule.dailyBudgetMin - used).coerceAtLeast(0))
    }
    BlockMode.SCHEDULE -> schedules.firstOrNull { it.id == rule.scheduleId }?.let {
        context.getString(R.string.mode_schedule_summary, it.name, ScheduleLogic.hhmm(it.startMinute), ScheduleLogic.hhmm(it.endMinute))
    } ?: context.getString(R.string.mode_schedule)
}
