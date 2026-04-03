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

// ── Emotion tone controls ElevenLabs voice_settings ──────────────────────────
//
// WHY IT SOUNDED THE SAME EVERY TIME:
//   Old TONE_SETTINGS had ALL tones clustered at HIGH stability (0.68–0.90).
//   ElevenLabs: HIGH stability = monotone/robotic. That's why every tone was flat.
//
//   New values span the full audible range (0.20–0.58):
//   EMERGENCY  stability=0.20, style=0.70 → urgent, intense, commanding
//   EMPATHETIC stability=0.32, style=0.48 → gentle, soft, patient
//   NORMAL     stability=0.55, style=0.10 → clear, neutral info delivery
//   WARM       stability=0.42, style=0.40 → friendly, natural, unhurried
//   EXCITED    stability=0.28, style=0.55 → upbeat, energetic, cheerful
//
// TUNING GUIDE:
//   Want warmer?      → lower stability (0.35–0.45), raise style (0.3–0.4)
//   Want more calm?   → raise stability (0.6+), lower style (0.05)
//   Want urgency?     → raise speed (1.0+), lower stability (0.2), raise style
//   Want sympathy?    → lower stability (0.3–0.35), raise style (0.4), slow speed
// ─────────────────────────────────────────────────────────────────────────────
enum class EmotionTone {
    EMERGENCY,    // Ambulance/medical — urgent, commanding, fast
    EMPATHETIC,   // Retry/error/frustration — gentle, slow, patient
    NORMAL,       // Product info, prices — clear, neutral
    WARM,         // Greeting, order placed, item added — friendly, unhurried
    EXCITED       // Payment done, confirmations — upbeat, energetic (NEW)
}

