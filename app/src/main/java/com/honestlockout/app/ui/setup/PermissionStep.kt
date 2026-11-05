package com.honestlockout.app.ui.setup

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import com.honestlockout.app.ui.StatusDot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.service.LockAccessibilityService
import com.honestlockout.app.service.LockDeviceAdminReceiver

/** The setup wizard's steps, in the spec's order. 3b (Samsung) and 5 (API 33+) are conditional. */
enum class StepId { NOTIFICATIONS, USAGE, BATTERY, SAMSUNG_BATTERY, OVERLAY, RESTRICTED, ACCESSIBILITY, DEVICE_ADMIN }

data class PermissionStep(
    val id: StepId,
    val number: String,
    val title: Int,
    val why: Int,
    /** False for steps that cannot be checked (3b, 5): Next is always enabled for them. */
    val checkable: Boolean = true,
    val optional: Boolean = false,
)

fun stepIcon(id: StepId): ImageVector = when (id) {
    StepId.NOTIFICATIONS -> Icons.Outlined.Notifications
    StepId.USAGE -> Icons.Outlined.Timer
    StepId.BATTERY, StepId.SAMSUNG_BATTERY -> Icons.Outlined.BatteryChargingFull
    StepId.OVERLAY -> Icons.Outlined.Layers
    StepId.RESTRICTED -> Icons.Outlined.LockOpen
    StepId.ACCESSIBILITY -> Icons.Outlined.Visibility
    StepId.DEVICE_ADMIN -> Icons.Outlined.AdminPanelSettings
}

object PermissionChecks {
    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun steps(): List<PermissionStep> = buildList {
        add(PermissionStep(StepId.NOTIFICATIONS, "1", R.string.step_notifications_title, R.string.step_notifications_why))
        add(PermissionStep(StepId.USAGE, "2", R.string.step_usage_title, R.string.step_usage_why))
        add(PermissionStep(StepId.BATTERY, "3", R.string.step_battery_title, R.string.step_battery_why))
        if (isSamsung) add(PermissionStep(StepId.SAMSUNG_BATTERY, "3b", R.string.step_samsung_title, R.string.step_samsung_why, checkable = false))
        add(PermissionStep(StepId.OVERLAY, "4", R.string.step_overlay_title, R.string.step_overlay_why))
        if (Build.VERSION.SDK_INT >= 33) add(PermissionStep(StepId.RESTRICTED, "5", R.string.step_restricted_title, R.string.step_restricted_why, checkable = false))
        add(PermissionStep(StepId.ACCESSIBILITY, "6", R.string.step_accessibility_title, R.string.step_accessibility_why))
        add(PermissionStep(StepId.DEVICE_ADMIN, "7", R.string.step_admin_title, R.string.step_admin_why, optional = true))
    }

    fun isGranted(context: Context, id: StepId): Boolean = when (id) {
        StepId.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        StepId.USAGE -> AppGraph.usageRepo.hasUsageAccess()
        StepId.BATTERY -> (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        StepId.SAMSUNG_BATTERY, StepId.RESTRICTED -> false
        StepId.OVERLAY -> Settings.canDrawOverlays(context)
        StepId.ACCESSIBILITY -> accessibilityEnabled(context)
        StepId.DEVICE_ADMIN -> deviceAdminActive(context)
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, LockAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun adminComponent(context: Context) = ComponentName(context, LockDeviceAdminReceiver::class.java)

    fun deviceAdminActive(context: Context): Boolean =
        (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComponent(context))

    fun appInfoIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** The settings page each step opens. Notifications on API 33+ use the runtime dialog instead. */
    fun intentFor(context: Context, id: StepId): Intent = when (id) {
        StepId.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        StepId.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        StepId.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        StepId.SAMSUNG_BATTERY -> Intent().setComponent(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
        )
        StepId.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        StepId.RESTRICTED -> appInfoIntent(context)
        StepId.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        StepId.DEVICE_ADMIN -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))
    }

    /** Starts [intent]; falls back to App info (or, for the Samsung step, battery settings), then Settings. */
    fun launch(context: Context, intent: Intent?, batteryFallback: Boolean = false) {
        val fallback = if (batteryFallback) Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) else appInfoIntent(context)
        val tries = listOfNotNull(intent, fallback, Intent(Settings.ACTION_SETTINGS))
        for (i in tries) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }
}

@Composable
fun StatusChip(granted: Boolean, checkable: Boolean) {
    val label = when {
        !checkable -> stringResource(R.string.status_reminder)
        granted -> stringResource(R.string.status_granted)
        else -> stringResource(R.string.status_not_yet)
    }
    StatusDot(if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, label)
}

@Composable
fun NumberedInstructions(lines: List<String>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { i, line ->
            Text("${i + 1}. $line", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
