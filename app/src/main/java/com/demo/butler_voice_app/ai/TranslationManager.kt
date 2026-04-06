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

    private val cache = object : LinkedHashMap<String, String>(150, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > 150
    }

    private val langNames = mapOf(
        "hi" to "Hindi", "te" to "Telugu",
        "ta" to "Tamil", "ml" to "Malayalam",
        "pa" to "Punjabi", "kn" to "Kannada",
        "mr" to "Marathi", "bn" to "Bengali"
    )

    private val preservedWords = setOf(
        "Butler", "UPI", "QR", "OTP", "ID", "GPay", "PhonePe", "Paytm",
        "Roy", "BHIM", "NEFT", "IMPS"
    )

    // ── Hinglish word list ─────────────────────────────────────────────────────
    //
    // WHY THIS EXISTS:
    // ButlerPersonalityEngine generates phrases like:
    //   "Kuch suna nahi. Thoda aur boliye."
    //   "Ho gaya Roy, Unity Basmati le liya... aur kuch mangwaoge?"
    //   "Bas itna hi hai na Roy? ek Unity Basmati, chauvan rupaye, order de doon?"
    //
    // These are ALREADY correct Hinglish — ElevenLabs reads them perfectly.
    // But TranslationManager.detectScript() returns "en" for them (no Devanagari),
    // so it sends them to GPT for translation to "Hindi".
    // GPT returns mixed: "Kuch सुना नहीं. Thoda और बोलिए."
    // TTSManager then has to normalize this back to "Kuch suna nahi. Thoda aur boliye."
    //
    // This round-trip (Roman → mixed Devanagari → Roman) is wasteful and creates
    // messy logs. The fix: detect Roman Hinglish and skip translation entirely.
    //
    // A phrase is Hinglish if it contains 2+ words from this list.
    // This covers all Butler output phrases without false-positives on real English.
    // ─────────────────────────────────────────────────────────────────────────────
    private val HINGLISH_WORDS = setOf(
        // Verbs
        "kuch", "nahi", "chahiye", "boliye", "bolein", "bolo", "batao",
        "mangwao", "mangwaoge", "mangwaayein", "lena", "dena", "dijiye",
        "karein", "kariye", "karna", "milega", "milegi", "mila", "mili",
        "hoga", "hogi", "hua", "hui", "suna", "suni", "suniye",
        "chaliye", "chalo", "jaao", "ruko", "dekho", "laao", "bhejo",
        "bataiye", "pahunchega", "pahunchegi", "aayega", "aayegi",
        "le liya", "le loon", "le lo", "kar diya", "kar do", "kar di",
        "bata do", "bhej do", "ho gaya", "ho gayi", "aa gaya", "aa gayi",
        "daal do", "rakh do", "nikaalo",
        // Adjectives & adverbs
        "theek", "achha", "achhi", "achhe", "badhiya", "pakka",
        "bilkul", "zaroor", "jaldi", "thoda", "thodi", "zyada",
        "khaali", "taiyaar", "shaandaar", "khatam", "sabse", "abhi",
        "zara", "sasta", "mahanga", "taaza",
        // Nouns
        "rupaye", "rupiya", "paise", "minute", "awaaz", "naam",
        "samaan", "baat", "kaam", "ghar", "aaj", "kal", "baad",
        "pehle", "phir", "ab", "paas", "dukaan", "keemat", "daam",
        "daal", "chawal", "tel", "atta", "doodh", "chai", "ghee",
        "namak", "cheeni", "masala", "sabzi", "dahi", "makhan",
        "anda", "ande", "kilo", "packet", "bottle", "brand",
        // Question words
        "kya", "kaunsa", "kitna", "kitni", "kitne", "kahaan", "kaise",
        "koi", "kaun",
        // Pronouns & connectors
        "mujhe", "aapko", "aapka", "hamara", "lekin", "saath",
        "mein", "aur", "bhi", "par", "se", "ke", "ka", "ki",
        // Affirmations & social
        "haan", "nahi", "bas", "shukriya", "maaf", "ghabraiye",
        // Grocery & order specific
        "order", "cart", "payment", "confirm", "delivery", "booking",
        "checkout", "lagaoon", "daalu", "mangwaoge", "le doon",
        "doon", "lagaa", "chahiye", "milega",
        // Time
        "tees", "bees", "pachaas", "chaalis", "ek sau", "do sau",
        "paanch sau", "chauvan", "paintalis", "ikkaavan"
    )

    private fun isRomanHinglish(text: String): Boolean {
        // If text has any Devanagari, it's already mixed — don't skip translation
        if (text.any { c -> c.code in 0x0900..0x097F }) return false

        val lower = text.lowercase()
        val matchCount = HINGLISH_WORDS.count { word -> lower.contains(word) }
        return matchCount >= 2
    }

    suspend fun translate(text: String, targetLang: String): String {
        if (text.isBlank()) return text

        val targetBase = targetLang.substringBefore("-")

        // ── Already English → no translation needed ───────────────────────────
        if (targetBase == "en") return text

        // ── Same script detected → skip ───────────────────────────────────────
        val sourceScript = detectScript(text)
        if (sourceScript == targetBase) {
            Log.d(TAG, "Same language ($sourceScript == $targetBase), skipping")
            return text
        }

        // ── Roman Hinglish → skip translation ─────────────────────────────────
        //
        // ButlerPersonalityEngine already generates correct Hinglish in Roman script.
        // Sending "Ho gaya Roy, le liya... aur kuch mangwaoge?" to GPT for "Hindi"
        // translation produces mixed output: "Ho गया Roy, ले लिया... aur kuch मांगवाओगे?"
        // TTSManager then has to clean this up. Skip the round-trip entirely.
        //
        // Note: this only fires for Indic targets (hi, mr, te, etc.) because the
        // targetBase == "en" early return above handles English already.
        if (targetBase in setOf("hi", "mr", "pa", "gu") && isRomanHinglish(text)) {
            Log.d(TAG, "Roman Hinglish — skipping translation, using as-is")
            return text
        }

        val cacheKey = "$targetLang:$text"
        cache[cacheKey]?.let {
            Log.d(TAG, "Cache hit")
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                val langName = langNames[targetBase] ?: return@withContext text

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

                // Protect proper nouns: ALL-CAPS names, Order/Booking IDs, numbers with units
                Regex("""\b[A-Z][A-Z ]{2,}\b""").findAll(text).forEach { protect(it.value.trim()) }
                Regex("""\b[A-Z]{2,}-\d+\b""").findAll(text).forEach { protect(it.value) }
                Regex("""\d+\s*(rupees?|rs\.?|₹|mins?|minutes?|km|kg|g|ml|l)\b""", RegexOption.IGNORE_CASE)
                    .findAll(text).forEach { protect(it.value.trim()) }
                Regex("""\b[A-Z][a-z]{1,19}\b""").findAll(text).forEach {
                    if (it.value !in preservedWords) protect(it.value)
                }

                val prompt = """Translate the following text to $langName.
Use informal, friendly, spoken Indian tone — like a helpful shopkeeper talking to a regular customer.
Hinglish (Roman script Hindi with some English mixed in) is preferred over pure Devanagari.
Return the text in Roman script, NOT Devanagari, whenever possible.
CRITICAL: Keep every placeholder EXACTLY as-is: XX0XX, XX1XX, XX2XX etc — do NOT translate them.
Return ONLY the translated text. No quotes, no preamble.
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
                    Log.e(TAG, "Error ${response.code} — falling back to original")
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

                placeholderMap.forEach { (ph, original) ->
                    translated = translated.replace(ph, original)
                }

                if (Regex("XX\\d+XX").containsMatchIn(translated)) {
                    Log.w(TAG, "Placeholder leak — falling back to original")
                    return@withContext text
                }

                Log.d(TAG, "SUCCESS → $translated")
                cache[cacheKey] = translated
                translated

            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message} — returning original")
                text
            }
        }
    }

    fun detectScript(text: String): String {
        var devanagari = 0; var telugu = 0; var tamil = 0
        var malayalam  = 0; var kannada = 0; var latin = 0

        for (c in text) {
            when (c.code) {
                in 0x0900..0x097F -> devanagari++
                in 0x0C00..0x0C7F -> telugu++
                in 0x0B80..0x0BFF -> tamil++
                in 0x0D00..0x0D7F -> malayalam++
                in 0x0C80..0x0CFF -> kannada++
                in 0x0041..0x007A -> latin++
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