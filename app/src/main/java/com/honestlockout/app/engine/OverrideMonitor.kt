package com.honestlockout.app.engine

import com.honestlockout.app.data.db.ActivityShape
import com.honestlockout.app.data.db.Strictness
import com.honestlockout.app.data.db.StrikeReason
import com.honestlockout.app.data.db.entities.ActivitySample
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.data.db.entities.PurposeTemplate
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.data.settings.Keys
import com.honestlockout.app.data.settings.Settings
import kotlin.random.Random

/*
 * Choices made where the spec leaves room (spec rule 1: simplest option, stated here):
 * - Opening a *different blocked app* during an override shows that app's lock screen and goes
 *   HOME, so the front app becomes the launcher. To still warn as round 4's test expects, the
 *   attempt is sticky ([onBlockedAttempt]): it counts as OFF_APP until the user is back in an
 *   allowed app. Ordinary trips to the launcher or this app never count.
 * - WRONG_SHAPE is judged on completed minutes, so it could never clear inside a 30 s grace.
 *   While its warning shows, it clears early once the user behaves as the purpose expects
 *   (typing for TYPING; slow scrolling for READING/PASSIVE), then needs fresh minutes again.
 * - "Back to it" pauses the grace clock for 10 s (the clock resumes where it was).
 * - A check-in waits while a warning is showing, and warnings wait while a check-in is showing.
 */

sealed class TickResult {
    object Nothing : TickResult()
    /** A new warning started (show the banner + a HIGH notification). */
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

/** Shape thresholds per minute. Round 8 reads them from settings. */
data class ShapeThresholds(val typingScrollMax: Int, val readingScrollMax: Int, val passiveScrollMax: Int, val passiveClickMax: Int)

class OverrideMonitor(private val random: Random = Random.Default) {

    companion object {
        // Defaults; the live values come from settings (round 8).
        const val TYPING_SCROLL_MAX = 15
        const val READING_SCROLL_MAX = 40
        const val PASSIVE_SCROLL_MAX = 30
        const val PASSIVE_CLICK_MAX = 20
        const val DEBOUNCE_MS = 5_000L
        const val LENIENT_SCREEN_DEBOUNCE_MS = 10_000L
        const val BACK_TO_IT_MS = 10_000L
        const val STRICT_CHECKIN_ANSWER_SEC = 20

        fun thresholds(s: Settings) = ShapeThresholds(
            s.int(Keys.TYPING_SCROLL_MAX), s.int(Keys.READING_SCROLL_MAX),
            s.int(Keys.PASSIVE_SCROLL_MAX), s.int(Keys.PASSIVE_CLICK_MAX),
        )
    }

    private data class Minute(val index: Int, val typing: Int, val scroll: Int, val click: Int)

    var override: Override? = null
        private set
    private var allowed: Set<String> = emptySet()
    private var blockedLabels: Set<String> = emptySet()
    private var shape: ActivityShape = ActivityShape.ANY
    private var strictness = Strictness.NORMAL
    private var graceMs = 30_000L
    private var maxWarnings = 3
    private var checkinMinGap = 3
    private var checkinMaxGap = 8
    private var checkinAnswerMs = 30_000L
    private var th = ShapeThresholds(TYPING_SCROLL_MAX, READING_SCROLL_MAX, PASSIVE_SCROLL_MAX, PASSIVE_CLICK_MAX)

    /** Packages that never count as off-app (launcher, system UI, IME, this app). Set by the service. */
    @Volatile var isIgnored: (String) -> Boolean = { false }

    private var frontPackage: String? = null
    private var blockedAttempt = false
    private var currentScreenLabel: String? = null
    private var screenOn = true

    private var minuteIndex = 0
    private var typing = 0
    private var scroll = 0
    private var click = 0
    private var flaggedThisMinute: String? = null
    private val minutes = ArrayDeque<Minute>()
    private var shapeBaselineMinute = 0
    private var pendingSample: ActivitySample? = null

    private var mismatchSince: Long? = null
    private var mismatchReason: StrikeReason? = null
    private var warningStartedAt: Long? = null
    private var hiddenUntil = 0L
    private var warnTyping = 0
    private var warnScroll = 0
    private var warnClick = 0
    var warningCount = 0
        private set

    private var nextCheckinAt: Long? = null
    private var checkinShownAt: Long? = null
    var checkinsAsked = 0
        private set
    var checkinsAnswered = 0
        private set

    val isActive: Boolean get() = override != null
    val statement: String get() = override?.statement.orEmpty()

