package com.emotion.pet

import android.animation.ValueAnimator
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Peta наднича от края на екрана над другите приложения (Edge Pop, изисква
 * SYSTEM_ALERT_WINDOW). По-голямата част от балона стои извън видимата
 * област — влачиш го и при пускане пак се "прибира" (snap) към най-близкия
 * край. Пуска се/спира от менюто → „Плаващ прозорец“. Тап отваря
 * приложението, задържане на едно място изключва балона.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var bubbleSizePx = 0
    private var peekVisiblePx = 0
    private var screenWidthPx = 0
    private var snapAnimator: ValueAnimator? = null

    private val peekNudge = object : Runnable {
        override fun run() {
            nudge()
            handler.postDelayed(this, 6000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        addBubble()
        handler.postDelayed(peekNudge, 6000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /** При завъртане на екрана размерите за snap-ване към край са невалидни — преизчисляваме ги. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = bubble ?: return
        val lp = params ?: return
        val wm = windowManager ?: return
        val wasLeft = lp.x < screenWidthPx / 2
        screenWidthPx = resources.displayMetrics.widthPixels
        lp.x = if (wasLeft) leftEdgeX() else rightEdgeX()
        runCatching { wm.updateViewLayout(view, lp) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        snapAnimator?.cancel()
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
        val prefs = Prefs(this)
        val pet = Presets.pet(prefs.petId)
        val customImage = prefs.spriteType == Prefs.TYPE_IMAGE && SpriteStore.exists(this)

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_pet, null)
        view.findViewById<TextView>(R.id.overlayEmoji).text =
            if (customImage) "🖼" else prefs.emoji.ifBlank { pet.emoji }

        val density = resources.displayMetrics.density
        bubbleSizePx = (64 * density).toInt()
        peekVisiblePx = (bubbleSizePx * PEEK_VISIBLE_FRACTION).toInt()
        screenWidthPx = resources.displayMetrics.widthPixels

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
            x = rightEdgeX()
            y = 220
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        bubble = view
        params = lp
        attachDragHandling(view, wm, lp)

        runCatching { wm.addView(view, lp) }
    }

    private fun leftEdgeX(): Int = -(bubbleSizePx - peekVisiblePx)
    private fun rightEdgeX(): Int = screenWidthPx - peekVisiblePx

    private fun attachDragHandling(view: View, wm: WindowManager, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var downAt = 0L

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    handler.removeCallbacks(peekNudge)
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    downAt = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    lp.x = startX + dx
                    lp.y = startY + dy
                    runCatching { wm.updateViewLayout(view, lp) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // влаченето никога не гаси балона, дори да е продължило над 500ms
                    when {
                        moved -> snapToNearestEdge(wm, view, lp)
                        System.currentTimeMillis() - downAt >= 500L -> disableOverlay()
                        else -> openApp()
                    }
                    handler.postDelayed(peekNudge, 6000L)
                    true
                }

                else -> false
            }
        }
    }

    /** При пускане Peta се "прибира" (snap) обратно към най-близкия край — Edge Pop. */
    private fun snapToNearestEdge(wm: WindowManager, view: View, lp: WindowManager.LayoutParams) {
        val center = lp.x + bubbleSizePx / 2
        val targetX = if (center < screenWidthPx / 2) leftEdgeX() else rightEdgeX()
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(lp.x, targetX).apply {
            duration = 220L
            addUpdateListener { a ->
                lp.x = a.animatedValue as Int
                runCatching { wm.updateViewLayout(view, lp) }
            }
            start()
        }
    }

    /** Лек "поглед към теб" — балонът леко се измества навътре и се връща, за да не изглежда забравен. */
    private fun nudge() {
        val wm = windowManager ?: return
        val view = bubble ?: return
        val lp = params ?: return
        val atLeft = lp.x < screenWidthPx / 2
        val nudgeBy = (12 * resources.displayMetrics.density).toInt()
        val out = if (atLeft) leftEdgeX() else rightEdgeX()
        val inward = if (atLeft) out + nudgeBy else out - nudgeBy
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(out, inward, out).apply {
            duration = 900L
            addUpdateListener { a ->
                lp.x = a.animatedValue as Int
                runCatching { wm.updateViewLayout(view, lp) }
            }
            start()
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
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        params = null
        windowManager = null
    }

    companion object {
        private const val CHANNEL_ID = "overlay_pet"
        private const val NOTIF_ID = 42
        private const val PEEK_VISIBLE_FRACTION = 0.55f

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
