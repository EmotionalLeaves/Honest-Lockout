package com.honestlockout.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.nav.Routes

/** The hub. Every other screen is reached from here and has a back arrow. */
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
            if (s.loaded && !s.accessibilityOn) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        stringResource(R.string.home_service_off), Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            s.active?.let { o ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.home_active_title, Format.appLabel(context, o.packageName)), style = MaterialTheme.typography.labelLarge)
                        Text(o.statement, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.home_minutes_left, s.activeMinutesLeft))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { vm.done() }) { Text(stringResource(R.string.notif_done)) }
                            OutlinedButton(onClick = { vm.lockIt() }) { Text(stringResource(R.string.home_lock_it)) }
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.home_today)) {
                Text(Format.minutes(context, s.todayMinutes), style = MaterialTheme.typography.headlineMedium)
                s.budgets.forEach { b ->
                    Text(stringResource(R.string.home_budget_left, b.name, b.leftMin, b.budgetMin))
                }
            }

            SectionCard(stringResource(R.string.home_strikes)) {
                Text(
                    if (s.activeStrikes == 0) stringResource(R.string.home_no_strikes)
                    else stringResource(R.string.home_strike_count, s.activeStrikes),
                    style = MaterialTheme.typography.titleMedium,
                )
                s.oldestStrikeExpiresAt?.let { Text(stringResource(R.string.home_oldest_expires, Format.dateTime(context, it))) }
                Text(
                    s.nextOverrideAt?.let { stringResource(R.string.home_next_override_at, Format.dateTime(context, it)) }
                        ?: stringResource(R.string.home_next_override_now)
                )
            }

            Button(
                onClick = { nav.navigate(Routes.pick()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.home_start_override)) }
            FilledTonalButton(onClick = { nav.navigate(Routes.BLOCKLIST) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_block_list))
            }
            FilledTonalButton(onClick = { nav.navigate(Routes.HISTORY) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_history))
            }
            FilledTonalButton(onClick = { nav.navigate(Routes.SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
                BadgedBox(badge = { if (s.pendingCount > 0) Badge { Text("${s.pendingCount}") } }) {
                    Text(stringResource(R.string.home_settings))
                }
            }
            TextButton(onClick = { nav.navigate(Routes.BREAKGLASS) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.home_emergency_unlock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}
