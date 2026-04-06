package com.demo.butler_voice_app.ai

import android.util.Log
import com.demo.butler_voice_app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class IntentRouting {
    data class GoToGrocery(val items: List<ParsedItem>) : IntentRouting()
    data class GoToService(val category: String, val timePreference: String?) : IntentRouting()
    object FinishOrder   : IntentRouting()
    object ConfirmOrder  : IntentRouting()
    object CancelOrder   : IntentRouting()
    object AskClarify    : IntentRouting()
    object ShowMenu      : IntentRouting()
    object Unknown       : IntentRouting()
}

data class FullParsedIntent(
    val routing: IntentRouting,
    val language: String,
    val confidence: Double,
    val serviceCategory: String?,
    val timePreference: String?,
    val rawText: String
)

object AIParser {
    private const val TAG = "AIParser"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val parseMutex = Mutex()
    private var lastParsedTranscript: String = ""


    private val SYSTEM_PROMPT = """
You are an intent parser for Butler, a multilingual Indian voice assistant.
User speaks Hindi (hi), English (en), Marathi (mr), or Telugu (te).
Return ONLY valid JSON. No markdown. No explanation.

Intent options:
- "grocery_order"    : user wants to buy/add specific grocery items.
                       KEY RULE: if the user mentions ANY product name (rice, dal, oil,
                       milk, chawal, daal, doodh, atta, cheeni, namak, chai, ghee, etc.)
                       the intent is ALWAYS grocery_order — even if the sentence starts
                       with "haan", "yes", "bilkul", "theek hai", or any affirmation.
                       Examples: "haan dal bhi chahiye"    → grocery_order
                                 "हां, डाल लेना है"         → grocery_order
                                 "yes I also want milk"    → grocery_order
                                 "bilkul, rice bhi dena"   → grocery_order
                                 "theek hai aur oil chahiye" → grocery_order

- "grocery_reorder"  : repeat previous grocery order exactly

- "service_request"  : needs home service (plumber, electrician, carpenter, cleaner,
                       painter, doctor, ambulance, pharmacy, mechanic, salon, taxi)

- "service_reorder"  : rebook a previous service booking

- "finish_order"     : user is DONE adding items, wants to stop adding and proceed.
                       NO product name is mentioned.
                       Use this when user says NO MORE ITEMS in any of these forms:
                         • Simple negation alone: "नहीं", "nahi", "no"
                         • Negation + "more/aur": "नहीं और कुछ", "nahi aur kuch"
                         • Full refusal sentences:
                             "नहीं और कुछ मत माँगा"         → finish_order
                             "aur kuch nahi chahiye"        → finish_order
                             "bas ho gaya"                  → finish_order
                             "kuch nahi chahiye"            → finish_order
                             "aur kuch mat lo"              → finish_order
                             "bas itna hi"                  → finish_order
                             "bas, khatam"                  → finish_order
                             "nahi chahiye kuch aur"        → finish_order
                             "बस आ गया, हो गया"             → finish_order
                             "nothing else"                 → finish_order
                             "that's all"                   → finish_order
                             "done"                         → finish_order
                         • Checkout triggers:
                             "checkout", "place order", "order karo",
                             "order kar do", "le lo", "finalize karo"

- "confirm_order"    : user says YES to the ORDER SUMMARY that Butler just read aloud.
                       ONLY when Butler has already listed the full cart and asked
                       "order karoon?" or "lagaoon?" or "shall I place it?".
                       ONLY when no product name follows the affirmation.
                       Triggers: हाँ, yes, ok, theek hai, bilkul, haan, kar do,
                                 pakka, laga do, haan order karo, haan kar do
                       NEGATIVE EXAMPLES (these are NOT confirm_order):
                         "हाँ, चावल भी लेना है"  → grocery_order  (product present)
                         "हाँ ऑर्डर करो"         → confirm_order  (no product, affirm order)

- "cancel_order"     : user wants to cancel the ENTIRE PLACED ORDER
                       (cancel, रद्द, stop, mat karo, band karo, order cancel karo)
                       NOT used for "no more items" — that is finish_order.

- "navigate_back"    : go back / main menu

- "unknown"          : genuinely unclear, no product and no clear intent

CRITICAL DISAMBIGUATION RULES:
1. "affirmation + [product name]"            → grocery_order   (NEVER confirm_order)
2. "affirmation alone" (after cart summary)  → confirm_order
3. "negation + [no product name]"            → finish_order    (NEVER cancel_order)
4. "cancel / रद्द / cancel order"            → cancel_order
5. When in doubt between grocery_order and confirm_order → choose grocery_order
6. Short filler words (haan, ok, theek) before a product = grocery_order
7. "बस आ गया", "ho gaya", "bas itna" without product = finish_order

WORKED EXAMPLES — MEMORIZE THESE:
User said                            │ intent
─────────────────────────────────────┼────────────────────
"हां, डाल लेना है"                  │ grocery_order  [dal]
"haan dal bhi chahiye"               │ grocery_order  [dal]
"theek hai rice dena"                │ grocery_order  [rice]
"yes milk bhi chahiye"               │ grocery_order  [milk]
"नहीं और कुछ मत माँगा"               │ finish_order
"aur kuch nahi chahiye"              │ finish_order
"bas ho gaya"                        │ finish_order
"बस आ गया, हो गया"                   │ finish_order
"kuch nahi chahiye"                  │ finish_order
"nothing else"                       │ finish_order
"done"                               │ finish_order
"हाँ"  (alone, after cart read)      │ confirm_order
"हाँ ऑर्डर करो"                      │ confirm_order
"yes" (alone, after cart summary)    │ confirm_order
"cancel karo"                        │ cancel_order
"order cancel karo"                  │ cancel_order
"plumber chahiye"                    │ service_request [plumber]
"bas karo"                           │ finish_order
"order kar do"                       │ finish_order

Response schema — always this exact shape:
{
  "intent": "<from list above>",
  "confidence": <0.0–1.0>,
  "language": "<hi|en|mr|te>",
  "items": [{"name": "<english>", "quantity": <int>, "unit": "<kg|g|L|piece|null>"}],
  "service_category": "<plumber|electrician|carpenter|cleaner|painter|mechanic|doctor|ambulance|pharmacy|null>",
  "time_preference": "<now|today|tomorrow|morning|evening|null>"
}

Rules:
- items[] only for grocery_order/grocery_reorder — empty array otherwise
- service_category only for service_request/service_reorder — null otherwise
- confidence below 0.5 means genuinely unclear
""".trimIndent()

