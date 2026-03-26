package com.demo.butler_voice_app.ai

/**
 * MultilingualMatcher
 *
 * Handles number word recognition across ALL major Indian languages.
 * Sarvam STT returns transcripts in the detected script — Hindi spoken
 * words may come back as Punjabi (ਦੋ), Gujarati (બે), Odia (ଦୁଇ), etc.
 * This class normalizes all of them to a position (1/2/3).
 *
 * Also handles:
 *   - Confirmation words ("yes"/"हाँ"/"ஆம்"/"అవును" etc.) across languages
 *   - Cancellation words ("no"/"नहीं"/"இல்லை" etc.)
 *   - "Finish order" phrases in all Indian languages
 */
object MultilingualMatcher {

    // ── Number 1 across Indian languages ─────────────────────
    private val ONE_WORDS = setOf(
        // English
        "1", "one", "first",
        // Hindi/Devanagari
        "एक", "पहला", "पहले", "पहली",
        // Romanized Hindi
        "ek", "pahla", "pehla",
        // Punjabi (Gurmukhi)
        "ਇੱਕ", "ਪਹਿਲਾ",
        // Gujarati
        "એક", "પહેલો",
        // Marathi
        "एक", "पहिला",
        // Bengali
        "এক", "প্রথম",
        // Tamil
        "ஒன்று", "முதல்",
        // Telugu
        "ఒకటి", "మొదటి",
        // Kannada
        "ಒಂದು", "ಮೊದಲ",
        // Malayalam
        "ഒന്ന്", "ഒന്നാമത്",
        // Odia
        "ଏକ", "ପ୍ରଥମ",
        // Urdu
        "ایک", "پہلا",
        // Assamese
        "এক", "প্ৰথম",
        // Bhojpuri/Maithili/Awadhi
        "ekal", "pahile"
    )

    // ── Number 2 across Indian languages ─────────────────────
    private val TWO_WORDS = setOf(
        // English
        "2", "two", "second",
        // Hindi/Devanagari
        "दो", "दूसरा", "दूसरे", "दूसरी",
        // Romanized Hindi
        "do", "doosra", "dusra",
        // Punjabi (Gurmukhi)
        "ਦੋ", "ਦੂਜਾ",
        // Gujarati
        "બે", "બીજો",
        // Marathi
        "दोन", "दुसरा",
        // Bengali
        "দুই", "দ্বিতীয়",
        // Tamil
        "இரண்டு", "இரண்டாவது",
        // Telugu
        "రెండు", "రెండవ",
        // Kannada
        "ಎರಡು", "ಎರಡನೇ",
        // Malayalam
        "രണ്ട്", "രണ്ടാമത്",
        // Odia
        "ଦୁଇ", "ଦ୍ୱିତୀୟ",
        // Urdu
        "دو", "دوسرا",
        // Assamese
        "দুই", "দ্বিতীয়"
    )

    // ── Number 3 across Indian languages ─────────────────────
    private val THREE_WORDS = setOf(
        // English
        "3", "three", "third",
        // Hindi/Devanagari
        "तीन", "तीसरा", "तीसरे", "तीसरी",
        // Romanized Hindi
        "teen", "teesra", "tisra",
        // Punjabi (Gurmukhi)
        "ਤਿੰਨ", "ਤੀਜਾ",
        // Gujarati
        "ત્રણ", "ત્રીજો",
        // Marathi
        "तीन", "तिसरा",
        // Bengali
        "তিন", "তৃতীয়",
        // Tamil
        "மூன்று", "மூன்றாவது",
        // Telugu
        "మూడు", "మూడవ",
        // Kannada
        "ಮೂರು", "ಮೂರನೇ",
        // Malayalam
        "മൂന്ന്", "മൂന്നാമത്",
        // Odia
        "ତିନି", "ତୃତୀୟ",
        // Urdu
        "تین", "تیسرا",
        // Assamese
        "তিনি", "তৃতীয়"
    )

    // ── Yes/Confirm across Indian languages ──────────────────
    val YES_WORDS = setOf(
        "yes", "yeah", "yep", "ok", "okay", "sure", "fine", "correct",
        // Hindi
        "हाँ", "हां", "हा", "बिल्कुल", "ठीक", "ठीक है", "जरूर", "हाँ जी",
        // Romanized Hindi
        "haan", "ha", "bilkul", "theek", "theek hai", "zaroor", "ji haan",
        // Punjabi
        "ਹਾਂ", "ਹਾਂ ਜੀ", "ਠੀਕ ਹੈ",
        // Gujarati
        "હા", "ઠીક છે",
        // Marathi
        "हो", "होय", "बरं",
        // Bengali
        "হ্যাঁ", "হ্যাঁ জি", "ঠিক আছে",
        // Tamil
        "ஆம்", "சரி",
        // Telugu
        "అవును", "సరే",
        // Kannada
        "ಹೌದು", "ಸರಿ",
        // Malayalam
        "അതെ", "ശരി",
        // Odia
        "ହଁ", "ଠିକ ଅଛି",
        // Urdu
        "ہاں", "جی ہاں",
        // Assamese
        "হয়", "হয় জী",
        // Order-specific
        "confirm", "place", "proceed", "order kar do", "kar do", "karo",
        "ऑर्डर कर दो", "ऑर्डर करो", "चलो", "done", "place order"
    )

