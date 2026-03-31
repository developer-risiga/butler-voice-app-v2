package com.demo.butler_voice_app.services

import android.content.Context
import android.util.Log
import com.demo.butler_voice_app.ai.LanguageManager

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE VOICE HANDLER
// All voice prompts + sub-type conversation for India services platform
// ══════════════════════════════════════════════════════════════════════════════

object ServiceVoiceHandler {

    private const val TAG = "ServiceVoiceHandler"

    // ══════════════════════════════════════════════════════════════════════════
    // SUB-TYPE TAXONOMY
    // For each ServiceSector that benefits from clarification,
    // we define sub-types with keywords for voice matching.
    // ══════════════════════════════════════════════════════════════════════════

    data class SubType(
        val id: String,
        val displayEn: String,
        val displayHi: String,
        val displayTe: String,
        val keywords: List<String>   // fuzzy-match from transcript (lowercase)
    ) {
        fun getDisplay(lang: String): String = when {
            lang.startsWith("hi") -> displayHi
            lang.startsWith("te") -> displayTe
            else -> displayEn
        }
    }

    // Map: ServiceSector.name → list of sub-types
    val SUB_TYPES: Map<String, List<SubType>> = mapOf(

        "PLUMBER" to listOf(
            SubType("bath_fitting",   "Bath fitting",       "बाथ फिटिंग",      "బాత్ ఫిటింగ్",   listOf("bath","shower","geyser","naha","fitting","बाथ","नहाना","बाथरूम फिटिंग","shower fitting")),
            SubType("toilet_repair",  "Toilet repair",      "टॉयलेट रिपेयर",   "టాయ్‌లెట్",       listOf("toilet","commode","flush","potty","टॉयलेट","टायलेट","फ्लश","कमोड","toilet kharab")),
            SubType("basin_sink",     "Basin & sink",       "बेसिन / सिंक",    "బేసిన్",          listOf("basin","sink","washbasin","बेसिन","sink","नल","टोंटी")),
            SubType("pipe_leak",      "Pipe leak",          "पाइप लीक",        "పైపు లీక్",       listOf("leak","pipe","water","drip","टपक","पाइप","लीक","rissa","पानी टपक","पाइप टूट","लीकेज")),
            SubType("water_tank",     "Water tank",         "वॉटर टैंक",        "వాటర్ ట్యాంక్",  listOf("tank","overhead","टैंक","पानी","water tank","टंकी","पानी की टंकी")),
            SubType("drainage",       "Drainage blockage",  "ड्रेनेज",          "డ్రైనేజ్",        listOf("drain","blocked","choke","overflow","ड्रेन","जाम","नाली","नाली बंद","बंद नाली"))
        ),

        "ELECTRICIAN" to listOf(
            SubType("fan_light",      "Fan / light fitting","फैन / लाइट",       "ఫ్యాన్ / లైట్",   listOf("fan","light","bulb","tube","फैन","लाइट","बल्ब","tube light","पंखा","बत्ती","पंखा नहीं चलता","लाइट नहीं")),
            SubType("switchboard",    "Switchboard",        "स्विचबोर्ड",       "స్విచ్‌బోర్డ్",   listOf("switch","board","plug","socket","स्विच","plug point","बोर्ड","स्विचबोर्ड","socket nahi")),
            SubType("ac_repair",      "AC repair",          "AC रिपेयर",        "AC రిపేర్",        listOf("ac","air condition","cooling","एसी","air conditioner","cooling nahi","ठंडा नहीं","एसी खराब","AC नहीं चलता")),
            SubType("wiring_fault",   "Wiring fault",       "वायरिंग फॉल्ट",   "వైరింగ్",          listOf("wire","wiring","short","trip","वायर","short circuit","spark","current","बिजली नहीं","ट्रिप","शॉर्ट सर्किट")),
            SubType("meter_issue",    "Meter / bill issue", "मीटर / बिल",      "మీటర్",            listOf("meter","bill","ebill","मीटर","बिजली बिल","electricity bill","मीटर खराब","बिल ज्यादा")),
            SubType("inverter",       "Inverter / UPS",     "इन्वर्टर",         "ఇన్వర్టర్",        listOf("inverter","ups","battery","backup","इन्वर्टर","बैटरी","इनवर्टर","backup nahi"))
        ),

        "CARPENTER" to listOf(
            SubType("door_window",    "Door / window",      "दरवाजा / खिड़की", "తలుపు",           listOf("door","window","darwaza","दरवाजा","खिड़की","hinge","lock","shutter","दरवाजा खराब","दरवाजा नहीं खुलता")),
            SubType("furniture_repair","Furniture repair",  "फर्नीचर रिपेयर",  "ఫర్నిచర్",        listOf("furniture","sofa","chair","table","फर्नीचर","repair","तोड़ा","टूट","कुर्सी","मेज टूट")),
            SubType("wardrobe",       "Wardrobe / almirah", "अलमारी",           "వార్డ్‌రోబ్",     listOf("wardrobe","almirah","cupboard","अलमारी","closet","shelf","रैक","almari","अलमारी खराब")),
            SubType("bed_frame",      "Bed frame",          "पलंग / बेड",       "మంచం",            listOf("bed","palang","cot","खाट","पलंग","divan","mattress","gadda","बेड टूट","पलंग खराब")),
            SubType("false_ceiling",  "False ceiling",      "फॉल्स सीलिंग",    "ఫాల్స్ సీలింగ్", listOf("ceiling","false","pop","plywood","ceiling repair","छत","सीलिंग","POP"))
        ),

        "PAINTER" to listOf(
            SubType("full_room",      "Full room painting", "पूरा कमरा",        "పూర్తి గది",      listOf("full","room","wall","पूरा","कमरा","दीवार","whole","complete","पूरा घर रंग","पूरा कमरा")),
            SubType("touch_up",       "Touch-up / patch",   "टच-अप",            "టచ్-అప్",         listOf("touch","small","patch","थोड़ा","छोटा","spot","dab","थोड़ा सा","छोटा काम")),
            SubType("waterproofing",  "Waterproofing",      "वॉटरप्रूफिंग",     "వాటర్‌ప్రూఫింగ్", listOf("waterproof","leak","seepage","water","छत से पानी","baarish","वाटरप्रूफ","लीकेज")),
            SubType("exterior",       "Exterior / outside", "बाहरी पेंट",       "బాహ్య పెయింట్",   listOf("outside","exterior","outer","bahar","घर के बाहर","compound","बाहर से","बाहरी")),
            SubType("texture",        "Texture / design",   "टेक्सचर पेंट",     "టెక్స్చర్",       listOf("texture","design","pattern","fancy","designer","textured","टेक्सचर","डिज़ाइन"))
        ),

        "CLEANING" to listOf(
            SubType("deep_clean",     "Full home deep clean","पूरा घर",          "పూర్తి ఇల్లు",   listOf("full","deep","home","पूरा","घर","deep clean","whole house","sabkuch","पूरे घर की सफाई","घर साफ")),
            SubType("sofa_carpet",    "Sofa / carpet",      "सोफा / कारपेट",    "సోఫా",            listOf("sofa","carpet","rug","couch","सोफा","कारपेट","गद्दा","सोफा साफ","carpet clean")),
            SubType("kitchen",        "Kitchen",            "किचन",              "వంటగది",          listOf("kitchen","chimney","stove","hob","किचन","rasoi","रसोई","chimney","किचन साफ","रसोई साफ")),
            SubType("bathroom",       "Bathroom",           "बाथरूम",            "బాత్‌రూమ్",      listOf("bathroom","toilet clean","बाथरूम","washroom","tiles","बाथरूम साफ","टाइल्स साफ")),
            SubType("post_construction","Post-construction","कंस्ट्रक्शन के बाद","నిర్మాణం తర్వాత",listOf("construction","renovation","after build","kaam","नया घर","new house","नया मकान","renovation"))
        ),

        "AC_REPAIR" to listOf(
            SubType("ac_service",     "AC servicing",       "AC सर्विसिंग",     "AC సర్వీసింగ్",   listOf("service","servicing","cleaning","gas","सर्विस","साफ","wash")),
            SubType("ac_not_cooling", "AC not cooling",     "AC ठंडा नहीं",    "AC చల్లగా లేదు",  listOf("not cooling","warm","hot","ठंडा नहीं","cooling nahi","गर्म","heat")),
            SubType("ac_install",     "AC installation",    "AC installation",  "AC ఇన్‌స్టాల్",   listOf("install","new","fit","लगाना","install","नया","new ac")),
            SubType("ac_gas_refill",  "Gas refill",         "गैस भरवाना",       "గ్యాస్ నింపడం",   listOf("gas","refill","recharge","गैस","भरना","regas")),
            SubType("washing_machine","Washing machine",    "वाशिंग मशीन",      "వాషింగ్ మిషన్",  listOf("washing","machine","washer","धुलाई","वाशिंग","kapde")),
            SubType("fridge",         "Refrigerator",       "फ्रिज",             "ఫ్రిడ్జ్",         listOf("fridge","refrigerator","cooling","फ्रिज","freezer")),
            SubType("microwave",      "Microwave / OTG",    "माइक्रोवेव",        "మైక్రోవేవ్",      listOf("microwave","oven","otg","माइक्रोवेव","bake")),
            SubType("geyser",         "Geyser / water heater","गीज़र",           "గీజర్",            listOf("geyser","water heater","hot water","गीज़र","गर्म पानी"))
        ),

        "DOCTOR" to listOf(
            SubType("general_physician","General physician","सामान्य डॉक्टर",  "జనరల్ డాక్టర్",  listOf("general","fever","cough","cold","medicine","बुखार","खांसी","सर्दी","बीमार","डॉक्टर चाहिए","सामान्य","general doctor","bukhar","khansi")),
            SubType("dermatologist",  "Skin specialist",    "स्किन डॉक्टर",    "స్కిన్ స్పెషలిస్ట్",listOf("skin","rash","acne","allergy","itching","स्किन","चर्म","खुजली","दाने","चेहरे पर","skin problem")),
            SubType("pediatrician",   "Child doctor",       "बच्चों के डॉक्टर","చిన్నారుల డాక్టర్",listOf("child","baby","kids","बच्चे","children","बच्चा","infant","बच्चों का डॉक्टर","छोटे बच्चे")),
            SubType("orthopedic",     "Bone / joint doctor","हड्डी / जोड़",    "ఎముక డాక్టర్",    listOf("bone","joint","knee","back","hadi","घुटना","पीठ","कमर","leg","हड्डी","जोड़","घुटने में दर्द","कमर दर्द")),
            SubType("gynecologist",   "Gynecologist",       "महिला डॉक्टर",    "స్త్రీ రోగ వైద్యుడు",listOf("women","pregnancy","periods","gynec","महिला","गर्भ","mc","महिला डॉक्टर","ladies doctor","प्रेगनेंसी")),
            SubType("dentist",        "Dentist",            "दांत के डॉक्टर",  "దంత వైద్యుడు",    listOf("tooth","teeth","dental","dant","दांत","cavity","toothache","दांत दर्द","दांत का डॉक्टर","मसूड़े"))
        ),

        "MEDICINE" to listOf(
            SubType(
                "prescription",   "Upload prescription", "पर्ची से दवाई",   "ప్రిస్క్రిప్షన్",
                listOf(
                    // English / romanized
                    "prescription","parchi","parchee","doctor ne likha","upload","rx","doctor ka parcha",
                    // Hindi Devanagari — the critical additions
                    "पर्ची","पर्ची से","पर्ची से दवाई","डॉक्टर की पर्ची","दवाई की पर्ची","पर्चा",
                    "अपलोड","पर्ची अपलोड",
                    // Telugu
                    "ప్రిస్క్రిప్షన్","చీటీ","చీటీ ద్వారా"
                )
            ),
            SubType(
                "otc_medicine",   "Without prescription", "बिना पर्ची",     "ప్రిస్క్రిప్షన్ లేకుండా",
                listOf(
                    // English / romanized
                    "paracetamol","medicine","tablet","syrup","dawa","without","direct","otc","crocin","dolo",
                    // Hindi Devanagari
                    "बिना पर्ची","बिना","खुद","सीधे","बिना डॉक्टर","दवाई चाहिए","टेबलेट","सिरप","दवा",
                    // Telugu
                    "మందు","నేరుగా","ప్రిస్క్రిప్షన్ లేకుండా"
                )
            ),
            SubType(
                "vitamins",       "Vitamins & supplements","विटामिन",       "విటమిన్లు",
                listOf(
                    // English / romanized
                    "vitamin","supplement","protein","calcium","iron","multivitamin","zinc","omega","health supplement",
                    // Hindi Devanagari
                    "विटामिन","सप्लीमेंट","प्रोटीन","कैल्शियम","आयरन","मल्टीविटामिन","स्वास्थ्य",
                    // Telugu
                    "విటమిన్","సప్లిమెంట్"
                )
            ),
            SubType(
                "baby_products",  "Baby / infant",      "बच्चों की दवाई",  "శిశు ఔషధం",
                listOf(
                    // English / romanized
                    "baby","infant","diaper","gripe water","cerelac","newborn","child medicine","kids medicine",
                    // Hindi Devanagari
                    "बच्चों की दवाई","बच्चे की दवा","शिशु","नवजात","बच्चे के लिए","छोटे बच्चे",
                    // Telugu
                    "శిశువు","పిల్లల మందు","బేబీ"
                )
            )
        ),

        "FOOD" to listOf(
            SubType("biryani",        "Biryani",            "बिरयानी",          "బిర్యానీ",         listOf("biryani","biriyani","rice","hyderabadi","dum")),
            SubType("thali",          "Thali / meals",      "थाली / खाना",     "థాలీ",             listOf("thali","meal","lunch","dinner","khana","full meal","తినడం")),
            SubType("tiffin",         "Tiffin / breakfast", "टिफिन",            "టిఫిన్",           listOf("tiffin","breakfast","idli","dosa","upma","poha","idly","vada")),
            SubType("chinese",        "Chinese",            "चाइनीज़",          "చైనీస్",           listOf("chinese","noodles","fried rice","manchurian","chowmein")),
            SubType("pizza_burger",   "Pizza / burger",     "पिज्जा / बर्गर",  "పిజ్జా / బర్గర్",  listOf("pizza","burger","sandwich","sub","wrap")),
            SubType("sweets",         "Sweets / mithai",    "मिठाई",            "స్వీట్లు",         listOf("sweet","mithai","ladoo","barfi","gulab jamun","halwa","dessert"))
        ),

        "TAXI" to listOf(
            SubType("local",          "Local ride",         "लोकल राइड",        "లోకల్ రైడ్",       listOf("local","nearby","short","thoda","paas","near","market")),
            SubType("outstation",     "Outstation",         "बाहर शहर",         "అవుట్‌స్టేషన్",   listOf("outstation","out of city","long","bahar","city se bahar","trip")),
            SubType("airport",        "Airport drop / pick","एयरपोर्ट",         "విమానాశ్రయం",     listOf("airport","flight","terminal","aeroplane","plane")),
            SubType("rental",         "Rental (hourly)",    "किराए पर",         "అద్దె",            listOf("rental","hire","few hours","rent","hours","time","poora din"))
        ),

        "SALON" to listOf(
            SubType("haircut",        "Haircut",            "बाल कटाई",         "జుట్టు కత్తిరించడం",listOf("haircut","cut","trim","बाल","kataai","hairstyle","shave","daadi")),
            SubType("facial",         "Facial / cleanup",   "फेशियल",           "ఫేషియల్",          listOf("facial","cleanup","face","glow","skin","फेशियल","face clean")),
            SubType("waxing",         "Waxing / threading", "वैक्सिंग / थ्रेडिंग","వాక్సింగ్",     listOf("wax","waxing","threading","eyebrow","upperlip","वैक्सिंग","भौंह")),
            SubType("mehendi",        "Mehendi",            "मेहंदी",            "మెహందీ",           listOf("mehendi","henna","मेहंदी","bridal","design")),
            SubType("makeup",         "Makeup / bridal",    "मेकअप",             "మేకప్",            listOf("makeup","bridal","party","function","make up","मेकअप"))
        ),

        "CAR_SERVICE" to listOf(
            SubType("car_wash",       "Car wash",           "कार धोना",         "కారు కడగడం",       listOf("wash","clean","धोना","car wash","exterior","interior")),
            SubType("oil_change",     "Oil change",         "तेल बदलना",        "ఆయిల్ మార్పు",    listOf("oil","change","filter","service","तेल बदलना","engine oil")),
            SubType("tyre_puncture",  "Tyre / puncture",    "टायर / पंचर",     "టైర్",              listOf("tyre","tire","puncture","flat","पंचर","टायर","wheel")),
            SubType("mechanic",       "General mechanic",   "मैकेनिक",          "మెకానిక్",         listOf("mechanic","repair","engine","problem","issue","मैकेनिक","gaadi"))
        ),

        "TUTOR" to listOf(
            SubType("maths",          "Maths",              "गणित",              "గణితం",            listOf("maths","math","numbers","algebra","geometry","गणित","calculation")),
            SubType("science",        "Science",            "विज्ञान",           "విజ్ఞానం",         listOf("science","physics","chemistry","biology","विज्ञान")),
            SubType("english",        "English",            "अंग्रेज़ी",          "ఆంగ్లం",           listOf("english","grammar","speaking","essay","अंग्रेज़ी","spoken")),
            SubType("computer",       "Computer / coding",  "कंप्यूटर",          "కంప్యూటర్",        listOf("computer","coding","programming","excel","word","कंप्यूटर")),
            SubType("all_subjects",   "All subjects",       "सभी विषय",          "అన్ని సబ్జెక్టులు",listOf("all","tutor","coaching","tuition","homework","सभी","guide"))
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // SUB-TYPE PROMPTS — spoken by Butler to clarify
    // ══════════════════════════════════════════════════════════════════════════

    fun buildSubTypePrompt(sector: ServiceSector, lang: String): String {
        val subTypes = SUB_TYPES[sector.name] ?: return buildSectorDetectedPrompt(sector, lang)
        val options  = subTypes.take(4)
        return when {
            lang.startsWith("hi") -> {
                val optList = options.joinToString(", ") { it.displayHi }
                "${sector.displayName} के लिए किस काम की ज़रूरत है? $optList — कौन सा?"
            }
            lang.startsWith("te") -> {
                val optList = options.joinToString(", ") { it.displayTe }
                "${sector.displayName} కోసం ఏ పని కావాలి? $optList?"
            }
            else -> {
                val optList = options.joinToString(", ") { it.displayEn }
                "Which ${sector.displayName.lowercase()} service? $optList?"
            }
        }
    }

    fun buildSubTypeRetryPrompt(sector: ServiceSector, lang: String): String {
        val subTypes = SUB_TYPES[sector.name] ?: return "please say again?"
        val top3 = subTypes.take(3)
        return when {
            lang.startsWith("hi") -> {
                val ops = top3.joinToString(", या ") { it.displayHi }
                "समझा नहीं। $ops — इनमें से कौन सा?"
            }
            lang.startsWith("te") -> {
                val ops = top3.joinToString(", లేదా ") { it.displayTe }
                "అర్థం కాలేదు. $ops — వీటిలో ఏది?"
            }
            else -> {
                val ops = top3.joinToString(", or ") { it.displayEn }
                "Sorry, didn't catch that. $ops — which one?"
            }
        }
    }

    fun matchSubType(transcript: String, sector: ServiceSector): SubType? {
        val subTypes = SUB_TYPES[sector.name] ?: return null
        val lower = transcript.lowercase()
        // Score each sub-type by how many keywords match
        return subTypes.mapNotNull { sub ->
            val score = sub.keywords.count { kw -> lower.contains(kw.lowercase()) }
            if (score > 0) Pair(sub, score) else null
        }.maxByOrNull { it.second }?.first
    }

    fun hasSectorSubTypes(sector: ServiceSector): Boolean =
        SUB_TYPES.containsKey(sector.name)

    fun buildBookingConfirmPrompt(sector: ServiceSector, subType: SubType?, timeSlot: String?, lang: String): String {
        val serviceDisplay = subType?.getDisplay(lang) ?: sector.displayName
        val timeDisplay = timeSlot ?: when {
            lang.startsWith("hi") -> "आज"
            lang.startsWith("te") -> "ఈరోజు"
            else -> "today"
        }
        return when {
            lang.startsWith("hi") ->
                "$serviceDisplay, $timeDisplay। बुक करूँ?"
            lang.startsWith("te") ->
                "$serviceDisplay, $timeDisplay. బుక్ చేయనా?"
            else ->
                "$serviceDisplay, $timeDisplay. Shall I book?"
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXISTING METHODS — unchanged
    // ══════════════════════════════════════════════════════════════════════════

    private val SERVICE_TRIGGER_WORDS = listOf(
        "book","need","find","call","hire","service","services",
        "chahiye","bulao","dhundho","service chahiye","kaam",
        "కావాలి","పిలవండి","సేవ",
        "plumber","electrician","doctor","medicine","dawa","taxi",
        "food","khana","salon","carpenter","painter","cleaner",
        "maid","driver","tutor","lawyer","ca","insurance","loan",
        "nurse","ambulance","gym","trainer","pet","pandit",
        "courier","laundry","tailor","photographer","catering"
    )

    fun isServiceRequest(transcript: String): Boolean {
        val lower = transcript.lowercase()
        return SERVICE_TRIGGER_WORDS.any { lower.contains(it) } &&
                ServiceManager.detectServiceIntent(transcript).sector != null
    }

    fun isPrescriptionRequest(transcript: String): Boolean {
        val lower = transcript.lowercase()
        val rxWords = listOf(
            "prescription","parchi","parchee","dawai ki parchee",
            "doctor ne likha","upload prescription","medicines from prescription",
            "scan prescription","read prescription"
        )
        return rxWords.any { lower.contains(it) }
    }

    fun buildServiceCategoryPrompt(lang: String = "en"): String = when {
        lang.startsWith("hi") ->
            "आप कौन सी सेवा चाहते हैं? जैसे प्लंबर, डॉक्टर, खाना, दवाई, टैक्सी, या कोई और?"
        lang.startsWith("te") ->
            "మీకు ఏ సేవ కావాలి? ప్లంబర్, డాక్టర్, తినుబండారాలు, మందులు లేదా ఏదైనా?"
        lang.startsWith("ta") ->
            "உங்களுக்கு என்ன சேவை வேண்டும்? பிளம்பர், டாக்டர், உணவு, மருந்து?"
        else ->
            "Which service do you need? Say plumber, doctor, food, medicine, taxi, or any other service."
    }

    fun buildSectorDetectedPrompt(sector: ServiceSector, lang: String = "en"): String = when {
        lang.startsWith("hi") -> "मैं ${sector.displayName} के लिए आपके पास के providers ढूंढ रहा हूं।"
        lang.startsWith("te") -> "${sector.displayName} కోసం మీ దగ్గర ఉన్న providers కనుగొంటున్నాను।"
        else -> "Finding ${sector.displayName} providers near you. Please wait a moment."
    }

    fun buildProviderSelectedPrompt(provider: ServiceProvider, lang: String = "en"): String = when {
        lang.startsWith("hi") ->
            "${provider.name} चुना गया। वो ${provider.distanceKm} किलोमीटर दूर हैं और ${provider.eta} में आ सकते हैं। क्या बुक करूं?"
        else ->
            "You've selected ${provider.name}, ${provider.distanceKm}km away, arriving in ${provider.eta}. Shall I confirm the booking? Say yes to confirm or no to see other options."
    }

    fun buildBookingConfirmedPrompt(provider: ServiceProvider, bookingId: String, lang: String = "en"): String = when {
        lang.startsWith("hi") ->
            "${provider.name} की बुकिंग हो गई। Booking ID है $bookingId। वो ${provider.eta} में पहुंच जाएंगे। धन्यवाद!"
        else ->
            "${provider.name} has been booked! Your booking ID is $bookingId. They'll arrive in ${provider.eta}. Is there anything else you need?"
    }

    fun buildPrescriptionUploadPrompt(lang: String = "en"): String = when {
        lang.startsWith("hi") ->
            "अपनी prescription की फोटो लें या gallery से upload करें। मैं medicines का नाम पढ़ लूंगा।"
        else ->
            "Please photograph your prescription or upload it from your gallery. I'll read the medicine names automatically and find the nearest pharmacy."
    }

    fun buildMedicinesExtractedPrompt(medicines: List<String>, pharmacyCount: Int, lang: String = "en"): String {
        val medNames = when {
            medicines.isEmpty() -> "the medicines"
            medicines.size == 1 -> medicines[0]
            medicines.size <= 3 -> medicines.dropLast(1).joinToString(", ") + " and " + medicines.last()
            else -> "${medicines.take(2).joinToString(", ")} and ${medicines.size - 2} more"
        }
        return when {
            lang.startsWith("hi") ->
                "मैंने prescription से $medNames पढ़ा। $pharmacyCount pharmacies आपके पास ये दे सकती हैं। 1, 2, या 3 बोलो।"
            else ->
                "I've read your prescription and found $medNames. $pharmacyCount nearby pharmacies can deliver these. Say 1, 2, or 3 to choose."
        }
    }

    fun parseFilterCommand(transcript: String): ServiceFilter? {
        val lower = transcript.lowercase()
        return when {
            lower.contains("by rating") || lower.contains("best rated") || lower.contains("highest rated") ->
                ServiceFilter(sortBy = ServiceSort.RATING)
            lower.contains("nearest") || lower.contains("closest") || lower.contains("paas wala") ->
                ServiceFilter(sortBy = ServiceSort.DISTANCE)
            lower.contains("cheapest") || lower.contains("cheap") || lower.contains("sasta") || lower.contains("budget") ->
                ServiceFilter(sortBy = ServiceSort.PRICE_LOW)
            lower.contains("fastest") || lower.contains("quick") || lower.contains("jaldi") ->
                ServiceFilter(sortBy = ServiceSort.FASTEST)
            lower.contains("premium") || lower.contains("best") ->
                ServiceFilter(minRating = 4.5, sortBy = ServiceSort.RATING)
            else -> null
        }
    }

    fun isEmergency(transcript: String): Boolean {
        val lower = transcript.lowercase()
        return listOf(
            "emergency","ambulance","help me","bachao","accident","unconscious",
            "heart attack","stroke","fire","danger","urgent help"
        ).any { lower.contains(it) }
    }

    fun buildEmergencyPrompt(lang: String = "en"): String = when {
        lang.startsWith("hi") -> "आपातकाल! 108 को call कर रहा हूं। कृपया शांत रहें।"
        else -> "EMERGENCY! Contacting 108 ambulance service now. Please stay calm and stay on the line."
    }

    fun parseNumberSelection(transcript: String): Int? {
        val lower = transcript.lowercase().trim()
        return when {
            lower.contains("one")   || lower.contains("एक")   || lower.contains("ఒకటి") || lower == "1" -> 1
            lower.contains("two")   || lower.contains("दो")   || lower.contains("రెండు") || lower == "2" -> 2
            lower.contains("three") || lower.contains("तीन")  || lower.contains("మూడు")  || lower == "3" -> 3
            lower.contains("first") || lower.contains("pehla")  -> 1
            lower.contains("second")|| lower.contains("doosra") -> 2
            lower.contains("third") || lower.contains("teesra") -> 3
            else -> lower.filter { it.isDigit() }.firstOrNull()?.toString()?.toIntOrNull()
        }
    }
}