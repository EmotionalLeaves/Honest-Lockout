package com.honestlockout.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.honestlockout.app.MainActivity
import com.honestlockout.app.R
import com.honestlockout.app.data.db.entities.Override

/** Notification channels and builders. The override notification is channel "override" (LOW). */
object Notifications {
    const val CHANNEL_OVERRIDE = "override"
    const val CHANNEL_WARNING = "warning"
    const val CHANNEL_INFO = "info"
    const val ID_OVERRIDE = 1001
    const val ID_WARNING = 1002
    const val ID_INFO = 1003

    private val smallIcon = android.R.drawable.ic_lock_idle_lock

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERRIDE, context.getString(R.string.channel_override), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WARNING, context.getString(R.string.channel_warning), NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, context.getString(R.string.channel_info), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun serviceAction(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getForegroundService(
            context, code, OverrideForegroundService.intent(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context, route: String?, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code, MainActivity.routeIntent(context, route),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun overrideStarting(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_OVERRIDE)
            .setSmallIcon(smallIcon)
            .setContentTitle(context.getString(R.string.notif_override_title))
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun updateOverride(context: Context, o: Override, now: Long) {
        val elapsed = ((now - o.startedAt) / 60_000L).toInt().coerceIn(0, o.plannedMin)
        val n = NotificationCompat.Builder(context, CHANNEL_OVERRIDE)
            .setSmallIcon(smallIcon)
            .setContentTitle(o.statement)
            .setContentText(context.getString(R.string.notif_override_progress, elapsed, o.plannedMin))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context, null, 10))
            .addAction(0, context.getString(R.string.notif_done), serviceAction(context, OverrideForegroundService.ACTION_DONE, 11))
            .addAction(0, context.getString(R.string.notif_extend), openApp(context, MainActivity.ROUTE_EXTEND, 12))
            .build()
        notify(context, ID_OVERRIDE, n)
    }

    fun showWarning(context: Context, statement: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(smallIcon)
            .setContentTitle(context.getString(R.string.notif_warning_title))
            .setContentText(context.getString(R.string.notif_warning_text, statement))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.banner_back_to_it), serviceAction(context, OverrideForegroundService.ACTION_BACK_TO_IT, 21))
            .addAction(0, context.getString(R.string.banner_end_it), serviceAction(context, OverrideForegroundService.ACTION_CONFESS, 22))
            .build()
        notify(context, ID_WARNING, n)
    }

    fun showCheckin(context: Context, statement: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(smallIcon)
            .setContentTitle(context.getString(R.string.notif_checkin_title))
            .setContentText(context.getString(R.string.notif_checkin_text, statement))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.banner_yes), serviceAction(context, OverrideForegroundService.ACTION_CHECKIN_YES, 31))
            .addAction(0, context.getString(R.string.banner_end_it), serviceAction(context, OverrideForegroundService.ACTION_CONFESS, 32))
            .build()
        notify(context, ID_WARNING, n)
    }

    fun cancelWarning(context: Context) = NotificationManagerCompat.from(context).cancel(ID_WARNING)

    /** [silent] for listening overrides, so a timeout doesn't wake you up. */
    fun showTimesUp(context: Context, statement: String, silent: Boolean = false) {
        val n = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(smallIcon)
            .setSilent(silent)
            .setContentTitle(context.getString(R.string.notif_times_up, statement))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, null, 40))
            .build()
        notify(context, ID_INFO, n)
    }

    private fun notify(context: Context, id: Int, n: Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) runCatching { nm.notify(id, n) }
    }
}
