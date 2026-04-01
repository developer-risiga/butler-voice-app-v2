package com.demo.butler_voice_app.ai

/**
 * IndianLanguageProcessor — Real colloquial daily-use phrases
 * Hindi · Telugu · Kannada · Tamil · Malayalam · Punjabi · Odia
 *
 * These are exactly what people say in kitchens, markets, and WhatsApp —
 * not textbook language. Sourced from native speaker usage.
 */
object IndianLanguageProcessor {

    // ─── Colloquial YES detection ────────────────────────────────────────────
    val YES_PHRASES = setOf(
        // Hindi daily
        "हाँ","हां","हा","जी","जी हाँ","ठीक है","बिल्कुल","कर दो","कर दे",
        "दे दो","भेज दो","मँगा दो","हाँ यार","अरे हाँ","चलो ठीक है","अच्छा",
        "हाँ भाई","done","ok","okay","haan","ha","ji","theek hai",
        // Telugu daily
        "అవును","అవు","సరే","ఆ","హా","పంపించు","ఓకే","చేయి","వేయి",
        "సరేనండి","అవుననుకో","ఆర్డర్ పెట్టు",
        // Kannada daily
        "ಹೌದು","ಸರಿ","ಆಯ್ತು","ಹ","ಓಕೆ","ಹಾಕಿ","ಕಳಿಸಿ","ಮಾಡಿ",
        "ಹೌದಣ್ಣ","ಸರಿ ಮಾಡಿ","ಓಕೆ ಮಾಡಿ",
        // Tamil daily
        "ஆம்","சரி","ஓகே","ஆமா","பண்ணு","அனுப்பு","வேணும்",
        "ஆமாம்","சரிதான்","ஓகேடா",
        // Malayalam daily
        "ആണ്","ശരി","ഓക്കേ","ഹ","ചെയ്യൂ","അയക്കൂ","വേണം",
        "ശരിയാ","ഓക്കേ ചെയ്യൂ","ആണ് ചെയ്യൂ",
        // Punjabi daily
        "ਹਾਂ","ਜੀ","ਠੀਕ ਹੈ","ਕਰ ਦਿਓ","ਭੇਜੋ","ਲੈ ਆਓ","ਹਾਂ ਜੀ",
        "ਬਿਲਕੁਲ","ਚੱਲੋ ਠੀਕ ਹੈ","ਓਕੇ",
        // Odia daily
        "ହଁ","ଠିକ ଅଛି","ହଁ ଦିଅ","ପଠାଅ","ଦରକାର","ଓକେ",
        "ହଁ ଭାଇ","ହଁ ଦେ","ହଁ ଆଣ"
    )

    // ─── Colloquial NO detection ─────────────────────────────────────────────
    val NO_PHRASES = setOf(
        // Hindi daily
        "नहीं","नहीं यार","नही","ना","मत","रहने दो","छोड़ो","cancel",
        "नहीं चाहिए","नहीं करना","बंद करो","नो","मत करो","रद्द करो",
        // Telugu daily
        "వద్దు","వద్దులే","కాదు","వेण్ड","క్యాన్సిల్","వదిలేయ్",
        "వద్దు నండి","కాదు లేండి",
        // Kannada daily
        "ಬೇಡ","ಬೇಡ ಬಿಡಿ","ಇಲ್ಲ","ಕ್ಯಾನ್ಸಲ್","ಬಿಡಿ","ಬೇಡ ಮಾಡಿ",
        // Tamil daily
        "வேண்டாம்","இல்ல","கேன்சல்","வேண்டா","வேண்டாம்டா",
        // Malayalam daily
        "വേണ്ട","ഇല്ല","കാൻസൽ","വേണ്ടടാ","വേണ്ടേ",
        // Punjabi daily
        "ਨਹੀਂ","ਨਾ","ਛੱਡੋ","ਕੈਂਸਲ","ਨਹੀਂ ਚਾਹੀਦਾ","ਰਹਿਣ ਦਿਓ",
        // Odia daily
        "ନାହିଁ","ଦରକାର ନାହିଁ","ଛାଡ଼","କ୍ୟାନ୍ସେଲ","ନା"
    )

