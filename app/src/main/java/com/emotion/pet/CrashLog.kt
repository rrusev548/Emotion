package com.emotion.pet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Хваща неочакваните Java изключения и ги записва във файл, за да може
 * потребителят да ги покаже/копира при следващото пускане. Така без adb
 * можем да видим точната причина за crash на реално устройство.
 *
 * (Нативните SIGSEGV крашове не минават през Java handler-а — за тях се
 * разчита на софтуерния слой в PetView + евентуално bug report от телефона.)
 */
object CrashLog {

    private const val FILE = "crash_log.txt"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val sb = StringBuilder()
                sb.appendLine("time=").append(System.currentTimeMillis())
                sb.appendLine("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                sb.appendLine("android=").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(')')
                sb.appendLine("thread=").append(thread?.name ?: "?")
                sb.appendLine("exception=").append(throwable.toString())
                sb.appendLine(sw.toString())
                File(app.filesDir, FILE).writeText(sb.toString())
            } catch (e: Throwable) {
                Log.e("EmotionPet", "Неуспешен запис на crash log", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("EmotionPet crash", text))
    }
}
