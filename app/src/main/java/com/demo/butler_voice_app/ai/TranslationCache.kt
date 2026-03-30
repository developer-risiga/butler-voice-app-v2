package com.demo.butler_voice_app.ai

import android.util.LruCache
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

// ══════════════════════════════════════════════════════════════════════════════
// TranslationCache
//
// FIX: "Finding Plumber आपके पास" — English service names leaking into Hindi
// CAUSE: Strings like "Finding Plumber near you." sent to Translate API,
//        but "Plumber" is never translated → Hindi voice mispronounces it
// FIX:  Pre-translated static strings cover every Butler phrase including
//       service category names fully in Hindi — zero API calls for these
// ══════════════════════════════════════════════════════════════════════════════

object TranslationCache {

    private const val TAG = "TranslationCache"

    // Runtime LRU for dynamic content (names, prices, distances)
    private val lru = LruCache<String, String>(300)

    // ── Pre-translated strings ─────────────────────────────────────────────
    // Key format: "english text|langCode"
    // Covers every Butler phrase so Translate API is never called for these
    private val static_strings = mapOf(

        // ── Common responses ──────────────────────────────────────────────
        "Sorry, I didn't catch that. Please speak clearly.|hi" to "माफ करें, मैं समझ नहीं पाया। कृपया साफ बोलें।",
        "Say camera to take a photo, or upload to choose from gallery.|hi" to "कैमरा बोलें तस्वीर लेने के लिए, या अपलोड बोलें गैलरी से चुनने के लिए।",
        "Say yes to confirm or no to go back.|hi" to "हाँ बोलें पुष्टि करने के लिए, या नहीं बोलें वापस जाने के लिए।",
        "Please say 1, 2, or 3 to choose.|hi" to "कृपया 1, 2 या 3 बोलें चुनाव करने के लिए।",
        "Booking confirmed. Thank you!|hi" to "बुकिंग हो गई। धन्यवाद!",
        "Tell me what you want to order.|hi" to "मुझे बताओ तुम क्या ऑर्डर करना चाहते हो।",
        "What would you like?|hi" to "आप क्या चाहते हैं?",
        "Is there anything else you need?|hi" to "क्या आपको और कुछ चाहिए?",

        // ── Service sector discovery ───────────────────────────────────────
        // FIX: Full Hindi — no English category name leaking through
        "Finding Plumber providers near you.|hi" to "आपके पास प्लंबर ढूंढ रहे हैं।",
        "Finding Electrician providers near you.|hi" to "आपके पास इलेक्ट्रीशियन ढूंढ रहे हैं।",
        "Finding Doctor providers near you.|hi" to "आपके पास डॉक्टर ढूंढ रहे हैं।",
        "Finding Medicine & Pharmacy providers near you.|hi" to "आपके पास दवाई की दुकान ढूंढ रहे हैं।",
        "Finding Cleaning providers near you.|hi" to "आपके पास सफाई सेवा ढूंढ रहे हैं।",
        "Finding Carpenter providers near you.|hi" to "आपके पास बढ़ई ढूंढ रहे हैं।",
        "Finding AC Repair providers near you.|hi" to "आपके पास एसी मरम्मत ढूंढ रहे हैं।",
        "Finding Salon providers near you.|hi" to "आपके पास सैलून ढूंढ रहे हैं।",
        "Finding Pest Control providers near you.|hi" to "आपके पास कीट नियंत्रण ढूंढ रहे हैं।",
        "Finding Taxi providers near you.|hi" to "आपके पास टैक्सी ढूंढ रहे हैं।",
        "Finding Food providers near you.|hi" to "आपके पास खाना ढूंढ रहे हैं।",
        "Finding service providers near you.|hi" to "आपके पास सेवा प्रदाता ढूंढ रहे हैं।",

        // ── Prescription flow ──────────────────────────────────────────────
        "I will help you find a pharmacy for your medicines. Please say camera to take a photo of your prescription, or say upload to choose an image from your gallery.|hi"
                to "मैं आपकी दवाइयों के लिए फार्मेसी ढूंढूंगा। कृपया कैमरा बोलें पर्चे की फोटो लेने के लिए, या अपलोड बोलें गैलरी से छवि चुनने के लिए।",
        "Reading your prescription. Please wait a moment.|hi" to "आपका पर्चा पढ़ रहे हैं। एक पल रुकिए।",
        "I had trouble reading the prescription. Please say the medicine names one by one.|hi"
                to "पर्चा पढ़ने में दिक्कत हुई। कृपया दवाइयों के नाम एक-एक करके बोलें।",
        "Please say the first medicine name.|hi" to "कृपया पहली दवाई का नाम बोलें।",
        "Please say the medicine name clearly.|hi" to "कृपया दवाई का नाम साफ बोलें।",
        "Opening gallery. Please select your prescription image or PDF.|hi"
                to "गैलरी खुल रही है। कृपया अपने पर्चे की छवि या PDF चुनें।",

        // ── Grocery ────────────────────────────────────────────────────────
        "Added to cart. More items or say done to checkout?|hi" to "कार्ट में जोड़ा। और चाहिए या checkout करने के लिए 'हो गया' बोलें?",
        "Cart is empty. What would you like?|hi" to "कार्ट खाली है। आप क्या चाहेंगे?",
        "Order cancelled.|hi" to "ऑर्डर रद्द हो गया।",
        "Say yes to place the order, or no to cancel.|hi" to "ऑर्डर देने के लिए हाँ बोलें, या रद्द करने के लिए नहीं।",

        // ── Payment ────────────────────────────────────────────────────────
        "Say card, UPI, or QR to choose payment method.|hi" to "कार्ड, UPI या QR बोलें भुगतान का तरीका चुनने के लिए।",
        "Have you completed the payment? Say yes or no.|hi" to "क्या भुगतान हो गया? हाँ या नहीं बोलें।",
        "Great! Payment confirmed. Placing your order now.|hi" to "बढ़िया! भुगतान हो गया। अभी ऑर्डर दे रहे हैं।",

        // ── Emergency ─────────────────────────────────────────────────────
        "Emergency! Connecting to emergency services right away.|hi" to "आपातकाल! तुरंत आपातकालीन सेवाओं से जोड़ रहे हैं।",

        // ── Marathi versions ───────────────────────────────────────────────
        "Sorry, I didn't catch that. Please speak clearly.|mr" to "माफ करा, मला समजले नाही। कृपया स्पष्ट बोला।",
        "Finding Plumber providers near you.|mr" to "तुमच्याजवळ प्लंबर शोधत आहोत।",
        "Finding Doctor providers near you.|mr" to "तुमच्याजवळ डॉक्टर शोधत आहोत।",
        "Booking confirmed. Thank you!|mr" to "बुकिंग झाली. धन्यवाद!",
        "Say yes to confirm or no to go back.|mr" to "पुष्टी करण्यासाठी हो बोला किंवा मागे जाण्यासाठी नाही.",

        // ── Telugu versions ────────────────────────────────────────────────
        "Sorry, I didn't catch that. Please speak clearly.|te" to "క్షమించండి, నాకు అర్థం కాలేదు. దయచేసి స్పష్టంగా మాట్లాడండి.",
        "Finding Plumber providers near you.|te" to "మీ దగ్గర ప్లంబర్ వెతుకుతున్నాము.",
        "Finding Doctor providers near you.|te" to "మీ దగ్గర డాక్టర్ వెతుకుతున్నాము.",
        "Booking confirmed. Thank you!|te" to "బుకింగ్ నిర్ధారించబడింది. ధన్యవాదాలు!",
        "What would you like?|te" to "మీరు ఏమి కావాలంటారు?"
    )

