package com.honestlockout.app.engine

import com.honestlockout.app.data.db.StrikeReason

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
