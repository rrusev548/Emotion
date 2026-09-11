package com.emotion.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Looper
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import kotlin.math.roundToInt

/**
 * Рендерира истинските екрани на приложението като PNG в docs/screenshots.
 * Използва се от CI, за да може да се види UI-ът без емулатор.
 * Ако нещо се обърка, тестът не проваля билда — само логва проблема.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "bg-rBG-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    private lateinit var context: Context
    private lateinit var prefs: Prefs
    private val outDir = File("../docs/screenshots").apply { mkdirs() }
    private val errors = StringBuilder()

    private val screenW: Int get() = context.resources.displayMetrics.widthPixels
    private val screenH: Int get() = context.resources.displayMetrics.heightPixels

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.petId = "shiba"
        prefs.nameCustomized = false
        prefs.sizeDp = 120
        prefs.speed = 1f
        prefs.mirrored = false
        prefs.palette = Presets.Palette.NIGHT.name
        prefs.wallpaperPath = null
        prefs.fullness = 72
        prefs.energy = 84
        prefs.mood = 91
        prefs.sleeping = false
        prefs.keepAwake = true
        prefs.greeted = false
        prefs.onboarded = true
        prefs.aiProvider = "groq"
        prefs.aiModel = "llama-3.3-70b-versatile"
        prefs.aiKey = "gsk_демо-ключ-за-скрийншот"
        prefs.aiPersonality = ""
        ChatStore.clear(prefs)

        // демо образът, който ползваме и в README
        val demo = File("../docs/pet-demo.png")
        if (demo.isFile) {
            runCatching { SpriteStore.file(context).writeBytes(demo.readBytes()) }
            prefs.spriteType = Prefs.TYPE_IMAGE
        } else {
            prefs.spriteType = Prefs.TYPE_EMOJI
            prefs.emoji = "🐶"
        }
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun forceLayout(view: View, w: Int, h: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
    }

    private fun record(name: String, t: Throwable?) {
        if (t == null) return
        errors.append("== ").append(name).append(" ==\n")
            .append(t.toString()).append('\n')
        t.stackTrace.take(12).forEach { errors.append("   at ").append(it).append('\n') }
        errors.append('\n')
        println("SCREENSHOT_FAIL $name: $t")
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Screenshot → ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }

    private fun shotMain() = runCatching {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idle(1500) // любимецът се разхожда + поздравът се появява
            scenario.onActivity { a -> forceLayout(a.window.decorView, screenW, screenH) }
            idle(250)
            val bmp = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            scenario.onActivity { a -> a.window.decorView.draw(Canvas(bmp)) }
            writePng(bmp, File(outDir, "01-room.png"))
        }
    }.onFailure { record("01-room.png", it) }

    private fun shotChat() = runCatching {
        ChatStore.save(
            prefs,
            listOf(
                ChatStore.Msg(ChatStore.ROLE_PET, "Здравей! Аз съм Моки. Как си? 🐾"),
                ChatStore.Msg(ChatStore.ROLE_USER, "Добре! Гладен ли си?"),
                ChatStore.Msg(ChatStore.ROLE_PET, "Малко 🍎 Натисни «Храни» в менюто и ще съм щастлив."),
                ChatStore.Msg(ChatStore.ROLE_USER, "Искаш ли да поиграем?"),
                ChatStore.Msg(ChatStore.ROLE_PET, "Да! Обичам да тичам из екрана 🎾 Хайде!")
            )
        )
        ActivityScenario.launch(ChatActivity::class.java).use { scenario ->
            idle(400)
            scenario.onActivity { a -> forceLayout(a.window.decorView, screenW, screenH) }
            idle(250)
            val bmp = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            scenario.onActivity { a -> a.window.decorView.draw(Canvas(bmp)) }
            writePng(bmp, File(outDir, "03-chat.png"))
        }
    }.onFailure { record("03-chat.png", it) }

    /** Снима стаята + отворения bottom sheet (истинският диалогов прозорец, ако може). */
    private fun shotSheet(name: String, show: (MainActivity) -> BottomSheetDialogFragment) = runCatching {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idle(1200)
            scenario.onActivity { a -> forceLayout(a.window.decorView, screenW, screenH) }
            idle(200)

            val room = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            scenario.onActivity { a -> a.window.decorView.draw(Canvas(room)) }

            var sheet: BottomSheetDialogFragment? = null
            var showError: Throwable? = null
            scenario.onActivity { a ->
                try {
                    sheet = show(a)
                } catch (t: Throwable) {
                    showError = t
                }
            }
            if (showError != null) throw showError!!

            idle(600)
            val fragment = sheet ?: error("bottom sheet-ът не се създаде")
            val dialogDecor: View? = fragment.dialog?.window?.decorView

            if (dialogDecor != null) {
                // истинският прозорец на диалога (стъклото + затъмнението са нарисувани от системата)
                forceLayout(dialogDecor, screenW, screenH)
                idle(300)
                val dialogBmp = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
                dialogDecor.draw(Canvas(dialogBmp))
                if (hasVisibleContent(dialogBmp)) {
                    // сглобяваме както изглежда на живо: стаята + затъмнение + листът
                    val ratio = when {
                        name.startsWith("05") -> 0.82f
                        name.startsWith("04") -> 0.88f
                        else -> 0.86f
                    }
                    val top = (screenH * (1 - ratio)).toInt()
                    val out = room.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(out)
                    canvas.drawRect(
                        0f, top.toFloat(), screenW.toFloat(), screenH.toFloat(),
                        Paint().apply { color = 0x8C000000.toInt() }
                    )
                    canvas.drawBitmap(
                        dialogBmp,
                        android.graphics.Rect(0, top, screenW, screenH),
                        android.graphics.Rect(0, top, screenW, screenH),
                        null
                    )
                    writePng(out, File(outDir, name))
                    return@use
                }
            }

            // резерва: съставяме стаята + sheet-а ръчно
            val sv = fragment.view ?: error("sheet-ът няма view")
            val sheetH = (screenH * 0.86f).roundToInt()
            forceLayout(sv, screenW, sheetH)
            idle(200)
            val sheetBmp = Bitmap.createBitmap(screenW, sheetH, Bitmap.Config.ARGB_8888)
            sv.draw(Canvas(sheetBmp))

            val out = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawBitmap(room, 0f, 0f, null)
            val radius = 28f * context.resources.displayMetrics.density
            val path = Path().apply {
                addRoundRect(
                    RectF(0f, 0f, screenW.toFloat(), sheetH.toFloat()),
                    floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
                    Path.Direction.CW
                )
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.translate(0f, (screenH - sheetH).toFloat())
            canvas.drawBitmap(sheetBmp, 0f, 0f, null)
            canvas.restore()
            writePng(out, File(outDir, name))
        }
    }.onFailure { record(name, it) }

    /** Проверява дали в картинката има реално съдържание (не е само фон). */
    private fun hasVisibleContent(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val distinct = pixels.toHashSet()
        return distinct.size > 24
    }

    /** Сглобява обзорна картинка от всички екрани. */
    private fun buildOverview() = runCatching {
        val shots = listOf("01-room.png", "02-menu.png", "03-chat.png", "04-ai.png", "05-pets.png")
            .map { File(outDir, it) }
            .filter { it.isFile }
            .map { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
        if (shots.isEmpty()) return@runCatching
        val scale = 0.38f
        val gap = 28
        val pad = 36
        val titleH = 84
        val widths = shots.map { (it.width * scale).toInt() }
        val heights = shots.map { (it.height * scale).toInt() }
        val totalW = pad * 2 + widths.sum() + gap * (shots.size - 1)
        val totalH = pad + titleH + heights.max()
        val out = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.rgb(16, 11, 30))
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(255, 214, 232)
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Emotion Pet — екрани от истинския код (Robolectric)",
            totalW / 2f, pad + 26f, title
        )
        var x = pad.toFloat()
        val top = (pad + titleH).toFloat()
        shots.forEachIndexed { i, shot ->
            val w = widths[i]
            val h = heights[i]
            val sc = Bitmap.createScaledBitmap(shot, w, h, true)
            canvas.drawBitmap(sc, x, top, null)
            x += w + gap
        }
        writePng(out, File(outDir, "00-overview.png"))
    }.onFailure { record("00-overview.png", it) }

    @Test
    fun `рендерира екраните`() {
        val errorsFile = File(outDir, "errors.txt")
        if (errorsFile.exists()) errorsFile.delete()
        shotMain()
        shotSheet("02-menu.png") { activity ->
            MenuSheet().also { it.show(activity.supportFragmentManager, MenuSheet.TAG) }
        }
        shotChat()
        shotSheet("04-ai.png") { activity ->
            AiSheet().also { it.show(activity.supportFragmentManager, AiSheet.TAG) }
        }
        shotSheet("05-pets.png") { activity ->
            PetPickerSheet().also { it.show(activity.supportFragmentManager, PetPickerSheet.TAG) }
        }
        buildOverview()
        if (errors.isNotEmpty()) {
            File(outDir, "errors.txt").writeText(errors.toString())
            println("SCREENSHOT ERRORS:\n$errors")
        }
    }
}
