package com.honestlockout.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.honestlockout.app.R
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.GroupHeader
import com.honestlockout.app.ui.ProgressRing
import com.honestlockout.app.ui.Stat
import com.honestlockout.app.ui.UsageBar
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.theme.Danger
import com.honestlockout.app.ui.theme.Primary
import com.honestlockout.app.ui.theme.TextMuted
import com.honestlockout.app.ui.theme.Warning

/** The hub: one status card, today's numbers, a grouped list of destinations. */
@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshNow() }

    AppScaffold(title = stringResource(R.string.app_name), onBack = null) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!s.loaded) { Spacer(Modifier.height(1.dp)); return@Column }
            AnimatedContent(targetState = s.active != null, label = "hero") { running ->
                if (running) ActiveHero(s, vm) else StatusHero(s, nav)
            }

            Column {
                GroupHeader(stringResource(R.string.home_today))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat(Format.minutes(context, s.todayMinutes), stringResource(R.string.home_stat_screen_time))
                            Stat("${s.activeStrikes}", stringResource(R.string.home_stat_strikes), valueColor = if (s.activeStrikes == 0) MaterialTheme.colorScheme.onSurface else Danger)
                            Stat("${s.blockedCount}", stringResource(R.string.home_stat_blocked))
                        }
                        s.budgets.forEach { b ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.home_budget_left_short, b.leftMin, b.budgetMin), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                }
                                UsageBar((b.budgetMin - b.leftMin).toFloat() / b.budgetMin.coerceAtLeast(1), color = if (b.leftMin == 0) Warning else Primary)
                            }
                        }
                        s.oldestStrikeExpiresAt?.let {
                            Text(stringResource(R.string.home_oldest_expires, Format.dateTime(context, it)), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column {
                    NavRow(Icons.Outlined.Block, Primary, stringResource(R.string.home_block_list), stringResource(R.string.home_tile_block_sub, s.blockedCount)) { nav.navigate(Routes.BLOCKLIST) }
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outline)
                    NavRow(Icons.Outlined.History, MaterialTheme.colorScheme.onSurface, stringResource(R.string.home_history), stringResource(R.string.home_tile_history_sub)) { nav.navigate(Routes.HISTORY) }
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outline)
                    NavRow(Icons.Outlined.Settings, MaterialTheme.colorScheme.onSurface, stringResource(R.string.home_settings), stringResource(R.string.home_tile_settings_sub), badge = s.pendingCount) { nav.navigate(Routes.SETTINGS) }
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outline)
                    NavRow(Icons.Outlined.LockOpen, Danger, stringResource(R.string.home_emergency_unlock), stringResource(R.string.home_tile_emergency_sub)) { nav.navigate(Routes.BREAKGLASS) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** iOS-style row: tinted icon square, title + subtitle, chevron. */
@Composable
private fun NavRow(icon: ImageVector, tint: Color, title: String, subtitle: String, badge: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (badge > 0) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Warning), contentAlignment = Alignment.Center) {
                Text("$badge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiary)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
private fun StatusHero(s: HomeState, nav: NavController) {
    val context = LocalContext.current
    val ok = s.accessibilityOn
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) Primary else Danger))
                Text(
                    stringResource(if (ok) R.string.home_hero_protected else R.string.home_hero_service_off).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = TextMuted,
                )
            }
            Text(
                when {
                    !ok -> stringResource(R.string.home_service_off)
                    s.blockedCount == 0 -> stringResource(R.string.home_hero_no_apps)
                    s.nextOverrideAt != null -> stringResource(R.string.home_next_override_at, Format.dateTime(context, s.nextOverrideAt))
                    else -> stringResource(R.string.home_hero_ready, s.blockedCount)
                },
                style = if (ok) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { nav.navigate(if (s.blockedCount == 0) Routes.APPPICKER else Routes.pick()) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(if (s.blockedCount == 0) R.string.blocklist_add else R.string.home_start_override), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun ActiveHero(s: HomeState, vm: HomeViewModel) {
    val context = LocalContext.current
    val o = s.active ?: return
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.home_active_title, Format.appLabel(context, o.packageName)).uppercase(), style = MaterialTheme.typography.labelMedium, color = TextMuted)
            ProgressRing(progress = 1f - s.activeMinutesLeft / o.plannedMin.coerceAtLeast(1).toFloat(), size = 150, stroke = 10) {
                Stat("${s.activeMinutesLeft}", stringResource(R.string.home_ring_min_left))
            }
            Text(o.statement, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.done() }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.notif_done)) }
                OutlinedButton(onClick = { vm.lockIt() }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.home_lock_it)) }
            }
        }
    }
}
