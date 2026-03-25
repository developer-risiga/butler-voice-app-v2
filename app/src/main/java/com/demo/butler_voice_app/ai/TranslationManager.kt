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
        "hi" to "Hindi",
        "te" to "Telugu",
        "ta" to "Tamil",
        "ml" to "Malayalam"
    )

    // Regex to find proper nouns (capitalized words) and order IDs
    private val properNounRegex = Regex("""(?<!\. )[A-Z][a-z]{1,20}""")
    private val orderIdRegex    = Regex("""[A-Z0-9]{5,8}""")
    private val productNameRegex = Regex("""[A-Z][A-Z ]{3,}""") // ALL CAPS product names

    suspend fun translate(text: String, targetLang: String): String {
        if (targetLang == "en" || text.isBlank()) return text

        val cacheKey = "$targetLang:$text"
        cache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val langName = langNames[targetLang] ?: return@withContext text

                // ── STEP 1: Extract tokens that must NOT be translated ──
                val protectedTokens = mutableMapOf<String, String>()
                var counter = 0
                var processedText = text

                // Protect ALL CAPS product names (e.g. "INDIA GATE SUPER RICE")
                productNameRegex.findAll(text).forEach { match ->
                    val token   = match.value.trim()
                    if (token.length > 3) {
                        val placeholder = "PROT${counter++}"
                        protectedTokens[placeholder] = token
                        processedText = processedText.replace(token, placeholder)
                    }
                }

                // Protect order IDs (e.g. "3BDB79")
                orderIdRegex.findAll(processedText).forEach { match ->
                    val token = match.value
                    if (!protectedTokens.containsValue(token)) {
                        val placeholder = "PROT${counter++}"
                        protectedTokens[placeholder] = token
                        processedText = processedText.replace(token, placeholder)
                    }
                }

                // Protect capitalized proper nouns (e.g. "Third", "Pushpak")
                properNounRegex.findAll(processedText).forEach { match ->
                    val token = match.value
                    // Don't protect common English words
                    val commonWords = setOf(
                        "Your", "You", "The", "What", "Would", "Like", "Order",
                        "Added", "Anything", "Welcome", "Back", "Last", "Time",
                        "Account", "Created", "Great", "Nice", "Meet", "Got",
                        "Now", "Say", "Please", "Sorry", "Could", "Find",
                        "Place", "Shall", "Thank", "Goodbye", "Using", "Backup"
                    )
                    if (token !in commonWords && !protectedTokens.containsValue(token)) {
                        val placeholder = "PROT${counter++}"
                        protectedTokens[placeholder] = token
                        processedText = processedText.replace(token, placeholder)
                    }
                }

                // ── STEP 2: Translate with placeholders ──
                val prompt = """Translate ONLY the following text into $langName.
Keep all placeholder tokens (PROT0, PROT1, etc.) exactly as-is — do NOT translate them.
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
                    Log.e("Translate", "Error ${response.code}: $responseBody")
                    return@withContext text
                }

                var translated = JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .removePrefix("\"")
                    .removeSuffix("\"")

                // ── STEP 3: Restore protected tokens ──
                protectedTokens.forEach { (placeholder, original) ->
                    translated = translated.replace(placeholder, original)
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
