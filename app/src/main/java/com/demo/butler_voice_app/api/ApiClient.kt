package com.demo.butler_voice_app.api

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log
import com.demo.butler_voice_app.CartItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30,  TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val url  get() = SupabaseClient.SUPABASE_URL
    private val key  get() = SupabaseClient.SUPABASE_KEY

    @Serializable
    data class Product(
        val id: Int,
        val name: String,
        val base_name: String? = null,
        val quantity: Double? = null,
        val unit: String? = null,
        val price: Double,
        val price_per_unit: Double? = null,
        val keywords: List<String>? = null,
        // FIX: added category_id so search scoring can heavily penalise
        // cross-category matches (e.g. "rice" hitting "Rice Bran Oil" in Oils)
        val category_id: Int? = null
    )

    @Serializable
    data class OrderResult(
        val id: String,
        val total_amount: Double,
        val order_status: String,
        val public_id: String = ""
    )

    @Serializable
    data class OrderHistory(
        val id: String,
        val total_amount: Double,
        val order_status: String,
        val payment_status: String,
        val created_at: String? = null
    )

    @Serializable
    data class OrderItemHistory(
        val id: String,
        val product_name: String,
        val quantity: Int,
        val price: Double
    )

    // ── PRODUCT CACHE ─────────────────────────────────────────────────────

    @Volatile
    private var cachedProducts: List<Product>? = null

    // ── CATEGORY MAP — query keyword → Supabase category_id ──────────────
    // Based on your categories table (id, name, slug):
    //  1=Aata/Flour  2=Rice  3=Dal/Pulses  4=Ghee  5=Oils  6=Salt
    //  7=Sugar       8=Tea/Coffee  9=Biscuits  10=Snacks  11=Packaged Food
    // 12=Dairy  13=Frozen  14=Sauces  15=Beverages  16=Spices
    // 17=Soap  18=Shampoo  19=Oral Care  20=Detergents  21=Dishwash
    // 22=Bread/Bakery  23=Vegetables  24=Fruits  25=Eggs  32=Hair Care
    private val queryCategoryMap: Map<String, Int> = mapOf(
        // Rice
        "rice" to 2, "chawal" to 2, "चावल" to 2, "అన్నం" to 2,
        "basmati" to 2, "sona masoori" to 2,
        // Flour/Atta
        "atta" to 1, "flour" to 1, "wheat" to 1, "maida" to 1,
        "आटा" to 1, "పిండి" to 1,
        // Dal
        "dal" to 3, "lentil" to 3, "pulse" to 3, "दाल" to 3,
        "పప్పు" to 3, "moong" to 3, "masoor" to 3, "chana" to 3, "toor" to 3,
        // Ghee
        "ghee" to 4, "घी" to 4,
        // Oil
        "oil" to 5, "तेल" to 5, "నూనె" to 5, "sunflower" to 5,
        "mustard oil" to 5, "coconut oil" to 5, "groundnut oil" to 5,
        // Salt
        "salt" to 6, "नमक" to 6, "ఉప్పు" to 6,
        // Sugar
        "sugar" to 7, "jaggery" to 7, "गुड़" to 7, "चीनी" to 7, "చక్కెర" to 7,
        // Tea/Coffee
        "tea" to 8, "coffee" to 8, "चाय" to 8, "టీ" to 8,
        // Biscuits
        "biscuit" to 9, "cookie" to 9, "cracker" to 9,
        // Snacks
        "snack" to 10, "namkeen" to 10, "chips" to 10, "mixture" to 10,
        // Dairy
        "milk" to 12, "curd" to 12, "paneer" to 12, "butter" to 12,
        "दूध" to 12, "పాలు" to 12, "दही" to 12,
        // Spices
        "masala" to 16, "spice" to 16, "हल्दी" to 16, "turmeric" to 16,
        "cumin" to 16, "jeera" to 16, "pepper" to 16, "mirchi" to 16,
        // Soap
        "soap" to 17, "sabun" to 17, "साबुन" to 17,
        // Shampoo
        "shampoo" to 18, "conditioner" to 18,
        // Eggs
        "egg" to 25, "eggs" to 25, "अंडा" to 25,
        // Bread
        "bread" to 22, "रोटी" to 22,
        // Vegetables
        "vegetable" to 23, "sabzi" to 23, "सब्जी" to 23
    )

    /**
     * Returns the expected category_id for a search query, or null if unknown.
     * Used in scoring to heavily penalise products from the wrong category.
     */
    private fun getExpectedCategory(query: String): Int? {
        val lower = query.lowercase().trim()
        // Exact match first
        queryCategoryMap[lower]?.let { return it }
        // Partial match
        return queryCategoryMap.entries.firstOrNull { (key, _) ->
            lower.contains(key) || key.contains(lower)
        }?.value
    }

    // ── PRE-FETCH ─────────────────────────────────────────────────────────

    suspend fun prefetchProducts() = withContext(Dispatchers.IO) {
        if (cachedProducts != null) return@withContext
        try {
            val req = Request.Builder()
                .url("$url/rest/v1/products?select=id,name,base_name,unit,price,keywords,category_id&limit=3000")
                .addHeader("apikey",        key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Accept",        "application/json")
                .get().build()

            val res  = http.newCall(req).execute()
            val body = res.body?.string() ?: "[]"
            Log.d("ApiClient", "Products prefetch: ${res.code}, size: ${body.length}")
            if (res.isSuccessful) {
                val list = try {
                    json.decodeFromString(ListSerializer(Product.serializer()), body)
                } catch (e: Exception) {
                    Log.e("ApiClient", "Prefetch parse error: ${e.message}"); emptyList()
                }
                cachedProducts = list
                Log.d("ApiClient", "Products cached: ${list.size}")
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "prefetchProducts ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ── PRODUCT SEARCH ────────────────────────────────────────────────────

    suspend fun searchProduct(query: String): Product? {
        return withContext(Dispatchers.IO) {
            try {
                val lower = query.lowercase().trim()

                val products = cachedProducts ?: run {
                    val req = Request.Builder()
                        .url("$url/rest/v1/products?select=id,name,base_name,unit,price,keywords,category_id&limit=3000")
                        .addHeader("apikey",        key)
                        .addHeader("Authorization", "Bearer $key")
                        .addHeader("Accept",        "application/json")
                        .get().build()

                    val res  = http.newCall(req).execute()
                    val body = res.body?.string() ?: "[]"
                    Log.d("ApiClient", "Products fetched: ${res.code}, size: ${body.length}")
                    if (!res.isSuccessful) {
                        Log.e("ApiClient", "Products failed: $body")
                        return@withContext null
                    }
                    val list = try {
                        json.decodeFromString(ListSerializer(Product.serializer()), body)
                    } catch (e: Exception) {
                        Log.e("ApiClient", "Parse error: ${e.message}"); emptyList()
                    }
                    cachedProducts = list
                    list
                }

                // Expected category for this query (null = unknown/general)
                val expectedCategory = getExpectedCategory(lower)

                val scored = products.mapNotNull { product ->
                    var score     = 0
                    val nameLower  = product.name.lowercase()
                    val baseLower  = product.base_name?.lowercase() ?: ""

                    // ── Name match scoring ────────────────────────────────
                    if (nameLower == lower)                                    score += 200
                    if (nameLower.endsWith(lower))                             score += 150
                    if (nameLower.contains(" $lower ") ||
                        nameLower.contains(" $lower")  ||
                        nameLower.startsWith("$lower "))                       score += 120
                    if (nameLower.contains(lower))                             score += 80
                    if (baseLower.endsWith(lower))                             score += 60
                    if (baseLower.contains(lower))                             score += 40

                    product.keywords?.forEach { kw ->
                        val kwLower = kw.lowercase()
                        if (kwLower == lower)                                  score += 30
                        else if (kwLower.contains(lower) || lower.contains(kwLower)) score += 10
                    }

                    // ── CATEGORY PENALTY — main fix for cross-category hits ─
                    // If we know the expected category and this product is in a
                    // different category, apply a heavy penalty.
                    // Example: "rice" (cat 2) searching "Rice Bran Oil" (cat 5)
                    //   → +80 for name contains "rice", -300 for wrong category
                    //   → final score = -220 → filtered out
                    if (expectedCategory != null && product.category_id != null
                        && product.category_id != expectedCategory) {
                        score -= 300
                    }

                    if (score > 0) Pair(product, score) else null
                }

                val best = scored.maxByOrNull { it.second }?.first
                Log.d("ApiClient", "searchProduct('$query') cat=$expectedCategory → ${best?.name}")
                best

            } catch (e: Exception) {
                Log.e("ApiClient", "searchProduct ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    fun clearProductCache() {
        cachedProducts = null
    }

    // ── CREATE ORDER ──────────────────────────────────────────────────────

    suspend fun createOrder(cartItems: List<CartItem>, userId: String): OrderResult {
        return withContext(Dispatchers.IO) {
            Log.d("ApiClient", "createOrder called. Cart size: ${cartItems.size}, userId: $userId")

            val token = UserSessionManager.getToken()
                ?: throw Exception("Not authenticated — no token")

            if (cartItems.isEmpty()) throw Exception("Cart is empty")

            val totalAmount = cartItems.sumOf { it.product.price * it.quantity }

            val orderJson = """{"user_id":"$userId","status":"confirmed","order_status":"placed","payment_status":"pending","total_amount":$totalAmount}"""

            val orderReq = Request.Builder()
                .url("$url/rest/v1/orders?select=*")
                .addHeader("apikey",        key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type",  "application/json")
                .addHeader("Prefer",        "return=representation")
                .post(orderJson.toRequestBody("application/json".toMediaType()))
                .build()

            val orderRes  = http.newCall(orderReq).execute()
            val orderBody = orderRes.body?.string() ?: throw Exception("Empty response")

            if (!orderRes.isSuccessful) throw Exception("Order failed ${orderRes.code}: $orderBody")

            val orderArr    = json.parseToJsonElement(orderBody).jsonArray
            val orderObj    = orderArr.first().jsonObject
            val orderId     = orderObj["id"]?.jsonPrimitive?.content ?: throw Exception("No order ID")
            val publicId    = orderObj["public_id"]?.jsonPrimitive?.content ?: ""
            val orderStatus = orderObj["order_status"]?.jsonPrimitive?.content ?: "placed"
            val orderTotal  = orderObj["total_amount"]?.jsonPrimitive?.content
                ?.toDoubleOrNull() ?: totalAmount

            val itemsJson = "[" + cartItems.joinToString(",") { item ->
                """{"order_id":"$orderId","product_id":${item.product.id},"product_name":"${item.product.name}","quantity":${item.quantity},"price":${item.product.price}}"""
            } + "]"

            val itemsReq = Request.Builder()
                .url("$url/rest/v1/order_items")
                .addHeader("apikey",        key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type",  "application/json")
                .addHeader("Prefer",        "return=minimal")
                .post(itemsJson.toRequestBody("application/json".toMediaType()))
                .build()

            val itemsRes  = http.newCall(itemsReq).execute()
            Log.d("ApiClient", "Items insert ${itemsRes.code}")

            OrderResult(id = orderId, total_amount = orderTotal,
                order_status = orderStatus, public_id = publicId)
        }
    }

    // ── ORDER HISTORY ─────────────────────────────────────────────────────

    suspend fun getOrderHistory(userId: String): List<OrderHistory> {
        return try {
            val token = UserSessionManager.getToken() ?: return emptyList()
            val req = Request.Builder()
                .url("$url/rest/v1/orders?user_id=eq.$userId&select=id,total_amount,order_status,payment_status,created_at&order=created_at.desc&limit=20")
                .addHeader("apikey",        key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept",        "application/json")
                .get().build()

            val res  = http.newCall(req).execute()
            val body = res.body?.string() ?: "[]"
            try {
                json.decodeFromString(ListSerializer(OrderHistory.serializer()), body)
            } catch (e: Exception) {
                Log.e("ApiClient", "getOrderHistory parse: ${e.message}"); emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "getOrderHistory failed: ${e.message}"); emptyList()
        }
    }

    // ── ORDER ITEMS ───────────────────────────────────────────────────────

    suspend fun getOrderItems(orderId: String): List<OrderItemHistory> {
        return try {
            val token = UserSessionManager.getToken() ?: return emptyList()
            val req = Request.Builder()
                .url("$url/rest/v1/order_items?order_id=eq.$orderId&select=id,product_name,quantity,price")
                .addHeader("apikey",        key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept",        "application/json")
                .get().build()

            val res  = http.newCall(req).execute()
            val body = res.body?.string() ?: "[]"
            try {
                json.decodeFromString(ListSerializer(OrderItemHistory.serializer()), body)
            } catch (e: Exception) {
                Log.e("ApiClient", "getOrderItems parse: ${e.message}"); emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "getOrderItems failed: ${e.message}"); emptyList()
        }
    }
}