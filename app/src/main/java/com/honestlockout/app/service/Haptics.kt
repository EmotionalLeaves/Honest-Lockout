package com.honestlockout.app.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short, distinct vibrations so a lock or a warning is felt even when the overlay is missed. */
object Haptics {
    private fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    fun tap(context: Context) = vibrator(context)?.let { runCatching { it.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) } }

    fun warn(context: Context) = vibrator(context)?.let {
        runCatching { it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80, 60, 160), -1)) }
    }
}
