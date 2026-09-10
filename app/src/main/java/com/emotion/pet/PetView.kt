package com.emotion.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Любимецът: рисува се върху стаята, разхожда се сам, реагира на докосване,
 * може да се влачи с пръст и да се "хвърля" из екрана.
 */
class PetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---------- външни реакции ----------
    var onPetTap: (() -> Unit)? = null
    var onPetLongPress: (() -> Unit)? = null

    // ---------- образ ----------
    private var emoji: String = Presets.EMOJIS.first()
    private var petBitmap: Bitmap? = null
    private var gifDrawable: Drawable? = null

    var petSizePx: Float = dp(110f)
        private set
    var speedScale: Float = 1f
        private set
    var mirrored: Boolean = false
        private set

    // ---------- стая ----------
    private var palette: Presets.Palette = Presets.Palette.NIGHT
    private var wallpaper: Bitmap? = null
    private var bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var bgShaderKey: String = ""

    // ---------- статове ----------
    var fullness: Int = 80
        private set
    var energy: Int = 85
        private set
    var mood: Int = 90
        private set
    var sleeping: Boolean = false
        private set

    // ---------- физика ----------
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var dir = 1
    private var legPhase = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var moveTimer = 0f
    private var resting = false
    private var dragging = false
    private var grabDx = 0f
    private var grabDy = 0f
    private var dragMoved = false
    private var dragStartTime = 0L
    private var lastMoveTime = 0L
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var thrown = false
    private var placed = false
    private var zzzPhase = 0f

    private val rnd = Random(System.nanoTime())

    // ---------- балон с реплика ----------
    private var bubbleText: String = ""
    private var bubbleUntil = 0L
    private var bubbleAlpha = 0f
    private var bubbleLayout: StaticLayout? = null

    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2F3EDFF")
        style = Paint.Style.FILL
    }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A4A8C")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val bubbleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#221A38")
        textSize = dp(14f)
    }
    private val zzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(20f)
    }
    private val scrimPaint = Paint().apply { color = Color.parseColor("#59000000") }
    private val wallpaperPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var wallpaperShader: BitmapShader? = null
    private var shadowShader: RadialGradient? = null
    private var shadowRadius = 0f

    private var lastFrame = SystemClock.uptimeMillis()
    private val frame = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastFrame).coerceIn(1L, 50L).toFloat()
            lastFrame = now
            step(dt)
            invalidate()
            if (isShown && windowVisibility == VISIBLE) postDelayed(this, 16L)
        }
    }

    private val longPressRunnable = Runnable {
        if (dragging && !dragMoved) {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onPetLongPress?.invoke()
        }
    }

    // =================== настройки ===================

    fun applyPrefs(prefs: Prefs) {
        emoji = prefs.emoji
        petSizePx = dp(prefs.sizeDp.toFloat())
        speedScale = prefs.speed
        mirrored = prefs.mirrored
        palette = Presets.Palette.of(prefs.palette)
        setStats(prefs.fullness, prefs.energy, prefs.mood, prefs.sleeping)
        loadSprite(prefs)
        loadWallpaper(prefs)
        shadowShader = null
        bgShaderKey = ""
        clampPos()
        invalidate()
    }

    private fun loadSprite(prefs: Prefs) {
        val key = prefs.spriteType + "|" + prefs.emoji + "|" +
            if (prefs.spriteType == Prefs.TYPE_IMAGE) SpriteStore.file(context).length() else 0L
        if (key == loadedSpriteKey) return
        loadedSpriteKey = key
        if (prefs.spriteType == Prefs.TYPE_IMAGE && SpriteStore.exists(context)) {
            petBitmap = SpriteStore.loadBitmap(context, 512)
            gifDrawable = SpriteStore.loadAnimated(context)
        } else {
            petBitmap = null
            gifDrawable = null
        }
    }

    private fun loadWallpaper(prefs: Prefs) {
        val path = prefs.wallpaperPath
        if (path == loadedWallpaperPath) return
        loadedWallpaperPath = path
        wallpaper = if (path != null) {
            android.graphics.BitmapFactory.decodeFile(path)
        } else {
            null
        }
        wallpaperShader = null
        bgShaderKey = ""
    }

    fun setStats(fullness: Int, energy: Int, mood: Int, sleeping: Boolean) {
        this.fullness = fullness
        this.energy = energy
        this.mood = mood
        val wasSleeping = this.sleeping
        this.sleeping = sleeping
        if (wasSleeping && !sleeping) {
            say(context.getString(R.string.pet_wake_line), 2200)
        }
        invalidate()
    }

    fun avatarBitmap(): Bitmap? = petBitmap

    /** true, докато потребителят влачи любимеца с пръст. */
    val isDragging: Boolean get() = dragging

    /** Текущ кратък статус за балончето според статовете. */
    fun needsLine(ctx: Context): String = when {
        sleeping -> ctx.getString(R.string.pet_sleep_line)
        fullness < 30 -> ctx.resources.getStringArray(R.array.pet_lines_hungry).random()
        energy < 30 -> ctx.resources.getStringArray(R.array.pet_lines_sleepy).random()
        mood < 35 -> ctx.resources.getStringArray(R.array.pet_lines_idle).random()
        else -> ctx.resources.getStringArray(R.array.pet_lines_idle).random()
    }

    // =================== реплики ===================

    fun say(text: String, durationMs: Long = 2600) {
        bubbleText = text
        bubbleUntil = SystemClock.uptimeMillis() + durationMs
        bubbleLayout = null
        bubbleAlpha = 1f
        invalidate()
    }

    // =================== живот ===================

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = SystemClock.uptimeMillis()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        removeCallbacks(frame)
        if (visibility == VISIBLE) {
            lastFrame = SystemClock.uptimeMillis()
            postDelayed(frame, 16L)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 || oldh == 0) {
            x = w / 2f
            y = h * 0.62f
            placed = true
        }
        bgShaderKey = ""
        wallpaperShader = null
        shadowShader = null
        clampPos()
    }

    private fun step(dt: Float) {
        val now = SystemClock.uptimeMillis()

        // балон
        val left = bubbleUntil - now
        bubbleAlpha = when {
            bubbleText.isEmpty() -> 0f
            left <= 0 -> {
                bubbleText = ""
                0f
            }
            left < 350 -> left / 350f
            else -> 1f
        }

        if (dragging) {
            return
        }

        if (width == 0 || height == 0) return

        // инерция след хвърляне
        if (thrown) {
            val decay = Math.pow(0.93, (dt / 16.67).toDouble()).toFloat()
            vx *= decay
            vy *= decay
            if (abs(vx) < 6f && abs(vy) < 6f) {
                vx = 0f
                vy = 0f
                thrown = false
            }
        }

        if (sleeping) {
            vx *= 0.86f
            vy *= 0.86f
            zzzPhase += dt / 900f
        } else if (!thrown) {
            moveTimer -= dt
            if (moveTimer <= 0f) pickTarget()

            val dx = targetX - x
            val dy = targetY - y
            val dist = sqrt(dx * dx + dy * dy)
            val maxSpeed = petSizePx * 1.9f * speedScale * energyFactor()
            if (!resting && dist > 8f) {
                val desired = min(maxSpeed, dist * 2.4f)
                val dvx = dx / dist * desired
                val dvy = dy / dist * desired
                val accel = 0.12f * (dt / 16.67f)
                vx += (dvx - vx) * accel
                vy += (dvy - vy) * accel
            } else {
                vx *= 0.88f
                vy *= 0.88f
            }
        }

        val stepX = vx * dt / 1000f
        val stepY = vy * dt / 1000f
        x += stepX
        y += stepY

        val speed = sqrt(vx * vx + vy * vy)
        if (absorbWalls()) {
            // отблъскване от стените
        }
        if (speed > 12f) {
            legPhase += dt / 1000f * (5f + speed / 60f)
            if (abs(vx) > 8f) dir = if (vx > 0) 1 else -1
        }
    }

    /** Връща true, ако любимецът удари стена. */
    private fun absorbWalls(): Boolean {
        if (width == 0 || height == 0) return false
        val half = petSizePx / 2f
        val leftBound = half + dp(8f)
        val rightBound = width - half - dp(8f)
        val topBound = dp(64f) + half
        val bottomBound = height - dp(84f) - half
        var hit = false
        if (x < leftBound) {
            x = leftBound; vx = abs(vx) * 0.6f; hit = true
        }
        if (x > rightBound) {
            x = rightBound; vx = -abs(vx) * 0.6f; hit = true
        }
        if (y < topBound) {
            y = topBound; vy = abs(vy) * 0.6f; hit = true
        }
        if (y > bottomBound) {
            y = bottomBound; vy = -abs(vy) * 0.6f; hit = true
        }
        return hit
    }

    private fun clampPos() {
        absorbWalls()
    }

    private fun pickTarget() {
        val half = petSizePx / 2f
        val leftBound = half + dp(12f)
        val rightBound = max(leftBound, width - half - dp(12f))
        val topBound = dp(66f) + half
        val bottomBound = max(topBound, height - dp(88f) - half)
        targetX = leftBound + rnd.nextFloat() * (rightBound - leftBound)
        targetY = topBound + rnd.nextFloat() * (bottomBound - topBound)
        resting = rnd.nextFloat() < 0.4f
        val base = if (resting) 1600 else 2600
        moveTimer = (base + rnd.nextInt(0, 2600)).toFloat()
    }

    private fun energyFactor(): Float = when {
        energy < 20 -> 0.45f
        energy < 45 -> 0.75f
        fullness < 20 -> 0.6f
        mood < 25 -> 0.7f
        else -> 1f
    }

    /** Пръстът докосна празна част от стаята — любимецът отива там. */
    fun walkTo(px: Float, py: Float) {
        if (sleeping) return
        val half = petSizePx / 2f
        targetX = px.coerceIn(half + dp(8f), max(half + dp(8f), width - half - dp(8f)))
        targetY = py.coerceIn(dp(64f) + half, max(dp(64f) + half, height - dp(84f) - half))
        resting = false
        thrown = false
        moveTimer = 4000f
    }

    // =================== рисуване ===================

    override fun onDraw(canvas: Canvas) {
        drawRoom(canvas)
        drawShadow(canvas)
        drawPet(canvas)
        if (sleeping) drawZzz(canvas)
        drawBubble(canvas)
    }

    private fun drawRoom(canvas: Canvas) {
        val wp = wallpaper
        val shader = wallpaperShader
        if (wp != null) {
            if (shader == null) {
                val m = Matrix()
                val scale = max(width / wp.width.toFloat(), height / wp.height.toFloat())
                val dx = (width - wp.width * scale) / 2f
                val dy = (height - wp.height * scale) / 2f
                m.setScale(scale, scale)
                m.postTranslate(dx, dy)
                wallpaperShader = BitmapShader(wp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    .also { it.setLocalMatrix(m) }
            }
            wallpaperPaint.shader = wallpaperShader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), wallpaperPaint)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        } else {
            val key = "${palette.name}_$width" + "x$height"
            if (key != bgShaderKey) {
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    palette.top, palette.bottom, Shader.TileMode.CLAMP
                )
                bgShaderKey = key
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }
    }

    private fun drawShadow(canvas: Canvas) {
        val s = petSizePx
        if (shadowShader == null || abs(shadowRadius - s) > 1f) {
            shadowRadius = s
            shadowShader = RadialGradient(
                0f, 0f, s * 0.36f,
                intArrayOf(Color.parseColor("#66000000"), Color.TRANSPARENT),
                floatArrayOf(0.2f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val sc = shadowShader ?: return
        shadowPaint.shader = sc
        canvas.save()
        canvas.translate(x, y + s * 0.46f)
        canvas.drawCircle(0f, 0f, s * 0.36f, shadowPaint)
        canvas.restore()
    }

    private fun drawPet(canvas: Canvas) {
        val s = petSizePx
        val speed = sqrt(vx * vx + vy * vy)
        val walking = speed > 12f && !dragging
        val bob = if (walking) abs(kotlin.math.sin(legPhase * 3.2f)) * s * 0.035f else if (sleeping) kotlin.math.sin(zzzPhase * 2f) * s * 0.01f else 0f
        val lean = if (walking) dir * 0.06f else 0f
        val half = s / 2f

        canvas.save()
        canvas.translate(x, y - bob)
        val flip = (if (dir < 0) -1f else 1f) * (if (mirrored) -1f else 1f)
        canvas.scale(flip, 1f)
        canvas.rotate(lean * 57.3f * 0.5f)

        val gif = gifDrawable
        when {
            gif != null -> {
                gif.setBounds((-half).toInt(), (-half).toInt(), half.toInt(), half.toInt())
                gif.draw(canvas)
            }

            petBitmap != null -> {
                val r = RectF(-half, -half, half, half)
                canvas.drawBitmap(petBitmap!!, null, r, spritePaint)
            }

            else -> {
                emojiPaint.textSize = s * 0.86f
                val fm = emojiPaint.fontMetrics
                canvas.drawText(emoji, 0f, -(fm.ascent + fm.descent) / 2f, emojiPaint)
            }
        }
        canvas.restore()
    }

    private fun drawZzz(canvas: Canvas) {
        val s = petSizePx
        zzzPaint.textSize = s * 0.2f
        val drift = (kotlin.math.sin(zzzPhase) * 0.5f + 0.5f)
        canvas.drawText(
            "💤",
            x + s * 0.32f + drift * s * 0.06f,
            y - s * 0.42f - drift * s * 0.12f,
            zzzPaint
        )
    }

    private fun drawBubble(canvas: Canvas) {
        if (bubbleText.isEmpty() || bubbleAlpha <= 0.02f) return
        val s = petSizePx
        val maxWidth = min(width * 0.72f, dp(260f)).toInt().coerceAtLeast(120)
        var layout = bubbleLayout
        if (layout == null || lastBubbleLayoutWidth != maxWidth) {
            layout = StaticLayout.Builder
                .obtain(bubbleText, 0, bubbleText.length, bubbleTextPaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(4)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(dp(2f), 1f)
                .build()
            bubbleLayout = layout
            lastBubbleLayoutWidth = maxWidth
        }
        val padH = dp(12f)
        val padV = dp(9f)
        val bw = layout.width + padH * 2
        val bh = layout.height + padV * 2
        var left = x - bw / 2f
        left = left.coerceIn(dp(6f), max(dp(6f), width - bw - dp(6f)))
        val top = max(dp(6f), y - s * 0.62f - bh)
        val rect = RectF(left, top, left + bw, top + bh)

        val oldAlpha = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255 * bubbleAlpha).toInt())
        bubblePaint.color = Color.parseColor("#F2F3EDFF")
        canvas.drawRoundRect(rect, dp(16f), dp(16f), bubblePaint)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), bubbleStroke)
        // "опашка" към любимеца
        val inset = dp(14f)
        val tailX = if (rect.width() > inset * 2.5f) {
            x.coerceIn(rect.left + inset, rect.right - inset)
        } else {
            rect.centerX()
        }
        val path = android.graphics.Path()
        path.moveTo(tailX - dp(7f), rect.bottom - dp(1f))
        path.lineTo(tailX + dp(7f), rect.bottom - dp(1f))
        path.lineTo(tailX, rect.bottom + dp(9f))
        path.close()
        canvas.drawPath(path, bubblePaint)
        canvas.save()
        canvas.translate(rect.left + padH, rect.top + padV)
        layout.draw(canvas)
        canvas.restore()
        canvas.restoreToCount(oldAlpha)
    }

    private var lastBubbleLayoutWidth = 0
    private var loadedSpriteKey: String? = null
    private var loadedWallpaperPath: String? = "___none___"

    // =================== докосване ===================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hitPet(event.x, event.y)) return false
                dragging = true
                dragMoved = false
                dragStartTime = SystemClock.uptimeMillis()
                lastMoveTime = dragStartTime
                lastMoveX = event.x
                lastMoveY = event.y
                grabDx = x - event.x
                grabDy = y - event.y
                vx = 0f
                vy = 0f
                thrown = false
                postDelayed(longPressRunnable, 550L)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val nx = event.x + grabDx
                val ny = event.y + grabDy
                if (!dragMoved &&
                    (abs(event.x - lastMoveX) > dp(6f) || abs(event.y - lastMoveY) > dp(6f))
                ) {
                    dragMoved = true
                    removeCallbacks(longPressRunnable)
                }
                val now = SystemClock.uptimeMillis()
                val dtMs = (now - lastMoveTime).coerceAtLeast(8L)
                vx = ((nx - x) / dtMs * 1000f).coerceIn(-2600f, 2600f)
                vy = ((ny - y) / dtMs * 1000f).coerceIn(-2600f, 2600f)
                x = nx
                y = ny
                lastMoveTime = now
                clampPos()
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dragging = false
                removeCallbacks(longPressRunnable)
                parent?.requestDisallowInterceptTouchEvent(false)
                val quick = SystemClock.uptimeMillis() - dragStartTime < 400L
                if (!dragMoved && quick) {
                    performClick()
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onPetTap?.invoke()
                } else {
                    thrown = true
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                removeCallbacks(longPressRunnable)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitPet(ex: Float, ey: Float): Boolean {
        val r = petSizePx * 0.58f
        return abs(ex - x) <= r && abs(ey - y) <= r
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
