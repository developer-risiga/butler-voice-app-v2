package com.demo.butler_voice_app.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.demo.butler_voice_app.BuildConfig
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE MANAGER — Real Supabase + GPT-4V prescription RAG
// ══════════════════════════════════════════════════════════════════════════════

object ServiceManager {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private const val TAG = "ServiceManager"

    // ── 1. Detect service intent from voice ───────────────────────────────────
    fun detectServiceIntent(transcript: String): ServiceIntent {
        val lower = transcript.lowercase().trim()

        val emergencyWords = listOf("emergency","accident","ambulance","hospital","unconscious",
            "bleeding","heart attack","stroke","fire","help","bachao","madad","108")
        if (emergencyWords.any { lower.contains(it) }) {
            return ServiceIntent(sector = ServiceSector.AMBULANCE, query = transcript, isEmergency = true)
        }

        val rxWords = listOf("prescription","parchi","parchee","dawai ki parchee",
            "doctor ne likha","upload prescription","medicine list","scan prescription")
        if (rxWords.any { lower.contains(it) }) {
            return ServiceIntent(sector = ServiceSector.MEDICINE, query = transcript, isPrescription = true)
        }

        var bestSector: ServiceSector? = null
        var bestScore  = 0
        for (sector in ServiceSector.values()) {
            val score = sector.voiceKeywords.count { keyword -> lower.contains(keyword) }
            if (score > bestScore) { bestScore = score; bestSector = sector }
        }

        val budgetMatch = Regex("(\\d{2,5})\\s*(rupees|rs|rupaye|₹)?").find(lower)
        val budget = budgetMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        return ServiceIntent(sector = bestSector, query = transcript, budget = budget)
    }

