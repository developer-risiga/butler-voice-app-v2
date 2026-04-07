package com.demo.butler_voice_app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class EmotionTone {
    EMERGENCY, EMPATHETIC, NORMAL, WARM, EXCITED
}

class TTSManager(
    private val context: Context,
    private val elevenLabsApiKey: String,
    private val voiceId: String = "K2Byg54sHB1oHegvENtI"
) {

    companion object {
        private const val TAG          = "TTS"
        private const val VOICE_EN     = "K2Byg54sHB1oHegvENtI"
        private const val VOICE_HI     = "K2Byg54sHB1oHegvENtI"
        private const val ELEVEN_MODEL = "eleven_flash_v2_5"
        private val DEVANAGARI         = Regex("[\\u0900-\\u097F]")

        private val ORDER_ID_REGEX = Regex("""\b[A-Z]{2,5}-0*(\d{1,6})\b""")
        private val DIGIT_WORDS = mapOf(
            '0' to "zero", '1' to "ek",    '2' to "do",
            '3' to "teen", '4' to "chaar", '5' to "paanch",
            '6' to "chhe", '7' to "saat",  '8' to "aath", '9' to "nau"
        )

        private val RUPEE_REGEX = Regex("₹(\\d+)")

        private val HINDI_1_TO_99 = mapOf(
            1  to "ek",          2  to "do",          3  to "teen",        4  to "chaar",
            5  to "paanch",      6  to "chhe",         7  to "saat",        8  to "aath",
            9  to "nau",         10 to "das",          11 to "gyarah",      12 to "barah",
            13 to "terah",       14 to "chaudah",      15 to "pandrah",     16 to "solah",
            17 to "satrah",      18 to "atharah",      19 to "unnees",      20 to "bees",
            21 to "ikkees",      22 to "baees",        23 to "teis",        24 to "chaubees",
            25 to "pachees",     26 to "chhabbees",    27 to "sattaees",    28 to "athaais",
            29 to "unatis",      30 to "tees",         31 to "ikatees",     32 to "battees",
            33 to "taintees",    34 to "chauntees",    35 to "paintees",    36 to "chhattees",
            37 to "saintees",    38 to "adtees",       39 to "unchaalis",   40 to "chaalis",
            41 to "ikataalees",  42 to "bayaalees",    43 to "taintaalees", 44 to "chauvaalees",
            45 to "paintaalees", 46 to "chhiyaalees",  47 to "saintaalees", 48 to "adtaalees",
            49 to "unchaas",     50 to "pachaas",      51 to "ikkaavan",    52 to "baavan",
            53 to "tirpan",      54 to "chauvan",      55 to "pachpan",     56 to "chhappan",
            57 to "sattavan",    58 to "atthaavan",    59 to "unsath",      60 to "saath",
            61 to "iksath",      62 to "baasath",      63 to "tirsath",     64 to "chaunsath",
            65 to "painsath",    66 to "chhiyasath",   67 to "sadsath",     68 to "adsath",
            69 to "unattar",     70 to "sattar",       71 to "ikhattar",    72 to "bahattar",
            73 to "tihattar",    74 to "chauhattar",   75 to "pachattar",   76 to "chhihattar",
            77 to "satattar",    78 to "atthattar",    79 to "unnasi",      80 to "assi",
            81 to "ikkyaasi",    82 to "bayasi",       83 to "tirasi",      84 to "chaurasi",
            85 to "pachasi",     86 to "chhiyasi",     87 to "sattaasi",    88 to "atthasi",
            89 to "navasi",      90 to "nabbe",        91 to "ikyaanave",   92 to "baanave",
            93 to "tianave",     94 to "chauranave",   95 to "pachaanave",  96 to "chhiyanave",
            97 to "satanave",    98 to "atthanave",    99 to "ninyaanave"
        )

        private val HINDI_HUNDREDS = mapOf(
            1 to "ek sau",     2 to "do sau",     3 to "teen sau",   4 to "chaar sau",
            5 to "paanch sau", 6 to "chhe sau",   7 to "saat sau",   8 to "aath sau",
            9 to "nau sau"
        )

        private fun amountToHindi(n: Int): String {
            if (n <= 0) return n.toString()
            if (n < 100)    return HINDI_1_TO_99[n] ?: n.toString()
            if (n < 1000) {
                val h = n / 100; val r = n % 100
                val hStr = HINDI_HUNDREDS[h] ?: return n.toString()
                return if (r == 0) hStr else "$hStr ${HINDI_1_TO_99[r] ?: r}"
            }
            if (n < 100_000) {
                val hazaarPart = n / 1000; val remainder = n % 1000
                val hazaarWord = HINDI_1_TO_99[hazaarPart] ?: hazaarPart.toString()
                val base       = "$hazaarWord hazaar"
                if (remainder == 0) return base
                if (remainder < 100) return "$base ${HINDI_1_TO_99[remainder] ?: remainder}"
                val remH = remainder / 100; val remR = remainder % 100
                val remHStr = HINDI_HUNDREDS[remH] ?: return "$base $remainder"
                return if (remR == 0) "$base $remHStr"
                else "$base $remHStr ${HINDI_1_TO_99[remR] ?: remR}"
            }
            return n.toString()
        }

        fun rupeeToSpoken(amount: Int, language: String): String = when {
            language.startsWith("hi") || language.startsWith("mr") ->
                "${amountToHindi(amount)} रुपये"
            language.startsWith("gu") || language.startsWith("pa") ->
                "${amountToHindi(amount)} rupiya"
            language.startsWith("te") -> "$amount rupayalu"
            language.startsWith("ta") -> "$amount rubai"
            language.startsWith("kn") -> "$amount rupai"
            language.startsWith("ml") -> "$amount rupay"
            else -> "$amount rupees"
        }

        private val DEVANAGARI_TO_ROMAN = linkedMapOf(
            "घबराइए मत"       to "ghabraiye mat",
            "समझ नहीं आया"    to "samajh nahi aaya",
            "फिर से बोलिए"    to "phir se boliye",
            "फिर से बोलें"    to "phir se bolein",
            "कुछ सुना नहीं"   to "kuch suna nahi",
            "कुछ और चाहिए"    to "kuch aur chahiye",
            "और कुछ चाहिए"    to "aur kuch chahiye",
            "और कुछ"          to "aur kuch",
            "कुछ और"          to "kuch aur",
            "ले लिया"         to "le liya",
            "ले लो"           to "le lo",
            "ले लें"          to "le lein",
            "ले लून"          to "le loon",
            "ले लूं"          to "le loon",
            "कर दो"           to "kar do",
            "बता दो"          to "bata do",
            "बता दीजिए"       to "bata dijiye",
            "हो गया है"       to "ho gaya hai",
            "हो गई है"        to "ho gayi hai",
            "हो गया"          to "ho gaya",
            "हो गई"           to "ho gayi",
            "आ गया"           to "aa gaya",
            "आ गई"            to "aa gayi",
            "कर दिया"         to "kar diya",
            "कर दी"           to "kar di",
            "नहीं है"         to "nahi hai",
            "नहीं मिला"       to "nahi mila",
            "नहीं मिली"       to "nahi mili",
            "मत माँगा"        to "mat manga",
            "minute में"      to "minute mein",
            "मिनट में"        to "minute mein",
            "Ho गया"          to "ho gaya",
            "ho गया"          to "ho gaya",
            "add हो गया"      to "add ho gaya",
            "जी हाँ"          to "ji haan",
            "माफ करना"        to "maaf karna",
            "थोड़ी देर"        to "thodi der",
            "थोड़ा जोर"        to "thoda zor",
            "पहुंच जाएगा"     to "pahunch jaayega",
            "पहुँच जाएगा"     to "pahunch jaayega",
            "पहुंच जायेगा"    to "pahunch jaayega",
            "बताइए"           to "bataiye",
            "बोलिए"           to "boliye",
            "बोलें"           to "bolein",
            "मंगवाएं"         to "mangwaayein",
            "मंगवाइए"         to "mangwaiye",
            "मंगवाना"         to "mangwaana",
            "चाहिए"           to "chahiye",
            "लेना"            to "lena",
            "देना"            to "dena",
            "दीजिए"           to "dijiye",
            "करना"            to "karna",
            "करें"            to "karein",
            "करिए"            to "kariye",
            "पहुंचेगा"        to "pahunchega",
            "पहुँचेगा"        to "pahunchega",
            "पहुंचेगी"        to "pahunchegi",
            "पहुँचेगी"        to "pahunchegi",
            "पहुंचाएगा"       to "pahunchaayega",
            "आएगा"            to "aayega",
            "आएगी"            to "aayegi",
            "भेज दो"          to "bhej do",
            "भेज दीजिए"       to "bhej dijiye",
            "मिलेगा"          to "milega",
            "मिलेगी"          to "milegi",
            "मिला"            to "mila",
            "मिली"            to "mili",
            "होगा"            to "hoga",
            "होगी"            to "hogi",
            "हुआ"             to "hua",
            "हुई"             to "hui",
            "सुना"            to "suna",
            "सुनी"            to "suni",
            "सुनिए"           to "suniye",
            "सुनो"            to "suno",
            "चलिए"            to "chaliye",
            "चलो"             to "chalo",
            "जाओ"             to "jao",
            "रुको"            to "ruko",
            "देखो"            to "dekho",
            "बोलो"            to "bolo",
            "बताओ"            to "batao",
            "लाओ"             to "lao",
            "भेजिए"           to "bhejiye",
            "रखिए"            to "rakhiye",
            "दिखाइए"          to "dikhaiye",
            "ठीक"             to "theek",
            "अच्छा"           to "achha",
            "अच्छी"           to "achhi",
            "अच्छे"           to "achhe",
            "बढ़िया"          to "badhiya",
            "सही"             to "sahi",
            "पक्का"           to "pakka",
            "बिल्कुल"         to "bilkul",
            "जरूर"            to "zaroor",
            "ज़रूर"            to "zaroor",
            "जल्दी"           to "jaldi",
            "थोड़ा"            to "thoda",
            "थोड़ी"            to "thodi",
            "ज्यादा"          to "zyada",
            "खाली"            to "khaali",
            "तैयार"           to "taiyaar",
            "शानदार"          to "shaandaar",
            "खत्म"            to "khatam",
            "सबसे"            to "sabse",
            "अभी"             to "abhi",
            "जरा"             to "zara",
            "सस्ता"           to "sasta",
            "महंगा"           to "mahanga",
            "ताजा"            to "taaza",
            "बहुत"            to "bahut",
            "बहुत अच्छा"      to "bahut achha",
            "बहुत बढ़िया"     to "bahut badhiya",
            "शुक्रिया"        to "shukriya",
            "पैसे"            to "paise",
            "मिनट"            to "minute",
            "आवाज़"            to "awaaz",
            "आवाज"            to "awaaz",
            "नाम"             to "naam",
            "सामान"           to "samaan",
            "बात"             to "baat",
            "काम"             to "kaam",
            "घर"              to "ghar",
            "आज"              to "aaj",
            "कल"              to "kal",
            "बाद"             to "baad",
            "पहले"            to "pehle",
            "फिर"             to "phir",
            "अब"              to "ab",
            "पास"             to "paas",
            "समझ"             to "samajh",
            "दुकान"           to "dukaan",
            "कीमत"            to "keemat",
            "दाम"             to "daam",
            "डिलीवरी"         to "delivery",
            "चावल"            to "chawal",
            "दाल"             to "daal",
            "तेल"             to "tel",
            "आटा"             to "atta",
            "दूध"             to "doodh",
            "चाय"             to "chai",
            "घी"              to "ghee",
            "नमक"             to "namak",
            "चीनी"            to "cheeni",
            "मसाला"           to "masala",
            "सब्जी"           to "sabzi",
            "दही"             to "dahi",
            "मक्खन"           to "makhan",
            "अंडा"            to "anda",
            "अंडे"            to "ande",
            "किलो"            to "kilo",
            "ग्राम"           to "gram",
            "पैकेट"           to "packet",
            "बोतल"            to "bottle",
            "ब्रांड"           to "brand",
            "बिस्कुट"         to "biscuit",
            "साबुन"           to "sabun",
            "कॉफी"            to "coffee",
            "क्या"            to "kya",
            "कौनसा"           to "kaunsa",
            "कौन सा"          to "kaun sa",
            "कौन सी"          to "kaun si",
            "कितना"           to "kitna",
            "कितनी"           to "kitni",
            "कितने"           to "kitne",
            "कहाँ"            to "kahaan",
            "कहां"            to "kahaan",
            "कैसे"            to "kaise",
            "कोई"             to "koi",
            "कुछ"             to "kuch",
            "मुझे"            to "mujhe",
            "आपको"            to "aapko",
            "आपका"            to "aapka",
            "आपकी"            to "aapki",
            "आपके"            to "aapke",
            "हमारा"           to "hamara",
            "लेकिन"           to "lekin",
            "साथ"             to "saath",
            "में"             to "mein",
            "और"              to "aur",
            "या"              to "ya",
            "भी"              to "bhi",
            "पर"              to "par",
            "से"              to "se",
            "के"              to "ke",
            "का"              to "ka",
            "की"              to "ki",
            "हाँ"             to "haan",
            "हां"             to "haan",
            "नहीं"            to "nahi",
            "नही"             to "nahi",
            "बस"              to "bas",
            "धन्यवाद"         to "dhanyavaad",
            "घबराइए"          to "ghabraiye",
            "एक"              to "ek",
            "दो"              to "do",
            "तीन"             to "teen",
            "चार"             to "chaar",
            "पाँच"            to "paanch",
            "पांच"            to "paanch",
            "छह"              to "chhe",
            "सात"             to "saat",
            "आठ"              to "aath",
            "नौ"              to "nau",
            "दस"              to "das",
            "बीस"             to "bees",
            "तीस"             to "tees",
            "चालीस"           to "chaalis",
            "पचास"            to "pachaas",
            "हैं"             to "hain",
            "है"              to "hai",
            "तो"              to "to",
            "ही"              to "hi"
        )

        private val TECH_WORD_MAP = linkedMapOf(
            "ऑर्डर्स"         to "orders",
            "ऑर्डर"           to "order",
            "कार्ट"            to "cart",
            "पेमेंट"           to "payment",
            "कैंसिल"           to "cancel",
            "कन्फर्म"          to "confirm",
            "डिलीवरी"          to "delivery",
            "बुकिंग"           to "booking",
            "ऑप्शन"            to "option",
            "चेकआउट"          to "checkout",
            "यूपीआई"           to "यू पी आई",
            "क्यूआर"           to "QR",
            "डेबिट"            to "debit",
            "क्रेडिट"          to "credit",
            "कार्ड"            to "card",
            "बटलर"             to "Butler",
            "अकाउंट"           to "account",
            "मोबाइल"           to "mobile",
            "नंबर"             to "number",
            "आईडी"             to "ID",
            "स्क्रीन"          to "screen",
            "अम्बुलेंस"        to "ambulance",
            "एम्बुलेंस"        to "ambulance",
            "डॉक्टर"           to "doctor",
            "एमरजेंसी"         to "emergency",
            "फार्मेसी"         to "pharmacy",
            "परफेक्ट"          to "perfect",
            "ओके"              to "okay",
            "थैंक्यू"          to "thank you",
            "सॉरी"             to "sorry"
        )

        private data class VoiceSettings(
            val stability: Double,
            val similarityBoost: Double,
            val style: Double,
            val useSpeakerBoost: Boolean = true,
            val speed: Double = 0.85
        )

        private val TONE_SETTINGS = mapOf(
            EmotionTone.EMERGENCY  to VoiceSettings(0.90, 0.82, 0.00, speed = 1.00),
            EmotionTone.EMPATHETIC to VoiceSettings(0.75, 0.82, 0.18, speed = 0.76),
            EmotionTone.NORMAL     to VoiceSettings(0.82, 0.80, 0.05, speed = 0.87),
            EmotionTone.WARM       to VoiceSettings(0.78, 0.82, 0.12, speed = 0.80),
            EmotionTone.EXCITED    to VoiceSettings(0.72, 0.80, 0.22, speed = 0.93)
        )
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false

    fun init(onReady: () -> Unit) {
        androidTts = TextToSpeech(context) { status ->
            androidTtsReady = (status == TextToSpeech.SUCCESS)
            if (androidTtsReady) androidTts?.language = Locale("hi", "IN")
            onReady()
        }
    }

    fun speak(
        text: String,
        language: String,
        tone: EmotionTone = EmotionTone.NORMAL,
        onDone: (() -> Unit)? = null
    ) {
        if (text.isBlank()) { Log.w(TAG, "speak() blank — skip"); onDone?.invoke(); return }

        val normalizedText = normalizeForTTS(text, language)
        val resolvedVoice  = resolveVoice(normalizedText, language)
        val settings       = TONE_SETTINGS[tone] ?: TONE_SETTINGS[EmotionTone.NORMAL]!!

        Log.d(TAG, "ElevenLabs [$language] tone=$tone → \"${normalizedText.take(100)}\"")
        Log.d(TAG, "  stability=${settings.stability} style=${settings.style} speed=${settings.speed}")

        // ── Devanagari passthrough check ──────────────────────────────────────
        // "रुपये", "रुपया", "यू पी आई" intentionally allowed through.
        val unmappedCheck = normalizedText
            .replace("रुपये", "").replace("रुपया", "")
            .replace("यू पी आई", "")
        if (DEVANAGARI.containsMatchIn(unmappedCheck)) {
            Log.w(TAG, "⚠️ Unmapped Devanagari in: $normalizedText")
        }

        val appContext = context.applicationContext
        Thread {
            try {
                val audioBytes = fetchElevenLabsAudio(normalizedText, resolvedVoice, settings)
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    playAudioBytes(appContext, audioBytes, onDone)
                } else {
                    Log.w(TAG, "ElevenLabs empty — fallback to Android TTS")
                    speakWithAndroidTts(normalizedText, language, onDone)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ElevenLabs error: ${e.message} — fallback")
                speakWithAndroidTts(normalizedText, language, onDone)
            }
        }.start()
    }

    // ── normalizeForTTS ───────────────────────────────────────────────────────
    //
    // Step 0: NFC              — canonicalize Devanagari combining chars
    // Step 1: Order IDs        — "BUT-000145" → "order number ek chaar paanch"
    // Step 2: Currency symbol  — "₹54" → "chauvan रुपये"
    // Step 2.5: Roman rupaye   — "paintalees rupaye" → "paintalees रुपये"
    // Step 2.7: UPI            — "UPI" → "यू पी आई" (letter-by-letter Hindi pronunciation)
    // Step 3: Tech/UI terms    — "ऑर्डर" → "order"
    // Step 4: Devanagari→Roman — "ठीक है" → "theek hai"
    // Step 5: Cleanup          — danda, em-dash, spacing, trim
    // ─────────────────────────────────────────────────────────────────────────
    fun normalizeForTTS(text: String, language: String): String {
        var result = text

        // Step 0 — Unicode NFC
        result = java.text.Normalizer.normalize(result, java.text.Normalizer.Form.NFC)

        // Step 1 — Order IDs
        result = ORDER_ID_REGEX.replace(result) { match ->
            val digits = match.groupValues[1]
            val spoken = digits.map { DIGIT_WORDS[it] ?: it.toString() }.joinToString(" ")
            "order number $spoken"
        }

        // Step 2 — Currency symbol  ₹ → spoken Hindi amount
        result = RUPEE_REGEX.replace(result) { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            rupeeToSpoken(amount, language)
        }
        result = result.replace(Regex("Rs\\.?\\s*(\\d+)")) { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            rupeeToSpoken(amount, language)
        }

        // Step 2.5 — Roman "rupaye" / "rupaya" → Devanagari रुपये
        if (language.startsWith("hi") || language.startsWith("mr")) {
            result = result.replace(Regex("\\brupaye\\b", RegexOption.IGNORE_CASE), "रुपये")
            result = result.replace(Regex("\\brupaya\\b", RegexOption.IGNORE_CASE), "रुपये")
            result = result.replace(Regex("\\brupee\\b",  RegexOption.IGNORE_CASE), "रुपये")
            result = result.replace(Regex("\\brupees\\b", RegexOption.IGNORE_CASE), "रुपये")
        }

        // Step 2.7 — UPI → Devanagari letters for natural Hindi pronunciation
        // ElevenLabs reads Roman "UPI" as a word ("yoo-pee"); "यू पी आई" gives
        // the correct letter-by-letter U-P-I cadence in Hindi voice.
        if (language.startsWith("hi") || language.startsWith("mr")) {
            result = result.replace(Regex("\\bUPI\\b"), "यू पी आई")
        }

        // Step 3 — Tech/UI terms (Devanagari branded words → Roman)
        TECH_WORD_MAP.forEach { (source, target) ->
            result = result.replace(source, target, ignoreCase = false)
        }

        // Step 4 — Devanagari → Roman
        // "रुपये" / "रुपया" / "यू पी आई" are not in the map — they pass through
        DEVANAGARI_TO_ROMAN.forEach { (devanagari, roman) ->
            result = result.replace(devanagari, roman, ignoreCase = false)
        }

        // Step 5 — Cleanup
        result = result
            .replace("।", ".")
            .replace("॥", ".")
            .replace("—", ", ")
            .replace("–", ", ")
            .replace(Regex("  +"), " ")
            .replace(Regex("\\.{4,}"), "...")
            .replace(Regex("\\s+\\."), ".")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("!{2,}"), "!")
            .replace(Regex("\\?{2,}"), "?")
            .trim()

        return result
    }

    private fun resolveVoice(text: String, language: String): String {
        return when {
            language.startsWith("hi") || language.startsWith("mr") -> VOICE_HI
            else -> VOICE_EN
        }
    }

    private fun fetchElevenLabsAudio(text: String, voice: String, settings: VoiceSettings): ByteArray? {
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", ELEVEN_MODEL)
            put("voice_settings", JSONObject().apply {
                put("stability",         settings.stability)
                put("similarity_boost",  settings.similarityBoost)
                put("style",             settings.style)
                put("use_speaker_boost", settings.useSpeakerBoost)
                put("speed",             settings.speed)
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voice")
            .addHeader("xi-api-key",   elevenLabsApiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept",       "audio/mpeg")
            .post(body)
            .build()

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "ElevenLabs HTTP ${response.code}: ${response.body?.string()?.take(200)}")
            return null
        }
        return response.body?.bytes()
    }

    private fun playAudioBytes(context: Context, audioBytes: ByteArray, onDone: (() -> Unit)?) {
        val tmp = File(context.cacheDir, "butler_tts_${System.currentTimeMillis()}.mp3")
        try { tmp.writeBytes(audioBytes) }
        catch (e: Exception) { Log.e(TAG, "Temp file write: ${e.message}"); onDone?.invoke(); return }

        releasePlayer()
        player = MediaPlayer().apply {
            try {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                setDataSource(tmp.absolutePath)
                prepare()
                setOnCompletionListener { tmp.delete(); releasePlayer(); onDone?.invoke() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    tmp.delete(); releasePlayer(); onDone?.invoke(); true
                }
                start()
                Log.d(TAG, "Playback started (${audioBytes.size} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer setup: ${e.message}")
                tmp.delete(); releasePlayer(); onDone?.invoke()
            }
        }
    }

    private fun releasePlayer() {
        try { player?.apply { if (isPlaying) stop(); reset(); release() } }
        catch (e: Exception) { Log.e(TAG, "releasePlayer: ${e.message}") }
        finally { player = null }
    }

    private fun speakWithAndroidTts(text: String, language: String, onDone: (() -> Unit)?) {
        val tts = androidTts
        if (tts == null || !androidTtsReady) { onDone?.invoke(); return }
        try {
            tts.language = when {
                language.startsWith("hi") -> Locale("hi", "IN")
                language.startsWith("te") -> Locale("te", "IN")
                language.startsWith("mr") -> Locale("mr", "IN")
                else -> Locale.ENGLISH
            }
            val id = "butler_${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?)  { onDone?.invoke() }
                override fun onError(id: String?) { onDone?.invoke() }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        } catch (e: Exception) {
            Log.e(TAG, "Android TTS: ${e.message}"); onDone?.invoke()
        }
    }

    fun stop()     { releasePlayer(); androidTts?.stop() }
    fun shutdown() { releasePlayer(); androidTts?.stop(); androidTts?.shutdown(); androidTts = null }
}