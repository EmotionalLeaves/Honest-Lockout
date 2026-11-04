package com.honestlockout.app.ui.override

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.repo.Eligibility
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.nav.Routes

private data class PickerGate(val refusal: String?, val activeStrikes: Int)

/** Enabled purpose templates as cards (Custom last). A refusal banner shows when not eligible. */
@Composable
fun PurposePickerScreen(nav: NavController, packageName: String?) {
    val context = LocalContext.current
    val templates by AppGraph.overrideRepo.templates().collectAsState(initial = emptyList())
    val gate by produceState(PickerGate(null, 0), packageName) {
        val now = System.currentTimeMillis()
        val e = AppGraph.overrideRepo.eligibility(packageName, null, now)
        value = PickerGate((e as? Eligibility.Refused)?.message, AppGraph.strikeRepo.activeCount(now))
    }
    val shown = templates.filter { it.enabled }.sortedBy { if (it.id == PurposeTemplates.ID_CUSTOM) Long.MAX_VALUE else it.id }

    AppScaffold(title = stringResource(R.string.pick_title), onBack = { nav.popBackStack() }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (packageName != null) {
                item { Text(stringResource(R.string.pick_for_app, Format.appLabel(context, packageName)), style = MaterialTheme.typography.titleMedium) }
            }
            gate.refusal?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(msg, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (gate.refusal == null && gate.activeStrikes >= 4) {
                item { Text(stringResource(R.string.refuse_four_strikes), color = MaterialTheme.colorScheme.tertiary) }
            }
            items(shown, key = { it.id }) { t ->
                val allowed = gate.refusal == null && (gate.activeStrikes < 4 || t.id in PurposeTemplates.allowedAtFourStrikes)
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(if (allowed) 1f else 0.45f)
                        .clickable(enabled = allowed) { nav.navigate(Routes.declare(t.id, packageName)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.pick_default_minutes, t.maxMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(PurposeTemplates.descriptionRes(t.id)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
