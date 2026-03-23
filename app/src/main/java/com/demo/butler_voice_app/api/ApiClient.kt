package com.demo.butler_voice_app.api

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class ApiClient {

    @Serializable
    data class Product(
        val id: Int,
        val name: String,
        val price: Double
    )

    @Serializable
    data class OrderRow(
        val user_id: String,
        val items: String,
        val total_amount: Double,
        val status: String = "pending"
    )

    @Serializable
    data class OrderResult(
        val id: String
    )

    // ---- SEARCH PRODUCT (unchanged) ----
    suspend fun searchProduct(query: String): Product? {
        return try {
            val result = SupabaseClient.client
                .from("products")
                .select(columns = Columns.list("*"))
                .decodeList<Product>()
            result.firstOrNull {
                it.name.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "searchProduct failed: ${e.message}")
            null
        }
    }

    // ---- REAL ORDER WRITE ----
    suspend fun createOrder(
        items: List<Map<String, Any>>,
        userId: String
    ): String {

        // Build items as JSON string
        val itemsArray = buildJsonArray {
            items.forEach { item ->
                addJsonObject {
                    put("product_id",   item["product_id"].toString())
                    put("product_name", item["product_name"].toString())
                    put("quantity",     item["quantity"].toString().toIntOrNull() ?: 1)
                    put("price",        item["price"].toString().toDoubleOrNull() ?: 0.0)
                }
            }
        }

        val totalAmount = items.sumOf {
            val qty   = it["quantity"].toString().toDoubleOrNull() ?: 1.0
            val price = it["price"].toString().toDoubleOrNull() ?: 0.0
            qty * price
        }

        val orderRow = OrderRow(
            user_id      = userId,
            items        = itemsArray.toString(),
            total_amount = totalAmount,
            status       = "pending"
        )

        val result = SupabaseClient.client
            .from("orders")
            .insert(orderRow) {
                select()
            }
            .decodeSingle<OrderResult>()

        Log.d("ApiClient", "✅ Order created: ${result.id}")

        // Return last 8 chars uppercase — easy to read aloud
        return result.id.takeLast(8).uppercase()
    }
}
