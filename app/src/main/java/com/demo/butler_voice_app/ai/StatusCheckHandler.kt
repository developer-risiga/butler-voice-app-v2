package com.demo.butler_voice_app.ai

import android.util.Log
import com.demo.butler_voice_app.api.OrderTrackingManager
import com.demo.butler_voice_app.api.TrackingType
import com.demo.butler_voice_app.api.TrackingVoiceStrings

// ══════════════════════════════════════════════════════════════════════════════
// Package: ai/ — matches MainActivity import
// Calls OrderTrackingManager (api/) for Supabase queries
// ══════════════════════════════════════════════════════════════════════════════

object StatusCheckHandler {

    private const val TAG = "StatusCheck"

    // Called from MainActivity.routeTranscript()
    fun isStatusQuery(text: String): Boolean {
        val lower = text.lowercase().trim()
        return STATUS_PATTERNS.any { lower.contains(it) }
    }

    // Called from MainActivity.handleStatusQuery()
    suspend fun handleStatusQuery(
        text: String,
        lang: String,
        firstName: String,
        userId: String,
        lastBookingId: String?
    ): StatusQueryResult {
        Log.d(TAG, "handleStatusQuery: text='$text' userId=$userId lastBookingId=$lastBookingId")

        // Extract explicit ID if user said "BUT-FB9815" or "BOR-100018"
        val specificId = extractId(text) ?: lastBookingId

        val result = if (specificId != null) {
            OrderTrackingManager.getStatusById(userId, specificId)
        } else {
            OrderTrackingManager.getLatestStatus(userId)
        }

        val voiceText = TrackingVoiceStrings.buildVoiceResponse(result, lang, firstName)

        return when (result.type) {
            TrackingType.ORDER   -> StatusQueryResult.OrderFound(voiceText, result)
            TrackingType.BOOKING -> StatusQueryResult.BookingFound(voiceText, result)
            TrackingType.UNKNOWN -> StatusQueryResult.NotFound(voiceText)
        }
    }

    private fun extractId(text: String): String? =
        Regex("\\b([A-Z]{2,4}-[A-Z0-9]{4,8})\\b", RegexOption.IGNORE_CASE)
            .find(text.uppercase())?.value

    private val STATUS_PATTERNS = listOf(
        // English
        "where is my order", "where's my order", "order status", "track my order",
        "when will my order", "delivery status", "where is my booking", "booking status",
        "when will the plumber", "when will the electrician", "has my order",
        "what happened to my order", "order update", "my order", "my delivery",
        "where is my delivery", "track my booking", "my plumber", "my electrician",
        // Hindi
        "मेरा ऑर्डर", "ऑर्डर कहाँ है", "ऑर्डर कहां है", "बुकिंग कहाँ है",
        "कब आएगा", "कितना टाइम", "डिलीवरी कब", "प्लंबर कब", "ऑर्डर स्टेटस",
        "मेरी बुकिंग", "कब पहुंचेगा", "order kahan", "kab aayega",
        // Marathi
        "माझा ऑर्डर", "ऑर्डर कुठे", "बुकिंग कुठे", "केव्हा येईल", "माझी बुकिंग",
        // Telugu
        "నా ఆర్డర్", "ఆర్డర్ ఎక్కడ", "బుకింగ్ ఎక్కడ", "ఎప్పుడు వస్తారు", "నా బుకింగ్"
    )
}

// ── Result sealed class ───────────────────────────────────────────────────────

sealed class StatusQueryResult {
    val voiceText: String get() = when (this) {
        is OrderFound   -> text
        is BookingFound -> text
        is NotFound     -> text
    }

    data class OrderFound(
        val text: String,
        val data: com.demo.butler_voice_app.api.TrackingResult
    ) : StatusQueryResult()

    data class BookingFound(
        val text: String,
        val data: com.demo.butler_voice_app.api.TrackingResult
    ) : StatusQueryResult()

    data class NotFound(val text: String) : StatusQueryResult()
}