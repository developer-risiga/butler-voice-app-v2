package com.demo.butler_voice_app.voice

import com.demo.butler_voice_app.ai.SessionLanguageManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class SarvamSTTManager(
    private val context: Context,
    private val apiKey: String
) {
    companion object {
        private const val TAG          = "SarvamSTT"
        private const val ENDPOINT     = "https://api.sarvam.ai/speech-to-text"
        private const val SAMPLE_RATE  = 16000
        private const val SILENCE_MS   = 1500L
        private const val MAX_REC_MS   = 7000L
        private const val MAX_TOKENS   = 6
        private const val REFILL_MS    = 10_000L
        private const val BASE_BACKOFF = 2_000L
        private const val MAX_BACKOFF  = 32_000L
        private const val MAX_RETRIES  = 4

        // ── Cross-script noise suppression ────────────────────────────────────
        //
        // PROBLEM THIS SOLVES:
        // During a hi-IN session, Sarvam occasionally returns 1-3 word results
        // in Gujarati/Kannada/Bengali — background noise it misidentified.
        // Example: session=hi-IN, Sarvam returns "ಅವತ್" (kn-IN, 1 word).
        // This was flipping the session language for no reason.
        //
        // THRESHOLD = 6: Real Gujarati input always comes with a full sentence.
        // A 1-6 word result in a completely different script = noise.
        //
        // CRITICAL RULE: NEVER suppress en-IN → Indic.
        // After wake word, session = en-IN (forced default).
        // Roy's first Hindi utterance is ALWAYS legitimate, never noise.
        // Suppressing it forces Roy to repeat 4x → AIParser sees qty=4 (bug).
        // ─────────────────────────────────────────────────────────────────────
        private const val NOISE_WORD_THRESHOLD = 6

        private val NON_DEVANAGARI_CODES = setOf(
            "kn-IN", "te-IN", "ta-IN", "ml-IN", "gu-IN", "pa-IN", "bn-IN", "or-IN"
        )
        private val DEVANAGARI_CODES = setOf("hi-IN", "mr-IN", "ne-IN", "mai-IN")
    }

    private val http        = OkHttpClient()
    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recorder    : AudioRecord? = null
    private var isRecording = false
    private val tokens      = AtomicInteger(MAX_TOKENS)
    private val lastRefill  = AtomicLong(System.currentTimeMillis())

    var lastPcmBuffer: ShortArray = ShortArray(0)
        private set
    var lastRecordingDurationMs: Long = 0L
        private set

    fun startListening(onResult: (String) -> Unit, onError: () -> Unit) {
        if (isRecording) stop()
        isRecording = true
        scope.launch {
            try {
                val audioBytes = recordAudio()
                if (audioBytes == null || audioBytes.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult("") }
                    return@launch
                }
                Log.d(TAG, "Sending audio to Sarvam (${audioBytes.size} bytes)")
                val transcript = transcribeWithRetry(audioBytes)
                withContext(Dispatchers.Main) { onResult(transcript) }
            } catch (e: Exception) {
                Log.e(TAG, "STT error: ${e.message}")
                withContext(Dispatchers.Main) { onError() }
            } finally {
                isRecording = false
            }
        }
    }

    fun stop() {
        isRecording = false
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    private fun recordAudio(): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return null
        }

        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
        )

        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            return null
        }

        recorder?.startRecording()
        Log.d(TAG, "Recording started")

        val allBytes    = mutableListOf<Byte>()
        val allShorts   = mutableListOf<Short>()
        val buf         = ShortArray(bufSize / 2)
        val startMs     = System.currentTimeMillis()
        var lastSoundMs = startMs

        while (isRecording) {
            val read = recorder?.read(buf, 0, buf.size) ?: break
            if (read <= 0) break

            val rms = sqrt(buf.take(read).sumOf { (it * it).toDouble() } / read)
            if (rms > 300) lastSoundMs = System.currentTimeMillis()

            for (i in 0 until read) allShorts.add(buf[i])
            val pcm = ByteArray(read * 2)
            for (i in 0 until read) {
                pcm[i * 2]     = (buf[i].toInt() and 0xFF).toByte()
                pcm[i * 2 + 1] = (buf[i].toInt() shr 8 and 0xFF).toByte()
            }
            allBytes.addAll(pcm.toList())

            val now = System.currentTimeMillis()
            if (now - lastSoundMs > SILENCE_MS && allBytes.size > bufSize * 2) {
                Log.d(TAG, "Silence detected, stopping"); break
            }
            if (now - startMs > MAX_REC_MS) break
        }

        lastPcmBuffer           = allShorts.toShortArray()
        lastRecordingDurationMs = System.currentTimeMillis() - startMs

        stop()
        if (allBytes.isEmpty()) return null
        return addWavHeader(allBytes.toByteArray())
    }

    private suspend fun transcribeWithRetry(audioBytes: ByteArray): String {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            awaitToken()
            val response = sendHttp(audioBytes)
            Log.d(TAG, "Response: $response")

            val obj = try { JSONObject(response) } catch (_: Exception) { JSONObject() }

            when {
                obj.has("error") -> {
                    val code = obj.optJSONObject("error")?.optString("code") ?: ""
                    if (code == "rate_limit_exceeded_error") {
                        attempt++
                        val wait = backoffMs(attempt)
                        Log.w(TAG, "Rate limited — waiting ${wait}ms (attempt $attempt/$MAX_RETRIES)")
                        delay(wait)
                    } else {
                        Log.e(TAG, "STT error: $response"); return ""
                    }
                }
                obj.has("transcript") -> {
                    val t        = obj.optString("transcript", "").trim()
                    val langCode = obj.optString("language_code", "en-IN")
                    Log.d(TAG, "Locked: $langCode")

                    // ── Cross-script noise suppression ────────────────────────
                    if (t.isNotBlank()) {
                        val sessionHint = SessionLanguageManager.sarvamHint
                        if (sessionHint != null && langCode != sessionHint) {
                            val sessionBase = sessionHint.substringBefore("-").lowercase()

                            // ══ CRITICAL FIX ══════════════════════════════════
                            // NEVER suppress when session language is English.
                            //
                            // Root cause of the 4-retry bug:
                            //   1. Wake word detected → session forced to en-IN
                            //   2. Roy says "mujhe rice lena hai" (hi-IN, 4 words)
                            //   3. OLD CODE: sessionHint=en-IN, detectedIsOtherScript=true,
                            //      wordCount=4 ≤ 6 → SUPPRESSED → blank transcript
                            //   4. Roy repeats 4x → AIParser: quantity=4 (wrong!)
                            //
                            // en-IN is ONLY a forced default, never a real user language.
                            // Any Indic detection when session=en-IN = user's real language.
                            // ══════════════════════════════════════════════════
                            if (sessionBase == "en") {
                                Log.d(TAG, "en-IN → $langCode: legitimate language start, passing through")
                                // No suppression — fall through to return t
                            } else {
                                // Indic → Different Indic: apply noise suppression
                                val wordCount = t.trim()
                                    .split("\\s+".toRegex())
                                    .filter { it.isNotEmpty() }
                                    .size
                                val sessionIsDevanagari = sessionHint in DEVANAGARI_CODES
                                val detectedIsOtherScript = when {
                                    sessionIsDevanagari -> langCode in NON_DEVANAGARI_CODES
                                    else                -> langCode in DEVANAGARI_CODES
                                }
                                if (detectedIsOtherScript && wordCount <= NOISE_WORD_THRESHOLD) {
                                    Log.w(TAG, "Cross-script noise suppressed: '$t' " +
                                            "($langCode) session=$sessionHint wordCount=$wordCount")
                                    return ""
                                }
                            }
                        }
                    }

                    if (t.isBlank()) Log.e(TAG, "No transcript found")
                    else             Log.d(TAG, "Transcript: $t")
                    return t
                }
                else -> {
                    Log.e(TAG, "Unexpected response: $response"); return ""
                }
            }
        }
        Log.e(TAG, "Max retries ($MAX_RETRIES) exceeded")
        return ""
    }

    private fun sendHttp(audioBytes: ByteArray): String {
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", "audio.wav",
                    audioBytes.toRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "saarika:v2.5")
                .build()

            // Only send language hint for confirmed Indic sessions.
            // For en-IN (the forced default), don't constrain Sarvam's detection —
            // we want it to freely detect whatever language Roy is speaking.
            val hint = SessionLanguageManager.sarvamHint
            val url  = if (hint != null && !hint.startsWith("en")) "$ENDPOINT?language_code=$hint"
            else ENDPOINT

            val req = Request.Builder()
                .url(url)
                .header("api-subscription-key", apiKey)
                .post(body)
                .build()

            http.newCall(req).execute().body?.string() ?: "{}"
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}"); "{}"
        }
    }

    private suspend fun awaitToken() {
        while (true) {
            refill()
            if (tokens.get() > 0) { tokens.decrementAndGet(); return }
            Log.d(TAG, "Token bucket empty — waiting ${REFILL_MS}ms")
            delay(REFILL_MS)
        }
    }

    private fun refill() {
        val now     = System.currentTimeMillis()
        val elapsed = now - lastRefill.get()
        val add     = (elapsed / REFILL_MS).toInt()
        if (add > 0) {
            tokens.set(min(MAX_TOKENS, tokens.get() + add))
            lastRefill.set(now)
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val capped = min(BASE_BACKOFF * (1L shl (attempt - 1)), MAX_BACKOFF)
        return (capped + Random.nextLong(-300L, 300L)).coerceAtLeast(500L)
    }

    private fun addWavHeader(pcm: ByteArray): ByteArray {
        val totalLen = pcm.size + 36
        val byteRate = SAMPLE_RATE * 2
        return ByteArray(44).also { h ->
            fun Int.le4(i: Int) {
                h[i] = toByte(); h[i+1] = (shr(8)).toByte()
                h[i+2] = (shr(16)).toByte(); h[i+3] = (shr(24)).toByte()
            }
            fun Int.le2(i: Int) { h[i] = toByte(); h[i+1] = (shr(8)).toByte() }
            "RIFF".forEachIndexed { i, c -> h[i]    = c.code.toByte() }; totalLen.le4(4)
            "WAVE".forEachIndexed { i, c -> h[8+i]  = c.code.toByte() }
            "fmt ".forEachIndexed { i, c -> h[12+i] = c.code.toByte() }
            16.le4(16); 1.le2(20); 1.le2(22)
            SAMPLE_RATE.le4(24); byteRate.le4(28); 2.le2(32); 16.le2(34)
            "data".forEachIndexed { i, c -> h[36+i] = c.code.toByte() }
            pcm.size.le4(40)
        } + pcm
    }
}