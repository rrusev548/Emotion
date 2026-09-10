package com.emotion.pet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Пуши всеки layout на приложението. Хваща липсващи размери, счупени стилове
 * и невалидни референции — тоест неща, които биха сринали приложението на телефона.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "bg-rBG-w411dp-h891dp-xhdpi")
class LayoutInflateTest {

    // Само LayoutInflater-ът на реална Activity има инсталиран AppCompat factory —
    // без него Material компонентите (и app:tint върху обикновен ImageView) гърмят.
    private val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
    private val context: android.content.Context = activity
    private val inflater: LayoutInflater = activity.layoutInflater

    private val root = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun inflate(id: Int, attachToRoot: Boolean = true): View {
        return if (attachToRoot) {
            inflater.inflate(id, root, false)
        } else {
            inflater.inflate(id, null, false)
        }
    }

    @Test
    fun `всички layouts се inflate-ват без грешка`() {
        // директни R.layout константи (в debug build packageName е с наставка .debug)
        val layouts = mapOf(
            "activity_main" to R.layout.activity_main,
            "activity_chat" to R.layout.activity_chat,
            "sheet_menu" to R.layout.sheet_menu,
            "sheet_ai" to R.layout.sheet_ai,
            "sheet_pets" to R.layout.sheet_pets,
            "item_msg_user" to R.layout.item_msg_user,
            "item_msg_pet" to R.layout.item_msg_pet,
            "item_emoji" to R.layout.item_emoji,
            "item_pet" to R.layout.item_pet
        )
        val failures = mutableListOf<String>()
        layouts.forEach { (name, id) ->
            try {
                val view = inflate(id)
                // размери: ако някой елемент няма layout_height, findViewById минава,
                // но рендирането на детето би гръмнало — затова мерим
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, 1080, 1920)
            } catch (t: Throwable) {
                failures += "$name → ${t.javaClass.simpleName}: ${t.message?.take(200)}"
            }
        }
        println("LayoutInflateTest: проверени ${layouts.size} layouts, проблеми: ${failures.size}")
        failures.forEach { println("  ✗ $it") }
        assertTrue("Счупени layouts:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