    // ─── Colloquial DONE/FINISH detection ───────────────────────────────────
    val DONE_PHRASES = setOf(
        // Hindi daily
        "बस","हो गया","इतना ही","बस यही","बस कर","order कर दो",
        "यही लेना है","चेकआउट","checkout","place कर दो","भेज दो",
        "बस इतना ही काफी है","ले लो","ठीक है इतना",
        // Telugu daily
        "చాలు","అయిపోయింది","ఇంతే","పెట్టేయ్","ఇక చాలు","ఓకే ఇంతే",
        // Kannada daily
        "ಸಾಕು","ಮುಗಿತು","ಆಯ್ತು","ಇಷ್ಟೇ","ಇಷ್ಟೇ ಸಾಕು",
        // Tamil daily
        "போதும்","முடிஞ்சாச்சு","இவ்ளோதான்","ஆர்டர் பண்ணு",
        // Malayalam daily
        "മതി","കഴിഞ്ഞു","ഇത്രേ","ഓർഡർ ചെയ്യൂ",
        // Punjabi daily
        "ਬੱਸ","ਹੋ ਗਿਆ","ਇਹੀ ਲੈਣਾ ਹੈ","ਆਰਡਰ ਕਰ ਦਿਓ",
        // Odia daily
        "ଏତିକି","ହୋଇଗଲା","ଅର୍ଡର ଦିଅ","ଯଥେଷ୍ଟ"
    )

