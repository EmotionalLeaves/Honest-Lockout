package com.honestlockout.app.data.rules

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.honestlockout.app.AppLog
import com.honestlockout.app.data.db.ScreenAction
import com.honestlockout.app.data.db.daos.ScreenRuleDao
import com.honestlockout.app.data.db.entities.ScreenRule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RuleFile(val version: Int = 1, val rules: List<RuleJson> = emptyList())

@Serializable
private data class RuleJson(
    @SerialName("package") val pkg: String,
    val label: String,
    val viewId: String? = null,
    val text: String? = null,
    val action: String = "BLOCK",
)

/**
 * Loads assets/screen_rules.json into the ScreenRule table (cleared and refilled on every app
 * start and on "Reload from file") and matches rules against a live accessibility node tree.
 */
class ScreenRulesLoader(private val context: Context, private val dao: ScreenRuleDao) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var byPackage: Map<String, List<ScreenRule>> = emptyMap()

    suspend fun reloadFromAssets(): Int {
        val rules = runCatching {
            val text = context.assets.open("screen_rules.json").bufferedReader().use { it.readText() }
            json.decodeFromString(RuleFile.serializer(), text).rules.map {
                ScreenRule(
                    packageName = it.pkg, matchViewId = it.viewId, matchText = it.text, label = it.label,
                    action = runCatching { ScreenAction.valueOf(it.action) }.getOrDefault(ScreenAction.BLOCK),
                )
            }
        }.onFailure { AppLog.w("HL/Engine", "screen_rules.json failed to parse: ${it.message}") }
            .getOrDefault(emptyList())
        dao.clear()
        dao.insertAll(rules)
        refreshCache()
        AppLog.w("HL/Engine", "Loaded ${rules.size} screen rules")
        return rules.size
    }

    suspend fun refreshCache() {
        byPackage = dao.getAllOnce().groupBy { it.packageName }
    }

    fun hasRulesFor(packageName: String): Boolean = byPackage.containsKey(packageName)

    /** Distinct labels across all rules, for the Custom purpose and the template editor. */
    fun allLabels(): List<String> = byPackage.values.flatten().map { it.label }.distinct().sorted()

    /**
     * Returns the labels of every BLOCK rule that matches [root] (viewId matches first).
     * viewId rules are checked first (strong); text rules are the fallback (weak): a text rule
     * matches only a node whose text/contentDescription equals it (case-insensitive) and that is
     * selected, or the window title. The walk is capped at [MAX_NODES]; child nodes are recycled.
     *
     * Choice (spec rule 1): a viewId counts only when its node is visible to the user, and an id
     * ending in "_tab" counts only when that tab is selected, because tab buttons such as
     * Instagram's feed_tab are always present in the window.
     */
    fun matchingLabels(root: AccessibilityNodeInfo?, packageName: String, windowTitle: CharSequence? = null): Set<String> {
        val rules = byPackage[packageName]?.filter { it.action == ScreenAction.BLOCK } ?: return emptySet()
        if (root == null || rules.isEmpty()) return emptySet()
        val viewIds = HashSet<String>()
        val selectedTexts = HashSet<String>()
        windowTitle?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { selectedTexts += it }
        walk(root) { node ->
            node.viewIdResourceName?.let { id ->
                val isTab = id.endsWith("_tab")
                if (node.isVisibleToUser && (!isTab || node.isSelected)) viewIds += id
            }
            if (node.isSelected) {
                node.text?.toString()?.trim()?.lowercase()?.let { selectedTexts += it }
                node.contentDescription?.toString()?.trim()?.lowercase()?.let { selectedTexts += it }
            }
        }
        val strong = rules.filter { it.matchViewId != null && it.matchViewId in viewIds }.map { it.label }
        val weak = rules.filter { it.matchText != null && it.matchText.trim().lowercase() in selectedTexts }.map { it.label }
        return LinkedHashSet<String>().apply { addAll(strong); addAll(weak) }
    }

    /** The first matching label (viewId matches first), or null. */
    fun matchesAny(root: AccessibilityNodeInfo?, packageName: String, windowTitle: CharSequence? = null): String? =
        matchingLabels(root, packageName, windowTitle).firstOrNull()

    companion object {
        const val MAX_NODES = 400

        /** Breadth-first walk over at most [limit] nodes. The root itself is not recycled here. */
        fun walk(root: AccessibilityNodeInfo, limit: Int = MAX_NODES, visit: (AccessibilityNodeInfo) -> Unit) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < limit) {
                val node = queue.removeFirst()
                count++
                runCatching { visit(node) }
                for (i in 0 until node.childCount) {
                    if (count + queue.size >= limit) break
                    runCatching { node.getChild(i) }.getOrNull()?.let { queue.add(it) }
                }
                if (node !== root) recycle(node)
            }
            queue.forEach { if (it !== root) recycle(it) }
        }

        @Suppress("DEPRECATION")
        fun recycle(node: AccessibilityNodeInfo) {
            // recycle() is a no-op from API 33; still required below it.
            if (Build.VERSION.SDK_INT < 33) runCatching { node.recycle() }
        }
    }
}
