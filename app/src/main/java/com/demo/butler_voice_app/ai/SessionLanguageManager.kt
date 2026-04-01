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
 *  Any → English             │ any        │ 1  (very reliable detection)
 *  English → Indic           │ 3+ words   │ 1  (genuine speaker)
 *  English → Indic           │ 1-2 words  │ 2  (moderate protection)
 *  Indic → Different Indic   │ 3+ words   │ 1  (e.g. investor speaks Telugu)
 *  Indic → Different Indic   │ 1-2 words  │ 3  (likely false detection)
 *  Odia / Bengali            │ any        │ ∞  (blocked entirely)
 * ─────────────────────────────────────────────────────────────────
 *
 * DEMO SCENARIOS (all correct):
 *  Roy says "मुझे rice चाहिए"        → hi-IN in 1 utterance    ✅
 *  Team member says "I want rice"    → en-IN in 1 utterance    ✅
 *  COO says "हाँ दाल भी चाहिए"       → hi-IN in 1 utterance    ✅
 *  Investor says "నాకు rice కావాలి"  → te-IN in 1 utterance    ✅
 *  "हाँ" mis-detected as od-IN       → BLOCKED entirely        ✅
 *  "ಅಂತ" (1 word) during Hindi       → needs 3 hits → ignored  ✅
 */
object SessionLanguageManager {

    private val BLOCKED = setOf("od", "bn")
    private val ALLOWED = setOf("hi", "en", "te", "ta", "kn", "ml", "pa", "gu", "mr")
    private val INDIC   = setOf("hi", "te", "ta", "kn", "ml", "pa", "gu", "mr")

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
    private var languageExplicitlySet: Boolean = false  // true after forceSet or confirmed switch

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

        if (base in BLOCKED) {
            Log.d("SessionLang", "Blocked: $sarvamLangCode")
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

        val wordCount = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val isLong    = wordCount >= 3

        val threshold = when {
            base == "en"                                   -> 1
            lockedBase == "en" && isLong                   -> 1
            lockedBase == "en"                             -> 2
            lockedBase in INDIC && base in INDIC && isLong -> 1
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

        // Use the lower of the initial threshold and the current one.
        // If user starts with a long sentence, don't make them wait for the
        // original short-word threshold.
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