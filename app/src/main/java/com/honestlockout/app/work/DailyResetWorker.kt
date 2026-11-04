package com.honestlockout.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.engine.DayClock
import java.util.concurrent.TimeUnit

/**
 * Writes yesterday's UsageDay rows (and back-fills any of the last 7 days that have none, so the
 * History chart has data after a gap) and resets appeals_this_week on Mondays.
 * Runs every 24 h and on app start when last_daily_reset is not today.
 */
class DailyResetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { runReset(System.currentTimeMillis()) }
            .onFailure { AppLog.w("HL/Work", "DailyResetWorker failed", it) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "daily_reset_periodic"

        suspend fun runReset(now: Long) {
            val today = DayClock.localDate(now)
            val todayKey = DayClock.dateKey(today)
            val settings = AppGraph.settings
            val s = settings.snapshot()
            if (s.lastDailyReset == todayKey) return
            val usage = AppGraph.usageRepo
            usage.writeDay(today.minusDays(1))
            for (back in 2L..6L) {
                val d = today.minusDays(back)
                if (!usage.hasRowsFor(d)) usage.writeDay(d)
            }
            // Monday reset; also catches a missed Monday by comparing the week of the last reset.
            val lastWeekStart = runCatching { DayClock.parseDate(s.lastDailyReset) }.getOrNull()
                ?.let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
            val thisWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            if (DayClock.isMonday(now) || lastWeekStart != thisWeekStart) settings.setAppealsThisWeek(0)
            settings.setLastDailyReset(todayKey)
            AppLog.w("HL/Work", "Daily reset done for $todayKey")
        }

        /** Called on app start: reset now if today's reset has not happened. */
        suspend fun runIfNeeded(now: Long) {
            if (AppGraph.settings.snapshot().lastDailyReset != DayClock.dateKey(now)) runReset(now)
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<DailyResetWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
