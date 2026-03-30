package com.demo.butler_voice_app.api

import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorePrice(
    val storeName: String,       // "Blinkit", "Zepto", "Swiggy Instamart"
    val storeId: String,
    val productName: String,
    val productId: String,
    val priceRs: Double,
    val unit: String,
    val distanceKm: Double,
    val deliveryMins: Int        // estimated delivery time
)

data class PriceComparison(
    val itemName: String,
    val cheapest: StorePrice,
    val others: List<StorePrice>,
    val savingsRs: Double,       // how much cheaper vs most expensive
    val allPrices: List<StorePrice>
) {
    val hasMeaningfulSavings: Boolean get() = savingsRs >= 10.0
}

object PriceComparisonEngine {

    private const val TAG = "PriceComparison"

    // Build the voice announcement for price comparison
    // This is what Butler actually says to the user
    fun buildVoiceAnnouncement(comparison: PriceComparison, lang: String): String {
        val cheapest   = comparison.cheapest
        val cheapPrice = cheapest.priceRs.toInt()
        val storeName  = cheapest.storeName

        return when {
            // Multiple stores found with meaningful price difference
            comparison.allPrices.size > 1 && comparison.hasMeaningfulSavings -> {
                val expensive  = comparison.allPrices.last()
                val savings    = comparison.savingsRs.toInt()
                when {
                    lang.startsWith("hi") ->
                        "$storeName pe sabse sasta hai — ${cheapPrice} rupees. ${savings} rupees ki bachat. wahan se mangwaoon?"
                    else ->
                        "${storeName} has the best price — ${cheapPrice} rupees. Save ${savings} rupees. Order from there?"
                }
            }
            // Multiple stores, similar prices — just show cheapest
            comparison.allPrices.size > 1 -> {
                when {
                    lang.startsWith("hi") ->
                        "${storeName} pe ${cheapPrice} rupees mein mil raha hai. ${cheapest.deliveryMins} minute mein delivery. order karoon?"
                    else ->
                        "${cheapPrice} rupees at ${storeName}. Delivery in ${cheapest.deliveryMins} mins. Shall I order?"
                }
            }
            // Only one store found
            else -> {
                when {
                    lang.startsWith("hi") ->
                        "${storeName} pe ${cheapPrice} rupees mein available hai. order karoon?"
                    else ->
                        "Available at ${storeName} for ${cheapPrice} rupees. Order?"
                }
            }
        }
    }

    // Called when user says "haan" after hearing the comparison
    // Returns the store + product to order from
    fun selectBestOption(comparison: PriceComparison): StorePrice = comparison.cheapest

    // If user says "doosra option" or "second one" — return next best
    fun selectByIndex(comparison: PriceComparison, index: Int): StorePrice? =
        comparison.allPrices.getOrNull(index)

    // Build a short screen label for the UI card
    fun buildSavingsBadge(comparison: PriceComparison): String? {
        if (!comparison.hasMeaningfulSavings) return null
        return "Save ₹${comparison.savingsRs.toInt()}"
    }
}