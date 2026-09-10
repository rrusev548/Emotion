package com.emotion.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/**
 * Каченият образ на любимеца (PNG/JPG/GIF) се копира във filesDir,
 * за да не зависи от галерията и да не излиза от телефона.
 */
object SpriteStore {

    private const val FILE_NAME = "pet_sprite.img"

    fun file(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun exists(ctx: Context): Boolean = file(ctx).isFile && file(ctx).length() > 0

    fun saveFromUri(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file(ctx)).use { output -> input.copyTo(output) }
        }
        exists(ctx)
    } catch (_: Exception) {
        false
    }

    fun saveFromBitmap(ctx: Context, bitmap: Bitmap): Boolean = try {
        FileOutputStream(file(ctx)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Статична картинка (за рисуване и за аватар в чата). */
    fun loadBitmap(ctx: Context, maxDim: Int): Bitmap? {
        val f = file(ctx)
        if (!f.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            var d = maxOf(bounds.outWidth, bounds.outHeight)
            while (d / 2 >= maxDim && sample < 8) {
                d /= 2
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(f.absolutePath, opts)
        } catch (_: Exception) {
            null
        }
    }

    /** Анимиран GIF на Android 9+ (по-старите показват първия кадър). */
    fun loadAnimated(ctx: Context): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        if (!exists(ctx)) return null
        return try {
            val d = Drawable.createFromPath(file(ctx).absolutePath)
            if (d is AnimatedImageDrawable) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                d.start()
                d
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    // ---------- фон на стаята ----------

    private const val WALLPAPER_NAME = "pet_wallpaper.img"

    fun wallpaperFile(ctx: Context): File = File(ctx.filesDir, WALLPAPER_NAME)

    fun saveWallpaperFromUri(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(wallpaperFile(ctx)).use { output -> input.copyTo(output) }
        }
        val f = wallpaperFile(ctx)
        if (f.isFile && f.length() > 0 && android.graphics.BitmapFactory.decodeFile(f.absolutePath) != null) {
            f.absolutePath
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    fun clearWallpaper(ctx: Context) {
        runCatching { wallpaperFile(ctx).delete() }
    }
}
