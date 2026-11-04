package com.honestlockout.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.SecondaryButton
import kotlinx.coroutines.delay

/**
 * Screen inspector: for 60 s the accessibility service logs the viewIdResourceName and text of
 * the front window's nodes, here and to logcat (tag HL/Inspect). Copy an ID into screen_rules.json.
 */
@Composable
fun InspectorScreen(nav: NavController) {
    val inspector = AppGraph.inspector
    val lines by inspector.lines.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000L) } }
    val left = ((inspector.activeUntil - now) / 1000L).coerceAtLeast(0)

    AppScaffold(title = stringResource(R.string.rules_inspect), onBack = { nav.popBackStack() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.inspector_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!AppGraph.accessibilityConnected) Text(stringResource(R.string.inspector_needs_service), color = MaterialTheme.colorScheme.error)
            if (left > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Text(stringResource(R.string.inspector_running, left), style = MaterialTheme.typography.labelLarge)
                }
                SecondaryButton(stringResource(R.string.inspector_stop)) { inspector.stop() }
            } else {
                PrimaryButton(stringResource(R.string.inspector_start)) { inspector.start() }
            }
            Text(stringResource(R.string.inspector_count, lines.size).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                        items(lines) { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
