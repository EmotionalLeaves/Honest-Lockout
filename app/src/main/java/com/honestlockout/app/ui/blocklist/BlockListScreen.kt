package com.honestlockout.app.ui.blocklist

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.honestlockout.app.ui.nav.Routes
import kotlinx.coroutines.delay

/** Every BlockRule as a row. Tap to edit; swipe does nothing (removal lives in the editor). */
@Composable
fun BlockListScreen(nav: NavController) {
    val context = LocalContext.current
    val rules by AppGraph.blockRepo.rules().collectAsState(initial = null)
    val schedules by AppGraph.blockRepo.schedules().collectAsState(initial = emptyList())
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000L); now.longValue = System.currentTimeMillis() } }

    AppScaffold(
        title = stringResource(R.string.blocklist_title),
        onBack = { nav.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.APPPICKER) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.blocklist_add)) },
            )
        },
    ) { padding ->
        val list = rules ?: return@AppScaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.blocklist_empty), padding)
            return@AppScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(list, key = { it.packageName }) { rule ->
                ListItem(
                    modifier = Modifier.clickable { nav.navigate(Routes.blockRule(rule.packageName)) },
                    leadingContent = { AppIcon(rule.packageName) },
                    headlineContent = { Text(Format.appLabel(context, rule.packageName)) },
                    supportingContent = { Text(modeSummary(context, rule, schedules, now.longValue)) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** "Always", "30 min/day, 12 left", "Bedtime 22:00–07:00". */
fun modeSummary(context: Context, rule: BlockRule, schedules: List<Schedule>, now: Long): String = when (rule.mode) {
    BlockMode.ALWAYS -> context.getString(R.string.mode_always)
    BlockMode.BUDGET -> {
        val used = AppGraph.liveUsage.minutesFor(rule.packageName, now)
        context.getString(R.string.mode_budget_left, rule.dailyBudgetMin, (rule.dailyBudgetMin - used).coerceAtLeast(0))
    }
    BlockMode.SCHEDULE -> schedules.firstOrNull { it.id == rule.scheduleId }?.let {
        context.getString(R.string.mode_schedule_summary, it.name, ScheduleLogic.hhmm(it.startMinute), ScheduleLogic.hhmm(it.endMinute))
    } ?: context.getString(R.string.mode_schedule)
}

