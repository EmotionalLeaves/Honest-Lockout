package com.honestlockout.app.data.repo

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.honestlockout.app.data.db.AppDatabase
import com.honestlockout.app.data.db.entities.UsageDay
import com.honestlockout.app.engine.DayClock
import java.time.LocalDate

/**
 * Screen-time numbers. Past days come from UsageDay rows (written by DailyResetWorker from
 * UsageStatsManager.queryUsageStats(INTERVAL_DAILY)); today comes live from usage events so it
 * is accurate to the minute. The launcher, system UI and this app are not counted.
 */
class UsageRepository(private val context: Context, private val db: AppDatabase) {
    private val usm get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION") // the spec's check; still the documented way to read this op
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun excluded(): Set<String> {
        val home = runCatching {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
            ).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return home.toSet() + setOf("com.android.systemui", "android", context.packageName)
    }

    /** Exact foreground milliseconds per package between [start] and [end], from usage events. */
    fun foregroundMs(start: Long, end: Long): Map<String, Long> {
        if (!hasUsageAccess() || end <= start) return emptyMap()
        val result = HashMap<String, Long>()
        val openSince = HashMap<String, Long>()
        val events = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return emptyMap()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> openSince[pkg] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    openSince.remove(pkg)?.let { s -> result[pkg] = (result[pkg] ?: 0L) + (e.timeStamp - s) }
                }
                SCREEN_NON_INTERACTIVE -> {
                    openSince.forEach { (p, s) -> result[p] = (result[p] ?: 0L) + (e.timeStamp - s) }
                    openSince.clear()
                }
            }
        }
        openSince.forEach { (p, s) -> result[p] = (result[p] ?: 0L) + (end - s) }
        val skip = excluded()
        return result.filterKeys { it !in skip }.filterValues { it > 0 }
    }

    fun todayMinutesByPackage(now: Long): Map<String, Int> =
        foregroundMs(DayClock.startOfDay(now), now).mapValues { (it.value / 60_000L).toInt() }.filterValues { it > 0 }

    /** Same per-app rounding as the History chart, so Home and today's bar always agree. */
    fun todayTotalMinutes(now: Long): Int = todayMinutesByPackage(now).values.sum()

    /** Writes [date]'s UsageDay rows from UsageStatsManager.queryUsageStats(INTERVAL_DAILY). */
    suspend fun writeDay(date: LocalDate) {
        if (!hasUsageAccess()) return
        val start = DayClock.startOfDay(date)
        val end = DayClock.startOfDay(date.plusDays(1))
        val stats = runCatching { usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) }.getOrNull() ?: return
        val skip = excluded()
        val key = DayClock.dateKey(date)
        stats.filter { it.packageName !in skip && it.firstTimeStamp >= start - HOUR && it.firstTimeStamp < end }
            .groupBy { it.packageName }
            .forEach { (pkg, list) ->
                val minutes = (list.maxOf { it.totalTimeInForeground } / 60_000L).toInt()
                if (minutes > 0) db.usageDayDao().upsert(key, pkg, minutes)
            }
    }

    suspend fun hasRowsFor(date: LocalDate): Boolean = db.usageDayDao().countFor(DayClock.dateKey(date)) > 0
    suspend fun forDate(date: LocalDate): List<UsageDay> = db.usageDayDao().forDate(DayClock.dateKey(date))

    companion object {
        private const val HOUR = 60L * 60 * 1000
        /** UsageEvents.Event.SCREEN_NON_INTERACTIVE (API 28); a literal keeps minSdk 26 lint-clean. */
        private const val SCREEN_NON_INTERACTIVE = 16
    }
}
