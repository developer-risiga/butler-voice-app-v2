package com.demo.butler_voice_app.utils

/**
 * ButlerPhraseBank — scripted Butler phrases for all major Indian languages.
 * Languages: EN · HI · TE · TA · KN · ML · PA · GU · MR
 *
 * Usage:
 *   ButlerPhraseBank.get("ask_item", "te")   → Telugu random variant
 *   ButlerPhraseBank.get("ask_more",  "kn")  → Kannada random variant
 */
object ButlerPhraseBank {

    private val phrases: Map<String, Map<String, List<String>>> = mapOf(

        // ── Ordering ──────────────────────────────────────────────────────────

        "ask_item" to mapOf(
            "en" to listOf("What would you like?", "Tell me, what do you need?", "What shall I order?", "Go ahead, say the item."),
            "hi" to listOf("क्या चाहिए?", "बताइए, क्या लेना है?", "क्या मंगवाऊँ?", "कुछ और?"),
            "hg" to listOf("Kya chahiye?", "Batao kya mangwaaoon?", "Kya order karein?", "Bolo kya laana hai."),
            "te" to listOf("ఏమి కావాలి?", "చెప్పండి, ఏమి తీసుకోవాలి?", "ఇంకా ఏమైనా?", "ఏమి ఆర్డర్ చేయాలి?"),
            "ta" to listOf("என்ன வேணும்?", "சொல்லுங்க, என்ன வாங்கணும்?", "இன்னும் என்னாவது?"),
            "kn" to listOf("ಏನು ಬೇಕು?", "ಹೇಳಿ, ಏನು ತರಬೇಕು?", "ಇನ್ನೇನಾದರೂ?"),
            "ml" to listOf("എന്ത് വേണം?", "പറയൂ, എന്ത് വേണം?", "ഇനി എന്തെങ്കിലും?"),
            "pa" to listOf("ਕੀ ਚਾਹੀਦਾ?", "ਦੱਸੋ, ਕੀ ਲੈਣਾ ਹੈ?", "ਕੁਝ ਹੋਰ?"),
            "gu" to listOf("Shu joiye?", "Kaho, shu mangavun?", "Kahi biju?"),
            "mr" to listOf("काय हवं?", "सांगा, काय घ्यायचं?", "आणखी काही?")
        ),

        "ask_quantity" to mapOf(
            "en" to listOf("How much do you need?", "What quantity? One kg, two kg?", "One or two?"),
            "hi" to listOf("कितना चाहिए?", "कितने किलो?", "एक या दो?", "मात्रा बताइए।"),
            "hg" to listOf("Kitna chahiye?", "Kitne kilo?", "Ek ya do?"),
            "te" to listOf("ఎంత కావాలి?", "ఎంత కిలో?", "పరిమాణం చెప్పండి."),
            "ta" to listOf("எவ்வளவு வேணும்?", "எத்தனை கிலோ?", "ஒன்னா, ரெண்டா?"),
            "kn" to listOf("ಎಷ್ಟು ಬೇಕು?", "ಎಷ್ಟು ಕೆಜಿ?", "ಒಂದು ಅಥವಾ ಎರಡು?"),
            "ml" to listOf("എത്ര വേണം?", "ഒരു കിലോ, ഒരു ലിറ്റർ?", "എത്ര?"),
            "pa" to listOf("ਕਿੰਨਾ ਚਾਹੀਦਾ?", "ਕਿੰਨੇ ਕਿਲੋ?", "ਇੱਕ ਜਾਂ ਦੋ?"),
            "gu" to listOf("Ketlun joiye?", "Ketla kilo?", "Ek ke be?"),
            "mr" to listOf("किती हवं?", "किती किलो?", "एक की दोन?")
        ),

        "ask_more" to mapOf(
            "en" to listOf("Anything else?", "What else do you need?", "More?", "Shall I add anything else?", "That all?"),
            "hi" to listOf("और कुछ?", "कुछ और चाहिए?", "बस इतना?", "और क्या लेना है?"),
            "hg" to listOf("Aur kuch?", "Kuch aur lena hai?", "Bas itna?", "Aur kya lena hai?"),
            "te" to listOf("ఇంకా ఏమైనా?", "అంతేనా?", "ఇంకేమైనా కావాలా?", "మరేమైనా?"),
            "ta" to listOf("வேற ஏதாவது?", "அவ்வளவுதானா?", "இன்னும் ஏதாவது?"),
            "kn" to listOf("ಇನ್ನೇನಾದರೂ?", "ಅಷ್ಟೇನಾ?", "ಇನ್ನೂ ಏನಾದರೂ ಬೇಕೇ?"),
            "ml" to listOf("ഇനി എന്തെങ്കിലും?", "അത്രയേ ഉള്ളൂ?", "ഇനി വേറേ?"),
            "pa" to listOf("ਕੁਝ ਹੋਰ?", "ਬੱਸ ਇੰਨਾ?", "ਹੋਰ ਕੀ ਚਾਹੀਦਾ?"),
            "gu" to listOf("Biju khai?", "Bas atle j?", "Kahi biju joiye?"),
            "mr" to listOf("आणखी काही?", "बस एवढंच?", "आणखी काय हवं?")
        ),

        "added_item" to mapOf(
            "en" to listOf("Done,", "Got it,", "Added,", "Perfect,"),
            "hi" to listOf("Add krdiya।", "Cart mein add ho gaya।", "Ho gaya,"),
            "hg" to listOf("Add krdiya,", "Done,", "Ho gaya,"),
            "te" to listOf("అయింది,", "జోడించాను,", "సరే,"),
            "ta" to listOf("சேர்த்தாச்சு,", "சரி,", "ஆச்சு,"),
            "kn" to listOf("ಸೇರಿಸಿದೆ,", "ಸರಿ,", "ಆಯ್ತು,"),
            "ml" to listOf("ചേർത്തു,", "ശരി,", "ആയി,"),
            "pa" to listOf("ਪਾ ਦਿੱਤਾ,", "ਠੀਕ,", "ਹੋ ਗਿਆ,"),
            "gu" to listOf("Nakhi didhu,", "Thayun,", "Saru,"),
            "mr" to listOf("टाकलं,", "ठीक,", "झालं,")
        ),

        "confirm_order" to mapOf(
            "en" to listOf("Shall I order?", "Confirm it?", "Place this order?", "Go ahead?"),
            "hi" to listOf("Order krna hai?", "Confirm krna hai?", "Order lgaana hai?"),
            "hg" to listOf("Order krna hai?", "Confirm krna hai?", "Order lgaana hai?"),
            "te" to listOf("ఆర్డర్ చేయనా?", "నిర్ధారించనా?", "పెట్టనా?"),
            "ta" to listOf("ஆர்டர் பண்ணட்டுமா?", "சரிதானா?", "பண்ணட்டுமா?"),
            "kn" to listOf("ಆರ್ಡರ್ ಮಾಡಲೇ?", "ಸರಿ ಮಾಡಲೇ?", "ಆರ್ಡರ್ ಹಾಕಲೇ?"),
            "ml" to listOf("ഓർഡർ ചെയ്യട്ടേ?", "ചേർക്കട്ടേ?", "ഒക്കെ ആ?"),
            "pa" to listOf("ਆਰਡਰ ਕਰਾਂ?", "ਪੱਕਾ?", "ਲਾਵਾਂ?"),
            "gu" to listOf("Order karish?", "Confirm karu?", "Thaiy?"),
            "mr" to listOf("ऑर्डर करू?", "पक्कं?", "टाकू का?")
        ),

        "order_placed" to mapOf(
            "en" to listOf("Order placed!", "Done, it's on the way!", "Confirmed, coming soon!"),
            "hi" to listOf("Order Place ho gaya!", "Order aap tak pahoch jaayega.", "Order confirm ho gaya!"),
            "hg" to listOf("Order Place ho gaya!", "Order aap tak pahoch jaayega.", "Order confirm ho gaya!"),
            "te" to listOf("ఆర్డర్ పెట్టాను!", "అయింది! వస్తోంది.", "సరే! దారిలో ఉంది."),
            "ta" to listOf("ஆர்டர் ஆச்சு!", "வருது!", "சரி! வழியில இருக்கு."),
            "kn" to listOf("ಆರ್ಡರ್ ಆಯ್ತು!", "ಬರ್ತಾ ಇದೆ!", "ಸರಿ! ದಾರಿಯಲ್ಲಿ ಇದೆ."),
            "ml" to listOf("ഓർഡർ ആയി!", "വരുന്നുണ്ട്!", "ശരി! വഴിയിലുണ്ട്."),
            "pa" to listOf("ਆਰਡਰ ਹੋ ਗਿਆ!", "ਆਉਂਦਾ ਹੈ!", "ਰਾਹ ਵਿੱਚ ਹੈ!"),
            "gu" to listOf("Order thai gayu!", "Aave chhe!", "Saru! Rastemaa chhe."),
            "mr" to listOf("ऑर्डर झालं!", "येतंय!", "ठीक! वाटेत आहे.")
        ),

        // ── Payment ───────────────────────────────────────────────────────────

        "ask_payment" to mapOf(
            "en" to listOf("UPI or card?", "How would you like to pay? UPI, card, or QR?"),
            "hi" to listOf("UPI से दोगे या card से?", "पेमेंट कैसे? UPI, card या QR?"),
            "hg" to listOf("UPI ya card?", "Payment kaise karoge?"),
            "te" to listOf("UPI istara leda card?", "ఎలా పేమెంట్ చేయాలి?"),
            "ta" to listOf("UPI la kuduppeenga, card la?", "Payment எப்படி?"),
            "kn" to listOf("UPI na card?", "Hege pay madali?"),
            "ml" to listOf("UPI aano card aano?", "Payment engane?"),
            "pa" to listOf("UPI de ke card de?", "Kiven paisa denaa?"),
            "gu" to listOf("UPI ke card?", "Kem pay karish?"),
            "mr" to listOf("UPI की कार्ड?", "पेमेंट कसं करायचं?")
        ),

        "payment_done" to mapOf(
            "en" to listOf("Got it, placing your order.", "Done! Placing order.", "Received, ordering now."),
            "hi" to listOf("अच्छा, order डाल रहा हूँ।", "हो गया! Order दे रहा हूँ।"),
            "hg" to listOf("Done! Order place kar raha hoon.", "Theek hai, order lagata hoon."),
            "te" to listOf("సరే, ఆర్డర్ పెడుతున్నాను.", "అయింది, order చేస్తున్నాను."),
            "ta" to listOf("சரி, ஆர்டர் பண்றேன்.", "ஆச்சு, order போடுறேன்."),
            "kn" to listOf("Sari, order maduttiddeeni.", "Aaytu, order hakuttiddeeni."),
            "ml" to listOf("Sari, order cheyyunnu.", "Ayi, order ithaa."),
            "pa" to listOf("Theek, order paa raha hoon.", "Ho gaya, order laanda hoon."),
            "gu" to listOf("Saru, order karun chhu.", "Thai gayu, order de chhu."),
            "mr" to listOf("ठीक, ऑर्डर देतो.", "झालं, ऑर्डर टाकतो.")
        ),

        "payment_confirm_ask" to mapOf(
            "en" to listOf("Payment done? Say yes or no.", "Did you complete the payment?"),
            "hi" to listOf("पेमेंट हो गई? हाँ या नहीं बोलें।", "पेमेंट complete हुई?"),
            "hg" to listOf("Payment ho gayi? Haan ya nahi.", "Payment complete hua?"),
            "te" to listOf("పేమెంట్ అయిందా? అవును లేదా కాదు.", "చెల్లించారా?"),
            "ta" to listOf("Payment ஆச்சா? ஆம் அல்லது இல்லை.", "Pay பண்ணீங்களா?"),
            "kn" to listOf("Payment aytaa? Howdu alla haeli.", "Payment madidiraa?"),
            "ml" to listOf("Payment aayoo? Athe alle parayo.", "Pay cheyythoo?"),
            "pa" to listOf("Payment ho gayi? Haan ya nahi.", "Pay kar ditta?"),
            "gu" to listOf("Payment thai? Ha ke na.", "Payment karyu?"),
            "mr" to listOf("पेमेंट झालं? हो किंवा नाही.", "पैसे भरलेस का?")
        ),

        // ── Service ───────────────────────────────────────────────────────────

        "ask_service_type" to mapOf(
            "en" to listOf("Which service? Plumber, electrician, doctor, taxi, or any other?"),
            "hi" to listOf("कौन सी सर्विस? प्लम्बर, डॉक्टर, taxi, खाना, दवाई?", "क्या सर्विस चाहिए?"),
            "hg" to listOf("Kaun si service? Plumber, electrician, doctor, taxi?"),
            "te" to listOf("ఏ సేవ కావాలి? ప్లంబర్, డాక్టర్, taxi?", "ఏ సర్వీస్?"),
            "ta" to listOf("என்ன சர்வீஸ்? பிளம்பர், டாக்டர், taxi?"),
            "kn" to listOf("Yavudu service? Plumber, doctor, taxi?", "Etha service beku?"),
            "ml" to listOf("Etha service? Plumber, doctor, taxi?", "Etha service veno?"),
            "pa" to listOf("ਕਿਹੜੀ ਸੇਵਾ? ਪਲੰਬਰ, ਡਾਕਟਰ, taxi?"),
            "gu" to listOf("Kevi service? Plumber, doctor, taxi?"),
            "mr" to listOf("कोणती सेवा? प्लंबर, डॉक्टर, टैक्सी?")
        ),

        "service_booking_confirm" to mapOf(
            "en" to listOf("Booking confirmed!", "Booked! They'll be there soon.", "Done, booking confirmed."),
            "hi" to listOf("बुकिंग हो गई!", "बुक हो गया! जल्दी आएंगे।", "हो गया, booking confirm।"),
            "hg" to listOf("Booking ho gayi!", "Booked! Jaldi aayenge."),
            "te" to listOf("బుకింగ్ అయింది!", "బుక్ చేశాను! త్వరలో వస్తారు."),
            "ta" to listOf("Booking ஆச்சு!", "Book ஆச்சு! சீக்கிரம் வருவாங்க."),
            "kn" to listOf("Booking aaytu!", "Book aaytu! Bega bartaare."),
            "ml" to listOf("Booking ayi!", "Book cheythu! Udan etum."),
            "pa" to listOf("Booking ho gayi!", "Booked! Jaldi aao."),
            "gu" to listOf("Booking thai gayu!", "Book thai gayu! Jaldi aavse."),
            "mr" to listOf("बुकिंग झाली!", "बुक झालं! लवकर येतील.")
        ),

        "service_when" to mapOf(
            "en" to listOf("When do you need it? Today, tomorrow, or another day?", "Preferred time?"),
            "hi" to listOf("कब चाहिए? आज, कल, या कोई और दिन?", "कब आना चाहिए?"),
            "hg" to listOf("Kab chahiye? Aaj, kal, ya koi aur din?"),
            "te" to listOf("ఎప్పుడు కావాలి? ఈరోజు, రేపు?", "ఎప్పుడు రావాలి?"),
            "ta" to listOf("எப்போ வேணும்? இன்னைக்கா, நாளைக்கா?"),
            "kn" to listOf("Yaavaga beku? Idde, naale?", "Yenu samaya?"),
            "ml" to listOf("Eppol veno? Innu, naale?", "Yethu samayam?"),
            "pa" to listOf("Kaddon chahida? Aaj, kal?", "Kidon da time?"),
            "gu" to listOf("Kyare joie? Aaj, kaal?", "Kayo time?"),
            "mr" to listOf("केव्हा हवं? आज, उद्या?", "वेळ कधी?")
        ),

        // ── Status / History ──────────────────────────────────────────────────

        "no_orders" to mapOf(
            "en" to listOf("No orders yet. Want to order something?", "No order history. What shall I get?"),
            "hi" to listOf("अभी तक कोई order नहीं। कुछ order करें?", "कोई पुराना order नहीं।"),
            "hg" to listOf("Koi order nahi abhi tak. Kuch order karein?"),
            "te" to listOf("ఇంతవరకు ఆర్డర్లు లేవు. ఏదైనా ఆర్డర్ చేయాలా?"),
            "ta" to listOf("Order history இல்லை. ஏதாவது order பண்ணணுமா?"),
            "kn" to listOf("Yeenu order illa. Yenu order madali?"),
            "ml" to listOf("Order onnumilla. Enthu order cheyyamo?"),
            "pa" to listOf("Koi purana order nahi. Kuch order kariye?"),
            "gu" to listOf("Koi order nathi. Kahi order kariye?"),
            "mr" to listOf("आत्तापर्यंत ऑर्डर नाही. काही ऑर्डर करायचं का?")
        ),

        "error_retry" to mapOf(
            "en" to listOf("Something went wrong. Try again?", "Network issue. Retry?"),
            "hi" to listOf("नेटवर्क issue। फिर try करें? हाँ या नहीं।", "कुछ गड़बड़ हुई। दोबारा?"),
            "hg" to listOf("Network issue. Try karein? Haan ya nahi.", "Kuch problem aayi. Dobara?"),
            "te" to listOf("నెట్‌వర్క్ సమస్య. మళ్ళీ ప్రయత్నించాలా?", "ఏదో తప్పు జరిగింది."),
            "ta" to listOf("Network problem. மறுபடி try பண்ணட்டுமா?"),
            "kn" to listOf("Network problem. Matte try madona?"),
            "ml" to listOf("Network problem. Oru mukham koodi try cheyyamo?"),
            "pa" to listOf("Network problem. Phir try karie?"),
            "gu" to listOf("Network problem. Fari try kariye?"),
            "mr" to listOf("Network problem. पुन्हा try करायचं का?")
        )
    )

    /**
     * Returns a random phrase for the given key and language.
     * Falls back to English if the language variant is not available.
     * Falls back to the key string if the key doesn't exist.
     */
    fun get(key: String, lang: String): String {
        val base    = lang.substringBefore("-").lowercase().take(2)
        val langMap = phrases[key] ?: return key
        return (langMap[base] ?: langMap["en"] ?: listOf(key)).random()
    }

    /**
     * Returns all variants for a key + language (useful for pick() logic in caller).
     */
    fun getAll(key: String, lang: String): List<String> {
        val base    = lang.substringBefore("-").lowercase().take(2)
        val langMap = phrases[key] ?: return listOf(key)
        return langMap[base] ?: langMap["en"] ?: listOf(key)
    }
}