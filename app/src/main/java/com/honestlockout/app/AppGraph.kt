package com.honestlockout.app

import android.app.Application
import android.util.Log
import com.honestlockout.app.data.db.AppDatabase
import com.honestlockout.app.data.repo.BlockRepository
import com.honestlockout.app.data.repo.OverrideRepository
import com.honestlockout.app.data.repo.PendingChangeRepository
import com.honestlockout.app.data.repo.StrikeRepository
import com.honestlockout.app.data.repo.UsageRepository
import com.honestlockout.app.data.rules.ScreenRulesLoader
import com.honestlockout.app.data.settings.SettingsStore
import com.honestlockout.app.engine.BlockDecider
import com.honestlockout.app.engine.LiveUsageTracker
import com.honestlockout.app.engine.LoosenGuard
import com.honestlockout.app.engine.OverrideMonitor
import com.honestlockout.app.engine.StrikeLadder
import com.honestlockout.app.overlay.OverlayController
import com.honestlockout.app.service.LockAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hand-written dependency holder (no DI framework). Fields are set once in
 * [HonestLockoutApp.onCreate]; services, workers and ViewModels read them directly.
 */
object AppGraph {
    lateinit var app: Application
    lateinit var db: AppDatabase
    lateinit var settings: SettingsStore
    lateinit var screenRules: ScreenRulesLoader
    lateinit var blockRepo: BlockRepository
    lateinit var overrideRepo: OverrideRepository
    lateinit var strikeRepo: StrikeRepository
    lateinit var usageRepo: UsageRepository
    lateinit var pendingRepo: PendingChangeRepository
    lateinit var decider: BlockDecider
    lateinit var monitor: OverrideMonitor
    lateinit var ladder: StrikeLadder
    lateinit var loosenGuard: LoosenGuard
    lateinit var overlay: OverlayController

    /** In-memory foreground minutes per package since local midnight (budget blocking). */
    val liveUsage = LiveUsageTracker()

    /** 2 000-line ring buffer behind the hidden History > Export log item. */
    val log = RingLog(2000)

    /** State of Settings > Screen rules > Inspect current screen. */
    val inspector = InspectorState()

    /**
     * The settings guard stays quiet until this time. Set when this app itself opens a system
     * page that names it (the device-admin activation prompt), so tightening is never covered.
     */
    @Volatile var guardSuppressedUntil: Long = 0L

    @Volatile var accessibilityConnected: Boolean = false
    @Volatile var accessibility: LockAccessibilityService? = null

    /** Process-wide scope for fire-and-forget work started outside a lifecycle. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(application: Application) {
        app = application
        db = AppDatabase.build(application)
        settings = SettingsStore(application)
        screenRules = ScreenRulesLoader(application, db.screenRuleDao())
        blockRepo = BlockRepository(db)
        usageRepo = UsageRepository(application, db)
        pendingRepo = PendingChangeRepository(db)
        strikeRepo = StrikeRepository(db, settings)
        overrideRepo = OverrideRepository(application, db, settings, strikeRepo)
        decider = BlockDecider(db, settings, liveUsage)
        monitor = OverrideMonitor()
        ladder = StrikeLadder(settings, overrideRepo)
        loosenGuard = LoosenGuard(application, db, settings, pendingRepo)
        overlay = OverlayController(application)
        liveUsage.attach(PrefsUsageStore(application), System.currentTimeMillis())
    }
}

/** Every warning log in the app goes through here so it also lands in the ring buffer. */
object AppLog {
    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
        AppGraph.log.add(tag, if (t != null) "$msg: ${t.javaClass.simpleName}: ${t.message}" else msg)
    }
}

class RingLog(private val capacity: Int) {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun add(tag: String, msg: String) {
        if (lines.size >= capacity) lines.removeFirst()
        lines.addLast("${fmt.format(Date())} $tag $msg")
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")
}

/** Screen inspector: while active, the accessibility service logs the front window's nodes. */
class InspectorState {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    @Volatile var activeUntil: Long = 0L
        private set

    fun isActive(now: Long = System.currentTimeMillis()) = now < activeUntil

    fun start(durationMs: Long = 60_000L) {
        _lines.value = emptyList()
        activeUntil = System.currentTimeMillis() + durationMs
    }

    fun stop() { activeUntil = 0L }

    fun append(newLines: List<String>) {
        if (newLines.isEmpty()) return
        val seen = _lines.value.toHashSet()
        val fresh = newLines.filter { it !in seen }
        if (fresh.isNotEmpty()) _lines.value = (_lines.value + fresh).takeLast(1000)
    }
}

/** Today's live usage totals in a small private prefs file: "day" plus "pkg=ms" lines. */
private class PrefsUsageStore(context: android.content.Context) : LiveUsageTracker.Store {
    private val prefs = context.getSharedPreferences("live_usage", android.content.Context.MODE_PRIVATE)

    override fun load(): Pair<String, Map<String, Long>>? {
        val day = prefs.getString("day", null) ?: return null
        val values = prefs.getString("ms", "").orEmpty().lineSequence().mapNotNull { line ->
            val i = line.lastIndexOf('=')
            if (i <= 0) null else line.substring(i + 1).toLongOrNull()?.let { line.substring(0, i) to it }
        }.toMap()
        return day to values
    }

    override fun save(dayKey: String, values: Map<String, Long>) {
        prefs.edit().putString("day", dayKey).putString("ms", values.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }
}
