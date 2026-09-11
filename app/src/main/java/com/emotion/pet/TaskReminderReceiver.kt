package com.emotion.pet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Огражда се от AlarmManager, когато срокът на задача изтече — показва напомняне. */
class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val text = intent.getStringExtra(EXTRA_TASK_TEXT) ?: return
        if (taskId < 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tasks_reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager?.createNotificationChannel(channel)
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) return

        val openApp = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            Intent(context, TasksActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tasks)
            .setContentTitle(context.getString(R.string.tasks_reminder_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification) }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TEXT = "task_text"
        private const val CHANNEL_ID = "task_reminders"
    }
}
