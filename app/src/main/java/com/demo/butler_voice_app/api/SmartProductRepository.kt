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

    // ── CATEGORY MAP — matches ApiClient.queryCategoryMap ─────────────────
    // keyword → expected category_id from your Supabase categories table
    private val queryCategoryMap: Map<String, Int> = mapOf(
        "rice" to 2, "chawal" to 2, "चावल" to 2, "basmati" to 2, "sona masoori" to 2,
        "atta" to 1, "flour" to 1, "wheat" to 1, "maida" to 1, "आटा" to 1,
        "dal" to 3, "lentil" to 3, "moong" to 3, "masoor" to 3,
        "chana" to 3, "toor" to 3, "दाल" to 3,
        "ghee" to 4, "घी" to 4,
        "oil" to 5, "तेल" to 5, "sunflower" to 5,
        "salt" to 6, "नमक" to 6,
        "sugar" to 7, "jaggery" to 7, "चीनी" to 7, "गुड़" to 7,
        "tea" to 8, "coffee" to 8, "चाय" to 8,
        "biscuit" to 9, "cookie" to 9,
        "milk" to 12, "curd" to 12, "paneer" to 12, "butter" to 12,
        "दूध" to 12, "दही" to 12,
        "masala" to 16, "spice" to 16, "turmeric" to 16, "cumin" to 16,
        "soap" to 17, "shampoo" to 18,
        "egg" to 25, "eggs" to 25,
        "bread" to 22
    )

    private fun getExpectedCategory(keyword: String): Int? {
        val lower = keyword.lowercase().trim()
        return queryCategoryMap[lower]
            ?: queryCategoryMap.entries.firstOrNull { (k, _) ->
                lower.contains(k) || k.contains(lower)
            }?.value
    }

    // ── CATEGORY POST-FILTER ───────────────────────────────────────────────
    // The Supabase RPC does text search and may return cross-category hits
    // (e.g. searching "rice" returns "Rice Bran Oil" from Oils category).
    //
    // FIX: After getting RPC results, filter out products that don't belong
    // to the expected category based on their name patterns.
    //
    // This is safe — if filtering removes everything, we return the original
    // unfiltered list as fallback so Butler never shows an empty result.
    private fun postFilterByCategory(
        keyword: String,
        results: List<ProductRecommendation>
    ): List<ProductRecommendation> {
        if (results.size <= 1) return results  // nothing to filter

        val lower = keyword.lowercase().trim()
        val expectedCat = getExpectedCategory(lower) ?: return results

        val filtered = results.filter { rec ->
            isProductInCategory(rec.productName, expectedCat, lower)
        }

        Log.d(TAG, "Category filter: ${results.size} → ${filtered.size} for '$keyword' (cat=$expectedCat)")
        return if (filtered.isNotEmpty()) filtered else results  // fallback to unfiltered
    }

    private fun isProductInCategory(productName: String, categoryId: Int, keyword: String): Boolean {
        val name = productName.lowercase()
        return when (categoryId) {
            2 -> // Rice — exclude rice bran oil, rice water, rice starch, rice flour
                name.contains("rice") &&
                        !name.contains("bran oil") && !name.contains("rice water") &&
                        !name.contains("rice starch") && !name.contains("face") &&
                        !name.contains("wash") && !name.contains("cream") &&
                        !name.contains("lotion") && !name.contains("shampoo")
            1 -> // Atta/Flour — exclude face wash, body wash with "wheat"
                (name.contains("atta") || name.contains("flour") ||
                        (name.contains("wheat") && !name.contains("wheat grass") &&
                                !name.contains("wheat germ")))
            3 -> // Dal/Pulses — exclude dalda (ghee brand)
                (name.contains("dal") || name.contains("moong") || name.contains("masoor") ||
                        name.contains("chana") || name.contains("toor") || name.contains("urad")) &&
                        !name.contains("dalda")
            5 -> // Oils — must contain "oil"
                name.contains("oil")
            4 -> // Ghee
                name.contains("ghee")
            6 -> // Salt
                name.contains("salt") || name.contains("namak")
            7 -> // Sugar/Jaggery
                name.contains("sugar") || name.contains("jaggery") || name.contains("shakkar")
            12 -> // Dairy — exclude milkmaid from "milk" search
                (name.contains("milk") || name.contains("curd") || name.contains("paneer") ||
                        name.contains("butter") || name.contains("ghee")) &&
                        (keyword != "milk" || !name.contains("milkmaid"))
            17 -> // Soap
                name.contains("soap") || name.contains("bar")
            18 -> // Shampoo
                name.contains("shampoo") || name.contains("conditioner")
            else -> true  // Unknown category — don't filter
        }
    }

    suspend fun getTopRecommendations(
        keyword: String,
        userLocation: Location?
    ): List<ProductRecommendation> = withContext(Dispatchers.IO) {

        val lat = userLocation?.latitude  ?: 22.7196
        val lng = userLocation?.longitude ?: 75.8577

        try {
            val body = JSONObject().apply {
                put("keyword", keyword)
                put("user_lat", lat)
                put("user_lng", lng)
                put("result_limit", TOP_N)
                // Pass category_id if known — Supabase function will use it if it supports
                // the parameter, and safely ignore it if not.
                getExpectedCategory(keyword)?.let { put("p_category_id", it) }
            }

            val response = supabaseClient.rpc("search_products_near", body)
            val rows     = JSONArray(response)
            val results  = mutableListOf<ProductRecommendation>()

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

            // Apply category filter to remove cross-category contamination
            postFilterByCategory(keyword, results)

        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$keyword': ${e.message}")
            emptyList()
        }
    }

    // ── PRICE COMPARISON ──────────────────────────────────────────────────

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