package com.demo.butler_voice_app.ai

import okhttp3.*
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAIClient {

    private val client = OkHttpClient()

    fun parseOrder(text: String, apiKey: String, onResult: (AIOrderResponse?) -> Unit) {

<<<<<<< HEAD
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
=======
    val prompt = """
        You are a grocery order parser.

        Extract all grocery items from the sentence.

        Return ONLY valid JSON. No explanation. No text.

        Format:
        {
        "items": [
            {
            "name": "rice",
            "quantity": 2,
            "unit": "kg"
            }
        ]
        }

        Rules:
        - Always return "items"
        - If quantity not mentioned → use 1
        - If unit not mentioned → use null
        - Support multiple items

        User: "$text"
        """

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
            onResult(AIOrderResponse(emptyList()))
        }

        override fun onResponse(call: Call, response: Response) {

            val result = response.body?.string() ?: return onResult(AIOrderResponse(emptyList()))

            try {
                val content = JSONObject(result)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                println("AI RAW RESPONSE: $content")

                val cleaned = content
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val json = JSONObject(cleaned)
                val itemsArray = json.getJSONArray("items")

                val items = mutableListOf<AIItem>()

                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)

                    items.add(
                        AIItem(
                            name = obj.getString("name"),
                            quantity = obj.optInt("quantity", 1),
                            unit = if (obj.has("unit")) obj.getString("unit") else null
                        )
                    )
                }

                onResult(AIOrderResponse(items))

            } catch (e: Exception) {
                onResult(AIOrderResponse(emptyList()))
            }
        }
    })
}
>>>>>>> 0ba2e64 (AI parsing + multi-item voice ordering)
}