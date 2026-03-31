package com.demo.butler_voice_app.utils

import com.demo.butler_voice_app.ai.UserMood

/**
 * Makes Butler sound human by injecting natural fillers, thinking sounds,
 * mood-aware acknowledgements, and transition phrases.
 *
 * Rule: always call getThinkingFiller() and speak it BEFORE starting the OpenAI/API call —
 * so TTS fires instantly while the network request runs in parallel.
 */
object HumanFillerManager {

    // ── Thinking sounds (played immediately after STT, while AI processes) ───

    private val thinkingFillers = mapOf(
        "en" to listOf(
            "haan...", "sure...", "okay...", "let me check...",
            "one sec...", "got it...", "hmm...", "accha..."
        ),
        "hi" to listOf(
            "हाँ...", "ठीक है...", "देखता हूँ...", "एक सेकंड...",
            "हम्म...", "अच्छा...", "बताता हूँ...", "रुको ज़रा..."
        ),
        "te" to listOf(
            "సరే...", "చూస్తాను...", "ఒక్క నిమిషం...",
            "హా...", "అలాగే..."
        ),
        "ta" to listOf("சரி...", "ஒரு நிமிடம்...", "பார்க்கிறேன்..."),
        "ml" to listOf("ശരി...", "ഒരു നിമിഷം...", "നോക്കാം..."),
        "kn" to listOf("ಸರಿ...", "ನೋಡುತ್ತೇನೆ...", "ಒಂದು ನಿಮಿಷ...")
    )

    // ── Transition phrases (before reading cart/confirmation) ─────────────────

    private val transitionPhrases = mapOf(
        "en" to listOf(
            "alright —", "okay so —", "so —", "right —", "theek hai,"
        ),
        "hi" to listOf(
            "ठीक है, तो —", "अच्छा —", "चलते हैं —",
            "सुनो —", "तो —"
        ),
        "te" to listOf(
            "సరే, అయితే —", "అలాగే —", "తో —"
        ),
        "ta" to listOf("சரி, அப்படியானால் —", "நல்லது —"),
        "ml" to listOf("ശരി, അപ്പോൾ —", "ആയി —"),
        "kn" to listOf("ಸರಿ, ಆದರೆ —", "ಆಯ್ತು —")
    )

    // ── Mood-aware acknowledgements ──────────────────────────────────────────

    private val tiredAcks = mapOf(
        "en" to listOf("almost done.", "just a moment.", "won't take long."),
        "hi" to listOf("बस एक पल।", "जल्दी करता हूँ।", "हो जाएगा।"),
        "te" to listOf("ఒక్క నిమిషం.", "త్వరగా చేస్తాను.")
    )

    private val frustratedAcks = mapOf(
        "en" to listOf("I hear you.", "let me fix that.", "okay, right away."),
        "hi" to listOf("समझा।", "अभी करता हूँ।", "ठीक है, चलो।"),
        "te" to listOf("అర్థమైంది.", "ఇప్పుడే చేస్తాను.")
    )

    // ── Empty transcript retry (human, not robotic) ───────────────────────────

    private val emptyRetries = mapOf(
        "en" to listOf(
            "hmm, didn't catch that. go ahead.",
            "say that again?",
            "couldn't hear you clearly. try once more."
        ),
        "hi" to listOf(
            "सुना नहीं। फिर बोलो।",
            "क्या? एक बार और।",
            "ज़रा और ज़ोर से बोलो।"
        ),
        "te" to listOf(
            "వినలేదు. మళ్ళీ చెప్పండి.",
            "ఒకసారి మళ్ళీ అడగండి."
        )
    )

    // ── Related item suggestions ──────────────────────────────────────────────

    private val relatedItems = mapOf(
        "rice"   to mapOf("en" to "dal", "hi" to "दाल", "te" to "పప్పు"),
        "dal"    to mapOf("en" to "rice", "hi" to "चावल", "te" to "అన్నం"),
        "milk"   to mapOf("en" to "sugar", "hi" to "चीनी", "te" to "చక్కెర"),
        "bread"  to mapOf("en" to "butter", "hi" to "मक्खन", "te" to "వెన్న"),
        "oil"    to mapOf("en" to "salt", "hi" to "नमक", "te" to "ఉప్పు"),
        "atta"   to mapOf("en" to "oil", "hi" to "तेल", "te" to "నూనె"),
        "eggs"   to mapOf("en" to "bread", "hi" to "रोटी", "te" to "బ్రెడ్"),
        "tea"    to mapOf("en" to "sugar", "hi" to "चीनी", "te" to "చక్కెర"),
        "ghee"   to mapOf("en" to "atta", "hi" to "आटा", "te" to "పిండి"),
        "coffee" to mapOf("en" to "milk", "hi" to "दूध", "te" to "పాలు")
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun getThinkingFiller(lang: String): String {
        val base = lang.substringBefore("-")
        return (thinkingFillers[base] ?: thinkingFillers["en"]!!).random()
    }

    fun getTransition(lang: String): String {
        val base = lang.substringBefore("-")
        return (transitionPhrases[base] ?: transitionPhrases["en"]!!).random()
    }

    fun getMoodAck(mood: UserMood, lang: String): String? {
        val base = lang.substringBefore("-")
        return when (mood) {
            UserMood.TIRED      -> (tiredAcks[base]      ?: tiredAcks["en"]!!).random()
            UserMood.FRUSTRATED -> (frustratedAcks[base] ?: frustratedAcks["en"]!!).random()
            else                -> null   // CALM and any other moods return nothing
        }
    }

    /**
     * Returns retry message for empty transcripts.
     * @param retryCount  0-based — first retry uses index 0, etc.
     */
    fun getEmptyRetry(lang: String, retryCount: Int): String {
        val base = lang.substringBefore("-")
        val list = emptyRetries[base] ?: emptyRetries["en"]!!
        return list[retryCount.coerceAtMost(list.lastIndex)]
    }

    /**
     * Suggest a related item to cross-sell after adding a product.
     * Returns null if no suggestion available.
     */
    fun getRelatedSuggestion(productName: String, lang: String): String? {
        val base    = lang.substringBefore("-")
        val lower   = productName.lowercase()
        val entry   = relatedItems.entries.firstOrNull { lower.contains(it.key) } ?: return null
        return entry.value[base] ?: entry.value["en"]
    }
}