package com.demo.butler_voice_app.utils

/**
 * ButlerSpeechFormatter — pre-processes every string before it goes to TTS.
 *
 * Called once, right before ttsManager.speak(), after TranslationManager.
 *
 * CRITICAL FIX in this version:
 *   OLD fixPunctuation had: .replace("...", ".")
 *   This was REMOVING natural pause cues that ElevenLabs uses for breath/rhythm.
 *   "Haan, batayein Roy... aaj kya chahiye?" needs "..." for the pause after Roy.
 *   Without it, Butler sounds rushed and robotic.
 *
 * ElevenLabs punctuation cues:
 *   ","   → ~150ms breath
 *   "."   → ~300ms pause
 *   "..."  → ~500ms natural pause — MUST BE PRESERVED
 *   "—"   → ~200ms pause (converted to comma)
 *   "?"   → rising inflection + pause
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

    /**
     * Format with emotion-aware text transformation.
     * ElevenLabs responds strongly to punctuation, CAPS, and spacing.
     * These text-level cues reinforce the voice_settings parameters.
     *
     * Call this version instead of format() when tone is known.
     */
    fun formatWithEmotion(text: String, lang: String, tone: com.demo.butler_voice_app.EmotionTone): String {
        var t = format(text, lang)
        t = when (tone) {
            // EXCITED: exclamation adds energy, key words in caps
            com.demo.butler_voice_app.EmotionTone.EXCITED -> {
                t.replace(Regex("[.]\\s*$"), "!")
                    .replace("ho gaya", "ho gaya!")
                    .replace("हो गया", "हो गया!")
                    .replace("perfect", "perfect!")
                    .replace("परफेक्ट", "परफेक्ट!")
            }
            // EMPATHETIC: add pauses between words for slower, gentler delivery
            com.demo.butler_voice_app.EmotionTone.EMPATHETIC -> {
                t.replace(". ", "... ")
                    .replace("boliye", "boliye...")
                    .replace("phir se", "phir se...")
                    .replace("फिर से", "फिर से...")
            }
            // EMERGENCY: ALL CAPS key phrases + fast delivery markers
            com.demo.butler_voice_app.EmotionTone.EMERGENCY -> {
                t.replace("ambulance", "AMBULANCE")
                    .replace("emergency", "EMERGENCY")
                    .replace("घबराइए मत", "घबराइए मत!")
                    .replace(Regex("[!]?\\s*$"), "!!")
            }
            // WARM: ellipsis pauses make delivery feel natural and unhurried
            com.demo.butler_voice_app.EmotionTone.WARM -> t
            // NORMAL: no changes needed, clean delivery
            com.demo.butler_voice_app.EmotionTone.NORMAL -> t
        }
        return t.trim()
    }

    // ── 1. Provider names — strip trailing dashes, double spaces ─────────────
    private fun fixProviderNames(t: String): String {
        return t
            .replace(Regex("\\s+-\\s+[A-Z][a-zA-Z\\s,]+\\.?$"), "")
            .replace(Regex("\\s+-\\s*$"), "")
            .replace(Regex("\\s{2,}"), " ")
    }

    // ── 2. Product casing — ALL CAPS → Title Case ────────────────────────────
    // "DAAWAT BROWN" → "Daawat Brown"
    private fun fixProductCasing(t: String): String {
        return t.replace(Regex("\\b([A-Z]{3,})\\b")) { match ->
            val word = match.value
            if (word in setOf("UPI", "QR", "AC", "ID", "OTP", "ATM", "GST", "EMI", "ITR", "PDF")) {
                word
            } else {
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

    // ── 3. Order IDs — spell them out clearly ────────────────────────────────
    private fun fixOrderIds(t: String): String {
        return t.replace(Regex("\\bBUT-([A-Z0-9]{4,8})\\b")) { match ->
            val id = match.groupValues[1]
            "BUT " + id.map { c -> c.toString() }.joinToString(" ")
        }
    }

    // ── 4. Email/UPI symbols ──────────────────────────────────────────────────
    private fun fixEmailSymbols(t: String): String {
        return t
            .replace("butler@upi", "butler at UPI")
            .replace(Regex("([a-z]+)@([a-z]+)")) { m ->
                "${m.groupValues[1]} at ${m.groupValues[2]}"
            }
    }

    // ── 5. Currency ───────────────────────────────────────────────────────────
    private fun fixCurrency(t: String, lang: String): String {
        val hi = lang.startsWith("hi") || lang.startsWith("te") || lang.startsWith("mr")
        return t
            .replace(Regex("₹\\s*(\\d+)")) { m ->
                val amount = m.groupValues[1].toIntOrNull() ?: 0
                if (hi) "${numberToHindi(amount)} rupaye" else "$amount rupees"
            }
            .replace(Regex("\\bR[Ss]\\.?\\s*(\\d+)")) { m ->
                val amount = m.groupValues[1].toIntOrNull() ?: 0
                if (hi) "${numberToHindi(amount)} rupaye" else "$amount rupees"
            }
            .replace(Regex("(\\d+)\\s+rupees")) { m ->
                val amount = m.groupValues[1].toIntOrNull() ?: 0
                if (hi) "${numberToHindi(amount)} rupaye" else "$amount rupees"
            }
            .replace(Regex("(\\d+)\\s+rupaye")) { m ->
                val amount = m.groupValues[1].toIntOrNull() ?: 0
                if (!hi) "$amount rupees" else "${numberToHindi(amount)} rupaye"
            }
    }

    // ── 6. Numbers — digits to words in Hindi ────────────────────────────────
    private fun fixNumbers(t: String, lang: String): String {
        if (!lang.startsWith("hi")) return t
        return t.replace(Regex("\\b(\\d{1,3})\\b")) { m ->
            val num = m.value.toIntOrNull() ?: return@replace m.value
            if (num in 1900..2099) return@replace m.value
            if (num == 0) return@replace "zero"
            numberToHindi(num)
        }
    }

    // ── 7. Punctuation cleanup ───────────────────────────────────────────────
    // CRITICAL FIX: "..." is PRESERVED — it creates ~500ms natural pause in ElevenLabs.
    // The OLD code had .replace("...", ".") which removed all breathing room from speech.
    // Butler phrases like "Haan, batayein Roy... aaj kya chahiye?" need that pause.
    private fun fixPunctuation(t: String): String {
        return t
            .replace(" — ", ", ")       // em-dash → comma pause (natural)
            .replace("—", ", ")
            // ✅ REMOVED: .replace("...", ".")  ← was killing all natural pauses!
            // "..." is now KEPT — ElevenLabs uses it as a ~500ms breathing pause
            .replace("!!", "!")
            .replace("??", "?")
    }

    // ══════════════════════════════════════════════════════════════════════
    // HINDI NUMBER WORDS (unchanged from original)
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
        return n.toString()
    }
}