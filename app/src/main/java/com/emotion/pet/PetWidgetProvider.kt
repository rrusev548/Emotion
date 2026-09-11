package com.emotion.pet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget за началния екран: аватар + статове на любимеца; тап отваря приложението. */
class PetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {

        /** Извиква се при промяна на статовете, за да се обнови widget-ът веднага. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id -> updateWidget(context, manager, id) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = Prefs(context)
            Needs.applyDecay(prefs)
            val pet = Presets.pet(prefs.petId)
            val customImage = prefs.spriteType == Prefs.TYPE_IMAGE && SpriteStore.exists(context)
            val avatar = if (customImage) "🖼" else prefs.emoji.ifBlank { pet.emoji }

            val views = RemoteViews(context.packageName, R.layout.widget_pet).apply {
                setTextViewText(R.id.widgetAvatar, avatar)
                setTextViewText(
                    R.id.widgetName,
                    if (prefs.sleeping) "😴 ${prefs.petName}" else prefs.petName
                )
                setTextViewText(
                    R.id.widgetStats,
                    context.getString(R.string.stats_detail, prefs.fullness, prefs.energy, prefs.mood)
                )
                val openApp = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widgetRoot, openApp)
            }
            manager.updateAppWidget(id, views)
        }
    }
}
