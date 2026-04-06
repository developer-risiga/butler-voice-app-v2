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

    val deliveryMins: Int get() = (distanceKm * 8).toInt().coerceIn(10, 60)

    val voiceShortcut: String get() = productName.split(" ").firstOrNull() ?: productName

    /** "DAAWAT BROWN BASMATI RICE" → "Daawat Brown Basmati Rice" */
    val readableName: String get() = productName.lowercase().split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

// ── ProductSearchResult ───────────────────────────────────────────────────────
//
// Wraps the result of searchWithFallback() so the caller (MainActivity) knows
// whether the products returned actually match what the user asked for.
//
// isCategoryMismatch = true means:
//   - The user asked for X (e.g. "arhar dal")
//   - RPC returned 0 results even after synonym expansion
//   - The fallback search found something, but it's a different dal variety
//   - Butler must ASK the user before adding — never silently substitute
//
// isCategoryMismatch = false means normal flow — call confirmAddProduct() as usual.
//
// requestedKeyword : what the user originally asked ("arhar")
// resolvedKeyword  : what we actually searched ("toor dal") after synonym expansion
//                    — same as requestedKeyword if no synonym was applied
// ─────────────────────────────────────────────────────────────────────────────
data class ProductSearchResult(
    val products: List<ProductRecommendation>,
    val isCategoryMismatch: Boolean,
    val requestedKeyword: String,
    val resolvedKeyword: String
) {
    fun isEmpty() = products.isEmpty()
    fun first()   = products.first()
}

class SmartProductRepository(private val supabaseClient: SupabaseClient) {

    companion object {
        private const val TAG         = "SmartProductRepo"
        private const val TOP_N       = 3
        private const val FETCH_LIMIT = 10
    }

    // ── SYNONYM MAP ───────────────────────────────────────────────────────────
    //
    // Normalises what the user said → what we search in the DB.
    //
    // WHY THIS IS SEPARATE FROM queryCategoryMap:
    //   queryCategoryMap maps search keyword → category ID for post-filtering.
    //   SYNONYM_MAP maps user utterance → canonical search term used in RPC.
    //   They work in sequence: synonym expansion first, then category filtering.
    //
    // ROOT CAUSE OF BUG 2 (arhar → moong):
    //   "arhar" was not in queryCategoryMap AND not in any synonym map.
    //   ItemNormalizer.normalize("arhar") returned "toor dal" correctly,
    //   but the RPC search for "toor dal" returned 0 results because the
    //   Supabase product names use "toor" not "toor dal".
    //   The MainActivity fallback then grabbed the first ApiClient result
    //   (moong) without checking if it was the right variety.
    //
    // ADDING SYNONYMS HERE fixes the RPC step so 0-result fallbacks are rare.
    // ─────────────────────────────────────────────────────────────────────────
    private val SYNONYM_MAP: Map<String, String> = mapOf(
        // Dal synonyms — all map to the canonical RPC search term
        "arhar"      to "toor",
        "arhar dal"  to "toor dal",
        "अरहर"       to "toor",
        "अरहर दाल"  to "toor dal",
        "तूर"        to "toor",
        "तुअर"       to "toor",
        "toor dal"   to "toor",       // "toor dal" as a phrase → search just "toor"
        "tur dal"    to "toor",
        "tur"        to "toor",
        "pigeon pea" to "toor",

        // Rice synonyms
        "chawal"      to "rice",
        "चावल"        to "rice",
        "sona masuri"  to "sona masoori",
        "sona masuri rice" to "sona masoori",
        "ponni"       to "ponni rice",

        // Atta synonyms
        "gehun"       to "wheat",
        "gehun atta"  to "wheat atta",
        "गेहूं"       to "wheat",
        "आटा"         to "atta",

        // Oil synonyms
        "sarson"      to "mustard oil",
        "sarson ka tel" to "mustard oil",
        "tel"         to "oil",
        "तेल"         to "oil",
        "nariyal tel" to "coconut oil",

        // Milk synonyms
        "doodh"       to "milk",
        "दूध"         to "milk",

        // Sugar synonyms
        "cheeni"      to "sugar",
        "चीनी"        to "sugar",
        "shakkar"     to "sugar",

        // Tea synonyms
        "chai"        to "tea",
        "चाय"         to "tea",

        // Salt synonyms
        "namak"       to "salt",
        "नमक"         to "salt",

        // Ghee synonyms
        "घी"          to "ghee",

        // Common Hinglish grocery words
        "dahi"        to "curd",
        "दही"         to "curd",
        "paneer"      to "paneer",
        "makhan"      to "butter",
        "मक्खन"       to "butter"
    )

