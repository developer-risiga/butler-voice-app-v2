package com.demo.butler_voice_app.utils

/**
 * ResponseTemplates — every spoken Butler phrase, all Indian languages.
 *
 * Languages: EN · HI · HINGLISH · TE · ML · KN · TA · PA · GU · MR
 *
 * RULES:
 *  1. Every Hindi string contains ≥1 Devanagari word → TranslationManager skips it.
 *  2. No "ऑर्डर","कार्ट" etc. — TTSManager normalizer handles those.
 *  3. All strings are TTS-ready (no markdown, no special chars except ₹).
 *
 * Usage:
 *   val tts = ResponseTemplates.wakeResponse("Priya", "te")
 *   speak(tts)
 */
object ResponseTemplates {

    // ─────────────────────────────────────────────────────────────
    // 1. WAKE RESPONSE
    // ─────────────────────────────────────────────────────────────
    fun wakeResponse(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "हाँ, बताइए $name… आज आपको क्या चाहिए?"
        lang.startsWith("hg") -> "Haan, batayein $name… aaj aapko kya chahiye?"
        lang.startsWith("te") -> "అవును, చెప్పండి $name… ఈరోజు మీకు ఏమి కావాలి?"
        lang.startsWith("ml") -> "ഹ, പറയൂ $name… ഇന്ന് എന്ത് വേണം?"
        lang.startsWith("kn") -> "ಹೌದು, ಹೇಳಿ $name… ಇಂದು ಏನು ಬೇಕು?"
        lang.startsWith("ta") -> "ஆம், சொல்லுங்கள் $name… இன்னைக்கு என்ன வேணும்?"
        lang.startsWith("pa") -> "ਹਾਂ, ਦੱਸੋ $name… ਅੱਜ ਕੀ ਚਾਹੀਦਾ?"
        lang.startsWith("gu") -> "હા, કહો $name… આજ સ઼ := shu joiye?"
        lang.startsWith("mr") -> "हो, सांगा $name… आज काय हवं?"
        else                   -> "Yes, tell me $name… what do you need today?"
    }

    // ─────────────────────────────────────────────────────────────
    // 2. GREETING / HELP
    // ─────────────────────────────────────────────────────────────
    fun greetingHelp(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "नमस्ते $name! किराना, दवाइयाँ या होम सर्विस — क्या चाहिए?"
        lang.startsWith("hg") -> "Hello $name! Grocery, medicine ya home service — kya chahiye?"
        lang.startsWith("te") -> "నమస్కారం $name! కిరాణా, మందులు లేదా హోమ్ సర్వీస్ — ఏమి కావాలి?"
        lang.startsWith("ml") -> "നമസ്കാരം $name! ഗ്രോസറി, മരുന്ന്, ഹോം സർവീസ് — എന്ത് വേണം?"
        lang.startsWith("kn") -> "ನಮಸ್ಕಾರ $name! ಕಿರಾಣಾ, ಔಷಧಿ ಅಥವಾ ಹೋಮ್ ಸರ್ವೀಸ್ — ಏನು ಬೇಕು?"
        lang.startsWith("ta") -> "வணக்கம் $name! கிரோசரி, மருந்து, ஹோம் சர்வீஸ் — என்ன வேணும்?"
        lang.startsWith("pa") -> "ਸਤ ਸ੍ਰੀ ਅਕਾਲ $name! ਕਿਰਾਣਾ, ਦਵਾਈਆਂ ਜਾਂ ਘਰ ਸੇਵਾ — ਕੀ ਚਾਹੀਦਾ?"
        lang.startsWith("gu") -> "નમસ્તે $name! ગ્રોસરી, દવા અથવા હોમ સર્વિસ — શું જોઈએ?"
        lang.startsWith("mr") -> "नमस्कार $name! किराणा, औषध किंवा होम सर्विस — काय हवं?"
        else                   -> "Hello $name! I can help with groceries, medicines, or home services. What do you need?"
    }

    // ─────────────────────────────────────────────────────────────
    // 3a. ASK TYPE – RICE
    // ─────────────────────────────────────────────────────────────
    fun askTypeRice(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "कौन सा राइस चाहिए $name — बासमती, ब्राउन राइस या नॉर्मल?"
        lang.startsWith("hg") -> "Kaunsa rice chahiye $name — basmati, brown rice ya normal?"
        lang.startsWith("te") -> "$name, ఏ బియ్యం కావాలి — బాస్మతి, బ్రౌన్ రైస్ లేదా సాధారణ?"
        lang.startsWith("ml") -> "$name, ఏत్ అరి వేణం — బాస్మతి, బ్రౌన్ రైస్, అతో సాధారణ అరియో?"
        lang.startsWith("kn") -> "$name, ಯಾವ ಅಕ್ಕಿ ಬೇಕು — ಬಾಸ್ಮತಿ, ಬ್ರೌನ್ ರೈಸ್, ಅಥವಾ ಸಾಮಾನ್ಯ ಅಕ್ಕಿ?"
        lang.startsWith("ta") -> "$name, எந்த அரிசி வேணும் — பாஸ்மதி, பிரவுன் ரைஸ், அல்லது சாதாரண அரிசியா?"
        lang.startsWith("pa") -> "$name, ਕਿਹੜਾ ਚਾਵਲ ਚਾਹੀਦਾ — ਬਾਸਮਤੀ, ਬ੍ਰਾਊਨ ਰਾਈਸ, ਜਾਂ ਸਾਧਾਰਨ?"
        lang.startsWith("gu") -> "$name, ક્યો ચોખા જોઈએ — બાસ્મતી, બ્રાઉન રાઇઝ, અથવા સાદો?"
        lang.startsWith("mr") -> "$name, कोणता तांदूळ हवा — बासमती, ब्राऊन राईस, किंवा साधा?"
        else                   -> "Which rice would you like $name — basmati, brown rice, or regular?"
    }

