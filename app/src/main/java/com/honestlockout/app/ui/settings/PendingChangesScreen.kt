package com.honestlockout.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.EmptyState
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.RowDivider
import kotlinx.coroutines.launch

/** Queued loosening changes. Cancel is tightening, so it is instant. */
@Composable
fun PendingChangesScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by AppGraph.pendingRepo.pending().collectAsState(initial = null)

    AppScaffold(title = stringResource(R.string.settings_pending), onBack = { nav.popBackStack() }) { padding ->
        val list = pending ?: return@AppScaffold
        if (list.isEmpty()) { EmptyState(stringResource(R.string.pending_empty), padding, icon = Icons.Outlined.Schedule); return@AppScaffold }
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.pending_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column {
                    list.forEachIndexed { i, c ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(c.description, style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.pending_applies, Format.dateTime(context, c.applyAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { scope.launch { AppGraph.pendingRepo.cancel(c.id) } }) {
                                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
