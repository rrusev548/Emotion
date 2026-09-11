package com.emotion.pet

import android.graphics.Color

/** Каталог: любимци, акцентни цветове, палитри на стаята и ИИ доставчици. */
object Presets {

    // ==================== ЛЮБИМЦИ ====================

    data class Pet(
        val id: String,
        val name: String,
        val emoji: String,
        val tagline: String,
        val personality: String,
        val accent: Int,
        /** Вграден образ (вместо emoji) — рисува се в стаята, докато потребителят не качи своя снимка. */
        val spriteRes: Int? = null
    ) {
        val accentHex: String get() = String.format("#%06X", 0xFFFFFF and accent)
    }

    val PETS = listOf(
        Pet(
            "peta", "Peta", "😈", "Един Peta, много мозъци",
            "дяволито, будно и лоялно демонче-спътник; закачливо и уверено, но винаги на твоя страна — " +
                "смени му мозъка от Brain Lab и той пак си остава същият",
            Color.parseColor("#FF4438"),
            spriteRes = R.drawable.peta_devil
        ),
        Pet(
            "shiba", "Моки", "🐶", "Игрив и верен",
            "игрив, верен и леко ревнив; обича топки, разходки и да те посреща всеки път",
            Color.parseColor("#FF9A5C")
        ),
        Pet(
            "cat", "Кики", "🐱", "Независима и загадъчна",
            "независима и саркастична котка; обича да спи по 18 часа и да съди тихо отстрани",
            Color.parseColor("#8E7BFF")
        ),
        Pet(
            "bunny", "Бъни", "🐰", "Скоклива и сладка",
            "скоклива и сладка; говори бързо, обича моркови и подскача на всеки ъгъл",
            Color.parseColor("#FF7BA9")
        ),
        Pet(
            "panda", "Бамбу", "🐼", "Спокоен и мечтателен",
            "спокоен, мързелив и мечтателен; философства между две хапки бамбук",
            Color.parseColor("#7FD1C1")
        ),
        Pet(
            "fox", "Фокси", "🦊", "Хитра и закачлива",
            "хитра, закачлива и самоуверена; обича загадки и леки пакости",
            Color.parseColor("#FF7A45")
        ),
        Pet(
            "penguin", "Пипи", "🐧", "Делови и елегантен",
            "делови, точен и елегантен; говори като малък мениджър с голямо сърце",
            Color.parseColor("#5AA9FF")
        ),
        Pet(
            "unicorn", "Луна", "🦄", "Мечтателна и магична",
            "мечтателна, магична и вдъхновяваща; вярва, че всяко желание се сбъдва",
            Color.parseColor("#C77DFF")
        ),
        Pet(
            "dragon", "Спаркс", "🐲", "Дързък и огнен",
            "дързък, огнен и уверен; обича предизвикателства и говори с ентусиазъм",
            Color.parseColor("#FF5C7A")
        ),
        Pet(
            "robot", "Байт", "🤖", "Логичен и забавен",
            "логичен и забавен робот; обича числа, факти и сухи шеги",
            Color.parseColor("#4FD1FF")
        ),
        Pet(
            "ghost", "Буу", "👻", "Срамежлив и загадъчен",
            "срамежлив, загадъчен и нежен; появява се тихо и обича истории преди сън",
            Color.parseColor("#A9B6FF")
        ),
        Pet(
            "star", "Блясък", "⭐", "Лъчезарен и позитивен",
            "лъчезарен и позитивен; намира доброто във всичко и хвали постоянно",
            Color.parseColor("#FFC94F")
        ),
        Pet(
            "frog", "Скок", "🐸", "Весел и шумен",
            "весел, шумен и общителен; говори на възклицания и обича вода",
            Color.parseColor("#7BE07B")
        )
    )

    fun pet(id: String?): Pet = PETS.firstOrNull { it.id == id } ?: PETS.first()

    val EMOJIS: List<String> = PETS.map { it.emoji } + listOf(
        "🐨", "🐯", "🐢", "🐙", "🐝", "❤️", "🌙", "🍕", "🌸", "🎮"
    )

    // ==================== СТАЯ ====================

    enum class Palette(val label: String, val top: Int, val bottom: Int, val glow: Int) {
        NIGHT("Тъмнина", Color.parseColor("#120C22"), Color.parseColor("#2A1B4D"), 0x33FFFFFF),
        SUNSET("Залез", Color.parseColor("#2B1030"), Color.parseColor("#7A2E52"), 0x33FFFFFF),
        MINT("Мента", Color.parseColor("#08201F"), Color.parseColor("#16453F"), 0x33FFFFFF),
        OCEAN("Океан", Color.parseColor("#08172B"), Color.parseColor("#133A63"), 0x33FFFFFF);

        companion object {
            fun of(name: String?): Palette =
                entries.firstOrNull { it.name.equals(name, true) } ?: NIGHT
        }
    }

    // ==================== ИИ ====================

    data class AiProvider(
        val id: String,
        val label: String,
        val baseUrl: String,
        val defaultModel: String,
        val freeModel: String? = null,
        val keyUrl: String = ""
    )

    val PROVIDERS = listOf(
        AiProvider(
            id = "openai",
            label = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            keyUrl = "https://platform.openai.com/api-keys"
        ),
        AiProvider(
            id = "groq",
            label = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            freeModel = "llama-3.1-8b-instant",
            keyUrl = "https://console.groq.com/keys"
        ),
        AiProvider(
            id = "openrouter",
            label = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "openai/gpt-4o-mini",
            freeModel = "meta-llama/llama-3.3-70b-instruct:free",
            keyUrl = "https://openrouter.ai/keys"
        ),
        AiProvider(
            id = "gemini",
            label = "Gemini",
            // Google дава официален OpenAI-съвместим ендпойнт — работи директно с AiClient
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            defaultModel = "gemini-3.8-flash",
            keyUrl = "https://aistudio.google.com/apikey"
        ),
        AiProvider(
            id = "claude",
            label = "Claude",
            baseUrl = "https://api.anthropic.com/v1",
            defaultModel = "claude-sonnet-5",
            keyUrl = "https://console.anthropic.com/settings/keys"
        ),
        AiProvider(
            id = "custom",
            label = "Custom",
            baseUrl = "",
            defaultModel = "",
            keyUrl = ""
        )
    )

    fun provider(id: String?): AiProvider =
        PROVIDERS.firstOrNull { it.id == id } ?: PROVIDERS.first()

    fun effectiveBaseUrl(prefs: Prefs): String {
        val p = provider(prefs.aiProvider)
        return if (prefs.aiProvider == "custom") prefs.aiBaseUrl else p.baseUrl
    }

    fun effectiveModel(prefs: Prefs): String {
        val p = provider(prefs.aiProvider)
        if (prefs.aiModel.isNotBlank()) return prefs.aiModel
        return p.defaultModel
    }

    /** Характерът на любимеца — собственият, ако е зададен, иначе от каталога. */
    fun personality(prefs: Prefs): String =
        prefs.aiPersonality.ifBlank { pet(prefs.petId).personality }
}
