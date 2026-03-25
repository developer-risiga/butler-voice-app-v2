package com.demo.butler_voice_app.ai

object LanguageManager {

    private var sessionLanguage: String = "en"

    /**
     * Only switch if we're confident (non-English script detected).
     * Never downgrade back to English mid-session.
     */
    fun setLanguage(lang: String) {
        if (lang != "en") {
            sessionLanguage = lang
        }
        // If lang == "en" and sessionLanguage is already hi/te/ta, keep it
        // This prevents Hinglish/"Prince at the rate" flipping back to English
    }

    fun forceSet(lang: String) {
        sessionLanguage = lang
    }

    fun getLanguage(): String = sessionLanguage

    fun reset() {
        sessionLanguage = "en"
    }
}
