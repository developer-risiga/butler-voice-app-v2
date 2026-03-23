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

    // ─── DATA MODELS ──────────────────────────────────────────

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
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
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
    
                Log.d("ApiClient", "Products code: ${res.code}, sample: ${body.take(100)}")
    
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
                    if (product.name.lowercase() == lower) score += 100
                    if (product.name.lowercase().contains(lower)) score += 50
                    if (product.base_name?.lowercase()?.contains(lower) == true) score += 40
                    product.keywords?.forEach { kw ->
                        if (kw.lowercase().contains(lower) ||
                            lower.contains(kw.lowercase())) score += 30
                    }
                    if (score > 0) Pair(product, score) else null
                }
    
                val best = scored.maxByOrNull { it.second }?.first
                Log.d("ApiClient", "searchProduct('$query') → ${best?.name}")
                best
    
            } catch (e: Exception) {
                Log.e("ApiClient", "searchProduct ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    // ─── CREATE ORDER ─────────────────────────────────────────

    suspend fun createOrder(cartItems: List<CartItem>, userId: String): OrderResult {
        val token = UserSessionManager.getToken()
            ?: throw Exception("Not authenticated")

        val totalAmount = cartItems.sumOf { it.product.price * it.quantity }

        // Step 1: Insert order
        val orderJson = """
            {
              "user_id":"$userId",
              "status":"confirmed",
              "order_status":"placed",
              "payment_status":"pending",
              "total_amount":$totalAmount,
              "address":"",
              "latitude":0.0,
              "longitude":0.0
            }
        """.trimIndent()

        val orderReq = Request.Builder()
            .url("$url/rest/v1/orders?select=*")
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(orderJson.toRequestBody("application/json".toMediaType()))
            .build()

        val orderRes  = http.newCall(orderReq).execute()
        val orderBody = orderRes.body?.string() ?: throw Exception("No order response")

        if (!orderRes.isSuccessful) throw Exception("Order insert failed: $orderBody")

        Log.d("ApiClient", "Order response: $orderBody")

        val orderArr   = json.parseToJsonElement(orderBody).jsonArray
        val orderObj   = orderArr.first().jsonObject
        val orderId    = orderObj["id"]?.jsonPrimitive?.content ?: throw Exception("No order ID")
        val orderStatus = orderObj["order_status"]?.jsonPrimitive?.content ?: "placed"
        val orderTotal  = orderObj["total_amount"]?.jsonPrimitive?.content
            ?.toDoubleOrNull() ?: totalAmount

        Log.d("ApiClient", "Order created: $orderId")

        // Step 2: Insert order items
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

        val itemsRes = http.newCall(itemsReq).execute()
        Log.d("ApiClient", "Items insert code: ${itemsRes.code}")

        return OrderResult(
            id           = orderId,
            total_amount = orderTotal,
            order_status = orderStatus
        )
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
