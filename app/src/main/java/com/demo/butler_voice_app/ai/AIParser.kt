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
- "finish_order"     : user is DONE ordering, wants to checkout/confirm cart summary.
                       ONLY when NO product name is mentioned.
                       Triggers: नहीं, बस, khatam, done, that's all, nothing else,
                       ho gaya, bas karo, checkout, place order, order karo, order kar do
- "confirm_order"    : user says YES to the ORDER SUMMARY that Butler just read aloud.
                       ONLY when Butler has already listed the full cart and asked
                       "lagaoon?" or "shall I place it?".
                       Triggers (ONLY when no product name follows):
                       हाँ, yes, ok, theek hai, bilkul, haan, kar do, pakka, laga do
- "cancel_order"     : user wants to cancel (cancel, nahi, रद्द, stop, mat karo, band karo)
- "navigate_back"    : go back / main menu
- "unknown"          : genuinely unclear, no product and no clear intent

CRITICAL DISAMBIGUATION RULES:
1. "affirmation + [product name]"  = grocery_order   (NEVER confirm_order)
2. "affirmation alone"             = confirm_order    (when no product follows)
3. When in doubt between grocery_order and confirm_order → choose grocery_order
4. Short filler words (haan, ok, theek) before a product name do NOT make it a confirmation

EXAMPLES TO LEARN FROM:
- "हां, डाल लेना है"           → grocery_order,  items=[dal]
- "haan dal bhi chahiye"      → grocery_order,  items=[dal]
- "theek hai rice dena"       → grocery_order,  items=[rice]
- "yes milk bhi chahiye"      → grocery_order,  items=[milk]
- "हाँ"  (alone, after cart read) → confirm_order, items=[]
- "yes" (alone, after cart read)  → confirm_order, items=[]
- "bas karo"                  → finish_order,   items=[]
- "order kar do"              → finish_order,   items=[]
- "nahi chahiye"              → cancel_order,   items=[]
- "plumber chahiye"           → service_request, service_category=plumber

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