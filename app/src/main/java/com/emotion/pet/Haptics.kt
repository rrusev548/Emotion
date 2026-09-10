package com.emotion.pet

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Кратка тактилна обратна връзка при досег с любимеца — работи на всички поддържани версии (minSdk 24). */
object Haptics {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Леко тупване — при докосване на любимеца. */
    fun tick(context: Context) = pulse(context, 12L)

    /** По-осезаемо тупване — при действие от менюто (храна, игра, сън, гали). */
    fun bump(context: Context) = pulse(context, 28L)

    private fun pulse(context: Context, ms: Long) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}
