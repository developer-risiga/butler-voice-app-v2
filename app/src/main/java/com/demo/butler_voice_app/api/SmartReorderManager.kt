package com.demo.butler_voice_app.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ReorderSuggestion(
    val productName: String,
    val avgQty: Int,
    val daysSinceLastOrder: Long
)

object SmartReorderManager {

    private val http = OkHttpClient()

    /**
     * Fetches top reorder suggestions for a user from the
     * user_reorder_suggestions view in Supabase.
     */
    suspend fun getSuggestions(userId: String): List<ReorderSuggestion> {
        return withContext(Dispatchers.IO) {
            try {
                val url   = SupabaseClient.SUPABASE_URL
                val key   = SupabaseClient.SUPABASE_KEY
                val token = UserSessionManager.getToken() ?: return@withContext emptyList()

                val req = Request.Builder()
                    .url("$url/rest/v1/user_reorder_suggestions?user_id=eq.$userId&order=order_count.desc&limit=5")
                    .addHeader("apikey", key)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .get().build()

                val res  = http.newCall(req).execute()
                val body = res.body?.string() ?: "[]"

                if (!res.isSuccessful) {
                    Log.w("SmartReorder", "Failed ${res.code}: $body")
                    return@withContext emptyList()
                }

                val arr = org.json.JSONArray(body)
                (0 until arr.length()).map { i ->
                    val obj         = arr.getJSONObject(i)
                    val lastOrdered = obj.optString("last_ordered_at", "")
                    val daysSince   = try {
                        val sdf  = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                        val date = sdf.parse(lastOrdered.take(19))
                        val diff = System.currentTimeMillis() - (date?.time ?: 0L)
                        diff / (1000L * 60 * 60 * 24)
                    } catch (e: Exception) { 7L }

                    ReorderSuggestion(
                        productName        = obj.optString("product_name", ""),
                        avgQty             = obj.optInt("avg_quantity", 1).coerceAtLeast(1),
                        daysSinceLastOrder = daysSince
                    )
                }.filter { it.productName.isNotBlank() }

            } catch (e: Exception) {
                Log.e("SmartReorder", "Error: ${e.message}")
                emptyList()
            }
        }
    }


    fun buildReorderGreeting(suggestions: List<ReorderSuggestion>, name: String): String? {
        if (suggestions.isEmpty()) return null

        val top   = suggestions.take(3)
        val items = top.joinToString(", ") {
            "${it.avgQty} ${it.productName.lowercase().split(" ").take(2).joinToString(" ")}"
        }
        val dayText = when {
            top.first().daysSinceLastOrder <= 1  -> "yesterday"
            top.first().daysSinceLastOrder <= 7  -> "${top.first().daysSinceLastOrder} days ago"
            top.first().daysSinceLastOrder <= 14 -> "last week"
            else -> "recently"
        }

        return when (top.size) {
            1    -> "Welcome back $name! You ordered ${top.first().productName.split(" ").take(2).joinToString(" ")} $dayText. Want me to reorder it? Just say yes!"
            2    -> "Welcome back $name! Want me to reorder your usual: $items? Just say yes!"
            else -> "Hey $name! Want me to reorder your usual groceries: $items? Say yes and I'll add them right away!"
        }
    }
}