    // ── 2. Search providers — REAL Supabase RPC ───────────────────────────────
    suspend fun searchProviders(
        intent: ServiceIntent,
        userLat: Double?,
        userLng: Double?,
        filter: ServiceFilter = ServiceFilter()
    ): List<ServiceProvider> = withContext(Dispatchers.IO) {
        val lat = userLat ?: 22.7196
        val lng = userLng ?: 75.8577

        try {
            val sortBy = when (filter.sortBy) {
                ServiceSort.RATING    -> "rating"
                ServiceSort.DISTANCE  -> "distance"
                ServiceSort.PRICE_LOW -> "price_low"
                ServiceSort.FASTEST   -> "fastest"
                else                  -> "rating"
            }

            val bodyJson = JSONObject().apply {
                put("user_lat",    lat)
                put("user_lng",    lng)
                if (intent.sector != null) put("sector_name", intent.sector.name)
                put("radius_km",   filter.maxDistanceKm)
                put("sort_by",     sortBy)
                put("max_results", 5)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/get_nearby_providers")
                .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                .addHeader("Content-Type",  "application/json")
                .post(bodyJson).build()

            val response = http.newCall(request).execute()
            val resBody  = response.body?.string() ?: ""
            Log.d(TAG, "Supabase providers (${response.code}): ${resBody.take(300)}")

            if (!response.isSuccessful || resBody.isBlank() || resBody == "[]") {
                Log.w(TAG, "Supabase returned no data — using fallback")
                return@withContext fallbackProviders(intent, lat, lng, filter)
            }

            val arr = JSONArray(resBody)
            if (arr.length() == 0) return@withContext fallbackProviders(intent, lat, lng, filter)

            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ServiceProvider(
                    id          = obj.optString("id"),
                    name        = obj.optString("name"),
                    sector      = intent.sector ?: ServiceSector.GROCERY,
                    rating      = obj.optDouble("rating", 4.0),
                    reviewCount = obj.optInt("review_count", 0),
                    priceMin    = obj.optInt("price_min", 0),
                    priceMax    = obj.optInt("price_max", 0),
                    priceUnit   = obj.optString("price_unit", "per visit"),
                    distanceKm  = obj.optDouble("distance_km", 1.0),
                    isAvailable = obj.optBoolean("is_available", true),
                    eta         = "${obj.optInt("eta_minutes", 30)} min",
                    tags        = parseStringArray(obj.optJSONArray("tags")),
                    location    = obj.optString("location_name", "Nearby"),
                    phone       = obj.optString("phone", ""),
                    experience  = obj.optString("experience", ""),
                    languages   = parseStringArray(obj.optJSONArray("languages")),
                    description = obj.optString("description", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchProviders failed: ${e.message} — using fallback")
            fallbackProviders(intent, lat, lng, filter)
        }
    }

    private fun parseStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    // ── 3. Create booking in Supabase ─────────────────────────────────────────
    suspend fun createBooking(
        userId: String,
        provider: ServiceProvider,
        sector: ServiceSector,
        notes: String = ""
    ): String = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("p_user_id",     userId)
                put("p_provider_id", provider.id)
                put("p_sector",      sector.name)
                put("p_notes",       notes)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/create_service_booking")
                .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                .addHeader("Content-Type",  "application/json")
                .post(bodyJson).build()

            val response = http.newCall(request).execute()
            val resBody  = response.body?.string() ?: ""
            Log.d(TAG, "Booking response (${response.code}): $resBody")

            if (response.isSuccessful && resBody.isNotBlank()) {
                JSONObject(resBody).optString("booking_id", generateLocalBookingId())
            } else {
                generateLocalBookingId()
            }
        } catch (e: Exception) {
            Log.e(TAG, "createBooking failed: ${e.message}")
            generateLocalBookingId()
        }
    }

    private fun generateLocalBookingId() =
        "BUT-" + java.util.UUID.randomUUID().toString().takeLast(6).uppercase()

    // ── 4. GPT-4 Vision prescription RAG ─────────────────────────────────────
    suspend fun extractMedicinesFromPrescription(
        base64Image: String,
        openAiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("model", "gpt-4o")
                put("max_tokens", 500)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "You are a medical prescription reader for Indian pharmacies. Extract ALL medicine names with dosage from this image. Return ONLY a JSON array like: [\"Paracetamol 500mg\", \"Azithromycin 250mg\"]. If unreadable, return [].")
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Image"))
                        })
                    })
                }))
            }.toString().toRequestBody("application/json".toMediaType())

            val response = http.newCall(
                Request.Builder().url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $openAiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson).build()
            ).execute()

            val content = JSONObject(response.body?.string() ?: return@withContext emptyList())
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
                .replace("```json", "").replace("```", "").trim()

            val arr = JSONArray(content)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "extractMedicines failed: ${e.message}")
            emptyList()
        }
    }

    // ── 5. Find nearby pharmacies ─────────────────────────────────────────────
    suspend fun findMedicalShopsForPrescription(
        medicines: List<String>,
        userLat: Double,
        userLng: Double
    ): List<ServiceProvider> = searchProviders(
        ServiceIntent(ServiceSector.MEDICINE, medicines.joinToString(", ")),
        userLat, userLng,
        ServiceFilter(maxDistanceKm = 5.0, sortBy = ServiceSort.DISTANCE)
    )

    // ── 6. Voice response builder ─────────────────────────────────────────────
    fun buildServiceVoiceResponse(
        sector: ServiceSector,
        providers: List<ServiceProvider>,
        userName: String = ""
    ): String {
        if (providers.isEmpty())
            return "I couldn't find any ${sector.displayName} providers near you. Say wider area to search more."
        val top     = providers.first()
        val count   = providers.size
        val greeting = if (userName.isNotBlank()) "$userName, " else ""
        val price   = if (top.priceMin > 0) ", charges ${top.priceMin} to ${top.priceMax} ${top.priceUnit}" else ""
        return when (sector) {
            ServiceSector.AMBULANCE ->
                "EMERGENCY! Nearest is ${top.name}, ${top.distanceKm}km away, arriving in ${top.eta}. Calling now!"
            ServiceSector.MEDICINE ->
                "${greeting}Found $count pharmacies. ${top.name} is ${top.distanceKm}km away, delivering in ${top.eta}. Say 1, 2, or 3."
            else ->
                "${greeting}Found $count ${sector.displayName} professionals. ${top.name} is ${top.distanceKm}km away, rated ${top.rating} stars${price}. Say 1 to book."
        }
    }

    // ── 7. Fallback mock (when Supabase unreachable) ──────────────────────────
    private fun fallbackProviders(
        intent: ServiceIntent,
        lat: Double,
        lng: Double,
        filter: ServiceFilter
    ): List<ServiceProvider> {
        val sector = intent.sector ?: return emptyList()
        val templates = mapOf(
            ServiceSector.PLUMBER     to listOf("Ramesh Plumbing" to 4.5, "Urban Company Pro" to 4.7, "Quick Fix" to 4.3, "Local Plumber" to 4.1, "HomeTriangle" to 4.4),
            ServiceSector.ELECTRICIAN to listOf("Vijay Electricals" to 4.6, "Urban Electrician" to 4.8, "PowerFix" to 4.3, "Local Electrician" to 4.2, "HomeHelp" to 4.5),
            ServiceSector.DOCTOR      to listOf("Dr. Sharma MBBS" to 4.8, "Dr. Patel Specialist" to 4.9, "Practo Online" to 4.5, "Apollo Clinic" to 4.7, "City Doctor" to 4.6),
            ServiceSector.MEDICINE    to listOf("Apollo Pharmacy" to 4.7, "MedPlus" to 4.5, "Jan Aushadhi" to 4.3, "PharmEasy" to 4.6, "Wellness Forever" to 4.4),
            ServiceSector.AMBULANCE   to listOf("108 Emergency" to 5.0, "Ziqitza" to 4.8, "StanPlus" to 4.7, "Apollo Ambulance" to 4.9),
            ServiceSector.TAXI        to listOf("Ola Mini" to 4.3, "Uber Go" to 4.4, "Rapido" to 4.2, "InDrive" to 4.1),
            ServiceSector.FOOD        to listOf("Swiggy Express" to 4.5, "Zomato Gold" to 4.6, "Local Tiffin" to 4.7),
            ServiceSector.CLEANING    to listOf("Urban Cleaning" to 4.6, "Local Maid" to 4.3, "ZAP Cleaning" to 4.4),
            ServiceSector.SALON       to listOf("Jawed Habib" to 4.5, "Green Trends" to 4.4, "Home Salon" to 4.6),
            ServiceSector.AC_REPAIR   to listOf("CoolCare AC" to 4.5, "Urban AC" to 4.7, "FrostFix" to 4.3),
        )
        val priceRanges = mapOf(
            ServiceSector.PLUMBER to Triple(200, 500, "per hour"), ServiceSector.ELECTRICIAN to Triple(200, 600, "per hour"),
            ServiceSector.DOCTOR  to Triple(300, 800, "per consultation"), ServiceSector.TAXI to Triple(50, 500, "per trip"),
            ServiceSector.CLEANING to Triple(500, 1500, "per session"), ServiceSector.SALON to Triple(100, 1000, "per service"),
            ServiceSector.AC_REPAIR to Triple(300, 1500, "per service"),
        )
        val names = templates[sector] ?: listOf("Top Provider" to 4.5, "Verified Pro" to 4.3, "Local Expert" to 4.1)
        val (pMin, pMax, pUnit) = priceRanges[sector] ?: Triple(0, 0, "varies")
        return names.mapIndexed { i, (name, rating) ->
            ServiceProvider(
                id = "${sector.name}_fb_$i", name = name, sector = sector,
                rating = rating, reviewCount = 100 + i * 80,
                priceMin = pMin, priceMax = pMax, priceUnit = pUnit,
                distanceKm = 0.4 + i * 0.5, isAvailable = true,
                eta = "${10 + i * 5} min", tags = listOf("verified"),
                location = "Near you", experience = "${3 + i} years"
            )
        }.sortedWith(when (filter.sortBy) {
            ServiceSort.RATING    -> compareByDescending { it.rating }
            ServiceSort.DISTANCE  -> compareBy { it.distanceKm }
            ServiceSort.PRICE_LOW -> compareBy { it.priceMin }
            ServiceSort.FASTEST   -> compareBy { it.eta }
            else                  -> compareByDescending { it.rating }
        })
    }
}