    @Synchronized
    fun start(o: Override, template: PurposeTemplate?, s: Settings, now: Long, frontNow: String? = null) {
        override = o
        allowed = template?.let { PurposeTemplates.split(it.allowedPackages).toSet() } ?: emptySet()
        blockedLabels = template?.let { PurposeTemplates.split(it.blockedScreenLabels).toSet() } ?: emptySet()
        shape = template?.expectedShape ?: ActivityShape.ANY
        strictness = s.strictness
        graceMs = s.warningGraceSec * 1000L
        maxWarnings = s.maxWarnings.coerceAtLeast(1)
        checkinMinGap = s.checkinMinGapMin.coerceAtLeast(1)
        checkinMaxGap = s.checkinMaxGapMin.coerceAtLeast(checkinMinGap)
        checkinAnswerMs = (if (strictness == Strictness.STRICT) STRICT_CHECKIN_ANSWER_SEC else s.checkinAnswerSec) * 1000L
        th = thresholds(s)
        frontPackage = frontNow ?: o.packageName
        blockedAttempt = false
        currentScreenLabel = null
        minuteIndex = ((now - o.startedAt) / 60_000L).toInt().coerceAtLeast(0)
        typing = 0; scroll = 0; click = 0; flaggedThisMinute = null
        minutes.clear(); shapeBaselineMinute = 0; pendingSample = null
        mismatchSince = null; mismatchReason = null; warningStartedAt = null; hiddenUntil = 0
        warningCount = o.warningCount
        checkinsAsked = o.checkinsAsked
        checkinsAnswered = o.checkinsAnswered
        checkinShownAt = null
        nextCheckinAt = scheduleCheckin(maxOf(now, o.startedAt))
    }

    @Synchronized
    fun stop() {
        override = null
        warningStartedAt = null
        checkinShownAt = null
        pendingSample = null
    }

    private fun endAt(o: Override) = o.startedAt + o.plannedMin * 60_000L

    private fun scheduleCheckin(from: Long): Long? {
        val o = override ?: return null
        val gapMin = if (checkinMaxGap > checkinMinGap) random.nextInt(checkinMinGap, checkinMaxGap + 1) else checkinMinGap
        val t = maxOf(from + gapMin * 60_000L + random.nextLong(0, 60_000L), o.startedAt + 2 * 60_000L)
        return if (t > endAt(o) - 60_000L) null else t
    }

    fun isAllowedPackage(pkg: String): Boolean {
        val o = override ?: return false
        return pkg == o.packageName || pkg in allowed || EmergencyAllowlist.contains(pkg)
    }

    /** True when screen and activity signals should be collected for [pkg]. */
    fun watchesPackage(pkg: String): Boolean {
        val o = override ?: return false
        return pkg == o.packageName || pkg in allowed
    }

    @Synchronized fun onFrontApp(pkg: String) {
        if (override == null) return
        if (isAllowedPackage(pkg)) blockedAttempt = false
        if (pkg != frontPackage) currentScreenLabel = null
        frontPackage = pkg
    }

    @Synchronized fun onBlockedAttempt(pkg: String) {
        if (override == null || isAllowedPackage(pkg)) return
        blockedAttempt = true
        frontPackage = pkg
    }

    @Synchronized fun onScreen(label: String?) {
        if (override == null) return
        currentScreenLabel = label
        if (label != null && label in blockedLabels) flaggedThisMinute = label
    }

    @Synchronized fun onTyping() { if (override != null) { typing++; warnTyping++ } }
    @Synchronized fun onScroll() { if (override != null) { scroll++; warnScroll++ } }
    @Synchronized fun onClick() { if (override != null) { click++; warnClick++ } }

    @Synchronized fun onScreenState(on: Boolean) {
        screenOn = on
        // A check-in is skipped (not failed) while the screen is off; it shows again once it is on.
        if (!on && checkinShownAt != null) { checkinShownAt = null; nextCheckinAt = 0L }
    }

    /** "Back to it": hide the banner for 10 s; the grace clock pauses meanwhile. */
    @Synchronized fun onBackToIt(now: Long) {
        if (warningStartedAt != null) hiddenUntil = now + BACK_TO_IT_MS
    }

    @Synchronized fun onCheckinYes(now: Long) {
        if (checkinShownAt == null) return
        checkinShownAt = null
        checkinsAnswered++
        nextCheckinAt = scheduleCheckin(now)
    }

    @Synchronized fun onCheckinMissed(): TickResult {
        checkinShownAt = null
        return TickResult.Strike(StrikeReason.CHECKIN_MISSED)
    }

    /** One completed minute sample, if a minute just finished. Consumed by the service. */
    @Synchronized fun takeSample(): ActivitySample? = pendingSample.also { pendingSample = null }

    @Synchronized
    fun bannerState(now: Long): BannerState {
        val w = warningStartedAt
        if (w != null) {
            if (now < hiddenUntil) return BannerState.None
            return BannerState.Warning(((graceMs - (now - w)) / 1000L).toInt().coerceAtLeast(0))
        }
        val c = checkinShownAt ?: return BannerState.None
        return BannerState.Checkin(((checkinAnswerMs - (now - c)) / 1000L).toInt().coerceAtLeast(0))
    }

