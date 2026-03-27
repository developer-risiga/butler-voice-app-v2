package com.demo.butler_voice_app.services

import android.content.Context
import android.util.Log
import com.demo.butler_voice_app.ai.LanguageManager

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE VOICE HANDLER
// Plug this into MainActivity to handle all India services via voice
// ══════════════════════════════════════════════════════════════════════════════

object ServiceVoiceHandler {

    private const val TAG = "ServiceVoiceHandler"

    // ── Keywords that trigger the services platform ────────────────────────────
    private val SERVICE_TRIGGER_WORDS = listOf(
        // English
        "book", "need", "find", "call", "hire", "service", "services",
        // Hindi
        "chahiye", "bulao", "dhundho", "service chahiye", "kaam",
        // Telugu
        "kావాలి", "పిలవండి", "సేవ",
        // Common service names (from all sectors)
        "plumber", "electrician", "doctor", "medicine", "dawa", "taxi",
        "food", "khana", "salon", "carpenter", "painter", "cleaner",
        "maid", "driver", "tutor", "lawyer", "ca", "insurance", "loan",
        "nurse", "ambulance", "gym", "trainer", "pet", "pandit",
        "courier", "laundry", "tailor", "photographer", "catering"
    )

    // ── Check if transcript is a service request ───────────────────────────────
    fun isServiceRequest(transcript: String): Boolean {
        val lower = transcript.lowercase()
        return SERVICE_TRIGGER_WORDS.any { lower.contains(it) } &&
                ServiceManager.detectServiceIntent(transcript).sector != null
    }

    // ── Check if transcript is a prescription request ─────────────────────────
    fun isPrescriptionRequest(transcript: String): Boolean {
        val lower = transcript.lowercase()
        val rxWords = listOf("prescription", "parchi", "parchee", "dawai ki parchee",
            "doctor ne likha", "upload prescription", "medicines from prescription",
            "scan prescription", "read prescription")
        return rxWords.any { lower.contains(it) }
    }

    // ── Build voice prompt asking which service ────────────────────────────────
    fun buildServiceCategoryPrompt(lang: String = "en"): String = when {
        lang.startsWith("hi") ->
            "आप कौन सी सेवा चाहते हैं? जैसे प्लंबर, डॉक्टर, खाना, दवाई, टैक्सी, या कोई और?"
        lang.startsWith("te") ->
            "మీకు ఏ సేవ కావాలి? ప్లంబర్, డాక్టర్, తినుబండారాలు, మందులు లేదా ఏదైనా?"
        lang.startsWith("ta") ->
            "உங்களுக்கு என்ன சேவை வேண்டும்? பிளம்பர், டாக்டர், உணவு, மருந்து?"
        else ->
            "Which service do you need? Say plumber, doctor, food, medicine, taxi, or any other service."
    }

    // ── Build voice response for detected sector ───────────────────────────────
    fun buildSectorDetectedPrompt(sector: ServiceSector, lang: String = "en"): String = when {
        lang.startsWith("hi") -> "मैं ${sector.displayName} के लिए आपके पास के providers ढूंढ रहा हूं।"
        lang.startsWith("te") -> "${sector.displayName} కోసం మీ దగ్గర ఉన్న providers కనుగొంటున్నాను।"
        else -> "Finding ${sector.displayName} providers near you. Please wait a moment."
    }

    // ── Build voice response when provider selected ────────────────────────────
    fun buildProviderSelectedPrompt(provider: ServiceProvider, lang: String = "en"): String = when {
        lang.startsWith("hi") ->
            "${provider.name} चुना गया। वो ${provider.distanceKm} किलोमीटर दूर हैं और ${provider.eta} में आ सकते हैं। क्या बुक करूं?"
        else ->
            "You've selected ${provider.name}, ${provider.distanceKm}km away, arriving in ${provider.eta}. Shall I confirm the booking? Say yes to confirm or no to see other options."
    }

