package com.demo.butler_voice_app.ai

import com.demo.butler_voice_app.BuildConfig
class AIOrderParser {

    private val client = OpenAIClient()

    fun parse(text: String, onResult: (AIOrderResponse?) -> Unit) {
        client.parseOrder(text, BuildConfig.OPENAI_API_KEY, onResult)
    }
}