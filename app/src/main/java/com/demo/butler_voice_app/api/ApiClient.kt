package com.demo.butler_voice_app.api

import android.util.Log
import com.demo.butler_voice_app.CartItem
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class ApiClient {

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
        val order_status: String
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

    suspend fun searchProduct(query: String): Product? {
        return try {
            val lower = query.lowercase().trim()
            val allProducts = SupabaseClient.client
                .from("products")
                .select()
                .decodeList<Product>()

            val scored = allProducts.mapNotNull { product ->
                var score = 0
                if (product.name.lowercase() == lower) score += 100
                if (product.name.lowercase().contains(lower)) score += 50
                if (product.base_name?.lowercase()?.contains(lower) == true) score += 40
                product.keywords?.forEach { kw ->
                    if (kw.lowercase().contains(lower) || lower.contains(kw.lowercase())) score += 30
                }
                if (score > 0) Pair(product, score) else null
            }
            val best = scored.maxByOrNull { it.second }?.first
            Log.d("ApiClient", "searchProduct('$query') found: ${best?.name}")
            best
        } catch (e: Exception) {
            Log.e("ApiClient", "searchProduct failed: ${e.message}")
            null
        }
    }

    suspend fun createOrder(cartItems: List<CartItem>, userId: String): OrderResult {
        val totalAmount = cartItems.sumOf { it.product.price * it.quantity }

        val order = SupabaseClient.client
            .from("orders")
            .insert(OrderInsert(user_id = userId, total_amount = totalAmount)) {
                select()
            }
            .decodeSingle<OrderResult>()

        Log.d("ApiClient", "Order created: ${order.id}")

        val items = cartItems.map {
            OrderItemInsert(
                order_id     = order.id,
                product_id   = it.product.id,
                product_name = it.product.name,
                quantity     = it.quantity,
                price        = it.product.price
            )
        }
        SupabaseClient.client.from("order_items").insert(items)
        Log.d("ApiClient", "${items.size} items inserted")
        return order
    }

    suspend fun getOrderHistory(userId: String): List<OrderHistory> {
        return try {
            SupabaseClient.client
                .from("orders")
                .select(columns = Columns.list(
                    "id", "total_amount", "order_status", "payment_status", "created_at"
                ))
                .decodeList<OrderHistory>()
        } catch (e: Exception) {
            Log.e("ApiClient", "getOrderHistory failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getOrderItems(orderId: String): List<OrderItemHistory> {
        return try {
            SupabaseClient.client
                .from("order_items")
                .select(columns = Columns.list(
                    "id", "product_name", "quantity", "price"
                ))
                .decodeList<OrderItemHistory>()
        } catch (e: Exception) {
            Log.e("ApiClient", "getOrderItems failed: ${e.message}")
            emptyList()
        }
    }
}