    // ── Build voice booking confirmed prompt ───────────────────────────────────
    fun buildBookingConfirmedPrompt(provider: ServiceProvider, bookingId: String, lang: String = "en"): String = when {
        lang.startsWith("hi") ->
            "${provider.name} की बुकिंग हो गई। Booking ID है $bookingId। वो ${provider.eta} में पहुंच जाएंगे। धन्यवाद!"
        else ->
            "${provider.name} has been booked! Your booking ID is $bookingId. They'll arrive in ${provider.eta}. Is there anything else you need?"
    }

    // ── Build prescription voice prompts ──────────────────────────────────────
    fun buildPrescriptionUploadPrompt(lang: String = "en"): String = when {
        lang.startsWith("hi") ->
            "अपनी prescription की फोटो लें या gallery से upload करें। मैं medicines का नाम पढ़ लूंगा।"
        else ->
            "Please photograph your prescription or upload it from your gallery. I'll read the medicine names automatically and find the nearest pharmacy."
    }

    fun buildMedicinesExtractedPrompt(medicines: List<String>, pharmacyCount: Int, lang: String = "en"): String {
        val medNames = when {
            medicines.isEmpty() -> "the medicines"
            medicines.size == 1 -> medicines[0]
            medicines.size <= 3 -> medicines.dropLast(1).joinToString(", ") + " and " + medicines.last()
            else -> "${medicines.take(2).joinToString(", ")} and ${medicines.size - 2} more"
        }
        return when {
            lang.startsWith("hi") ->
                "मैंने prescription से $medNames पढ़ा। $pharmacyCount pharmacies आपके पास ये दे सकती हैं। 1, 2, या 3 बोलो।"
            else ->
                "I've read your prescription and found $medNames. $pharmacyCount nearby pharmacies can deliver these. Say 1, 2, or 3 to choose."
        }
    }

    // ── Filter commands ────────────────────────────────────────────────────────
    fun parseFilterCommand(transcript: String): ServiceFilter? {
        val lower = transcript.lowercase()
        return when {
            lower.contains("by rating") || lower.contains("best rated") || lower.contains("highest rated") ->
                ServiceFilter(sortBy = ServiceSort.RATING)
            lower.contains("nearest") || lower.contains("closest") || lower.contains("paas wala") ->
                ServiceFilter(sortBy = ServiceSort.DISTANCE)
            lower.contains("cheapest") || lower.contains("cheap") || lower.contains("sasta") || lower.contains("budget") ->
                ServiceFilter(sortBy = ServiceSort.PRICE_LOW)
            lower.contains("fastest") || lower.contains("quick") || lower.contains("jaldi") ->
                ServiceFilter(sortBy = ServiceSort.FASTEST)
            lower.contains("premium") || lower.contains("best") ->
                ServiceFilter(minRating = 4.5, sortBy = ServiceSort.RATING)
            else -> null
        }
    }

    // ── Emergency detection ────────────────────────────────────────────────────
    fun isEmergency(transcript: String): Boolean {
        val lower = transcript.lowercase()
        return listOf("emergency", "ambulance", "help me", "bachao", "accident", "unconscious",
            "heart attack", "stroke", "fire", "danger", "urgent help").any { lower.contains(it) }
    }

    fun buildEmergencyPrompt(lang: String = "en"): String = when {
        lang.startsWith("hi") -> "आपातकाल! 108 को call कर रहा हूं। कृपया शांत रहें।"
        else -> "EMERGENCY! Contacting 108 ambulance service now. Please stay calm and stay on the line."
    }

    // ── Number selection (1/2/3) from voice ───────────────────────────────────
    fun parseNumberSelection(transcript: String): Int? {
        val lower = transcript.lowercase().trim()
        return when {
            lower.contains("one") || lower.contains("एक") || lower.contains("ఒకటి") || lower == "1" -> 1
            lower.contains("two") || lower.contains("दो") || lower.contains("రెండు") || lower == "2" -> 2
            lower.contains("three") || lower.contains("तीन") || lower.contains("మూడు") || lower == "3" -> 3
            lower.contains("first") || lower.contains("pehla") -> 1
            lower.contains("second") || lower.contains("doosra") -> 2
            lower.contains("third") || lower.contains("teesra") -> 3
            else -> lower.filter { it.isDigit() }.firstOrNull()?.toString()?.toIntOrNull()
        }
    }
}