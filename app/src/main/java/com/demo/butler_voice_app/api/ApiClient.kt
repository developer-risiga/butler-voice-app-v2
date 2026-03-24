package com.demo.butler_voice_app.api

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log
import com.demo.butler_voice_app.CartItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiClient {

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val url get() = SupabaseClient.SUPABASE_URL
    private val key get() = SupabaseClient.SUPABASE_KEY

    @Serializable
    data class Product(
        val id: Int,
        val name: String,
        val base_name: String? = null,
        val quantity: Double? = null,
        val unit: String? = null,
        val price: Double,
        val price_per_unit: Double? = null,
        val keywords: List<String>? = null
    )

    @Serializable
    data class OrderResult(
        val id: String,
        val total_amount: Double,
        val order_status: String
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

    // ─── PRODUCT SEARCH ───────────────────────────────────────

    suspend fun searchProduct(query: String): Product? {
        return withContext(Dispatchers.IO) {
            try {
                val lower = query.lowercase().trim()

                val req = Request.Builder()
                    .url("$url/rest/v1/products?select=*")
                    .addHeader("apikey", key)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                val res  = http.newCall(req).execute()
                val body = res.body?.string() ?: "[]"

                Log.d("ApiClient", "Products code: ${res.code}")

                if (!res.isSuccessful) {
                    Log.e("ApiClient", "Products failed: $body")
                    return@withContext null
                }

                val arr = json.parseToJsonElement(body).jsonArray
                Log.d("ApiClient", "Products count: ${arr.size}")

                val products = arr.mapNotNull {
                    try { json.decodeFromJsonElement(Product.serializer(), it) }
                    catch (e: Exception) { null }
                }

                val scored = products.mapNotNull { product ->
                    var score = 0
                    val nameLower = product.name.lowercase()
                    val baseLower = product.base_name?.lowercase() ?: ""

                    // Exact full name match
                    if (nameLower == lower) score += 200

                    // Name ends with query word — e.g. "INDIA GATE SUPER RICE" ends with "rice"
                    if (nameLower.endsWith(lower)) score += 150

                    // Name contains query as whole word
                    if (nameLower.contains(" $lower ") ||
                        nameLower.contains(" $lower") ||
                        nameLower.startsWith("$lower ")) score += 120

                    // Name contains query anywhere
                    if (nameLower.contains(lower)) score += 80

                    // Base name ends with query
                    if (baseLower.endsWith(lower)) score += 60

                    // Base name contains query
                    if (baseLower.contains(lower)) score += 40

                    // Keyword exact match
                    product.keywords?.forEach { kw ->
                        val kwLower = kw.lowercase()
                        if (kwLower == lower) score += 30
                        else if (kwLower.contains(lower) || lower.contains(kwLower)) score += 10
                    }

                    // Penalty: product is actually a different category that happens to contain word
                    // e.g. "RICE BRAN OIL" when searching "rice" — it's oil, not rice
                    if (lower == "rice" && (nameLower.contains("oil") || nameLower.contains("bran"))) score -= 100
                    if (lower == "oil" && nameLower.contains("rice bran")) score += 20
                    if (lower == "dal" && nameLower.contains("dalda")) score -= 50
                    if (lower == "milk" && nameLower.contains("milkmaid")) score -= 50

                    if (score > 0) Pair(product, score) else null
                }

                val best = scored.maxByOrNull { it.second }?.first
                Log.d("ApiClient", "searchProduct('$query') → ${best?.name} (score: ${scored.maxByOrNull { it.second }?.second})")
                best

            } catch (e: Exception) {
                Log.e("ApiClient", "searchProduct ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    // ─── CREATE ORDER ─────────────────────────────────────────

    suspend fun createOrder(cartItems: List<CartItem>, userId: String): OrderResult {
        return withContext(Dispatchers.IO) {
            Log.d("ApiClient", "createOrder called. Cart size: ${cartItems.size}, userId: $userId")

            val token = UserSessionManager.getToken()
                ?: throw Exception("Not authenticated — no token")

            if (cartItems.isEmpty()) throw Exception("Cart is empty")

            val totalAmount = cartItems.sumOf { it.product.price * it.quantity }
            Log.d("ApiClient", "Total amount: $totalAmount")

            val orderJson = """{"user_id":"$userId","status":"confirmed","order_status":"placed","payment_status":"pending","total_amount":$totalAmount}"""

            val orderReq = Request.Builder()
                .url("$url/rest/v1/orders?select=*")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(orderJson.toRequestBody("application/json".toMediaType()))
                .build()

            val orderRes  = http.newCall(orderReq).execute()
            val orderBody = orderRes.body?.string() ?: throw Exception("Empty response")

            Log.d("ApiClient", "Order response ${orderRes.code}: $orderBody")

            if (!orderRes.isSuccessful) throw Exception("Order failed ${orderRes.code}: $orderBody")

            val orderArr    = json.parseToJsonElement(orderBody).jsonArray
            val orderObj    = orderArr.first().jsonObject
            val orderId     = orderObj["id"]?.jsonPrimitive?.content ?: throw Exception("No order ID")
            val orderStatus = orderObj["order_status"]?.jsonPrimitive?.content ?: "placed"
            val orderTotal  = orderObj["total_amount"]?.jsonPrimitive?.content
                ?.toDoubleOrNull() ?: totalAmount

            Log.d("ApiClient", "Order created: $orderId")

            val itemsJson = "[" + cartItems.joinToString(",") { item ->
                """{"order_id":"$orderId","product_id":${item.product.id},"product_name":"${item.product.name}","quantity":${item.quantity},"price":${item.product.price}}"""
            } + "]"

            val itemsReq = Request.Builder()
                .url("$url/rest/v1/order_items")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(itemsJson.toRequestBody("application/json".toMediaType()))
                .build()

            val itemsRes  = http.newCall(itemsReq).execute()
            val itemsBody = itemsRes.body?.string()
            Log.d("ApiClient", "Items insert ${itemsRes.code}: $itemsBody")

            OrderResult(
                id           = orderId,
                total_amount = orderTotal,
                order_status = orderStatus
            )
        }
    }

    // ─── ORDER HISTORY ────────────────────────────────────────

    suspend fun getOrderHistory(userId: String): List<OrderHistory> {
        return try {
            val token = UserSessionManager.getToken() ?: return emptyList()

            val req = Request.Builder()
                .url("$url/rest/v1/orders?user_id=eq.$userId&select=id,total_amount,order_status,payment_status,created_at&order=created_at.desc&limit=20")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val res  = http.newCall(req).execute()
            val body = res.body?.string() ?: "[]"
            val arr  = json.parseToJsonElement(body).jsonArray

            arr.mapNotNull {
                try { json.decodeFromJsonElement(OrderHistory.serializer(), it) }
                catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "getOrderHistory failed: ${e.message}")
            emptyList()
        }
    }

    // ─── ORDER ITEMS ──────────────────────────────────────────

    suspend fun getOrderItems(orderId: String): List<OrderItemHistory> {
        return try {
            val token = UserSessionManager.getToken() ?: return emptyList()

            val req = Request.Builder()
                .url("$url/rest/v1/order_items?order_id=eq.$orderId&select=id,product_name,quantity,price")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val res  = http.newCall(req).execute()
            val body = res.body?.string() ?: "[]"
            val arr  = json.parseToJsonElement(body).jsonArray

            arr.mapNotNull {
                try { json.decodeFromJsonElement(OrderItemHistory.serializer(), it) }
                catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "getOrderItems failed: ${e.message}")
            emptyList()
        }
    }
}
