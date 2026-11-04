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
import java.util.concurrent.TimeUnit

/**
 * Strikes expire by query (expiresAt), so nothing is deleted for them. This removes Lockout rows
 * whose until has passed; Home observes the tables through Flows, so it refreshes by itself.
 */
class StrikeExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching {
            val removed = AppGraph.strikeRepo.deleteExpiredLockouts(System.currentTimeMillis())
            if (removed > 0) AppLog.w("HL/Work", "Removed $removed expired lockouts")
        }.onFailure { AppLog.w("HL/Work", "StrikeExpiryWorker failed", it) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "strike_expiry_periodic"

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<StrikeExpiryWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "strike_expiry_once", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<StrikeExpiryWorker>().build()
            )
        }
    }
}
