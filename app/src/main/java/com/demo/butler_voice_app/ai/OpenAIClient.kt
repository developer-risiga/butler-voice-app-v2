package com.demo.butler_voice_app.ai

import okhttp3.*
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAIClient {

    private val client = OkHttpClient()

    fun parseOrder(text: String, apiKey: String, onResult: (AIOrderResponse?) -> Unit) {

        val prompt = """
            Extract grocery items from this sentence.
            Return JSON only.

            Sentence: "$text"

            Format:
            {
              "items": [
                { "name": "rice", "quantity": 2, "unit": "kg" }
              ]
            }
        """.trimIndent()

        val json = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", listOf(
                mapOf("role" to "user", "content" to prompt)
            ))
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string() ?: return onResult(null)

                try {
                    val content = JSONObject(result)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    val json = JSONObject(content)
                    val itemsArray = json.getJSONArray("items")

                    val items = mutableListOf<AIItem>()

                    for (i in 0 until itemsArray.length()) {
                        val obj = itemsArray.getJSONObject(i)
                        items.add(
                            AIItem(
                                name = obj.getString("name"),
                                quantity = obj.optInt("quantity", 1),
                                unit = obj.optString("unit", null)
                            )
                        )
                    }

                    onResult(AIOrderResponse(items))

                } catch (e: Exception) {
                    onResult(null)
                }
            }
        })
    }
}