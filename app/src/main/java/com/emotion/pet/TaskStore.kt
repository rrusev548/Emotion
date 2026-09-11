package com.emotion.pet

import org.json.JSONArray
import org.json.JSONObject

/** Задачи/напомняния на Peta — пазят се локално като компактен JSON. */
object TaskStore {

    data class Task(
        val id: Long,
        val text: String,
        val done: Boolean = false,
        val createdAt: Long = System.currentTimeMillis(),
        /** Краен срок (ms epoch) — null, ако задачата няма напомняне. */
        val dueAt: Long? = null
    )

    private const val MAX_TASKS = 300

    fun load(prefs: Prefs): MutableList<Task> {
        val out = mutableListOf<Task>()
        try {
            val arr = JSONArray(prefs.tasksLog)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString("text")
                if (text.isNotBlank()) {
                    val due = if (o.has("due")) o.optLong("due").takeIf { it > 0 } else null
                    out.add(Task(o.optLong("id"), text, o.optBoolean("done"), o.optLong("ts"), due))
                }
            }
        } catch (_: Exception) {
            // повредени данни — започваме на чисто
        }
        return out
    }

    private fun save(prefs: Prefs, tasks: List<Task>) {
        val arr = JSONArray()
        tasks.takeLast(MAX_TASKS).forEach { t ->
            val o = JSONObject()
                .put("id", t.id)
                .put("text", t.text)
                .put("done", t.done)
                .put("ts", t.createdAt)
            if (t.dueAt != null) o.put("due", t.dueAt)
            arr.put(o)
        }
        prefs.tasksLog = arr.toString()
    }

    fun add(prefs: Prefs, text: String, dueAt: Long? = null): MutableList<Task> {
        val list = load(prefs)
        list.add(Task(System.currentTimeMillis(), text, dueAt = dueAt))
        save(prefs, list)
        return list
    }

    fun setDone(prefs: Prefs, id: Long, done: Boolean): MutableList<Task> {
        val list = load(prefs)
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = list[idx].copy(done = done)
        save(prefs, list)
        return list
    }

    fun remove(prefs: Prefs, id: Long): MutableList<Task> {
        val list = load(prefs).apply { removeAll { it.id == id } }
        save(prefs, list)
        return list
    }
}
