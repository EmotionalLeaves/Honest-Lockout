package com.honestlockout.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.MainActivity
import com.honestlockout.app.R
import com.honestlockout.app.data.repo.Eligibility
import com.honestlockout.app.data.rules.ScreenRulesLoader
import com.honestlockout.app.engine.Decision
import com.honestlockout.app.engine.DayClock
import com.honestlockout.app.engine.EmergencyAllowlist
import com.honestlockout.app.overlay.LockInfo
import com.honestlockout.app.overlay.OverlayController
import com.honestlockout.app.overlay.OverlayTexts
import com.honestlockout.app.ui.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The eyes and hands: tracks the front app, draws the lock overlay, feeds OverrideMonitor and runs
 * the settings guard. Holds no business rules (those live in engine/). Every handler is wrapped
 * in runCatching and logs with tag HL/Acc.
 */
class LockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var guard: SettingsGuard
    private lateinit var inspector: ScreenInspector
    /** Never front-app changes: system UI, "android", the keyboard. Launchers are tracked separately. */
    private val ignorePackages = HashSet<String>()
    /** Home screens plus their companions (search box, assistant, Samsung Free): never "another app". */
    private var launchers: Set<String> = emptySet()
    /** pkg/class -> is a real Activity. Non-activity windows (popups, overlays, dialogs) never change the front app. */
    private val activityCache = HashMap<String, Boolean>()
    private val lastContentAt = HashMap<String, Long>()
    private var lastRecheckAt = 0L
    @Volatile private var offRecorded = false
    private val tamper by lazy { TamperWatch(this, scope) }

    /** The last real front app (launcher included). */
    @Volatile var frontPackage: String? = null
        private set

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val on = i.action == Intent.ACTION_SCREEN_ON
            AppGraph.liveUsage.onScreen(on, System.currentTimeMillis())
            AppGraph.monitor.onScreenState(on)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val now = System.currentTimeMillis()
        offRecorded = false
        AppGraph.accessibility = this
        AppGraph.accessibilityConnected = true
        AppGraph.overlay.attachService(this)
        guard = SettingsGuard(this).also { it.connectedAt = now }
        inspector = ScreenInspector(this)
        refreshIgnoreSet()
        EmergencyAllowlist.refreshDefaults(this)
        AppGraph.monitor.isIgnored = { it in launchers || it == packageName || it in TRANSIENT_SYSTEM }
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        tamper.watchConditions()
        guard.watch(scope)
        scope.launch { safe("connect") { onConnected(now) } }
        scope.launch {
            while (isActive) {
                safe("heartbeat") {
                    val t = System.currentTimeMillis()
                    AppGraph.settings.setLastServiceSeen(t)
                    guard.refreshActive(t)
                    refreshIgnoreSet()
                    // Watchdog: an active override must always have its clock running.
                    if (!AppGraph.overrideRepo.endIfExpired(t) && AppGraph.overrideRepo.getActive() != null &&
                        !OverrideForegroundService.running
                    ) OverrideForegroundService.resume(this@LockAccessibilityService)
                }
                delay(HEARTBEAT_MS)
            }
        }
        AppLog.w(TAG, "Accessibility service connected")
    }

    private suspend fun onConnected(now: Long) {
        tamper.onConnected(now)
        // Turned off by the user: the strike lands now, so the user sees it.
        AppGraph.ladder.recordTamper(now)?.let { result ->
            AppGraph.overlay.showLockout(OverlayTexts.lockoutInfo(this, result)) { goHome(); AppGraph.overlay.removeFull() }
        }
        // Seed today's budget counter: system usage today minus time spent under overrides.
        val seeded = withContext(Dispatchers.IO) {
            val start = DayClock.startOfDay(now)
            val sys = AppGraph.usageRepo.foregroundMs(start, now)
            val ovr = AppGraph.overrideRepo.overrideMsSince(start, now)
            sys.mapValues { (pkg, ms) -> (ms - (ovr[pkg] ?: 0L)).coerceAtLeast(0L) }
        }
        AppGraph.liveUsage.seed(seeded, now)
        guard.refreshActive(now)
        // Resume monitoring if an override survived a process restart.
        if (AppGraph.overrideRepo.getActive() != null && !OverrideForegroundService.running) {
            OverrideForegroundService.resume(this)
        }
    }

    private fun refreshIgnoreSet() {
        launchers = runCatching {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        val ime = runCatching { Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }
            .getOrNull()?.substringBefore('/')
        val assistant = runCatching { Settings.Secure.getString(contentResolver, "assistant") }.getOrNull()?.substringBefore('/')
        launchers = launchers + HOME_COMPANIONS + listOfNotNull(assistant)
        ignorePackages.clear()
        ignorePackages += setOf("com.android.systemui", "android")
        ime?.let { ignorePackages += it }
        ignorePackages -= launchers
    }

    /** True when [className] is an Activity of [pkg] (cached). Overlays and popups report widget classes. */
    private fun isActivity(pkg: String, className: String?): Boolean {
        val cls = className ?: return false
        if (!cls.contains('.') || cls.startsWith("android.widget.") || cls.startsWith("android.view.") ||
            cls.startsWith("android.app.Dialog") || cls.startsWith("androidx.") || cls.startsWith("android.inputmethodservice.")
        ) return false
        return activityCache.getOrPut("$pkg/$cls") {
            runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0); true }.getOrDefault(false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        runCatching {
            val pkg = e.packageName?.toString() ?: return
            when (e.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                    onWindowState(pkg, e.className?.toString(), e.text?.joinToString(" "))
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> onContent(pkg)
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> if (watches(pkg)) AppGraph.monitor.onTyping()
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (watches(pkg)) AppGraph.monitor.onScroll()
                AccessibilityEvent.TYPE_VIEW_CLICKED -> if (watches(pkg)) AppGraph.monitor.onClick()
            }
        }.onFailure { AppLog.w(TAG, "event ${e.eventType} failed", it) }
    }

    private fun watches(pkg: String) =
        AppGraph.monitor.isActive && pkg == frontPackage && AppGraph.monitor.watchesPackage(pkg)

    /** Step 1 (and 6): a window came to the front. */
    private fun onWindowState(pkg: String, className: String?, title: String?) {
        val now = System.currentTimeMillis()
        if (pkg != packageName) inspector.maybeDump(now, force = true)
        if (pkg in ignorePackages) return
        // Our own overlay windows are not front-app changes; MainActivity is.
        if (pkg == packageName && className != MainActivity::class.java.name) return
        // A popup, dialog or overlay from some other package (e.g. the launcher's search box right
        // after HOME) is not a new front app. If it really is a new app whose first window is not an
        // Activity, the active-window check below catches it a moment later.
        if (pkg != frontPackage && pkg !in launchers && className != "$pkg.ActiveWindow" && !isActivity(pkg, className)) {
            main.postDelayed({ confirmFront(pkg) }, ACTIVE_WINDOW_CHECK_MS)
            return
        }
        val monitor = AppGraph.monitor
        if (pkg != frontPackage) {
            val covered = monitor.isActive && monitor.isAllowedPackage(pkg) && !EmergencyAllowlist.contains(pkg)
            AppGraph.liveUsage.onFront(pkg, counts = !covered, now = now)
        }
        frontPackage = pkg
        monitor.onFrontApp(pkg)

        val overlay = AppGraph.overlay
        if (overlay.fullKind == OverlayController.FullKind.CHANGE_QUEUED && !guard.isSettingsPackage(pkg, title)) {
            overlay.removeFull() // the guard only covers Settings pages
        } else if (pkg == packageName) {
            overlay.removeFull() // our own app is never covered: it's where you unlock or change rules
        } else if (overlay.hasFullScreen && pkg !in launchers) {
            val sameLock = overlay.fullKind == OverlayController.FullKind.LOCK && overlay.fullPackage == pkg
            if (!sameLock) overlay.removeFull()
        }
        if (guard.isSettingsPackage(pkg, title)) {
            guard.check(pkg, now)
            if (guard.isSystemSettings(pkg)) return
        }
        if (pkg == packageName || pkg in launchers || EmergencyAllowlist.contains(pkg)) return
        scope.launch { safe("decide") { decideAndAct(pkg, headline = null) } }
    }

    /** Step 2 (and 6): content of the front window changed. Throttled to once per 500 ms per package. */
    private fun onContent(pkg: String) {
        val now = System.currentTimeMillis()
        if ((lastContentAt[pkg] ?: 0L) + CONTENT_THROTTLE_MS > now) return
        lastContentAt[pkg] = now
        if (pkg != packageName) inspector.maybeDump(now, force = false)
        if (pkg != frontPackage) return
        if (guard.isSystemSettings(pkg)) { guard.check(pkg, now); return }
        val monitor = AppGraph.monitor
        if (monitor.isActive && monitor.watchesPackage(pkg)) {
            val root = runCatching { rootInActiveWindow }.getOrNull()
            if (root != null && root.packageName?.toString() == pkg) {
                val title = runCatching { windows.firstOrNull { it.isActive }?.title }.getOrNull()
                monitor.onScreen(AppGraph.screenRules.matchingLabels(root, pkg, title))
            }
            root?.let { ScreenRulesLoader.recycle(it) }
        }
        // A budget or schedule may run out while the app is in front: decide again.
        if (!AppGraph.overlay.hasFullScreen && now - lastRecheckAt > RECHECK_MS && pkg != packageName &&
            pkg !in launchers && !EmergencyAllowlist.contains(pkg)
        ) {
            lastRecheckAt = now
            scope.launch { safe("recheck") { decideAndAct(pkg, headline = null) } }
        }
    }

    /** Runs BlockDecider and, on a Lock decision, shows the lock overlay and goes HOME 300 ms later. */
    private suspend fun decideAndAct(pkg: String, headline: String?) {
        val now = System.currentTimeMillis()
        val decision = AppGraph.decider.decide(pkg, now)
        if (decision !is Decision.Lock || frontPackage != pkg) return
        val overlay = AppGraph.overlay
        if (headline == null && overlay.fullKind == OverlayController.FullKind.LOCK && overlay.fullPackage == pkg) return
        if (AppGraph.monitor.isActive) AppGraph.monitor.onBlockedAttempt(pkg)
        val rule = AppGraph.blockRepo.getRule(pkg)
        val refusal = (AppGraph.overrideRepo.eligibility(pkg, null, now) as? Eligibility.Refused)?.message
        val info = LockInfo(
            packageName = pkg,
            appName = Format.appLabel(this, pkg),
            headline = headline,
            reasonLine = OverlayTexts.lockReasonLine(this, decision, rule?.dailyBudgetMin ?: 0),
            nextOverrideLine = OverlayTexts.nextOverrideLine(this, AppGraph.strikeRepo.activeGlobalLockout(now)?.until),
            refusal = refusal,
        )
        if (frontPackage != pkg) return // the user moved on (maybe to an emergency app) meanwhile
        overlay.showLock(
            info,
            onOverride = {
                overlay.removeFull()
                startActivity(MainActivity.routeIntent(this, MainActivity.overrideRoute(pkg)))
            },
            onClose = { goHome(); overlay.removeFull() },
        )
        Haptics.tap(this)
        main.postDelayed({ if (frontPackage == pkg || frontPackage in launchers) goHome() }, HOME_DELAY_MS)
        AppLog.w(TAG, "Locked $pkg: ${decision.reason}")
    }

    /** Fallback for apps whose first window is not an Activity: trust the active window's package. */
    private fun confirmFront(pkg: String) {
        if (pkg == frontPackage) return
        val active = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
        if (active == pkg) onWindowState(pkg, "$pkg.ActiveWindow", null)
    }

    /** After a TIMEOUT the app in front locks again, with "Time's up". */
    fun reevaluateFront(timesUp: Boolean) {
        val pkg = frontPackage ?: return
        if (pkg == packageName || pkg in launchers) return
        scope.launch { safe("reevaluate") { decideAndAct(pkg, if (timesUp) getString(R.string.lock_times_up) else null) } }
    }

    fun goHome() { performGlobalAction(GLOBAL_ACTION_HOME) }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        recordServiceOff()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        recordServiceOff()
        runCatching { unregisterReceiver(screenReceiver) }
        AppGraph.overlay.detachService()
        scope.cancel()
        super.onDestroy()
    }

    private fun recordServiceOff() {
        if (offRecorded) return
        offRecorded = true
        AppGraph.accessibilityConnected = false
        if (AppGraph.accessibility === this) AppGraph.accessibility = null
        tamper.onServiceOff(System.currentTimeMillis())
    }

    private suspend fun safe(what: String, block: suspend () -> Unit) {
        runCatching { block() }.onFailure { AppLog.w(TAG, "$what failed", it) }
    }

    companion object {
        private const val TAG = "HL/Acc"
        private const val HEARTBEAT_MS = 60_000L
        private const val CONTENT_THROTTLE_MS = 500L
        private const val RECHECK_MS = 1_500L
        private const val HOME_DELAY_MS = 300L
        private const val ACTIVE_WINDOW_CHECK_MS = 400L

        /** Packages that draw over the home screen and belong to it. */
        private val HOME_COMPANIONS = setOf(
            "com.google.android.googlequicksearchbox", "com.samsung.android.app.spage", "com.samsung.android.bixby.agent",
        )

        /**
         * System pickers and dialogs an override's task can legitimately pass through (share
         * sheet, permission prompts, photo/document pickers, account chooser, installer).
         * They never count as leaving the declared app.
         */
        private val TRANSIENT_SYSTEM = setOf(
            "com.android.intentresolver", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.providers.media.module", "com.google.android.providers.media.module",
            "com.android.documentsui", "com.google.android.documentsui", "com.google.android.gms",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
        )
    }
}
