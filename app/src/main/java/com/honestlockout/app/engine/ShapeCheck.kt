package com.honestlockout.app.engine

import com.honestlockout.app.data.db.ActivityShape
import com.honestlockout.app.data.db.Strictness
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.data.settings.Settings

/** Shape thresholds per minute. Round 8 reads them from settings (higher = loosening). */
data class ShapeThresholds(
    val typingScrollMax: Int,
    val readingScrollMax: Int,
    val passiveScrollMax: Int,
    val passiveClickMax: Int,
) {
    companion object {
        fun from(s: Settings) = ShapeThresholds(
            s.int(Keys.TYPING_SCROLL_MAX), s.int(Keys.READING_SCROLL_MAX),
            s.int(Keys.PASSIVE_SCROLL_MAX), s.int(Keys.PASSIVE_CLICK_MAX),
        )
    }
}

/** One completed minute of activity counters. */
data class MinuteCounts(val index: Int, val typing: Int, val scroll: Int, val click: Int)

/**
 * The activity-shape signal. A minute mismatches when (per expectedShape):
 * TYPING: typing == 0 && scroll >= TYPING_SCROLL_MAX; READING: scroll >= READING_SCROLL_MAX;
 * PASSIVE: scroll >= PASSIVE_SCROLL_MAX || click >= PASSIVE_CLICK_MAX; ANY: never.
 * Evaluated only after minute 2, on the last 2 full minutes (STRICT: 1; LENIENT: off).
 */
object ShapeCheck {

    fun minuteMismatch(shape: ActivityShape, m: MinuteCounts, th: ShapeThresholds): Boolean = when (shape) {
        ActivityShape.TYPING -> m.typing == 0 && m.scroll >= th.typingScrollMax
        ActivityShape.READING -> m.scroll >= th.readingScrollMax
        ActivityShape.PASSIVE -> m.scroll >= th.passiveScrollMax || m.click >= th.passiveClickMax
        ActivityShape.ANY -> false
    }

    /** True when the completed minutes (at or after [baselineMinute]) show the wrong shape. */
    fun mismatch(
        shape: ActivityShape, strictness: Strictness, th: ShapeThresholds,
        currentMinute: Int, minutes: List<MinuteCounts>, baselineMinute: Int,
    ): Boolean {
        if (shape == ActivityShape.ANY || strictness == Strictness.LENIENT || currentMinute < 2) return false
        val need = if (strictness == Strictness.STRICT) 1 else 2
        val recent = minutes.filter { it.index >= baselineMinute }.takeLast(need)
        return recent.size == need && recent.all { minuteMismatch(shape, it, th) }
    }

    /**
     * While a WRONG_SHAPE warning shows, behaving as the purpose expects clears it early:
     * any typing for TYPING; scrolling (and tapping) at under half the threshold rate for
     * READING / PASSIVE, judged after at least 10 s.
     */
    fun backOnTrack(shape: ActivityShape, th: ShapeThresholds, elapsedMs: Long, typing: Int, scroll: Int, click: Int): Boolean {
        val e = elapsedMs.coerceAtLeast(1)
        return when (shape) {
            ActivityShape.TYPING -> typing >= 1
            ActivityShape.READING -> e >= 10_000 && scroll * 120_000L <= th.readingScrollMax * e
            ActivityShape.PASSIVE -> e >= 10_000 &&
                scroll * 120_000L <= th.passiveScrollMax * e && click * 120_000L <= th.passiveClickMax * e
            ActivityShape.ANY -> true
        }
    }
}
