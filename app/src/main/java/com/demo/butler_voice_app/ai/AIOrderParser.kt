package com.demo.butler_voice_app.ai

import com.demo.butler_voice_app.BuildConfig
import com.demo.butler_voice_app.api.UserSessionManager

class AIOrderParser {
    private val client = OpenAIClient()

    fun parse(text: String, onResult: (AIOrderResponse?) -> Unit) {
        // Build personalized prompt with user history
        val personalizationContext = UserSessionManager.buildPersonalizationContext()
        val enhancedText = if (personalizationContext.isNotBlank()) {
            "$text\n\nContext: $personalizationContext"
        } else {
            text
        }
        client.parseOrder(enhancedText, BuildConfig.OPENAI_API_KEY, onResult)
    }
}
