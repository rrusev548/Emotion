package com.emotion.pet

import android.content.Context
import android.content.SharedPreferences

/**
 * Малък слой върху SharedPreferences — всичко (име, образ, статове, ИИ ключ)
 * се пази само на устройството.
 */
class Prefs(context: Context) {

    private val app = context.applicationContext
    private val sp: SharedPreferences = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- любимец ----------
    var petId: String
        get() = sp.getString(K_PET_ID, Presets.PETS.first().id) ?: Presets.PETS.first().id
        set(v) = sp.edit().putString(K_PET_ID, v).apply()

    /** Име: собственото, ако потребителят е преименувал, иначе името от каталога. */
    var petName: String
        get() = sp.getString(K_NAME, null)?.takeIf { it.isNotBlank() } ?: Presets.pet(petId).name
        set(v) {
            sp.edit().putString(K_NAME, v.trim()).putBoolean(K_NAME_CUSTOM, true).apply()
        }

    /** true, ако потребителят сам е избрал име (тогава смяната на любимец не го пипа). */
    var nameCustomized: Boolean
        get() = sp.getBoolean(K_NAME_CUSTOM, false)
        set(v) = sp.edit().putBoolean(K_NAME_CUSTOM, v).apply()

    /** "emoji" или "image" */
    var spriteType: String
        get() = sp.getString(K_SPRITE_TYPE, TYPE_EMOJI) ?: TYPE_EMOJI
        set(v) = sp.edit().putString(K_SPRITE_TYPE, v).apply()

    var emoji: String
        get() = sp.getString(K_EMOJI, Presets.EMOJIS.first()) ?: Presets.EMOJIS.first()
        set(v) = sp.edit().putString(K_EMOJI, v).apply()

    var sizeDp: Int
        get() = sp.getInt(K_SIZE, 110)
        set(v) = sp.edit().putInt(K_SIZE, v.coerceIn(MIN_SIZE, MAX_SIZE)).apply()

    var speed: Float
        get() = sp.getFloat(K_SPEED, 1f)
        set(v) = sp.edit().putFloat(K_SPEED, v.coerceIn(0.3f, 2f)).apply()

    var mirrored: Boolean
        get() = sp.getBoolean(K_MIRROR, false)
        set(v) = sp.edit().putBoolean(K_MIRROR, v).apply()

    /** Отскачане от стените (DVD-стил) + squash анимация при удар. */
    var bounce: Boolean
        get() = sp.getBoolean(K_BOUNCE, true)
        set(v) = sp.edit().putBoolean(K_BOUNCE, v).apply()

    /** Плаващи частици в стаята. */
    var particles: Boolean
        get() = sp.getBoolean(K_PARTICLES, true)
        set(v) = sp.edit().putBoolean(K_PARTICLES, v).apply()

    // ---------- стая ----------
    var palette: String
        get() = sp.getString(K_PALETTE, Presets.Palette.NIGHT.name)
            ?: Presets.Palette.NIGHT.name
        set(v) = sp.edit().putString(K_PALETTE, v).apply()

    var wallpaperPath: String?
        get() = sp.getString(K_WALLPAPER, null)
        set(v) = sp.edit().putString(K_WALLPAPER, v).apply()

    // ---------- статове ----------
    var fullness: Int
        get() = sp.getInt(K_FULL, 80)
        set(v) = sp.edit().putInt(K_FULL, v.coerceIn(0, 100)).apply()

    var energy: Int
        get() = sp.getInt(K_ENERGY, 85)
        set(v) = sp.edit().putInt(K_ENERGY, v.coerceIn(0, 100)).apply()

    var mood: Int
        get() = sp.getInt(K_MOOD, 90)
        set(v) = sp.edit().putInt(K_MOOD, v.coerceIn(0, 100)).apply()

    var sleeping: Boolean
        get() = sp.getBoolean(K_SLEEPING, false)
        set(v) = sp.edit().putBoolean(K_SLEEPING, v).apply()

    var lastTick: Long
        get() = sp.getLong(K_LAST_TICK, System.currentTimeMillis())
        set(v) = sp.edit().putLong(K_LAST_TICK, v).apply()

    // ---------- настройки ----------
    var keepAwake: Boolean
        get() = sp.getBoolean(K_KEEP_AWAKE, true)
        set(v) = sp.edit().putBoolean(K_KEEP_AWAKE, v).apply()

    /** Желае ли потребителят плаващият балон да е активен (независимо дали разрешението е дадено). */
    var overlayEnabled: Boolean
        get() = sp.getBoolean(K_OVERLAY, false)
        set(v) = sp.edit().putBoolean(K_OVERLAY, v).apply()

    // ---------- ИИ ----------
    var aiProvider: String
        get() = sp.getString(K_AI_PROVIDER, "openai") ?: "openai"
        set(v) = sp.edit().putString(K_AI_PROVIDER, v).apply()

    var aiModel: String
        get() = sp.getString(K_AI_MODEL, "") ?: ""
        set(v) = sp.edit().putString(K_AI_MODEL, v.trim()).apply()

    var aiKey: String
        get() = sp.getString(K_AI_KEY, "") ?: ""
        set(v) = sp.edit().putString(K_AI_KEY, v.trim()).apply()

    var aiBaseUrl: String
        get() = sp.getString(K_AI_BASE, "") ?: ""
        set(v) = sp.edit().putString(K_AI_BASE, v.trim()).apply()

    var aiPersonality: String
        get() = sp.getString(K_AI_PERSONA, "") ?: ""
        set(v) = sp.edit().putString(K_AI_PERSONA, v.trim()).apply()

    var chatLog: String
        get() = sp.getString(K_CHAT, "[]") ?: "[]"
        set(v) = sp.edit().putString(K_CHAT, v).apply()

    var greeted: Boolean
        get() = sp.getBoolean(K_GREETED, false)
        set(v) = sp.edit().putBoolean(K_GREETED, v).apply()

    companion object {
        private const val FILE = "emotion_pet"

        private const val K_PET_ID = "pet_id"
        private const val K_NAME = "pet_name"
        private const val K_NAME_CUSTOM = "pet_name_custom"
        private const val K_BOUNCE = "bounce"
        private const val K_PARTICLES = "particles"
        private const val K_SPRITE_TYPE = "sprite_type"
        private const val K_EMOJI = "emoji"
        private const val K_SIZE = "size_dp"
        private const val K_SPEED = "speed"
        private const val K_MIRROR = "mirrored"
        private const val K_PALETTE = "palette"
        private const val K_WALLPAPER = "wallpaper"
        private const val K_FULL = "fullness"
        private const val K_ENERGY = "energy"
        private const val K_MOOD = "mood"
        private const val K_SLEEPING = "sleeping"
        private const val K_LAST_TICK = "last_tick"
        private const val K_KEEP_AWAKE = "keep_awake"
        private const val K_OVERLAY = "overlay_enabled"
        private const val K_AI_PROVIDER = "ai_provider"
        private const val K_AI_MODEL = "ai_model"
        private const val K_AI_KEY = "ai_key"
        private const val K_AI_BASE = "ai_base_url"
        private const val K_AI_PERSONA = "ai_personality"
        private const val K_CHAT = "chat_log"
        private const val K_GREETED = "greeted"

        const val TYPE_EMOJI = "emoji"
        const val TYPE_IMAGE = "image"

        const val MIN_SIZE = 50
        const val MAX_SIZE = 190
    }
}
