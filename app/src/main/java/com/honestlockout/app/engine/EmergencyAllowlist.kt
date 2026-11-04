package com.honestlockout.app.engine

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Packages that can never be blocked. Hardcoded and not editable. The current default dialer
 * and default SMS app are added at service start ([refreshDefaults]) and cached.
 */
object EmergencyAllowlist {
    val packages: Set<String> = setOf(
        "com.android.phone", "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
        "com.android.mms", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "com.google.android.apps.maps",
        "com.android.emergency", "com.android.settings", "com.android.systemui",
        "com.honestlockout.app",
    )

    @Volatile private var defaults: Set<String> = emptySet()

    fun refreshDefaults(context: Context) {
        val found = mutableSetOf<String>()
        runCatching {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
        }.getOrNull()?.let { found += it }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { found += it }
        defaults = found
    }

    fun contains(packageName: String): Boolean = packageName in packages || packageName in defaults
}