    // ─────────────────────────────────────────────────────────────
    // 3b. ASK TYPE – OIL
    // ─────────────────────────────────────────────────────────────
    fun askTypeOil(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "कौन सा तेल $name — सनफ्लावर, सरसों, मूँगफली या नारियल?"
        lang.startsWith("hg") -> "Kaun sa oil $name — sunflower, sarson, moongfali ya nariyal?"
        lang.startsWith("te") -> "$name, ఏ నూనె — పొద్దుతిరుగుడు, ఆవ, వేరుశనగ లేదా కొబ్బరి నూనె?"
        lang.startsWith("ml") -> "$name, ഏത് എണ്ണ — സൂര്യകാന്തി, കടുകെണ്ണ, നിലക്കടല, അതോ തേങ്ങ ഓയിലോ?"
        lang.startsWith("kn") -> "$name, ಯಾವ ಎಣ್ಣೆ — ಸೂರ್ಯಕಾಂತಿ, ಸಾಸಿವೆ, ಶೇಂಗಾ, ಅಥವಾ ತೆಂಗಿನ ಎಣ್ಣೆ?"
        lang.startsWith("ta") -> "$name, எந்த எண்ணை — சூரியகாந்தி, கடுகெண்ணை, நிலக்கடலை, அல்லது தேங்காய் ஆயிலா?"
        lang.startsWith("pa") -> "$name, ਕਿਹੜਾ ਤੇਲ — ਸੂਰਜਮੁਖੀ, ਸਰ੍ਹੋਂ, ਮੂੰਗਫਲੀ, ਜਾਂ ਨਾਰੀਅਲ?"
        lang.startsWith("gu") -> "$name, ક્યું તેલ — સૂર્યમુખી, સરસવ, મગફળી, અથવા નાળિયેર?"
        lang.startsWith("mr") -> "$name, कोणतं तेल — सूर्यफूल, मोहरी, शेंगदाणा, किंवा नारळ?"
        else                   -> "Which oil $name — sunflower, mustard, groundnut, or coconut?"
    }

    // ─────────────────────────────────────────────────────────────
    // 3c. ASK TYPE – DAL
    // ─────────────────────────────────────────────────────────────
    fun askTypeDaal(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "कौन सी दाल $name — तुअर, मूँग, चना, मसूर या उड़द?"
        lang.startsWith("hg") -> "Kaun si daal $name — toor, moong, chana, masoor ya urad?"
        lang.startsWith("te") -> "$name, ఏ పప్పు — కంది, పెసర, శనగ, మసూర్ లేదా మినప్పప్పు?"
        lang.startsWith("ml") -> "$name, ഏത് പരിപ്പ് — തുവര, മൂങ്, ചന, മസൂർ, അതോ ഉഴുന്നോ?"
        lang.startsWith("kn") -> "$name, ಯಾವ ಬೇಳೆ — ತೊಗರಿ, ಹೆಸರು, ಕಡಲೆ, ಮಸೂರ, ಅಥವಾ ಉದ್ದಿನ ಬೇಳೆ?"
        lang.startsWith("ta") -> "$name, எந்த பருப்பு — துவரை, பாசி, கடலை, மசூர், அல்லது உளுந்தா?"
        lang.startsWith("pa") -> "$name, ਕਿਹੜੀ ਦਾਲ — ਤੂਰ, ਮੂੰਗ, ਚਨਾ, ਮਸੂਰ, ਜਾਂ ਉੜਦ?"
        lang.startsWith("gu") -> "$name, ક્યો દાળ — તુવેર, મગ, ચણા, મસૂર, અથવા અળદ?"
        lang.startsWith("mr") -> "$name, कोणती डाळ — तूर, मूग, हरभरा, मसूर, किंवा उडीद?"
        else                   -> "Which dal $name — toor, moong, chana, masoor, or urad?"
    }

    // ─────────────────────────────────────────────────────────────
    // 3d. ASK TYPE – ATTA
    // ─────────────────────────────────────────────────────────────
    fun askTypeAtta(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "कौन सा आटा $name — गेहूँ का आटा, मैदा या मल्टीग्रेन?"
        lang.startsWith("hg") -> "Kaun sa aata $name — gehun ka aata, maida ya multigrain?"
        lang.startsWith("te") -> "$name, ఏ పిండి — గోధుమ పిండి, మైదా లేదా మల్టీగ్రెయిన్?"
        lang.startsWith("ml") -> "$name, ഏത് മാവ് — ഗോതമ്പ് മാവ്, മൈദ, അതോ മൾട്ടിഗ്രെയിൻ?"
        lang.startsWith("kn") -> "$name, ಯಾವ ಹಿಟ್ಟು — ಗೋಧಿ ಹಿಟ್ಟು, ಮೈದ, ಅಥವಾ ಮಲ್ಟಿಗ್ರೈನ್?"
        lang.startsWith("ta") -> "$name, எந்த மாவு — கோதுமை மாவு, மைதா, அல்லது மல்டிகிரெய்னா?"
        lang.startsWith("pa") -> "$name, ਕਿਹੜਾ ਆਟਾ — ਕਣਕ ਦਾ ਆਟਾ, ਮੈਦਾ, ਜਾਂ ਮਲਟੀਗ੍ਰੇਨ?"
        lang.startsWith("gu") -> "$name, ક્યો લોટ — ઘઉંનો, મેંદો, અથવા મલ્ટિગ્રેઇન?"
        lang.startsWith("mr") -> "$name, कोणता पीठ — गव्हाचं, मैदा, किंवा मल्टिग्रेन?"
        else                   -> "Which flour $name — wheat atta, maida, or multigrain?"
    }

