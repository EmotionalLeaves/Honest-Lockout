package com.honestlockout.app.engine

import com.honestlockout.app.data.db.entities.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.BitSet

/** All day boundaries use the phone's time zone (spec, Troubleshooting: UTC vs local midnight). */
object DayClock {
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun zone(): ZoneId = ZoneId.systemDefault()
    fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone())
    fun localDate(ms: Long): LocalDate = zoned(ms).toLocalDate()
    fun dateKey(ms: Long): String = localDate(ms).format(dateFmt)
    fun dateKey(date: LocalDate): String = date.format(dateFmt)
    fun parseDate(key: String): LocalDate = LocalDate.parse(key, dateFmt)
    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone()).toInstant().toEpochMilli()
    fun startOfDay(ms: Long): Long = startOfDay(localDate(ms))
    fun nextMidnight(ms: Long): Long = startOfDay(localDate(ms).plusDays(1))
    /** 0 = Monday … 6 = Sunday. */
    fun dayIndex(ms: Long): Int = zoned(ms).dayOfWeek.value - 1
    fun minuteOfDay(ms: Long): Int = zoned(ms).let { it.hour * 60 + it.minute }
    fun isMonday(ms: Long): Boolean = dayIndex(ms) == 0
}

object ScheduleLogic {
    private fun listed(s: Schedule, day: Int): Boolean = s.days.getOrNull(day) == '1'

    /**
     * Same-day window: active when start <= m < end on a listed day. A window whose end is at or
     * before its start crosses midnight: active when m >= start on a listed day, or m < end on the
     * day after a listed day.
     */
    fun isActive(s: Schedule, now: Long): Boolean {
        val day = DayClock.dayIndex(now)
        val m = DayClock.minuteOfDay(now)
        return if (s.endMinute > s.startMinute) {
            listed(s, day) && m >= s.startMinute && m < s.endMinute
        } else {
            (listed(s, day) && m >= s.startMinute) || (listed(s, (day + 6) % 7) && m < s.endMinute)
        }
    }

    /** End of the active window containing [now] (call only when [isActive] is true). */
    fun endOfActiveWindow(s: Schedule, now: Long): Long {
        val today = DayClock.localDate(now)
        val m = DayClock.minuteOfDay(now)
        val endDate = if (s.endMinute > s.startMinute || m < s.endMinute) today else today.plusDays(1)
        return DayClock.startOfDay(endDate) + s.endMinute * 60_000L
    }

    /** Set of active minutes across the week (bit i = minute i from Monday 00:00). */
    fun weekMinutes(s: Schedule): BitSet {
        val bits = BitSet(WEEK)
        for (day in 0 until 7) {
            if (!listed(s, day)) continue
            if (s.endMinute > s.startMinute) {
                bits.set(day * 1440 + s.startMinute, day * 1440 + s.endMinute)
            } else {
                bits.set(day * 1440 + s.startMinute, day * 1440 + 1440)
                val next = (day + 1) % 7
                bits.set(next * 1440, next * 1440 + s.endMinute)
            }
        }
        return bits
    }

    /** True when [new] covers every minute [old] covered (i.e. not fewer days or shorter hours). */
    fun covers(new: Schedule, old: Schedule): Boolean {
        val missing = weekMinutes(old).clone() as BitSet
        missing.andNot(weekMinutes(new))
        return missing.isEmpty
    }

    fun hhmm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    private const val WEEK = 7 * 1440
}
