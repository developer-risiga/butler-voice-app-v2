package com.demo.butler_voice_app.ai

data class AIOrderResponse(
    val items: List<AIItem>
)

data class AIItem(
    val name: String,
    val quantity: Int,
    val unit: String? = null
)