    // ─────────────────────────────────────────────────────────────
    // 4. ASK QUANTITY
    // ─────────────────────────────────────────────────────────────
    fun askQuantity(name: String, product: String, lang: String): String = when {
        lang.startsWith("hi") -> "$product कितना चाहिए $name — आधा किलो, एक किलो या दो किलो?"
        lang.startsWith("hg") -> "$name, kitna $product chahiye — half kg, ek kg ya do kg?"
        lang.startsWith("te") -> "$name, $product ఎంత కావాలి — అర కిలో, ఒక కిలో లేదా రెండు కిలో?"
        lang.startsWith("ml") -> "$name, $product എത്ര വേണം — അര കിലോ, ഒരു കിലോ, അതോ രണ്ട് കിലോ?"
        lang.startsWith("kn") -> "$name, $product ಎಷ್ಟು ಬೇಕು — ಅರ್ಧ ಕೆಜಿ, ಒಂದು ಕೆಜಿ, ಅಥವಾ ಎರಡು ಕೆಜಿ?"
        lang.startsWith("ta") -> "$name, $product எவ்வளவு வேணும் — அரை கிலோ, ஒரு கிலோ, அல்லது இரண்டு கிலோ?"
        lang.startsWith("pa") -> "$name, $product ਕਿੰਨਾ ਚਾਹੀਦਾ — ਅੱਧਾ ਕਿਲੋ, ਇੱਕ ਕਿਲੋ, ਜਾਂ ਦੋ ਕਿਲੋ?"
        lang.startsWith("gu") -> "$name, $product કેટ઼ := shu joiye — અડધો, એક, અથવા બે કિલો?"
        lang.startsWith("mr") -> "$name, $product किती हवं — अर्धा किलो, एक किलो, किंवा दोन किलो?"
        else                   -> "How much $product do you need $name — half kg, one kg, or two kg?"
    }

    // ─────────────────────────────────────────────────────────────
    // 5. CONFIRM ADD PRODUCT
    // ─────────────────────────────────────────────────────────────
    fun confirmAddProduct(name: String, product: String, price: Int, lang: String): String = when {
        lang.startsWith("hi") -> "$product ₹$price का है… कार्ट में ऐड करना है $name?"
        lang.startsWith("hg") -> "$product ₹$price ka hai… kya ise cart me add karna hai $name?"
        lang.startsWith("te") -> "$product ₹$price అవుతుంది… $name, కార్ట్‌లో యాడ్ చేయాలా?"
        lang.startsWith("ml") -> "$product ₹$price ആണ്… $name, കാർട്ടിൽ ചേർക്കണോ?"
        lang.startsWith("kn") -> "$product ₹$price ಆಗುತ್ತದೆ… $name, ಕಾರ್ಟ್‌ಗೆ ಆ್ಯಡ್ ಮಾಡಬೇಕೇ?"
        lang.startsWith("ta") -> "$product ₹$price ஆகும்… $name, கார்ட்டில சேர்க்கணுமா?"
        lang.startsWith("pa") -> "$product ₹$price ਦਾ ਹੈ… $name, ਕਾਰਟ ਵਿੱਚ ਪਾਉਣਾ ਹੈ?"
        lang.startsWith("gu") -> "$product ₹$price નો છે… $name, કાર્ટમાં ઉ઼ := merge karu?"
        lang.startsWith("mr") -> "$product ₹$price चा आहे… $name, कार्टमध्ये टाकायचं का?"
        else                   -> "$product costs ₹$price… should I add it to your cart $name?"
    }

    // ─────────────────────────────────────────────────────────────
    // 6. ITEM ADDED
    // ─────────────────────────────────────────────────────────────
    fun itemAdded(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "ठीक है, कार्ट में ऐड कर दिया… और कुछ चाहिए $name?"
        lang.startsWith("hg") -> "Theek hai, cart me add kar diya… aur kuch chahiye $name?"
        lang.startsWith("te") -> "సరే, కార్ట్‌లో యాడ్ చేసాను… $name, ఇంకా ఏమైనా కావాలా?"
        lang.startsWith("ml") -> "ശരി, കാർട്ടിൽ ചേർത്തു… $name, മറ്റെന്തെങ്കിലും വേണോ?"
        lang.startsWith("kn") -> "ಸರಿ, ಕಾರ್ಟ್‌ಗೆ ಸೇರಿಸಿದೆ… $name, ಇನ್ನೇನಾದರೂ ಬೇಕೇ?"
        lang.startsWith("ta") -> "சரி, கார்ட்டில சேர்த்தாச்சு… $name, இன்னும் என்னாவது வேணுமா?"
        lang.startsWith("pa") -> "ਠੀਕ ਹੈ, ਕਾਰਟ ਵਿੱਚ ਪਾ ਦਿੱਤਾ… $name, ਕੁਝ ਹੋਰ ਚਾਹੀਦਾ?"
        lang.startsWith("gu") -> "ઠીક, કાર્ટમાં ઉ઼ := add kari didhu… $name, biju khai joiye?"
        lang.startsWith("mr") -> "ठीक, कार्टमध्ये टाकलं… $name, आणखी काही हवं?"
        else                   -> "Alright, added to your cart… do you need anything else $name?"
    }

    // ─────────────────────────────────────────────────────────────
    // 7. CONFIRM ADD NEXT ITEM
    // ─────────────────────────────────────────────────────────────
    fun confirmAddNext(name: String, product: String, price: Int, lang: String): String = when {
        lang.startsWith("hi") -> "$product ₹$price का है… यह भी कार्ट में ऐड करना है $name?"
        lang.startsWith("hg") -> "$product ₹$price ka hai… kya ise bhi cart me add karna hai $name?"
        lang.startsWith("te") -> "$product ₹$price అవుతుంది… $name, ఇది కూడా యాడ్ చేయాలా?"
        lang.startsWith("ml") -> "$product ₹$price ആണ്… $name, ഇതും ചേർക്കണോ?"
        lang.startsWith("kn") -> "$product ₹$price ಆಗುತ್ತದೆ… $name, ಇದನ್ನೂ ಕಾರ್ಟ್‌ಗೆ ಹಾಕಬೇಕೇ?"
        lang.startsWith("ta") -> "$product ₹$price ஆகும்… $name, இதையும் சேர்க்கணுமா?"
        lang.startsWith("pa") -> "$product ₹$price ਦਾ ਹੈ… $name, ਇਹ ਵੀ ਕਾਰਟ ਵਿੱਚ ਪਾਉਣਾ ਹੈ?"
        lang.startsWith("gu") -> "$product ₹$price નો છે… $name, આ પણ ઉ઼ := add karu?"
        lang.startsWith("mr") -> "$product ₹$price चा आहे… $name, हे पण टाकायचं का?"
        else                   -> "$product costs ₹$price… should I add this as well $name?"
    }