    private fun expandSynonym(keyword: String): String {
        val lower = keyword.lowercase().trim()
        return SYNONYM_MAP[lower] ?: keyword
    }

    // ── CATEGORY MAP ──────────────────────────────────────────────────────────
    private val queryCategoryMap: Map<String, Int> = mapOf(
        "rice" to 2, "chawal" to 2, "चावल" to 2, "basmati" to 2, "sona masoori" to 2,
        "atta" to 1, "flour" to 1, "wheat" to 1, "maida" to 1, "आटा" to 1,
        "dal" to 3, "lentil" to 3, "moong" to 3, "masoor" to 3,
        "chana" to 3, "toor" to 3, "दाल" to 3,
        // Bug 2 fix — arhar/अरहर now map to category 3 (dal)
        // so even if synonym expansion doesn't fire, category filter still works
        "arhar" to 3, "अरहर" to 3, "tur" to 3, "urad" to 3, "arhar dal" to 3,
        "ghee" to 4, "घी" to 4,
        "oil" to 5, "तेल" to 5, "sunflower" to 5, "mustard oil" to 5,
        "salt" to 6, "नमक" to 6,
        "sugar" to 7, "jaggery" to 7, "चीनी" to 7, "गुड़" to 7,
        "tea" to 8, "coffee" to 8, "चाय" to 8,
        "biscuit" to 9, "cookie" to 9,
        "milk" to 12, "curd" to 12, "paneer" to 12, "butter" to 12,
        "दूध" to 12, "दही" to 12,
        "masala" to 16, "spice" to 16, "turmeric" to 16, "cumin" to 16,
        "soap" to 17, "shampoo" to 18,
        "egg" to 25, "eggs" to 25, "bread" to 22
    )

    private fun getExpectedCategory(keyword: String): Int? {
        val lower = keyword.lowercase().trim()
        return queryCategoryMap[lower]
            ?: queryCategoryMap.entries.firstOrNull { (k, _) ->
                lower.contains(k) || k.contains(lower)
            }?.value
    }

    // ── CATEGORY POST-FILTER ──────────────────────────────────────────────────
    private fun postFilterByCategory(
        keyword: String,
        results: List<ProductRecommendation>
    ): List<ProductRecommendation> {
        if (results.size <= 1) return results
        val lower       = keyword.lowercase()
        val expectedCat = getExpectedCategory(lower) ?: return results

        val filtered = results.filter { rec ->
            isProductInCategory(rec.productName, expectedCat, lower)
        }
        Log.d(TAG, "Category filter: ${results.size} → ${filtered.size} for '$keyword' (cat=$expectedCat)")
        return if (filtered.isNotEmpty()) filtered else results
    }

    private fun isProductInCategory(productName: String, categoryId: Int, keyword: String): Boolean {
        val name = productName.lowercase()
        return when (categoryId) {
            2  -> name.contains("rice") &&
                    !name.contains("bran oil") && !name.contains("rice water") &&
                    !name.contains("rice starch") && !name.contains("face") &&
                    !name.contains("wash") && !name.contains("cream") &&
                    !name.contains("lotion") && !name.contains("shampoo")
            1  -> name.contains("atta") || name.contains("flour") ||
                    (name.contains("wheat") && !name.contains("wheat grass"))
            3  -> (name.contains("dal") || name.contains("moong") || name.contains("masoor") ||
                    name.contains("chana") || name.contains("toor") || name.contains("urad") ||
                    name.contains("arhar") || name.contains("tur")) &&
                    !name.contains("dalda")
            5  -> name.contains("oil")
            4  -> name.contains("ghee")
            6  -> name.contains("salt") || name.contains("namak")
            7  -> name.contains("sugar") || name.contains("jaggery") || name.contains("shakkar")
            12 -> (name.contains("milk") || name.contains("curd") || name.contains("paneer") ||
                    name.contains("butter")) &&
                    (keyword != "milk" || !name.contains("milkmaid"))
            17 -> name.contains("soap") || name.contains("bar")
            18 -> name.contains("shampoo") || name.contains("conditioner")
            else -> true
        }
    }

