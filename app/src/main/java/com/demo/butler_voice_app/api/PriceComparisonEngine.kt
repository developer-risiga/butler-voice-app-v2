package com.demo.butler_voice_app.api

data class StorePrice(
    val storeName: String,
    val storeId: String,
    val productName: String,
    val productId: String,
    val priceRs: Double,
    val unit: String,
    val distanceKm: Double,
    val deliveryMins: Int
)

data class PriceComparison(
    val itemName: String,
    val cheapest: StorePrice,
    val others: List<StorePrice>,
    val savingsRs: Double,
    val allPrices: List<StorePrice>
) {
    val hasMeaningfulSavings: Boolean get() = savingsRs >= 10.0
}

object PriceComparisonEngine {

    fun buildVoiceAnnouncement(comparison: PriceComparison, lang: String): String {
        val cheapest   = comparison.cheapest
        val cheapPrice = cheapest.priceRs.toInt()
        val storeName  = cheapest.storeName

        return when {
            comparison.allPrices.size > 1 && comparison.hasMeaningfulSavings -> {
                val savings = comparison.savingsRs.toInt()
                when {
                    lang.startsWith("hi") ->
                        "$storeName pe sabse sasta hai — $cheapPrice rupees. $savings rupees ki bachat. wahan se mangwaoon?"
                    else ->
                        "$storeName has the best price — $cheapPrice rupees. Save $savings rupees. Order from there?"
                }
            }
            comparison.allPrices.size > 1 -> {
                when {
                    lang.startsWith("hi") ->
                        "$storeName pe $cheapPrice rupees mein mil raha hai. ${cheapest.deliveryMins} minute mein delivery. order karoon?"
                    else ->
                        "$cheapPrice rupees at $storeName. Delivery in ${cheapest.deliveryMins} mins. Shall I order?"
                }
            }
            else -> {
                when {
                    lang.startsWith("hi") ->
                        "$storeName pe $cheapPrice rupees mein available hai. order karoon?"
                    else ->
                        "Available at $storeName for $cheapPrice rupees. Order?"
                }
            }
        }
    }

    fun selectBestOption(comparison: PriceComparison): StorePrice = comparison.cheapest

    fun buildSavingsBadge(comparison: PriceComparison): String? {
        if (!comparison.hasMeaningfulSavings) return null
        return "Save ₹${comparison.savingsRs.toInt()}"
    }
}