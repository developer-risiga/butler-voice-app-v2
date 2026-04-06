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

// ── Emotion tone → ElevenLabs voice_settings ─────────────────────────────────
//
// TONE DESIGN — tuned for warmth and professionalism:
//
//   PREVIOUS: all tones had stability=0.85, style=0.0 → identical flat delivery
//   NOW: differentiated stability + style for genuine emotional color
//
//   ElevenLabs stability:
//     0.90+ = controlled, robotic (use only for emergencies)
//     0.78–0.82 = natural human variation (professional + warm)
//     0.72–0.76 = expressive, emotional (empathy, excitement)
//
//   ElevenLabs style:
//     0.0 = flat, no personality
//     0.05–0.12 = subtle warmth (safe, no random outbursts)
//     0.15–0.22 = audible emotion (empathy, celebration)
//
// ─────────────────────────────────────────────────────────────────────────────
enum class EmotionTone {
    EMERGENCY,    // Medical crisis — controlled, commanding
    EMPATHETIC,   // Retry / error / user frustrated — gentle, patient
    NORMAL,       // Product info, prices — clear, professional
    WARM,         // Greeting, item added — friendly neighborhood assistant
    EXCITED       // Payment done, order confirmed — celebratory
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

        // ── Order ID normalization ────────────────────────────────────────────
        private val ORDER_ID_REGEX = Regex("""\b[A-Z]{2,5}-0*(\d{1,6})\b""")
        private val DIGIT_WORDS = mapOf(
            '0' to "zero", '1' to "ek",    '2' to "do",
            '3' to "teen", '4' to "chaar", '5' to "paanch",
            '6' to "chhe", '7' to "saat",  '8' to "aath", '9' to "nau"
        )

        // ── Devanagari → Roman: THE core pronunciation fix ───────────────────
        //
        // WHY THIS MATTERS:
        //   ElevenLabs is trained on Roman-script text. When it receives Devanagari
        //   ("ठीक है Roy"), it mispronounces or skips those characters, making Butler
        //   sound robotic and broken.
        //
        //   Butler speaks Hinglish — Hindi meaning, Roman pronunciation.
        //   This map converts every common Devanagari word to its Roman phonetic
        //   equivalent BEFORE sending to ElevenLabs.
        //   "ठीक है Roy... aur kuch chahiye?" → "theek hai Roy... aur kuch chahiye?"
        //   ElevenLabs reads the result perfectly in a natural Indian accent.
        //
        // ORDER: Multi-word phrases MUST appear before single words to prevent
        //   partial replacements. "हो गया" → "ho gaya" before "है" → "hai".
        // ─────────────────────────────────────────────────────────────────────
        private val DEVANAGARI_TO_ROMAN = linkedMapOf(

            // ── Multi-word phrases first ──────────────────────────────────────
            "घबराइए मत"       to "ghabraiye mat",
            "समझ नहीं आया"    to "samajh nahi aaya",
            "फिर से बोलिए"    to "phir se boliye",
            "फिर से बोलें"    to "phir se bolein",
            "कुछ और चाहिए"    to "kuch aur chahiye",
            "और कुछ चाहिए"    to "aur kuch chahiye",
            "और कुछ"          to "aur kuch",
            "कुछ और"          to "kuch aur",
            "ले लिया"         to "le liya",
            "ले लो"           to "le lo",
            "ले लें"          to "le lein",
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
            "add हो गया"      to "add ho gaya",
            "place हो गया"    to "place ho gaya",
            "minute में"      to "minute mein",
            "मिनट में"        to "minute mein",
            "Ho गया"          to "ho gaya",
            "ho गया"          to "ho gaya",

            // ── Common verbs ──────────────────────────────────────────────────
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
            "पहुंचेगी"        to "pahunchegi",
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

            // ── Adjectives & adverbs ──────────────────────────────────────────
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
            "थोड़ी देर"        to "thodi der",

            // ── Common nouns ──────────────────────────────────────────────────
            "रुपये"           to "rupaye",
            "रुपया"           to "rupaya",
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

            // ── Grocery words ─────────────────────────────────────────────────
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

            // ── Question words ────────────────────────────────────────────────
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

            // ── Pronouns & connectors ─────────────────────────────────────────
            "मुझे"            to "mujhe",
            "आपको"            to "aapko",
            "आपका"            to "aapka",
            "आपकी"            to "aapki",
            "आपके"            to "aapke",
            "हमारा"           to "hamara",
            "लेकिन"           to "lekin",
            "साथ"             to "saath",
            "में"             to "mein",

            // ── Affirmations & social words ───────────────────────────────────
            "हाँ"             to "haan",
            "हां"             to "haan",
            "जी हाँ"          to "ji haan",
            "नहीं"            to "nahi",
            "नही"             to "nahi",
            "बस"              to "bas",
            "शुक्रिया"        to "shukriya",
            "धन्यवाद"         to "dhanyavaad",
            "माफ करना"        to "maaf karna",
            "घबराइए"          to "ghabraiye",
            "चलिए"            to "chaliye",
            "चलो"             to "chalo",
            "जरा"             to "zara",

            // ── Numbers (when spoken in Devanagari) ───────────────────────────
            "एक"              to "ek",
            "दो"              to "do",
            "तीन"             to "teen",
            "चार"             to "chaar",
            "पाँच"            to "paanch",
            "पांच"            to "paanch",

            // ── Common verb "hai" last — shortest, most likely to false-match ─
            "हैं"             to "hain",
            "है"              to "hai"
        )

