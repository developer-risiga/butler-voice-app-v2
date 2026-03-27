package com.demo.butler_voice_app.services

import android.content.Context
import android.util.Log
import com.demo.butler_voice_app.api.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE MANAGER — Voice detection + search + RAG prescription
// ══════════════════════════════════════════════════════════════════════════════

object ServiceManager {

    private val http = OkHttpClient()
    private const val TAG = "ServiceManager"

    // ── 1. Detect service intent from voice transcript ────────────────────────
    fun detectServiceIntent(transcript: String): ServiceIntent {
        val lower = transcript.lowercase().trim()

        // Emergency detection first
        val emergencyWords = listOf("emergency", "accident", "ambulance", "hospital", "unconscious",
            "bleeding", "heart attack", "stroke", "fire", "help", "bachao", "madad")
        if (emergencyWords.any { lower.contains(it) }) {
            return ServiceIntent(
                sector      = ServiceSector.AMBULANCE,
                query       = transcript,
                isEmergency = true
            )
        }

        // Prescription / medicine detection
        val rxWords = listOf("prescription", "doctor ne likha", "parchee", "parchi", "dawai ki parchee",
            "upload prescription", "prescription upload", "scan prescription", "medicine list")
        if (rxWords.any { lower.contains(it) }) {
            return ServiceIntent(
                sector         = ServiceSector.MEDICINE,
                query          = transcript,
                isPrescription = true
            )
        }

        // Match sector by keywords
        var bestSector: ServiceSector? = null
        var bestScore = 0
        for (sector in ServiceSector.values()) {
            val score = sector.voiceKeywords.count { keyword -> lower.contains(keyword) }
            if (score > bestScore) { bestScore = score; bestSector = sector }
        }

        // Extract budget if mentioned
        val budgetMatch = Regex("(\\d{2,5})\\s*(rupees|rs|rupaye|rupe|₹)?").find(lower)
        val budget = budgetMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        return ServiceIntent(
            sector = bestSector,
            query  = transcript,
            budget = budget
        )
    }

    // ── 2. Search service providers near user ─────────────────────────────────
    suspend fun searchProviders(
        intent: ServiceIntent,
        userLat: Double?,
        userLng: Double?,
        filter: ServiceFilter = ServiceFilter()
    ): List<ServiceProvider> = withContext(Dispatchers.IO) {
        try {
            // In production this hits Supabase RPC for nearby providers
            // For now return smart mock data based on sector + location
            generateMockProviders(intent, userLat ?: 22.7196, userLng ?: 75.8577, filter)
        } catch (e: Exception) {
            Log.e(TAG, "searchProviders failed: ${e.message}")
            emptyList()
        }
    }

