package com.demo.butler_voice_app.utils

/**
 * All scripted Butler phrases, with random variants per language.
 * Use get(key, lang) anywhere — always returns a natural string.
 */
object ButlerPhraseBank {

    private val phrases = mapOf(

        // ── Ordering ──────────────────────────────────────────────────────────

        "ask_item" to mapOf(
            "en" to listOf(
                "batao kya chahiye?",
                "kya lena hai aaj?",
                "kya mangwaoon?",
                "bolo, kya order karein?",
                "aur kya chahiye?"
            ),
            "hi" to listOf(
                "क्या चाहिए?",
                "बताइए, क्या लेना है?",
                "क्या मंगवाऊँ?",
                "कुछ और?"
            ),
            "te" to listOf(
                "ఏమి కావాలి?",
                "చెప్పండి, ఏమి తీసుకోవాలి?",
                "ఇంకా ఏమైనా?"
            )
        ),

        "ask_quantity" to mapOf(
            "en" to listOf(
                "kitna chahiye?",
                "kitne loge?",
                "quantity batao — ek kilo, do kilo?",
                "ek ya do?"
            ),
            "hi" to listOf(
                "कितना चाहिए?",
                "कितने किलो?",
                "मात्रा बताइए"
            ),
            "te" to listOf(
                "ఎంత కావాలి?",
                "పరిమాణం చెప్పండి"
            )
        ),

        "ask_more" to mapOf(
            "en" to listOf(
                "aur kuch?",
                "kuch aur chahiye?",
                "bas itna ya kuch aur?",
                "aur kya mangwaoon?"
            ),
            "hi" to listOf(
                "और कुछ?",
                "कुछ और चाहिए?",
                "बस इतना?",
                "और क्या लेना है?"
            ),
            "te" to listOf(
                "ఇంకా ఏమైనా?",
                "అంతేనా?",
                "ఇంకేమైనా కావాలా?"
            )
        ),

        "added_item" to mapOf(
            "en" to listOf("haan, add ho gaya.", "done,", "perfect,", "ho gaya,"),
            "hi" to listOf("हाँ, जुड़ गया।", "बढ़िया,", "हो गया,"),
            "te" to listOf("అయింది,", "జోడించాను,", "సరే,")
        ),

        "confirm_order" to mapOf(
            "en" to listOf(
                "order karon?",
                "pakka?",
                "place karoon?",
                "confirm kar doon?",
                "theek hai, order lagaoon?"
            ),
            "hi" to listOf(
                "ऑर्डर करूँ?",
                "पक्का?",
                "confirm कर दूँ?",
                "लगा दूँ?"
            ),
            "te" to listOf(
                "ఆర్డర్ చేయనా?",
                "నిర్ధారించనా?",
                "పెట్టనా?"
            )
        ),

        "order_placed" to mapOf(
            "en" to listOf(
                "done! aa raha hai.",
                "perfect, order lag gaya!",
                "haan, order ho gaya!",
                "bilkul! raste mein hai."
            ),
            "hi" to listOf(
                "हाँ, ऑर्डर हो गया!",
                "बढ़िया, रास्ते में है।",
                "ऑर्डर लग गया!",
                "बिल्कुल! चल पड़ा।"
            ),
            "te" to listOf(
                "అయింది! వస్తోంది.",
                "ఆర్డర్ పెట్టాను!",
                "సరే! దారిలో ఉంది."
            )
        ),

        // ── Payment ───────────────────────────────────────────────────────────

        "ask_payment" to mapOf(
            "en" to listOf(
                "card, UPI, ya QR — kaise pay karein?",
                "payment kaise karoge? card, UPI, ya QR?",
                "kya prefer karoge — card, UPI, ya QR scan?"
            ),
            "hi" to listOf(
                "कार्ड, UPI, या QR — कैसे पे करें?",
                "पेमेंट कैसे करोगे?",
                "क्या prefer करोगे — कार्ड, UPI, या QR?"
            ),
            "te" to listOf(
                "కార్డ్, UPI, లేదా QR — ఎలా చెల్లిస్తారు?",
                "పేమెంట్ ఎలా చేయాలి?"
            )
        ),

        "payment_done" to mapOf(
            "en" to listOf("perfect! order place kar raha hoon.", "done! placing order.", "great, lagata hoon."),
            "hi" to listOf("परफेक्ट! ऑर्डर लगा रहा हूँ।", "हो गया! ऑर्डर डाल रहा हूँ।"),
            "te" to listOf("పెర్ఫెక్ట్! ఆర్డర్ పెడుతున్నాను.", "సరే! ఆర్డర్ చేస్తున్నాను.")
        ),

        "payment_confirm_ask" to mapOf(
            "en" to listOf(
                "payment ho gayi? haan ya nahi bolein.",
                "payment complete hua? bolo.",
                "pay kar diya? haan ya nahi?"
            ),
            "hi" to listOf(
                "पेमेंट हो गई? हाँ या नहीं बोलें।",
                "पेमेंट complete हुई? बताइए।"
            ),
            "te" to listOf(
                "పేమెంట్ అయిందా? అవును లేదా కాదు చెప్పండి.",
                "చెల్లించారా?"
            )
        ),

        // ── Service ───────────────────────────────────────────────────────────

        "ask_service_type" to mapOf(
            "en" to listOf(
                "which service do you need? plumber, electrician, doctor, food, medicine, taxi, or any other?",
                "kaunsi service chahiye? bolo."
            ),
            "hi" to listOf(
                "कौन सी सेवा चाहिए? प्लम्बर, इलेक्ट्रीशियन, डॉक्टर, खाना, दवाई, टैक्सी — कोई भी?",
                "क्या सर्विस चाहिए? बताइए।"
            ),
            "te" to listOf(
                "ఏ సేవ కావాలి? ప్లంబర్, ఎలక్ట్రీషియన్, డాక్టర్, తినుబండారాలు, మందులు?",
                "ఏ సర్వీస్ కావాలో చెప్పండి."
            )
        ),

        "service_booking_confirm" to mapOf(
            "en" to listOf(
                "booking confirm ho gayi!",
                "booked! aa jayenge jaldi.",
                "done, booking ho gayi."
            ),
            "hi" to listOf(
                "बुकिंग हो गई!",
                "बुक हो गया! जल्दी आएंगे।",
                "हो गया, बुकिंग confirm।"
            ),
            "te" to listOf(
                "బుకింగ్ అయింది!",
                "బుక్ చేశాను! త్వరలో వస్తారు."
            )
        ),

        "service_subtype_not_understood" to mapOf(
            "en" to listOf(
                "sorry, didn't get that. which type?",
                "say it again — which one?",
                "which service type specifically?"
            ),
            "hi" to listOf(
                "समझा नहीं। कौन सा type?",
                "फिर से बोलो — कौन सा?",
                "कौन सी service specifically?"
            ),
            "te" to listOf(
                "అర్థం కాలేదు. ఏ రకం?",
                "మళ్ళీ చెప్పండి — ఏది?"
            )
        ),

        "service_when" to mapOf(
            "en" to listOf(
                "when do you need it? today, tomorrow, or another day?",
                "kab chahiye? aaj, kal, ya koi aur din?"
            ),
            "hi" to listOf(
                "कब चाहिए? आज, कल, या कोई और दिन?",
                "कब आना चाहिए?"
            ),
            "te" to listOf(
                "ఎప్పుడు కావాలి? ఈరోజు, రేపు?",
                "ఎప్పుడు రావాలి?"
            )
        ),

        // ── Status / History ──────────────────────────────────────────────────

        "no_orders" to mapOf(
            "en" to listOf(
                "koi order nahi hai abhi tak. kuch order karein?",
                "no orders yet. kya mangwaoon?"
            ),
            "hi" to listOf(
                "अभी तक कोई ऑर्डर नहीं है। कुछ ऑर्डर करें?",
                "कोई पुराना ऑर्डर नहीं।"
            ),
            "te" to listOf(
                "ఇంతవరకు ఆర్డర్లు లేవు. ఏదైనా ఆర్డర్ చేయాలా?",
                "ఆర్డర్లు లేవు."
            )
        ),

        "error_retry" to mapOf(
            "en" to listOf(
                "network issue. retry karein? haan ya nahi.",
                "something went wrong. try again?",
                "oops, problem hua. dobara try karein?"
            ),
            "hi" to listOf(
                "नेटवर्क issue। फिर try करें? हाँ या नहीं।",
                "कुछ गड़बड़ हुई। दोबारा try करें?"
            ),
            "te" to listOf(
                "నెట్‌వర్క్ సమస్య. మళ్ళీ ప్రయత్నించాలా?",
                "ఏదో తప్పు జరిగింది. మళ్ళీ చేయాలా?"
            )
        )
    )

    /**
     * Get a random phrase for the given key and language.
     * Falls back to English if the language isn't available.
     * Falls back to the key itself if the key doesn't exist.
     */
    fun get(key: String, lang: String): String {
        val base = lang.substringBefore("-")
        val langMap = phrases[key] ?: return key
        return (langMap[base] ?: langMap["en"] ?: listOf(key)).random()
    }
}