    // ── MISMATCH DETECTION ────────────────────────────────────────────────────
    //
    // Determines whether a fallback result is the same variety as requested
    // or a different one. Used to decide whether to ask the user for permission.
    //
    // Example: user asked "arhar" (toor dal family), fallback returned "moong"
    // → these are both dal (category 3) but different varieties → isMismatch=true
    //
    // Dal variety groups — if requested and returned are in different groups,
    // it's a mismatch even though both are category 3.
    // ─────────────────────────────────────────────────────────────────────────
    private val DAL_VARIETY_GROUPS = listOf(
        setOf("toor", "arhar", "tur", "pigeon"),   // toor family
        setOf("moong", "mung", "green gram"),        // moong family
        setOf("masoor", "red lentil"),               // masoor family
        setOf("urad", "black gram", "black dal"),    // urad family
        setOf("chana", "chickpea", "gram")           // chana family
    )

    private fun isSameVariety(requestedKeyword: String, productName: String): Boolean {
        val req  = requestedKeyword.lowercase()
        val prod = productName.lowercase()

        // Find which group the requested keyword belongs to
        val reqGroup  = DAL_VARIETY_GROUPS.firstOrNull { group -> group.any { req.contains(it) } }
        val prodGroup = DAL_VARIETY_GROUPS.firstOrNull { group -> group.any { prod.contains(it) } }

        // If both are in a known group — they must be the SAME group
        if (reqGroup != null && prodGroup != null) return reqGroup == prodGroup

        // If only one is in a group (or neither), fall back to simple contains check
        return prod.contains(req) || req.split(" ").any { prod.contains(it) }
    }

    // ── COMPOSITE RANKING ─────────────────────────────────────────────────────
    private fun compositeScore(rec: ProductRecommendation, avgPrice: Double): Double {
        val priceScore    = if (avgPrice > 0) rec.priceRs / avgPrice else 1.0
        val distanceScore = rec.distanceKm / 2.0
        val deliveryScore = rec.deliveryMins.toDouble() / 30.0
        return priceScore * 0.50 + distanceScore * 0.30 + deliveryScore * 0.20
    }

    private fun rankAndTake(
        results: List<ProductRecommendation>,
        n: Int
    ): List<ProductRecommendation> {
        if (results.isEmpty()) return results
        val avgPrice = results.map { it.priceRs }.average()
        return results.sortedBy { compositeScore(it, avgPrice) }.take(n)
    }

