package com.demo.butler_voice_app

import com.demo.butler_voice_app.api.ApiClient

data class CartItem(
    val product: ApiClient.Product,
    var quantity: Int,
    val unit: String? = null
) {
    fun displayQty(): String {
        val u = unit ?: product.unit ?: ""
        return if (u.isNotBlank()) "$quantity $u" else "$quantity"
    }
    fun lineTotal(): Double = product.price * quantity
}