        // ── Tech / UI terms (Devanagari → Roman) ─────────────────────────────
        private val TECH_WORD_MAP = linkedMapOf(
            "ऑर्डर्स"         to "orders",
            "ऑर्डर"           to "order",
            "कार्ट"            to "cart",
            "पेमेंट"           to "payment",
            "कैंसिल"           to "cancel",
            "कन्फर्म"          to "confirm",
            "डिलीवरी"          to "delivery",
            "बुकिंग"           to "booking",
            "ऑप्शन्स"          to "options",
            "ऑप्शन"            to "option",
            "चेकआउट"          to "checkout",
            "यूपीआई"           to "UPI",
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
            EmotionTone.EMERGENCY  to VoiceSettings(stability=0.90, similarityBoost=0.82, style=0.0,  speed=1.0),
            EmotionTone.EMPATHETIC to VoiceSettings(stability=0.75, similarityBoost=0.82, style=0.18, speed=0.76),
            EmotionTone.NORMAL     to VoiceSettings(stability=0.82, similarityBoost=0.80, style=0.05, speed=0.87),
            EmotionTone.WARM       to VoiceSettings(stability=0.78, similarityBoost=0.82, style=0.12, speed=0.80),
            EmotionTone.EXCITED    to VoiceSettings(stability=0.72, similarityBoost=0.80, style=0.22, speed=0.93)
        )
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30,  TimeUnit.SECONDS)
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

        Log.d(TAG, "ElevenLabs [$language] tone=$tone voice=$resolvedVoice → \"${normalizedText.take(80)}\"")
        Log.d(TAG, "  stability=${settings.stability} style=${settings.style} speed=${settings.speed}")

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
    // Pipeline (order is critical):
    //   1. Order IDs (BUT-000145 → "order number ek chaar paanch")
    //   2. Tech/UI terms ("ऑर्डर" → "order")
    //   3. Common Devanagari → Roman ("ठीक है" → "theek hai")  ← biggest fix
    //   4. Cleanup (spaces, dots)
    // ─────────────────────────────────────────────────────────────────────────
    private fun normalizeForTTS(text: String, language: String): String {
        var result = text

        // Step 1 — Order IDs
        result = ORDER_ID_REGEX.replace(result) { match ->
            val digits = match.groupValues[1]
            val spoken = digits.map { DIGIT_WORDS[it] ?: it.toString() }.joinToString(" ")
            "order number $spoken"
        }

        // Step 2 — Tech/UI terms
        TECH_WORD_MAP.forEach { (source, target) ->
            result = result.replace(source, target, ignoreCase = false)
        }

        // Step 3 — Comprehensive Devanagari → Roman
        DEVANAGARI_TO_ROMAN.forEach { (devanagari, roman) ->
            result = result.replace(devanagari, roman, ignoreCase = false)
        }

        // Step 4 — Cleanup
        result = result
            .replace(Regex("  +"), " ")
            .replace(Regex("\\.{4,}"), "...")
            .replace(Regex("\\s+\\."), ".")
            .trim()

        return result
    }

    private fun resolveVoice(text: String, language: String): String {
        if (DEVANAGARI.containsMatchIn(text)) return VOICE_HI
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
        catch (e: Exception) { Log.e(TAG, "Temp file write failed: ${e.message}"); onDone?.invoke(); return }

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
                Log.e(TAG, "MediaPlayer setup failed: ${e.message}")
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