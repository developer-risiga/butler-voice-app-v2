package com.demo.butler_voice_app.api


import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class ApiClient {

    // ─── DATA MODELS ───────────────────────────────────────────

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
        val description: String? = null
    )

    @Serializable
    data class OrderInsert(
        val user_id: String,
        val status: String = "confirmed",
        val order_status: String = "placed",
        val payment_status: String = "pending",
        val total_amount: Double,
        val address: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    )

    @Serializable
    data class OrderResult(
        val id: String,
        val total_amount: Double,
        val order_status: String,
        val created_at: String? = null
    )

    @Serializable
    data class OrderItemInsert(
        val order_id: String,
        val product_id: Int,
        val product_name: String,
        val quantity: Int,
        val price: Double
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

    // ─── PRODUCT SEARCH ────────────────────────────────────────

    suspend fun searchProduct(query: String): Product? {
        return try {
            val lower = query.lowercase().trim()

            // 1. Try exact name match first
            val allProducts = SupabaseClient.client
                .from("products")
                .select(columns = Columns.list(
                    "id", "name", "base_name", "quantity",
                    "unit", "price", "price_per_unit", "keywords"
                ))
                .decodeList<Product>()

            // 2. Score each product — keywords array gets highest weight
            val scored = allProducts.mapNotNull { product ->
                var score = 0

                // Exact name match
                if (product.name.lowercase() == lower) score += 100

                // Name contains query
                if (product.name.lowercase().contains(lower)) score += 50

                // base_name match
                if (product.base_name?.lowercase()?.contains(lower) == true) score += 40

                // Keywords array match
                product.keywords?.forEach { kw ->
                    if (kw.lowercase().contains(lower) ||
                        lower.contains(kw.lowercase())) score += 30
                }

                if (score > 0) Pair(product, score) else null
            }

            val best = scored.maxByOrNull { it.second }?.first
            Log.d("ApiClient", "searchProduct('$query') → ${best?.name} (score: ${scored.maxByOrNull{it.second}?.second})")
            best

        } catch (e: Exception) {
            Log.e("ApiClient", "searchProduct failed: ${e.message}")
            null
        }
    }

    // ─── CREATE FULL ORDER (orders + order_items) ──────────────

    suspend fun createOrder(
        cartItems: List<CartItem>,
        userId: String
    ): OrderResult {

        val totalAmount = cartItems.sumOf {
            it.product.price * it.quantity
        }

        // Step 1: Insert into orders table
        val orderInsert = OrderInsert(
            user_id      = userId,
            total_amount = totalAmount
        )

        val order = SupabaseClient.client
            .from("orders")
            .insert(orderInsert) { select() }
            .decodeSingle<OrderResult>()

        Log.d("ApiClient", "✅ Order created: ${order.id}")

        // Step 2: Insert all items into order_items table
        val orderItems = cartItems.map { cartItem ->
            OrderItemInsert(
                order_id     = order.id,
                product_id   = cartItem.product.id,
                product_name = cartItem.product.name,
                quantity     = cartItem.quantity,
                price        = cartItem.product.price
            )
        }

        SupabaseClient.client
            .from("order_items")
            .insert(orderItems)

        Log.d("ApiClient", "✅ ${orderItems.size} order items inserted")

        return order
    }

    // ─── FETCH ORDER HISTORY ───────────────────────────────────

    suspend fun getOrderHistory(userId: String): List<OrderHistory> {
        return try {
            SupabaseClient.client
                .from("orders")
                .select(columns = Columns.list(
                    "id", "total_amount", "order_status",
                    "payment_status", "created_at"
                )) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<OrderHistory>()
        } catch (e: Exception) {
            Log.e("ApiClient", "getOrderHistory failed: ${e.message}")
            emptyList()
        }
    }

    // ─── FETCH ITEMS FOR ONE ORDER ─────────────────────────────

    suspend fun getOrderItems(orderId: String): List<OrderItemHistory> {
        return try {
            SupabaseClient.client
                .from("order_items")
                .select(columns = Columns.list(
                    "id", "product_name", "quantity", "price"
                )) {
                    filter {
                        eq("order_id", orderId)
                    }
                }
                .decodeList<OrderItemHistory>()
        } catch (e: Exception) {
            Log.e("ApiClient", "getOrderItems failed: ${e.message}")
            emptyList()
        }
    }
}