    // ── 3. RAG: Extract medicine names from prescription image using GPT-4V ────
    suspend fun extractMedicinesFromPrescription(
        base64Image: String,
        openAiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", "gpt-4o")
                put("max_tokens", 500)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", """You are a medical prescription reader. Extract ALL medicine names from this prescription image.
Return ONLY a JSON array of medicine names, nothing else. Example: ["Paracetamol 500mg", "Azithromycin 250mg"]
If you cannot read the prescription clearly, return an empty array [].""")
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                })
                            })
                        })
                    })
                })
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $openAiKey")
                .addHeader("Content-Type", "application/json")
                .post(body).build()

            val response = http.newCall(request).execute()
            val resBody  = response.body?.string() ?: return@withContext emptyList()
            val content  = JSONObject(resBody)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()

            // Parse the JSON array of medicine names
            val cleaned = content.replace("```json", "").replace("```", "").trim()
            val arr = JSONArray(cleaned)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "extractMedicines failed: ${e.message}")
            emptyList()
        }
    }

    // ── 4. Find nearby medical shops that can fulfil the prescription ──────────
    suspend fun findMedicalShopsForPrescription(
        medicines: List<String>,
        userLat: Double,
        userLng: Double
    ): List<ServiceProvider> = withContext(Dispatchers.IO) {
        // In production: query Supabase for medical shops with these medicines in stock
        // Returns shops sorted by distance + availability
        medicines.indices.map { i ->
            ServiceProvider(
                id           = "shop_${i + 1}",
                name         = listOf("Apollo Pharmacy", "MedPlus", "Jan Aushadhi Store", "Wellness Forever", "Guardian Pharmacy")[i % 5],
                sector       = ServiceSector.MEDICINE,
                rating       = 4.2 + (i * 0.1),
                reviewCount  = 340 + i * 50,
                priceMin     = 0,
                priceMax     = 0,
                priceUnit    = "as per prescription",
                distanceKm   = 0.5 + i * 0.3,
                isAvailable  = true,
                eta          = "${15 + i * 5} min",
                tags         = listOf("verified", "24/7", "home delivery"),
                location     = "Nearby",
                experience   = "Trusted pharmacy",
                description  = "Has ${medicines.take(3).joinToString(", ")} in stock"
            )
        }.take(3)
    }

    // ── 5. Build voice response for service search results ────────────────────
    fun buildServiceVoiceResponse(
        sector: ServiceSector,
        providers: List<ServiceProvider>,
        userName: String = ""
    ): String {
        if (providers.isEmpty()) return "I couldn't find any ${sector.displayName} providers near you right now. Would you like me to try a wider area?"

        val top = providers.first()
        val count = providers.size
        val greeting = if (userName.isNotBlank()) "$userName, " else ""

        return when (sector) {
            ServiceSector.AMBULANCE ->
                "EMERGENCY! Calling ambulance now. Nearest is ${top.name}, ${top.distanceKm}km away, arriving in ${top.eta}. Stay calm."

            ServiceSector.MEDICINE ->
                "${greeting}I found $count pharmacy options. The closest is ${top.name}, ${top.distanceKm}km away, rated ${top.rating} stars. They can deliver in ${top.eta}. Say 1 to choose them, or 2 and 3 for other options."

            ServiceSector.DOCTOR ->
                "${greeting}I found $count doctors available. ${top.name} is ${top.distanceKm}km away with ${top.reviewCount} reviews and rated ${top.rating} stars. Consultation starts at ${top.priceMin} rupees. Say 1 to book."

            ServiceSector.PLUMBER, ServiceSector.ELECTRICIAN, ServiceSector.CARPENTER ->
                "${greeting}Found $count ${sector.displayName} professionals. ${top.name} is ${top.distanceKm}km away, rated ${top.rating} stars, charges ${top.priceMin} to ${top.priceMax} rupees ${top.priceUnit}. Say 1 to book."

            ServiceSector.FOOD ->
                "${greeting}Found $count restaurants delivering near you. Say the name or number to order."

            else ->
                "${greeting}I found $count ${sector.displayName} providers. The top rated is ${top.name}, ${top.distanceKm}km away, rated ${top.rating} stars. Say 1 to book, or ask me to filter by price or rating."
        }
    }

    // ── 6. Build prescription voice prompt ────────────────────────────────────
    fun buildPrescriptionPrompt(): String =
        "To order medicines from your prescription, please show your prescription to the camera or upload a photo. I'll read it automatically and find the best pharmacy near you."

    fun buildPrescriptionFoundPrompt(medicines: List<String>, shopCount: Int): String {
        val medList = when {
            medicines.isEmpty() -> "the medicines"
            medicines.size == 1 -> medicines[0]
            medicines.size <= 3 -> medicines.dropLast(1).joinToString(", ") + " and " + medicines.last()
            else -> "${medicines.take(2).joinToString(", ")} and ${medicines.size - 2} more medicines"
        }
        return "I've read your prescription and found $medList. I found $shopCount pharmacies nearby that can deliver them. Say 1, 2, or 3 to choose your pharmacy."
    }

    // ── 7. Smart mock providers generator (replace with Supabase in production) ─
    private fun generateMockProviders(
        intent: ServiceIntent,
        lat: Double,
        lng: Double,
        filter: ServiceFilter
    ): List<ServiceProvider> {
        val sector = intent.sector ?: return emptyList()

        // Provider templates per sector
        val templates: List<Triple<String, Double, String>> = when (sector) {
            ServiceSector.GROCERY -> listOf(
                Triple("Big Basket Express", 4.5, "0.3"),
                Triple("Blinkit", 4.3, "0.5"),
                Triple("Zepto", 4.4, "0.8"),
                Triple("D-Mart Ready", 4.2, "1.2"),
                Triple("Local Kirana Store", 4.1, "0.2")
            )
            ServiceSector.MEDICINE -> listOf(
                Triple("Apollo Pharmacy", 4.6, "0.4"),
                Triple("MedPlus", 4.4, "0.6"),
                Triple("1mg Delivery", 4.5, "0.1"),
                Triple("PharmEasy", 4.3, "0.1"),
                Triple("Jan Aushadhi", 4.2, "1.1")
            )
            ServiceSector.DOCTOR -> listOf(
                Triple("Dr. Rajesh Sharma (MBBS, MD)", 4.8, "1.2"),
                Triple("Dr. Priya Patel (Specialist)", 4.7, "2.1"),
                Triple("Practo Teleconsult", 4.5, "0.1"),
                Triple("Apollo Clinic", 4.6, "1.8"),
                Triple("Aarogya Health Center", 4.3, "0.9")
            )
            ServiceSector.PLUMBER -> listOf(
                Triple("Ramesh Plumbing Works", 4.4, "1.1"),
                Triple("Quick Fix Plumbers", 4.3, "0.8"),
                Triple("Urban Company Pro", 4.6, "1.5"),
                Triple("HomeTriangle Plumber", 4.2, "2.0"),
                Triple("Local Plumber — Suresh", 4.1, "0.5")
            )
            ServiceSector.ELECTRICIAN -> listOf(
                Triple("Vijay Electricals", 4.5, "0.7"),
                Triple("Urban Company Electrician", 4.6, "1.4"),
                Triple("PowerFix Services", 4.3, "1.9"),
                Triple("Local — Raju Electrician", 4.2, "0.4"),
                Triple("HomeHelp Electrics", 4.4, "2.2")
            )
            ServiceSector.CLEANING -> listOf(
                Triple("UrbanClap Cleaning", 4.5, "1.2"),
                Triple("ZAP Cleaning Services", 4.3, "0.9"),
                Triple("House Joy", 4.4, "1.7"),
                Triple("Local Bai — Sunita", 4.1, "0.3"),
                Triple("Helpr Home Services", 4.2, "2.0")
            )
            ServiceSector.TAXI -> listOf(
                Triple("Ola Mini", 4.3, "0.2"),
                Triple("Uber Go", 4.4, "0.3"),
                Triple("Rapido Auto", 4.2, "0.1"),
                Triple("InDrive", 4.1, "0.4"),
                Triple("Meru Cabs", 4.0, "1.0")
            )
            ServiceSector.FOOD -> listOf(
                Triple("Swiggy Express", 4.4, "0.5"),
                Triple("Zomato Gold", 4.5, "0.8"),
                Triple("Local Tiffin Service", 4.3, "0.3"),
                Triple("Biryani By Kilo", 4.6, "1.2"),
                Triple("McDonald's Delivery", 4.2, "1.5")
            )
            ServiceSector.AMBULANCE -> listOf(
                Triple("108 Emergency Services", 5.0, "0.0"),
                Triple("Ziqitza Ambulance", 4.8, "1.2"),
                Triple("CATS Ambulance", 4.7, "2.0"),
                Triple("Apollo Ambulance", 4.9, "1.8"),
                Triple("StanPlus", 4.6, "0.8")
            )
            ServiceSector.SALON -> listOf(
                Triple("Jawed Habib", 4.5, "1.1"),
                Triple("Green Trends", 4.4, "0.9"),
                Triple("VLCC", 4.3, "1.5"),
                Triple("YLG Salon", 4.6, "2.0"),
                Triple("Local Beauty Parlour", 4.2, "0.4")
            )
            ServiceSector.REAL_ESTATE -> listOf(
                Triple("NoBroker", 4.4, "0.1"),
                Triple("99acres Agent", 4.3, "1.2"),
                Triple("MagicBricks", 4.2, "0.1"),
                Triple("Local Property Agent", 4.5, "0.8"),
                Triple("Housing.com Pro", 4.3, "0.1")
            )
            ServiceSector.CA_SERVICES -> listOf(
                Triple("CA Amit Jain", 4.7, "2.1"),
                Triple("TaxBuddy Online", 4.5, "0.1"),
                Triple("ClearTax Expert", 4.6, "0.1"),
                Triple("Local CA Firm", 4.4, "1.5"),
                Triple("Vakilsearch", 4.3, "0.1")
            )
            ServiceSector.AGRICULTURE -> listOf(
                Triple("Kisan Seva Center", 4.4, "2.5"),
                Triple("AgroStar Delivery", 4.5, "0.1"),
                Triple("BigHaat Farm Store", 4.3, "0.1"),
                Triple("Local Krishi Kendra", 4.2, "3.1"),
                Triple("Tractor Junction", 4.1, "5.0")
            )
            else -> listOf(
                Triple("Top Rated Provider", 4.5, "1.0"),
                Triple("Verified Professional", 4.3, "1.5"),
                Triple("Budget Option", 4.1, "2.0"),
                Triple("Premium Service", 4.7, "2.5"),
                Triple("Nearby Provider", 4.2, "0.8")
            )
        }

        val priceRanges = mapOf(
            ServiceSector.GROCERY    to Triple(0, 0, "free delivery"),
            ServiceSector.MEDICINE   to Triple(0, 0, "as per MRP"),
            ServiceSector.DOCTOR     to Triple(200, 800, "per consultation"),
            ServiceSector.PLUMBER    to Triple(200, 500, "per hour"),
            ServiceSector.ELECTRICIAN to Triple(200, 600, "per hour"),
            ServiceSector.CARPENTER  to Triple(300, 800, "per hour"),
            ServiceSector.CLEANING   to Triple(500, 1500, "per session"),
            ServiceSector.TAXI       to Triple(50, 500, "per trip"),
            ServiceSector.FOOD       to Triple(0, 0, "as per menu"),
            ServiceSector.SALON      to Triple(100, 1000, "per service"),
            ServiceSector.PAINTER    to Triple(10, 25, "per sq ft"),
            ServiceSector.AC_REPAIR  to Triple(300, 1500, "per service"),
            ServiceSector.TUTOR      to Triple(500, 2000, "per month"),
            ServiceSector.LEGAL      to Triple(1000, 5000, "per consultation"),
            ServiceSector.CA_SERVICES to Triple(500, 5000, "per filing"),
            ServiceSector.AGRICULTURE to Triple(100, 5000, "varies"),
        )

        val (min, max, unit) = priceRanges[sector] ?: Triple(100, 1000, "varies")
        val etaList = listOf("10 min", "15 min", "20 min", "30 min", "45 min")
        val tagsList = listOf(
            listOf("verified", "background checked"),
            listOf("trained", "certified"),
            listOf("top rated", "200+ jobs"),
            listOf("verified", "GST registered"),
            listOf("5 star", "fast response")
        )

        return templates.mapIndexed { i, (name, rating, distStr) ->
            ServiceProvider(
                id          = "${sector.name.lowercase()}_${i + 1}",
                name        = name,
                sector      = sector,
                rating      = rating,
                reviewCount = 100 + i * 75,
                priceMin    = min,
                priceMax    = max,
                priceUnit   = unit,
                distanceKm  = distStr.toDouble(),
                isAvailable = true,
                eta         = etaList[i % etaList.size],
                tags        = tagsList[i % tagsList.size],
                location    = "Near you",
                experience  = "${2 + i} years",
                description = "Trusted ${sector.displayName} in your area"
            )
        }.filter {
            it.distanceKm <= filter.maxDistanceKm &&
                    it.rating >= filter.minRating &&
                    (!filter.availableOnly || it.isAvailable)
        }.sortedWith(
            when (filter.sortBy) {
                ServiceSort.RATING    -> compareByDescending { it.rating }
                ServiceSort.PRICE_LOW -> compareBy { it.priceMin }
                ServiceSort.DISTANCE  -> compareBy { it.distanceKm }
                ServiceSort.FASTEST   -> compareBy { it.eta }
                else                  -> compareByDescending { it.rating }
            }
        )
    }
}