    // ── No/Cancel across Indian languages ────────────────────
    val NO_WORDS = setOf(
        "no", "nope", "cancel", "stop",
        // Hindi
        "नहीं", "नही", "मत", "रुको", "बंद करो",
        // Romanized Hindi
        "nahi", "mat", "ruko", "band karo",
        // Punjabi
        "ਨਹੀਂ",
        // Gujarati
        "ના",
        // Marathi
        "नाही",
        // Bengali
        "না",
        // Tamil
        "இல்லை",
        // Telugu
        "కాదు", "వద్దు",
        // Kannada
        "ಇಲ್ಲ",
        // Malayalam
        "ഇല്ല",
        // Odia
        "ନା",
        // Urdu
        "نہیں",
        // Assamese
        "নহয়"
    )

    // ── "Nothing more / finish order" across languages ───────
    val DONE_WORDS = setOf(
        "done", "nothing", "finish", "that's all", "thats all",
        // Hindi
        "बस", "हो गया", "इतना ही", "और नहीं", "कुछ नहीं", "खत्म",
        // Romanized Hindi
        "bas", "ho gaya", "khatam", "aur nahi", "kuch nahi",
        // Punjabi
        "ਬੱਸ",
        // Gujarati
        "બસ",
        // Marathi
        "बस",
        // Bengali
        "শেষ", "আর না",
        // Tamil
        "போதும்",
        // Telugu
        "చాలు",
        // Kannada
        "ಸಾಕು",
        // Malayalam
        "മതി",
        // Odia
        "ଯଥେଷ୍ଟ",
        // Assamese
        "বস"
    )

    /**
     * Matches spoken text to option 1, 2, or 3.
     * Returns the 0-based index (0, 1, 2) or -1 if no match.
     *
     * Handles:
     *   - All Indian language number words
     *   - Devanagari/Gurmukhi/Gujarati/Tamil/Telugu scripts
     *   - Mixed language responses ("option two", "number 2 chahiye")
     *   - Noise like "Two." or "ਦੋ।"
     */
    fun matchNumber(spoken: String): Int {
        val cleaned = spoken.lowercase()
            .replace(Regex("[।.!?,;؟]"), " ")  // remove punctuation from all scripts
            .trim()

        // Split into tokens for word-level matching
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }

        // Check each token against number sets
        for (token in tokens) {
            when {
                ONE_WORDS.any { it.equals(token, ignoreCase = true) ||
                        cleaned.contains(it, ignoreCase = true) &&
                        it.length > 1 } -> return 0

                TWO_WORDS.any { it.equals(token, ignoreCase = true) ||
                        cleaned.contains(it, ignoreCase = true) &&
                        it.length > 1 } -> return 1

                THREE_WORDS.any { it.equals(token, ignoreCase = true) ||
                        cleaned.contains(it, ignoreCase = true) &&
                        it.length > 1 } -> return 2
            }
        }

        // Direct substring check for single-character scripts (e.g. ਦੋ, ந etc.)
        return when {
            ONE_WORDS.any { cleaned.contains(it) && it.length >= 1 } -> 0
            TWO_WORDS.any { cleaned.contains(it) && it.length >= 1 } -> 1
            THREE_WORDS.any { cleaned.contains(it) && it.length >= 1 } -> 2
            else -> -1
        }
    }

    /**
     * Returns true if the spoken text means "yes/confirm" in any Indian language.
     */
    fun isYes(spoken: String): Boolean {
        val cleaned = spoken.lowercase().replace(Regex("[।.!?,]"), "").trim()
        return YES_WORDS.any { cleaned.contains(it.lowercase()) }
    }

    /**
     * Returns true if the spoken text means "no/cancel" in any Indian language.
     */
    fun isNo(spoken: String): Boolean {
        val cleaned = spoken.lowercase().replace(Regex("[।.!?,]"), "").trim()
        return NO_WORDS.any { cleaned.contains(it.lowercase()) }
    }

    /**
     * Returns true if the spoken text means "I'm done/nothing more".
     */
    fun isDone(spoken: String): Boolean {
        val cleaned = spoken.lowercase().replace(Regex("[।.!?,]"), "").trim()
        return DONE_WORDS.any { cleaned.contains(it.lowercase()) }
    }

    /**
     * Detects which Indian language the text is written in based on Unicode ranges.
     * Returns ISO 639-1 code.
     */
    fun detectScript(text: String): String {
        val counts = mutableMapOf<String, Int>()
        for (char in text) {
            val block = Character.UnicodeBlock.of(char)
            when (block) {
                Character.UnicodeBlock.DEVANAGARI       -> counts["hi"] = (counts["hi"] ?: 0) + 1
                Character.UnicodeBlock.GURMUKHI         -> counts["pa"] = (counts["pa"] ?: 0) + 1
                Character.UnicodeBlock.GUJARATI         -> counts["gu"] = (counts["gu"] ?: 0) + 1
                Character.UnicodeBlock.BENGALI          -> counts["bn"] = (counts["bn"] ?: 0) + 1
                Character.UnicodeBlock.TAMIL            -> counts["ta"] = (counts["ta"] ?: 0) + 1
                Character.UnicodeBlock.TELUGU           -> counts["te"] = (counts["te"] ?: 0) + 1
                Character.UnicodeBlock.KANNADA          -> counts["kn"] = (counts["kn"] ?: 0) + 1
                Character.UnicodeBlock.MALAYALAM        -> counts["ml"] = (counts["ml"] ?: 0) + 1
                Character.UnicodeBlock.ORIYA            -> counts["or"] = (counts["or"] ?: 0) + 1
                Character.UnicodeBlock.ARABIC           -> counts["ur"] = (counts["ur"] ?: 0) + 1
                else -> {}
            }
        }
        return counts.maxByOrNull { it.value }?.key ?: "en"
    }
}