class TTSManager(
    private val context: Context,
    private val elevenLabsApiKey: String,
    private val voiceId: String = "RwXLkVKnRloV1UPh3Ccx"
) {

    companion object {
        private const val TAG          = "TTS"
        private const val VOICE_EN     = "RwXLkVKnRloV1UPh3Ccx"
        private const val VOICE_HI     = "RwXLkVKnRloV1UPh3Ccx"
        private const val ELEVEN_MODEL = "eleven_multilingual_v2"
        private val DEVANAGARI         = Regex("[\\u0900-\\u097F]")

        private val TTS_WORD_MAP = linkedMapOf(
            "Ho गया"           to "Ho gaya",
            "ho गया"           to "ho gaya",
            "Ho Gaya"          to "ho gaya",
            "Haan! "           to "haan! ",
            "Aur "             to "aur ",
            "ऑर्डर"           to "order",
            "ऑर्डर्स"         to "orders",
            "कार्ट"            to "cart",
            "पेमेंट"           to "payment",
            "कैंसिल"           to "cancel",
            "कन्फर्म"          to "confirm",
            "डिलीवरी"          to "delivery",
            "बुकिंग"           to "booking",
            "ऑप्शन"            to "option",
            "ऑप्शन्स"          to "options",
            "चेकआउट"          to "checkout",
            "लोड"              to "load",
            "यूपीआई"           to "UPI",
            "क्यूआर"           to "QR",
            "कार्ड"            to "card",
            "डेबिट"            to "debit",
            "क्रेडिट"          to "credit",
            "बटलर"             to "Butler",
            "अकाउंट"           to "account",
            "पासवर्ड"          to "password",
            "ईमेल"             to "email",
            "मोबाइल"           to "mobile",
            "नंबर"             to "number",
            "आईडी"             to "ID",
            "किलो"             to "kilo",
            "लीटर"             to "litre",
            "पैकेट"            to "packet",
            "अम्बुलेंस"        to "ambulance",
            "एम्बुलेंस"        to "ambulance",
            "डॉक्टर"           to "doctor",
            "एमरजेंसी"         to "emergency",
            "फार्मेसी"         to "pharmacy",
            "स्क्रीन"          to "screen",
            "ब्रांड"            to "brand",
            "परफेक्ट"          to "perfect",
            "ओके"              to "okay",
            "प्लीज़"            to "please",
            "थैंक्यू"          to "thank you",
            "सॉरी"             to "sorry",
            "हेलो"             to "hello",
            "बाय"              to "bye"
        )

        private data class VoiceSettings(
            val stability: Double,
            val similarityBoost: Double,
            val style: Double,
            val useSpeakerBoost: Boolean = true,
            val speed: Double = 0.85
        )

        // ══════════════════════════════════════════════════════════════════
        // TONE SETTINGS — THIS IS THE FIX
        //
        // OLD: All tones had HIGH stability (0.68–0.90) and LOW style (0.00–0.12)
        //      → All sounded identical and flat.
        //
        // NEW: Tones span the full range. The difference between EMERGENCY (0.20)
        //      and NORMAL (0.55) is immediately audible. WARM (style=0.40) vs
        //      NORMAL (style=0.10) sounds like two different personalities.
        // ══════════════════════════════════════════════════════════════════
        private val TONE_SETTINGS = mapOf(

            // EMERGENCY — ambulance, medical, danger
            // stability=0.20: maximum expression, pitch variation, urgency
            // style=0.70: intense and commanding (was 0.00 = completely flat/robotic)
            // speed=1.08: slightly faster = urgency
            // OLD: stability=0.90, style=0.00 → sounded flat and robotic for emergencies
            EmotionTone.EMERGENCY to VoiceSettings(
                stability = 0.20,        // LOW = maximum expression
                similarityBoost = 0.88,
                style = 0.70,            // HIGH = urgent, serious, commanding
                speed = 1.08             // faster = urgency
            ),

            // EMPATHETIC — silence retries, errors, user frustrated
            // stability=0.32: emotional, gentle variation
            // style=0.48: soft sympathy clearly audible (was 0.03 = nearly no character)
            // speed=0.75: slower = patience, giving user time (was 0.82 = too fast)
            // OLD: stability=0.75, style=0.03 → nearly identical to NORMAL
            EmotionTone.EMPATHETIC to VoiceSettings(
                stability = 0.32,        // LOW-MEDIUM = emotional, gentle
                similarityBoost = 0.82,
                style = 0.48,            // MEDIUM-HIGH = soft sympathy audible
                speed = 0.75             // SLOW = patient, not rushing user
            ),

            // NORMAL — prices, product lists, factual info
            // Stable and clear. Not robotic, not performative.
            EmotionTone.NORMAL to VoiceSettings(
                stability = 0.55,        // MEDIUM = stable, consistent
                similarityBoost = 0.72,
                style = 0.10,            // LOW = neutral info delivery
                speed = 0.88
            ),

            // WARM — greetings, item added, order placed
            // stability=0.42: natural friendly variation (was 0.68 = too flat)
            // style=0.40: warmth clearly audible (was 0.12 = barely any personality)
            // speed=0.82: slightly slow = unhurried, comfortable
            // OLD: stability=0.68, style=0.12 → nearly same as NORMAL, no warmth
            EmotionTone.WARM to VoiceSettings(
                stability = 0.42,        // LOW-MEDIUM = expressive natural range
                similarityBoost = 0.78,
                style = 0.40,            // MEDIUM = warmth and personality audible
                speed = 0.82             // slightly slow = unhurried, friendly
            ),

            // EXCITED — payment done, confirmations, good news (NEW tone)
            // stability=0.28: dynamic, lively
            // style=0.55: clear upbeat energy
            // speed=0.95: slightly faster = cheerful
            EmotionTone.EXCITED to VoiceSettings(
                stability = 0.28,        // LOW = dynamic, energetic
                similarityBoost = 0.76,
                style = 0.55,            // HIGH = clear upbeat energy
                speed = 0.95             // slightly faster = cheerful pace
            )
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

        val normalizedText = normalizeForTTS(text)
        val resolvedVoice  = resolveVoice(normalizedText, language)
        val settings       = TONE_SETTINGS[tone] ?: TONE_SETTINGS[EmotionTone.NORMAL]!!
        Log.d(TAG, "ElevenLabs [$language] tone=$tone voice=$resolvedVoice → \"${normalizedText.take(60)}\"")
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

    private fun normalizeForTTS(text: String): String {
        var result = text
        TTS_WORD_MAP.forEach { (source, target) ->
            result = result.replace(source, target, ignoreCase = false)
        }
        return result
    }

    private fun resolveVoice(text: String, language: String): String {
        if (DEVANAGARI.containsMatchIn(text)) return VOICE_HI
        return when {
            language.startsWith("hi") || language.startsWith("mr") -> VOICE_HI
            else -> VOICE_EN
        }
    }

    private fun fetchElevenLabsAudio(
        text: String,
        voice: String,
        settings: VoiceSettings
    ): ByteArray? {
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
        try {
            tmp.writeBytes(audioBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Temp file write failed: ${e.message}")
            onDone?.invoke()
            return
        }

        releasePlayer()
        player = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
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
            Log.e(TAG, "Android TTS: ${e.message}")
            onDone?.invoke()
        }
    }

    fun stop()     { releasePlayer(); androidTts?.stop() }
    fun shutdown() { releasePlayer(); androidTts?.stop(); androidTts?.shutdown(); androidTts = null }
}