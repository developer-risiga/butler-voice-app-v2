package com.demo.butler_voice_app.ai

import android.util.LruCache

object TranslationCache {
    private val lru = LruCache<String, String>(300)
    private val static = mapOf(
        "Tell me what you want to order.|hi" to "मुझे बताओ तुम क्या ऑर्डर करना चाहते हो।",
        "Tell me what you want to order.|mr" to "मला सांगा तुम्हाला काय ऑर्डर करायचे आहे.",
        "Tell me what you want to order.|te" to "మీరు ఏమి ఆర్డర్ చేయాలనుకుంటున్నారో చెప్పండి.",
        "Sorry, I didn't catch that. Please speak clearly.|hi" to "माफ करें, मैं समझ नहीं पाया। कृपया साफ बोलें।",
        "Sorry, I didn't catch that. Please speak clearly.|mr" to "माफ करा, मला समजले नाही. कृपया स्पष्ट बोला.",
        "Sorry, I didn't catch that. Please speak clearly.|te" to "క్షమించండి, స్పష్టంగా మాట్లాడండి.",
        "Say yes to confirm or no to go back.|hi" to "बुकिंग कन्फर्म करने के लिए हाँ बोलो या वापस के लिए नहीं।",
        "Say yes to confirm or no to go back.|mr" to "बुकिंग साठी हो किंवा परत जाण्यासाठी नाही.",
        "Say yes to confirm or no to go back.|te" to "నిర్ధారించడానికి అవును లేదా వెనక్కి వెళ్ళడానికి కాదు.",
        "Booking confirmed. Thank you!|hi" to "बुकिंग कन्फर्म हो गई। धन्यवाद!",
        "Booking confirmed. Thank you!|mr" to "बुकिंग कन्फर्म झाली. धन्यवाद!",
        "Booking confirmed. Thank you!|te" to "బుకింగ్ నిర్ధారించబడింది. ధన్యవాదాలు!",
        "Please say 1, 2 or 3 to select a provider.|hi" to "कृपया 1, 2 या 3 बोलो।",
        "Please say 1, 2 or 3 to select a provider.|mr" to "कृपया 1, 2 किंवा 3 सांगा.",
        "Please say 1, 2 or 3 to select a provider.|te" to "1, 2 లేదా 3 చెప్పండి.",
    )

    fun get(text: String, lang: String): String {
        if (lang == "en") return text
        val key = "$text|$lang"
        return static[key] ?: lru.get(key) ?: text
    }

    fun put(text: String, lang: String, translated: String) {
        lru.put("$text|$lang", translated)
    }
}