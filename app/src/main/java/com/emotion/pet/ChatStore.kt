package com.emotion.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** История на чата — пази се локално като компактен JSON. */
object ChatStore {

    data class Msg(val role: String, val content: String, val ts: Long = System.currentTimeMillis())

    const val ROLE_USER = "user"
    const val ROLE_PET = "assistant"
    private const val MAX_MESSAGES = 200

    fun load(prefs: Prefs): MutableList<Msg> {
        val out = mutableListOf<Msg>()
        try {
            val arr = JSONArray(prefs.chatLog)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val role = o.optString("r")
                val content = o.optString("c")
                if (content.isNotBlank()) out.add(Msg(role, content, o.optLong("t")))
            }
        } catch (_: Exception) {
            // повредена история — започваме на чисто
        }
        return out
    }

    fun save(prefs: Prefs, messages: List<Msg>) {
        val arr = JSONArray()
        messages.takeLast(MAX_MESSAGES).forEach { m ->
            arr.put(JSONObject().put("r", m.role).put("c", m.content).put("t", m.ts))
        }
        prefs.chatLog = arr.toString()
    }

    fun append(prefs: Prefs, msg: Msg): MutableList<Msg> {
        val list = load(prefs).also { it.add(msg) }
        save(prefs, list)
        return list
    }

    fun clear(prefs: Prefs) {
        prefs.chatLog = "[]"
    }

    /** Роля "user"/"assistant" → ролята, която моделът очаква. */
    fun toApi(messages: List<Msg>): List<AiClient.Msg> =
        messages.map { AiClient.Msg(if (it.role == ROLE_USER) "user" else "assistant", it.content) }

    fun systemPrompt(ctx: Context, prefs: Prefs): String {
        val persona = prefs.aiPersonality.ifBlank { ctx.getString(R.string.ai_personality_hint) }
        return """
            Ти си "${prefs.petName}" — малък виртуален любимец, който живее на екрана на телефона на потребителя.
            Характер: $persona
            Говориш кратко (1–3 изречения), топло и забавно, винаги в ролята на любимец — никога не казвай, че си езиков модел.
            Можеш да използваш 1–2 емоджита. Отговаряй на езика, на който пише потребителят (по подразбиране български).
            Състояние в момента → ситост: ${prefs.fullness}/100, енергия: ${prefs.energy}/100, настроение: ${prefs.mood}/100.
            ${if (prefs.sleeping) "В момента си сънен и току-що се събуди." else ""}
        """.trimIndent()
    }
}
