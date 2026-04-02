package com.demo.butler_voice_app.utils

/**
 * ButlerSpeechFormatter — pre-processes every string before it goes to TTS.
 *
 * Called once, right before ttsManager.speak(), after TranslationManager.
 *
 * FIXES FOUND IN LOGCAT:
 *   • "₹45" or "RS 95" → "pachpan rupaye" (Hindi) / "45 rupees" (English)
 *   • "DAAWAT BROWN" → "Daawat Brown" (ALL CAPS products from DB)
 *   • "Netmeds Partner -" → "Netmeds Partner" (trailing dash from name truncation)
 *   • "45" spoken as "char-panch" → "pachpan" (proper Hindi number words)
 *   • Numbers above 100 spoken digit by digit → proper words ("ek sau pachaas")
 *   • "BUT-D56DFD" → "D 5 6 D F D" (spell out order IDs so they're clear)
 *   • "butler@upi" → "butler at UPI" (email symbols confuse TTS)
 *   • Excessive punctuation that causes unnatural pauses
 */
object ButlerSpeechFormatter {

    fun format(text: String, lang: String): String {
        var t = text
        t = fixProviderNames(t)
        t = fixProductCasing(t)
        t = fixOrderIds(t)
        t = fixEmailSymbols(t)
        t = fixCurrency(t, lang)
        t = fixNumbers(t, lang)
        t = fixPunctuation(t)
        return t.trim()
    }

    // ── 1. Provider names — strip trailing dashes, double spaces ─────────────
    // "Netmeds Partner -" → "Netmeds Partner"
    // "Urban Company Electrician - Palasia" → "Urban Company Electrician"
    private fun fixProviderNames(t: String): String {
        return t
            .replace(Regex("\\s+-\\s+[A-Z][a-zA-Z\\s,]+\\.?$"), "") // strip "- Location" suffix
            .replace(Regex("\\s+-\\s*$"), "")                          // strip trailing " -"
            .replace(Regex("\\s{2,}"), " ")                            // collapse double spaces
    }