    // ─── Regional colloquial product names → English search term ────────────
    val REGIONAL_PRODUCT_MAP = mapOf(
        // ── HINDI daily kitchen words ──────────────────────────────────────
        "चावल" to "rice", "chawal" to "rice", "bhaath" to "rice",
        "आटा" to "wheat flour", "atta" to "wheat flour", "gehun" to "wheat",
        "तेल" to "oil", "tel" to "oil", "sarson ka tel" to "mustard oil",
        "दूध" to "milk", "doodh" to "milk",
        "चीनी" to "sugar", "cheeni" to "sugar", "shakkar" to "sugar",
        "नमक" to "salt", "namak" to "salt",
        "दाल" to "dal", "daal" to "dal",
        "घी" to "ghee", "makhan" to "butter", "मक्खन" to "butter",
        "चाय" to "tea", "chai" to "tea",
        "प्याज" to "onion", "pyaz" to "onion", "pyaaj" to "onion",
        "टमाटर" to "tomato", "tamatar" to "tomato",
        "आलू" to "potato", "aloo" to "potato",
        "अंडा" to "egg", "anda" to "egg",
        "पनीर" to "paneer", "paneer" to "paneer",
        "मैदा" to "refined flour", "maida" to "refined flour",
        "बेसन" to "gram flour", "besan" to "gram flour",
        "हल्दी" to "turmeric", "haldi" to "turmeric",
        "जीरा" to "cumin", "jeera" to "cumin",
        "मिर्च" to "chilli", "mirch" to "chilli",
        "धनिया" to "coriander", "dhaniya" to "coriander",
        "लहसुन" to "garlic", "lehsun" to "garlic",
        "अदरक" to "ginger", "adrak" to "ginger",
        "पालक" to "spinach", "saag" to "spinach",
        "ब्रेड" to "bread", "pav" to "bread",
        "दही" to "curd", "dahi" to "curd",
        "रोटी" to "bread", "chapati" to "bread",
        "पोहा" to "poha", "poha" to "poha",
        "सूजी" to "semolina", "suji" to "semolina", "rava" to "semolina",
        "साबुन" to "soap", "sabun" to "soap",
        "बिस्किट" to "biscuit", "biscuit" to "biscuit",
        "नूडल्स" to "noodles", "noodles" to "noodles", "maggi" to "noodles",

        // ── TELUGU daily kitchen words ─────────────────────────────────────
        "బియ్యం" to "rice", "biyyam" to "rice", "annam" to "rice",
        "పిండి" to "flour", "pindi" to "flour",
        "నూనె" to "oil", "nune" to "oil",
        "పాలు" to "milk", "paalu" to "milk",
        "పంచదార" to "sugar", "panchadadara" to "sugar", "bellam" to "jaggery",
        "ఉప్పు" to "salt", "uppu" to "salt",
        "పప్పు" to "dal", "pappu" to "dal",
        "నెయ్యి" to "ghee", "neyyi" to "ghee",
        "వెన్న" to "butter", "venna" to "butter",
        "చాయ్" to "tea", "chaay" to "tea",
        "ఉల్లిపాయ" to "onion", "ullipaya" to "onion",
        "టమాటా" to "tomato", "tomata" to "tomato",
        "బంగాళదుంప" to "potato", "bangaladumpa" to "potato",
        "గుడ్డు" to "egg", "guddu" to "egg",
        "పెరుగు" to "curd", "perugu" to "curd",
        "మిరపకాయ" to "chilli", "mirapakaya" to "chilli",
        "పసుపు" to "turmeric", "pasupu" to "turmeric",
        "జీలకర్ర" to "cumin", "jeelakarra" to "cumin",
        "వెల్లుల్లి" to "garlic", "velluli" to "garlic",
        "అల్లం" to "ginger", "allam" to "ginger",
        "కూర" to "vegetable", "koora" to "vegetable",
        "ఆకుకూర" to "greens", "sabbu" to "soap",
        "పాలపొడి" to "milk powder",

        // ── KANNADA daily kitchen words ────────────────────────────────────
        "ಅಕ್ಕಿ" to "rice", "akki" to "rice",
        "ಹಿಟ್ಟು" to "flour", "hittu" to "flour",
        "ಎಣ್ಣೆ" to "oil", "enne" to "oil",
        "ಹಾಲು" to "milk", "haalu" to "milk",
        "ಸಕ್ಕರೆ" to "sugar", "sakkare" to "sugar", "bella" to "jaggery",
        "ಉಪ್ಪು" to "salt", "uppu" to "salt",
        "ಬೇಳೆ" to "dal", "bele" to "dal",
        "ತುಪ್ಪ" to "ghee", "tuppa" to "ghee",
        "ಬೆಣ್ಣೆ" to "butter", "benne" to "butter",
        "ಚಹಾ" to "tea", "chaha" to "tea",
        "ಈರುಳ್ಳಿ" to "onion", "eerulli" to "onion",
        "ಟೊಮೆಟೊ" to "tomato",
        "ಆಲೂ" to "potato", "aloo" to "potato",
        "ಮೊಟ್ಟೆ" to "egg", "motte" to "egg",
        "ಮೊಸರು" to "curd", "mosaru" to "curd",
        "ಮೆಣಸಿನಕಾಯಿ" to "chilli", "mensina" to "chilli",
        "ಅರಿಶಿನ" to "turmeric", "arishina" to "turmeric",
        "ಜೀರಿಗೆ" to "cumin", "jeerige" to "cumin",
        "ಬೆಳ್ಳುಳ್ಳಿ" to "garlic", "bellulli" to "garlic",
        "ಶುಂಠಿ" to "ginger", "shunthi" to "ginger",
        "ಸೊಪ್ಪು" to "greens", "soppu" to "greens",
        "ರಾಗಿ" to "ragi flour",
        "ಅವಲಕ್ಕಿ" to "poha", "avalakki" to "poha",

        // ── TAMIL daily kitchen words ──────────────────────────────────────
        "அரிசி" to "rice", "arisi" to "rice",
        "மாவு" to "flour", "maavu" to "flour",
        "எண்ணெய்" to "oil", "ennai" to "oil",
        "பால்" to "milk", "paal" to "milk",
        "சர்க்கரை" to "sugar", "sarkkarai" to "sugar", "vellam" to "jaggery",
        "உப்பு" to "salt", "uppu" to "salt",
        "பருப்பு" to "dal", "paruppu" to "dal",
        "நெய்" to "ghee", "nei" to "ghee",
        "வெண்ணெய்" to "butter", "vennai" to "butter",
        "தேநீர்" to "tea", "theneer" to "tea", "chai" to "tea",
        "வெங்காயம்" to "onion", "vengayam" to "onion",
        "தக்காளி" to "tomato", "thakkali" to "tomato",
        "உருளைக்கிழங்கு" to "potato", "urulaikilangu" to "potato",
        "முட்டை" to "egg", "muttai" to "egg",
        "தயிர்" to "curd", "thayir" to "curd",
        "மிளகாய்" to "chilli", "milagai" to "chilli",
        "மஞ்சள்" to "turmeric", "manjal" to "turmeric",
        "சீரகம்" to "cumin", "seeragam" to "cumin",
        "பூண்டு" to "garlic", "poondu" to "garlic",
        "இஞ்சி" to "ginger", "inji" to "ginger",
        "கீரை" to "greens", "keerai" to "greens",
        "இட்லி மாவு" to "idli batter", "idli maavu" to "idli batter",
        "வாழைப்பழம்" to "banana",

        // ── MALAYALAM daily kitchen words ──────────────────────────────────
        "അരി" to "rice", "ari" to "rice", "choru" to "rice",
        "മാവ്" to "flour", "maav" to "flour",
        "എണ്ണ" to "oil", "enna" to "oil",
        "പാൽ" to "milk", "paal" to "milk",
        "പഞ്ചസാര" to "sugar", "panchasara" to "sugar", "sarkkara" to "jaggery",
        "ഉപ്പ്" to "salt", "uppu" to "salt",
        "പരിപ്പ്" to "dal", "parippu" to "dal",
        "നെയ്യ്" to "ghee", "neyy" to "ghee",
        "വെണ്ണ" to "butter", "venna" to "butter",
        "ചായ" to "tea", "chaaya" to "tea",
        "ഉള്ളി" to "onion", "ulli" to "onion",
        "തക്കാളി" to "tomato", "thakkaali" to "tomato",
        "ഉരുളക്കിഴങ്ങ്" to "potato", "urulakizhangu" to "potato",
        "മുട്ട" to "egg", "mutta" to "egg",
        "തൈര്" to "curd", "thairu" to "curd",
        "മുളക്" to "chilli", "mulak" to "chilli",
        "മഞ്ഞൾ" to "turmeric", "manjal" to "turmeric",
        "ജീരകം" to "cumin", "jeerakam" to "cumin",
        "വെളുത്തുള്ളി" to "garlic", "veluthulli" to "garlic",
        "ഇഞ്ചി" to "ginger", "inchi" to "ginger",
        "ചേന" to "yam", "cheena" to "yam",
        "കപ്പ" to "tapioca",
        "കൊഞ്ച്" to "prawns", "konju" to "prawns",
        "കോഴിക്കറി" to "chicken curry",
        "ഇഡ്ഡലി" to "idli",

        // ── PUNJABI daily kitchen words ────────────────────────────────────
        "ਚਾਵਲ" to "rice", "chawal" to "rice",
        "ਆਟਾ" to "wheat flour", "atta" to "wheat flour",
        "ਤੇਲ" to "oil", "tel" to "oil", "sarson da tel" to "mustard oil",
        "ਦੁੱਧ" to "milk", "dudh" to "milk",
        "ਖੰਡ" to "sugar", "khand" to "sugar", "gurr" to "jaggery",
        "ਲੂਣ" to "salt", "lun" to "salt",
        "ਦਾਲ" to "dal", "daal" to "dal",
        "ਮੱਖਣ" to "butter", "makhan" to "butter",
        "ਲੱਸੀ" to "lassi", "lassi" to "lassi",
        "ਚਾਹ" to "tea", "chaa" to "tea",
        "ਪਿਆਜ਼" to "onion", "pyaz" to "onion",
        "ਟਮਾਟਰ" to "tomato", "tamatar" to "tomato",
        "ਆਲੂ" to "potato", "aloo" to "potato",
        "ਅੰਡਾ" to "egg", "anda" to "egg",
        "ਦਹੀਂ" to "curd", "dahiin" to "curd",
        "ਮਿਰਚ" to "chilli", "mirch" to "chilli",
        "ਹਲਦੀ" to "turmeric", "haldi" to "turmeric",
        "ਜੀਰਾ" to "cumin", "jeera" to "cumin",
        "ਲਸਣ" to "garlic", "lasan" to "garlic",
        "ਅਦਰਕ" to "ginger", "adrak" to "ginger",
        "ਸਾਗ" to "mustard greens", "saag" to "mustard greens",
        "ਮੱਕੀ ਦਾ ਆਟਾ" to "maize flour", "makki atta" to "maize flour",
        "ਪਨੀਰ" to "paneer", "paneer" to "paneer",

        // ── ODIA daily kitchen words ───────────────────────────────────────
        "ଚାଉଳ" to "rice", "chawala" to "rice",
        "ଆଟା" to "flour", "ata" to "flour",
        "ତେଲ" to "oil", "tela" to "oil",
        "ଦୁଧ" to "milk", "dudha" to "milk",
        "ଚିନି" to "sugar", "chini" to "sugar", "guda" to "jaggery",
        "ଲୁଣ" to "salt", "luna" to "salt",
        "ଡାଲି" to "dal", "daali" to "dal",
        "ଘିଅ" to "ghee", "ghia" to "ghee",
        "ମଖନ" to "butter", "makhana" to "butter",
        "ଚା" to "tea", "chaa" to "tea",
        "ପିଆଜ" to "onion", "piaja" to "onion",
        "ଟୋମାଟୋ" to "tomato",
        "ଆଳୁ" to "potato", "alu" to "potato",
        "ଅଣ୍ଡା" to "egg", "anda" to "egg",
        "ଦହି" to "curd", "dahi" to "curd",
        "ଲଙ୍କା" to "chilli", "lanka" to "chilli",
        "ହଳଦୀ" to "turmeric", "haladi" to "turmeric",
        "ଜିରା" to "cumin", "jira" to "cumin",
        "ରସୁଣ" to "garlic", "rasuna" to "garlic",
        "ଅଦା" to "ginger", "ada" to "ginger",
        "ଶାଗ" to "greens", "saaga" to "greens",
        "ଚଉଡ଼ା" to "poha", "chauda" to "poha",
        "ସୂଜି" to "semolina", "suji" to "semolina"
    )