    suspend fun translate(text: String, targetLang: String): String {
        if (targetLang == "en" || targetLang.startsWith("en")) return text
        if (text.isBlank()) return text

        // 1. Static strings — instant, zero API calls
        val key = "$text|$targetLang"
        static_strings[key]?.let { return it }

        // 2. LRU runtime cache — instant
        lru.get(key)?.let { return it }

        // 3. Dynamic content only — call Sarvam Translate
        return try {
            val translated = callSarvamTranslate(text, targetLang)
            lru.put(key, translated)
            translated
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed for '$text' → $targetLang: ${e.message}")
            text   // fallback: speak original rather than crash or freeze
        }
    }

    private suspend fun callSarvamTranslate(text: String, targetLang: String): String =
        withContext(Dispatchers.IO) {
            val langCode = when {
                targetLang.startsWith("hi") -> "hi-IN"
                targetLang.startsWith("mr") -> "mr-IN"
                targetLang.startsWith("te") -> "te-IN"
                targetLang.startsWith("ta") -> "ta-IN"
                else -> "hi-IN"
            }
            val body = JSONObject().apply {
                put("input", text)
                put("source_language_code", "en-IN")
                put("target_language_code", langCode)
                put("speaker_gender", "Female")
                put("mode", "formal")
            }.toString().toRequestBody("application/json".toMediaType())

            val response = OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.sarvam.ai/translate")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("api-subscription-key", getSarvamKey())
                    .post(body)
                    .build()
            ).execute()

            val json = JSONObject(response.body?.string() ?: return@withContext text)
            json.optString("translated_text", text)
        }

    // Pull key from the same BuildConfig the rest of the app uses
    private fun getSarvamKey(): String =
        try {
            com.demo.butler_voice_app.BuildConfig.SARVAM_API_KEY
        } catch (e: Exception) { "" }
}