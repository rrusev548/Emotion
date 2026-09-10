package com.emotion.pet

import android.graphics.Color

/** Готови образи, цветове на стаята и OpenAI-съвместими доставчици на ИИ. */
object Presets {

    val EMOJIS = listOf(
        "🐶", "🐱", "🐰", "🐼", "🐸", "🐧", "🦊", "🐨",
        "🐯", "🦄", "🐢", "🐙", "🐝", "👻", "🤖", "⭐",
        "❤️", "🌙", "🍕", "🌸"
    )

    enum class Palette(val top: Int, val bottom: Int) {
        NIGHT(Color.parseColor("#17102E"), Color.parseColor("#31214F")),
        DAWN(Color.parseColor("#3A1436"), Color.parseColor("#8A3D58")),
        MINT(Color.parseColor("#0F2529"), Color.parseColor("#1E4A44"));

        companion object {
            fun of(name: String?): Palette =
                entries.firstOrNull { it.name.equals(name, true) } ?: NIGHT
        }
    }

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
}