    // ─── Language-specific order intent detection ────────────────────────────

    val ORDER_INTENT_PHRASES = mapOf(
        // Hindi
        "मुझे चाहिए" to "order", "मुझे लेना है" to "order",
        "मँगवाना है" to "order", "भिजवाओ" to "order",
        "order करना है" to "order", "ऑर्डर करना है" to "order",
        "लेना है" to "order", "चाहिए" to "order",
        "मंगाओ" to "order", "घर भेजो" to "order",
        // Telugu
        "కావాలి" to "order", "తీసుకోవాలి" to "order",
        "ఆర్డర్ పెట్టాలి" to "order", "పంపించాలి" to "order",
        // Kannada
        "ಬೇಕಾಗಿದೆ" to "order", "ತರಬೇಕು" to "order",
        "ಆರ್ಡರ್ ಮಾಡಬೇಕು" to "order",
        // Tamil
        "வேண்டும்" to "order", "வாங்கணும்" to "order",
        "ஆர்டர் பண்ணணும்" to "order",
        // Malayalam
        "വേണം" to "order", "വാങ്ങണം" to "order",
        "ഓർഡർ ചെയ്യണം" to "order",
        // Punjabi
        "ਚਾਹੀਦਾ" to "order", "ਲੈਣਾ ਹੈ" to "order",
        "ਮੰਗਵਾਉਣਾ ਹੈ" to "order",
        // Odia
        "ଦରକାର" to "order", "ନେବାକୁ ଅଛି" to "order",
        "ଅର୍ଡର କରିବାକୁ ଅଛି" to "order"
    )

