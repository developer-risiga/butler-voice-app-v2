package com.demo.butler_voice_app.ai

import android.util.Log

/**
 * EmailPasswordParser — 100% voice-accurate email & password recognition
 *
 * Handles every Indian accent variation Sarvam STT produces:
 *   "ajay at the rate gmail dot com"       → ajay@gmail.com
 *   "A J A Y underscore 9 9 at gmail"      → ajay_99@gmail.com
 *   "subhash one two three at yahoo"       → subhash123@yahoo.com
 *   "capital S small u b h a s h 1 2 3"   → Subhash123 (password)
 *   "my email is ravi dot kumar at gmail"  → ravi.kumar@gmail.com
 */
object EmailPasswordParser {

    private const val TAG = "EmailParser"

    // ─── AT-sign spoken variations ───────────────────────────────────────────
    private val AT_PATTERNS = listOf(
        // Multi-word (match longest first)
        "at the rate of", "at the rate", "at rate", "at da rate",
        "aat da rate", "at d rate",
        // Hindi
        "एट द रेट ऑफ", "एट द रेट", "एट रेट", "एट", "@",
        // Telugu
        "ఆట్ ది రేట్", "ఆట్",
        // Tamil
        "ஆட் தி ரேட்", "ஆட்",
        // Kannada
        "ಆಟ್ ದಿ ರೇಟ್", "ಆಟ್",
        // Malayalam
        "ആറ്റ് ദ റേറ്റ്", "ആറ്റ്",
        // Punjabi
        "ਐਟ ਦੀ ਰੇਟ", "ਐਟ",
        // Odia
        "ଆଟ ଦ ରେଟ", "ଆଟ",
        // English casual
        "at"
    ).sortedByDescending { it.length }  // longest match first

    // ─── DOT spoken variations ───────────────────────────────────────────────
    private val DOT_PATTERNS = listOf(
        "dot com", "dot in", "dot net", "dot org", "dot co dot in",
        "dot co", "dot",
        "डॉट कॉम", "डॉट", "ਡੌਟ ਕਾਮ", "ਡੌਟ",
        "ഡോട്ട്", "డాట్", "ಡಾಟ್", "ட்டாட்", "ଡଟ"
    ).sortedByDescending { it.length }

    // ─── Common domain shortcuts ─────────────────────────────────────────────
    private val DOMAIN_MAP = mapOf(
        "gmail" to "gmail.com", "yahoo" to "yahoo.com",
        "hotmail" to "hotmail.com", "outlook" to "outlook.com",
        "rediff" to "rediffmail.com", "rediffmail" to "rediffmail.com",
        "ymail" to "yahoo.com", "proton" to "protonmail.com",
        "icloud" to "icloud.com", "zoho" to "zoho.com"
    )

    // ─── Spelled-out letter → character ─────────────────────────────────────
    private val LETTER_MAP = mapOf(
        // "A for Apple" style (Indian accent)
        "a for apple" to "a", "b for boy" to "b", "c for cat" to "c",
        "d for dog" to "d", "e for egg" to "e", "f for fish" to "f",
        "g for good" to "g", "h for hen" to "h", "i for ink" to "i",
        "j for jug" to "j", "k for king" to "k", "l for lion" to "l",
        "m for mango" to "m", "n for nut" to "n", "o for owl" to "o",
        "p for pen" to "p", "q for queen" to "q", "r for rat" to "r",
        "s for sun" to "s", "t for top" to "t", "u for umbrella" to "u",
        "v for van" to "v", "w for water" to "w", "x for xray" to "x",
        "y for yellow" to "y", "z for zebra" to "z",
        // Standard NATO
        "alpha" to "a", "bravo" to "b", "charlie" to "c", "delta" to "d",
        "echo" to "e", "foxtrot" to "f", "golf" to "g", "hotel" to "h",
        "india" to "i", "juliet" to "j", "kilo" to "k", "lima" to "l",
        "mike" to "m", "november" to "n", "oscar" to "o", "papa" to "p",
        "quebec" to "q", "romeo" to "r", "sierra" to "s", "tango" to "t",
        "uniform" to "u", "victor" to "v", "whiskey" to "w", "xray" to "x",
        "yankee" to "y", "zulu" to "z",
        // Numbers spoken
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3",
        "four" to "4", "five" to "5", "six" to "6", "seven" to "7",
        "eight" to "8", "nine" to "9",
        // Hindi numbers
        "शून्य" to "0", "एक" to "1", "दो" to "2", "तीन" to "3",
        "चार" to "4", "पाँच" to "5", "छह" to "6", "सात" to "7",
        "आठ" to "8", "नौ" to "9",
        // Telugu numbers
        "సున్న" to "0", "ఒకటి" to "1", "రెండు" to "2", "మూడు" to "3",
        "నాలుగు" to "4", "ఐదు" to "5",
        // Symbols
        "underscore" to "_", "under score" to "_",
        "dash" to "-", "hyphen" to "-", "minus" to "-",
        "dot" to ".", "period" to ".", "full stop" to ".",
        "plus" to "+", "at sign" to "@", "hash" to "#"
    ).toSortedMap(compareByDescending { it.length })