    // ─────────────────────────────────────────────────────────────
    // 8. ITEM NOT FOUND
    // ─────────────────────────────────────────────────────────────
    fun itemNotFound(name: String, item: String, lang: String): String = when {
        lang.startsWith("hi") -> "माफ करें $name, पास में $item नहीं मिला। कुछ मिलता-जुलता देखूँ?"
        lang.startsWith("hg") -> "Sorry $name, $item nearby nahi mila. Kuch similar dhundhe?"
        lang.startsWith("te") -> "క్షమించండి $name, దగ్గర్లో $item దొరకలేదు. ఇలాంటిది వేరేది చూపించనా?"
        lang.startsWith("ml") -> "ക്ഷമിക്കണം $name, അടുത്ത് $item കിട്ടുന്നില്ല. ഇതിനോടൊക്കെ ഉള്ളത് നോക്കട്ടേ?"
        lang.startsWith("kn") -> "ಕ್ಷಮಿಸಿ $name, ಹತ್ತಿರ $item ಸಿಗಲಿಲ್ಲ. ಹೋಲಿಕೆ ಇರುವುದು ಹುಡುಕಲೇ?"
        lang.startsWith("ta") -> "மன்னிக்கணும் $name, பக்கத்துல $item கிடைக்கலை. இதுமாதிரி வேற தேடட்டுமா?"
        lang.startsWith("pa") -> "ਮਾਫ਼ ਕਰਨਾ $name, ਨੇੜੇ $item ਨਹੀਂ ਮਿਲਿਆ। ਕੋਈ ਮਿਲਦਾ-ਜੁਲਦਾ ਦੇਖਾਂ?"
        lang.startsWith("gu") -> "માફ ਕਰਜੋ $name, નzic := paas $item nathu madyu. Meldaijultu joie?"
        lang.startsWith("mr") -> "माफ करा $name, जवळ $item मिळाला नाही. मिळतं-जुळतं पाहू का?"
        else                   -> "Sorry $name, couldn't find $item nearby. Should I look for something similar?"
    }

    // ─────────────────────────────────────────────────────────────
    // 9. CONFIRM ORDER (done adding)
    // ─────────────────────────────────────────────────────────────
    fun confirmOrder(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "ठीक है… बस इतना ही है ना $name? क्या order करना है?"
        lang.startsWith("hg") -> "Theek hai… bas itna hi hai na $name? Kya order karna hai?"
        lang.startsWith("te") -> "సరే… $name, అంతే కదా? ఆర్డర్ చేయాలా?"
        lang.startsWith("ml") -> "ശരി… $name, ഇത്രയേ ഉള്ളൂ അല്ലേ? ഓർഡർ ചെയ്യട്ടേ?"
        lang.startsWith("kn") -> "ಸರಿ… $name, ಅಷ್ಟೇ ತಾನೆ? ಆರ್ಡರ್ ಮಾಡಲೇ?"
        lang.startsWith("ta") -> "சரி… $name, இவ்வளவுதானே? ஆர்டர் பண்ணட்டுமா?"
        lang.startsWith("pa") -> "ਠੀਕ ਹੈ… $name, ਬੱਸ ਇੰਨਾ ਹੀ ਹੈ ਨਾ? ਆਰਡਰ ਕਰਨਾ ਹੈ?"
        lang.startsWith("gu") -> "ઠીક… $name, bas atle j chhe na? Order karu?"
        lang.startsWith("mr") -> "ठीक… $name, बस इतकंच आहे ना? ऑर्डर करायचं का?"
        else                   -> "Alright… that's all, right $name? Should I place your order?"
    }

    // ─────────────────────────────────────────────────────────────
    // 10. ASK PAYMENT MODE
    // ─────────────────────────────────────────────────────────────
    fun askPayment(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "$name, पेमेंट कैसे करना है — UPI, कार्ड या कैश ऑन डिलीवरी?"
        lang.startsWith("hg") -> "$name, payment kaise karna chahenge — UPI, card ya cash on delivery?"
        lang.startsWith("te") -> "$name, ఎలా పేమెంట్ చేయాలనుకుంటున్నారు — UPI, కార్డ్ లేదా క్యాష్ ఆన్ డెలివరీ?"
        lang.startsWith("ml") -> "$name, എങ്ങനെ പണം അടയ്ക്കണം — UPI, കാർഡ്, അതോ ക്യാഷ് ഓൺ ഡെലിവറിയോ?"
        lang.startsWith("kn") -> "$name, ಹೇಗೆ ಪೇಮೆಂಟ್ ಮಾಡಬೇಕು — UPI, ಕಾರ್ಡ್, ಅಥವಾ ಕ್ಯಾಶ್ ಆನ್ ಡೆಲಿವರಿ?"
        lang.startsWith("ta") -> "$name, எப்படி pay பண்ணுவீங்க — UPI, கார்ட், அல்லது cash on delivery?"
        lang.startsWith("pa") -> "$name, ਪੇਮੈਂਟ ਕਿਵੇਂ ਕਰਨੀ ਹੈ — UPI, ਕਾਰਡ, ਜਾਂ ਕੈਸ਼ ਆਨ ਡਿਲੀਵਰੀ?"
        lang.startsWith("gu") -> "$name, payment kem karva chho — UPI, card, ke cash on delivery?"
        lang.startsWith("mr") -> "$name, पेमेंट कसं करायचं — UPI, कार्ड, किंवा कॅश ऑन डिलिव्हरी?"
        else                   -> "How would you like to pay $name — UPI, card, or cash on delivery?"
    }

