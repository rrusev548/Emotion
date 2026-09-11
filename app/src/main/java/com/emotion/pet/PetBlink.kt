package com.emotion.pet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

/** Лека непрекъсната "въздишка" — споделена между PetView и MiniPetOverlay. */
object PetMotion {
    fun idleBreath(idlePhase: Float, sizePx: Float): Float =
        sin(idlePhase * 1.8f) * sizePx * 0.014f
}

/**
 * Кратко периодично "затваряне на очите" върху вграден pet bitmap — прави
 * PetView и MiniPetOverlay да изглеждат живи, не като замръзнала снимка.
 * Всяка инстанция си пази собствен таймер, за да не мигат синхронно.
 */
class PetBlink {

    private var nextBlinkAt = 0L
    private var blinkStartedAt = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random(System.nanoTime())

    fun step(now: Long) {
        if (nextBlinkAt == 0L) nextBlinkAt = now + MIN_GAP_MS + rnd.nextInt(GAP_JITTER_MS)
        if (now >= nextBlinkAt) {
            blinkStartedAt = now
            nextBlinkAt = now + DURATION_MS + MIN_GAP_MS + rnd.nextInt(GAP_JITTER_MS)
        }
    }

    /** [half] е половината от размера на bitmap-а (координатите са спрямо неговия център). */
    fun draw(canvas: Canvas, half: Float, accent: Int, now: Long) {
        val amount = amountAt(now)
        if (amount <= 0.02f) return
        val eyeW = half * 0.30f
        val eyeH = half * 0.16f * amount
        val eyeY = -half * 0.18f
        paint.color = Color.argb(
            (215 * amount).toInt().coerceIn(0, 255),
            (Color.red(accent) * 0.55f).toInt(),
            (Color.green(accent) * 0.45f).toInt(),
            (Color.blue(accent) * 0.45f).toInt()
        )
        for (sign in intArrayOf(-1, 1)) {
            val ex = sign * half * 0.20f
            canvas.drawRoundRect(
                ex - eyeW / 2f, eyeY - eyeH / 2f, ex + eyeW / 2f, eyeY + eyeH / 2f,
                eyeH * 0.5f, eyeH * 0.5f, paint
            )
        }
    }

    private fun amountAt(now: Long): Float {
        val elapsed = now - blinkStartedAt
        if (elapsed < 0L || elapsed > DURATION_MS) return 0f
        val t = elapsed / DURATION_MS.toFloat()
        return if (t < 0.5f) t * 2f else (1f - t) * 2f
    }

    private companion object {
        const val MIN_GAP_MS = 2200L
        const val GAP_JITTER_MS = 3200
        const val DURATION_MS = 140L
    }
}