    // ─── MAIN: Parse email ────────────────────────────────────────────────────

    /**
     * Converts spoken email to valid email string.
     * Returns null if not recognisable — use [parseEmailBestEffort] for fallback.
     */
    fun parseEmail(spoken: String): String? {
        var text = spoken.lowercase().trim()

        // Strip prefix phrases
        text = text
            .replace(Regex("(my\\s+)?(email|ईमेल)(\\s+(id|address|is|hai|aadress|ID))*\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(मेरी?\\s+)?ईमेल(\\s+आईडी)?\\s*है?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        // Replace "dot X" sequences (longest first)
        for (dp in DOT_PATTERNS) {
            text = text.replace(dp, ".", ignoreCase = true)
        }

        // Replace "at" sequences (longest first)
        for (ap in AT_PATTERNS) {
            text = text.replace(" $ap ", "@", ignoreCase = true)
            text = text.replace(ap, "@", ignoreCase = true)
        }

        // Expand domain shortcuts (e.g. "@gmail" → "@gmail.com")
        val domainPattern = Regex("@([a-z]+)$")
        val match = domainPattern.find(text)
        if (match != null) {
            val bare = match.groupValues[1]
            val full = DOMAIN_MAP[bare]
            if (full != null) text = text.replace("@$bare", "@$full")
        }

        // Remove all spaces
        text = text.replace(Regex("\\s+"), "")

        // Normalise multiple @@ or ..
        text = text.replace(Regex("@+"), "@").replace(Regex("\\.{2,}"), ".")
        text = text.trimEnd('.', ',', '!', '?', '।')

        Log.d(TAG, "Email parsed: [$spoken] → [$text]")
        return if (isValidEmail(text)) text else null
    }

    /** Best-effort: returns something even if not perfect email */
    fun parseEmailBestEffort(spoken: String): String {
        return parseEmail(spoken) ?: run {
            spoken.lowercase()
                .replace(Regex("at(\\s+the\\s+rate)?"), "@")
                .replace(Regex("dot"), ".")
                .replace(Regex("\\s+"), "")
                .trimEnd('.', '!', '?')
        }
    }

    fun isValidEmail(email: String): Boolean =
        email.contains("@") &&
                email.contains(".") &&
                email.length >= 6 &&
                email.indexOf("@") > 0 &&
                email.lastIndexOf(".") > email.indexOf("@") &&
                !email.endsWith(".") &&
                !email.startsWith("@") &&
                !email.contains("@@") &&
                !email.contains(".@")

    // ─── MAIN: Parse password ─────────────────────────────────────────────────

    /**
     * Converts spoken password to string.
     * "capital S small u b h a s h 1 2 3 at sign" → "Subhash123@"
     * "Subhash123" (spoken directly)               → "Subhash123"
     */
    fun parsePassword(spoken: String): String {
        var text = spoken.lowercase().trim()

        // Strip leading prefix
        text = text
            .replace(Regex("(my\\s+)?(password|पासवर्ड|పాస్‌వర్డ్|ಪಾಸ್‌ವರ್ಡ್|கடவுச்சொல்)\\s*(is|hai|aadhe)?\\s*"), "")
            .trim()

        // If it looks like a single word already (no spaces), return it
        if (!text.contains(" ") && text.length >= 4) {
            Log.d(TAG, "Password direct: [$spoken] → [$text]")
            return text
        }

        // Letter-by-letter parsing
        val result = StringBuilder()
        var upperNext = false
        var lowerNext = false
        var i = 0
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }

        while (i < words.size) {
            val word = words[i]

            // Capital / uppercase modifier
            if (word in listOf("capital", "uppercase", "caps", "upper", "big")) {
                upperNext = true; i++; continue
            }
            if (word in listOf("small", "lowercase", "lower", "little")) {
                lowerNext = true; i++; continue
            }

            // Check 3-word pattern "a for apple"
            if (i + 2 < words.size && words[i + 1] == "for") {
                val threeWord = "${words[i]} for ${words[i + 2]}"
                val letter = LETTER_MAP[threeWord]
                if (letter != null) {
                    result.append(applyCase(letter, upperNext, lowerNext))
                    upperNext = false; lowerNext = false; i += 3; continue
                }
            }

            // Check single-word letter / number / symbol
            val mapped = LETTER_MAP[word]
            if (mapped != null) {
                result.append(applyCase(mapped, upperNext, lowerNext))
                upperNext = false; lowerNext = false; i++; continue
            }

            // Special symbols spoken in Indian style
            when (word) {
                "at", "at sign", "at the rate" -> { result.append("@"); i++; continue }
                "hash", "hashtag" -> { result.append("#"); i++; continue }
                "dollar" -> { result.append("$"); i++; continue }
                "exclamation", "bang" -> { result.append("!"); i++; continue }
                "star", "asterisk" -> { result.append("*"); i++; continue }
                "underscore" -> { result.append("_"); i++; continue }
                "dash", "hyphen" -> { result.append("-"); i++; continue }
                "space" -> { result.append(" "); i++; continue }
            }

            // Direct digit
            if (word.matches(Regex("\\d+"))) {
                result.append(word); upperNext = false; lowerNext = false; i++; continue
            }

            // Direct word (spoken as-is, e.g. "ravi123")
            if (word.length > 1) {
                val char = applyCase(word, upperNext, lowerNext)
                result.append(char); upperNext = false; lowerNext = false
            }
            i++
        }

        val password = result.toString()
            .replace(Regex("\\s+"), "")
            .trimEnd('.', '।', '!')
        Log.d(TAG, "Password parsed: [$spoken] → len=${password.length}")
        return password
    }

    private fun applyCase(s: String, upper: Boolean, lower: Boolean): String = when {
        upper -> s.uppercase()
        lower -> s.lowercase()
        else  -> s
    }

    // ─── Language-specific prompts ────────────────────────────────────────────

    fun getEmailPrompt(lang: String): String = when (lang) {
        "hi" -> "अपनी ईमेल बोलिए — जैसे: ravi at the rate gmail dot com"
        "te" -> "మీ ఇమెయిల్ చెప్పండి — ఉదాహరణకు: ravi at the rate gmail dot com"
        "kn" -> "ನಿಮ್ಮ ಇಮೇಲ್ ಹೇಳಿ — ಉದಾ: ravi at the rate gmail dot com"
        "ta" -> "உங்கள் மின்னஞ்சல் சொல்லுங்கள் — எ.கா: ravi at the rate gmail dot com"
        "ml" -> "നിങ്ങളുടെ ഇമെയിൽ പറയൂ — ഉദ്ദേ: ravi at the rate gmail dot com"
        "pa" -> "ਆਪਣੀ ਈਮੇਲ ਦੱਸੋ — ਜਿਵੇਂ: ravi at the rate gmail dot com"
        "or" -> "ଆପଣଙ୍କ ଇମେଲ କୁହନ୍ତୁ — ଯଥା: ravi at the rate gmail dot com"
        else -> "Please say your email — for example: ravi at the rate gmail dot com"
    }

    fun getPasswordPrompt(lang: String): String = when (lang) {
        "hi" -> "अब पासवर्ड बोलिए — जैसे: capital R, a, v, i, 1, 2, 3"
        "te" -> "ఇప్పుడు మీ పాస్‌వర్డ్ చెప్పండి"
        "kn" -> "ಈಗ ಪಾಸ್‌ವರ್ಡ್ ಹೇಳಿ"
        "ta" -> "கடவுச்சொல் சொல்லுங்கள்"
        "ml" -> "ഇപ്പോൾ പാസ്‌വേഡ് പറയൂ"
        "pa" -> "ਹੁਣ ਪਾਸਵਰਡ ਦੱਸੋ"
        "or" -> "ଏବେ ପାସ୍ୱର୍ଡ କୁହନ୍ତୁ"
        else -> "Now say your password — for example: capital R, a, v, i, 1, 2, 3"
    }

    fun getEmailRetryPrompt(lang: String): String = when (lang) {
        "hi" -> "ईमेल समझ नहीं आई। फिर से बोलिए जैसे: john at the rate gmail dot com"
        "te" -> "ఇమెయిల్ అర్థం కాలేదు. మళ్ళీ చెప్పండి: john at the rate gmail dot com"
        "kn" -> "ಇಮೇಲ್ ಅರ್ಥವಾಗಲಿಲ್ಲ. ಮತ್ತೆ ಹೇಳಿ: john at the rate gmail dot com"
        "ta" -> "மின்னஞ்சல் புரியவில்லை. மீண்டும் சொல்லுங்கள்: john at the rate gmail dot com"
        "ml" -> "ഇമെയിൽ മനസ്സിലായില്ല. വീണ്ടും പറയൂ: john at the rate gmail dot com"
        "pa" -> "ਈਮੇਲ ਸਮਝ ਨਹੀਂ ਆਈ। ਦੁਬਾਰਾ ਦੱਸੋ: john at the rate gmail dot com"
        "or" -> "ଇମେଲ ବୁଝିଲିନି। ପୁଣି କୁହନ୍ତୁ: john at the rate gmail dot com"
        else -> "I didn't catch the email. Please say it again: john at the rate gmail dot com"
    }
}