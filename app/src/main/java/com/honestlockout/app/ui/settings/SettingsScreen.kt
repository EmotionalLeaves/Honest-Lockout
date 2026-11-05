package com.honestlockout.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.BuildConfig
import com.honestlockout.app.R
import com.honestlockout.app.ui.cardBorder
import com.honestlockout.app.data.db.Strictness
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.data.settings.Settings
import com.honestlockout.app.engine.GuardResult
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.ExpandableSection
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.GroupHeader
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.SecondaryButton
import com.honestlockout.app.ui.SelectRow
import com.honestlockout.app.ui.TextLink
import com.honestlockout.app.ui.guardMessage
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.setup.PermissionChecks
import com.honestlockout.app.ui.setup.StepId
import kotlinx.coroutines.launch

/**
 * Settings in plain language: how changes work, a strictness preset, what a strike costs, timers,
 * purposes, then the advanced bits. Every change goes through LoosenGuard and says Applied or Queued.
 */
@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by AppGraph.settings.data.collectAsState(initial = null)
    val templates by AppGraph.overrideRepo.templates().collectAsState(initial = emptyList())
    val pendingCount by AppGraph.pendingRepo.pendingCount().collectAsState(initial = 0)
    var adminTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { adminTick++ }
    val adminActive = remember(adminTick) { PermissionChecks.deviceAdminActive(context) }

    fun report(results: List<GuardResult>) = scope.launch {
        val queued = results.filterIsInstance<GuardResult.Queued>()
        val msg = when {
            queued.isEmpty() -> context.getString(R.string.guard_applied)
            queued.size == results.size -> guardMessage(context, queued.maxBy { it.applyAt })
            else -> context.getString(R.string.guard_mixed, queued.size, results.size)
        }
        snackbar.showSnackbar(msg)
    }
    fun change(key: String, value: String) = scope.launch {
        report(listOf(AppGraph.loosenGuard.request(SettingChange.SetSetting(key, value))))
    }
    val onInt: (String, Int) -> Unit = { k, v -> change(k, v.toString()) }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        adminTick++
        if (PermissionChecks.deviceAdminActive(context)) change(Keys.DEVICE_ADMIN_WANTED, "true")
    }

    AppScaffold(title = stringResource(R.string.settings_title), onBack = { nav.popBackStack() }, snackbar = snackbar) { padding ->
        val s = settings ?: return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HowChangesWork(s, pendingCount) { nav.navigate(Routes.PENDING) }

            StrictnessPicker(s) { level ->
                scope.launch {
                    val guard = AppGraph.loosenGuard
                    val results = mutableListOf(guard.request(SettingChange.SetSetting(Keys.STRICTNESS, level.name)))
                    StrictnessPresets.values(level).forEach { (k, v) ->
                        if (s.int(k) != v) results += guard.request(SettingChange.SetSetting(k, v.toString()))
                    }
                    report(results)
                }
            }

            LadderSettings(s, onInt)
            TimingSettings(s, onInt)

            ExpandableSection(stringResource(R.string.settings_templates), stringResource(R.string.settings_templates_desc)) {
                templates.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate(Routes.template(t.id)) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyLarge)
                            val screens = PurposeTemplates.split(t.blockedScreenLabels)
                            Text(
                                if (screens.isEmpty()) stringResource(R.string.template_row_no_screens, Format.minutes(context, t.maxMinutes))
                                else stringResource(R.string.template_row, Format.minutes(context, t.maxMinutes), screens.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            ExpandableSection(stringResource(R.string.settings_advanced), stringResource(R.string.settings_advanced_desc)) {
                ThresholdSettings(s, onInt)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.settings_screen_rules), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.settings_screen_rules_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(stringResource(R.string.rules_inspect)) { nav.navigate(Routes.INSPECTOR) }
                TextLink(stringResource(R.string.rules_reload), color = MaterialTheme.colorScheme.primary) {
                    scope.launch {
                        val n = AppGraph.screenRules.reloadFromAssets()
                        snackbar.showSnackbar(context.getString(R.string.rules_reloaded, n))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_device_admin), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_device_admin_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = adminActive,
                        onCheckedChange = { on ->
                            if (on) {
                                AppGraph.guardSuppressedUntil = System.currentTimeMillis() + 2 * 60_000L
                                runCatching { adminLauncher.launch(PermissionChecks.intentFor(context, StepId.DEVICE_ADMIN)) }
                            } else {
                                scope.launch { report(listOf(AppGraph.loosenGuard.request(SettingChange.DisableDeviceAdmin()))) }
                            }
                        },
                    )
                }
            }

            TextLink(stringResource(R.string.settings_break_glass), color = MaterialTheme.colorScheme.error) { nav.navigate(Routes.BREAKGLASS) }
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The one rule that explains every other setting, plus the queue of waiting changes. */
@Composable
private fun HowChangesWork(s: Settings, pendingCount: Int, onOpenPending: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.settings_how_title).uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.settings_how_body, s.loosenDelayHours), style = MaterialTheme.typography.bodyLarge)
            if (pendingCount > 0) {
                RowDivider(inset = 0)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenPending).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_pending_count, pendingCount), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Three presets as check rows, each saying exactly what it changes. */
@Composable
private fun StrictnessPicker(s: Settings, onPick: (Strictness) -> Unit) {
    Column {
        GroupHeader(stringResource(R.string.settings_strictness))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = cardBorder(),
        ) {
            Column {
                Strictness.entries.forEachIndexed { i, level ->
                    if (i > 0) RowDivider()
                    SelectRow(
                        title = stringResource(
                            when (level) {
                                Strictness.LENIENT -> R.string.strict_lenient
                                Strictness.NORMAL -> R.string.strict_normal
                                Strictness.STRICT -> R.string.strict_strict
                            }
                        ),
                        subtitle = stringResource(
                            when (level) {
                                Strictness.LENIENT -> R.string.strict_lenient_desc
                                Strictness.NORMAL -> R.string.strict_normal_desc
                                Strictness.STRICT -> R.string.strict_strict_desc
                            }
                        ),
                        selected = s.strictness == level,
                    ) { if (s.strictness != level) onPick(level) }
                }
            }
        }
    }
}
