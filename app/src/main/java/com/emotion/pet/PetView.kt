package com.emotion.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Любимецът: живее в модерна стая, разхожда се сам, отскача от стените
 * с squash анимация, реагира на докосване и може да се хвърля с пръст.
 */
class PetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        // Samsung и някои други устройства крашват нативно (SIGSEGV в Skia),
        // когато emoji се рисува с drawText върху hardware-accelerated Canvas.
        // Software слой за целия изглед е най-надеждният начин да се избегне това.
        // Анимацията тук е лека — няколко кръга + текст — така че няма проблем с производителността.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    // ---------- външни реакции ----------
    var onPetTap: (() -> Unit)? = null
    var onPetLongPress: (() -> Unit)? = null
    var onBounce: (() -> Unit)? = null

    // ---------- образ ----------
    private var emoji: String = Presets.PETS.first().emoji
    private var petBitmap: Bitmap? = null
    private var gifDrawable: Drawable? = null

    var petSizePx: Float = dp(110f)
        private set
    var speedScale: Float = 1f
        private set
    var mirrored: Boolean = false
        private set
    var bounceEnabled: Boolean = true
        private set
    var particlesEnabled: Boolean = true
        private set
    var accent: Int = Presets.PETS.first().accent
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
    private var dash = 1f
    private var dragging = false
    private var grabDx = 0f
    private var grabDy = 0f
    private var dragMoved = false
    private var dragStartTime = 0L
    private var lastMoveTime = 0L
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var thrown = false
    private var zzzPhase = 0f
    private var squash = 0f
    private var squashAxis = 0          // 0 = хоризонтална стена, 1 = вертикална
    private var bounceCount = 0
    private var glowPhase = 0f
    private var ambientPhase = 0f

    private val rnd = Random(System.nanoTime())

    // ---------- ефекти ----------
    private class Ring(var cx: Float, var cy: Float, var started: Long, var axis: Int)
    private class Spark(var cx: Float, var cy: Float, var vx: Float, var vy: Float, var started: Long)
    private class Dust(var cx: Float, var cy: Float, var r: Float, var speed: Float, var phase: Float, var seed: Float)

    private val rings = ArrayList<Ring>(4)
    private val sparks = ArrayList<Spark>(24)
    private val dust = ArrayList<Dust>(18)

    // ---------- балон с реплика ----------
    private var bubbleText: String = ""
    private var bubbleUntil = 0L
    private var bubbleAlpha = 0f
    private var bubbleLayout: StaticLayout? = null
    private var lastBubbleLayoutWidth = 0

    private var loadedSpriteKey: String? = null
    private var loadedWallpaperPath: String? = "___none___"

    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val bubbleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#211836")
        textSize = dp(14.5f)
    }
    private val zzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val scrimPaint = Paint().apply { color = Color.parseColor("#66000000") }
    private val wallpaperPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var wallpaperShader: BitmapShader? = null
    private var shadowShader: RadialGradient? = null
    private var glowShader: RadialGradient? = null
    private var blobShaders = HashMap<Int, RadialGradient>()
    private var floorShader: RadialGradient? = null
    private var floorKey = ""
    private var lastHitAt = 0L
    private var lastHitAxis = -1
    private var ambient = emptyList<FloatArray>()

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
        bounceEnabled = prefs.bounce
        particlesEnabled = prefs.particles
        accent = Presets.pet(prefs.petId).accent
        palette = Presets.Palette.of(prefs.palette)
        setStats(prefs.fullness, prefs.energy, prefs.mood, prefs.sleeping)
        loadSprite(prefs)
        loadWallpaper(prefs)
        shadowShader = null
        glowShader = null
        blobShaders.clear()
        bgShaderKey = ""
        seedDust()
        ambient = listOf(
            floatArrayOf(0.22f, 0.18f, 0.55f, accent.toFloat()),
            floatArrayOf(0.82f, 0.62f, 0.60f, Color.WHITE.toFloat())
        )
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
    val isDragging: Boolean get() = dragging

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

    /** Импулс отвън (напр. при хвърляне от потребителя или при "dash"). */
    fun flick(dx: Float, dy: Float) {
        vx = dx
        vy = dy
        thrown = true
    }

    // =================== живот ===================

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = SystemClock.uptimeMillis()
        seedDust()
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
        }
        bgShaderKey = ""
        wallpaperShader = null
        shadowShader = null
        glowShader = null
        blobShaders.clear()
        floorShader = null
        floorKey = ""
        seedDust()
        clampPos()
    }

    private fun step(dt: Float) {
        val now = SystemClock.uptimeMillis()
        val df = (dt / 16.67f).coerceIn(0.2f, 3f)

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

        // меко връщане на squash-а към 1 (пружина)
        squash *= Math.pow(0.86, df.toDouble()).toFloat()
        if (squash < 0.01f) squash = 0f

        glowPhase += dt / 1000f * 1.4f
        ambientPhase += dt / 1000f * 0.25f

        // ударни вълни и искри
        if (rings.isNotEmpty()) rings.removeAll { now - it.started > 520L }
        if (sparks.isNotEmpty()) {
            val it = sparks.iterator()
            while (it.hasNext()) {
                val s = it.next()
                val age = (now - s.started) / 1000f
                if (age > 0.62f) {
                    it.remove()
                } else {
                    s.cx += s.vx * dt / 1000f
                    s.cy += s.vy * dt / 1000f
                    s.vy += 380f * dt / 1000f
                    s.vx *= 0.99f
                }
            }
        }
        if (particlesEnabled && dust.isNotEmpty()) {
            dust.forEach { p ->
                p.cy -= p.speed * dt / 1000f
                p.phase += dt / 1000f * 0.8f
                if (p.cy < -dp(20f)) {
                    p.cy = height + dp(20f)
                    p.cx = rnd.nextFloat() * width
                }
            }
        }

        if (dragging) return
        if (width == 0 || height == 0) return

        if (sleeping) {
            vx *= 0.84f
            vy *= 0.84f
            zzzPhase += dt / 900f
        } else if (thrown) {
            // инерция + триене; отскача до спиране
            val friction = Math.pow(if (bounceEnabled) 0.988 else 0.93, df.toDouble()).toFloat()
            vx *= friction
            vy *= friction
            if (speed() < dp(28f)) {
                thrown = false
                vx = 0f
                vy = 0f
                moveTimer = 0f
            }
        } else {
            moveTimer -= dt
            if (moveTimer <= 0f) pickTarget()

            val dx = targetX - x
            val dy = targetY - y
            val dist = sqrt(dx * dx + dy * dy)
            val maxSpeed = petSizePx * 1.9f * speedScale * energyFactor() * dash
            if (!resting && dist > 8f) {
                val desired = min(maxSpeed, dist * 2.6f)
                val accel = 0.13f * df
                vx += (dx / dist * desired - vx) * accel
                vy += (dy / dist * desired - vy) * accel
            } else {
                vx *= 0.88f
                vy *= 0.88f
            }
        }

        x += vx * dt / 1000f
        y += vy * dt / 1000f

        val sp = speed()
        collideWalls()
        if (sp > 12f) {
            legPhase += dt / 1000f * (5f + sp / 60f) * (if (thrown) 1.5f else 1f)
            if (abs(vx) > 8f) dir = if (vx > 0) 1 else -1
        }
    }

    private fun speed(): Float = sqrt(vx * vx + vy * vy)

    /**
     * Стените на стаята. Когато отскачането е включено — любимецът се отблъсква
     * с възстановяване на скоростта (DVD-стил) и рисува ударна вълна.
     */
    private fun collideWalls() {
        if (width == 0 || height == 0) return
        val half = petSizePx / 2f
        val leftBound = half + dp(10f)
        val rightBound = width - half - dp(10f)
        val topBound = dp(78f) + half
        val bottomBound = height - dp(96f) - half

        if (x < leftBound) {
            x = leftBound
            onWallHit(0, leftBound, y)
            vx = if (bounceEnabled) abs(vx) * 0.86f else 0f
        } else if (x > rightBound) {
            x = rightBound
            onWallHit(0, rightBound, y)
            vx = if (bounceEnabled) -abs(vx) * 0.86f else 0f
        }

        if (y < topBound) {
            y = topBound
            onWallHit(1, x, topBound)
            vy = if (bounceEnabled) abs(vy) * 0.86f else 0f
        } else if (y > bottomBound) {
            y = bottomBound
            onWallHit(1, x, bottomBound)
            vy = if (bounceEnabled) -abs(vy) * 0.86f else 0f
        }
    }

    private fun onWallHit(axis: Int, cx: Float, cy: Float) {
        if (!bounceEnabled) return
        val impact = speed()
        if (impact < dp(90f)) return
        val now = SystemClock.uptimeMillis()
        if (axis == lastHitAxis && now - lastHitAt < 220L) return
        lastHitAt = now
        lastHitAxis = axis
        squash = 1f
        squashAxis = axis
        rings.add(Ring(cx, cy, now, axis))
        if (rings.size > 6) rings.removeAt(0)
        repeat(7) {
            val a = rnd.nextFloat() * 6.28f
            val s = dp(60f) + rnd.nextFloat() * dp(150f)
            sparks.add(Spark(cx, cy, cos(a) * s, sin(a) * s - dp(40f), now))
        }
        while (sparks.size > 40) sparks.removeAt(0)
        bounceCount++
        if (impact > dp(260f)) {
            say(context.getString(R.string.pet_bounce_line), 1200)
        }
        onBounce?.invoke()
    }

    private fun clampPos() {
        if (bounceEnabled) return
        collideWalls()
    }

    private fun pickTarget() {
        val half = petSizePx / 2f
        val leftBound = half + dp(14f)
        val rightBound = max(leftBound, width - half - dp(14f))
        val topBound = dp(80f) + half
        val bottomBound = max(topBound, height - dp(100f) - half)
        targetX = leftBound + rnd.nextFloat() * (rightBound - leftBound)
        targetY = topBound + rnd.nextFloat() * (bottomBound - topBound)
        resting = rnd.nextFloat() < 0.34f
        dash = if (rnd.nextFloat() < 0.18f) 1.9f else 1f   // понякога тича с всичка сила
        val base = if (resting) 1500 else 2400
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
        targetX = px.coerceIn(half + dp(10f), max(half + dp(10f), width - half - dp(10f)))
        targetY = py.coerceIn(dp(78f) + half, max(dp(78f) + half, height - dp(96f) - half))
        resting = false
        thrown = false
        dash = 1f
        moveTimer = 4000f
    }

    private fun seedDust() {
        dust.clear()
        if (width == 0 || height == 0) return
        repeat(16) {
            dust.add(
                Dust(
                    cx = rnd.nextFloat() * width,
                    cy = rnd.nextFloat() * height,
                    r = dp(1.6f) + rnd.nextFloat() * dp(3.6f),
                    speed = dp(6f) + rnd.nextFloat() * dp(16f),
                    phase = rnd.nextFloat() * 6.28f,
                    seed = rnd.nextFloat()
                )
            )
        }
    }

    // =================== рисуване ===================

    override fun onDraw(canvas: Canvas) {
        drawRoom(canvas)
        if (particlesEnabled) drawAmbient(canvas)
        drawGlow(canvas)
        drawShadow(canvas)
        drawPet(canvas)
        if (sleeping) drawZzz(canvas)
        drawEffects(canvas)
        drawBubble(canvas)
    }

    private fun drawRoom(canvas: Canvas) {
        val wp = wallpaper
        if (wp != null) {
            if (wallpaperShader == null) {
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
            val key = "${palette.name}_${width}x$height"
            if (key != bgShaderKey) {
                bgPaint.shader = LinearGradient(
                    0f, 0f, width * 0.25f, height.toFloat(),
                    palette.top, palette.bottom, Shader.TileMode.CLAMP
                )
                bgShaderKey = key
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            // мек "под" за дълбочина (кеширан)
            if (floorShader == null || floorKey != key) {
                floorKey = key
                floorShader = RadialGradient(
                    width / 2f, height * 1.02f, height * 0.55f,
                    intArrayOf(Color.parseColor("#2EFFFFFF"), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
            }
            blobPaint.shader = floorShader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint)
        }
    }

    /** Две големи размазани светлини, които бавно се носят — дава дълбочина. */
    private fun drawAmbient(canvas: Canvas) {
        if (ambient.isEmpty() || width == 0) return
        ambient.forEachIndexed { i, cfg ->
            val fx = cfg[0]
            val fy = cfg[1]
            val fr = cfg[2]
            val color = cfg[3].toInt()
            val radius = max(width, height) * fr
            val shader = blobShaders.getOrPut(i) {
                RadialGradient(
                    0f, 0f, radius,
                    intArrayOf((0x26 shl 24) or (color and 0xFFFFFF), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
            }
            canvas.save()
            canvas.translate(
                width * fx + sin(ambientPhase + i * 1.7f) * dp(26f),
                height * fy + cos(ambientPhase * 1.3f + i) * dp(30f)
            )
            blobPaint.shader = shader
            canvas.drawCircle(0f, 0f, radius, blobPaint)
            canvas.restore()
        }
    }

    /** Цветен ореол в акцента на любимеца. */
    private fun drawGlow(canvas: Canvas) {
        val s = petSizePx
        if (glowShader == null) {
            glowShader = RadialGradient(
                0f, 0f, s * 0.95f,
                intArrayOf((0x3D shl 24) or (accent and 0xFFFFFF), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }
        val pulse = 1f + sin(glowPhase) * 0.05f
        canvas.save()
        canvas.translate(x, y + s * 0.12f)
        canvas.scale(pulse, pulse)
        glowPaint.shader = glowShader
        canvas.drawCircle(0f, 0f, s * 0.95f, glowPaint)
        canvas.restore()
    }

    private fun drawShadow(canvas: Canvas) {
        val s = petSizePx
        if (shadowShader == null || abs((shadowRadius) - s) > 1f) {
            shadowRadius = s
            shadowShader = RadialGradient(
                0f, 0f, s * 0.34f,
                intArrayOf(Color.parseColor("#7A000000"), Color.TRANSPARENT),
                floatArrayOf(0.15f, 1f), Shader.TileMode.CLAMP
            )
        }
        val sc = shadowShader ?: return
        shadowPaint.shader = sc
        canvas.save()
        canvas.translate(x, y + s * 0.45f)
        canvas.scale(1f + squash * 0.25f, 1f - squash * 0.15f)
        canvas.drawCircle(0f, 0f, s * 0.34f, shadowPaint)
        canvas.restore()
    }

    private fun drawPet(canvas: Canvas) {
        val s = petSizePx
        val sp = speed()
        val walking = sp > 12f && !dragging
        val bob = when {
            walking -> abs(sin(legPhase * 3.2f)) * s * (if (thrown) 0.05f else 0.035f)
            sleeping -> sin(zzzPhase * 2f) * s * 0.012f
            else -> 0f
        }
        val lean = when {
            thrown -> (vx / 2200f).coerceIn(-0.18f, 0.18f) * 57.3f
            walking -> dir * 4.5f
            else -> 0f
        }
        val half = s / 2f

        // squash & stretch при удар в стена
        val sx = if (squashAxis == 0) 1f - 0.30f * squash else 1f + 0.22f * squash
        val sy = if (squashAxis == 0) 1f + 0.30f * squash else 1f - 0.30f * squash

        canvas.save()
        canvas.translate(x, y - bob)
        canvas.scale((if (dir < 0) -1f else 1f) * (if (mirrored) -1f else 1f), 1f)
        canvas.scale(sx, sy)
        canvas.rotate(lean)

        val gif = gifDrawable
        when {
            gif != null -> {
                gif.setBounds((-half).toInt(), (-half).toInt(), half.toInt(), half.toInt())
                gif.draw(canvas)
            }

            petBitmap != null -> {
                canvas.drawBitmap(petBitmap!!, null, RectF(-half, -half, half, half), spritePaint)
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
        val drift = sin(zzzPhase) * 0.5f + 0.5f
        canvas.drawText(
            "💤",
            x + s * 0.32f + drift * s * 0.06f,
            y - s * 0.42f - drift * s * 0.12f,
            zzzPaint
        )
    }

    /** Ударни вълни, искри и прашинки. */
    private fun drawEffects(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()

        if (particlesEnabled) {
            dust.forEach { p ->
                val alpha = (0.10f + 0.10f * (0.5f + 0.5f * sin(p.phase))) * 255f
                dustPaint.color = Color.argb(
                    alpha.toInt().coerceIn(0, 255),
                    Color.red(accent),
                    Color.green(accent),
                    Color.blue(accent)
                )
                val px = p.cx + sin(p.phase * 1.4f) * dp(10f) * p.seed
                canvas.drawCircle(px, p.cy, p.r * (0.7f + p.seed * 0.6f), dustPaint)
            }
        }

        rings.forEach { ring ->
            val t = ((now - ring.started) / 520f).coerceIn(0f, 1f)
            val radius = (dp(16f) + dp(58f) * t) * (if (ring.axis == 1) 0.72f else 1f)
            ringPaint.color = Color.argb(
                ((1f - t) * 170).toInt(),
                Color.red(accent), Color.green(accent), Color.blue(accent)
            )
            ringPaint.strokeWidth = dp(3.2f) * (1f - t * 0.8f)
            canvas.drawCircle(ring.cx, ring.cy, radius, ringPaint)
        }

        sparks.forEach { s ->
            val t = ((now - s.started) / 620f).coerceIn(0f, 1f)
            sparkPaint.color = Color.argb(
                ((1f - t) * 220).toInt(),
                Color.red(accent), Color.green(accent), Color.blue(accent)
            )
            canvas.drawCircle(s.cx, s.cy, dp(2.6f) * (1f - t * 0.5f), sparkPaint)
        }
    }

    private fun drawBubble(canvas: Canvas) {
        if (bubbleText.isEmpty() || bubbleAlpha <= 0.02f) return
        val s = petSizePx
        val maxWidth = min(width * 0.72f, dp(268f)).toInt().coerceAtLeast(120)
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
        val padH = dp(14f)
        val padV = dp(10f)
        val bw = layout.width + padH * 2
        val bh = layout.height + padV * 2
        var left = x - bw / 2f
        left = left.coerceIn(dp(8f), max(dp(8f), width - bw - dp(8f)))
        val top = max(dp(8f), y - s * (0.62f + squash * 0.06f) - bh)
        val rect = RectF(left, top, left + bw, top + bh)

        val oldAlpha = canvas.saveLayerAlpha(
            0f, 0f, width.toFloat(), height.toFloat(), (255 * bubbleAlpha).toInt()
        )
        bubblePaint.color = Color.parseColor("#F5F2FC")
        canvas.drawRoundRect(rect, dp(18f), dp(18f), bubblePaint)
        bubbleStroke.color = Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(rect, dp(18f), dp(18f), bubbleStroke)

        val inset = dp(16f)
        val tailX = if (rect.width() > inset * 2.5f) {
            x.coerceIn(rect.left + inset, rect.right - inset)
        } else {
            rect.centerX()
        }
        val path = Path()
        path.moveTo(tailX - dp(8f), rect.bottom - dp(1f))
        path.lineTo(tailX + dp(8f), rect.bottom - dp(1f))
        path.lineTo(tailX, rect.bottom + dp(10f))
        path.close()
        canvas.drawPath(path, bubblePaint)

        canvas.save()
        canvas.translate(rect.left + padH, rect.top + padV)
        layout.draw(canvas)
        canvas.restore()
        canvas.restoreToCount(oldAlpha)
    }

    private var shadowRadius = 0f

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
                animate().cancel()
                animate().scaleX(1.06f).scaleY(1.06f).setDuration(120L).start()
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
                if (!bounceEnabled) clampPos() else clampDragged()
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dragging = false
                removeCallbacks(longPressRunnable)
                parent?.requestDisallowInterceptTouchEvent(false)
                animate().cancel()
                animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
                val quick = SystemClock.uptimeMillis() - dragStartTime < 400L
                if (!dragMoved && quick) {
                    performClick()
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onPetTap?.invoke()
                } else {
                    thrown = true
                    squash = 0.5f
                    squashAxis = if (abs(vx) > abs(vy)) 0 else 1
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                removeCallbacks(longPressRunnable)
                parent?.requestDisallowInterceptTouchEvent(false)
                animate().cancel()
                animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Докато влачиш пръст, любимецът остава в стаята (меко). */
    private fun clampDragged() {
        val half = petSizePx / 2f
        val left = half + dp(6f)
        val right = max(left, width - half - dp(6f))
        val top = dp(72f) + half
        val bottom = max(top, height - dp(92f) - half)
        if (x < left) x = left
        if (x > right) x = right
        if (y < top) y = top
        if (y > bottom) y = bottom
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitPet(ex: Float, ey: Float): Boolean {
        val r = petSizePx * 0.58f * 1.05f
        return abs(ex - x) <= r && abs(ey - y) <= r
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
