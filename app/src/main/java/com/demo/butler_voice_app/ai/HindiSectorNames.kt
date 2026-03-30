package com.demo.butler_voice_app.ai

// ══════════════════════════════════════════════════════════════════════════════
// HindiSectorNames
//
// FIX: "Finding Plumber आपके पास" — English sector name leaks into Hindi
//
// Usage in ServiceVoiceHandler / IndianLanguageProcessor:
//   val sectorName = HindiSectorNames.get(sector, language)
//   speak("Finding $sectorName near you.")
//
// This gives "Finding प्लंबर near you." → TranslationCache translates the
// whole string → "आपके पास प्लंबर ढूंढ रहे हैं।"  ✓
// ══════════════════════════════════════════════════════════════════════════════

object HindiSectorNames {

    private val hi = mapOf(
        "PLUMBER"      to "प्लंबर",
        "ELECTRICIAN"  to "इलेक्ट्रीशियन",
        "DOCTOR"       to "डॉक्टर",
        "MEDICINE"     to "दवाई की दुकान",
        "CLEANING"     to "सफाई सेवा",
        "CARPENTER"    to "बढ़ई",
        "AC_REPAIR"    to "एसी मरम्मत",
        "SALON"        to "सैलून",
        "PEST_CONTROL" to "कीट नियंत्रण",
        "TAXI"         to "टैक्सी",
        "FOOD"         to "खाना",
        "AMBULANCE"    to "एम्बुलेंस",
        "PAINTER"      to "पेंटर",
        "TUTOR"        to "ट्यूटर",
        "COURIER"      to "कूरियर",
        "PANDIT"       to "पंडित"
    )

    private val te = mapOf(
        "PLUMBER"      to "ప్లంబర్",
        "ELECTRICIAN"  to "ఎలక్ట్రీషియన్",
        "DOCTOR"       to "డాక్టర్",
        "MEDICINE"     to "మందుల దుకాణం",
        "CLEANING"     to "శుభ్రత సేవ",
        "CARPENTER"    to "వడ్రంగి",
        "TAXI"         to "టాక్సీ",
        "AMBULANCE"    to "అంబులెన్స్"
    )

    private val mr = mapOf(
        "PLUMBER"      to "प्लंबर",
        "ELECTRICIAN"  to "इलेक्ट्रिशियन",
        "DOCTOR"       to "डॉक्टर",
        "MEDICINE"     to "औषधालय",
        "TAXI"         to "टॅक्सी"
    )

    fun get(sectorName: String, language: String): String {
        val map = when {
            language.startsWith("hi") || language.startsWith("mr-") -> hi
            language.startsWith("mr") -> mr
            language.startsWith("te") -> te
            else -> return toDisplayName(sectorName)   // English fallback
        }
        return map[sectorName.uppercase()] ?: toDisplayName(sectorName)
    }

    // "AC_REPAIR" → "AC Repair"
    private fun toDisplayName(s: String) =
        s.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
}