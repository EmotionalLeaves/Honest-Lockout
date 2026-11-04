package com.honestlockout.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.honestlockout.app.data.db.ActivityShape
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.data.db.EndReason
import com.honestlockout.app.data.db.ScreenAction
import com.honestlockout.app.data.db.StrikeReason

// All times are epoch milliseconds. Table names equal the class names (spec, Data model).

@Entity(tableName = "BlockRule")
data class BlockRule(
    @PrimaryKey val packageName: String,
    val mode: BlockMode,
    val dailyBudgetMin: Int = 0,
    val scheduleId: Long? = null,
    val createdAt: Long,
)

/** [days] is 7 chars of 1/0, Monday first. End before start means the window crosses midnight. */
@Entity(tableName = "Schedule")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val days: String,
    val startMinute: Int,
    val endMinute: Int,
)

@Entity(tableName = "PurposeTemplate")
data class PurposeTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Comma-separated; empty = pick the app at override time. */
    val allowedPackages: String,
    /** Comma-separated labels from ScreenRule. */
    val blockedScreenLabels: String,
    val expectedShape: ActivityShape,
    val maxMinutes: Int,
    val builtIn: Boolean,
    val enabled: Boolean = true,
)

@Entity(tableName = "ScreenRule")
data class ScreenRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val matchViewId: String?,
    val matchText: String?,
    val label: String,
    val action: ScreenAction,
)

/** At most one row has endedAt == null at any time. */
@Entity(tableName = "Override")
data class Override(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val purposeId: Long,
    val statement: String,
    val startedAt: Long,
    val plannedMin: Int,
    val endedAt: Long? = null,
    val endReason: EndReason? = null,
    val warningCount: Int = 0,
    val checkinsAsked: Int = 0,
    val checkinsAnswered: Int = 0,
)

@Entity(tableName = "ActivitySample")
data class ActivitySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val overrideId: Long,
    val minuteIndex: Int,
    val frontPackage: String,
    val typingEvents: Int,
    val scrollEvents: Int,
    val clickEvents: Int,
    val flaggedScreen: String?,
)

/** Active = forgivenAt == null && expiresAt > now. */
@Entity(tableName = "Strike")
data class Strike(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val overrideId: Long?,
    val packageName: String?,
    val reason: StrikeReason,
    val createdAt: Long,
    val expiresAt: Long,
    val forgivenAt: Long? = null,
    val appealed: Boolean = false,
)

/** packageName == null means all overrides are blocked (the "wait"). */
@Entity(tableName = "Lockout")
data class Lockout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String?,
    val until: Long,
    val reason: String,
)

@Entity(tableName = "PendingChange")
data class PendingChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val changeJson: String,
    val createdAt: Long,
    val applyAt: Long,
    val cancelled: Boolean = false,
)

/** [date] is yyyy-MM-dd in the phone's time zone. */
@Entity(tableName = "UsageDay", primaryKeys = ["date", "packageName"])
data class UsageDay(
    val date: String,
    val packageName: String,
    val minutes: Int,
)

/** kind is SERVICE_OFF, SERVICE_KILLED or REBOOT. */
@Entity(tableName = "TamperEvent")
data class TamperEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val detectedAt: Long,
    val kind: String,
    val strikeId: Long?,
) {
    companion object {
        const val SERVICE_OFF = "SERVICE_OFF"
        const val SERVICE_KILLED = "SERVICE_KILLED"
        const val REBOOT = "REBOOT"
    }
}
