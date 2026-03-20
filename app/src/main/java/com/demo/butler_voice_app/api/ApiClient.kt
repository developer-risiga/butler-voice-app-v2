package com.demo.butler_voice_app.api


import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter
import io.github.jan.supabase.postgrest.decodeList
import io.github.jan.supabase.postgrest.decodeSingle
import kotlinx.serialization.Serializable

class ApiClient {

    // -----------------------------
    // ✅ PRODUCT DATA MODEL
    // -----------------------------
    @Serializable
    data class Product(
        val id: Int,
        val name: String,   // Make sure DB column is "name"
        val price: Double
    )

    // -----------------------------
    // 🔍 SEARCH PRODUCT
    // -----------------------------
    suspend fun searchProduct(query: String): Product? {
        return try {

            val result = SupabaseClient.client
                .from("products")
                .select {
                    filter {
                        ilike("searchable_text", "%$query%")
                    }
                    limit(1)
                }
                .decodeList<Product>()

            result.firstOrNull()

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // -----------------------------
    // 📦 CREATE ORDER (RPC)
    // -----------------------------
    suspend fun createOrder(items: List<Map<String, Any>>): String {
        return try {

            val response = SupabaseClient.client
                .rpc(
                    "create_order",
                    mapOf(
                        "items" to items
                    )
                )
                .decodeSingle<Map<String, Any>>()

            response["order_id"].toString()

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}