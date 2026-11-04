package com.honestlockout.app.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.R
import com.honestlockout.app.data.db.StrikeReason
import com.honestlockout.app.data.db.entities.TamperEvent
import kotlinx.coroutines.launch

/**
 * Device admin with no policies: it only makes uninstalling require deactivation first.
 * Deactivating while a lockout is active is a tamper strike.
 */
class LockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    override fun onDisabled(context: Context, intent: Intent) {
        val pending = goAsync()
        AppGraph.appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                // A queued DisableDeviceAdmin change clears device_admin_wanted before removing
                // the admin; that is the legitimate path and is not a tamper event.
                if (!AppGraph.settings.snapshot().deviceAdminWanted) {
                    AppLog.w("HL/Svc", "Device admin removed by an applied change")
                    return@runCatching
                }
                val lockoutActive = AppGraph.strikeRepo.anyLockoutActive(now)
                val strikeId = if (lockoutActive) {
                    AppGraph.ladder.record(null, null, StrikeReason.TAMPER, now).strikeId
                } else null
                AppGraph.strikeRepo.recordTamperEvent(TamperEvent.SERVICE_OFF, now, strikeId ?: NO_STRIKE)
                AppLog.w("HL/Svc", "Device admin disabled; strike=${strikeId != null}")
            }.onFailure { AppLog.w("HL/Svc", "onDisabled failed", it) }
            pending.finish()
        }
    }

    companion object {
        /**
         * Marks the SERVICE_OFF event as already handled so the accessibility service does not
         * turn it into a second TAMPER strike on its next connect.
         */
        const val NO_STRIKE = -1L
    }
}
