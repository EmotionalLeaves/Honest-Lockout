package com.honestlockout.app.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import com.honestlockout.app.AppLog
import com.honestlockout.app.R

/**
 * Owns every overlay window: at most one full-screen view (lock, lockout or change queued) and at
 * most one banner (warning / check-in) at a time, plus the optional timer bubble. Showing a new
 * one replaces the old. Full-screen views use TYPE_ACCESSIBILITY_OVERLAY through the attached
 * accessibility service; the banner and bubble use TYPE_APPLICATION_OVERLAY when the app may draw
 * over other apps (falling back to the accessibility overlay for the banner).
 * All calls are marshalled onto the main thread.
 */
class OverlayController(private val appContext: Context) {

    enum class FullKind { LOCK, LOCKOUT, CHANGE_QUEUED }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var service: AccessibilityService? = null

    private var fullView: View? = null
    private var fullWm: WindowManager? = null
    @Volatile var fullKind: FullKind? = null
        private set
    @Volatile var fullPackage: String? = null
        private set

    private var banner: WarningBannerView? = null
    private var bannerWm: WindowManager? = null
    private var bubble: TimerBubbleView? = null
    private var bubbleWm: WindowManager? = null

    val hasFullScreen: Boolean get() = fullView != null
    val hasBanner: Boolean get() = banner != null

    fun attachService(s: AccessibilityService) { service = s }

    fun detachService() {
        onMain { removeFullNow(); removeBannerNow() }
        service = null
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) runCatching(block).onFailure { AppLog.w("HL/Acc", "overlay", it) }
        else main.post { runCatching(block).onFailure { AppLog.w("HL/Acc", "overlay", it) } }
    }

    private fun themed(base: Context) = ContextThemeWrapper(base, R.style.Theme_HonestLockout)
    private fun canDrawApp() = Settings.canDrawOverlays(appContext)

    // ---------------------------------------------------------------- full screen

    fun showLock(info: LockInfo, onOverride: () -> Unit, onClose: () -> Unit) = onMain {
        val ctx = service ?: return@onMain
        showFull(ctx, FullKind.LOCK, info.packageName, LockOverlayView(themed(ctx), info, onOverride, onClose))
    }

    fun showLockout(info: LockoutInfo, onOk: () -> Unit) = onMain {
        val ctx: Context = service ?: appContext
        showFull(ctx, FullKind.LOCKOUT, null, LockoutOverlayView(themed(ctx), info, onOk))
    }

    fun showChangeQueued(onGoBack: () -> Unit) = onMain {
        val ctx = service ?: return@onMain
        if (fullKind == FullKind.CHANGE_QUEUED) return@onMain
        showFull(ctx, FullKind.CHANGE_QUEUED, null, ChangeQueuedView(themed(ctx), onGoBack))
    }

    private fun showFull(ctx: Context, kind: FullKind, pkg: String?, view: View) {
        removeFullNow()
        val viaService = ctx is AccessibilityService
        if (!viaService && !canDrawApp()) return
        val type = if (viaService) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, 0, PixelFormat.OPAQUE,
        )
        avoidSystemBars(params)
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        view.alpha = 0f
        wm.addView(view, params)
        view.animate().alpha(1f).setDuration(160L).start()
        view.requestFocus()
        fullView = view; fullWm = wm; fullKind = kind; fullPackage = pkg
        AppLog.w("HL/Acc", "overlay added: $kind $pkg type=$type")
    }

    fun removeFull() = onMain { removeFullNow() }

    private fun removeFullNow() {
        val v = fullView ?: return
        AppLog.w("HL/Acc", "overlay removed: $fullKind $fullPackage", Throwable())
        runCatching { fullWm?.removeViewImmediate(v) }
        fullView = null; fullWm = null; fullKind = null; fullPackage = null
    }

    // ---------------------------------------------------------------- banner

    /** Shows or updates the warning / check-in banner. */
    fun showBanner(kind: BannerKind, statement: String, secondsLeft: Int, graceSec: Int, onPrimary: (BannerKind) -> Unit, onEnd: () -> Unit) = onMain {
        val existing = banner
        if (existing != null && existing.kind == kind) { existing.setSeconds(secondsLeft); return@onMain }
        removeBannerNow()
        // A full-screen lock (TYPE_ACCESSIBILITY_OVERLAY) always sits above application overlays,
        // which would hide the banner. During an override the banner matters more: drop the lock.
        if (fullKind == FullKind.LOCK) removeFullNow()
        val useApp = canDrawApp()
        val ctx: Context = if (useApp) appContext else (service ?: return@onMain)
        val view = WarningBannerView(themed(ctx), onPrimary, onEnd)
        view.bind(kind, statement, secondsLeft, graceSec)
        val height = appContext.resources.displayMetrics.heightPixels / 3
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, height,
            if (useApp) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP; dimAmount = 0.35f }
        avoidSystemBars(params)
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        view.translationY = -height / 3f
        wm.addView(view, params)
        view.animate().translationY(0f).setDuration(220L).start()
        banner = view; bannerWm = wm
    }

    fun hideBanner() = onMain { removeBannerNow() }

    private fun removeBannerNow() {
        val v = banner ?: return
        runCatching { bannerWm?.removeViewImmediate(v) }
        banner = null; bannerWm = null
    }

    // ---------------------------------------------------------------- bubble

    fun showOrUpdateBubble(minutesLeft: Int, onDone: () -> Unit) = onMain {
        if (!canDrawApp()) return@onMain
        bubble?.let { it.setMinutesLeft(minutesLeft); return@onMain }
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = TimerBubbleView(themed(appContext), wm, onDone)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24; y = appContext.resources.displayMetrics.heightPixels / 2
        }
        view.params = params
        view.setMinutesLeft(minutesLeft)
        wm.addView(view, params)
        bubble = view; bubbleWm = wm
    }

    fun hideBubble() = onMain {
        val v = bubble ?: return@onMain
        runCatching { bubbleWm?.removeViewImmediate(v) }
        bubble = null; bubbleWm = null
    }

    fun removeAll() = onMain {
        removeFullNow(); removeBannerNow()
        bubble?.let { runCatching { bubbleWm?.removeViewImmediate(it) } }
        bubble = null; bubbleWm = null
    }

    /** Overlays never cover the status bar or the navigation bar. */
    private fun avoidSystemBars(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= 30) {
            params.fitInsetsTypes = WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        }
    }
}
