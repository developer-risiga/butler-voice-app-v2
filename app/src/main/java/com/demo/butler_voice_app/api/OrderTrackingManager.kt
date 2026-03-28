package com.demo.butler_voice_app.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════════════════════════════════════
// DATA MODELS
// ══════════════════════════════════════════════════════════════════════════════

enum class TrackingType { ORDER, BOOKING, UNKNOWN }

data class TrackingResult(
    val type:         TrackingType,
    val id:           String,
    val publicId:     String,
    val status:       String,
    val eta:          String?,
    val providerName: String?,
    val totalAmount:  Double?,
    val itemCount:    Int?,
    val createdAt:    String?,
    val found:        Boolean
)

// ══════════════════════════════════════════════════════════════════════════════
// VOICE STRINGS
// English base — TranslationManager handles hi/mr/te in speak()
// ══════════════════════════════════════════════════════════════════════════════

object TrackingVoiceStrings {

    fun buildVoiceResponse(result: TrackingResult, lang: String, firstName: String): String {
        val greeting = if (firstName.isNotBlank()) "$firstName, " else ""

        if (!result.found) {
            return when (result.status) {
                "error"     -> "${greeting}I had trouble checking your status. Please try again."
                "not_found" -> if (result.publicId.isNotBlank())
                    "${greeting}I could not find order ${result.publicId}. Please check the ID."
                else "${greeting}I could not find any recent orders or bookings."
                else        -> "${greeting}I could not find any recent orders or bookings."
            }
        }

        return when (result.type) {
            TrackingType.ORDER   -> buildOrderLine(result, greeting)
            TrackingType.BOOKING -> buildBookingLine(result, greeting)
            TrackingType.UNKNOWN -> "${greeting}I could not find any recent orders or bookings."
        }
    }

    private fun buildOrderLine(r: TrackingResult, greeting: String): String {
        val id     = r.publicId.ifBlank { r.id.takeLast(6).uppercase() }
        val eta    = r.eta ?: "30"
        val amount = r.totalAmount?.let { "${it.toInt()} rupees" } ?: ""
        return when (r.status.lowercase()) {
            "placed", "confirmed"             -> "${greeting}Order $id is confirmed and being prepared. Arriving in $eta minutes."
            "processing", "preparing"         -> "${greeting}Order $id is being prepared right now. Arriving in $eta minutes."
            "out_for_delivery", "on_the_way"  -> "${greeting}Order $id is out for delivery and will reach you in $eta minutes."
            "delivered"                       -> "${greeting}Order $id has been delivered. Total was $amount. Thank you!"
            "cancelled"                       -> "${greeting}Order $id was cancelled. Say new order if you want to reorder."
            else                              -> "${greeting}Order $id status is ${r.status}. Estimated delivery in $eta minutes."
        }
    }

