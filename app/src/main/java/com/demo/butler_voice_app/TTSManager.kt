package com.demo.butler_voice_app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════════════════════════════════════
// TTSManager — fixes THREE bugs in one file:
//
// BUG 1 — MediaPlayer "went away with unhandled events" (TTS cutoff)
//   CAUSE:  MediaPlayer created as local var → GC kills it before onCompletion
//   FIX:    Hold as class member `private var player: MediaPlayer?`
//           Release only inside onCompletion / onError callbacks
//
// BUG 2 — Hindi TTS broken / mixed language in responses
//   CAUSE:  English text sent to Hindi ElevenLabs voice, or vice versa
//   FIX:    detectLang() checks for Devanagari script → routes to Hindi voice
//           ElevenLabs multilingual-v2 model handles both scripts cleanly
//
// BUG 3 — Silent drops / onDone never called
//   CAUSE:  Network error or MediaPlayer error → onDone callback swallowed
//   FIX:    Every error path calls onDone() so the conversation never freezes
// ══════════════════════════════════════════════════════════════════════════════

class TTSManager(
    private val context: Context,
    private val elevenLabsApiKey: String,
    private val voiceId: String = "RwXLkVKnRloV1UPh3Ccx"     // English default
) {
    companion object {
        private const val TAG = "TTS"

        // ── Voice IDs ──────────────────────────────────────────────────────────
        private const val VOICE_EN = "RwXLkVKnRloV1UPh3Ccx"   // English
        private const val VOICE_HI = "pqHfZKP75CvOlQylNhV4"   // Hindi

        // ElevenLabs multilingual-v2 handles both en + hi scripts without
        // mispronouncing — do NOT use monolingual models for Hindi text
        private const val ELEVEN_MODEL = "eleven_multilingual_v2"

        // Devanagari Unicode block: U+0900–U+097F
        private val DEVANAGARI = Regex("[\\u0900-\\u097F]")
    }

    // ── HTTP client (shared, not recreated per call) ───────────────────────
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── FIX 1: Strong reference — survives GC until onCompletion fires ─────
    private var player: MediaPlayer? = null

    // ── Fallback Android TTS (used if ElevenLabs fails) ───────────────────
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
    // PUBLIC speak() — entry point for all TTS in the app
    // ══════════════════════════════════════════════════════════════════════

    fun speak(text: String, language: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            Log.w(TAG, "speak() called with blank text — skipping")
            onDone?.invoke()
            return
        }

        // ── FIX 2: Route to correct voice based on script ─────────────────
        val resolvedVoice = resolveVoice(text, language)
        Log.d(TAG, "ElevenLabs [$language] voice=$resolvedVoice → \"$text\"")

        // Network call must be off main thread
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
    // VOICE RESOLUTION — FIX 2: detect Hindi script, pick correct voice
    // ══════════════════════════════════════════════════════════════════════

    private fun resolveVoice(text: String, language: String): String {
        // If text contains Devanagari characters → always use Hindi voice
        if (DEVANAGARI.containsMatchIn(text)) return VOICE_HI

        // Language tag routing
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
                put("stability", 0.5)
                put("similarity_boost", 0.75)
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voice")
            .addHeader("xi-api-key", elevenLabsApiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "audio/mpeg")
            .post(body)
            .build()

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "ElevenLabs HTTP error: ${response.code} — ${response.body?.string()?.take(200)}")
            return null
        }
        return response.body?.bytes()
    }

    // ══════════════════════════════════════════════════════════════════════
    // MEDIAPLAYER — FIX 1 + FIX 3: strong ref, onDone always called
    // ══════════════════════════════════════════════════════════════════════

    private fun playAudioBytes(context: Context, audioBytes: ByteArray, onDone: (() -> Unit)?) {
        // Write to temp file — MediaPlayer cannot play from byte[]
        val tmp = File(context.cacheDir, "butler_tts_${System.currentTimeMillis()}.mp3")
        try {
            tmp.writeBytes(audioBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write temp audio: ${e.message}")
            onDone?.invoke()   // FIX 3: never swallow onDone
            return
        }

        // Release previous player before creating new one
        releasePlayer()

        // FIX 1: assign to class member — strong reference prevents GC
        player = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                setDataSource(tmp.absolutePath)
                prepare()   // synchronous — file is local, instant

                // FIX 1 + FIX 3: release in BOTH callbacks
                setOnCompletionListener {
                    Log.d(TAG, "Playback complete")
                    tmp.delete()
                    releasePlayer()
                    onDone?.invoke()   // ← always fires
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    tmp.delete()
                    releasePlayer()
                    onDone?.invoke()   // FIX 3: fires even on error
                    true
                }

                start()
                Log.d(TAG, "Playback started (${audioBytes.size} bytes)")

            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer setup failed: ${e.message}")
                tmp.delete()
                releasePlayer()
                onDone?.invoke()   // FIX 3: fires even on exception
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RELEASE — safe, idempotent
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
            player = null   // ← nullify so GC can reclaim
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ANDROID TTS FALLBACK — used when ElevenLabs fails
    // Ensures the conversation never freezes even without internet
    // ══════════════════════════════════════════════════════════════════════

    private fun speakWithAndroidTts(text: String, language: String, onDone: (() -> Unit)?) {
        val tts = androidTts
        if (tts == null || !androidTtsReady) {
            Log.e(TAG, "Android TTS not ready — invoking onDone directly")
            onDone?.invoke()   // FIX 3: still unblocks conversation flow
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
                override fun onDone(id: String?) {
                    Log.d(TAG, "Android TTS done")
                    onDone?.invoke()   // FIX 3: always fires
                }
                override fun onError(id: String?) {
                    Log.e(TAG, "Android TTS error")
                    onDone?.invoke()   // FIX 3: fires on error too
                }
            })

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        } catch (e: Exception) {
            Log.e(TAG, "Android TTS exception: ${e.message}")
            onDone?.invoke()   // FIX 3: last-resort unblock
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // STOP — call when user starts speaking or session ends
    // ══════════════════════════════════════════════════════════════════════

    fun stop() {
        releasePlayer()
        androidTts?.stop()
    }

    // ══════════════════════════════════════════════════════════════════════
    // SHUTDOWN — call in onDestroy()
    // ══════════════════════════════════════════════════════════════════════

    fun shutdown() {
        releasePlayer()
        androidTts?.stop()
        androidTts?.shutdown()
        androidTts = null
    }
}