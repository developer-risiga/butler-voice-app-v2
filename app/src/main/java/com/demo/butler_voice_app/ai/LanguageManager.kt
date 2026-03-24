package com.demo.butler_voice_app.ai

object LanguageManager {

    private var sessionLanguage: String = "en"

    fun setLanguage(lang: String) {
        // Always update (no restriction)
        sessionLanguage = lang
    }

    fun getLanguage(): String {
        return sessionLanguage
    }

    fun reset() {
        sessionLanguage = "en"
    }
}