    // ─────────────────────────────────────────────────────────────
    // 11. PAYMENT INSTRUCTIONS
    // ─────────────────────────────────────────────────────────────
    fun paymentUpi(lang: String): String = when {
        lang.startsWith("hi") -> "ठीक है, UPI से पेमेंट कर लीजिए… हो जाए तो बताइए।"
        lang.startsWith("hg") -> "Theek hai, UPI se payment kar lijiye… ho jaaye to batayein."
        lang.startsWith("te") -> "సరే, UPI ద్వారా పేమెంట్ చేయండి… అయిన తర్వాత చెప్పండి."
        lang.startsWith("ml") -> "ശരി, UPI വഴി പേ ചെയ്യൂ… കഴിഞ്ഞാൽ പറയൂ."
        lang.startsWith("kn") -> "ಸರಿ, UPI ಮೂಲಕ ಪೇಮೆಂಟ್ ಮಾಡಿ… ಆದ ಮೇಲೆ ತಿಳಿಸಿ."
        lang.startsWith("ta") -> "சரி, UPI மூலம் பண்ணுங்க… முடிஞ்சதும் சொல்லுங்க."
        lang.startsWith("pa") -> "ਠੀਕ ਹੈ, UPI ਨਾਲ ਪੇਮੈਂਟ ਕਰੋ… ਹੋ ਜਾਵੇ ਤਾਂ ਦੱਸੋ।"
        lang.startsWith("gu") -> "Saru, UPI thi payment karo… thai jay etle kahejo."
        lang.startsWith("mr") -> "ठीक, UPI ने पेमेंट करा… झालं की सांगा."
        else                   -> "Alright, please complete the payment using UPI… let me know once done."
    }

    fun paymentCard(lang: String): String = when {
        lang.startsWith("hi") -> "पेमेंट के लिए कार्ड टैप या स्वाइप कर लीजिए।"
        lang.startsWith("hg") -> "Payment ke liye card tap ya swipe kar lijiye."
        lang.startsWith("te") -> "పేమెంట్ కోసం కార్డ్ ట్యాప్ లేదా స్వైప్ చేయండి."
        lang.startsWith("ml") -> "പേ ചെയ്യാൻ കാർഡ് ടാപ്പ് ചെയ്യൂ."
        lang.startsWith("kn") -> "ಪೇಮೆಂಟ್ ಮಾಡಲು ಕಾರ್ಡ್ ಟ್ಯಾಪ್ ಮಾಡಿ."
        lang.startsWith("ta") -> "பேமெண்ட்க்கு கார்டை டேப் செய்யுங்க."
        lang.startsWith("pa") -> "ਪੇਮੈਂਟ ਲਈ ਕਾਰਡ ਟੈਪ ਜਾਂ ਸਵਾਈਪ ਕਰੋ।"
        lang.startsWith("gu") -> "Payment mate card tap ke swipe karo."
        lang.startsWith("mr") -> "पेमेंटसाठी कार्ड टॅप किंवा स्वाइप करा."
        else                   -> "Please tap or swipe your card to complete payment."
    }

    fun paymentCash(lang: String): String = when {
        lang.startsWith("hi") -> "ठीक है, कैश ऑन डिलीवरी! सही बदलाव तैयार रखिए।"
        lang.startsWith("hg") -> "Cash on delivery hoga! Exact change ready rakhein."
        lang.startsWith("te") -> "సరే, క్యాష్ ఆన్ డెలివరీ! సరైన చిల్లర సిద్ధంగా ఉంచండి."
        lang.startsWith("ml") -> "ക്യാഷ് ഓൺ ഡെലിവറി! ശരിയായ ചില്ലറ തയ്യാറാക്കിയേക്കൂ."
        lang.startsWith("kn") -> "ಕ್ಯಾಶ್ ಆನ್ ಡೆಲಿವರಿ! ಸರಿಯಾದ ಚಿಲ್ಲರೆ ರೆಡಿ ಇಟ್ಟುಕೊಳ್ಳಿ."
        lang.startsWith("ta") -> "கேஷ் ஆன் டெலிவரி! சரியான சில்லறை ரெடியா வைங்க."
        lang.startsWith("pa") -> "ਕੈਸ਼ ਆਨ ਡਿਲੀਵਰੀ! ਸਹੀ ਚਿੱਲਰ ਤਿਆਰ ਰੱਖੋ।"
        lang.startsWith("gu") -> "Cash on delivery! Sarabar chiller taiyar rakhjo."
        lang.startsWith("mr") -> "कॅश ऑन डिलिव्हरी! योग्य सुट्टे तयार ठेवा."
        else                   -> "Cash on delivery! Please keep exact change ready."
    }

    // ─────────────────────────────────────────────────────────────
    // 12. ORDER SUCCESS
    // ─────────────────────────────────────────────────────────────
    fun orderSuccess(name: String, minutes: Int = 30, lang: String): String = when {
        lang.startsWith("hi") -> "परफेक्ट… आपका order हो गया है $name। लगभग $minutes मिनट में आ जाएगा।"
        lang.startsWith("hg") -> "Perfect… aapka order place ho gaya hai $name. Lagbhag $minutes minute me aa jayega."
        lang.startsWith("te") -> "పర్ఫెక్ట్… $name, మీ ఆర్డర్ ప్లేస్ అయింది. దాదాపు $minutes నిమిషాల్లో వస్తుంది."
        lang.startsWith("ml") -> "പൊളി… $name, ഓർഡർ ചെയ്തു. ഏകദേശം $minutes മിനിറ്റ് ഉള്ളിൽ എത്തും."
        lang.startsWith("kn") -> "ಪರ್ಫೆಕ್ಟ್… $name, ಆರ್ಡರ್ ಆಯಿತು. ಸುಮಾರು $minutes ನಿಮಿಷದಲ್ಲಿ ಬರುತ್ತದೆ."
        lang.startsWith("ta") -> "பர்ஃபெக்ட்… $name, ஆர்டர் ஆச்சு. சுமார் $minutes நிமிஷத்தில வரும்."
        lang.startsWith("pa") -> "ਪਰਫੈਕਟ… $name, ਆਰਡਰ ਹੋ ਗਿਆ। ਲਗਭਗ $minutes ਮਿੰਟ ਵਿੱਚ ਆ ਜਾਵੇਗਾ।"
        lang.startsWith("gu") -> "Perfect… $name, order thai gayu. Lagbhag $minutes minute ma aavse."
        lang.startsWith("mr") -> "परफेक्ट… $name, ऑर्डर झालं. साधारण $minutes मिनिटांत येईल."
        else                   -> "Perfect… your order has been placed $name. It will arrive in about $minutes minutes."
    }

