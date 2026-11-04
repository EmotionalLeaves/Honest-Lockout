package com.honestlockout.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.honestlockout.app.AppGraph
import com.honestlockout.app.data.rules.ScreenRulesLoader

/**
 * Settings > Screen rules > Inspect current screen. While active (60 s) it lists the
 * viewIdResourceName, text and contentDescription of the front window's nodes, in the app and in
 * logcat (tag HL/Inspect), so the user can fix screen_rules.json from their own phone.
 */
class ScreenInspector(private val service: AccessibilityService) {
    private var lastAt = 0L

    fun maybeDump(now: Long, force: Boolean) {
        if (!AppGraph.inspector.isActive(now)) return
        if (!force && now - lastAt < 1_000L) return
        lastAt = now
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg == service.packageName) { ScreenRulesLoader.recycle(root); return }
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
        fresh.forEach { Log.i(TAG, it) }
    }

    companion object {
        const val TAG = "HL/Inspect"
    }
}
