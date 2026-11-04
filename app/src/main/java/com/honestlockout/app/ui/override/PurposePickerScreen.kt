package com.honestlockout.app.ui.override

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.data.repo.Eligibility
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.nav.Routes

private data class PickerGate(val refusal: String?, val activeStrikes: Int, val last: Override? = null)

fun purposeIcon(id: Long): ImageVector = when (id) {
    PurposeTemplates.ID_NAVIGATION -> Icons.Filled.Navigation
    PurposeTemplates.ID_REPLY -> Icons.Filled.ChatBubble
    PurposeTemplates.ID_LOOKUP -> Icons.Filled.Search
    PurposeTemplates.ID_WORK -> Icons.Filled.Work
    PurposeTemplates.ID_PAY -> Icons.Filled.AccountBalance
    PurposeTemplates.ID_LISTEN -> Icons.Filled.Headphones
    else -> Icons.Filled.Tune
}

/** Enabled purpose templates as cards (Custom last). A refusal banner shows when not eligible. */
@Composable
fun PurposePickerScreen(nav: NavController, packageName: String?) {
    val context = LocalContext.current
    val templates by AppGraph.overrideRepo.templates().collectAsState(initial = emptyList())
    val gate by produceState(PickerGate(null, 0), packageName) {
        val now = System.currentTimeMillis()
        val e = AppGraph.overrideRepo.eligibility(packageName, null, now)
        val last = AppGraph.overrideRepo.lastClean(packageName)
        value = PickerGate((e as? Eligibility.Refused)?.message, AppGraph.strikeRepo.activeCount(now), last)
    }
    val shown = templates.filter { it.enabled }.sortedBy { if (it.id == PurposeTemplates.ID_CUSTOM) Long.MAX_VALUE else it.id }

    AppScaffold(title = stringResource(R.string.pick_title), onBack = { nav.popBackStack() }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (packageName != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        AppIcon(packageName, 36)
                        Text(stringResource(R.string.pick_for_app, Format.appLabel(context, packageName)), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            gate.refusal?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(msg, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            val last = gate.last
            val lastTemplate = last?.let { l -> templates.firstOrNull { it.id == l.purposeId && it.enabled } }
            if (gate.refusal == null && last != null && lastTemplate != null &&
                (gate.activeStrikes < 4 || lastTemplate.id in PurposeTemplates.allowedAtFourStrikes)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.declare(lastTemplate.id, packageName ?: last.packageName, statement = last.statement)) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.pick_last_time, lastTemplate.name, last.plannedMin), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(last.statement, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
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
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(purposeIcon(t.id), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(PurposeTemplates.descriptionRes(t.id)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.pick_default_minutes, t.maxMinutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