    // ─────────────────────────────────────────────────────────────
    // 13. ORDER FAILED
    // ─────────────────────────────────────────────────────────────
    fun orderFailed(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "माफ करें $name, order में कोई समस्या आई। कृपया थोड़ी देर में फिर से कोशिश करें।"
        lang.startsWith("hg") -> "Sorry $name, order mein problem aayi. Thodi der baad try karein."
        lang.startsWith("te") -> "క్షమించండి $name, ఆర్డర్‌లో సమస్య వచ్చింది. దయచేసి మళ్ళీ ప్రయత్నించండి."
        lang.startsWith("ml") -> "ക്ഷമിക്കണം $name, ഓർഡറിൽ ഒരു പ്രശ്നം വന്നു. ഒന്ന് കൂടി ശ്രമിക്കൂ."
        lang.startsWith("kn") -> "ಕ್ಷಮಿಸಿ $name, ಆರ್ಡರ್‌ನಲ್ಲಿ ಸಮಸ್ಯೆ ಆಯಿತು. ಸ್ವಲ್ಪ ಸಮಯದ ನಂತರ ಪ್ರಯತ್ನಿಸಿ."
        lang.startsWith("ta") -> "மன்னிக்கணும் $name, ஆர்டர்ல பிரச்சனை வந்துச்சு. கொஞ்சம் கழிச்சு try பண்ணுங்க."
        lang.startsWith("pa") -> "ਮਾਫ਼ ਕਰਨਾ $name, ਆਰਡਰ ਵਿੱਚ ਸਮੱਸਿਆ ਆਈ। ਥੋੜੀ ਦੇਰ ਬਾਅਦ ਕੋਸ਼ਿਸ਼ ਕਰੋ।"
        lang.startsWith("gu") -> "Mafi mangjo $name, order ma prashna aaviyo. Thodi var bad try karo."
        lang.startsWith("mr") -> "माफ करा $name, ऑर्डरमध्ये समस्या आली. थोड्या वेळाने पुन्हा प्रयत्न करा."
        else                   -> "Sorry $name, there was an issue placing your order. Please try again in a moment."
    }

    // ─────────────────────────────────────────────────────────────
    // 14. PRESCRIPTION PROMPT
    // ─────────────────────────────────────────────────────────────
    fun prescriptionPrompt(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "$name, पर्ची है? Camera से photo लें या gallery से upload करें।"
        lang.startsWith("hg") -> "$name, kya prescription hai? Camera se photo lo ya gallery se upload karo."
        lang.startsWith("te") -> "$name, మీ దగ్గర ప్రిస్క్రిప్షన్ ఉందా? కెమెరా చూపించండి లేదా మందు పేరు చెప్పండి."
        lang.startsWith("ml") -> "$name, കൈయിൽ കുറിപ്പടി ഉണ്ടോ? ക്യാമറ കാണിക്കൂ, അല്ലെങ്കിൽ മരുന്നിന്റെ പേര് പറയൂ."
        lang.startsWith("kn") -> "$name, ನಿಮ್ಮ ಹತ್ತಿರ ಪ್ರಿಸ್ಕ್ರಿಪ್ಷನ್ ಇದೆಯಾ? ಕ್ಯಾಮೆರಾ ತೋರಿಸಿ ಅಥವಾ ಮಾತ್ರೆ ಹೆಸರು ಹೇಳಿ."
        lang.startsWith("ta") -> "$name, குறிப்பேடு இருக்கா? கேமரா காட்டுங்க அல்லது மருந்து பேர சொல்லுங்க."
        lang.startsWith("pa") -> "$name, ਪਰਚੀ ਹੈ? Camera ਨਾਲ photo ਖਿੱਚੋ ਜਾਂ gallery ਤੋਂ upload ਕਰੋ।"
        lang.startsWith("gu") -> "$name, prescription chhe? Camera thi photo lo ke gallery thi upload karo."
        lang.startsWith("mr") -> "$name, चिठ्ठी आहे? Camera ने photo घ्या किंवा gallery मधून upload करा."
        else                   -> "$name, do you have a prescription? Show the camera or just tell me the medicine name."
    }

    // ─────────────────────────────────────────────────────────────
    // 15. SERVICE CONFIRM
    // ─────────────────────────────────────────────────────────────
    fun serviceConfirm(name: String, service: String, lang: String): String = when {
        lang.startsWith("hi") -> "$name, $service बुक कर सकता हूँ। कब का समय ठीक रहेगा?"
        lang.startsWith("hg") -> "$name, $service book kar sakta hoon. Kab ka time theek rahega?"
        lang.startsWith("te") -> "$name, $service బుక్ చేయగలను. ఏ తేదీ మరియు సమయం సరిగ్గా ఉంటుంది?"
        lang.startsWith("ml") -> "$name, $service ബുക്ക് ചെയ്യാം. ഏത് ദിവസം, ഏത് സമയം ശരിയാകും?"
        lang.startsWith("kn") -> "$name, $service ಬುಕ್ ಮಾಡಬಹುದು. ಯಾವ ದಿನ, ಯಾವ ಸಮಯ ಸರಿಹೋಗುತ್ತದೆ?"
        lang.startsWith("ta") -> "$name, $service book பண்ணலாம். எந்த நேரம் சரியா இருக்கும்?"
        lang.startsWith("pa") -> "$name, $service book ਕਰ ਸਕਦਾ ਹਾਂ। ਕਦੋਂ ਦਾ ਸਮਾਂ ਠੀਕ ਰਹੇਗਾ?"
        lang.startsWith("gu") -> "$name, $service book kari shakay chhu. Kyare no time thik raheshe?"
        lang.startsWith("mr") -> "$name, $service बुक करता येईल. कधीचा वेळ योग्य असेल?"
        else                   -> "$name, I can book $service for you. What date and time works best?"
    }

