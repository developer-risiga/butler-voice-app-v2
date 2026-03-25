package com.demo.butler_voice_app.ai

object LanguageDetector {

    fun detect(text: String): String {
        if (text.isBlank()) return "en"

        val hindiCount   = Regex("[\u0900-\u097F]").findAll(text).count()
        val teluguCount  = Regex("[\u0C00-\u0C7F]").findAll(text).count()
        val tamilCount   = Regex("[\u0B80-\u0BFF]").findAll(text).count()
        val malayCount   = Regex("[\u0D00-\u0D7F]").findAll(text).count()
        val punjabiCount = Regex("[\u0A00-\u0A7F]").findAll(text).count()
        val totalNonEn   = hindiCount + teluguCount + tamilCount + malayCount + punjabiCount

        // Only switch language if clearly non-English (>2 non-Latin chars)
        if (totalNonEn < 3) return "en"

        return when (maxOf(hindiCount, teluguCount, tamilCount, malayCount, punjabiCount)) {
            hindiCount  -> "hi"
            teluguCount -> "te"
            tamilCount  -> "ta"
            malayCount  -> "ml"
             punjabiCount -> "pa"
            else        -> "en"
        }
    }
}
