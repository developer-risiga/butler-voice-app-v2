package com.demo.butler_voice_app.ai

import android.util.Log

object SessionLanguageManager {
    private const val TAG = "SessionLang"
    private const val CONSECUTIVE_NEEDED = 2

    var lockedLanguage: String? = null
        private set

    val ttsLanguage: String get() = lockedLanguage?.substringBefore("-") ?: "en"
    val sarvamHint:  String? get() = lockedLanguage

    private var pendingLanguage: String? = null
    private var consecutiveCount = 0

    fun onDetection(languageCode: String) {
        when {
            lockedLanguage == null -> {
                lockedLanguage = languageCode
                Log.d(TAG, "Locked: $languageCode")
            }
            languageCode == lockedLanguage -> {
                pendingLanguage = null; consecutiveCount = 0
            }
            languageCode == pendingLanguage -> {
                consecutiveCount++
                if (consecutiveCount >= CONSECUTIVE_NEEDED) {
                    Log.d(TAG, "Switched: $lockedLanguage → $languageCode")
                    lockedLanguage = languageCode; pendingLanguage = null; consecutiveCount = 0
                }
            }
            else -> { pendingLanguage = languageCode; consecutiveCount = 1 }
        }
    }

    fun reset() { lockedLanguage = null; pendingLanguage = null; consecutiveCount = 0 }
}