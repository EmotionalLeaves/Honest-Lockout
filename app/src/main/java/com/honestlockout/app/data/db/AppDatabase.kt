package com.honestlockout.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.honestlockout.app.data.db.daos.ActivitySampleDao
import com.honestlockout.app.data.db.daos.BlockRuleDao
import com.honestlockout.app.data.db.daos.LockoutDao
import com.honestlockout.app.data.db.daos.OverrideDao
import com.honestlockout.app.data.db.daos.PendingChangeDao
import com.honestlockout.app.data.db.daos.PurposeTemplateDao
import com.honestlockout.app.data.db.daos.ScheduleDao
import com.honestlockout.app.data.db.daos.ScreenRuleDao
import com.honestlockout.app.data.db.daos.StrikeDao
import com.honestlockout.app.data.db.daos.TamperEventDao
import com.honestlockout.app.data.db.daos.UsageDayDao
import com.honestlockout.app.data.db.entities.ActivitySample
import com.honestlockout.app.data.db.entities.BlockRule
import com.honestlockout.app.data.db.entities.Lockout
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.data.db.entities.PendingChange
import com.honestlockout.app.data.db.entities.PurposeTemplate
import com.honestlockout.app.data.db.entities.Schedule
import com.honestlockout.app.data.db.entities.ScreenRule
import com.honestlockout.app.data.db.entities.Strike
import com.honestlockout.app.data.db.entities.TamperEvent
import com.honestlockout.app.data.db.entities.UsageDay

/*
 * Schema version 1 is the complete schema for all eight rounds (they were built together),
 * so no Migration objects exist yet. fallbackToDestructiveMigration() is deliberately NOT used:
 * wiping the database would erase strikes. Any future schema change must bump the version
 * and add a Migration here.
 */
@Database(
    entities = [
        BlockRule::class, Schedule::class, PurposeTemplate::class, ScreenRule::class,
        Override::class, ActivitySample::class, Strike::class, Lockout::class,
        PendingChange::class, UsageDay::class, TamperEvent::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun purposeTemplateDao(): PurposeTemplateDao
    abstract fun screenRuleDao(): ScreenRuleDao
    abstract fun overrideDao(): OverrideDao
    abstract fun activitySampleDao(): ActivitySampleDao
    abstract fun strikeDao(): StrikeDao
    abstract fun lockoutDao(): LockoutDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun usageDayDao(): UsageDayDao
    abstract fun tamperEventDao(): TamperEventDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "honest.db").build()
    }
}
