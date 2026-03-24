package com.demo.butler_voice_app.ai

object LanguageDetector {

    fun detect(text: String): String {
        return when {
            Regex("[\u0900-\u097F]").containsMatchIn(text) -> "hi" // Hindi
            Regex("[\u0C00-\u0C7F]").containsMatchIn(text) -> "te" // Telugu
            Regex("[\u0B80-\u0BFF]").containsMatchIn(text) -> "ta" // Tamil
            Regex("[\u0D00-\u0D7F]").containsMatchIn(text) -> "ml" // Malayalam
            else -> "en"
        }
    }
}