    // ─── Warm contextual greetings ───────────────────────────────────────────

    fun getWelcomeGreeting(lang: String, name: String = ""): String {
        val n = if (name.isNotBlank()) " $name" else ""
        return when (lang) {
            "hi" -> listOf(
                "हाँ$n बोलो! क्या मँगाना है आज?",
                "नमस्ते$n! बताओ क्या चाहिए?",
                "अरे वाह$n! बोलो क्या लेना है?",
                "जी$n हाँ, बोलिए क्या चाहिए?"
            ).random()
            "te" -> listOf(
                "నమస్కారం$n! ఏమి కావాలి?",
                "చెప్పండి$n! ఏమి order చేయాలి?",
                "అడగండి$n! ఏమి తీసుకోవాలి?"
            ).random()
            "kn" -> listOf(
                "ನಮಸ್ಕಾರ$n! ಏನು ಬೇಕು?",
                "ಹೇಳಿ$n! ಇಂದು ಏನು ತರಲಿ?",
                "ಒಳ್ಳೇದು$n! ಏನು order ಮಾಡಬೇಕು?"
            ).random()
            "ta" -> listOf(
                "வணக்கம்$n! என்ன வேணும்?",
                "சொல்லுங்கள்$n! என்ன order பண்ணணும்?",
                "கேளுங்கள்$n! இன்னைக்கு என்ன வேணும்?"
            ).random()
            "ml" -> listOf(
                "നമസ്കാരം$n! എന്ത് വേണം?",
                "പറയൂ$n! ഇന്ന് എന്ത് order ചെയ്യണം?",
                "ചോദിക്കൂ$n! എന്ത് കൊണ്ടുവരണം?"
            ).random()
            "pa" -> listOf(
                "ਸਤ ਸ੍ਰੀ ਅਕਾਲ$n! ਕੀ ਚਾਹੀਦਾ?",
                "ਦੱਸੋ$n! ਕੀ ਮੰਗਵਾਉਣਾ ਹੈ?",
                "ਬੋਲੋ$n! ਅੱਜ ਕੀ ਲੈਣਾ ਹੈ?"
            ).random()
            "or" -> listOf(
                "ନମସ୍କାର$n! କଣ ଦରକାର?",
                "କୁହନ୍ତୁ$n! ଆଜି କଣ order ହବ?"
            ).random()
            else -> if (name.isNotBlank()) "Welcome back $name! What would you like today?"
            else "Welcome! What would you like to order?"
        }
    }

