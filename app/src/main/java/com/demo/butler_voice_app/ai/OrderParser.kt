package com.demo.butler_voice_app.ai

data class Order(
    val product: String,
    val quantity: Int
)

class OrderParser {

    fun parse(text: String): Order? {

        val lower = text.lowercase()

        return when {
            lower.contains("rice") -> Order("rice", 1)
            lower.contains("oil") -> Order("oil", 1)
            lower.contains("sugar") -> Order("sugar", 1)
            lower.contains("salt") -> Order("salt", 1)
            else -> null
        }
    }
}