package com.honestlockout.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.R
import com.honestlockout.app.data.rules.ScreenRulesLoader
import com.honestlockout.app.data.repo.PendingChangeRepository
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Covers this app's own pages in Settings (App info, Accessibility, Device admin, Uninstall…)
 * while blocking is in force, and queues an "Allow disabling the service" change the first time
 * it fires in a 24 h window. When that change applies, the guard pauses for one hour.
 */
class SettingsGuard(private val service: AccessibilityService) {

    private val appName = service.getString(R.string.app_name).lowercase()
    private val keywords = service.resources.getStringArray(R.array.guard_keywords).map { it.lowercase() }

    private val main = Handler(Looper.getMainLooper())

    @Volatile var connectedAt: Long = System.currentTimeMillis()
    @Volatile private var active = false

    fun isSystemSettings(pkg: String): Boolean = pkg in SYSTEM_SETTINGS

    /** System settings packages, or any window whose title contains "Settings". */
    fun isSettingsPackage(pkg: String, windowTitle: CharSequence?): Boolean =
        isSystemSettings(pkg) || (windowTitle?.toString()?.contains("Settings", ignoreCase = true) == true)

    /**
     * Active while setup is complete, the guard is not paused, and a block rule, a lockout or a
     * pending loosening exists. Recomputed whenever those tables change and once a minute (for
     * lockouts and the pause running out).
     */
    suspend fun refreshActive(now: Long) {
        val s = AppGraph.settings.snapshot()
        active = s.setupComplete && now >= s.guardPausedUntil &&
            (AppGraph.blockRepo.ruleCount() > 0 || AppGraph.strikeRepo.anyLockoutActive(now) ||
                AppGraph.pendingRepo.hasPendingLoosening())
    }

    fun watch(scope: CoroutineScope) {
        scope.launch {
            merge(
                AppGraph.settings.data.map { }, AppGraph.blockRepo.rules().map { },
                AppGraph.strikeRepo.lockouts().map { }, AppGraph.pendingRepo.pending().map { },
            ).collect { refreshActive(System.currentTimeMillis()) }
        }
    }

    /** Returns true if the page was covered. Must run on the main thread. */
    fun check(pkg: String, now: Long): Boolean {
        if (!active || now - connectedAt < GRACE_AFTER_CONNECT_MS || now < AppGraph.guardSuppressedUntil) return false
        val root = settingsRoot(pkg) ?: return false
        val texts = StringBuilder()
        ScreenRulesLoader.walk(root) { node ->
            node.text?.let { texts.append(it).append('\n') }
            node.contentDescription?.let { texts.append(it).append('\n') }
        }
        ScreenRulesLoader.recycle(root)
        val all = texts.toString().lowercase()
        val hit = all.contains(appName) && keywords.any { all.contains(it) }
        val overlay = AppGraph.overlay
        if (hit) {
            overlay.showChangeQueued {
                // Remove the (focused) overlay first so the BACK reaches Settings, not the overlay.
                overlay.removeFull()
                main.postDelayed({ service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }, 120L)
            }
            queueAllowDisableIfNeeded(now)
        } else if (overlay.fullKind == OverlayController.FullKind.CHANGE_QUEUED) {
            overlay.removeFull()
        }
        return hit
    }

    /** The settings window's root, even when our own overlay is the active window. */
    private fun settingsRoot(pkg: String): AccessibilityNodeInfo? {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val r = runCatching { w.root }.getOrNull() ?: continue
            if (r.packageName?.toString() == pkg) return r
            ScreenRulesLoader.recycle(r)
        }
        return runCatching { service.rootInActiveWindow }.getOrNull()?.takeIf { it.packageName?.toString() == pkg }
    }

    private fun queueAllowDisableIfNeeded(now: Long) {
        AppGraph.appScope.launch {
            val s = AppGraph.settings.snapshot()
            if (now - s.lastAllowDisableRequest < DAY_MS) return@launch
            val already = AppGraph.pendingRepo.pendingOnce().any { PendingChangeRepository.isAllowDisable(it) }
            AppGraph.settings.setLastAllowDisableRequest(now)
            if (already) return@launch
            val guard = AppGraph.loosenGuard
            guard.queue(
                SettingChange.AllowDisable, guard.describe(SettingChange.AllowDisable), now,
                now + s.loosenDelayHours * 3_600_000L,
            )
            AppLog.w("HL/Acc", "Settings guard fired: queued AllowDisable")
        }
    }

    companion object {
        private val SYSTEM_SETTINGS = setOf("com.android.settings", "com.samsung.android.settings")
        private const val GRACE_AFTER_CONNECT_MS = 2 * 60_000L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

