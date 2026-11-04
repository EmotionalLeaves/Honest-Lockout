package com.honestlockout.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.honestlockout.app.AppLog
import com.honestlockout.app.R
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.service.OverrideForegroundService
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.theme.HugeNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Emergency exit, never refused: confirm, wait break_glass_wait_min (leaving the screen does not
 * cancel it), type the sentence, then everything unlocks for break_glass_unlock_min at the cost of
 * two strikes. The countdown lives in a BreakGlass PendingChange row, so it survives restarts.
 */
@Composable
fun BreakGlassScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by AppGraph.pendingRepo.breakGlass().collectAsState(initial = null)
    val settings by AppGraph.settings.data.collectAsState(initial = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000L) } }
    var typed by rememberSaveable { mutableStateOf("") }
    val sentence = stringResource(R.string.break_glass_sentence)

    AppScaffold(title = stringResource(R.string.settings_break_glass), onBack = { nav.popBackStack() }) { padding ->
        val s = settings ?: return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val p = pending
            when {
                s.breakGlassUntil > now -> {
                    Text(stringResource(R.string.break_glass_active, Format.time(context, s.breakGlassUntil)), style = MaterialTheme.typography.titleLarge)
                }
                p == null -> {
                    Text(
                        stringResource(R.string.break_glass_confirm, s.breakGlassUnlockMin, s.breakGlassWaitMin),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            scope.launch {
                                val t = System.currentTimeMillis()
                                val guard = AppGraph.loosenGuard
                                guard.queue(SettingChange.BreakGlass, guard.describe(SettingChange.BreakGlass), t, t + s.breakGlassWaitMin * 60_000L)
                            }
                        },
                    ) { Text(stringResource(R.string.break_glass_continue)) }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { nav.popBackStack() }) { Text(stringResource(R.string.action_cancel)) }
                }
                p.applyAt > now -> {
                    val left = (p.applyAt - now) / 1000L
                    Text(stringResource(R.string.break_glass_waiting), style = MaterialTheme.typography.titleMedium)
                    Text("%d:%02d".format(left / 60, left % 60), style = HugeNumber, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { AppGraph.pendingRepo.delete(p.id) } }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                else -> {
                    Text(stringResource(R.string.break_glass_type), style = MaterialTheme.typography.titleMedium)
                    Text(sentence, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = typed.trim().equals(sentence.trim(), ignoreCase = true),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val t = System.currentTimeMillis()
                                    AppGraph.ladder.recordBreakGlass(t)
                                    AppGraph.settings.setBreakGlassUntil(t + s.breakGlassUnlockMin * 60_000L)
                                    if (AppGraph.overrideRepo.end(EndReason.BREAK_GLASS, t) != null) {
                                        OverrideForegroundService.send(context, OverrideForegroundService.ACTION_STOP_SILENT)
                                    }
                                    AppGraph.overlay.removeAll()
                                    AppGraph.pendingRepo.delete(p.id)
                                    Toast.makeText(context, context.getString(R.string.toast_unlocked, s.breakGlassUnlockMin), Toast.LENGTH_LONG).show()
                                    nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                                }.onFailure { AppLog.w("HL/Engine", "Break-glass failed", it) }
                            }
                        },
                    ) { Text(stringResource(R.string.break_glass_unlock)) }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { AppGraph.pendingRepo.delete(p.id) } }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}
