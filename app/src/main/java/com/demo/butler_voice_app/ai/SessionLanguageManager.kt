package com.demo.butler_voice_app.ai

import android.util.Log

/**
 * Manages real-time language switching across a session.
 *
 * DESIGN PRINCIPLE:
 * The only reliable way to distinguish a false detection from a genuine speaker
 * is transcript length. A single short word ("हाँ", "ಅಂತ", "ହଁ") is likely a
 * mis-detection. A full sentence ("నాకు rice కావాలి", "mujhe dal chahiye") is
 * definitively a real person speaking.
 *
 * THRESHOLD TABLE:
 * ─────────────────────────────────────────────────────────────────
 *  Switch direction          │ Transcript │ Hits needed
 * ─────────────────────────────────────────────────────────────────
 *  Any → English             │ 3+ words   │ 2  (raised — protects Hindi sessions)
 *  Any → English             │ 1-2 words  │ blocked if no-switch word
 *  English → Indic           │ 3+ words   │ 1  (genuine speaker)
 *  English → Indic           │ 1-2 words  │ 2  (moderate protection)
 *  Indic → Different Indic   │ 3+ words   │ 1  (e.g. investor speaks Telugu)
 *  Indic → Different Indic   │ 1-2 words  │ 3  (likely false detection)
 *  Odia / Bengali            │ any        │ ∞  (blocked entirely)
 *  No-switch words (UPI/QR)  │ any        │ ∞  (blocked — universal terms)
 * ─────────────────────────────────────────────────────────────────
 *
 * FIX: "UPI" was causing hi-IN → en-IN flip mid-session.
 * "UPI", "QR", "OK" are universal terms used across all Indian languages.
 * Sarvam tags them as en-IN but they should never trigger a language switch.
 */
object SessionLanguageManager {

    private val BLOCKED = setOf("od", "bn")
    private val ALLOWED = setOf("hi", "en", "te", "ta", "kn", "ml", "pa", "gu", "mr")
    private val INDIC   = setOf("hi", "te", "ta", "kn", "ml", "pa", "gu", "mr")

    // ── Universal terms that must never trigger a language switch ─────────
    // These appear in every Indian language context. Sarvam may tag them as
    // en-IN, but switching language for a single "UPI" or "QR" destroys the
    // session — user has to re-establish their language with 3+ utterances.
    private val NO_SWITCH_WORDS = setOf(
        "upi", "qr", "ok", "okay", "yes", "no", "ha", "id",
        "otp", "atm", "ac", "tv", "pc", "app", "pin", "sms",
        "gpay", "bhim", "paytm", "phonepe", "neft", "imps"
    )

    var lockedLanguage: String = "en-IN"
        private set

    val ttsLanguage: String
        get() = lockedLanguage.substringBefore("-")

    /**
     * Language hint passed to Sarvam API as ?language_code= query param.
     * Telling Sarvam what language to expect improves STT accuracy.
     * Returns null when language hasn't been determined yet (fresh session)
     * so Sarvam auto-detects instead of being locked to English by default.
     */
    val sarvamHint: String?
        get() = if (languageExplicitlySet) lockedLanguage else null

    private var pendingLanguage: String  = ""
    private var consecutiveCount: Int    = 0
    private var pendingThreshold: Int    = 3
    private var languageExplicitlySet: Boolean = false

    /**
     * Call on every non-blank STT result.
     *
     * @param sarvamLangCode  e.g. "hi-IN", "te-IN"
     * @param transcript      The actual text — word count determines threshold
     * @return true if locked language changed; caller must sync LanguageManager
     */
    fun onDetection(sarvamLangCode: String, transcript: String = ""): Boolean {
        val base       = sarvamLangCode.substringBefore("-").lowercase()
        val lockedBase = lockedLanguage.substringBefore("-").lowercase()

        // ── Script blocked entirely ────────────────────────────────────────
        if (base in BLOCKED) {
            Log.d("SessionLang", "Blocked: $sarvamLangCode")
            return false
        }
        if (base !in ALLOWED) {
            Log.d("SessionLang", "Unsupported: $sarvamLangCode — ignored")
            return false
        }

        // ── Already locked to this language — confirm and clear pending ────
        if (base == lockedBase) {
            if (pendingLanguage.isNotBlank())
                Log.d("SessionLang", "Confirmed $lockedLanguage — dropping candidate $pendingLanguage")
            pendingLanguage  = ""
            consecutiveCount = 0
            return false
        }

        val wordCount       = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val transcriptLower = transcript.trim().lowercase().replace(Regex("[।,!?.॥]"), "")
        val isLong          = wordCount >= 3

        // ── Guard 1: universal no-switch words ────────────────────────────
        // Single words like "UPI", "QR", "OK" used in all Indian languages.
        // Never trigger a language switch regardless of what Sarvam detected.
        if (wordCount == 1 && transcriptLower in NO_SWITCH_WORDS) {
            Log.d("SessionLang", "No-switch word '$transcriptLower' — keeping $lockedLanguage")
            return false
        }

        // ── Guard 2: switching back to English from Indic session ─────────
        // A single English word mid-Hindi session (like "done", "okay") is
        // almost always code-switching, not a real language change.
        // Require 3 words minimum before considering English switch.
        if (base == "en" && lockedBase in INDIC && !isLong) {
            Log.d("SessionLang", "Short en utterance during $lockedBase session — ignored")
            return false
        }

        val threshold = when {
            // Switching to English now requires 2 consecutive detections minimum
            base == "en"                                   -> 2
            lockedBase == "en" && isLong                   -> 1
            lockedBase == "en"                             -> 2
            lockedBase in INDIC && base in INDIC && isLong -> 2
            else                                           -> 3
        }

        val pendingBase = pendingLanguage.substringBefore("-").lowercase()
        if (base == pendingBase) {
            consecutiveCount++
            Log.d("SessionLang", "[$base ${wordCount}w] $consecutiveCount/$threshold hits")
        } else {
            pendingLanguage  = sarvamLangCode
            pendingThreshold = threshold
            consecutiveCount = 1
            Log.d("SessionLang", "[$base ${wordCount}w] New candidate: 1/$threshold")
        }

        val effectiveThreshold = minOf(pendingThreshold, threshold)

        if (consecutiveCount >= effectiveThreshold) {
            val previous     = lockedLanguage
            lockedLanguage   = sarvamLangCode
            pendingLanguage  = ""
            consecutiveCount = 0
            pendingThreshold = 3
            languageExplicitlySet = true
            Log.d("SessionLang", "Switched: $previous → $lockedLanguage ($wordCount words)")
            return true
        }
        return false
    }

    fun onBlankTranscript() {
        Log.d("SessionLang", "Blank transcript — locked=$lockedLanguage unchanged")
    }

    fun reset() {
        lockedLanguage        = "en-IN"
        pendingLanguage       = ""
        consecutiveCount      = 0
        pendingThreshold      = 3
        languageExplicitlySet = false
        Log.d("SessionLang", "Reset")
    }

    fun forceSet(sarvamLangCode: String) {
        val base = sarvamLangCode.substringBefore("-").lowercase()
        if (base !in BLOCKED && base in ALLOWED) {
            lockedLanguage        = sarvamLangCode
            pendingLanguage       = ""
            consecutiveCount      = 0
            pendingThreshold      = 3
            languageExplicitlySet = true
            Log.d("SessionLang", "Force-set: $sarvamLangCode")
        } else {
            Log.w("SessionLang", "forceSet ignored: $sarvamLangCode")
        }
    }
}