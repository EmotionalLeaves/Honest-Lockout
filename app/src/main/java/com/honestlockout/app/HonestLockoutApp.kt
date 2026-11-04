package com.honestlockout.app

import android.app.Application
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.service.Notifications
import com.honestlockout.app.work.DailyResetWorker
import com.honestlockout.app.work.PendingChangeWorker
import com.honestlockout.app.work.StrikeExpiryWorker
import kotlinx.coroutines.launch

/** Creates AppGraph, loads rules and templates, and schedules the WorkManager jobs. */
class HonestLockoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifications.ensureChannels(this)
        DailyResetWorker.schedulePeriodic(this)
        StrikeExpiryWorker.schedulePeriodic(this)
        PendingChangeWorker.schedulePeriodic(this)
        AppGraph.appScope.launch {
            runCatching {
                PurposeTemplates.ensureInserted(this@HonestLockoutApp, AppGraph.db.purposeTemplateDao())
                AppGraph.screenRules.reloadFromAssets()
                val now = System.currentTimeMillis()
                PendingChangeWorker.applyDue(now) // also on every app open (spec, troubleshooting)
                AppGraph.strikeRepo.deleteExpiredLockouts(now)
                DailyResetWorker.runIfNeeded(now)
            }.onFailure { AppLog.w("HL/Work", "Startup tasks failed", it) }
        }
    }
}
