package com.demo.butler_voice_app.ai

import android.util.Log

/**
 * Tracks language across a session.
 *
 * Key behaviour:
 * - Scripts like Devanagari (hi), Telugu (te), Tamil (ta), Malayalam (ml), Kannada (kn)
 *   are detected with certainty from the Unicode range → switch immediately (threshold = 1).
 * - Roman-script ambiguity (e.g. Hinglish vs English) → require 2 consecutive hits before
 *   switching, to avoid false flips on code-mixed sentences.
 * - Once switched, the TTS voice must also be updated. Call ttsVoiceId after onDetection.
 */
object SessionLanguageManager {

    private const val TAG = "SessionLang"

    // How many consecutive different detections needed before switching — for roman script
    private const val ROMAN_CONSECUTIVE_NEEDED = 2

    // Currently confirmed session language (BCP-47 e.g. "hi-IN", "en-IN")
    var lockedLanguage: String? = null
        private set

    // Convenience: base language code e.g. "hi", "en", "te"
    val ttsLanguage: String get() = lockedLanguage?.substringBefore("-") ?: "en"

    // ElevenLabs voice ID for the current locked language
    val ttsVoiceId: String get() = voiceFor(lockedLanguage ?: "en-IN")

    // Optional: pass to Sarvam as language hint for faster / more accurate STT
    val sarvamHint: String? get() = lockedLanguage

    private var pendingLanguage: String? = null
    private var consecutiveCount = 0

    // ── Called after every Sarvam STT response ──────────────────────────────

    /**
     * @param languageCode  BCP-47 code returned by Sarvam e.g. "hi-IN", "en-IN"
     * @return true if the locked language changed (caller should update TTS voice)
     */
    fun onDetection(languageCode: String): Boolean {
        val current = lockedLanguage

        // First detection — lock immediately regardless of script
        if (current == null) {
            lockedLanguage = languageCode
            pendingLanguage = null
            consecutiveCount = 0
            Log.d(TAG, "Locked: $languageCode")
            return true
        }

        // Same as current — no change, reset pending
        if (languageCode == current) {
            pendingLanguage = null
            consecutiveCount = 0
            return false
        }

        // Different language detected — decide threshold based on script
        val threshold = if (isNonRomanScript(languageCode)) 1 else ROMAN_CONSECUTIVE_NEEDED

        when {
            languageCode == pendingLanguage -> {
                consecutiveCount++
                if (consecutiveCount >= threshold) {
                    Log.d(TAG, "Language switched: $current → $languageCode (after $consecutiveCount hits)")
                    lockedLanguage = languageCode
                    pendingLanguage = null
                    consecutiveCount = 0
                    return true
                }
            }
            else -> {
                pendingLanguage = languageCode
                consecutiveCount = 1
                // Non-roman script detected once → switch immediately
                if (threshold == 1) {
                    Log.d(TAG, "Language switched (script-based): $current → $languageCode")
                    lockedLanguage = languageCode
                    pendingLanguage = null
                    consecutiveCount = 0
                    return true
                }
            }
        }
        return false
    }

    fun reset() {
        lockedLanguage = null
        pendingLanguage = null
        consecutiveCount = 0
        Log.d(TAG, "Reset")
    }

    // ── Script detection ─────────────────────────────────────────────────────

    /**
     * Languages where even a single detection is reliable enough to switch immediately,
     * because their Unicode script range can't be confused with another language.
     */
    private fun isNonRomanScript(code: String): Boolean = when {
        code.startsWith("hi") -> true   // Devanagari
        code.startsWith("te") -> true   // Telugu
        code.startsWith("ta") -> true   // Tamil
        code.startsWith("ml") -> true   // Malayalam
        code.startsWith("kn") -> true   // Kannada
        code.startsWith("mr") -> true   // Marathi (also Devanagari)
        code.startsWith("pa") -> true   // Punjabi (Gurmukhi)
        code.startsWith("gu") -> true   // Gujarati
        code.startsWith("bn") -> true   // Bengali
        else -> false
    }

    // ── ElevenLabs voice mapping ─────────────────────────────────────────────

    fun voiceFor(langCode: String): String = when {
        langCode.startsWith("hi") -> "pqHfZKP75CvOlQylNhV4"   // Hindi
        langCode.startsWith("te") -> "YOUR_TELUGU_VOICE_ID"
        langCode.startsWith("ta") -> "YOUR_TAMIL_VOICE_ID"
        langCode.startsWith("ml") -> "YOUR_MALAYALAM_VOICE_ID"
        langCode.startsWith("kn") -> "YOUR_KANNADA_VOICE_ID"
        langCode.startsWith("mr") -> "pqHfZKP75CvOlQylNhV4"   // Marathi uses Hindi voice
        else                      -> "RwXLkVKnRloV1UPh3Ccx"   // English default
    }
}