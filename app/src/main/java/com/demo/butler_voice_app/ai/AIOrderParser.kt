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
    val detectedLanguage: String = "en"
)

object AIOrderParser {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun parse(text: String): ParsedOrder {
        return withContext(Dispatchers.IO) {
            try {
                // Log key info to diagnose the issue
                val keyLength = BuildConfig.OPENAI_API_KEY.length
                val keyStart  = BuildConfig.OPENAI_API_KEY.take(10)
                Log.d("AIParser", "Key length: $keyLength, starts: $keyStart")

                if (keyLength < 20 || BuildConfig.OPENAI_API_KEY == "DUMMY_KEY") {
                    Log.e("AIParser", "OpenAI key is missing or dummy — skipping AI parse")
                    return@withContext ParsedOrder(emptyList())
                }

                val context = UserSessionManager.buildPersonalizationContext()
                val prompt  = "Extract grocery items from: \"$text\". Context: $context. Return ONLY JSON: {\"language\":\"en\",\"items\":[{\"name\":\"rice\",\"quantity\":2,\"unit\":\"kg\"}]}"

                val reqBody = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", listOf(mapOf("role" to "user", "content" to prompt)))
                    put("max_tokens", 200)
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(reqBody)
                    .build()

                val res     = http.newCall(req).execute()
                val resBody = res.body?.string() ?: return@withContext ParsedOrder(emptyList())

                Log.d("AIParser", "OpenAI response code: ${res.code}")
                Log.d("AIParser", "OpenAI response: $resBody")

                if (!res.isSuccessful) {
                    Log.e("AIParser", "OpenAI error ${res.code}: $resBody")
                    return@withContext ParsedOrder(emptyList())
                }

                val content = JSONObject(resBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .replace("```json", "").replace("```", "").trim()

                Log.d("AIParser", "Content: $content")

                val json     = JSONObject(content)
                val language = json.optString("language", "en")
                val arr      = json.getJSONArray("items")
                val items    = mutableListOf<ParsedItem>()

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    items.add(ParsedItem(
                        name     = obj.getString("name"),
                        quantity = obj.optInt("quantity", 1),
                        unit     = if (obj.has("unit") && !obj.isNull("unit") &&
                            obj.getString("unit") != "null") obj.getString("unit") else null
                    ))
                }

                Log.d("AIParser", "Parsed: ${items.size} items, lang=$language")
                ParsedOrder(items, language)

            } catch (e: Exception) {
                Log.e("AIParser", "Exception ${e.javaClass.simpleName}: ${e.message}")
                ParsedOrder(emptyList())
            }
        }
    }
}
