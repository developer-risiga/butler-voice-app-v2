package com.demo.butler_voice_app.ai

import android.util.Log
import com.demo.butler_voice_app.BuildConfig
import com.demo.butler_voice_app.api.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ParsedItem(
    val name: String,
    val quantity: Int = 1,
    val unit: String? = null
)

data class ParsedOrder(
    val items: List<ParsedItem>,
    val detectedLanguage: String = "en",
    val intent: String = "order"
)

object AIOrderParser {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun parse(text: String): ParsedOrder {
        return withContext(Dispatchers.IO) {
            try {
                val keyLength = BuildConfig.OPENAI_API_KEY.length
                if (keyLength < 20 || BuildConfig.OPENAI_API_KEY == "DUMMY_KEY") {
                    Log.e("AIParser", "OpenAI key missing")
                    return@withContext ParsedOrder(emptyList())
                }

                val detectedLang = LanguageDetector.detect(text)
                val context      = UserSessionManager.buildPersonalizationContext()

                val prompt = """
You are an AI assistant for a multilingual Indian grocery voice ordering system.

User said: "$text"
Detected language: $detectedLang
Customer context: $context

IDENTIFY the INTENT — pick exactly one:
- "order"         → user wants to add/order grocery items (e.g. "I want rice", "मुझे चावल चाहिए", "rice lena hai")
- "add_more"      → user wants to add more items to existing cart
- "finish_order"  → user is DONE and wants to checkout/confirm
                    Hindi triggers: नहीं, बस, कुछ नहीं, नहीं चाहिए, नहीं चाहूंगा, और कुछ नहीं, aur nahi, kuch nahi, bas, khatam
                    English triggers: no, done, nothing, that's all, finish, stop, nothing else
- "confirm_order" → user CONFIRMS placing the order
                    Hindi triggers: हाँ, हां, ऑर्डर कर दो, kar do, haan, theek hai, bilkul
                    English triggers: yes, ok, confirm, place it, proceed, sure
- "cancel_order"  → user CANCELS (cancel, no, nahi mat karo, ruk jao)
- "history"       → user asks about past orders
- "unknown"       → completely unclear input

RULES:
1. Only extract items[] if intent is "order" or "add_more"
2. For all other intents, return items as empty array []
3. Normalize all item names to English (rice, oil, sugar, dal, milk, atta, etc.)
4. Default quantity = 1
5. Default unit: kg for staples (rice, sugar, wheat, dal), litre for oil/milk, piece for eggs/bread
6. Return ONLY valid JSON — no markdown, no explanation, no preamble

RESPONSE FORMAT:
{
  "language": "$detectedLang",
  "intent": "order",
  "items": [
    {"name": "rice", "quantity": 2, "unit": "kg"}
  ]
}
""".trimIndent()

                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }

                val reqBody = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", messagesArray)
                    put("max_tokens", 300)
                    put("temperature", 0.1)
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(reqBody)
                    .build()

                val res     = http.newCall(req).execute()
                val resBody = res.body?.string() ?: return@withContext ParsedOrder(emptyList())

                Log.d("AIParser", "OpenAI code: ${res.code}")

                if (!res.isSuccessful) {
                    Log.e("AIParser", "OpenAI error ${res.code}: $resBody")
                    return@withContext ParsedOrder(emptyList())
                }

                val rawContent = JSONObject(resBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                val cleanedContent = rawContent
                    .replace("```json", "")
                    .replace("```", "")
                    .replace("\n", " ")
                    .trim()

                Log.d("AIParser", "Content: $cleanedContent")

                val json = try {
                    JSONObject(cleanedContent)
                } catch (e: Exception) {
                    Log.e("AIParser", "Invalid JSON: $cleanedContent")
                    return@withContext ParsedOrder(emptyList(), detectedLang)
                }

                val language = json.optString("language", detectedLang)
                val intent   = json.optString("intent", "order")
                val arr      = json.optJSONArray("items") ?: org.json.JSONArray()
                val items    = mutableListOf<ParsedItem>()

                for (i in 0 until arr.length()) {
                    val obj     = arr.getJSONObject(i)
                    val rawName = obj.optString("name", "")
                    if (rawName.isBlank()) continue
                    items.add(
                        ParsedItem(
                            name     = ItemNormalizer.normalize(rawName),
                            quantity = obj.optInt("quantity", 1),
                            unit     = obj.optString("unit").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }

                Log.d("AIParser", "Parsed: ${items.size} items, intent=$intent, lang=$language")
                ParsedOrder(items, language, intent)

            } catch (e: Exception) {
                Log.e("AIParser", "Exception ${e.javaClass.simpleName}: ${e.message}")
                ParsedOrder(emptyList())
            }
        }
    }
}
