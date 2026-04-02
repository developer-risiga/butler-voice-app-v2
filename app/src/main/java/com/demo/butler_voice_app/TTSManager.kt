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
// Never use a cheerful/expressive tone for emergencies or distress.
//
// EMERGENCY  → high stability, zero style  → serious, controlled, urgent
// EMPATHETIC → medium stability, low style → gentle, calm, caring
// NORMAL     → default (ordering, greetings, questions)
// WARM       → used for order confirmed, booking done
// ─────────────────────────────────────────────────────────────────────────────
enum class EmotionTone {
    EMERGENCY,    // "Ambulance bula raha hoon" — serious, no cheerfulness
    EMPATHETIC,   // "Samajh gaye, tension mat lo" — gentle
    NORMAL,       // Default for ordering/questions
    WARM          // Order placed, booking confirmed — positive but not giddy
}

class TTSManager(
    private val context: Context,
    private val elevenLabsApiKey: String,
    private val voiceId: String = "RwXLkVKnRloV1UPh3Ccx"
) {
    // ── TTS TEXT NORMALIZER ───────────────────────────────────────────────
    // ElevenLabs mispronounces English loanwords written in Devanagari script.
    // Example: "ऑर्डर" → sounds like "On Order" instead of "Order"
    // Fix: replace Devanagari-script English words with Latin equivalents
    // BEFORE sending to ElevenLabs. The model pronounces Latin correctly.
    // Native Hindi words (हाँ, क्या, etc.) are left untouched.
    private fun normalizeForTTS(text: String): String {
        var result = text
        TTS_WORD_MAP.forEach { (hindi, latin) ->
            result = result.replace(hindi, latin, ignoreCase = false)
        }
        return result
    }

    companion object {
        // ── Devanagari loanword → Latin replacement map ───────────────────
        // Add more entries as you discover mispronunciations in production.
        private val TTS_WORD_MAP = linkedMapOf(
            // Orders & Commerce
            "ऑर्डर"     to "order",
            "ऑर्डर्स"   to "orders",
            "कार्ट"      to "cart",
            "पेमेंट"     to "payment",
            "कैंसिल"     to "cancel",
            "कन्फर्म"    to "confirm",
            "डिलीवरी"    to "delivery",
            "बुकिंग"     to "booking",
            "ऑप्शन"      to "option",
            "ऑप्शन्स"    to "options",
            // Payment
            "यूपीआई"     to "UPI",
            "क्यूआर"     to "QR",
            "कार्ड"      to "card",
            "डेबिट"      to "debit",
            "क्रेडिट"    to "credit",
            // App
            "बटलर"       to "Butler",
            "अकाउंट"     to "account",
            "पासवर्ड"    to "password",
            "ईमेल"       to "email",
            "मोबाइल"     to "mobile",
            "नंबर"       to "number",
            "आईडी"       to "ID",
            // Units & quantities
            "किलो"       to "kilo",
            "लीटर"       to "litre",
            "पैकेट"      to "packet",
            // Services
            "अम्बुलेंस"  to "ambulance",
            "डॉक्टर"     to "doctor",
            "एमरजेंसी"   to "emergency",
            "फार्मेसी"   to "pharmacy",
            // Common Hinglish
            "परफेक्ट"    to "perfect",
            "ओके"        to "okay",
            "प्लीज़"      to "please",
            "थैंक्यू"    to "thank you",
            "सॉरी"       to "sorry",
            "हेलो"       to "hello",
            "बाय"        to "bye"
        )

        private const val TAG = "TTS"
        private const val VOICE_EN = "RwXLkVKnRloV1UPh3Ccx"
        private const val VOICE_HI = "RwXLkVKnRloV1UPh3Ccx"
        private const val ELEVEN_MODEL = "eleven_multilingual_v2"
        private val DEVANAGARI = Regex("[\\u0900-\\u097F]")

        private data class VoiceSettings(
            val stability: Double,
            val similarityBoost: Double,
            val style: Double,
            val useSpeakerBoost: Boolean = true,
            val speed: Double = 0.85   // <1.0 = slower speech — housewife/elderly default
        )

        private val TONE_SETTINGS = mapOf(
            // stability: higher = more consistent pacing, less "running away"
            // style: lower = cleaner pronunciation, easier to understand
            // speed: <1.0 = slower — critical for housewife/elderly users
            EmotionTone.EMERGENCY  to VoiceSettings(0.90, 0.90, 0.00, speed = 1.00), // urgent stays normal speed
            EmotionTone.EMPATHETIC to VoiceSettings(0.75, 0.85, 0.03, speed = 0.82), // gentle and slow
            EmotionTone.NORMAL     to VoiceSettings(0.72, 0.82, 0.08, speed = 0.85), // clear, measured
            EmotionTone.WARM       to VoiceSettings(0.68, 0.82, 0.12, speed = 0.83)  // warm but not rushed
        )
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30,  TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false

    // ── INIT ──────────────────────────────────────────────────────────────────

    fun init(onReady: () -> Unit) {
        androidTts = TextToSpeech(context) { status ->
            androidTtsReady = (status == TextToSpeech.SUCCESS)
            if (androidTtsReady) androidTts?.language = Locale("hi", "IN")
            onReady()
        }
    }

    // ── PUBLIC speak() ────────────────────────────────────────────────────────
    // tone defaults to NORMAL so all existing call sites compile unchanged.

    fun speak(
        text: String,
        language: String,
        tone: EmotionTone = EmotionTone.NORMAL,
        onDone: (() -> Unit)? = null
    ) {
        if (text.isBlank()) { Log.w(TAG, "speak() blank — skip"); onDone?.invoke(); return }

        // ── Normalize Devanagari loanwords before sending to ElevenLabs ──
        // "ऑर्डर" → "order", "पेमेंट" → "payment", etc.
        // Native Hindi words are untouched.
        val normalizedText = normalizeForTTS(text)

        val resolvedVoice = resolveVoice(normalizedText, language)
        val settings      = TONE_SETTINGS[tone] ?: TONE_SETTINGS[EmotionTone.NORMAL]!!
        Log.d(TAG, "ElevenLabs [$language] tone=$tone voice=$resolvedVoice → \"${normalizedText.take(60)}\"")

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

    // ── VOICE RESOLUTION ──────────────────────────────────────────────────────

    private fun resolveVoice(text: String, language: String): String {
        if (DEVANAGARI.containsMatchIn(text)) return VOICE_HI
        return when {
            language.startsWith("hi") || language.startsWith("mr") -> VOICE_HI
            else -> VOICE_EN
        }
    }

    // ── ELEVENLABS HTTP ───────────────────────────────────────────────────────

    private fun fetchElevenLabsAudio(
        text: String,
        voice: String,
        settings: VoiceSettings
    ): ByteArray? {
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", ELEVEN_MODEL)
            put("voice_settings", JSONObject().apply {
                put("stability",        settings.stability)
                put("similarity_boost", settings.similarityBoost)
                put("style",            settings.style)
                put("use_speaker_boost", settings.useSpeakerBoost)
                put("speed",            settings.speed) // 0.85 = ~15% slower, still natural
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

    // ── MEDIAPLAYER ───────────────────────────────────────────────────────────

    private fun playAudioBytes(context: Context, audioBytes: ByteArray, onDone: (() -> Unit)?) {
        val tmp = File(context.cacheDir, "butler_tts_${System.currentTimeMillis()}.mp3")
        try { tmp.writeBytes(audioBytes) }
        catch (e: Exception) { Log.e(TAG, "Temp file write failed: ${e.message}"); onDone?.invoke(); return }

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

    // ── ANDROID TTS FALLBACK ──────────────────────────────────────────────────

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
        } catch (e: Exception) { Log.e(TAG, "Android TTS: ${e.message}"); onDone?.invoke() }
    }

    // ── STOP / SHUTDOWN ───────────────────────────────────────────────────────

    fun stop()     { releasePlayer(); androidTts?.stop() }
    fun shutdown() { releasePlayer(); androidTts?.stop(); androidTts?.shutdown(); androidTts = null }
}