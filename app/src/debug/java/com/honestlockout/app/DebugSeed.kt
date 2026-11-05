package com.honestlockout.app

import android.content.Intent
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.data.db.StrikeReason
import com.honestlockout.app.data.db.entities.BlockRule
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.data.db.entities.Strike
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.engine.DayClock
import com.honestlockout.app.engine.EmergencyAllowlist
import com.honestlockout.app.ui.blocklist.launchableApps
import kotlinx.coroutines.runBlocking

/**
 * Debug builds only (this file lives in src/debug): `adb shell am start ... --ez debug_seed true`
 * marks setup complete and fills the database with sample rules, overrides and a strike, so the
 * screenshot workflow can capture populated screens. Never shipped in release.
 */
object DebugSeed {
    fun maybeSeed(intent: Intent?) {
        if (intent?.getBooleanExtra("debug_seed", false) != true) return
        runBlocking {
            val db = AppGraph.db
            val now = System.currentTimeMillis()
            AppGraph.settings.setSetupComplete(true)
            if (db.blockRuleDao().count() > 0) return@runBlocking
            val apps = launchableApps(AppGraph.app).filter { !EmergencyAllowlist.contains(it.packageName) }
            val picks = apps.take(6).map { it.packageName }
            picks.forEachIndexed { i, pkg ->
                val mode = when (i % 3) { 0 -> BlockMode.ALWAYS; 1 -> BlockMode.BUDGET; else -> BlockMode.ALWAYS }
                db.blockRuleDao().insert(BlockRule(pkg, mode, if (mode == BlockMode.BUDGET) 30 else 0, null, now - i * 3_600_000L))
            }
            val a = picks.getOrNull(0) ?: return@runBlocking
            val b = picks.getOrNull(1) ?: a
            val h = 3_600_000L
            db.overrideDao().insert(Override(packageName = a, purposeId = PurposeTemplates.ID_REPLY, statement = "Reply to Sam about Friday", startedAt = now - 3 * h, plannedMin = 10, endedAt = now - 3 * h + 6 * 60_000L, endReason = EndReason.DONE))
            val s = db.overrideDao().insert(Override(packageName = b, purposeId = PurposeTemplates.ID_LOOKUP, statement = "Look up a recipe", startedAt = now - 26 * h, plannedMin = 15, endedAt = now - 26 * h + 9 * 60_000L, endReason = EndReason.STRIKE, warningCount = 1))
            db.strikeDao().insert(Strike(overrideId = s, packageName = b, reason = StrikeReason.OFF_SCREEN, createdAt = now - 26 * h + 9 * 60_000L, expiresAt = now + 6 * 24 * h))
            db.overrideDao().insert(Override(packageName = b, purposeId = PurposeTemplates.ID_LISTEN, statement = "Fall asleep to a video", startedAt = now - 50 * h, plannedMin = 120, endedAt = now - 48 * h, endReason = EndReason.TIMEOUT))
            db.usageDayDao().upsert(DayClock.dateKey(now - 24 * h), a, 48)
            db.usageDayDao().upsert(DayClock.dateKey(now - 24 * h), b, 31)
            db.usageDayDao().upsert(DayClock.dateKey(now - 48 * h), a, 72)
            AppGraph.liveUsage.seed(mapOf(picks[1] to 18 * 60_000L), now)
        }
    }
}
