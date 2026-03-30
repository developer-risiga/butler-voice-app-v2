package com.demo.butler_voice_app.utils

object ButlerPhraseBank {

    private val phrases = mapOf(

        "ask_item" to mapOf(
            "en" to listOf("batao kya chahiye?", "kya lena hai aaj?", "kya mangwaoon?", "bolo, kya order karein?"),
            "hi" to listOf("क्या चाहिए?", "बताइए, क्या लेना है?", "क्या मंगवाऊँ?"),
            "te" to listOf("ఏమి కావాలి?", "చెప్పండి, ఏమి తీసుకోవాలి?")
        ),

        "ask_quantity" to mapOf(
            "en" to listOf("kitna chahiye?", "kitne loge?", "quantity batao"),
            "hi" to listOf("कितना चाहिए?", "कितने किलो?", "मात्रा बताइए"),
            "te" to listOf("ఎంత కావాలి?", "పరిమాణం చెప్పండి")
        ),

        "ask_more" to mapOf(
            "en" to listOf("aur kuch?", "kuch aur chahiye?", "bas itna ya kuch aur?", "aur kya mangwaoon?"),
            "hi" to listOf("और कुछ?", "कुछ और चाहिए?", "बस इतना?"),
            "te" to listOf("ఇంకా ఏమైనా?", "అంతేనా?")
        ),

        "confirm_order" to mapOf(
            "en" to listOf("order karon?", "pakka?", "place karoon?", "confirm kar doon?"),
            "hi" to listOf("ऑर्डर करूँ?", "पक्का?", "confirm कर दूँ?"),
            "te" to listOf("ఆర్డర్ చేయనా?", "నిర్ధారించనా?")
        ),

        "order_placed" to mapOf(
            "en" to listOf("done! aa raha hai.", "perfect, order lag gaya!", "haan, order ho gaya!"),
            "hi" to listOf("हाँ, ऑर्डर हो गया!", "बढ़िया, रास्ते में है।", "ऑर्डर लग गया!"),
            "te" to listOf("అయింది! వస్తోంది.", "ఆర్డర్ పెట్టాను!")
        ),

        "added_item" to mapOf(
            "en" to listOf("haan, add ho gaya.", "done,", "perfect,"),
            "hi" to listOf("हाँ, जुड़ गया।", "बढ़िया,", "हो गया,"),
            "te" to listOf("అయింది,", "జోడించాను,")
        )
    )

    fun get(key: String, lang: String): String {
        val langKey = when {
            lang.startsWith("hi") -> "hi"
            lang.startsWith("te") -> "te"
            else -> "en"
        }
        return (phrases[key]?.get(langKey) ?: phrases[key]?.get("en") ?: listOf(key)).random()
    }
}

