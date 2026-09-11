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
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Peta, но малък и физически — вижда се върху всеки екран на приложението
 * (освен стаята, където си има голямата интерактивна версия). Пада заради
 * гравитация, отскача от ръбовете на екрана, влачи се с пръст.
 *
 * Позицията/скоростта се пазят в [State], споделен обект — така любимецът
 * "продължава" от същото място и движение, докато преминаваш между екраните.
 */
class MiniPetOverlay(
    context: Context,
    private val onTap: () -> Unit
) : View(context) {

    object State {
        var x = -1f
        var y = -1f
        var vx = 0f
        var vy = 0f
    }

    private val sizePx = dp(56f)
    private var petBitmap: Bitmap? = null
    private var emoji: String = "😈"

    private var dragging = false
    private var dragMoved = false
    private var grabDx = 0f
    private var grabDy = 0f
    private var lastMoveTime = 0L
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var downAt = 0L
    private var squash = 0f
    private var squashAxis = 0

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowShader: RadialGradient? = null

    private var lastFrame = SystemClock.uptimeMillis()
    private val frame = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastFrame).coerceIn(1L, 50L).toFloat()
            lastFrame = now
            step(dt)
            invalidate()
            if (isShown) postDelayed(this, 16L)
        }
    }

    init {
        val prefs = Prefs(context)
        val pet = Presets.pet(prefs.petId)
        emoji = prefs.emoji.ifBlank { pet.emoji }
        val key = prefs.spriteType + "|" + prefs.petId + "|" +
            if (prefs.spriteType == Prefs.TYPE_IMAGE) SpriteStore.file(context).length() else 0L
        petBitmap = if (key == SpriteCache.key) {
            SpriteCache.bitmap
        } else {
            val res = pet.spriteRes
            val loaded = when {
                prefs.spriteType == Prefs.TYPE_IMAGE && SpriteStore.exists(context) ->
                    SpriteStore.loadBitmap(context, 128)

                res != null -> BitmapFactory.decodeResource(resources, res)
                else -> null
            }
            SpriteCache.key = key
            SpriteCache.bitmap = loaded
            loaded
        }
    }

    /** Всеки екран пресъздава MiniPetOverlay при onResume — не декодираме образа наново, ако не се е сменил. */
    private object SpriteCache {
        var key: String? = null
        var bitmap: Bitmap? = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = SystemClock.uptimeMillis()
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (State.x < 0f || State.y < 0f) {
            // първо появяване — долу вдясно, извън бар-а за долна навигация
            State.x = w - sizePx
            State.y = h * 0.4f
        }
        clampToBounds()
    }

    private fun step(dt: Float) {
        if (dragging || width == 0 || height == 0) return

        squash *= Math.pow(0.86, (dt / 16.67).coerceIn(0.2, 3.0)).toFloat()
        if (squash < 0.01f) squash = 0f

        State.vy += dp(PetView.GRAVITY_DP_PER_S2) * dt / 1000f
        State.vy = State.vy.coerceAtMost(dp(PetView.TERMINAL_VELOCITY_DP))
        State.vx *= Math.pow(0.995, (dt / 16.67).coerceIn(0.2, 3.0)).toFloat()

        State.x += State.vx * dt / 1000f
        State.y += State.vy * dt / 1000f

        collideBounds()
    }

    private fun collideBounds() {
        val half = sizePx / 2f
        val left = half
        val right = (width - half).coerceAtLeast(half)
        val top = half
        val bottom = (height - half).coerceAtLeast(half)

        if (State.x < left) {
            State.x = left
            State.vx = abs(State.vx) * RESTITUTION
            bounceSquash(0)
        } else if (State.x > right) {
            State.x = right
            State.vx = -abs(State.vx) * RESTITUTION
            bounceSquash(0)
        }

        if (State.y < top) {
            State.y = top
            State.vy = abs(State.vy) * RESTITUTION
            bounceSquash(1)
        } else if (State.y > bottom) {
            State.y = bottom
            State.vy = -abs(State.vy) * RESTITUTION
            bounceSquash(1)
        }
    }

    private fun bounceSquash(axis: Int) {
        if (speed() < dp(60f)) return
        squash = 1f
        squashAxis = axis
    }

    private fun clampToBounds() {
        val half = sizePx / 2f
        if (width > 0) State.x = State.x.coerceIn(half, (width - half).coerceAtLeast(half))
        if (height > 0) State.y = State.y.coerceIn(half, (height - half).coerceAtLeast(half))
    }

    private fun speed(): Float = sqrt(State.vx * State.vx + State.vy * State.vy)

    /** Само наистина ли пипаш топката — иначе този full-screen view би блокирал всичко под себе си. */
    private fun hitBall(ex: Float, ey: Float): Boolean {
        val r = sizePx * 0.65f
        return abs(ex - State.x) <= r && abs(ey - State.y) <= r
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hitBall(event.x, event.y)) return false
                dragging = true
                dragMoved = false
                downAt = SystemClock.uptimeMillis()
                lastMoveTime = downAt
                lastMoveX = event.x
                lastMoveY = event.y
                grabDx = State.x - event.x
                grabDy = State.y - event.y
                State.vx = 0f
                State.vy = 0f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                if (!dragMoved &&
                    (abs(event.x - lastMoveX) > dp(6f) || abs(event.y - lastMoveY) > dp(6f))
                ) {
                    dragMoved = true
                }
                val nx = event.x + grabDx
                val ny = event.y + grabDy
                val now = SystemClock.uptimeMillis()
                val dtMs = (now - lastMoveTime).coerceAtLeast(8L)
                State.vx = ((nx - State.x) / dtMs * 1000f).coerceIn(-2600f, 2600f)
                State.vy = ((ny - State.y) / dtMs * 1000f).coerceIn(-2600f, 2600f)
                State.x = nx
                State.y = ny
                lastMoveTime = now
                lastMoveX = event.x
                lastMoveY = event.y
                clampToBounds()
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dragging = false
                val quick = SystemClock.uptimeMillis() - downAt < 350L
                if (!dragMoved && quick) {
                    performClick()
                    onTap()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val s = sizePx
        val half = s / 2f

        if (shadowShader == null) {
            shadowShader = RadialGradient(
                0f, 0f, half * 0.9f,
                intArrayOf(Color.parseColor("#66000000"), Color.TRANSPARENT),
                floatArrayOf(0.1f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.translate(State.x, State.y + half * 0.55f)
        shadowPaint.shader = shadowShader
        canvas.drawCircle(0f, 0f, half * 0.9f, shadowPaint)
        canvas.restore()

        val sx = if (squashAxis == 0) 1f + 0.22f * squash else 1f - 0.30f * squash
        val sy = if (squashAxis == 0) 1f - 0.30f * squash else 1f + 0.22f * squash

        canvas.save()
        canvas.translate(State.x, State.y)
        canvas.scale(sx, sy)
        val bmp = petBitmap
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(-half, -half, half, half), bodyPaint)
        } else {
            emojiPaint.textSize = s * 0.86f
            val fm = emojiPaint.fontMetrics
            canvas.drawText(emoji, 0f, -(fm.ascent + fm.descent) / 2f, emojiPaint)
        }
        canvas.restore()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val RESTITUTION = 0.62f
    }
}
