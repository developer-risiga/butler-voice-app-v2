package com.demo.butler_voice_app.ai

import android.util.Log

/**
 * Manages language detection stability across a session.
 *
 * PROBLEM this class solves:
 * Sarvam STT frequently mis-identifies short Hindi utterances as Odia (od-IN),
 * Kannada (kn-IN), or Bengali (bn-IN). A single word like "हाँ" (yes) can be
 * returned as "ହଁ" (od-IN). This caused Butler to switch languages mid-session.
 *
 * SOLUTION — different thresholds per switch type:
 *
 * 1. → English: 1 hit required. Sarvam's English detection is highly accurate.
 *    This ensures the investor can pick up the phone and get English immediately.
 *
 * 2. English → Indic: 2 hits required. Prevents one accidental Indic detection
 *    from flipping an English session (e.g., typing noise mis-detected as Hindi).
 *
 * 3. Indic → Indic (same script family): 3 hits required. This is where false
 *    detections happen — "हाँ" → od-IN, short Hindi → kn-IN, bn-IN. Requires 3
 *    consecutive same-language detections before accepting the switch.
 *
 * 4. Odia (od), Bengali (bn): BLOCKED entirely. Near-zero legitimate speakers
 *    in AP/Telangana, and they are the #1 false detection for Hindi short words.
 */
object SessionLanguageManager {

    // ── Blocked languages (never legitimate in Butler's target market) ────────
    private val BLOCKED_LANGUAGES = setOf("od", "bn")

    // ── Allowed languages ─────────────────────────────────────────────────────
    private val ALLOWED_LANGUAGES = setOf(
        "hi", "en", "te", "ta", "kn", "ml", "pa", "gu", "mr"
    )

    // ── Script families (for threshold calculation) ───────────────────────────
    // Indic-to-Indic switches have the most false detections, so need more hits.
    private val INDIC_LANGUAGES = setOf("hi", "te", "ta", "kn", "ml", "pa", "gu", "mr")

    // ── State ─────────────────────────────────────────────────────────────────

    var lockedLanguage: String = "en-IN"
        private set

    val ttsLanguage: String
        get() = lockedLanguage.substringBefore("-")

    private var pendingLanguage: String  = ""
    private var consecutiveCount: Int    = 0

    // ── Core logic ────────────────────────────────────────────────────────────

    /**
     * Returns true if the locked language changed.
     * Threshold depends on the type of switch:
     *   any → English       = 1 hit  (fast, reliable, investor-friendly)
     *   English → Indic     = 2 hits (moderate protection)
     *   Indic → Indic       = 3 hits (maximum protection against false detections)
     */
    fun onDetection(sarvamLangCode: String): Boolean {
        val base      = sarvamLangCode.substringBefore("-").lowercase()
        val lockedBase = lockedLanguage.substringBefore("-").lowercase()

        // ── Blocked entirely ──────────────────────────────────────────────────
        if (base in BLOCKED_LANGUAGES) {
            Log.d("SessionLang", "Blocked: $sarvamLangCode")
            return false
        }

        // ── Not in our supported set ──────────────────────────────────────────
        if (base !in ALLOWED_LANGUAGES) {
            Log.d("SessionLang", "Unsupported: $sarvamLangCode — ignored")
            return false
        }

        // ── Already locked to this language ───────────────────────────────────
        if (base == lockedBase) {
            pendingLanguage  = ""
            consecutiveCount = 0
            return false
        }

        // ── Calculate threshold for this switch type ──────────────────────────
        val threshold = when {
            base == "en"                                       -> 1  // → English: always fast
            lockedBase == "en" && base in INDIC_LANGUAGES     -> 2  // English → Indic
            lockedBase in INDIC_LANGUAGES && base in INDIC_LANGUAGES -> 3  // Indic → Indic
            else                                               -> 2
        }

        // ── Accumulate consecutive hits ───────────────────────────────────────
        if (base == pendingLanguage.substringBefore("-").lowercase()) {
            consecutiveCount++
            Log.d("SessionLang",
                "Candidate $sarvamLangCode: $consecutiveCount/$threshold hits")
        } else {
            pendingLanguage  = sarvamLangCode
            consecutiveCount = 1
            Log.d("SessionLang",
                "New candidate: $sarvamLangCode (1/$threshold needed)")
        }

        // ── Threshold reached — switch ────────────────────────────────────────
        if (consecutiveCount >= threshold) {
            val previous     = lockedLanguage
            lockedLanguage   = sarvamLangCode
            pendingLanguage  = ""
            consecutiveCount = 0
            Log.d("SessionLang", "Language switched: $previous → $lockedLanguage")
            return true
        }

        return false
    }

    /**
     * Blank transcripts are neutral — do not change locked language or counter.
     */
    fun onBlankTranscript() {
        Log.d("SessionLang",
            "Blank transcript — pending language reset, locked=$lockedLanguage stays")
    }

    /** Reset at session start (after wake word). */
    fun reset() {
        lockedLanguage   = "en-IN"
        pendingLanguage  = ""
        consecutiveCount = 0
        Log.d("SessionLang", "Reset")
    }

    /**
     * Force-lock a language immediately (used at greeting time so first response
     * is already in the correct language without needing accumulation hits).
     */
    fun forceSet(sarvamLangCode: String) {
        val base = sarvamLangCode.substringBefore("-").lowercase()
        if (base !in BLOCKED_LANGUAGES && base in ALLOWED_LANGUAGES) {
            lockedLanguage   = sarvamLangCode
            pendingLanguage  = ""
            consecutiveCount = 0
            Log.d("SessionLang", "Force-set: $sarvamLangCode")
        } else {
            Log.w("SessionLang", "forceSet ignored — $sarvamLangCode blocked/unsupported")
        }
    }
}