    // ─────────────────────────────────────────────────────────────
    // 16. REPEAT / DIDN'T UNDERSTAND
    // ─────────────────────────────────────────────────────────────
    fun repeatRequest(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "माफ करें $name, समझ नहीं आया। फिर से बोल सकते हैं?"
        lang.startsWith("hg") -> "Sorry $name, samajh nahi aaya. Phir se bol sakte hain?"
        lang.startsWith("te") -> "క్షమించండి $name, అర్థం కాలేదు. మళ్ళీ చెప్పగలరా?"
        lang.startsWith("ml") -> "ക്ഷമിക്കണം $name, മനസ്സിലായില്ല. ഒന്ന് കൂടി പറയാമോ?"
        lang.startsWith("kn") -> "ಕ್ಷಮಿಸಿ $name, ಅರ್ಥ ಆಗಲಿಲ್ಲ. ಮತ್ತೆ ಹೇಳಬಲ್ಲಿರಾ?"
        lang.startsWith("ta") -> "மன்னிக்கணும் $name, புரியலை. ஒருமுறை சொல்லுவீங்களா?"
        lang.startsWith("pa") -> "ਮਾਫ਼ ਕਰਨਾ $name, ਸਮਝ ਨਹੀਂ ਆਇਆ। ਦੁਬਾਰਾ ਬੋਲ ਸਕਦੇ ਹੋ?"
        lang.startsWith("gu") -> "Mafi mangjo $name, samjhayo nahi. Fari bol sako?"
        lang.startsWith("mr") -> "माफ करा $name, समजलं नाही. पुन्हा सांगाल का?"
        else                   -> "Sorry $name, I didn't catch that. Could you please say it again?"
    }

    // ─────────────────────────────────────────────────────────────
    // 17. FALLBACK (unknown intent)
    // ─────────────────────────────────────────────────────────────
    fun fallback(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "$name, यह नहीं समझा। किराना, दवाइयाँ या होम सर्विस में मदद कर सकता हूँ।"
        lang.startsWith("hg") -> "$name, yeh samajh nahi aaya. Grocery, medicine ya home service mein help karta hoon."
        lang.startsWith("te") -> "$name, నాకు అది అర్థం కాలేదు. కిరాణా, మందులు లేదా హోమ్ సర్వీసెస్‌లో సహాయం చేస్తాను."
        lang.startsWith("ml") -> "$name, അത് മനസ്സിലായില്ല. ഗ്രോസറി, മരുന്ന്, ഹോം സർവീസ് — ഇതിൽ സഹായം ചെയ്യാം."
        lang.startsWith("kn") -> "$name, ಅರ್ಥ ಆಗಲಿಲ್ಲ. ಕಿರಾಣಾ, ಔಷಧಿ ಅಥವಾ ಹೋಮ್ ಸರ್ವೀಸ್‌ನಲ್ಲಿ ಸಹಾಯ ಮಾಡಬಲ್ಲೆ."
        lang.startsWith("ta") -> "$name, புரியலை. கிரோசரி, மருந்து, ஹோம் சர்வீஸ் — இதுல உதவுவேன்."
        lang.startsWith("pa") -> "$name, ਸਮਝ ਨਹੀਂ ਆਇਆ। ਕਿਰਾਣਾ, ਦਵਾਈਆਂ ਜਾਂ ਘਰ ਸੇਵਾ ਵਿੱਚ ਮਦਦ ਕਰ ਸਕਦਾ ਹਾਂ।"
        lang.startsWith("gu") -> "$name, samjhayo nahi. Grocery, dava ke home service ma madad kari shakay chhu."
        lang.startsWith("mr") -> "$name, समजलं नाही. किराणा, औषध किंवा होम सर्विसमध्ये मदत करतो."
        else                   -> "$name, I'm not sure about that. I can help with groceries, medicines, or home services."
    }

