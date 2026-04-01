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

class TTSManager(
    private val context: Context,
    private val elevenLabsApiKey: String,
    private val voiceId: String = "RwXLkVKnRloV1UPh3Ccx"
) {
    companion object {
        private const val TAG = "TTS"


        private const val VOICE_EN = "RwXLkVKnRloV1UPh3Ccx"   // Shreya — female
        private const val VOICE_HI = "RwXLkVKnRloV1UPh3Ccx"   // Shreya — same, multilingual

        // eleven_multilingual_v2: supports hi, te, en, mr, kn, ml, ta
        // Never use eleven_monolingual_v1 for Indian languages — it mispronounces
        private const val ELEVEN_MODEL = "eleven_multilingual_v2"

        // Devanagari Unicode block: U+0900–U+097F
        private val DEVANAGARI = Regex("[\\u0900-\\u097F]")
    }

    // ── HTTP client — shared, not recreated per call ───────────────────────
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30,  TimeUnit.SECONDS)
        .build()

    // Strong reference — survives GC until onCompletion fires
    private var player: MediaPlayer? = null

    // Fallback Android TTS (used if ElevenLabs fails or no internet)
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false

    // ══════════════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════════════

    fun init(onReady: () -> Unit) {
        androidTts = TextToSpeech(context) { status ->
            androidTtsReady = (status == TextToSpeech.SUCCESS)
            if (androidTtsReady) {
                androidTts?.language = Locale("hi", "IN")
            }
            onReady()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC speak()
    // ══════════════════════════════════════════════════════════════════════

    fun speak(text: String, language: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            Log.w(TAG, "speak() called with blank text — skipping")
            onDone?.invoke()
            return
        }

        val resolvedVoice = resolveVoice(text, language)
        Log.d(TAG, "ElevenLabs [$language] voice=$resolvedVoice → \"${text.take(60)}\"")

        val appContext = context.applicationContext
        Thread {
            try {
                val audioBytes = fetchElevenLabsAudio(text, resolvedVoice)
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    playAudioBytes(appContext, audioBytes, onDone)
                } else {
                    Log.w(TAG, "ElevenLabs returned empty audio — using Android TTS fallback")
                    speakWithAndroidTts(text, language, onDone)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ElevenLabs fetch error: ${e.message} — using Android TTS fallback")
                speakWithAndroidTts(text, language, onDone)
            }
        }.start()
    }

    // ══════════════════════════════════════════════════════════════════════
    // VOICE RESOLUTION
    // ══════════════════════════════════════════════════════════════════════

    private fun resolveVoice(text: String, language: String): String {
        // Devanagari script → Hindi voice (Shreya, multilingual)
        if (DEVANAGARI.containsMatchIn(text)) return VOICE_HI
        return when {
            language.startsWith("hi") || language.startsWith("mr") -> VOICE_HI
            else -> VOICE_EN
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ELEVENLABS HTTP CALL
    // ══════════════════════════════════════════════════════════════════════

    private fun fetchElevenLabsAudio(text: String, voice: String): ByteArray? {
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", ELEVEN_MODEL)
            put("voice_settings", JSONObject().apply {
                put("stability",        0.45)   // slightly lower = more natural Hindi
                put("similarity_boost", 0.80)
                put("style",            0.20)   // adds expressiveness
                put("use_speaker_boost", true)
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

    // ══════════════════════════════════════════════════════════════════════
    // MEDIAPLAYER
    // ══════════════════════════════════════════════════════════════════════

    private fun playAudioBytes(context: Context, audioBytes: ByteArray, onDone: (() -> Unit)?) {
        val tmp = File(context.cacheDir, "butler_tts_${System.currentTimeMillis()}.mp3")
        try {
            tmp.writeBytes(audioBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write temp audio: ${e.message}")
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

                setOnCompletionListener {
                    Log.d(TAG, "Playback complete")
                    tmp.delete()
                    releasePlayer()
                    onDone?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    tmp.delete()
                    releasePlayer()
                    onDone?.invoke()
                    true
                }

                start()
                Log.d(TAG, "Playback started (${audioBytes.size} bytes)")

            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer setup failed: ${e.message}")
                tmp.delete()
                releasePlayer()
                onDone?.invoke()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RELEASE
    // ══════════════════════════════════════════════════════════════════════

    private fun releasePlayer() {
        try {
            player?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "releasePlayer error: ${e.message}")
        } finally {
            player = null
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ANDROID TTS FALLBACK
    // ══════════════════════════════════════════════════════════════════════

    private fun speakWithAndroidTts(text: String, language: String, onDone: (() -> Unit)?) {
        val tts = androidTts
        if (tts == null || !androidTtsReady) {
            Log.e(TAG, "Android TTS not ready — invoking onDone directly")
            onDone?.invoke()
            return
        }

        try {
            val locale = when {
                language.startsWith("hi") -> Locale("hi", "IN")
                language.startsWith("mr") -> Locale("mr", "IN")
                language.startsWith("te") -> Locale("te", "IN")
                else -> Locale.ENGLISH
            }
            tts.language = locale

            val utteranceId = "butler_${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?)  { onDone?.invoke() }
                override fun onError(id: String?) { onDone?.invoke() }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        } catch (e: Exception) {
            Log.e(TAG, "Android TTS exception: ${e.message}")
            onDone?.invoke()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // STOP / SHUTDOWN
    // ══════════════════════════════════════════════════════════════════════

    fun stop() {
        releasePlayer()
        androidTts?.stop()
    }

    fun shutdown() {
        releasePlayer()
        androidTts?.stop()
        androidTts?.shutdown()
        androidTts = null
    }
}