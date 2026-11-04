package com.honestlockout.app.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.honestlockout.app.ui.BottomActionBar
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.SecondaryButton
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.TextLink
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

    // After two denials Android stops showing the dialog; then open the notification settings page.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && !PermissionChecks.isGranted(context, StepId.NOTIFICATIONS)) {
            PermissionChecks.launch(context, PermissionChecks.intentFor(context, StepId.NOTIFICATIONS))
        }
        refresh++
    }
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

    fun open() {
        when {
            step.id == StepId.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 -> notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            step.id == StepId.DEVICE_ADMIN -> runCatching { adminLauncher.launch(PermissionChecks.intentFor(context, step.id)) }
            else -> PermissionChecks.launch(context, PermissionChecks.intentFor(context, step.id), batteryFallback = step.id == StepId.SAMSUNG_BATTERY)
        }
    }

    AppScaffold(
        title = stringResource(R.string.setup_title),
        onBack = if (index > 0) ({ index-- }) else null,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(stringResource(R.string.setup_open_settings)) { open() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(stringResource(R.string.setup_check_again), Modifier.weight(1f)) { refresh++ }
                    SecondaryButton(
                        stringResource(if (index == steps.lastIndex) R.string.setup_finish else R.string.setup_next),
                        Modifier.weight(1f), enabled = granted || !step.checkable,
                    ) { next() }
                }
                if (step.optional) TextLink(stringResource(R.string.setup_skip)) { finish() }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StepDots(steps.size, index)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(stepIcon(step.id), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                Column {
                    Text(stringResource(R.string.setup_step_of, step.number, steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(step.title), style = MaterialTheme.typography.headlineSmall)
                }
            }
            Text(stringResource(step.why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusChip(granted, step.checkable)

            when (step.id) {
                StepId.RESTRICTED -> SectionCard {
                    NumberedInstructions(
                        listOf(
                            stringResource(R.string.step_restricted_1),
                            stringResource(R.string.step_restricted_2),
                            stringResource(R.string.step_restricted_3),
                        )
                    )
                }
                StepId.SAMSUNG_BATTERY -> SectionCard { Text(stringResource(R.string.step_samsung_note), style = MaterialTheme.typography.bodyLarge) }
                StepId.ACCESSIBILITY -> if (!accessibilityOk && Build.VERSION.SDK_INT >= 33) {
                    SectionCard {
                        Text(stringResource(R.string.step_accessibility_greyed), color = MaterialTheme.colorScheme.tertiary)
                        TextLink(stringResource(R.string.setup_open_app_info), color = MaterialTheme.colorScheme.primary) {
                            PermissionChecks.launch(context, PermissionChecks.appInfoIntent(context))
                        }
                    }
                }
                else -> Unit
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Progress as a row of short bars, the current one in the accent. */
@Composable
private fun StepDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.weight(1f).height(4.dp).clip(CircleShape).background(
                    when {
                        i < current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        i == current -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
            )
        }
    }
}