    fun getOrderConfirmation(lang: String, name: String = "", orderId: String = ""): String {
        // ── FIX: Name goes FIRST so TTS reads naturally ───────────────────
        // Before: "ऑर्डर दे दिया Roy!" → TTS says "Or de diya Roy" ❌
        // After:  "Roy, आपका order हो गया!" → TTS says correctly ✅
        //
        // "ऑर्डर" removed entirely — TTS normalizer in TTSManager handles
        // any remaining occurrences, but source text uses "order" (Latin)
        // which ElevenLabs always pronounces correctly.
        val prefix = if (name.isNotBlank()) "$name, " else ""
        val id     = if (orderId.isNotBlank()) " ID: $orderId." else ""
        return when (lang) {
            "hi" -> "${prefix}आपका order हो गया!$id थोड़ी देर में घर पहुँच जाएगा। शुक्रिया!"
            "te" -> "${prefix}మీ order అయిపోయింది!$id కొంచెం సేపట్లో వస్తుంది. ధన్యవాదాలు!"
            "kn" -> "${prefix}ನಿಮ್ಮ order ಆಯ್ತು!$id ಬೇಗ ತಲುಪಿಸುತ್ತೇವೆ. ಧನ್ಯವಾದ!"
            "ta" -> "${prefix}உங்கள் order ஆச்சு!$id கொஞ்ச நேரத்தில் வரும். நன்றி!"
            "ml" -> "${prefix}നിങ്ങളുടെ order ആയി!$id ഉടൻ എത്തും. നന്ദി!"
            "pa" -> "${prefix}ਤੁਹਾਡਾ order ਹੋ ਗਿਆ!$id ਜਲਦੀ ਪਹੁੰਚ ਜਾਵੇਗਾ. ਧੰਨਵਾਦ!"
            "or" -> "${prefix}ଆପଣଙ୍କ order ହୋଇଗଲା!$id ଶୀଘ୍ର ପହଞ୍ଚିବ. ଧନ୍ୟବାଦ!"
            else -> "${prefix}Your order is placed!$id Arriving soon. Thank you!"
        }
    }

    // ─── Normalise regional product name to English ──────────────────────────

    fun normalizeProduct(text: String): String {
        val lower = text.lowercase().trim()
        return REGIONAL_PRODUCT_MAP[lower]
            ?: REGIONAL_PRODUCT_MAP[text.trim()]
            ?: lower
    }

    // ─── Detect intent from colloquial phrase ────────────────────────────────

    fun detectIntent(text: String): String {
        for ((phrase, intent) in ORDER_INTENT_PHRASES) {
            if (text.contains(phrase, ignoreCase = true)) return intent
        }
        if (DONE_PHRASES.any { text.contains(it, ignoreCase = true) }) return "finish"
        if (YES_PHRASES.any  { text.contains(it, ignoreCase = true) }) return "confirm"
        if (NO_PHRASES.any   { text.contains(it, ignoreCase = true) }) return "cancel"
        return "unknown"
    }
}