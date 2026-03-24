package com.demo.butler_voice_app.ai

object LanguageManager {

    private var sessionLanguage: String = "en"

    fun setLanguage(lang: String) {
        if (sessionLanguage == "en") {
            sessionLanguage = lang
        }
    }

    fun getLanguage(): String {
        return sessionLanguage
    }

    fun reset() {
        sessionLanguage = "en"
    }
}
