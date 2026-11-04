package com.honestlockout.app.engine

import com.honestlockout.app.data.db.Strictness
import com.honestlockout.app.data.db.StrikeReason
import com.honestlockout.app.data.settings.Settings

/** What OverrideMonitor.tick tells the foreground service to do. */
sealed class TickResult {
    object Nothing : TickResult()
    /** A new warning started: show the banner and a HIGH notification. */
    object Warn : TickResult()
    object AskCheckin : TickResult()
    data class Strike(val reason: StrikeReason) : TickResult()
    object Timeout : TickResult()
}

sealed class BannerState {
    object None : BannerState()
    data class Warning(val secondsLeft: Int) : BannerState()
    data class Checkin(val secondsLeft: Int) : BannerState()
}

/** The settings an override is judged by, read once when it starts. */
data class MonitorConfig(
    val strictness: Strictness,
    val graceMs: Long,
    val maxWarnings: Int,
    val checkinMinGap: Int,
    val checkinMaxGap: Int,
    val checkinAnswerMs: Long,
    val thresholds: ShapeThresholds,
) {
    companion object {
        fun from(s: Settings): MonitorConfig {
            val minGap = s.checkinMinGapMin.coerceAtLeast(1)
            val answerSec = if (s.strictness == Strictness.STRICT) OverrideMonitor.STRICT_CHECKIN_ANSWER_SEC else s.checkinAnswerSec
            return MonitorConfig(
                strictness = s.strictness,
                graceMs = s.warningGraceSec * 1000L,
                maxWarnings = s.maxWarnings.coerceAtLeast(1),
                checkinMinGap = minGap,
                checkinMaxGap = s.checkinMaxGapMin.coerceAtLeast(minGap),
                checkinAnswerMs = answerSec * 1000L,
                thresholds = ShapeThresholds.from(s),
            )
        }
    }
}