    // ─────────────────────────────────────────────────────────────
    // 18. CART SUMMARY
    // ─────────────────────────────────────────────────────────────
    fun cartSummary(name: String, itemCount: Int, total: Int, lang: String): String {
        val s = if (itemCount > 1) "s" else ""
        return when {
            lang.startsWith("hi") -> "$name, cart में $itemCount आइटम हैं, कुल ₹$total। order करें?"
            lang.startsWith("hg") -> "$name, cart mein $itemCount item hain, total ₹$total. Order karein?"
            lang.startsWith("te") -> "$name, cart లో $itemCount వస్తువులు, మొత్తం ₹$total. ఆర్డర్ చేయాలా?"
            lang.startsWith("ml") -> "$name, cart ൽ $itemCount ഐറ്റം, ആകെ ₹$total. ഓർഡർ ചെയ്യണോ?"
            lang.startsWith("kn") -> "$name, cart ನಲ್ಲಿ $itemCount ಐಟಂ, ಒಟ್ಟು ₹$total. ಆರ್ಡರ್ ಮಾಡಬೇಕೇ?"
            lang.startsWith("ta") -> "$name, cart ல $itemCount item, மொத்தம் ₹$total. ஆர்டர் பண்ணட்டுமா?"
            lang.startsWith("pa") -> "$name, cart ਵਿੱਚ $itemCount item, ਕੁੱਲ ₹$total। ਆਰਡਰ ਕਰਨਾ ਹੈ?"
            lang.startsWith("gu") -> "$name, cart ma $itemCount item, kul ₹$total. Order karu?"
            lang.startsWith("mr") -> "$name, cart मध्ये $itemCount item, एकूण ₹$total. ऑर्डर करायचं?"
            else                   -> "Your cart has $itemCount item$s totalling ₹$total $name. Ready to order?"
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 19. DELIVERY TIME
    // ─────────────────────────────────────────────────────────────
    fun deliveryTime(name: String, minutes: Int, lang: String): String = when {
        lang.startsWith("hi") -> "$name, आपका order लगभग $minutes मिनट में पहुँच जाएगा।"
        lang.startsWith("hg") -> "$name, aapka order lagbhag $minutes minute mein aa jayega."
        lang.startsWith("te") -> "$name, మీ ఆర్డర్ దాదాపు $minutes నిమిషాల్లో చేరుకుంటుంది."
        lang.startsWith("ml") -> "$name, ഓർഡർ ഏകദേശം $minutes മിനിറ്റ് ഉള്ളിൽ എത്തും."
        lang.startsWith("kn") -> "$name, ಆರ್ಡರ್ ಸುಮಾರು $minutes ನಿಮಿಷದಲ್ಲಿ ತಲುಪುತ್ತದೆ."
        lang.startsWith("ta") -> "$name, ஆர்டர் சுமார் $minutes நிமிஷத்துல வரும்."
        lang.startsWith("pa") -> "$name, ਆਰਡਰ ਲਗਭਗ $minutes ਮਿੰਟ ਵਿੱਚ ਆ ਜਾਵੇਗਾ।"
        lang.startsWith("gu") -> "$name, order lagbhag $minutes minute ma aavse."
        lang.startsWith("mr") -> "$name, ऑर्डर साधारण $minutes मिनिटांत येईल."
        else                   -> "Your order will reach you in approximately $minutes minutes $name."
    }

    // ─────────────────────────────────────────────────────────────
    // 20. STORE CLOSED
    // ─────────────────────────────────────────────────────────────
    fun storeClosed(name: String, lang: String): String = when {
        lang.startsWith("hi") -> "माफ करें $name, अभी आपके क्षेत्र में कोई store उपलब्ध नहीं है। थोड़ी देर बाद फिर से कोशिश करें।"
        lang.startsWith("hg") -> "Sorry $name, abhi aapke area mein koi store available nahi hai. Baad mein try karein."
        lang.startsWith("te") -> "క్షమించండి $name, ఇప్పుడు మీ ప్రాంతంలో ఏ స్టోర్ అందుబాటులో లేదు. తర్వాత ప్రయత్నించండి."
        lang.startsWith("ml") -> "ക്ഷമിക്കണം $name, ഇപ്പോൾ നിങ്ങളുടെ ഏരിയയിൽ സ്‌റ്റോർ ഇല്ല. ഒന്ന് കൂടി ശ്രമിക്കൂ."
        lang.startsWith("kn") -> "ಕ್ಷಮಿಸಿ $name, ಈಗ ನಿಮ್ಮ ಪ್ರದೇಶದಲ್ಲಿ ಸ್ಟೋರ್ ಇಲ್ಲ. ನಂತರ ಪ್ರಯತ್ನಿಸಿ."
        lang.startsWith("ta") -> "மன்னிக்கணும் $name, இப்போ உங்க ஏரியால store இல்லை. கொஞ்சம் கழிச்சு try பண்ணுங்க."
        lang.startsWith("pa") -> "ਮਾਫ਼ ਕਰਨਾ $name, ਹੁਣ ਤੁਹਾਡੇ ਇਲਾਕੇ ਵਿੱਚ ਕੋਈ store ਉਪਲਬਧ ਨਹੀਂ। ਬਾਅਦ ਵਿੱਚ ਕੋਸ਼ਿਸ਼ ਕਰੋ।"
        lang.startsWith("gu") -> "Mafi mangjo $name, hamare area ma store available nathi. Pachi try karo."
        lang.startsWith("mr") -> "माफ करा $name, आत्ता तुमच्या भागात store उपलब्ध नाही. थोड्या वेळाने प्रयत्न करा."
        else                   -> "Sorry $name, no stores are available in your area right now. Please try again later."
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER: category-aware type question dispatch
    // ─────────────────────────────────────────────────────────────
    fun askTypeForCategory(name: String, rawCategory: String, lang: String): String {
        val cat = rawCategory.lowercase()
        return when {
            cat.containsAny("rice", "chawal", "బియ్యం", "arisi", "akki") -> askTypeRice(name, lang)
            cat.containsAny("oil", "tel", "నూనె", "ennai", "enne")        -> askTypeOil(name, lang)
            cat.containsAny("dal", "daal", "pappu", "paruppu", "bele")    -> askTypeDaal(name, lang)
            cat.containsAny("atta", "flour", "pindi", "maavu", "hittu")   -> askTypeAtta(name, lang)
            else -> {
                val display = rawCategory.replaceFirstChar { it.uppercase() }
                when {
                    lang.startsWith("hi") -> "$display कौन सा चाहिए $name?"
                    lang.startsWith("te") -> "$name, ఏ $display కావాలి?"
                    lang.startsWith("ml") -> "$name, ഏത് $display വേണം?"
                    lang.startsWith("kn") -> "$name, ಯಾವ $display ಬೇಕು?"
                    lang.startsWith("ta") -> "$name, எந்த $display வேணும்?"
                    lang.startsWith("pa") -> "$name, ਕਿਹੜਾ $display ਚਾਹੀਦਾ?"
                    lang.startsWith("gu") -> "$name, kyo $display joie?"
                    lang.startsWith("mr") -> "$name, कोणता $display हवा?"
                    else                  -> "Which $display would you like $name?"
                }
            }
        }
    }

    private fun String.containsAny(vararg terms: String) = terms.any { contains(it, ignoreCase = true) }
}