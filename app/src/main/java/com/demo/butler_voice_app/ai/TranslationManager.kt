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

    // Simple LRU-style cache to avoid re-translating same phrases
    private val cache = LinkedHashMap<String, String>(50, 0.75f, true)

    private val langNames = mapOf(
        "hi" to "Hindi",
        "te" to "Telugu",
        "ta" to "Tamil",
        "ml" to "Malayalam"
    )

    suspend fun translate(text: String, targetLang: String): String {
        if (targetLang == "en" || text.isBlank()) return text

        val cacheKey = "${targetLang}:$text"
        cache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val langName = langNames[targetLang] ?: return@withContext text

                val prompt = """You are a grocery assistant. Translate ONLY the following English text into $langName.
Return ONLY the translated sentence. No quotes, no explanation.
Text: $text"""

                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }

                val body = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", messages)
                    put("max_tokens", 150)
                    put("temperature", 0.3)
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
                    Log.e("Translate", "Error ${response.code}: $responseBody")
                    return@withContext text
                }

                val translated = JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .removePrefix("\"")
                    .removeSuffix("\"")

                Log.d("Translate", "SUCCESS → $translated")

                // Cache it
                if (cache.size >= 50) cache.remove(cache.keys.first())
                cache[cacheKey] = translated

                translated

            } catch (e: Exception) {
                Log.e("Translate", "Exception: ${e.message}")
                text
            }
        }
    }
}