    private fun buildBookingLine(r: TrackingResult, greeting: String): String {
        val id       = r.publicId.ifBlank { r.id.takeLast(6).uppercase() }
        val provider = r.providerName ?: "your service provider"
        val eta      = r.eta ?: "15"
        return when (r.status.lowercase()) {
            "confirmed", "booked"       -> "${greeting}Booking $id with $provider is confirmed. Arriving in $eta minutes."
            "on_the_way", "en_route"    -> "${greeting}$provider is on the way. Arriving in $eta minutes."
            "arrived", "in_progress"    -> "${greeting}$provider has arrived and is working. Booking $id in progress."
            "completed"                 -> "${greeting}Booking $id with $provider is completed."
            "cancelled"                 -> "${greeting}Booking $id was cancelled. Say book a service to rebook."
            else                        -> "${greeting}Booking $id with $provider. Status: ${r.status}."
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ORDER TRACKING MANAGER
// ══════════════════════════════════════════════════════════════════════════════

object OrderTrackingManager {

    private const val TAG = "OrderTracking"

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // ── Latest status (no ID — "where is my order?") ─────────────────────────

    suspend fun getLatestStatus(userId: String): TrackingResult =
        withContext(Dispatchers.IO) {
            try {
                fetchLatestOrder(userId)
                    ?: fetchLatestBooking(userId)
                    ?: notFound()
            } catch (e: Exception) {
                Log.e(TAG, "getLatestStatus error: ${e.message}")
                error()
            }
        }

    // ── Status by ID (user said "BUT-FB9815") ─────────────────────────────────

    suspend fun getStatusById(userId: String, searchId: String): TrackingResult =
        withContext(Dispatchers.IO) {
            try {
                val id = searchId.trim().uppercase()
                fetchOrderById(userId, id)
                    ?: fetchBookingById(userId, id)
                    ?: notFound(id)
            } catch (e: Exception) {
                Log.e(TAG, "getStatusById error: ${e.message}")
                error()
            }
        }

    // ── Orders ────────────────────────────────────────────────────────────────

    private fun fetchLatestOrder(userId: String): TrackingResult? {
        val token = UserSessionManager.getToken() ?: return null
        val body  = get(
            "${SupabaseClient.SUPABASE_URL}/rest/v1/orders" +
                    "?user_id=eq.$userId&order=created_at.desc&limit=1" +
                    "&select=id,public_id,order_status,total_amount,created_at,eta",
            token
        ) ?: return null
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        return parseOrder(arr.getJSONObject(0))
    }

    private fun fetchOrderById(userId: String, searchId: String): TrackingResult? {
        val token = UserSessionManager.getToken() ?: return null
        val body  = get(
            "${SupabaseClient.SUPABASE_URL}/rest/v1/orders" +
                    "?user_id=eq.$userId&public_id=eq.$searchId" +
                    "&select=id,public_id,order_status,total_amount,created_at,eta",
            token
        ) ?: return null
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        return parseOrder(arr.getJSONObject(0))
    }

    private fun parseOrder(o: JSONObject) = TrackingResult(
        type         = TrackingType.ORDER,
        id           = o.optString("id", ""),
        publicId     = o.optString("public_id", ""),
        status       = o.optString("order_status", "placed"),
        eta          = o.optString("eta").takeIf { it.isNotBlank() && it != "null" },
        providerName = null,
        totalAmount  = o.optDouble("total_amount").takeIf { !it.isNaN() && it > 0 },
        itemCount    = null,
        createdAt    = o.optString("created_at").takeIf { it.isNotBlank() },
        found        = true
    )

    // ── Bookings ──────────────────────────────────────────────────────────────

    private fun fetchLatestBooking(userId: String): TrackingResult? {
        val token = UserSessionManager.getToken() ?: return null
        // Try both table names in case yours is named differently
        val body = get(
            "${SupabaseClient.SUPABASE_URL}/rest/v1/bookings" +
                    "?user_id=eq.$userId&order=created_at.desc&limit=1" +
                    "&select=id,booking_id,status,eta_minutes,provider_name,sector,created_at",
            token
        ) ?: return null
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        return parseBooking(arr.getJSONObject(0))
    }

    private fun fetchBookingById(userId: String, searchId: String): TrackingResult? {
        val token = UserSessionManager.getToken() ?: return null
        val body  = get(
            "${SupabaseClient.SUPABASE_URL}/rest/v1/bookings" +
                    "?user_id=eq.$userId&booking_id=eq.$searchId" +
                    "&select=id,booking_id,status,eta_minutes,provider_name,sector,created_at",
            token
        ) ?: return null
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        return parseBooking(arr.getJSONObject(0))
    }

    private fun parseBooking(o: JSONObject) = TrackingResult(
        type         = TrackingType.BOOKING,
        id           = o.optString("id", ""),
        publicId     = o.optString("booking_id", ""),
        status       = o.optString("status", "confirmed"),
        eta          = o.optInt("eta_minutes", 0).takeIf { it > 0 }?.toString(),
        providerName = o.optString("provider_name").takeIf { it.isNotBlank() && it != "null" },
        totalAmount  = null,
        itemCount    = null,
        createdAt    = o.optString("created_at").takeIf { it.isNotBlank() },
        found        = true
    )

    // ── HTTP helper ───────────────────────────────────────────────────────────

    private fun get(url: String, token: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseClient.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get().build()
            val res  = http.newCall(req).execute()
            val body = res.body?.string()
            Log.d(TAG, "GET ${res.code}: ${body?.take(200)}")
            if (res.isSuccessful) body else null
        } catch (e: Exception) {
            Log.e(TAG, "HTTP error: ${e.message}")
            null
        }
    }

    // ── Sentinels ─────────────────────────────────────────────────────────────

    private fun notFound(id: String = "") = TrackingResult(
        TrackingType.UNKNOWN, id, id, "not_found", null, null, null, null, null, false
    )
    private fun error() = TrackingResult(
        TrackingType.UNKNOWN, "", "", "error", null, null, null, null, null, false
    )
}