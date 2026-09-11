package com.emotion.pet

import org.json.JSONArray
import org.json.JSONObject

/** "Памет" на Peta — предпочитания/бележки, които потребителят иска да запомни. Пази се локално. */
object MemoryStore {

    data class Note(
        val id: Long,
        val text: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    private const val MAX_NOTES = 200

    fun load(prefs: Prefs): MutableList<Note> {
        val out = mutableListOf<Note>()
        try {
            val arr = JSONArray(prefs.memoryLog)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString("text")
                if (text.isNotBlank()) out.add(Note(o.optLong("id"), text, o.optLong("ts")))
            }
        } catch (_: Exception) {
            // повредени данни — започваме на чисто
        }
        return out
    }

    private fun save(prefs: Prefs, notes: List<Note>) {
        val arr = JSONArray()
        notes.takeLast(MAX_NOTES).forEach { n ->
            arr.put(JSONObject().put("id", n.id).put("text", n.text).put("ts", n.createdAt))
        }
        prefs.memoryLog = arr.toString()
    }

    fun add(prefs: Prefs, text: String): MutableList<Note> {
        val list = load(prefs)
        list.add(Note(System.currentTimeMillis(), text))
        save(prefs, list)
        return list
    }

    fun remove(prefs: Prefs, id: Long): MutableList<Note> {
        val list = load(prefs).apply { removeAll { it.id == id } }
        save(prefs, list)
        return list
    }
}
