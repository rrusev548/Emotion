package com.emotion.pet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.emotion.pet.databinding.ActivityMainBinding

/** Основен екран: стаята с любимеца, HUD-ът и бързите бутони. */
class MainActivity : AppCompatActivity(),
    MenuSheet.Listener,
    PetPickerSheet.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var lastSayAt = 0L
    private var lastTapAt = 0L

    private val pickSprite = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handlePickedSprite(uri)
    }

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handlePickedWallpaper(uri)
    }

    /** Бавно "износване" на статовете + автоматичен сън. */
    private val ticker = object : Runnable {
        override fun run() {
            refreshStats()
            handler.postDelayed(this, 30_000L)
        }
    }

    /** От време на време любимецът казва нещо сам. */
    private val chatter = object : Runnable {
        override fun run() {
            maybeSaySomething()
            handler.postDelayed(this, 20_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        applyInsets()
        Needs.applyDecay(prefs)
        binding.petView.applyPrefs(prefs)
        refreshHud()

        binding.petView.onPetTap = { onPetTapped() }
        binding.petView.onPetLongPress = { showMenu() }
        binding.menuBtn.setOnClickListener { showMenu() }
        binding.chatBtn.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // докосване на празно място в стаята → любимецът отива там
        binding.root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP && !binding.petView.isDragging) {
                binding.petView.walkTo(event.x, event.y)
            }
            false
        }

        if (!prefs.greeted) {
            prefs.greeted = true
            handler.postDelayed(
                { sayNow(getString(R.string.chat_greeting, prefs.petName)) },
                900L
            )
        }
    }

    override fun onResume() {
        super.onResume()
        binding.petView.applyPrefs(prefs)
        refreshHud()
        applyKeepAwake(prefs.keepAwake)
        if (prefs.overlayEnabled && Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(chatter)
        handler.postDelayed(ticker, 30_000L)
        handler.postDelayed(chatter, 25_000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(chatter)
        prefs.lastTick = System.currentTimeMillis()
    }

    // =================== инициали ===================

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = top + Ui.dp(this@MainActivity, 12)
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.actionColumn) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = bottom + Ui.dp(this@MainActivity, 18)
            }
            insets
        }
    }

    private fun refreshHud() {
        val pet = Presets.pet(prefs.petId)
        val customImage = prefs.spriteType == Prefs.TYPE_IMAGE && SpriteStore.exists(this)
        binding.petNameText.text =
            if (prefs.sleeping) "😴 ${prefs.petName}" else prefs.petName
        binding.petAvatar.text = if (customImage) "🖼" else prefs.emoji.ifBlank { pet.emoji }
        binding.statsText.text =
            getString(R.string.stats_hud, prefs.fullness, prefs.energy, prefs.mood)
        binding.barFull.setProgressCompat(prefs.fullness, true)
        binding.barEnergy.setProgressCompat(prefs.energy, true)
        binding.barMood.setProgressCompat(prefs.mood, true)
    }

    private fun syncPet() {
        binding.petView.applyPrefs(prefs)
        refreshHud()
        PetWidgetProvider.requestUpdate(this)
    }

    private fun refreshStats() {
        Needs.applyDecay(prefs)
        if (!prefs.sleeping && prefs.energy <= 4) {
            prefs.sleeping = true
            sayNow(getString(R.string.pet_sleep_line))
        } else if (prefs.sleeping && prefs.energy >= 95) {
            prefs.sleeping = false
            sayNow(getString(R.string.pet_wake_line))
        }
        syncPet()
    }

    private fun sayNow(text: String) {
        lastSayAt = SystemClock.uptimeMillis()
        binding.petView.say(text)
    }

    private fun maybeSaySomething() {
        val now = SystemClock.uptimeMillis()
        if (now - lastSayAt < 45_000L) return
        val line = SmallBrain.idleLine(this, prefs)
        lastSayAt = now
        binding.petView.say(line, 3400L)
    }

    private fun applyKeepAwake(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // =================== действия ===================

    private fun onPetTapped() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTapAt <= 2000L) return
        lastTapAt = now
        Haptics.tick(this)
        prefs.mood = prefs.mood + 4
        val line = binding.petView.needsLine(this)
        sayNow(
            if (line == getString(R.string.pet_sleep_line)) getString(R.string.pet_tap_line) else line
        )
        syncPet()
    }

    private fun showMenu() {
        if (supportFragmentManager.findFragmentByTag(MenuSheet.TAG) != null) return
        MenuSheet().show(supportFragmentManager, MenuSheet.TAG)
    }

    private fun showPetPicker() {
        if (supportFragmentManager.findFragmentByTag(PetPickerSheet.TAG) != null) return
        PetPickerSheet().show(supportFragmentManager, PetPickerSheet.TAG)
    }

    override fun onCare(action: String) {
        Haptics.bump(this)
        when (action) {
            "feed" -> {
                prefs.fullness = prefs.fullness + 25
                prefs.mood = prefs.mood + 4
                if (prefs.sleeping && prefs.energy > 35) prefs.sleeping = false
                sayNow(getString(R.string.pet_feed_line))
            }

            "play" -> {
                prefs.mood = prefs.mood + 18
                prefs.energy = prefs.energy - 12
                prefs.fullness = prefs.fullness - 4
                prefs.sleeping = false
                sayNow(getString(R.string.pet_play_line))
                // играем си — любимецът хвърчи през стаята
                val angle = Math.toRadians((Math.random() * 360).toDouble())
                val power = resources.displayMetrics.density * 900f
                binding.petView.flick(
                    (Math.cos(angle) * power).toFloat(),
                    (Math.sin(angle) * power).toFloat()
                )
            }

            "sleep" -> {
                prefs.sleeping = !prefs.sleeping
                if (!prefs.sleeping) prefs.energy = prefs.energy + 15
                sayNow(
                    getString(
                        if (prefs.sleeping) R.string.pet_sleep_line else R.string.pet_wake_line
                    )
                )
            }

            "pet" -> {
                prefs.mood = prefs.mood + 6
                sayNow(getString(R.string.pet_tap_line))
            }
        }
        prefs.lastTick = System.currentTimeMillis()
        syncPet()
    }

    override fun onAppearanceChanged() = syncPet()

    override fun onMotionChanged() = syncPet()

    override fun onPickImage() {
        pickSprite.launch("image/*")
    }

    override fun onPickWallpaper() {
        pickWallpaper.launch("image/*")
    }

    override fun onClearWallpaper() {
        SpriteStore.clearWallpaper(this)
        prefs.wallpaperPath = null
        syncPet()
        Toast.makeText(this, R.string.room_wallpaper_clear, Toast.LENGTH_SHORT).show()
    }

    override fun onOpenAi() {
        if (supportFragmentManager.findFragmentByTag(AiSheet.TAG) != null) return
        AiSheet().show(supportFragmentManager, AiSheet.TAG)
    }

    override fun onKeepAwake(enabled: Boolean) {
        prefs.keepAwake = enabled
        applyKeepAwake(enabled)
    }

    override fun onResetPet() {
        prefs.fullness = 80
        prefs.energy = 85
        prefs.mood = 90
        prefs.sleeping = false
        prefs.lastTick = System.currentTimeMillis()
        syncPet()
        Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show()
    }

    override fun onRename() {
        val input = EditText(this).apply {
            setText(prefs.petName)
            hint = getString(R.string.rename_hint)
            setSelection(text.length)
            val h = Ui.dp(this@MainActivity, 20)
            val v = Ui.dp(this@MainActivity, 8)
            setPadding(h, v, h, 0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    prefs.petName = name
                    refreshHud()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onChoosePet() {
        showPetPicker()
    }

    override fun onOverlayToggle(enabled: Boolean) {
        prefs.overlayEnabled = enabled
        if (!enabled) {
            OverlayService.stop(this)
            return
        }
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        } else {
            Toast.makeText(this, R.string.overlay_perm_needed, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    // ---- избор на любимец от галерията ----

    override fun onPetChosen(pet: Presets.Pet) {
        prefs.aiPersonality = ""
        syncPet()
        sayNow(getString(R.string.pets_chosen, pet.name))
        Toast.makeText(this, getString(R.string.pets_chosen, pet.name), Toast.LENGTH_SHORT).show()
    }

    // =================== избор на картинки ===================

    private fun handlePickedSprite(uri: Uri) {
        if (!SpriteStore.saveFromUri(this, uri) || SpriteStore.loadBitmap(this, 64) == null) {
            SpriteStore.clear(this)
            Toast.makeText(this, R.string.photo_error, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.spriteType = Prefs.TYPE_IMAGE
        syncPet()
        hideKeyboard()
        Toast.makeText(this, R.string.toast_sprite_saved, Toast.LENGTH_SHORT).show()
    }

    private fun handlePickedWallpaper(uri: Uri) {
        val path = SpriteStore.saveWallpaperFromUri(this, uri)
        if (path == null) {
            Toast.makeText(this, R.string.photo_error, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.wallpaperPath = path
        syncPet()
        Toast.makeText(this, R.string.toast_wallpaper_saved, Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}

/** Логика на статовете: бавно намаляват с времето, сънят възстановява енергия. */
object Needs {

    private const val MINUTE = 60_000L
    private const val MAX_MINUTES = 60 * 24 * 7

    data class Stats(
        val fullness: Int,
        val energy: Int,
        val mood: Int,
        val sleeping: Boolean
    )

    /** Чиста функция без Android — така е лесна за тестване. */
    fun decay(stats: Stats, minutes: Int): Stats {
        if (minutes <= 0) return stats
        fun clamp(v: Int) = v.coerceIn(0, 100)
        return if (stats.sleeping) {
            Stats(
                fullness = clamp(stats.fullness - minutes / 60),
                energy = clamp(stats.energy + minutes / 6),
                mood = clamp(stats.mood),
                sleeping = stats.sleeping
            )
        } else {
            var mood = stats.mood - minutes / 90
            if (stats.fullness > 60 && stats.energy > 60) mood += minutes / 120
            Stats(
                fullness = clamp(stats.fullness - minutes / 25),
                energy = clamp(stats.energy - minutes / 35),
                mood = clamp(mood),
                sleeping = false
            )
        }
    }

    fun applyDecay(prefs: Prefs) {
        val now = System.currentTimeMillis()
        val elapsed = now - prefs.lastTick
        if (elapsed < MINUTE) return
        val minutes = (elapsed / MINUTE).toInt().coerceAtMost(MAX_MINUTES)
        val result = decay(
            Stats(prefs.fullness, prefs.energy, prefs.mood, prefs.sleeping),
            minutes
        )
        prefs.fullness = result.fullness
        prefs.energy = result.energy
        prefs.mood = result.mood
        prefs.sleeping = result.sleeping
        prefs.lastTick = now
    }
}
