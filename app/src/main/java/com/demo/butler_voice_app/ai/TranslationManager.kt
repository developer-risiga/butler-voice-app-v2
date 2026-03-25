package com.demo.butler_voice_app.ai

import android.util.Log
import com.demo.butler_voice_app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TranslationManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = LinkedHashMap<String, String>(100, 0.75f, true)

    private val langNames = mapOf(
        "hi" to "Hindi", "te" to "Telugu",
        "ta" to "Tamil", "ml" to "Malayalam", "pa" to "Punjabi"
    )

    private val commonWords = setOf(
        "Your", "You", "The", "What", "Would", "Like", "Order",
        "Added", "Anything", "Welcome", "Back", "Last", "Time",
        "Account", "Created", "Great", "Nice", "Meet", "Got",
        "Now", "Say", "Please", "Sorry", "Could", "Find",
        "Place", "Shall", "Thank", "Goodbye", "Tell", "Say"
    )

    suspend fun translate(text: String, targetLang: String): String {
        if (targetLang == "en" || text.isBlank()) return text

        val cacheKey = "$targetLang:$text"
        cache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val langName = langNames[targetLang] ?: return@withContext text

                // Build placeholder map
                val placeholderMap = mutableMapOf<String, String>()
                var processedText = text
                var counter = 0

                fun protect(token: String) {
                    if (token.isNotBlank() && !placeholderMap.containsValue(token)) {
                        val ph = "XX${counter}XX"
                        counter++
                        placeholderMap[ph] = token
                        processedText = processedText.replace(token, ph)
                    }
                }

                // 1. ALL CAPS product names e.g. "INDIA GATE SUPER RICE"
                Regex("""\b[A-Z][A-Z ]{2,}\b""").findAll(text).forEach {
                    protect(it.value.trim())
                }

                // 2. Order IDs e.g. "BOR-100021"
                Regex("""\b[A-Z]{2,}-\d+\b""").findAll(text).forEach {
                    protect(it.value)
                }

                // 3. Capitalized proper names e.g. "Pushp", "Messi"
                Regex("""\b[A-Z][a-z]{1,19}\b""").findAll(text).forEach {
                    if (it.value !in commonWords) protect(it.value)
                }

                val prompt = """Translate to $langName. Use informal friendly tone (like a voice assistant talking to a friend).
IMPORTANT: Keep all XX0XX, XX1XX, XX2XX placeholders EXACTLY unchanged.
Return ONLY the translated sentence. No quotes, no explanation.
Text: $processedText"""

                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }

                val body = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", messages)
                    put("max_tokens", 200)
                    put("temperature", 0.2)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response     = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext text

                if (!response.isSuccessful) {
                    Log.e("Translate", "Error ${response.code}")
                    return@withContext text
                }

                var translated = JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .removePrefix("\"").removeSuffix("\"")

                // Restore all placeholders
                placeholderMap.forEach { (ph, original) ->
                    translated = translated.replace(ph, original)
                }

                // Safety fallback — if any placeholder leaked through
                if (Regex("XX\\d+XX").containsMatchIn(translated)) {
                    Log.w("Translate", "Placeholder leak detected, using original")
                    return@withContext text
                }

                Log.d("Translate", "SUCCESS → $translated")
                if (cache.size >= 100) cache.remove(cache.keys.first())
                cache[cacheKey] = translated
                translated

            } catch (e: Exception) {
                Log.e("Translate", "Exception: ${e.message}")
                text
            }
        }
    }
}
