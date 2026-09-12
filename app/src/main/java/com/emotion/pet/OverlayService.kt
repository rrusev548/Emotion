package com.emotion.pet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Peta се разхожда свободно върху целия екран, над другите приложения
 * (изисква SYSTEM_ALERT_WINDOW): истинска гравитация, отскача от четирите
 * ръба, влачиш я с пръст и я хвърляш с инерция — досущ като у дома, в
 * стаята. Пуска се/спира от менюто → „Плаващ прозорец“. Бърз тап отваря
 * приложението, задържане на място го изключва.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var petView: FloatingPetView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private var screenWidthPx = 0
    private var screenHeightPx = 0
    private var vx = 0f
    private var vy = 0f
    private var dragging = false
    private var dragMoved = false
    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var grabDx = 0f
    private var grabDy = 0f
    private var lastMoveTime = 0L
    private var lastMoveX = 0f
    private var lastMoveY = 0f

    private var lastFrame = SystemClock.uptimeMillis()
    private val frame = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastFrame).coerceIn(1L, 50L).toFloat()
            lastFrame = now
            step(dt)
            handler.postDelayed(this, 16L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        addBubble()
        lastFrame = SystemClock.uptimeMillis()
        handler.post(frame)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /** При завъртане на екрана размерите за отскачане от ръбовете са невалидни — преизчисляваме ги. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = petView ?: return
        val lp = params ?: return
        val wm = windowManager ?: return
        screenWidthPx = resources.displayMetrics.widthPixels
        screenHeightPx = resources.displayMetrics.heightPixels
        lp.x = lp.x.coerceIn(0, (screenWidthPx - view.sizePx.toInt()).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (screenHeightPx - view.sizePx.toInt()).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(view, lp) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeBubble()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            manager?.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_apps)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun addBubble() {
        val view = FloatingPetView(this)
        screenWidthPx = resources.displayMetrics.widthPixels
        screenHeightPx = resources.displayMetrics.heightPixels

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidthPx - view.sizePx.toInt()
            y = (screenHeightPx * 0.35f).toInt()
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        petView = view
        params = lp
        attachDragHandling(view, wm, lp)

        runCatching { wm.addView(view, lp) }
    }

    private fun step(dt: Float) {
        val view = petView ?: return
        view.stepIdle(dt)
        view.invalidate()
        if (dragging || screenWidthPx == 0 || screenHeightPx == 0) return

        val lp = params ?: return
        val wm = windowManager ?: return
        val size = view.sizePx

        vy += dp(PetView.GRAVITY_DP_PER_S2) * dt / 1000f
        vy = vy.coerceAtMost(dp(PetView.TERMINAL_VELOCITY_DP))
        val friction = Math.pow(0.995, (dt / 16.67).coerceIn(0.2, 3.0)).toFloat()
        vx *= friction

        var nx = lp.x + vx * dt / 1000f
        var ny = lp.y + vy * dt / 1000f

        val left = 0f
        val right = (screenWidthPx - size).coerceAtLeast(0f)
        val top = 0f
        val bottom = (screenHeightPx - size).coerceAtLeast(0f)

        if (nx < left) {
            nx = left
            vx = abs(vx) * RESTITUTION
            bounceSquash(view, 0)
        } else if (nx > right) {
            nx = right
            vx = -abs(vx) * RESTITUTION
            bounceSquash(view, 0)
        }
        if (ny < top) {
            ny = top
            vy = abs(vy) * RESTITUTION
            bounceSquash(view, 1)
        } else if (ny > bottom) {
            ny = bottom
            vy = -abs(vy) * RESTITUTION
            bounceSquash(view, 1)
        }

        lp.x = nx.toInt()
        lp.y = ny.toInt()
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun bounceSquash(view: FloatingPetView, axis: Int) {
        if (speed() < dp(60f)) return
        view.squash = 1f
        view.squashAxis = axis
    }

    private fun speed(): Float = sqrt(vx * vx + vy * vy)

    private fun attachDragHandling(view: FloatingPetView, wm: WindowManager, lp: WindowManager.LayoutParams) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    view.dragging = true
                    dragMoved = false
                    downAt = SystemClock.uptimeMillis()
                    downX = event.rawX
                    downY = event.rawY
                    lastMoveTime = downAt
                    lastMoveX = event.rawX
                    lastMoveY = event.rawY
                    grabDx = lp.x - event.rawX
                    grabDy = lp.y - event.rawY
                    vx = 0f
                    vy = 0f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // спрямо първоначалната точка на допир, не спрямо последното move
                    // събитие — иначе бавно, но истинско влачене се брои за "неподвижно".
                    if (abs(event.rawX - downX) > 6f || abs(event.rawY - downY) > 6f) {
                        dragMoved = true
                    }
                    val nx = event.rawX + grabDx
                    val ny = event.rawY + grabDy
                    val now = SystemClock.uptimeMillis()
                    val dtMs = (now - lastMoveTime).coerceAtLeast(8L)
                    vx = ((nx - lp.x) / dtMs * 1000f).coerceIn(-2600f, 2600f)
                    vy = ((ny - lp.y) / dtMs * 1000f).coerceIn(-2600f, 2600f)
                    lp.x = nx.toInt()
                    lp.y = ny.toInt()
                    lastMoveTime = now
                    lastMoveX = event.rawX
                    lastMoveY = event.rawY
                    runCatching { wm.updateViewLayout(view, lp) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    dragging = false
                    view.dragging = false
                    val quick = SystemClock.uptimeMillis() - downAt < 350L
                    when {
                        !dragMoved && quick -> openApp()
                        !dragMoved -> disableOverlay()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    view.dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun disableOverlay() {
        Prefs(this).overlayEnabled = false
        stopSelf()
    }

    private fun removeBubble() {
        val wm = windowManager ?: return
        petView?.let { runCatching { wm.removeView(it) } }
        petView = null
        params = null
        windowManager = null
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val CHANNEL_ID = "overlay_pet"
        private const val NOTIF_ID = 42
        private const val RESTITUTION = 0.62f

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