    // ── RPC FETCH (internal) ──────────────────────────────────────────────────
    private suspend fun fetchFromRpc(
        keyword: String,
        lat: Double,
        lng: Double
    ): List<ProductRecommendation> {
        val body = JSONObject().apply {
            put("keyword",      keyword)
            put("user_lat",     lat)
            put("user_lng",     lng)
            put("result_limit", FETCH_LIMIT)
        }
        val response = supabaseClient.rpc("search_products_near", body)
        val rows     = JSONArray(response)
        val raw      = mutableListOf<ProductRecommendation>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            raw.add(
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
        return raw
    }

    // ── searchWithFallback ────────────────────────────────────────────────────
    //
    // THE KEY METHOD for Bug 2 fix. Call this from MainActivity instead of
    // the current two-step (getTopRecommendations + ApiClient.searchProduct).
    //
    // Flow:
    //   1. Expand synonym:  "arhar" → "toor"
    //   2. RPC search with resolved keyword
    //   3. Category post-filter
    //   4. If results found → return ProductSearchResult(isCategoryMismatch=false)
    //   5. If 0 results → try broader category search ("dal")
    //   6. If broader search finds something:
    //        - check variety: is it the same dal type or a different one?
    //        - if different variety → isCategoryMismatch=true (Butler asks user)
    //        - if same variety → isCategoryMismatch=false (normal flow)
    //   7. If still 0 → return empty ProductSearchResult
    //
    // MainActivity replaces:
    //   OLD:
    //     val recs = smartProductRepo.getTopRecommendations(itemName, location)
    //     if (recs.isEmpty()) { val fallback = apiClient.searchProduct(...) ... }
    //   NEW:
    //     val result = smartProductRepo.searchWithFallback(itemName, location)
    //     when {
    //         result.isEmpty()           -> butler.say(productNotFound(...))
    //         result.isCategoryMismatch  -> butler.say(productCategoryMismatch(...))
    //         else                       -> butler.say(confirmAddProduct(...))
    //     }
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun searchWithFallback(
        keyword: String,
        userLocation: Location?
    ): ProductSearchResult = withContext(Dispatchers.IO) {

        val lat = userLocation?.latitude  ?: 22.7196
        val lng = userLocation?.longitude ?: 75.8577

        // Step 1 — Synonym expansion
        val resolved = expandSynonym(keyword)
        if (resolved != keyword) {
            Log.d(TAG, "Synonym expanded: '$keyword' → '$resolved'")
        }

        try {
            // Step 2 — Primary RPC search with resolved keyword
            val raw = fetchFromRpc(resolved, lat, lng)
            Log.d(TAG, "RPC returned ${raw.size} candidates for '$resolved'")

            // Step 3 — Category post-filter
            val filtered = postFilterByCategory(resolved, raw)
            val ranked   = rankAndTake(filtered, TOP_N)

            if (ranked.isNotEmpty()) {
                Log.d(TAG, "Returning ${ranked.size} ranked results for '$resolved'")
                return@withContext ProductSearchResult(
                    products          = ranked,
                    isCategoryMismatch = false,
                    requestedKeyword  = keyword,
                    resolvedKeyword   = resolved
                )
            }

            // Step 4 — Primary returned 0. Try broader category fallback.
            // Example: "toor" → 0 results → try searching "dal" broadly
            val expectedCat = getExpectedCategory(resolved)
            val broaderTerm = when (expectedCat) {
                3    -> "dal"
                2    -> "rice"
                1    -> "atta"
                5    -> "oil"
                12   -> "milk"
                else -> null
            }

            if (broaderTerm != null && broaderTerm != resolved) {
                Log.d(TAG, "Primary empty — trying broader search: '$broaderTerm'")
                val broaderRaw      = fetchFromRpc(broaderTerm, lat, lng)
                val broaderFiltered = postFilterByCategory(broaderTerm, broaderRaw)
                val broaderRanked   = rankAndTake(broaderFiltered, TOP_N)

                if (broaderRanked.isNotEmpty()) {
                    // Check: is the first result the same variety as what was asked?
                    val topProduct  = broaderRanked.first()
                    val sameVariety = isSameVariety(resolved, topProduct.productName)
                    Log.d(TAG, "Broader fallback found: '${topProduct.productName}' " +
                            "sameVariety=$sameVariety for requested='$resolved'")

                    return@withContext ProductSearchResult(
                        products           = broaderRanked,
                        isCategoryMismatch = !sameVariety,  // true = ask user
                        requestedKeyword   = keyword,
                        resolvedKeyword    = broaderTerm
                    )
                }
            }

            // Step 5 — Nothing found at all
            Log.w(TAG, "No results found for '$keyword' (resolved: '$resolved')")
            ProductSearchResult(
                products           = emptyList(),
                isCategoryMismatch = false,
                requestedKeyword   = keyword,
                resolvedKeyword    = resolved
            )

        } catch (e: Exception) {
            Log.e(TAG, "searchWithFallback failed for '$keyword': ${e.message}")
            ProductSearchResult(
                products           = emptyList(),
                isCategoryMismatch = false,
                requestedKeyword   = keyword,
                resolvedKeyword    = keyword
            )
        }
    }

    // ── EXISTING METHODS — unchanged, kept for backward compatibility ─────────

    suspend fun getTopRecommendations(
        keyword: String,
        userLocation: Location?
    ): List<ProductRecommendation> = withContext(Dispatchers.IO) {

        val lat = userLocation?.latitude  ?: 22.7196
        val lng = userLocation?.longitude ?: 75.8577

        try {
            val resolved = expandSynonym(keyword)
            val raw      = fetchFromRpc(resolved, lat, lng)

            Log.d(TAG, "RPC returned ${raw.size} candidates for '$keyword'")
            val filtered = postFilterByCategory(resolved, raw)
            val ranked   = rankAndTake(filtered, TOP_N)
            Log.d(TAG, "Returning ${ranked.size} ranked results for '$keyword'")
            ranked

        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$keyword': ${e.message}")
            emptyList()
        }
    }

    fun buildComparison(
        itemName: String,
        recs: List<ProductRecommendation>
    ): PriceComparison? {
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
                deliveryMins = rec.deliveryMins
            )
        }.sortedBy { it.priceRs }

        val cheapest      = storePrices.first()
        val mostExpensive = storePrices.last()

        return PriceComparison(
            itemName  = itemName,
            cheapest  = cheapest,
            others    = storePrices.drop(1),
            savingsRs = mostExpensive.priceRs - cheapest.priceRs,
            allPrices = storePrices
        )
    }

    suspend fun getPriceComparison(
        itemName: String,
        userLocation: Location?
    ): PriceComparison? {
        return try {
            val recs = getTopRecommendations(itemName, userLocation)
            buildComparison(itemName, recs)
        } catch (e: Exception) {
            Log.e(TAG, "getPriceComparison failed: ${e.message}")
            null
        }
    }
}