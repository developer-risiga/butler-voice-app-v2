package com.demo.butler_voice_app.ai

import android.util.Log

/**
 * Manages real-time language switching across a session.
 *
 * DESIGN PRINCIPLE:
 * Transcript length is the only reliable signal for distinguishing a genuine
 * speaker from a false STT detection. A single word in a foreign script is
 * almost always noise. A full sentence is always a real person speaking.
 *
 * THRESHOLD TABLE:
 * ─────────────────────────────────────────────────────────────────────────
 *  Switch direction            │ Words  │ Hits needed  │ Rationale
 * ─────────────────────────────────────────────────────────────────────────
 *  Any → English               │ 3+     │ 2            │ Protects Hindi sessions
 *  Any → English               │ 1-2    │ blocked      │ "UPI", "ok" = code-switch
 *  English → Indic             │ 3+     │ 1            │ User's real language
 *  English → Indic             │ 1-2    │ 2            │ Short utterance
 *  Indic → Different Indic     │ 3+     │ 2            │ FIXED: was 1 → Gujarati bug
 *  Indic → Different Indic     │ 1-2    │ 3            │ Almost certainly noise
 *  Odia / Bengali              │ any    │ ∞            │ Blocked (misdetection-prone)
 *  No-switch words (UPI, QR)   │ any    │ ∞            │ Universal Indian terms
 * ─────────────────────────────────────────────────────────────────────────
 *
 * KEY FIX: Indic→Indic long utterance threshold was 1 (too sensitive).
 * Example from logcat: session=hi-IN, Sarvam returns 5-word gu-IN →
 *   "[gu 5w] New candidate: 1/1 → Switched: hi-IN → gu-IN"
 * With threshold=2: requires 2 consecutive same-language detections.
 * A real Gujarati speaker will naturally trigger this in 2 utterances.
 * A false detection almost never repeats consecutively.
 */
object SessionLanguageManager {

    private val BLOCKED = setOf("od", "bn")
    private val ALLOWED = setOf("hi", "en", "te", "ta", "kn", "ml", "pa", "gu", "mr")
    private val INDIC   = setOf("hi", "te", "ta", "kn", "ml", "pa", "gu", "mr")

    // Universal terms that appear in all Indian language contexts.
    // Sarvam tags these as en-IN, but they must never trigger a language switch.
    // "UPI" during a Hindi order = the user paying in UPI, not switching to English.
    private val NO_SWITCH_WORDS = setOf(
        "upi", "qr", "ok", "okay", "yes", "no", "ha", "id",
        "otp", "atm", "ac", "tv", "pc", "app", "pin", "sms",
        "gpay", "bhim", "paytm", "phonepe", "neft", "imps"
    )

    var lockedLanguage: String = "en-IN"
        private set

    val ttsLanguage: String
        get() = lockedLanguage.substringBefore("-")

    val sarvamHint: String?
        get() = if (languageExplicitlySet) lockedLanguage else null

    private var pendingLanguage: String  = ""
    private var consecutiveCount: Int    = 0
    private var pendingThreshold: Int    = 3
    private var languageExplicitlySet: Boolean = false

    fun onDetection(sarvamLangCode: String, transcript: String = ""): Boolean {
        val base       = sarvamLangCode.substringBefore("-").lowercase()
        val lockedBase = lockedLanguage.substringBefore("-").lowercase()

        if (base in BLOCKED) {
            Log.d("SessionLang", "Blocked script: $sarvamLangCode")
            return false
        }
        if (base !in ALLOWED) {
            Log.d("SessionLang", "Unsupported: $sarvamLangCode — ignored")
            return false
        }

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

        // Guard 1: Universal no-switch words
        if (wordCount == 1 && transcriptLower in NO_SWITCH_WORDS) {
            Log.d("SessionLang", "No-switch word '$transcriptLower' — keeping $lockedLanguage")
            return false
        }

        // Guard 2: Short English mid-Indic session = code-switching, not real switch
        if (base == "en" && lockedBase in INDIC && !isLong) {
            Log.d("SessionLang", "Short en utterance during $lockedBase session — ignored")
            return false
        }

        val threshold = when {
            base == "en"                                      -> 2   // English requires 2 consecutive
            lockedBase == "en" && isLong                      -> 1   // Long utterance after en-IN = real switch
            lockedBase == "en"                                -> 2   // Short utterance after en-IN
            // KEY FIX: was -> 1 for long Indic→Indic
            // Now requires 2 consecutive to prevent Gujarati hallucination switching
            lockedBase in INDIC && base in INDIC && isLong   -> 2
            else                                              -> 3   // Short Indic→Indic = almost always noise
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