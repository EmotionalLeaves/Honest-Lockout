package com.honestlockout.app.overlay

import android.content.Context
import com.honestlockout.app.R
import com.honestlockout.app.data.db.StrikeReason
import com.honestlockout.app.engine.Decision
import com.honestlockout.app.engine.LadderResult
import com.honestlockout.app.engine.LockReason
import com.honestlockout.app.ui.Format

/** User-visible lines for the overlays, built from strings.xml. */
object OverlayTexts {

    fun lockReasonLine(context: Context, lock: Decision.Lock, budgetMin: Int): String {
        val until = lock.until
        return when (lock.reason) {
            LockReason.ALWAYS -> context.getString(R.string.lock_reason_always)
            LockReason.BUDGET_SPENT -> context.getString(R.string.lock_reason_budget, budgetMin)
            LockReason.SCHEDULE -> if (until != null) context.getString(R.string.lock_reason_schedule, Format.time(context, until))
            else context.getString(R.string.lock_reason_always)
            LockReason.LOCKOUT -> context.getString(R.string.lock_reason_lockout, Format.dateTime(context, until ?: 0L))
            LockReason.BREAK_GLASS_WAIT -> context.getString(R.string.lock_reason_break_glass_wait, Format.time(context, until ?: 0L))
        }
    }

    fun nextOverrideLine(context: Context, globalLockoutUntil: Long?): String =
        if (globalLockoutUntil == null) context.getString(R.string.lock_next_now)
        else context.getString(R.string.lock_next_at, Format.dateTime(context, globalLockoutUntil))

    fun reasonName(context: Context, reason: StrikeReason): String = context.getString(
        when (reason) {
            StrikeReason.OFF_APP -> R.string.reason_off_app
            StrikeReason.OFF_SCREEN -> R.string.reason_off_screen
            StrikeReason.WRONG_SHAPE -> R.string.reason_wrong_shape
            StrikeReason.CHECKIN_MISSED -> R.string.reason_checkin_missed
            StrikeReason.THIRD_WARNING -> R.string.reason_third_warning
            StrikeReason.TAMPER -> R.string.reason_tamper
            StrikeReason.BREAK_GLASS -> R.string.reason_break_glass
            StrikeReason.APPEAL_REVERSED -> R.string.reason_appeal_reversed
        }
    )

    fun lockoutInfo(context: Context, r: LadderResult): LockoutInfo = LockoutInfo(
        title = context.getString(R.string.lockout_title, r.activeStrikes),
        reasonLine = reasonName(context, r.reason),
        appLockedLine = if (r.packageName != null && r.appLockedUntil != null) {
            context.getString(R.string.lockout_app_until, Format.appLabel(context, r.packageName), Format.dateTime(context, r.appLockedUntil))
        } else null,
        overridesReturnLine = r.overridesReturnAt?.let { context.getString(R.string.lockout_overrides_return, Format.dateTime(context, it)) },
    )
}
