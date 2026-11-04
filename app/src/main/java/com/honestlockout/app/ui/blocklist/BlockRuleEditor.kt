package com.honestlockout.app.ui.blocklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.engine.GuardResult
import com.honestlockout.app.engine.ScheduleLogic
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.Stepper
import com.honestlockout.app.ui.guardMessage
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.rangeOptions
import kotlinx.coroutines.launch

const val NEW_SCHEDULE_KEY = "new_schedule_id"

/**
 * The spec's stepper is 5–240 min; 1–4 are added so round 2's "BUDGET 2 min" test can be run.
 */
private const val NEW_RULE_WINDOW_MS = 15 * 60_000L

val BUDGET_OPTIONS: List<Int> = listOf(1, 2, 3, 4) + rangeOptions(5, 240, 5)

/**
 * ALWAYS / BUDGET / SCHEDULE for one app. Save and Remove go through LoosenGuard.
 * Choice: the first Save right after adding the app from the picker is part of adding it
 * (blocking a previously free app in any mode is tightening), so it applies at once.
 */
@Composable
fun BlockRuleEditorScreen(nav: NavController, packageName: String, isNew: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rules by AppGraph.blockRepo.rules().collectAsState(initial = null)
    val schedules by AppGraph.blockRepo.schedules().collectAsState(initial = emptyList())
    val rule = rules?.firstOrNull { it.packageName == packageName }

    var mode by rememberSaveable { mutableStateOf<BlockMode?>(null) }
    var budget by rememberSaveable { mutableIntStateOf(30) }
    var scheduleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var firstSaveFree by rememberSaveable { mutableStateOf(isNew) }
    LaunchedEffect(rule) {
        if (rule != null && mode == null) {
            mode = rule.mode
            budget = if (rule.dailyBudgetMin in BUDGET_OPTIONS) rule.dailyBudgetMin else 30
            scheduleId = rule.scheduleId
        }
    }
    // A schedule just created in the schedule editor is selected here.
    val created = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow(NEW_SCHEDULE_KEY, 0L)?.collectAsState()
    LaunchedEffect(created?.value) {
        val id = created?.value ?: 0L
        if (id > 0) { scheduleId = id; mode = BlockMode.SCHEDULE; nav.currentBackStackEntry?.savedStateHandle?.set(NEW_SCHEDULE_KEY, 0L) }
    }

    fun show(result: GuardResult) = scope.launch { snackbar.showSnackbar(guardMessage(context, result)) }

    AppScaffold(title = Format.appLabel(context, packageName), onBack = { nav.popBackStack() }, snackbar = snackbar) { padding ->
        if (rules != null && rule == null) {
            Text(stringResource(R.string.rule_removed), Modifier.padding(padding).padding(24.dp))
            return@AppScaffold
        }
        val m = mode ?: return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(packageName, 48)
                Text(Format.appLabel(context, packageName), style = MaterialTheme.typography.titleLarge)
            }
            val modes = BlockMode.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = m == option,
                        onClick = { mode = option },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) {
                        Text(
                            stringResource(
                                when (option) {
                                    BlockMode.ALWAYS -> R.string.mode_always
                                    BlockMode.BUDGET -> R.string.mode_budget
                                    BlockMode.SCHEDULE -> R.string.mode_schedule
                                }
                            )
                        )
                    }
                }
            }
            when (m) {
                BlockMode.ALWAYS -> Text(stringResource(R.string.rule_always_desc))
                BlockMode.BUDGET -> SectionCard {
                    Stepper(
                        label = stringResource(R.string.rule_budget_label), value = budget,
                        options = BUDGET_OPTIONS, format = { Format.minutes(context, it) },
                        onChange = { budget = it },
                    )
                }
                BlockMode.SCHEDULE -> SectionCard(stringResource(R.string.rule_schedule_label)) {
                    if (schedules.isEmpty()) Text(stringResource(R.string.rule_no_schedules))
                    schedules.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { scheduleId = s.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = scheduleId == s.id, onClick = { scheduleId = s.id })
                            Column(Modifier.weight(1f)) {
                                Text(s.name)
                                Text(
                                    "${ScheduleLogic.hhmm(s.startMinute)}–${ScheduleLogic.hhmm(s.endMinute)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { nav.navigate(Routes.schedule(s.id)) }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit))
                            }
                        }
                    }
                    TextButton(onClick = { nav.navigate(Routes.schedule(null)) }) { Text(stringResource(R.string.rule_new_schedule)) }
                }
            }
            Text(stringResource(R.string.rule_loosen_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = m != BlockMode.SCHEDULE || scheduleId != null,
                onClick = {
                    scope.launch {
                        val change = SettingChange.SetBlockMode(packageName, m, budget, scheduleId)
                        val now = System.currentTimeMillis()
                        val fresh = firstSaveFree && rule != null && now - rule.createdAt < NEW_RULE_WINDOW_MS
                        if (fresh) {
                            AppGraph.loosenGuard.applyChange(change, now)
                            firstSaveFree = false
                            show(GuardResult.Applied)
                        } else {
                            show(AppGraph.loosenGuard.request(change))
                        }
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { scope.launch { show(AppGraph.loosenGuard.request(SettingChange.RemoveBlockRule(packageName))) } },
            ) { Text(stringResource(R.string.rule_remove), color = MaterialTheme.colorScheme.error) }
        }
    }
}
