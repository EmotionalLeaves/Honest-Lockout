package com.honestlockout.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.MainActivity
import com.honestlockout.app.R
import com.honestlockout.app.data.db.entities.TamperEvent
import com.honestlockout.app.data.rules.ScreenRulesLoader
import com.honestlockout.app.engine.Decision
import com.honestlockout.app.engine.DayClock
import com.honestlockout.app.engine.EmergencyAllowlist
import com.honestlockout.app.overlay.OverlayController
import com.honestlockout.app.overlay.OverlayTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The eyes and hands: watches the front app, draws the lock overlay, feeds OverrideMonitor and
 * runs the settings guard. Holds no business rules (those live in engine/).
 * Every handler is wrapped in runCatching and logs with tag HL/Acc.
 */
class LockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var guard: SettingsGuard
    private val ignorePackages = HashSet<String>()   // launcher(s), system UI, IME, "android"
    private var launchers: Set<String> = emptySet()
    private var lastContentAt = HashMap<String, Long>()
    private var lastRecheckAt = 0L
    private var lastInspectAt = 0L
    @Volatile var frontPackage: String? = null
        private set

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val on = i.action == Intent.ACTION_SCREEN_ON
            val now = System.currentTimeMillis()
            AppGraph.liveUsage.onScreen(on, now)
            AppGraph.monitor.onScreenState(on)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val now = System.currentTimeMillis()
        AppGraph.accessibility = this
        AppGraph.accessibilityConnected = true
        AppGraph.overlay.attachService(this)
        guard = SettingsGuard(this).also { it.connectedAt = now }
        refreshIgnoreSet()
        EmergencyAllowlist.refreshDefaults(this)
        AppGraph.monitor.isIgnored = { it in launchers || it == packageName }
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { safe("connect") { onConnected(now) } }
        scope.launch {
            while (isActive) {
                safe("heartbeat") {
                    AppGraph.settings.setLastServiceSeen(System.currentTimeMillis())
                    guard.refreshActive(System.currentTimeMillis())
                    refreshIgnoreSet()
                }
                delay(60_000L)
            }
        }
        AppLog.w(TAG, "Accessibility service connected")
    }

    private suspend fun onConnected(now: Long) {
        val s = AppGraph.settings.snapshot()
        // Killed by the OS (no onUnbind): logged, never punished.
        if (s.setupComplete && s.lastServiceSeen > 0 && now - s.lastServiceSeen > 10 * 60_000L &&
            AppGraph.strikeRepo.unpunishedServiceOff().isEmpty() && !AppGraph.strikeRepo.rebootSince(s.lastServiceSeen)
        ) {
            AppGraph.strikeRepo.recordTamperEvent(TamperEvent.SERVICE_KILLED, now)
            AppLog.w(TAG, "Service was killed while away; logged without a strike")
        }
        AppGraph.settings.setLastServiceSeen(now)
        // Toggled off by the user: the strike lands now, so the user sees it.
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
        ignorePackages.clear()
        ignorePackages += launchers
        ignorePackages += setOf("com.android.systemui", "android")
        ime?.let { ignorePackages += it }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        runCatching {
            val pkg = e.packageName?.toString() ?: return
            when (e.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowState(pkg, e.className?.toString())
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> onContent(pkg)
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> if (watches(pkg)) AppGraph.monitor.onTyping()
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (watches(pkg)) AppGraph.monitor.onScroll()
                AccessibilityEvent.TYPE_VIEW_CLICKED -> if (watches(pkg)) AppGraph.monitor.onClick()
            }
        }.onFailure { AppLog.w(TAG, "event ${event.eventType} failed", it) }
    }

    private fun watches(pkg: String) = AppGraph.monitor.isActive && pkg == frontPackage && AppGraph.monitor.watchesPackage(pkg)

    private fun onWindowState(pkg: String, className: String?) {
        val now = System.currentTimeMillis()
        if (AppGraph.inspector.isActive(now) && pkg != packageName) inspect(now, force = true)
        // System UI, keyboards and our own overlay windows are not front-app changes.
        if (pkg in ignorePackages && pkg !in launchers) return
        if (pkg == packageName && className != MainActivity::class.java.name) return
        val changed = pkg != frontPackage
        frontPackage = pkg
        val monitor = AppGraph.monitor
        if (changed) {
            val covered = monitor.isActive && monitor.isAllowedPackage(pkg) && !com.honestlockout.app.engine.EmergencyAllowlist.contains(pkg)
            AppGraph.liveUsage.onFront(pkg, counts = !covered, now = now)
        }
        monitor.onFrontApp(pkg)
        val overlay = AppGraph.overlay
        if (overlay.hasFullScreen && pkg !in launchers && pkg != packageName) {
            val keep = overlay.fullKind == OverlayController.FullKind.LOCK && overlay.fullPackage == pkg
            if (!keep) overlay.removeFull()
        }
        if (guard.isSettingsPackage(pkg, null)) { guard.check(pkg, now); return }
        if (pkg == packageName || pkg in launchers || EmergencyAllowlist.contains(pkg)) return
        scope.launch { safe("decide") { decideAndAct(pkg, headline = null) } }
    }

    private fun onContent(pkg: String) {
        val now = System.currentTimeMillis()
        if ((lastContentAt[pkg] ?: 0L) + 500L > now) return
        lastContentAt[pkg] = now
        if (AppGraph.inspector.isActive(now) && pkg != packageName) inspect(now, force = false)
        if (pkg != frontPackage) return
        if (guard.isSettingsPackage(pkg, null)) { guard.check(pkg, now); return }
        val monitor = AppGraph.monitor
        if (monitor.isActive && monitor.watchesPackage(pkg)) {
            val root = runCatching { rootInActiveWindow }.getOrNull()
            if (root != null && root.packageName?.toString() == pkg) {
                val title = runCatching { windows.firstOrNull { it.isActive }?.title }.getOrNull()
                monitor.onScreen(AppGraph.screenRules.matchesAny(root, pkg, title))
            }
            root?.let { ScreenRulesLoader.recycle(it) }
        }
        // Budget or schedule may have run out while the app is in front.
        if (!AppGraph.overlay.hasFullScreen && now - lastRecheckAt > 1_500L && pkg != packageName &&
            pkg !in launchers && !EmergencyAllowlist.contains(pkg)
        ) {
            lastRecheckAt = now
            scope.launch { safe("recheck") { decideAndAct(pkg, headline = null) } }
        }
    }

    /** Runs BlockDecider and shows the lock overlay on a Lock decision. */
    private suspend fun decideAndAct(pkg: String, headline: String?) {
        val now = System.currentTimeMillis()
        val decision = AppGraph.decider.decide(pkg, now)
        if (decision !is Decision.Lock) return
        if (frontPackage != pkg) return
        val overlay = AppGraph.overlay
        if (overlay.fullKind == OverlayController.FullKind.LOCK && overlay.fullPackage == pkg && headline == null) return
        if (AppGraph.monitor.isActive) AppGraph.monitor.onBlockedAttempt(pkg)
        val rule = AppGraph.blockRepo.getRule(pkg)
        val eligibility = AppGraph.overrideRepo.eligibility(pkg, null, now)
        val refusal = (eligibility as? com.honestlockout.app.data.repo.Eligibility.Refused)?.message
        val info = com.honestlockout.app.overlay.LockInfo(
            packageName = pkg,
            appName = com.honestlockout.app.ui.Format.appLabel(this, pkg),
            headline = headline,
            reasonLine = OverlayTexts.lockReasonLine(this, decision, rule?.dailyBudgetMin ?: 0),
            nextOverrideLine = OverlayTexts.nextOverrideLine(this, AppGraph.strikeRepo.activeGlobalLockout(now)?.until),
            refusal = refusal,
        )
        overlay.showLock(
            info,
            onOverride = {
                overlay.removeFull()
                startActivity(MainActivity.routeIntent(this, MainActivity.overrideRoute(pkg)))
            },
            onClose = { goHome(); overlay.removeFull() },
        )
        main.postDelayed({ goHome() }, 300L)
        AppLog.w(TAG, "Locked $pkg: ${decision.reason}")
    }

    /** Called when an override ends by TIMEOUT so the app in front locks with "Time's up". */
    fun reevaluateFront(timesUp: Boolean) {
        val pkg = frontPackage ?: return
        if (pkg == packageName || pkg in launchers) return
        scope.launch { safe("reevaluate") { decideAndAct(pkg, if (timesUp) getString(R.string.lock_times_up) else null) } }
    }

    fun goHome() { performGlobalAction(GLOBAL_ACTION_HOME) }

    private fun inspect(now: Long, force: Boolean) {
        if (!force && now - lastInspectAt < 1_000L) return
        lastInspectAt = now
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg == packageName) { ScreenRulesLoader.recycle(root); return }
        val lines = ArrayList<String>()
        ScreenRulesLoader.walk(root) { n ->
            val id = n.viewIdResourceName
            val text = n.text?.toString()?.take(60)
            val desc = n.contentDescription?.toString()?.take(60)
            if (id != null || !text.isNullOrBlank() || !desc.isNullOrBlank()) {
                lines += buildString {
                    append(pkg).append(" | ").append(id ?: "-")
                    if (!text.isNullOrBlank()) append(" | text=").append(text)
                    if (!desc.isNullOrBlank()) append(" | desc=").append(desc)
                    if (n.isSelected) append(" | selected")
                }
            }
        }
        ScreenRulesLoader.recycle(root)
        val fresh = lines.distinct()
        AppGraph.inspector.append(fresh)
        fresh.forEach { Log.i("HL/Inspect", it) }
    }

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

    @Volatile private var offRecorded = false

    /** The user turned the service off while rules exist: a TAMPER strike lands on next connect. */
    private fun recordServiceOff() {
        if (offRecorded) return
        offRecorded = true
        AppGraph.accessibilityConnected = false
        if (AppGraph.accessibility === this) AppGraph.accessibility = null
        runCatching {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(2_000L) {
                    val now = System.currentTimeMillis()
                    val s = AppGraph.settings.snapshot()
                    if (s.setupComplete && now >= s.guardPausedUntil && AppGraph.blockRepo.ruleCount() > 0) {
                        AppGraph.strikeRepo.recordTamperEvent(TamperEvent.SERVICE_OFF, now)
                        AppLog.w(TAG, "Service turned off while rules exist: tamper recorded")
                    }
                }
            }
        }.onFailure { AppLog.w(TAG, "recordServiceOff failed", it) }
    }

    private suspend fun safe(what: String, block: suspend () -> Unit) {
        runCatching { block() }.onFailure { AppLog.w(TAG, "$what failed", it) }
    }

    @Suppress("unused") private fun isScreenOn() = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive

    companion object {
        private const val TAG = "HL/Acc"
    }
}
