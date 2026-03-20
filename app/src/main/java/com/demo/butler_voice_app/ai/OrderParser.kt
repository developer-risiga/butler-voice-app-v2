package com.demo.butler_voice_app.ai

class OrderParser {

    private val numberWords = mapOf(
        "a" to 1, "an" to 1, "one" to 1,
        "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    private val stopWords = setOf(
        "i", "want", "need", "get", "order", "give", "me",
        "please", "can", "you", "the", "some", "of"
    )

    fun parse(text: String): Order? {
        val words = text.lowercase().trim().split("\\s+".toRegex())
        var quantity = 1
        val productWords = mutableListOf<String>()

        for (word in words) {
            val num = numberWords[word] ?: word.toIntOrNull()
            if (num != null) {
                quantity = num
            } else if (word !in stopWords) {
                productWords.add(word)
            }
        }

        if (productWords.isEmpty()) return null
        return Order(productWords.joinToString(" "), quantity)
    }
}
