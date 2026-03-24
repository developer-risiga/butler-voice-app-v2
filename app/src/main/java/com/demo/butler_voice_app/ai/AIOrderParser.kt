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

data class ParsedOrder(
    val items: List<ParsedItem>,
    val detectedLanguage: String = "en"
)

data class ParsedItem(
    val name: String,
    val quantity: Int = 1,
    val unit: String? = null
)

object AIOrderParser {

    private val http = OkHttpClient()

    suspend fun parse(text: String): ParsedOrder {
        return withContext(Dispatchers.IO) {
            try {
                val context = UserSessionManager.buildPersonalizationContext()
                val prompt = """
You are a grocery ordering assistant for an Indian kiosk.
Extract ALL grocery items from the user's message.
Detect the language (en/hi/te).

User context: $context

User said: "$text"

Return ONLY valid JSON:
{
  "language": "en",
  "items": [
    {"name": "rice", "quantity": 2, "unit": "kg"},
    {"name": "oil", "quantity": 1, "unit": "L"}
  ]
}

Rules:
- Extract ALL items mentioned in one message
- If quantity not mentioned use 1
- If unit not mentioned use null  
- Normalize item names to English always
- Support: kg, g, L, ml, packet, piece
- language: en/hi/te based on input language
""".trimIndent()

                val body = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", listOf(
                        mapOf("role" to "user", "content" to prompt)
                    ))
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val res = http.newCall(req).execute()
                val resBody = res.body?.string() ?: return@withContext ParsedOrder(emptyList())

                val content = JSONObject(resBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .replace("```json", "").replace("```", "").trim()

                val json = JSONObject(content)
                val language = json.optString("language", "en")
                val itemsArr = json.getJSONArray("items")
                val items = mutableListOf<ParsedItem>()

                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.getJSONObject(i)
                    items.add(ParsedItem(
                        name     = obj.getString("name"),
                        quantity = obj.optInt("quantity", 1),
                        unit     = if (obj.has("unit") && !obj.isNull("unit"))
                            obj.getString("unit") else null
                    ))
                }

                Log.d("AIParser", "Parsed ${items.size} items, lang=$language")
                ParsedOrder(items, language)

            } catch (e: Exception) {
                Log.e("AIParser", "Parse failed: ${e.message}")
                ParsedOrder(emptyList())
            }
        }
    }

    // Generate response in user's language
    suspend fun generateResponse(
        prompt: String,
        language: String = "en"
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val langInstruction = when (language) {
                    "hi" -> "Respond in Hindi (Devanagari script)"
                    "te" -> "Respond in Telugu script"
                    else -> "Respond in English"
                }

                val body = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", listOf(
                        mapOf("role" to "system", "content" to "$langInstruction. Be brief and friendly."),
                        mapOf("role" to "user", "content" to prompt)
                    ))
                    put("max_tokens", 100)
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val res = http.newCall(req).execute()
                val resBody = res.body?.string() ?: return@withContext ""

                JSONObject(resBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

            } catch (e: Exception) {
                Log.e("AIParser", "Response generation failed: ${e.message}")
                ""
            }
        }
    }
}
