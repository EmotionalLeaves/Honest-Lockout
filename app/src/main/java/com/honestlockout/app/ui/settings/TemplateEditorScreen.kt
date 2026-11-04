package com.honestlockout.app.ui.settings

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.Stepper
import com.honestlockout.app.ui.guardMessage
import com.honestlockout.app.ui.rangeOptions
import kotlinx.coroutines.launch

/** 1–60 by the minute, then coarser steps up to 12 h (Listen purposes run overnight). */
private val TEMPLATE_MAX_OPTIONS = rangeOptions(1, 60) + rangeOptions(75, 180, 15) + rangeOptions(240, 720, 60)

/** Max minutes and blocked screens of one purpose. Built-ins cannot be deleted. */
@Composable
fun TemplateEditorScreen(nav: NavController, id: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val templates by AppGraph.overrideRepo.templates().collectAsState(initial = null)
    val t = templates?.firstOrNull { it.id == id }
    var max by rememberSaveable { mutableIntStateOf(-1) }
    var labels by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(t) {
        if (t != null && max < 0) { max = t.maxMinutes; labels = PurposeTemplates.split(t.blockedScreenLabels) }
    }
    val allLabels = remember { AppGraph.screenRules.allLabels() }

    AppScaffold(title = t?.name.orEmpty(), onBack = { nav.popBackStack() }, snackbar = snackbar) { padding ->
        val template = t ?: return@AppScaffold
        val chosen = labels ?: return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(PurposeTemplates.descriptionRes(template.id)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionCard {
                Stepper(stringResource(R.string.template_max), max, TEMPLATE_MAX_OPTIONS, { Format.minutes(context, it) }, { max = it })
            }
            SectionCard(stringResource(R.string.template_screens)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (allLabels + chosen).distinct().sorted().forEach { l ->
                        FilterChip(
                            selected = l in chosen,
                            onClick = { labels = if (l in chosen) chosen - l else chosen + l },
                            label = { Text(l) },
                        )
                    }
                }
            }
            Text(stringResource(R.string.rule_loosen_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        val guard = AppGraph.loosenGuard
                        val results = buildList {
                            if (max != template.maxMinutes) add(guard.request(SettingChange.SetTemplateMax(template.id, max)))
                            val csv = chosen.sorted().joinToString(",")
                            if (csv != PurposeTemplates.split(template.blockedScreenLabels).sorted().joinToString(",")) {
                                add(guard.request(SettingChange.SetTemplateScreens(template.id, csv)))
                            }
                        }
                        val msg = results.lastOrNull()?.let { r ->
                            results.firstOrNull { it is com.honestlockout.app.engine.GuardResult.Queued } ?: r
                        }?.let { guardMessage(context, it) } ?: context.getString(R.string.template_no_change)
                        snackbar.showSnackbar(msg)
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
