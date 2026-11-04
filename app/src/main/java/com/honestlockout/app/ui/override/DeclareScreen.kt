package com.honestlockout.app.ui.override

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.db.ActivityShape
import com.honestlockout.app.data.db.entities.PurposeTemplate
import com.honestlockout.app.data.repo.Eligibility
import com.honestlockout.app.data.repo.OverrideDraft
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.service.OverrideForegroundService
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.AppTextField
import com.honestlockout.app.ui.BottomActionBar
import com.honestlockout.app.ui.GroupHeader
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.SelectRow
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.nav.Routes
import kotlinx.coroutines.launch

const val STATEMENT_MIN = 8
const val STATEMENT_MAX = 120

private data class DeclareSetup(val template: PurposeTemplate, val apps: List<String>, val maxMinutes: Int, val fixedApp: String?)

/** Statement (with suggestions), app (sheet with search), minutes (quick chips + slider), Custom extras. */
@Composable
fun DeclareScreen(nav: NavController, templateId: Long, presetPackage: String?, extend: Boolean, presetStatement: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rules by AppGraph.blockRepo.rules().collectAsState(initial = emptyList())

    val setup by produceState<DeclareSetup?>(null, templateId, rules) {
        val t = AppGraph.overrideRepo.template(templateId) ?: return@produceState
        val active = if (extend) AppGraph.overrideRepo.getActive() else null
        val allowed = PurposeTemplates.split(t.allowedPackages).filter { Format.isInstalled(context, it) }
        val apps = if (allowed.isNotEmpty()) allowed else rules.map { it.packageName }
        val max = if (active != null) {
            val chainStart = AppGraph.settings.snapshot().extendChainStartedAt.takeIf { it in 1..active.startedAt } ?: active.startedAt
            val used = ((System.currentTimeMillis() - chainStart) / 60_000L).toInt()
            (t.maxMinutes - used).coerceAtLeast(1)
        } else t.maxMinutes.coerceAtLeast(1)
        value = DeclareSetup(t, apps.sortedBy { Format.appLabel(context, it).lowercase() }, max, active?.packageName)
    }

    var statement by rememberSaveable { mutableStateOf(presetStatement.orEmpty()) }
    var app by rememberSaveable { mutableStateOf(presetPackage) }
    var minutes by rememberSaveable { mutableIntStateOf(-1) }
    var labels by rememberSaveable { mutableStateOf(listOf<String>()) }
    var shape by rememberSaveable { mutableStateOf(ActivityShape.ANY) }
    var showAppSheet by remember { mutableStateOf(false) }
    LaunchedEffect(setup) {
        val s = setup ?: return@LaunchedEffect
        if (minutes < 0 || minutes > s.maxMinutes) minutes = s.maxMinutes
        if (s.fixedApp != null) app = s.fixedApp
        if (app == null && s.apps.size == 1) app = s.apps.first()
        if (s.template.id == PurposeTemplates.ID_CUSTOM && labels.isEmpty()) {
            labels = PurposeTemplates.split(s.template.blockedScreenLabels)
            shape = s.template.expectedShape
        }
    }

    val s = setup
    val isCustom = s?.template?.id == PurposeTemplates.ID_CUSTOM
    AppScaffold(
        title = s?.template?.name ?: "", onBack = { nav.popBackStack() }, snackbar = snackbar,
        bottomBar = {
            if (s != null) BottomActionBar {
                PrimaryButton(stringResource(R.string.declare_start), enabled = statement.trim().length >= STATEMENT_MIN && app != null) {
                    val pkg = app ?: return@PrimaryButton
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val e = AppGraph.overrideRepo.eligibility(pkg, s.template.id, now, extending = extend)
                        if (e is Eligibility.Refused) { snackbar.showSnackbar(e.message); return@launch }
                        AppGraph.overrideRepo.draft = OverrideDraft(
                            packageName = pkg, templateId = s.template.id, statement = statement.trim(),
                            minutes = minutes.coerceIn(1, s.maxMinutes),
                            customLabels = if (isCustom) labels else null, customShape = if (isCustom) shape else null,
                            isExtend = extend,
                        )
                        if (extend) OverrideForegroundService.send(context, OverrideForegroundService.ACTION_DONE_FOR_EXTEND)
                        nav.navigate(Routes.OVERRIDE_COUNTDOWN)
                    }
                }
            }
        },
    ) { padding ->
        if (s == null) return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                GroupHeader(stringResource(R.string.declare_label))
                AppTextField(
                    value = statement,
                    onValueChange = { statement = it.replace('\n', ' ').take(STATEMENT_MAX) },
                    placeholder = stringResource(R.string.declare_placeholder), minLines = 2,
                )
                Text(
                    stringResource(R.string.declare_count, statement.trim().length, STATEMENT_MAX),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 6.dp, end = 4.dp),
                )
            }
            val suggestions = suggestionsFor(s.template.id)
            if (suggestions.isNotEmpty() && statement.isBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    suggestions.forEach { sug ->
                        TextButton(onClick = { statement = sug }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                            Text(sug, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.declare_app)) {
                val chosen = app
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = s.fixedApp == null) { showAppSheet = true }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (chosen != null) {
                        AppIcon(chosen, 40)
                        Text(Format.appLabel(context, chosen), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(stringResource(if (s.apps.isEmpty()) R.string.declare_no_apps else R.string.declare_choose_app), Modifier.weight(1f))
                    }
                    if (s.fixedApp == null && s.apps.isNotEmpty()) Icon(Icons.Filled.Search, contentDescription = null)
                }
            }

            SectionCard(stringResource(R.string.declare_minutes, Format.minutes(context, minutes.coerceAtLeast(1)))) {
                val quick = if (s.maxMinutes > 60) listOf(15, 30, 60, 120, 240) else listOf(2, 5, 10, 15, 30)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.filter { it < s.maxMinutes }.plus(s.maxMinutes).distinct().forEach { m ->
                        FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(Format.minutes(context, m)) })
                    }
                }
                if (s.maxMinutes > 1) {
                    // Continuous track (no tick dots); values snap to whole minutes, or 5 min for long purposes.
                    val snap = if (s.maxMinutes > 60) 5 else 1
                    Slider(
                        value = minutes.coerceIn(1, s.maxMinutes).toFloat(),
                        onValueChange = { v ->
                            val m = if (snap > 1) (Math.round(v / snap) * snap).coerceAtLeast(snap) else Math.round(v)
                            minutes = m.coerceIn(1, s.maxMinutes)
                        },
                        valueRange = 1f..s.maxMinutes.toFloat(),
                    )
                }
                if (s.template.expectedShape == ActivityShape.LISTEN) {
                    Text(
                        stringResource(R.string.declare_listen_note, maxOf(5, minutes / 10)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isCustom) {
                Column {
                    GroupHeader(stringResource(R.string.declare_custom_screens))
                    val all = remember { AppGraph.screenRules.allLabels() }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column {
                            all.forEachIndexed { i, l ->
                                if (i > 0) RowDivider()
                                SelectRow(title = screenLabel(l), selected = l in labels) { labels = if (l in labels) labels - l else labels + l }
                            }
                        }
                    }
                }
                SectionCard(stringResource(R.string.declare_custom_shape)) {
                    // Chips wrap onto a second line instead of squeezing five labels into one row.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityShape.entries.forEach { sh ->
                            FilterChip(selected = shape == sh, onClick = { shape = sh }, label = { Text(shapeName(sh), maxLines = 1) })
                        }
                    }
                    Text(shapeHint(shape), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (showAppSheet) {
            AppSheet(s.apps, onPick = { app = it; showAppSheet = false }, onDismiss = { showAppSheet = false })
        }
    }
}

