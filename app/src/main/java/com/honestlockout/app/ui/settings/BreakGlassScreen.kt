package com.honestlockout.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.R
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.service.OverrideForegroundService
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.AppTextField
import com.honestlockout.app.ui.BottomActionBar
import com.honestlockout.app.ui.DangerButton
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.ProgressRing
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.TextLink
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.theme.HugeNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { ACTIVE, CONFIRM, WAITING, SENTENCE }

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

    val s = settings
    val p = pending
    val phase = when {
        s == null -> null
        s.breakGlassUntil > now -> Phase.ACTIVE
        p == null -> Phase.CONFIRM
        p.applyAt > now -> Phase.WAITING
        else -> Phase.SENTENCE
    }

    fun unlock() {
        val st = s ?: return
        val pc = p ?: return
        scope.launch {
            runCatching {
                val t = System.currentTimeMillis()
                AppGraph.monitor.stop()
                AppGraph.ladder.recordBreakGlass(t)
                AppGraph.settings.setBreakGlassUntil(t + st.breakGlassUnlockMin * 60_000L)
                if (AppGraph.overrideRepo.end(EndReason.BREAK_GLASS, t) != null) {
                    OverrideForegroundService.send(context, OverrideForegroundService.ACTION_STOP_SILENT)
                }
                AppGraph.overlay.removeAll()
                AppGraph.pendingRepo.delete(pc.id)
                Toast.makeText(context, context.getString(R.string.toast_unlocked, st.breakGlassUnlockMin), Toast.LENGTH_LONG).show()
                nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
            }.onFailure { AppLog.w("HL/Engine", "Break-glass failed", it) }
        }
    }

    AppScaffold(
        title = stringResource(R.string.settings_break_glass), onBack = { nav.popBackStack() },
        bottomBar = {
            val st = s
            if (st != null) when (phase) {
                Phase.CONFIRM -> BottomActionBar {
                    DangerButton(stringResource(R.string.break_glass_continue)) {
                        scope.launch {
                            val t = System.currentTimeMillis()
                            val guard = AppGraph.loosenGuard
                            guard.queue(SettingChange.BreakGlass, guard.describe(SettingChange.BreakGlass), t, t + st.breakGlassWaitMin * 60_000L)
                        }
                    }
                    TextLink(stringResource(R.string.action_cancel)) { nav.popBackStack() }
                }
                Phase.WAITING -> BottomActionBar {
                    TextLink(stringResource(R.string.break_glass_cancel_wait), color = MaterialTheme.colorScheme.error) { scope.launch { p?.let { AppGraph.pendingRepo.delete(it.id) } } }
                }
                Phase.SENTENCE -> BottomActionBar {
                    DangerButton(stringResource(R.string.break_glass_unlock), enabled = typed.trim().equals(sentence.trim(), ignoreCase = true)) { unlock() }
                    TextLink(stringResource(R.string.action_cancel)) { scope.launch { p?.let { AppGraph.pendingRepo.delete(it.id) } } }
                }
                else -> Unit
            }
        },
    ) { padding ->
        if (s == null || phase == null) return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (phase) {
                Phase.ACTIVE -> SectionCard {
                    Text(stringResource(R.string.break_glass_active, Format.time(context, s.breakGlassUntil)), style = MaterialTheme.typography.titleMedium)
                }
                Phase.CONFIRM -> {
                    Text(stringResource(R.string.break_glass_lead), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SectionCard(stringResource(R.string.break_glass_terms)) {
                        Term(stringResource(R.string.break_glass_term_unlock, s.breakGlassUnlockMin))
                        Term(stringResource(R.string.break_glass_term_wait, s.breakGlassWaitMin))
                        Term(stringResource(R.string.break_glass_term_strikes))
                    }
                }
                Phase.WAITING -> {
                    val pc = p ?: return@Column
                    val left = (pc.applyAt - now) / 1000L
                    val total = (s.breakGlassWaitMin * 60L).coerceAtLeast(1)
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ProgressRing(progress = 1f - left / total.toFloat(), size = 220, stroke = 12, color = MaterialTheme.colorScheme.error) {
                            Text("%d:%02d".format(left / 60, left % 60), style = HugeNumber.copy(fontSize = 56.sp))
                        }
                        Text(stringResource(R.string.break_glass_waiting), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                Phase.SENTENCE -> {
                    Text(stringResource(R.string.break_glass_type), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SectionCard { Text(sentence, style = MaterialTheme.typography.titleMedium) }
                    AppTextField(value = typed, onValueChange = { typed = it }, minLines = 2, placeholder = stringResource(R.string.break_glass_type_here))
                }
            }
        }
    }
}

@Composable
private fun Term(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyLarge)
}
