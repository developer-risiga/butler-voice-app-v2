package com.demo.butler_voice_app.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
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

/**
 * SarvamSTTManager — SAME API as before (startListening / stop).
 * Rate limit fix added inside: token bucket (max 6 req/min) + exponential backoff.
 */
class SarvamSTTManager(
    private val context: Context,
    private val apiKey: String
) {
    companion object {
        private const val TAG         = "SarvamSTT"
        private const val ENDPOINT    = "https://api.sarvam.ai/speech-to-text"
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_MS  = 1500L
        private const val MAX_REC_MS  = 7000L

        // Token bucket — 6 requests per minute max
        private const val MAX_TOKENS   = 6
        private const val REFILL_MS    = 10_000L   // 1 token per 10s

        // Backoff on rate-limit
        private const val BASE_BACKOFF = 2_000L
        private const val MAX_BACKOFF  = 32_000L
        private const val MAX_RETRIES  = 4
    }

    private val http       = OkHttpClient()
    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recorder   : AudioRecord? = null
    private var isRecording = false

    // Token bucket state
    private val tokens     = AtomicInteger(MAX_TOKENS)
    private val lastRefill = AtomicLong(System.currentTimeMillis())

    // ── Public API (unchanged from original) ─────────────────

    fun startListening(
        onResult : (String) -> Unit,
        onError  : () -> Unit
    ) {
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

    // ── Recording ─────────────────────────────────────────────

    private fun recordAudio(): ByteArray? {
        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )

        recorder?.startRecording()
        Log.d(TAG, "Recording started")

        val allBytes   = mutableListOf<Byte>()
        val buf        = ShortArray(bufSize / 2)
        val startMs    = System.currentTimeMillis()
        var silenceMs  = 0L
        var lastSoundMs = startMs

        while (isRecording) {
            val read = recorder?.read(buf, 0, buf.size) ?: break
            if (read <= 0) break

            val rms = sqrt(buf.take(read).sumOf { (it * it).toDouble() } / read)
            if (rms > 300) lastSoundMs = System.currentTimeMillis()

            val pcm = ByteArray(read * 2)
            for (i in 0 until read) {
                pcm[i * 2]     = (buf[i].toInt() and 0xFF).toByte()
                pcm[i * 2 + 1] = (buf[i].toInt() shr 8 and 0xFF).toByte()
            }
            allBytes.addAll(pcm.toList())

            val now = System.currentTimeMillis()
            silenceMs = now - lastSoundMs

            if (silenceMs > SILENCE_MS && allBytes.size > bufSize * 2) {
                Log.d(TAG, "Silence detected, stopping")
                break
            }
            if (now - startMs > MAX_REC_MS) break
        }

        stop()
        if (allBytes.isEmpty()) return null
        Log.d(TAG, "Sending audio to Sarvam (${allBytes.size} bytes)")
        return addWavHeader(allBytes.toByteArray(), SAMPLE_RATE)
    }

    // ── Transcription with rate-limit protection ──────────────

    private suspend fun transcribeWithRetry(audioBytes: ByteArray): String {
        var attempt = 0
        while (attempt <= MAX_RETRIES) {
            awaitToken()
            val response = sendHttp(audioBytes)
            Log.d(TAG, "Response: $response")

            val obj = try { JSONObject(response) } catch (_: Exception) { JSONObject() }

            when {
                obj.has("error") -> {
                    val code = obj.optJSONObject("error")?.optString("code") ?: ""
                    if (code == "rate_limit_exceeded_error") {
                        attempt++
                        if (attempt > MAX_RETRIES) {
                            Log.e(TAG, "Rate limit: max retries exceeded")
                            return ""
                        }
                        val wait = backoffMs(attempt)
                        Log.w(TAG, "Rate limited — waiting ${wait}ms (attempt $attempt)")
                        delay(wait)
                    } else {
                        Log.e(TAG, "STT error: $response")
                        return ""
                    }
                }
                obj.has("transcript") -> {
                    val t = obj.optString("transcript", "").trim()
                    if (t.isBlank()) Log.e(TAG, "No transcript found")
                    else Log.d(TAG, "Transcript: $t")
                    return t
                }
                else -> {
                    Log.e(TAG, "Unexpected response: $response")
                    return ""
                }
            }
        }
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
                .addFormDataPart("model", "saarika:v1")
                .build()

            val req = Request.Builder()
                .url(ENDPOINT)
                .header("api-subscription-key", apiKey)
                .post(body)
                .build()

            http.newCall(req).execute().body?.string() ?: "{}"
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
            "{}"
        }
    }

    // ── Token bucket ──────────────────────────────────────────

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
        val expo   = BASE_BACKOFF * (1L shl (attempt - 1))
        val capped = min(expo, MAX_BACKOFF)
        return (capped + Random.nextLong(-300L, 300L)).coerceAtLeast(500L)
    }

    // ── WAV header ────────────────────────────────────────────

    private fun addWavHeader(pcm: ByteArray, sampleRate: Int): ByteArray {
        val totalLen  = pcm.size + 36
        val byteRate  = sampleRate * 2
        return ByteArray(44).also { h ->
            fun Int.le4(i: Int) { h[i] = toByte(); h[i+1] = (shr(8)).toByte(); h[i+2] = (shr(16)).toByte(); h[i+3] = (shr(24)).toByte() }
            fun Int.le2(i: Int) { h[i] = toByte(); h[i+1] = (shr(8)).toByte() }
            "RIFF".forEachIndexed { i, c -> h[i] = c.code.toByte() }
            totalLen.le4(4)
            "WAVE".forEachIndexed { i, c -> h[8+i] = c.code.toByte() }
            "fmt ".forEachIndexed { i, c -> h[12+i] = c.code.toByte() }
            16.le4(16); 1.le2(20); 1.le2(22)
            sampleRate.le4(24); byteRate.le4(28); 2.le2(32); 16.le2(34)
            "data".forEachIndexed { i, c -> h[36+i] = c.code.toByte() }
            pcm.size.le4(40)
        } + pcm
    }
}