package com.honestlockout.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.data.db.entities.TamperEvent
import com.honestlockout.app.work.PendingChangeWorker
import com.honestlockout.app.work.StrikeExpiryWorker
import kotlinx.coroutines.launch

/**
 * On BOOT_COMPLETED and MY_PACKAGE_REPLACED: run the pending-change and strike-expiry workers
 * once, log a REBOOT tamper event (never punished), and end any override left running with
 * TAMPER and no strike. The accessibility service restarts by itself if its toggle is still on.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        AppGraph.appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                PendingChangeWorker.runNow(context)
                StrikeExpiryWorker.runOnce(context)
                AppGraph.strikeRepo.recordTamperEvent(TamperEvent.REBOOT, now)
                if (AppGraph.overrideRepo.getActive() != null) {
                    AppGraph.monitor.stop()
                    AppGraph.overrideRepo.end(EndReason.TAMPER, now)
                    // The service may already have been resumed by the accessibility service.
                    if (OverrideForegroundService.running) {
                        OverrideForegroundService.send(context, OverrideForegroundService.ACTION_STOP_SILENT)
                    }
                }
                AppLog.w("HL/Svc", "Boot/update handled: ${intent.action}")
            }.onFailure { AppLog.w("HL/Svc", "BootReceiver failed", it) }
            pending.finish()
        }
    }
}
