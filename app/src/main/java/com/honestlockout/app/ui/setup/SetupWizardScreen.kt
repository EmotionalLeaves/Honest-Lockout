package com.honestlockout.app.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.ui.AppScaffold
import kotlinx.coroutines.launch

/**
 * One permission step at a time. Next is enabled only when the step is granted (3b and 5 cannot
 * be checked; 7 can be skipped). Finishing sets setup_complete.
 */
@Composable
fun SetupWizardScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps = remember { PermissionChecks.steps() }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (PermissionChecks.deviceAdminActive(context)) {
            scope.launch { AppGraph.loosenGuard.request(SettingChange.SetSetting(Keys.DEVICE_ADMIN_WANTED, "true")) }
        }
        refresh++
    }

    val step = steps[index]
    val granted = remember(step, refresh) { PermissionChecks.isGranted(context, step.id) }
    val accessibilityOk = remember(refresh) { PermissionChecks.accessibilityEnabled(context) }

    fun finish() {
        scope.launch {
            AppGraph.settings.setSetupComplete(true)
            onFinished()
        }
    }

    fun next() { if (index < steps.lastIndex) index++ else finish() }

    AppScaffold(
        title = stringResource(R.string.setup_title),
        onBack = if (index > 0) ({ index-- }) else null,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.setup_step_of, step.number, steps.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(step.title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(step.why), style = MaterialTheme.typography.bodyLarge)
            StatusChip(granted, step.checkable)

            when (step.id) {
                StepId.RESTRICTED -> NumberedInstructions(
                    listOf(
                        stringResource(R.string.step_restricted_1),
                        stringResource(R.string.step_restricted_2),
                        stringResource(R.string.step_restricted_3),
                    )
                )
                StepId.SAMSUNG_BATTERY -> Text(stringResource(R.string.step_samsung_note), style = MaterialTheme.typography.bodyLarge)
                StepId.ACCESSIBILITY -> if (!accessibilityOk && Build.VERSION.SDK_INT >= 33) {
                    Text(stringResource(R.string.step_accessibility_greyed), color = MaterialTheme.colorScheme.tertiary)
                    OutlinedButton(onClick = { PermissionChecks.launch(context, PermissionChecks.appInfoIntent(context)) }) {
                        Text(stringResource(R.string.setup_open_app_info))
                    }
                }
                else -> Unit
            }

            Spacer(Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when {
                        step.id == StepId.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 ->
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        step.id == StepId.DEVICE_ADMIN ->
                            runCatching { adminLauncher.launch(PermissionChecks.intentFor(context, step.id)!!) }
                        else -> PermissionChecks.launch(context, PermissionChecks.intentFor(context, step.id))
                    }
                },
            ) { Text(stringResource(R.string.setup_open_settings)) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { refresh++ }) {
                    Text(stringResource(R.string.setup_check_again))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = granted || !step.checkable,
                    onClick = { next() },
                ) { Text(stringResource(if (index == steps.lastIndex) R.string.setup_finish else R.string.setup_next)) }
            }
            if (step.optional) {
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = { finish() }) {
                    Text(stringResource(R.string.setup_skip))
                }
            }
        }
    }
}
