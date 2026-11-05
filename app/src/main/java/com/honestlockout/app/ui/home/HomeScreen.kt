package com.honestlockout.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.honestlockout.app.R
import com.honestlockout.app.engine.DayClock
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.GroupHeader
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.ProgressRing
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.SecondaryButton
import com.honestlockout.app.ui.Staggered
import com.honestlockout.app.ui.StatusOrb
import com.honestlockout.app.ui.UsageBar
import com.honestlockout.app.ui.accentGlow
import com.honestlockout.app.ui.cardBorder
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.theme.Danger
import com.honestlockout.app.ui.theme.Primary
import com.honestlockout.app.ui.theme.TextMuted
import com.honestlockout.app.ui.theme.Warning
import java.time.format.TextStyle
import java.util.Locale

/**
 * Today: one status card with the one action that matters right now, the week's screen time,
 * daily limits, and strikes only when there are any. Navigation lives in the tab bar.
 */
@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshNow() }

    AppScaffold(
        title = stringResource(R.string.tab_today), onBack = null,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                Box {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.home_settings))
                    if (s.pendingCount > 0) Box(Modifier.size(8.dp).align(Alignment.TopEnd).background(Warning, RoundedCornerShape(4.dp)))
                }
            }
        },
    ) { padding ->
        if (!s.loaded) return@AppScaffold
        val glow = when {
            !s.accessibilityOn -> Danger
            s.nextOverrideAt != null -> Warning
            else -> Primary
        }
        Staggered(
            Modifier.fillMaxSize().accentGlow(glow).padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        ) {
            Item {
                AnimatedContent(targetState = s.active != null, label = "hero") { running ->
                    if (running) ActiveCard(s, vm) else StatusCard(s, nav)
                }
            }
            Item { Spacer(Modifier.height(24.dp)) }
            Item { WeekCard(s) }
            if (s.budgets.isNotEmpty()) {
                Item { Spacer(Modifier.height(24.dp)) }
                Item { LimitsCard(s) }
            }
            if (s.activeStrikes > 0) {
                Item { Spacer(Modifier.height(24.dp)) }
                Item { StrikesRow(s) { nav.navigate(Routes.HISTORY) { launchSingleTop = true } } }
            }
            Item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** The state of things in one sentence, and the single next action. */
@Composable
private fun StatusCard(s: HomeState, nav: NavController) {
    val context = LocalContext.current
    data class Status(val color: Color, val label: Int, val headline: String, val detail: String?)
    val st = when {
        !s.accessibilityOn -> Status(Danger, R.string.status_off, stringResource(R.string.status_off_headline), stringResource(R.string.status_off_detail))
        s.blockedCount == 0 -> Status(TextMuted, R.string.status_ready, stringResource(R.string.status_empty_headline), stringResource(R.string.status_empty_detail))
        s.nextOverrideAt != null -> Status(Warning, R.string.status_waiting, stringResource(R.string.status_waiting_headline, Format.time(context, s.nextOverrideAt)), stringResource(R.string.status_waiting_detail))
        else -> Status(Primary, R.string.status_on, context.resources.getQuantityString(R.plurals.status_on_headline, s.blockedCount, s.blockedCount), null)
    }
    Card(
        shape = RoundedCornerShape(24.dp), border = cardBorder(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusOrb(st.color, size = 8)
                Text(stringResource(st.label).uppercase(), style = MaterialTheme.typography.labelSmall, color = st.color)
            }
            Text(st.headline, style = MaterialTheme.typography.headlineSmall)
            st.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            Spacer(Modifier.height(10.dp))
            when {
                !s.accessibilityOn -> PrimaryButton(stringResource(R.string.status_off_action)) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                s.blockedCount == 0 -> PrimaryButton(stringResource(R.string.status_empty_action)) { nav.navigate(Routes.APPPICKER) }
                s.nextOverrideAt != null -> SecondaryButton(stringResource(R.string.status_on_action), enabled = false) {}
                else -> PrimaryButton(stringResource(R.string.status_on_action)) { nav.navigate(Routes.pick()) }
            }
        }
    }
}

@Composable
private fun ActiveCard(s: HomeState, vm: HomeViewModel) {
    val context = LocalContext.current
    val o = s.active ?: return
    Card(shape = RoundedCornerShape(24.dp), border = cardBorder(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(o.packageName, 24)
                Text(stringResource(R.string.home_active_title, Format.appLabel(context, o.packageName)).uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            ProgressRing(progress = 1f - s.activeMinutesLeft / o.plannedMin.coerceAtLeast(1).toFloat(), size = 168, stroke = 10) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.activeMinutesLeft}", style = MaterialTheme.typography.displayMedium)
                    Text(stringResource(R.string.home_ring_min_left), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                }
            }
            Text(o.statement, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.notif_done), Modifier.weight(1f)) { vm.done() }
                SecondaryButton(stringResource(R.string.home_lock_it), Modifier.weight(1f)) { vm.lockIt() }
            }
        }
    }
}

/** Today's total in large type over a 7-day bar strip (today in the accent). */
@Composable
private fun WeekCard(s: HomeState) {
    val context = LocalContext.current
    val week = s.week.ifEmpty { List(7) { 0 } }
    val avg = week.dropLast(1).filter { it > 0 }.let { if (it.isEmpty()) null else it.average().toInt() }
    Column {
        GroupHeader(stringResource(R.string.home_today))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = cardBorder()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Format.minutes(context, s.todayMinutes), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    if (avg != null) {
                        Text(stringResource(R.string.home_week_avg, Format.minutes(context, avg)), style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                val max = (week.maxOrNull() ?: 0).coerceAtLeast(1)
                val track = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                val past = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                    val slot = size.width / 7f
                    val barW = slot * 0.42f
                    week.forEachIndexed { i, m ->
                        val x = i * slot + (slot - barW) / 2
                        drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                        val h = (m / max.toFloat()) * size.height
                        if (h > 0f) drawRoundRect(
                            if (i == week.lastIndex) Primary else past,
                            Offset(x, size.height - h.coerceAtLeast(barW)), Size(barW, h.coerceAtLeast(barW)), CornerRadius(barW / 2),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val today = DayClock.localDate(System.currentTimeMillis())
                    (6 downTo 0).forEach { back ->
                        Text(
                            today.minusDays(back.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                            color = if (back == 0) MaterialTheme.colorScheme.onSurface else TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitsCard(s: HomeState) {
    Column {
        GroupHeader(stringResource(R.string.home_limits))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = cardBorder()) {
            Column {
                s.budgets.forEachIndexed { i, b ->
                    if (i > 0) RowDivider(inset = 60)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(b.packageName, 32)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row {
                                Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (b.leftMin == 0) stringResource(R.string.home_limit_used) else stringResource(R.string.home_limit_left, b.leftMin),
                                    style = MaterialTheme.typography.labelMedium, color = if (b.leftMin == 0) Warning else TextMuted,
                                )
                            }
                            UsageBar((b.budgetMin - b.leftMin).toFloat() / b.budgetMin.coerceAtLeast(1), color = if (b.leftMin == 0) Warning else Primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrikesRow(s: HomeState, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = cardBorder()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusOrb(Danger, size = 8)
            Column(Modifier.weight(1f)) {
                Text(context.resources.getQuantityString(R.plurals.home_strikes_active, s.activeStrikes, s.activeStrikes), style = MaterialTheme.typography.bodyLarge)
                s.oldestStrikeExpiresAt?.let { Text(stringResource(R.string.home_oldest_expires, Format.dateTime(context, it)), style = MaterialTheme.typography.bodySmall, color = TextMuted) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
        }
    }
}
