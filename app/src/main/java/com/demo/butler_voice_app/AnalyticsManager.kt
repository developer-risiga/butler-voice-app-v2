package com.demo.butler_voice_app

import android.util.Log
import com.demo.butler_voice_app.api.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


object AnalyticsManager {

    private val http  = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val url   get() = SupabaseClient.SUPABASE_URL
    private val key   get() = SupabaseClient.SUPABASE_KEY

    /** Call when wake word detected */
    fun logSessionStart(userId: String?, language: String) = log(
        "session_start", mapOf("user_id" to (userId ?: "anonymous"), "language" to language)
    )

    /** Call when order successfully placed */
    fun logOrderPlaced(orderId: String, total: Double, itemCount: Int, language: String) = log(
        "order_placed", mapOf(
            "order_id"   to orderId,
            "total"      to total,
            "item_count" to itemCount,
            "language"   to language
        )
    )

    /** Call when STT returns empty transcript */
    fun logSttEmpty(state: String) = log("stt_empty", mapOf("state" to state))

    /** Call when product search returns null */
    fun logItemNotFound(query: String) = log("item_not_found", mapOf("query" to query))

    /** Call when user drops off mid-session */
    fun logSessionAbandoned(lastState: String) = log("session_abandoned", mapOf("last_state" to lastState))

    /** Call when login/signup succeeds */
    fun logUserAuth(method: String, language: String) = log(
        "user_auth", mapOf("method" to method, "language" to language)
    )

    private fun log(event: String, props: Map<String, Any>) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("event_name",  event)
                    put("properties",  JSONObject(props))
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("$url/rest/v1/analytics_events")
                    .addHeader("apikey", key)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(body)
                    .build()

                val res = http.newCall(req).execute()
                if (res.isSuccessful) {
                    Log.d("Analytics", "✓ $event")
                } else {
                    Log.w("Analytics", "✗ $event: ${res.code}")
                }
            } catch (e: Exception) {
                Log.w("Analytics", "Failed to log $event: ${e.message}")
            }
        }
    }
}