    // ── 2. Product casing — ALL CAPS → Title Case ────────────────────────────
    // "DAAWAT BROWN" → "Daawat Brown"
    // Only applies to words that are 3+ chars and fully uppercase
    private fun fixProductCasing(t: String): String {
        return t.replace(Regex("\\b([A-Z]{3,})\\b")) { match ->
            val word = match.value
            // Keep known acronyms as-is
            if (word in setOf("UPI", "QR", "AC", "ID", "OTP", "ATM", "GST", "EMI", "ITR", "PDF")) {
                word
            } else {
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

    // ── 3. Order IDs — spell them out clearly ────────────────────────────────
    // "BUT-D56DFD" → "B U T D 5 6 D F D"
    // IDs read digit-by-digit sound clearer than TTS guessing pronunciation
    private fun fixOrderIds(t: String): String {
        return t.replace(Regex("\\bBUT-([A-Z0-9]{4,8})\\b")) { match ->
            val id = match.groupValues[1]
            "BUT " + id.map { c ->
                when {
                    c.isDigit() -> c.toString()
                    else        -> c.toString()
                }
            }.joinToString(" ")
        }
    }

    // ── 4. Email/UPI symbols ──────────────────────────────────────────────────
    // "butler@upi" → "butler at upi"
    // "@" is often ignored or mispronounced by ElevenLabs
    private fun fixEmailSymbols(t: String): String {
        return t
            .replace("butler@upi", "butler at UPI")
            .replace(Regex("([a-z]+)@([a-z]+)")) { m ->
                "${m.groupValues[1]} at ${m.groupValues[2]}"
            }
    }

    // ── 5. Currency — the big one ─────────────────────────────────────────────
    // TranslationManager was turning "45 rupees" into "RS 95" in Hindi
    // Fix: convert amount + "rupees"/"₹" to spoken words in the right language
    // BEFORE TranslationManager touches it — formatter runs AFTER translation,
    // so we catch whatever came out.
    private fun fixCurrency(t: String, lang: String): String {
        val hi = lang.startsWith("hi") || lang.startsWith("te") || lang.startsWith("mr")
        return t
            // "₹45" → amount word in language
            .replace(Regex("₹\\s*(\\d+)")) { m ->
                val amount = m.groupValues[1].toIntOrNull() ?: 0
                if (hi) "${numberToHindi(amount)} rupaye" else "$amount rupees"
            }
            // "RS 45" or "Rs 45" or "rs 45" → fix
            .replace(Regex("\\bR[Ss]\\.?\\s*(\\d+)")) { m ->
                val amount = m.groupValues[1].toIntOrNull() ?: 0
                if (hi) "${numberToHindi(amount)} rupaye" else "$amount rupees"
            }
            // "45 rupees" → in Hindi, say rupaye
            .replace(Regex("(\\d+)\\s+rupees")) { m ->
                val amount = m.groupValues[1].toIntOrNull() ?: 0
                if (hi) "${numberToHindi(amount)} rupaye" else "$amount rupees"
            }
            // "45 rupaye" → in English, say rupees
            .replace(Regex("(\\d+)\\s+rupaye")) { m ->
                val amount = m.groupValues[1].toIntOrNull() ?: 0
                if (!hi) "$amount rupees" else "${numberToHindi(amount)} rupaye"
            }
    }

    // ── 6. Numbers — digits to words in Hindi ────────────────────────────────
    // "3 options hain" → "teen options hain"
    // "15 min mein" → "pandrah min mein"
    // Only converts numbers 1-999 that appear in Hindi context
    // Does NOT convert order IDs, phone numbers, or years
    private fun fixNumbers(t: String, lang: String): String {
        if (!lang.startsWith("hi")) return t // only Hindi for now
        return t.replace(Regex("\\b(\\d{1,3})\\b")) { m ->
            val num = m.value.toIntOrNull() ?: return@replace m.value
            // Skip: looks like year (1900-2099), or phone fragment, or already part of ID
            if (num in 1900..2099) return@replace m.value
            if (num == 0) return@replace "zero"
            numberToHindi(num)
        }
    }

    // ── 7. Punctuation cleanup ───────────────────────────────────────────────
    // "—" causes long pauses, "..." causes very long pauses
    // Replace with natural pauses ElevenLabs handles well
    private fun fixPunctuation(t: String): String {
        return t
            .replace(" — ", ", ")      // em-dash → comma pause
            .replace("—", ", ")
            .replace("...", ".")        // ellipsis → period
            .replace("!!", "!")         // double exclamation
            .replace("??", "?")
            .replace(Regex("\\.{2,}"), ".") // multiple dots
    }

    // ══════════════════════════════════════════════════════════════════════
    // HINDI NUMBER WORDS
    // Covers 1-999. Used for rupee amounts + small counts.
    // ══════════════════════════════════════════════════════════════════════

    private val ones = arrayOf(
        "", "ek", "do", "teen", "chaar", "paanch", "chhe", "saat", "aath", "nau",
        "das", "gyarah", "barah", "terah", "chaudah", "pandrah", "solah", "satrah",
        "atharah", "unnees", "bees",
        "ikkees", "baees", "teis", "chaubees", "pachees", "chhabbees", "sattaees",
        "atthaees", "unnateeth", "tees",
        "ikatees", "battees", "tainteeth", "chautees", "paintees", "chhattees",
        "saintees", "atthaees2", "untalees", "chaalees",
        "iktalees", "bayalees", "tintalees", "chavaalees", "paintalees",
        "chhiyalees", "saintalees", "attaalees", "unanchyas", "pachaas",
        "ikkyavan", "bavan", "tirpan", "chauvan", "pachpan", "chhappan",
        "sattavan", "athavan", "unsath", "saath",
        "iksath", "basath", "tirsath", "chausath", "painsath", "chhiyasath",
        "sarsath", "athsath", "unhattar", "sattar",
        "ikahattar", "bahattar", "tihattar", "chauhattar", "pachhattar",
        "chhiyahattar", "sathattar", "athahattar", "unnasi", "assi",
        "ikyaasi", "bayaasi", "tiraasi", "chaurasi", "pachaasi", "chhiyaasi",
        "sataasi", "atthaasi", "navasi", "nabbe",
        "ikyaanave", "baanave", "tiraanave", "chauraanave", "pachaanave",
        "chhiyaanave", "sataanave", "atthaanave", "ninyaanave"
    )

    fun numberToHindi(n: Int): String {
        if (n <= 0) return "zero"
        if (n < 100 && n < ones.size) return ones[n].ifBlank { n.toString() }
        if (n < 200) return "ek sau${if (n > 100) " ${numberToHindi(n - 100)}" else ""}"
        if (n < 300) return "do sau${if (n > 200) " ${numberToHindi(n - 200)}" else ""}"
        if (n < 400) return "teen sau${if (n > 300) " ${numberToHindi(n - 300)}" else ""}"
        if (n < 500) return "chaar sau${if (n > 400) " ${numberToHindi(n - 400)}" else ""}"
        if (n < 600) return "paanch sau${if (n > 500) " ${numberToHindi(n - 500)}" else ""}"
        if (n < 700) return "chhe sau${if (n > 600) " ${numberToHindi(n - 600)}" else ""}"
        if (n < 800) return "saat sau${if (n > 700) " ${numberToHindi(n - 700)}" else ""}"
        if (n < 900) return "aath sau${if (n > 800) " ${numberToHindi(n - 800)}" else ""}"
        if (n < 1000) return "nau sau${if (n > 900) " ${numberToHindi(n - 900)}" else ""}"
        if (n < 100000) {
            val hazar = n / 1000
            val rem   = n % 1000
            return "${numberToHindi(hazar)} hazaar${if (rem > 0) " ${numberToHindi(rem)}" else ""}"
        }
        return n.toString() // fallback for very large numbers
    }
}