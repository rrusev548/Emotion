package com.emotion.pet

import android.content.Intent
import android.provider.Settings
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

/**
 * Базов клас за всички екрани освен стаята — закача малкия физически Peta
 * (виж [MiniPetOverlay]) върху съдържанието на прозореца, докато Activity-то
 * е видимо, за да е "върху всички екрани", не само в стаята. Докато сме
 * вътре в приложението (кой да е негов екран), едновременно с това спира
 * системния edge-peek балон (OverlayService) — той е за извън приложението,
 * не му е мястото едновременно с вътрешния MiniPetOverlay/голямата стая.
 */
abstract class PetOverlayActivity : AppCompatActivity() {

    /** MainActivity вече си има голямата интерактивна версия — там мини-Peta е излишен. */
    protected open val showMiniPet: Boolean = true

    private var miniPet: MiniPetOverlay? = null

    override fun onStart() {
        super.onStart()
        AppForeground.onActivityStart(this)
    }

    override fun onStop() {
        AppForeground.onActivityStop(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!showMiniPet) return
        val root = findViewById<ViewGroup>(android.R.id.content)
        if (miniPet == null) {
            miniPet = MiniPetOverlay(this) { goHome() }
            root.addView(
                miniPet,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        miniPet?.let { (it.parent as? ViewGroup)?.removeView(it) }
        miniPet = null
    }

    /** Tap върху мини-Peta връща в стаята — тя вече си е в стека под текущия екран. */
    private fun goHome() {
        if (this is MainActivity) return
        Haptics.tick(this)
        if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    /**
     * Брои колко от нашите Activity-та са видими в момента (независимо кое точно) —
     * при преход 0→1 приложението излиза на преден план, при 1→0 излиза на заден.
     */
    private object AppForeground {
        private var visibleCount = 0

        fun onActivityStart(activity: AppCompatActivity) {
            visibleCount++
            if (visibleCount != 1) return
            val prefs = Prefs(activity)
            if (prefs.overlayEnabled) OverlayService.stop(activity)
        }

        fun onActivityStop(activity: AppCompatActivity) {
            visibleCount = (visibleCount - 1).coerceAtLeast(0)
            if (visibleCount != 0) return
            val prefs = Prefs(activity)
            if (prefs.overlayEnabled && Settings.canDrawOverlays(activity)) {
                OverlayService.start(activity)
            }
        }
    }
}
