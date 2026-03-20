package com.demo.butler_voice_app.api

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

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

    @Serializable
    data class CreateOrderParams(
        val items: List<OrderItem>
    )

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

    suspend fun createOrder(items: List<Map<String, Any>>): String {
        return try {
            val orderItems = items.map { item ->
                OrderItem(
                    product_id = (item["product_id"] as Number).toInt(),
                    product_name = item["product_name"] as String,
                    quantity = (item["quantity"] as Number).toInt(),
                    price = (item["price"] as Number).toDouble()
                )
            }
            val params = CreateOrderParams(items = orderItems)
            val jsonParams = Json.encodeToJsonElement(params).jsonObject

            val response = SupabaseClient.client.postgrest
                .rpc("create_order", jsonParams)
                .decodeList<Map<String, String>>()

            response.first()["order_id"].toString()

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
