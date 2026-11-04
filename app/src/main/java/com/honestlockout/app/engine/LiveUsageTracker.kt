package com.honestlockout.app.engine

/**
 * In-memory foreground time per package since local midnight, fed by the accessibility service.
 * Lets budget blocking react immediately instead of waiting for DailyResetWorker.
 * Time spent under an override is not counted (overrides do not consume budget minutes).
 * Resets at local midnight. Thread-safe.
 */
class LiveUsageTracker {
    private val msByPackage = HashMap<String, Long>()
    private var dayKey: String = ""
    private var currentPkg: String? = null
    private var segmentStart: Long = 0L
    private var segmentCounts: Boolean = false
    private var screenOn: Boolean = true

    @Synchronized
    fun seed(values: Map<String, Long>, now: Long) {
        rollDay(now)
        msByPackage.clear()
        msByPackage.putAll(values)
    }

    /** Front app changed. [counts] is false while the package is covered by an override. */
    @Synchronized
    fun onFront(packageName: String, counts: Boolean, now: Long) {
        closeSegment(now)
        currentPkg = packageName
        segmentStart = now
        segmentCounts = counts
    }

    /** Re-split the running segment, e.g. when an override starts or ends on the front app. */
    @Synchronized
    fun refresh(counts: Boolean, now: Long) {
        val pkg = currentPkg ?: return
        onFront(pkg, counts, now)
    }

    @Synchronized
    fun onScreen(on: Boolean, now: Long) {
        closeSegment(now)
        screenOn = on
        segmentStart = now
    }

    @Synchronized
    fun minutesFor(packageName: String, now: Long): Int {
        rollDay(now)
        var ms = msByPackage[packageName] ?: 0L
        if (screenOn && segmentCounts && currentPkg == packageName && now > segmentStart) ms += now - segmentStart
        return (ms / 60_000L).toInt()
    }

    private fun closeSegment(now: Long) {
        rollDay(now)
        val pkg = currentPkg
        if (pkg != null && screenOn && segmentCounts && now > segmentStart) {
            msByPackage[pkg] = (msByPackage[pkg] ?: 0L) + (now - segmentStart)
        }
        segmentStart = now
    }

    private fun rollDay(now: Long) {
        val key = DayClock.dateKey(now)
        if (key != dayKey) {
            if (dayKey.isNotEmpty()) {
                msByPackage.clear()
                segmentStart = maxOf(segmentStart, DayClock.startOfDay(now))
            }
            dayKey = key
        }
    }
}
