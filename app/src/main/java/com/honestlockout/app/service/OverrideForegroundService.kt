package com.honestlockout.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.R
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.engine.BannerState
import com.honestlockout.app.engine.TickResult
import com.honestlockout.app.overlay.BannerKind
import com.honestlockout.app.overlay.OverlayTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The clock: runs while an override is active. Shows the persistent notification, ticks
 * OverrideMonitor every second and turns its results into banners, strikes and endings.
 * Every way an override ends goes through this service (actions below) so cleanup is in one place.
 */
class OverrideForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var current: Override? = null
    private var lastNotifAt = 0L
    private var ending = false
    private var graceSec = 30

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Notifications.ensureChannels(this)
        ServiceCompat.startForeground(
            this, Notifications.ID_OVERRIDE, Notifications.overrideStarting(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_RESUME
        scope.launch {
            runCatching { handle(action) }.onFailure { AppLog.w(TAG, "action $action failed", it) }
        }
        return START_STICKY
    }

    private suspend fun handle(action: String) {
        val now = System.currentTimeMillis()
        when (action) {
            ACTION_START, ACTION_RESUME -> begin(now)
            ACTION_DONE -> finish(EndReason.DONE, goHome = true)
            ACTION_DONE_FOR_EXTEND -> finish(EndReason.DONE, goHome = false)
            ACTION_CONFESS -> finish(EndReason.CONFESSED, goHome = true)
            ACTION_BACK_TO_IT -> { AppGraph.monitor.onBackToIt(now); Notifications.cancelWarning(this) }
            ACTION_CHECKIN_YES -> { AppGraph.monitor.onCheckinYes(now); Notifications.cancelWarning(this); syncCounters() }
            ACTION_STOP_SILENT -> { cleanupAfterEnd(); stopAll() }
        }
    }

    private suspend fun begin(now: Long) {
        val o = AppGraph.overrideRepo.getActive()
        if (o == null) { stopAll(); return }
        if (current?.id != o.id || !AppGraph.monitor.isActive) {
            val template = AppGraph.overrideRepo.template(o.purposeId)
            AppGraph.monitor.start(o, template, AppGraph.settings.snapshot(), now, AppGraph.accessibility?.frontPackage)
            AppGraph.monitor.onScreenState((getSystemService(POWER_SERVICE) as PowerManager).isInteractive)
            AppGraph.liveUsage.refresh(counts = false, now = now)
        }
        current = o
        ending = false
        graceSec = AppGraph.settings.snapshot().warningGraceSec
        updateNotification(now, force = true)
        if (ticker?.isActive != true) ticker = scope.launch { tickLoop() }
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            runCatching { tickOnce(System.currentTimeMillis()) }.onFailure { AppLog.w(TAG, "tick failed", it) }
            if (current == null) break
            delay(1_000L)
        }
    }

    private suspend fun tickOnce(now: Long) {
        val o = current ?: return
        val monitor = AppGraph.monitor
        val result = monitor.tick(now)
        monitor.takeSample()?.let { AppGraph.overrideRepo.insertSample(it) }
        when (result) {
            TickResult.Timeout -> { timeout(o); return }
            is TickResult.Strike -> { strike(o, result); return }
            TickResult.Warn -> { syncCounters(); Notifications.showWarning(this, o.statement) }
            TickResult.AskCheckin -> { syncCounters(); Notifications.showCheckin(this, o.statement) }
            TickResult.Nothing -> Unit
        }
        renderBanner(now, o)
        updateNotification(now, force = false)
        val minutesLeft = minutesLeft(o, now)
        AppGraph.overlay.showOrUpdateBubble(minutesLeft) { send(this, ACTION_DONE) }
    }

    private fun renderBanner(now: Long, o: Override) {
        val overlay = AppGraph.overlay
        val grace = graceSec
        when (val b = AppGraph.monitor.bannerState(now)) {
            BannerState.None -> if (overlay.hasBanner) overlay.hideBanner()
            is BannerState.Warning -> overlay.showBanner(BannerKind.WARNING, o.statement, b.secondsLeft, grace, ::onBannerPrimary) {
                send(this, ACTION_CONFESS)
            }
            is BannerState.Checkin -> overlay.showBanner(BannerKind.CHECKIN, o.statement, b.secondsLeft, grace, ::onBannerPrimary) {
                send(this, ACTION_CONFESS)
            }
        }
    }

    private fun onBannerPrimary(kind: BannerKind) {
        send(this, if (kind == BannerKind.WARNING) ACTION_BACK_TO_IT else ACTION_CHECKIN_YES)
        AppGraph.overlay.hideBanner()
    }

    private fun minutesLeft(o: Override, now: Long) =
        (((o.startedAt + o.plannedMin * 60_000L) - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)

    private suspend fun syncCounters() {
        val o = current ?: return
        val m = AppGraph.monitor
        AppGraph.overrideRepo.updateCounters(o.id, m.warningCount, m.checkinsAsked, m.checkinsAnswered)
    }

    private fun updateNotification(now: Long, force: Boolean) {
        val o = current ?: return
        if (!force && now - lastNotifAt < 15_000L) return
        lastNotifAt = now
        Notifications.updateOverride(this, o, now)
    }

    private suspend fun timeout(o: Override) {
        if (ending) return
        ending = true
        syncCounters()
        AppGraph.overrideRepo.end(EndReason.TIMEOUT, System.currentTimeMillis())?.let { outcome ->
            if (outcome.forgaveStrike) toast(R.string.toast_forgiven)
        }
        Notifications.showTimesUp(this, o.statement)
        cleanupAfterEnd()
        AppGraph.accessibility?.reevaluateFront(timesUp = true)
        stopAll()
    }

    private suspend fun strike(o: Override, s: TickResult.Strike) {
        if (ending) return
        ending = true
        syncCounters()
        val result = AppGraph.ladder.record(o.id, o.packageName, s.reason, System.currentTimeMillis())
        cleanupAfterEnd()
        AppGraph.overlay.showLockout(OverlayTexts.lockoutInfo(this, result)) {
            AppGraph.accessibility?.goHome()
            AppGraph.overlay.removeFull()
        }
        AppGraph.accessibility?.goHome()
        stopAll()
    }

    /** DONE / CONFESSED: always free. [goHome] is false when an Extend replaces this override. */
    private suspend fun finish(reason: EndReason, goHome: Boolean) {
        if (ending) return
        ending = true
        syncCounters()
        val outcome = AppGraph.overrideRepo.end(reason, System.currentTimeMillis())
        cleanupAfterEnd()
        if (goHome) AppGraph.accessibility?.goHome()
        if (outcome != null) {
            toast(if (reason == EndReason.CONFESSED) R.string.toast_confessed else R.string.toast_done)
            if (outcome.forgaveStrike) toast(R.string.toast_forgiven)
        }
        stopAll()
    }

    private fun cleanupAfterEnd() {
        AppGraph.monitor.stop()
        AppGraph.liveUsage.refresh(counts = true, now = System.currentTimeMillis())
        AppGraph.overlay.hideBanner()
        AppGraph.overlay.hideBubble()
        Notifications.cancelWarning(this)
        current = null
    }

    private fun toast(res: Int) = Toast.makeText(applicationContext, res, Toast.LENGTH_LONG).show()

    private fun stopAll() {
        current = null
        ticker?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        AppGraph.overlay.hideBanner()
        AppGraph.overlay.hideBubble()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HL/Svc"
        const val ACTION_START = "com.honestlockout.app.action.START"
        const val ACTION_RESUME = "com.honestlockout.app.action.RESUME"
        const val ACTION_DONE = "com.honestlockout.app.action.DONE"
        const val ACTION_DONE_FOR_EXTEND = "com.honestlockout.app.action.DONE_FOR_EXTEND"
        const val ACTION_CONFESS = "com.honestlockout.app.action.CONFESS"
        const val ACTION_BACK_TO_IT = "com.honestlockout.app.action.BACK_TO_IT"
        const val ACTION_CHECKIN_YES = "com.honestlockout.app.action.CHECKIN_YES"
        const val ACTION_STOP_SILENT = "com.honestlockout.app.action.STOP_SILENT"

        @Volatile var running = false
            private set

        fun intent(context: Context, action: String) =
            Intent(context, OverrideForegroundService::class.java).setAction(action)

        fun send(context: Context, action: String) {
            runCatching { ContextCompat.startForegroundService(context, intent(context, action)) }
                .onFailure { AppLog.w(TAG, "startForegroundService($action) failed", it) }
        }

        fun resume(context: Context) = send(context, ACTION_RESUME)
    }
}
