package com.emotion.pet

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Минимален клиент за всеки OpenAI-съвместим /chat/completions endpoint.
 * Работи с OpenAI, Groq, OpenRouter, Ollama, LM Studio и др.
 */
object AiClient {

    data class Msg(val role: String, val content: String)

    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Msg>,
        onResult: (ok: Boolean, text: String) -> Unit
    ) {
        pool.execute {
            var ok = false
            var text = ""
            try {
                val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                }

                val messages = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                history.takeLast(16).forEach { m ->
                    messages.put(JSONObject().put("role", m.role).put("content", m.content))
                }

                val payload = JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    .put("temperature", 0.8)
                    .put("max_tokens", 400)

                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

                if (code in 200..299) {
                    val content = JSONObject(raw)
                        .optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content").orEmpty()
                    if (content.isBlank()) {
                        text = "Празен отговор от модела."
                    } else {
                        ok = true
                        text = content.trim()
                    }
                } else {
                    text = parseError(code, raw)
                }
                conn.disconnect()
            } catch (e: Exception) {
                text = e.message ?: e.javaClass.simpleName
            }
            main.post { onResult(ok, text) }
        }
    }

    private fun parseError(code: Int, raw: String): String = try {
        val obj = JSONObject(raw)
        val msg = obj.optJSONObject("error")?.optString("message").orEmpty()
        if (msg.isBlank()) "HTTP $code" else "HTTP $code — $msg"
    } catch (_: Exception) {
        "HTTP $code"
    }

    /** Бърз тест, че ключът/моделът/URL-ът работят. */
    fun test(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        onResult: (ok: Boolean, text: String) -> Unit
    ) {
        chat(baseUrl, apiKey, model, systemPrompt, listOf(Msg("user", "ping")), onResult)
    }
}
