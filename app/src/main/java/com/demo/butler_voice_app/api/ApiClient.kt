package com.demo.butler_voice_app.api

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

import kotlinx.serialization.Serializable

class ApiClient {

    @Serializable
    data class Product(
        val id: Int,
        val name: String,
        val price: Double
    )

    @Serializable
    data class OrderItem(
        val product_id: Int,
        val product_name: String,
        val quantity: Int,
        val price: Double
    )

    // ✅ SEARCH PRODUCT (COMPATIBLE WAY)
    suspend fun searchProduct(query: String): Product? {
        return try {

            val result = SupabaseClient.client
                .from("products")
                .select(columns = Columns.list("*"))
                .decodeList<Product>()

            // 🔥 Manual filtering (SAFE fallback)
            result.firstOrNull {
                it.name.contains(query, ignoreCase = true)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ✅ CREATE ORDER (TEMP SAFE VERSION)
    suspend fun createOrder(items: List<Map<String, Any>>): String {
        return try {
            // For now just simulate success (until RPC fixed cleanly)
            "ORDER_SUCCESS_TEMP"

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}