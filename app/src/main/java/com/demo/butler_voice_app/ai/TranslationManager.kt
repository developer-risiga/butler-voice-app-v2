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

    private const val TAG = "Translate"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // LRU cache: 150 entries
    private val cache = object : LinkedHashMap<String, String>(150, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > 150
    }

    private val langNames = mapOf(
        "hi" to "Hindi", "te" to "Telugu",
        "ta" to "Tamil", "ml" to "Malayalam",
        "pa" to "Punjabi", "kn" to "Kannada",
        "mr" to "Marathi", "bn" to "Bengali"
    )

    // Words that must never be translated (proper nouns, brand names, etc.)
    private val preservedWords = setOf(
        "Your", "You", "The", "What", "Would", "Like", "Order",
        "Added", "Anything", "Welcome", "Back", "Last", "Time",
        "Account", "Created", "Great", "Nice", "Meet", "Got",
        "Now", "Say", "Please", "Sorry", "Could", "Find",
        "Place", "Shall", "Thank", "Goodbye", "Tell", "Say",
        "Butler", "UPI", "QR", "OTP", "ID"
    )

    suspend fun translate(text: String, targetLang: String): String {
        if (text.isBlank()) return text

        // ── KEY FIX: skip if source text is already in target language ─────────
        val sourceScript = detectScript(text)
        val targetBase   = targetLang.substringBefore("-")

        // If Devanagari/Telugu/etc. script detected and target matches → skip
        if (sourceScript == targetBase) {
            Log.d(TAG, "Same language ($sourceScript == $targetBase), skipping")
            return text
        }

        // If target is English and text appears to already be English → skip
        if (targetBase == "en" && sourceScript == "en") {
            return text
        }

        // If target is English (no translation needed for English-target in Indian context)
        if (targetBase == "en") return text

        val cacheKey = "$targetLang:$text"
        cache[cacheKey]?.let {
            Log.d(TAG, "Cache hit")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                val langName = langNames[targetBase] ?: return@withContext text

                // ── Build placeholder map to protect proper nouns / codes ─────
                val placeholderMap = mutableMapOf<String, String>()
                var processedText  = text
                var counter = 0

                fun protect(token: String) {
                    val trimmed = token.trim()
                    if (trimmed.isNotBlank() && !placeholderMap.containsValue(trimmed)) {
                        val ph = "XX${counter}XX"
                        counter++
                        placeholderMap[ph] = trimmed
                        processedText = processedText.replace(trimmed, ph)
                    }
                }

                // 1. ALL-CAPS product names e.g. "INDIA GATE SUPER RICE"
                Regex("""\b[A-Z][A-Z ]{2,}\b""").findAll(text).forEach { protect(it.value.trim()) }

                // 2. Order / Booking IDs e.g. "BUT-000049", "BK-1234"
                Regex("""\b[A-Z]{2,}-\d+\b""").findAll(text).forEach { protect(it.value) }

                // 3. Numeric values with units e.g. "143 rupees", "10 mins"
                Regex("""\d+\s*(rupees?|rs\.?|₹|mins?|minutes?|km|kg|g|ml|l)\b""", RegexOption.IGNORE_CASE)
                    .findAll(text).forEach { protect(it.value.trim()) }

                // 4. Capitalized proper names that aren't common words
                Regex("""\b[A-Z][a-z]{1,19}\b""").findAll(text).forEach {
                    if (it.value !in preservedWords) protect(it.value)
                }

                val prompt = """Translate the following text to $langName.
Use informal, friendly, spoken Indian tone — like a helpful voice assistant talking to a friend.
Hinglish is fine for casual phrases if it sounds natural.
CRITICAL: Keep every placeholder EXACTLY as-is: XX0XX, XX1XX, XX2XX etc — do NOT translate them.
Return ONLY the translated text. No quotes, no preamble, no explanation.
Text: $processedText"""

                val messages = JSONArray().apply {
                    put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                }

                val reqBody = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", messages)
                    put("max_tokens", 300)
                    put("temperature", 0.2)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(reqBody)
                    .build()

                val response     = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext text

                if (!response.isSuccessful) {
                    // ── KEY FIX: graceful fallback on any HTTP error (400, 429, 500) ──
                    Log.e(TAG, "Error ${response.code} — using original text as fallback")
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

                // Restore all placeholders
                placeholderMap.forEach { (ph, original) ->
                    translated = translated.replace(ph, original)
                }

                // Safety check — if any placeholder leaked, return original
                if (Regex("XX\\d+XX").containsMatchIn(translated)) {
                    Log.w(TAG, "Placeholder leak detected, falling back to original")
                    return@withContext text
                }

                Log.d(TAG, "SUCCESS → $translated")
                cache[cacheKey] = translated
                translated

            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message} — returning original")
                text  // ALWAYS fall back gracefully
            }
        }
    }

    // ── Script/language detection from text content ──────────────────────────

    /**
     * Detects the base language from Unicode character ranges.
     * Returns BCP-47 base code: "hi", "te", "ta", "ml", "kn", "en"
     */
    fun detectScript(text: String): String {
        var devanagari = 0; var telugu = 0; var tamil = 0
        var malayalam  = 0; var kannada = 0; var latin = 0

        for (c in text) {
            when (c.code) {
                in 0x0900..0x097F -> devanagari++   // Hindi / Marathi
                in 0x0C00..0x0C7F -> telugu++
                in 0x0B80..0x0BFF -> tamil++
                in 0x0D00..0x0D7F -> malayalam++
                in 0x0C80..0x0CFF -> kannada++
                in 0x0041..0x007A -> latin++         // ASCII letters
                in 0x0061..0x007A -> latin++
            }
        }

        val max = maxOf(devanagari, telugu, tamil, malayalam, kannada)
        return when {
            max == 0          -> "en"
            max == devanagari -> "hi"
            max == telugu     -> "te"
            max == tamil      -> "ta"
            max == malayalam  -> "ml"
            max == kannada    -> "kn"
            else              -> "en"
        }
    }
}