    suspend fun parse(transcript: String): FullParsedIntent = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) return@withContext fallback(transcript)

        parseMutex.withLock {
            if (transcript == lastParsedTranscript) {
                Log.w(TAG, "Duplicate transcript — skipping parse: '$transcript'")
                return@withLock fallback(transcript)
            }
            lastParsedTranscript = transcript
        }

        Log.d(TAG, "Parsing: '$transcript'")

        // ── Fast-path local classifier ──────────────────────────────────────
        // Catches the most common finish_order patterns without an API round-trip.
        // If matched, we still use confidence=0.95 and skip OpenAI entirely.
        // This eliminates ~3s latency for the "no more items" case which is the
        // most time-sensitive moment in the ordering flow.
        val localFinish = localFinishOrderCheck(transcript)
        if (localFinish != null) {
            Log.d(TAG, "Fast-path finish_order: '$transcript'")
            return@withContext localFinish
        }

        try {
            val reqBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("temperature", 0.1)
                put("max_tokens", 250)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
                    put(JSONObject().apply { put("role", "user");   put("content", transcript) })
                })
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .post(reqBody)
                .build()

            val response = http.newCall(request).execute()
            Log.d(TAG, "OpenAI code: ${response.code}")
            if (!response.isSuccessful) return@withContext fallback(transcript)

            val content = JSONObject(response.body!!.string())
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
                .replace("```json", "").replace("```", "").trim()
            Log.d(TAG, "Content: $content")
            buildResult(content, transcript)

        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            fallback(transcript)
        }
    }

    // ── Local fast-path for finish_order ─────────────────────────────────────
    // These patterns cover ~90% of real "done ordering" utterances from Indian
    // users. Matching is case-insensitive and script-agnostic.
    // IMPORTANT: only match if NO grocery keyword is present — prevents false
    // positives on "nahi, rice chahiye" type corrections.
    private val GROCERY_KEYWORDS = setOf(
        "rice", "dal", "daal", "chawal", "oil", "milk", "doodh", "atta", "cheeni",
        "namak", "chai", "ghee", "sugar", "flour", "sabzi", "aloo", "onion", "pyaaz",
        "tomato", "tamatar", "chips", "biscuit", "soap", "shampoo", "masala",
        "arhar", "moong", "masoor", "urad", "toor", "chana"
    )

    private val FINISH_ORDER_PATTERNS = listOf(
        // Devanagari patterns
        Regex("नहीं.{0,20}कुछ.{0,10}(मत|नहीं|और नहीं)", RegexOption.IGNORE_CASE),
        Regex("बस.{0,15}(हो गया|आ गया|कर|ठीक|itna)", RegexOption.IGNORE_CASE),
        Regex("कुछ नहीं चाहिए"),
        Regex("और कुछ नहीं"),
        Regex("बस इतना"),
        // Hinglish / Roman patterns
        Regex("\\bkuch\\b.{0,15}\\b(nahi|mat|nhi)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bnahi\\b.{0,20}\\b(kuch|aur)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bbas\\b.{0,20}\\b(ho gaya|hogaya|itna|khatam|kar|done)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(that'?s all|nothing else|done|finish|khatam|bas karo)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(checkout|place order|order kar do|order karo|finalize)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bho gaya\\b.{0,10}\\bbas\\b", RegexOption.IGNORE_CASE),
        Regex("\\baur kuch (mat|nahi|nhi)\\b", RegexOption.IGNORE_CASE),
    )

    private fun localFinishOrderCheck(transcript: String): FullParsedIntent? {
        val lower = transcript.lowercase()
        // If any grocery keyword is present, don't fast-path — let OpenAI decide
        if (GROCERY_KEYWORDS.any { lower.contains(it) }) return null
        val matched = FINISH_ORDER_PATTERNS.any { it.containsMatchIn(transcript) }
        if (!matched) return null
        return FullParsedIntent(
            routing        = IntentRouting.FinishOrder,
            language       = LanguageDetector.detect(transcript),
            confidence     = 0.95,
            serviceCategory = null,
            timePreference = null,
            rawText        = transcript
        )
    }

    fun resetDebounce() {
        lastParsedTranscript = ""
    }

    private fun buildResult(content: String, original: String): FullParsedIntent {
        val json            = JSONObject(content)
        val intentStr       = json.optString("intent", "unknown")
        val confidence      = json.optDouble("confidence", 0.5)
        val language        = json.optString("language", "en")
        val serviceCategory = json.optString("service_category")
            .takeIf { it.isNotBlank() && it != "null" }
        val timePreference  = json.optString("time_preference")
            .takeIf { it.isNotBlank() && it != "null" }
        val arr   = json.optJSONArray("items") ?: org.json.JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val o    = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ParsedItem(
                name     = ItemNormalizer.normalize(name),
                quantity = o.optInt("quantity", 1),
                unit     = o.optString("unit").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        Log.d(TAG, "Parsed: ${items.size} items, intent=$intentStr, confidence=$confidence, service=$serviceCategory")

        val routing = when {
            confidence < 0.3 -> IntentRouting.ShowMenu
            confidence < 0.5 -> IntentRouting.AskClarify
            else -> when (intentStr) {
                "grocery_order", "grocery_reorder"    -> IntentRouting.GoToGrocery(items)
                "service_request", "service_reorder"  -> IntentRouting.GoToService(serviceCategory ?: "general", timePreference)
                "finish_order"                        -> IntentRouting.FinishOrder
                "confirm_order"                       -> IntentRouting.ConfirmOrder
                "cancel_order"                        -> IntentRouting.CancelOrder
                else                                  -> IntentRouting.Unknown
            }
        }
        return FullParsedIntent(routing, language, confidence, serviceCategory, timePreference, original)
    }

    private fun fallback(t: String) = FullParsedIntent(
        IntentRouting.AskClarify, LanguageDetector.detect(t), 0.0, null, null, t
    )
}