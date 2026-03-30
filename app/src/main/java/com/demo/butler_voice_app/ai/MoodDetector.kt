package com.demo.butler_voice_app.ai

import android.util.Log

enum class UserMood {
    CALM,        // normal — standard Butler behavior
    FRUSTRATED,  // repeated attempts, high amplitude — skip options, go direct
    RUSHED,      // fast speech, short silence gaps — skip confirmations
    TIRED        // low amplitude, slow speech — proactive suggestions
}

data class MoodSignals(
    val avgAmplitude: Double,      // 0–32767, higher = louder/more forceful
    val amplitudeVariance: Double, // high variance = agitated
    val speechDurationMs: Long,    // total duration of actual speech
    val silenceRatioPercent: Int,  // % of recording that was silence
    val peakAmplitude: Int         // single loudest moment
)

object MoodDetector {

    private const val TAG = "MoodDetector"

    // Amplitude thresholds — tune these after testing in real environment
    private const val LOUD_THRESHOLD    = 8000.0   // above this = raised voice
    private const val QUIET_THRESHOLD   = 2000.0   // below this = soft/tired voice
    private const val SILENCE_THRESHOLD = 800       // below this = silence

    // Session frustration tracker — resets each session
    private var consecutiveRetries = 0
    private var lastMood = UserMood.CALM

    fun reset() {
        consecutiveRetries = 0
        lastMood = UserMood.CALM
    }

    fun recordRetry() {
        consecutiveRetries++
        Log.d(TAG, "Retry count: $consecutiveRetries")
    }

    // Main function — call this with the PCM buffer collected during recording
    fun analyse(pcmBuffer: ShortArray, recordingDurationMs: Long): UserMood {
        if (pcmBuffer.isEmpty()) return UserMood.CALM

        val signals = extractSignals(pcmBuffer, recordingDurationMs)
        Log.d(TAG, "Mood signals: avg=${signals.avgAmplitude.toInt()} " +
                "variance=${signals.amplitudeVariance.toInt()} " +
                "silence=${signals.silenceRatioPercent}% " +
                "retries=$consecutiveRetries")

        val mood = classify(signals)
        lastMood = mood
        Log.d(TAG, "Detected mood: $mood")
        return mood
    }

    private fun extractSignals(buffer: ShortArray, durationMs: Long): MoodSignals {
        var sum       = 0.0
        var peak      = 0
        var silentSamples = 0

        for (sample in buffer) {
            val abs = Math.abs(sample.toInt())
            sum += abs
            if (abs > peak) peak = abs
            if (abs < SILENCE_THRESHOLD) silentSamples++
        }

        val avg      = sum / buffer.size
        val silence  = (silentSamples * 100) / buffer.size

        // Variance — measures how "agitated" vs smooth the speech is
        var varianceSum = 0.0
        for (sample in buffer) {
            val diff = Math.abs(sample.toInt()) - avg
            varianceSum += diff * diff
        }
        val variance = varianceSum / buffer.size

        return MoodSignals(
            avgAmplitude       = avg,
            amplitudeVariance  = variance,
            speechDurationMs   = durationMs,
            silenceRatioPercent = silence,
            peakAmplitude      = peak
        )
    }

    private fun classify(signals: MoodSignals): UserMood {
        // FRUSTRATED: high amplitude + retries, or very high variance
        if (consecutiveRetries >= 2 ||
            (signals.avgAmplitude > LOUD_THRESHOLD && signals.amplitudeVariance > 20_000_000)) {
            return UserMood.FRUSTRATED
        }

        // RUSHED: short speech duration + low silence ratio (talking fast, no pauses)
        if (signals.speechDurationMs < 1500 && signals.silenceRatioPercent < 20) {
            return UserMood.RUSHED
        }

        // TIRED: low amplitude + high silence ratio (slow, quiet speech)
        if (signals.avgAmplitude < QUIET_THRESHOLD && signals.silenceRatioPercent > 50) {
            return UserMood.TIRED
        }

        return UserMood.CALM
    }

    fun getLastMood(): UserMood = lastMood
}