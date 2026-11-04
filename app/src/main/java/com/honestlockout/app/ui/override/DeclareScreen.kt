package com.honestlockout.app.ui.override

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.nav.Routes
import kotlinx.coroutines.launch

const val STATEMENT_MIN = 8
const val STATEMENT_MAX = 120

private data class DeclareSetup(val template: PurposeTemplate, val apps: List<String>, val maxMinutes: Int, val fixedApp: String?)

/** Statement, app, minutes (and Custom extras). Start is disabled until the statement has 8+ characters. */
@Composable
fun DeclareScreen(nav: NavController, templateId: Long, presetPackage: String?, extend: Boolean) {
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
            val used = ((System.currentTimeMillis() - active.startedAt) / 60_000L).toInt()
            (t.maxMinutes - used).coerceAtLeast(1)
        } else t.maxMinutes.coerceAtLeast(1)
        value = DeclareSetup(t, apps, max, active?.packageName)
    }

    var statement by rememberSaveable { mutableStateOf("") }
    var app by rememberSaveable { mutableStateOf(presetPackage) }
    var minutes by rememberSaveable { mutableIntStateOf(-1) }
    var labels by rememberSaveable { mutableStateOf(listOf<String>()) }
    var shape by rememberSaveable { mutableStateOf(ActivityShape.ANY) }
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
    AppScaffold(title = s?.template?.name ?: "", onBack = { nav.popBackStack() }, snackbar = snackbar) { padding ->
        if (s == null) return@AppScaffold
        val isCustom = s.template.id == PurposeTemplates.ID_CUSTOM
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = statement,
                onValueChange = { statement = it.replace('\n', ' ').take(STATEMENT_MAX) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.declare_placeholder)) },
                supportingText = { Text(stringResource(R.string.declare_count, statement.trim().length, STATEMENT_MAX)) },
                minLines = 2,
            )
            SectionCard(stringResource(R.string.declare_app)) {
                if (s.fixedApp != null) {
                    FilterChip(selected = true, onClick = {}, label = { Text(Format.appLabel(context, s.fixedApp)) })
                } else {
                    if (s.apps.isEmpty() && app == null) Text(stringResource(R.string.declare_no_apps))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val choices = (listOfNotNull(app) + s.apps).distinct()
                        choices.forEach { p ->
                            FilterChip(selected = app == p, onClick = { app = p }, label = { Text(Format.appLabel(context, p)) })
                        }
                    }
                }
            }
            SectionCard(stringResource(R.string.declare_minutes, minutes.coerceAtLeast(1))) {
                if (s.maxMinutes > 1) {
                    Slider(
                        value = minutes.coerceIn(1, s.maxMinutes).toFloat(),
                        onValueChange = { minutes = it.toInt().coerceIn(1, s.maxMinutes) },
                        valueRange = 1f..s.maxMinutes.toFloat(),
                        steps = (s.maxMinutes - 2).coerceAtLeast(0),
                    )
                }
                Text(stringResource(R.string.declare_max, s.maxMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isCustom) {
                SectionCard(stringResource(R.string.declare_custom_screens)) {
                    val all = remember { AppGraph.screenRules.allLabels() }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        all.forEach { l ->
                            FilterChip(
                                selected = l in labels,
                                onClick = { labels = if (l in labels) labels - l else labels + l },
                                label = { Text(l) },
                            )
                        }
                    }
                }
                SectionCard(stringResource(R.string.declare_custom_shape)) {
                    val shapes = ActivityShape.entries
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        shapes.forEachIndexed { i, sh ->
                            SegmentedButton(
                                selected = shape == sh, onClick = { shape = sh },
                                shape = SegmentedButtonDefaults.itemShape(i, shapes.size),
                            ) { Text(shapeName(sh)) }
                        }
                    }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = statement.trim().length >= STATEMENT_MIN && app != null,
                onClick = {
                    val pkg = app ?: return@Button
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
                },
            ) { Text(stringResource(R.string.declare_start)) }
        }
    }
}

@Composable
private fun shapeName(shape: ActivityShape) = stringResource(
    when (shape) {
        ActivityShape.TYPING -> R.string.shape_typing
        ActivityShape.READING -> R.string.shape_reading
        ActivityShape.PASSIVE -> R.string.shape_passive
        ActivityShape.ANY -> R.string.shape_any
    }
)