@Composable
private fun AppSheet(apps: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AppTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            placeholder = stringResource(R.string.apppicker_search),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
            items(apps.filter { query.isBlank() || Format.appLabel(context, it).contains(query, true) }, key = { it }) { p ->
                ListItem(
                    modifier = Modifier.clickable { onPick(p) },
                    leadingContent = { AppIcon(p) },
                    headlineContent = { Text(Format.appLabel(context, p)) },
                )
            }
        }
    }
}

@Composable
private fun suggestionsFor(templateId: Long): List<String> = when (templateId) {
    PurposeTemplates.ID_NAVIGATION -> stringArrayResource(R.array.suggest_navigation)
    PurposeTemplates.ID_REPLY -> stringArrayResource(R.array.suggest_reply)
    PurposeTemplates.ID_LOOKUP -> stringArrayResource(R.array.suggest_lookup)
    PurposeTemplates.ID_WORK -> stringArrayResource(R.array.suggest_work)
    PurposeTemplates.ID_PAY -> stringArrayResource(R.array.suggest_pay)
    PurposeTemplates.ID_LISTEN -> stringArrayResource(R.array.suggest_listen)
    else -> emptyArray()
}.toList()

/** "home_feed" -> "home feed". */
fun screenLabel(label: String) = label.replace('_', ' ')

@Composable
private fun shapeHint(shape: ActivityShape) = stringResource(
    when (shape) {
        ActivityShape.TYPING -> R.string.shape_typing_hint
        ActivityShape.READING -> R.string.shape_reading_hint
        ActivityShape.PASSIVE -> R.string.shape_passive_hint
        ActivityShape.ANY -> R.string.shape_any_hint
        ActivityShape.LISTEN -> R.string.shape_listen_hint
    }
)

@Composable
private fun shapeName(shape: ActivityShape) = stringResource(
    when (shape) {
        ActivityShape.TYPING -> R.string.shape_typing
        ActivityShape.READING -> R.string.shape_reading
        ActivityShape.PASSIVE -> R.string.shape_passive
        ActivityShape.ANY -> R.string.shape_any
        ActivityShape.LISTEN -> R.string.shape_listen
    }
)
