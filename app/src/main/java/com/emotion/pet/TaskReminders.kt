package com.emotion.pet

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Планира/отменя локални напомняния за задачи с краен срок през AlarmManager. */
object TaskReminders {

    private fun pendingIntent(context: Context, taskId: Long, text: String): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java)
            .putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(TaskReminderReceiver.EXTRA_TASK_TEXT, text)
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, task: TaskStore.Task) {
        val dueAt = task.dueAt ?: return
        if (dueAt <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context, task.id, task.text)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pi) }
    }

    fun cancel(context: Context, taskId: Long, text: String = "") {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context, taskId, text))
    }
}
