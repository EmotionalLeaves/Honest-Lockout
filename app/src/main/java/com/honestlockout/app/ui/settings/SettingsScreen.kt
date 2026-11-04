package com.honestlockout.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.BuildConfig
import com.honestlockout.app.R
import com.honestlockout.app.data.db.Strictness
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.engine.GuardResult
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.guardMessage
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.setup.PermissionChecks
import com.honestlockout.app.ui.setup.StepId
import kotlinx.coroutines.launch

/** Every change goes through LoosenGuard and reports Applied or Queued. */
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
            SectionCard(stringResource(R.string.settings_strictness)) {
                Text(stringResource(R.string.settings_strictness_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val levels = Strictness.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    levels.forEachIndexed { i, level ->
                        SegmentedButton(
                            selected = s.strictness == level,
                            shape = SegmentedButtonDefaults.itemShape(i, levels.size),
                            onClick = {
                                scope.launch {
                                    val guard = AppGraph.loosenGuard
                                    val results = mutableListOf(guard.request(SettingChange.SetSetting(Keys.STRICTNESS, level.name)))
                                    StrictnessPresets.values(level).forEach { (k, v) ->
                                        if (s.int(k) != v) results += guard.request(SettingChange.SetSetting(k, v.toString()))
                                    }
                                    report(results)
                                }
                            },
                        ) {
                            Text(
                                stringResource(
                                    when (level) {
                                        Strictness.LENIENT -> R.string.strict_lenient
                                        Strictness.NORMAL -> R.string.strict_normal
                                        Strictness.STRICT -> R.string.strict_strict
                                    }
                                )
                            )
                        }
                    }
                }
            }

            LadderSettings(s, onInt)
            TimingSettings(s, onInt)
            ThresholdSettings(s, onInt)

            SectionCard(stringResource(R.string.settings_templates)) {
                templates.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate(Routes.template(t.id)) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.name, Modifier.weight(1f))
                        Text(stringResource(R.string.pick_default_minutes, t.maxMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionCard(stringResource(R.string.settings_screen_rules)) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            val n = AppGraph.screenRules.reloadFromAssets()
                            snackbar.showSnackbar(context.getString(R.string.rules_reloaded, n))
                        }
                    },
                ) { Text(stringResource(R.string.rules_reload)) }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.INSPECTOR) }) {
                    Text(stringResource(R.string.rules_inspect))
                }
            }

            SectionCard {
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate(Routes.PENDING) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_pending), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (pendingCount > 0) Badge { Text("$pendingCount") }
                }
            }

            SectionCard(stringResource(R.string.settings_device_admin)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_device_admin_desc), Modifier.weight(1f))
                    Switch(
                        checked = adminActive,
                        onCheckedChange = { on ->
                            if (on) {
                                AppGraph.guardSuppressedUntil = System.currentTimeMillis() + 2 * 60_000L
                                runCatching {
                                    adminLauncher.launch(
                                        PermissionChecks.intentFor(context, StepId.DEVICE_ADMIN) ?: Intent()
                                    )
                                }
                            } else {
                                scope.launch { report(listOf(AppGraph.loosenGuard.request(SettingChange.DisableDeviceAdmin()))) }
                            }
                        },
                    )
                }
            }

            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.BREAKGLASS) }) {
                Text(stringResource(R.string.settings_break_glass), color = MaterialTheme.colorScheme.error)
            }

            SectionCard(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
