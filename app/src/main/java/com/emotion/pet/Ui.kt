package com.emotion.pet

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Дребни помощни функции за програмно създадени изгледи. */
object Ui {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Малък "чип", който може да е избран (isSelected). */
    fun chip(context: Context, text: String): TextView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(context, 8) }
        setBackgroundResource(R.drawable.bg_choice)
        setTextColor(ContextCompat.getColorStateList(context, R.color.choice_text))
        this.text = text
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(context, 16), dp(context, 9), dp(context, 16), dp(context, 9))
        isClickable = true
        isFocusable = true
    }
}
