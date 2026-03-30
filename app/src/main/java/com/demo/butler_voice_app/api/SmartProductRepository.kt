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
    val priceLabel: String get() = "₹${priceRs.toInt()}"

    val distanceLabel: String get() =
        if (distanceKm < 1.0) "${(distanceKm * 1000).toInt()} m away"
        else String.format(java.util.Locale.getDefault(), "%.1f km away", distanceKm)

    val voiceShortcut: String get() = productName.split(" ").firstOrNull() ?: productName
}

class SmartProductRepository(private val supabaseClient: SupabaseClient) {

    companion object {
        private const val TAG = "SmartProductRepo"
        private const val TOP_N = 3
    }

    suspend fun getTopRecommendations(
        keyword: String,
        userLocation: Location?
    ): List<ProductRecommendation> = withContext(Dispatchers.IO) {

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

    // ── LIVE PRICE INTELLIGENCE ───────────────────────────────────────────────
    suspend fun getPriceComparison(
        itemName: String,
        userLocation: Location?
    ): PriceComparison? {
        return try {
            val recs = getTopRecommendations(itemName, userLocation)
            if (recs.isEmpty()) return null

            val storePrices = recs.map { rec ->
                StorePrice(
                    storeName    = rec.storeName,
                    storeId      = rec.storeId,
                    productName  = rec.productName,
                    productId    = rec.productId.toString(),
                    priceRs      = rec.priceRs,
                    unit         = rec.unit,
                    distanceKm   = rec.distanceKm,
                    deliveryMins = (rec.distanceKm * 8).toInt().coerceIn(10, 60)
                )
            }.sortedBy { it.priceRs }

            if (storePrices.isEmpty()) return null

            val cheapest      = storePrices.first()
            val mostExpensive = storePrices.last()
            val savings       = mostExpensive.priceRs - cheapest.priceRs

            PriceComparison(
                itemName  = itemName,
                cheapest  = cheapest,
                others    = storePrices.drop(1),
                savingsRs = savings,
                allPrices = storePrices
            )
        } catch (e: Exception) {
            Log.e(TAG, "getPriceComparison failed: ${e.message}")
            null
        }
    }
}