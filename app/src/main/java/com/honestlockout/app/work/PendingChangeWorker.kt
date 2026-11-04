package com.honestlockout.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.data.repo.PendingChangeRepository
import java.util.concurrent.TimeUnit

/**
 * Applies every PendingChange with applyAt <= now && !cancelled through LoosenGuard.apply, then
 * deletes it so it never applies twice. BreakGlass rows are skipped: they complete on the
 * break-glass screen once the sentence is typed. Also runs on every app open and on boot.
 */
class PendingChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { applyDue(System.currentTimeMillis()) }
            .onFailure { AppLog.w("HL/Work", "PendingChangeWorker failed", it) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "pending_change_periodic"
        private const val ONCE = "pending_change_once"

        suspend fun applyDue(now: Long) {
            val repo = AppGraph.pendingRepo
            for (change in repo.due(now)) {
                if (PendingChangeRepository.isBreakGlass(change)) continue
                AppGraph.loosenGuard.apply(change.changeJson, now)
                repo.delete(change.id)
                AppLog.w("HL/Work", "Applied pending change ${change.id}: ${change.description}")
            }
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<PendingChangeWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** One-time run after [delayMs]; each queued change gets its own request. */
        fun enqueueOnce(context: Context, delayMs: Long) {
            val req = OneTimeWorkRequestBuilder<PendingChangeWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("$ONCE-${System.currentTimeMillis()}", ExistingWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$ONCE-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<PendingChangeWorker>().build()
            )
        }
    }
}
