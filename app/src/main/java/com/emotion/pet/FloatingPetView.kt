package com.emotion.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View

/**
 * Истинският Peta, рисуван за системния overlay прозорец — образ, мигане и
 * дишане като [MiniPetOverlay], но позицията му се движи през
 * WindowManager.LayoutParams (грижа на [OverlayService]), не през canvas
 * translate — прозорецът е точно с размера на любимеца, за да не блокира
 * докосвания извън него.
 */
class FloatingPetView(context: Context) : View(context) {

    val sizePx = dp(64f)
    private var petBitmap: Bitmap? = null
    private var emoji: String = "😈"
    private var accent: Int = Color.RED
    private var idlePhase = 0f
    private val blink = PetBlink()

    var dragging = false
    var squash = 0f
    var squashAxis = 0

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowShader: RadialGradient? = null

    init {
        val prefs = Prefs(context)
        val pet = Presets.pet(prefs.petId)
        emoji = prefs.emoji.ifBlank { pet.emoji }
        accent = pet.accent
        val customImage = prefs.spriteType == Prefs.TYPE_IMAGE && SpriteStore.exists(context)
        petBitmap = when {
            customImage -> SpriteStore.loadBitmap(context, 128)
            pet.spriteRes != null -> BitmapFactory.decodeResource(resources, pet.spriteRes)
            else -> null
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = sizePx.toInt()
        setMeasuredDimension(s, s)
    }

    /** Мигане и дишане вървят непрекъснато — извиква се от физическия loop на OverlayService. */
    fun stepIdle(dt: Float) {
        idlePhase += dt / 1000f
        blink.step(SystemClock.uptimeMillis())
        if (squash > 0f) {
            squash *= Math.pow(0.86, (dt / 16.67).coerceIn(0.2, 3.0)).toFloat()
            if (squash < 0.01f) squash = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = sizePx
        val half = s / 2f
        val cx = half
        val cy = half

        if (shadowShader == null) {
            shadowShader = RadialGradient(
                0f, 0f, half * 0.9f,
                intArrayOf(Color.parseColor("#66000000"), Color.TRANSPARENT),
                floatArrayOf(0.1f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.translate(cx, cy + half * 0.62f)
        shadowPaint.shader = shadowShader
        canvas.drawCircle(0f, 0f, half * 0.9f, shadowPaint)
        canvas.restore()

        val sx = if (squashAxis == 0) 1f + 0.22f * squash else 1f - 0.30f * squash
        val sy = if (squashAxis == 0) 1f - 0.30f * squash else 1f + 0.22f * squash
        val idleBreath = if (!dragging) PetMotion.idleBreath(idlePhase, s) else 0f

        canvas.save()
        canvas.translate(cx, cy - idleBreath)
        canvas.scale(sx, sy)
        val bmp = petBitmap
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(-half, -half, half, half), bodyPaint)
            blink.draw(canvas, half, accent, SystemClock.uptimeMillis())
        } else {
            emojiPaint.textSize = s * 0.86f
            val fm = emojiPaint.fontMetrics
            canvas.drawText(emoji, 0f, -(fm.ascent + fm.descent) / 2f, emojiPaint)
        }
        canvas.restore()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
