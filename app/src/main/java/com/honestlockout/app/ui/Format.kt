package com.honestlockout.app.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import com.honestlockout.app.R
import com.honestlockout.app.engine.DayClock
import java.util.Date

/** Shared formatting for Compose screens, overlays and notifications. Respects 12/24 h. */
object Format {
    fun time(context: Context, ms: Long): String = DateFormat.getTimeFormat(context).format(Date(ms))

    /** "3:42 pm" today, else "Tue 3:42 pm" / a medium date for further away. */
    fun dateTime(context: Context, ms: Long): String {
        val now = System.currentTimeMillis()
        val sameDay = DayClock.dateKey(ms) == DayClock.dateKey(now)
        if (sameDay) return time(context, ms)
        val days = (DayClock.localDate(ms).toEpochDay() - DayClock.localDate(now).toEpochDay())
        val day = if (days in -6..6) DateFormat.format("EEE", Date(ms)).toString() else DateFormat.getMediumDateFormat(context).format(Date(ms))
        return "$day ${time(context, ms)}"
    }

    fun date(context: Context, ms: Long): String = DateFormat.getMediumDateFormat(context).format(Date(ms))

    /** "45 min", "2 h", "1 h 30 min". */
    fun minutes(context: Context, minutes: Int): String = when {
        minutes < 60 -> context.getString(R.string.fmt_minutes, minutes)
        minutes % 60 == 0 -> context.getString(R.string.fmt_hours, minutes / 60)
        else -> context.getString(R.string.fmt_hours_minutes, minutes / 60, minutes % 60)
    }

    fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun appIcon(context: Context, packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0); true
    }.getOrDefault(false)
}
