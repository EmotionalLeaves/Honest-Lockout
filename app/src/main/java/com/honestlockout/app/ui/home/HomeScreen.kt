package com.honestlockout.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.honestlockout.app.R
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.NavTile
import com.honestlockout.app.ui.ProgressRing
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.Stat
import com.honestlockout.app.ui.StatusDot
import com.honestlockout.app.ui.UsageBar
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.theme.Danger
import com.honestlockout.app.ui.theme.Primary
import com.honestlockout.app.ui.theme.Warning

/** The hub: a status hero, today's numbers, and tiles to every other screen. */
@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshNow() }

    AppScaffold(title = stringResource(R.string.app_name), onBack = null) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedContent(targetState = s.active != null, label = "hero") { running ->
                if (running) ActiveHero(s, vm) else StatusHero(s, nav)
            }

            SectionCard(stringResource(R.string.home_today)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(Format.minutes(context, s.todayMinutes), stringResource(R.string.home_stat_screen_time))
                    Stat(
                        "${s.activeStrikes}", stringResource(R.string.home_stat_strikes),
                        valueColor = if (s.activeStrikes == 0) MaterialTheme.colorScheme.onSurface else Danger,
                    )
                    Stat("${s.blockedCount}", stringResource(R.string.home_stat_blocked))
                }
                s.budgets.forEach { b ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.home_budget_left_short, b.leftMin, b.budgetMin),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val used = (b.budgetMin - b.leftMin).toFloat() / b.budgetMin.coerceAtLeast(1)
                        UsageBar(used, color = if (b.leftMin == 0) Warning else Primary)
                    }
                }
                s.oldestStrikeExpiresAt?.let {
                    Text(stringResource(R.string.home_oldest_expires, Format.dateTime(context, it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavTile(Icons.Filled.Block, stringResource(R.string.home_block_list), stringResource(R.string.home_tile_block_sub, s.blockedCount), Modifier.weight(1f)) { nav.navigate(Routes.BLOCKLIST) }
                NavTile(Icons.Filled.History, stringResource(R.string.home_history), stringResource(R.string.home_tile_history_sub), Modifier.weight(1f)) { nav.navigate(Routes.HISTORY) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavTile(Icons.Filled.Settings, stringResource(R.string.home_settings), stringResource(R.string.home_tile_settings_sub), Modifier.weight(1f), badge = s.pendingCount) { nav.navigate(Routes.SETTINGS) }
                NavTile(Icons.Filled.Warning, stringResource(R.string.home_emergency_unlock), stringResource(R.string.home_tile_emergency_sub), Modifier.weight(1f)) { nav.navigate(Routes.BREAKGLASS) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusHero(s: HomeState, nav: NavController) {
    val context = LocalContext.current
    val ok = s.accessibilityOn
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    stringResource(if (ok) R.string.home_hero_protected else R.string.home_hero_service_off),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (!ok) {
                Text(stringResource(R.string.home_service_off), color = MaterialTheme.colorScheme.onErrorContainer)
            } else {
                StatusDot(
                    if (s.nextOverrideAt == null) Primary else Warning,
                    s.nextOverrideAt?.let { stringResource(R.string.home_next_override_at, Format.dateTime(context, it)) }
                        ?: stringResource(R.string.home_next_override_now),
                )
                if (s.blockedCount == 0) Text(stringResource(R.string.home_hero_no_apps), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Button(onClick = { nav.navigate(if (s.blockedCount == 0) Routes.APPPICKER else Routes.pick()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(if (s.blockedCount == 0) Icons.Filled.Block else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(if (s.blockedCount == 0) R.string.blocklist_add else R.string.home_start_override))
            }
        }
    }
}

@Composable
private fun ActiveHero(s: HomeState, vm: HomeViewModel) {
    val context = LocalContext.current
    val o = s.active ?: return
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.home_active_title, Format.appLabel(context, o.packageName)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            ProgressRing(progress = 1f - s.activeMinutesLeft / o.plannedMin.coerceAtLeast(1).toFloat(), size = 150, stroke = 10) {
                Stat("${s.activeMinutesLeft}", stringResource(R.string.home_ring_min_left), valueColor = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(o.statement, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.done() }) { Text(stringResource(R.string.notif_done)) }
                OutlinedButton(onClick = { vm.lockIt() }) { Text(stringResource(R.string.home_lock_it)) }
            }
        }
    }
}
