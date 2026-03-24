package com.demo.butler_voice_app.ai

import android.util.Log
import com.demo.butler_voice_app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object TranslationManager {

    private val client = OkHttpClient()

    suspend fun translate(
        text: String,
        targetLang: String
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                if (targetLang == "en") return@withContext text

                val prompt = """
Translate the following text into $targetLang.

Rules:
- Keep it natural and conversational
- Do not add explanations
- Return ONLY translated text

Text:
"$text"
""".trimIndent()

                val body = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", listOf(
                        mapOf("role" to "user", "content" to prompt)
                    ))
                    put("max_tokens", 100)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext text

                if (!response.isSuccessful) {
                    Log.e("Translate", "Error: $responseBody")
                    return@withContext text
                }

                val translated = JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                translated

            } catch (e: Exception) {
                Log.e("Translate", "Exception: ${e.message}")
                text
            }
        }
    }
}