    @Synchronized
    fun tick(now: Long): TickResult {
        val o = override ?: return TickResult.Nothing
        rollMinute(o, now)
        if (now >= endAt(o)) return TickResult.Timeout

        checkinShownAt?.let { if (now - it >= checkinAnswerMs) return onCheckinMissed() }

        val reason = currentMismatch(now)
        if (reason == null) {
            mismatchSince = null
            mismatchReason = null
            if (warningStartedAt != null) {
                warningStartedAt = null // resolved; it still counts toward warningCount
                hiddenUntil = 0
                if (mismatchReasonWasShape) shapeBaselineMinute = minuteIndex + 1
            }
        } else {
            if (mismatchSince == null) mismatchSince = now
            mismatchReason = reason
        }
        mismatchReasonWasShape = reason == StrikeReason.WRONG_SHAPE

        val w = warningStartedAt
        if (w != null && hiddenUntil > 0) {
            if (now < hiddenUntil) return TickResult.Nothing
            warningStartedAt = w + BACK_TO_IT_MS // resume the grace clock where it was
            hiddenUntil = 0
        }
        warningStartedAt?.let { if (reason != null && now - it >= graceMs) return TickResult.Strike(reason) }

        if (checkinShownAt == null && warningStartedAt == null && screenOn) {
            val due = nextCheckinAt
            if (due != null && now >= due && now < endAt(o) - 30_000L) {
                checkinShownAt = now
                checkinsAsked++
                return TickResult.AskCheckin
            }
        }

        val since = mismatchSince
        if (reason != null && since != null && warningStartedAt == null && checkinShownAt == null &&
            now - since >= debounceFor(reason)
        ) {
            warningCount++
            if (warningCount >= maxWarnings) return TickResult.Strike(StrikeReason.THIRD_WARNING)
            warningStartedAt = now
            warnTyping = 0; warnScroll = 0; warnClick = 0
            return TickResult.Warn
        }
        return TickResult.Nothing
    }

    private var mismatchReasonWasShape = false

    private fun debounceFor(reason: StrikeReason): Long =
        if (reason == StrikeReason.OFF_SCREEN && strictness == Strictness.LENIENT) LENIENT_SCREEN_DEBOUNCE_MS else DEBOUNCE_MS

    private fun currentMismatch(now: Long): StrikeReason? {
        val fp = frontPackage ?: return null
        val onAllowed = isAllowedPackage(fp)
        if (blockedAttempt && !onAllowed) return StrikeReason.OFF_APP
        if (isIgnored(fp)) return null
        if (!onAllowed) return StrikeReason.OFF_APP
        if (!watchesPackage(fp)) return null
        val label = currentScreenLabel
        if (label != null && label in blockedLabels) return StrikeReason.OFF_SCREEN
        if (shapeMismatch(now)) return StrikeReason.WRONG_SHAPE
        return null
    }

    private fun minuteMismatch(m: Minute): Boolean = when (shape) {
        ActivityShape.TYPING -> m.typing == 0 && m.scroll >= th.typingScrollMax
        ActivityShape.READING -> m.scroll >= th.readingScrollMax
        ActivityShape.PASSIVE -> m.scroll >= th.passiveScrollMax || m.click >= th.passiveClickMax
        ActivityShape.ANY -> false
    }

    private fun shapeMismatch(now: Long): Boolean {
        if (shape == ActivityShape.ANY || strictness == Strictness.LENIENT) return false
        if (minuteIndex < 2) return false
        val need = if (strictness == Strictness.STRICT) 1 else 2
        val recent = minutes.filter { it.index >= shapeBaselineMinute }.takeLast(need)
        if (recent.size < need || !recent.all { minuteMismatch(it) }) return false
        // While the warning shows, behaving as expected clears it early.
        val w = warningStartedAt
        if (w != null && mismatchReason == StrikeReason.WRONG_SHAPE) {
            val elapsed = (now - w).coerceAtLeast(1)
            val back = when (shape) {
                ActivityShape.TYPING -> warnTyping >= 1
                ActivityShape.READING -> elapsed >= 10_000 && warnScroll * 120_000L <= th.readingScrollMax * elapsed
                ActivityShape.PASSIVE -> elapsed >= 10_000 &&
                    warnScroll * 120_000L <= th.passiveScrollMax * elapsed && warnClick * 120_000L <= th.passiveClickMax * elapsed
                ActivityShape.ANY -> true
            }
            if (back) return false
        }
        return true
    }

    private fun rollMinute(o: Override, now: Long) {
        val idx = ((now - o.startedAt) / 60_000L).toInt()
        if (idx <= minuteIndex) return
        val m = Minute(minuteIndex, typing, scroll, click)
        minutes.addLast(m)
        while (minutes.size > 5) minutes.removeFirst()
        pendingSample = ActivitySample(
            overrideId = o.id, minuteIndex = minuteIndex, frontPackage = frontPackage ?: o.packageName,
            typingEvents = typing, scrollEvents = scroll, clickEvents = click, flaggedScreen = flaggedThisMinute,
        )
        typing = 0; scroll = 0; click = 0; flaggedThisMinute = null
        minuteIndex = idx
    }
}
