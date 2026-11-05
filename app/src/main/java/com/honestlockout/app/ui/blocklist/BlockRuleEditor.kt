package com.honestlockout.app.ui.blocklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.honestlockout.app.ui.cardBorder
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.engine.GuardResult
import com.honestlockout.app.engine.ScheduleLogic
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.BottomActionBar
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.GroupHeader
import com.honestlockout.app.ui.InfoNote
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.SegmentedControl
import com.honestlockout.app.ui.SelectRow
import com.honestlockout.app.ui.Stepper
import com.honestlockout.app.ui.TextLink
import com.honestlockout.app.ui.guardMessage
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.rangeOptions
import kotlinx.coroutines.launch

const val NEW_SCHEDULE_KEY = "new_schedule_id"
private const val NEW_RULE_WINDOW_MS = 15 * 60_000L

/** The spec's stepper is 5–240 min; 1–4 are added so round 2's "BUDGET 2 min" test can be run. */
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
    val created = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow(NEW_SCHEDULE_KEY, 0L)?.collectAsState()
    LaunchedEffect(created?.value) {
        val id = created?.value ?: 0L
        if (id > 0) { scheduleId = id; mode = BlockMode.SCHEDULE; nav.currentBackStackEntry?.savedStateHandle?.set(NEW_SCHEDULE_KEY, 0L) }
    }

    fun show(result: GuardResult) = scope.launch { snackbar.showSnackbar(guardMessage(context, result)) }

    val m = mode
    AppScaffold(
        title = Format.appLabel(context, packageName), onBack = { nav.popBackStack() }, snackbar = snackbar,
        bottomBar = {
            if (m != null && rule != null) BottomActionBar {
                val dirty = firstSaveFree || m != rule.mode ||
                    (m == BlockMode.BUDGET && budget != rule.dailyBudgetMin) ||
                    (m == BlockMode.SCHEDULE && scheduleId != rule.scheduleId)
                PrimaryButton(stringResource(R.string.action_save), enabled = dirty && (m != BlockMode.SCHEDULE || scheduleId != null)) {
                    scope.launch {
                        val change = SettingChange.SetBlockMode(packageName, m, budget, scheduleId)
                        val now = System.currentTimeMillis()
                        if (firstSaveFree && now - rule.createdAt < NEW_RULE_WINDOW_MS) {
                            AppGraph.loosenGuard.applyChange(change, now)
                            firstSaveFree = false
                            show(GuardResult.Applied)
                        } else {
                            show(AppGraph.loosenGuard.request(change))
                        }
                    }
                }
                TextLink(stringResource(R.string.rule_remove), color = MaterialTheme.colorScheme.error) {
                    scope.launch { show(AppGraph.loosenGuard.request(SettingChange.RemoveBlockRule(packageName))) }
                }
            }
        },
    ) { padding ->
        if (rules != null && rule == null) {
            Text(stringResource(R.string.rule_removed), Modifier.padding(padding).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppScaffold
        }
        if (m == null) return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(packageName, 56)
                Text(stringResource(R.string.rule_blocked_since, Format.date(context, rule?.createdAt ?: 0L)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                GroupHeader(stringResource(R.string.rule_mode))
                val modes = BlockMode.entries
                SegmentedControl(modes.map { modeName(it) }, modes.indexOf(m)) { mode = modes[it] }
                Text(
                    stringResource(
                        when (m) {
                            BlockMode.ALWAYS -> R.string.rule_always_desc
                            BlockMode.BUDGET -> R.string.rule_budget_desc
                            BlockMode.SCHEDULE -> R.string.rule_schedule_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
            when (m) {
                BlockMode.ALWAYS -> Unit
                BlockMode.BUDGET -> SectionCard(stringResource(R.string.rule_budget_label)) {
                    Stepper(
                        label = stringResource(R.string.rule_budget_per_day), value = budget,
                        options = BUDGET_OPTIONS, format = { Format.minutes(context, it) }, onChange = { budget = it },
                    )
                }
                BlockMode.SCHEDULE -> Column {
                    GroupHeader(stringResource(R.string.rule_schedule_label))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = cardBorder(),
                    ) {
                        Column {
                            schedules.forEach { s ->
                                SelectRow(
                                    title = s.name, selected = scheduleId == s.id,
                                    subtitle = "${ScheduleLogic.hhmm(s.startMinute)} – ${ScheduleLogic.hhmm(s.endMinute)}",
                                    trailing = {
                                        TextLink(stringResource(R.string.cd_edit), Modifier.width(64.dp), color = MaterialTheme.colorScheme.primary) { nav.navigate(Routes.schedule(s.id)) }
                                    },
                                ) { scheduleId = s.id }
                                RowDivider()
                            }
                            TextLink(stringResource(R.string.rule_new_schedule), color = MaterialTheme.colorScheme.primary) { nav.navigate(Routes.schedule(null)) }
                        }
                    }
                }
            }
            InfoNote(stringResource(R.string.rule_loosen_note))
        }
    }
}
