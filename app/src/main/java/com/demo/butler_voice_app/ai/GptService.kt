package com.demo.butler_voice_app.ai
import okhttp3.MediaType.Companion.toMediaTypeOrNull

import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class GptService {

    private val client = OkHttpClient()

    private val API_KEY = "YOUR_OPENAI_API_KEY"

    fun parseOrder(text: String, callback: (Order?) -> Unit) {

        val prompt = """
            Extract grocery order from this sentence.
            
            Sentence: "$text"
            
            Return JSON:
            {
              "product": "",
              "quantity": number
            }
        """.trimIndent()

        val json = JSONObject()
        json.put("model", "gpt-4o-mini")
        json.put("messages", listOf(
            mapOf("role" to "user", "content" to prompt)
        ))

        val body = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $API_KEY")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {

                val res = response.body?.string() ?: ""
                val json = JSONObject(res)

                val content = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                try {
                    val parsed = JSONObject(content)

                    val product = parsed.getString("product")
                    val quantity = parsed.getInt("quantity")

                    callback(Order(product, quantity))

                } catch (e: Exception) {
                    callback(null)
                }
            }
        })
    }
}