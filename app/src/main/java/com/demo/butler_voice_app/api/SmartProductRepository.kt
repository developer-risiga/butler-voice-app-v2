package com.demo.butler_voice_app.api

import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ProductRecommendation(
    val productId: Int,
    val productName: String,
    val imageUrl: String?,
    val unit: String,
    val priceRs: Double,
    val stockQty: Int,
    val storeId: String,
    val storeName: String,
    val distanceKm: Double
) {
    // Badge label for first card
    val isBestValue: Boolean get() = false // set externally after sorting

    val priceLabel: String get() = "₹${priceRs.toInt()}"

    val distanceLabel: String get() =
        if (distanceKm < 1.0) "${(distanceKm * 1000).toInt()} m away"
        else String.format("%.1f km away", distanceKm)

    // Voice shortcut: first word of product name
    val voiceShortcut: String get() = productName.split(" ").firstOrNull() ?: productName
}

class SmartProductRepository(private val supabaseClient: SupabaseClient) {

    companion object {
        private const val TAG = "SmartProductRepo"
        private const val TOP_N = 3
    }

    /**
     * Calls search_products_near() RPC and returns top 3 recommendations
     * sorted by distance then price.
     */
    suspend fun getTopRecommendations(
        keyword: String,
        userLocation: Location?
    ): List<ProductRecommendation> = withContext(Dispatchers.IO) {

        // Default to Indore city center if no GPS fix yet
        val lat = userLocation?.latitude ?: 22.7196
        val lng = userLocation?.longitude ?: 75.8577

        try {
            val body = JSONObject().apply {
                put("keyword", keyword)
                put("user_lat", lat)
                put("user_lng", lng)
                put("result_limit", TOP_N)
            }

            val response = supabaseClient.rpc("search_products_near", body)
            val rows = JSONArray(response)
            val results = mutableListOf<ProductRecommendation>()

            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                results.add(
                    ProductRecommendation(
                        productId   = row.getInt("product_id"),
                        productName = row.getString("product_name"),
                        imageUrl    = row.optString("image_url").ifEmpty { null },
                        unit        = row.optString("unit", "1 unit"),
                        priceRs     = row.getDouble("price_rs"),
                        stockQty    = row.getInt("stock_qty"),
                        storeId     = row.getString("store_id"),
                        storeName   = row.getString("store_name"),
                        distanceKm  = row.getDouble("distance_km")
                    )
                )
            }

            Log.d(TAG, "Got ${results.size} recommendations for '$keyword'")
            results

        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$keyword': ${e.message}")
            emptyList()
        }
    }
}