package com.emotion.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * След рестарт на телефона: връща плаващия балон, ако е бил включен и разрешението
 * е дадено, и препланира напомнянията за задачи — AlarmManager ги забравя при рестарт.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (prefs.overlayEnabled && Settings.canDrawOverlays(context)) {
            OverlayService.start(context)
        }
        TaskStore.load(prefs)
            .filter { !it.done && it.dueAt != null }
            .forEach { TaskReminders.schedule(context, it) }
    }
}
