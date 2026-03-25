package com.demo.butler_voice_app.ai

object ItemNormalizer {

    private val aliases = mapOf(
        // Rice
        "chawal" to "rice", "चावल" to "rice", "basmati" to "rice",
        // Dal / Lentils
        "दाल" to "dal", "lentil" to "dal", "lentils" to "dal",
        "moong dal" to "moong dal", "toor dal" to "toor dal",
        "chana dal" to "chana dal", "masoor dal" to "masoor dal",
        // Oil
        "तेल" to "oil", "cooking oil" to "oil", "sunflower" to "sunflower oil",
        "mustard oil" to "mustard oil",
        // Sugar
        "चीनी" to "sugar", "shakkar" to "sugar",
        // Salt
        "नमक" to "salt", "namak" to "salt",
        // Wheat / Flour
        "गेहूं" to "wheat", "atta" to "wheat flour", "aata" to "wheat flour",
        "maida" to "maida flour",
        // Milk
        "दूध" to "milk", "dudh" to "milk",
        // Tea / Coffee
        "chai" to "tea", "चाय" to "tea",
        // Spices
        "हल्दी" to "turmeric", "haldi" to "turmeric",
        "jeera" to "cumin", "जीरा" to "cumin",
        "mirch" to "chili", "मिर्च" to "chili",
        "dhaniya" to "coriander",
        // Pulses
        "chana" to "chickpeas", "rajma" to "kidney beans",
        // Other
        "bread" to "bread", "egg" to "eggs", "eggs" to "eggs",
        "paneer" to "paneer", "ghee" to "ghee", "butter" to "butter",
        "maida" to "all purpose flour", "sooji" to "semolina",
        "poha" to "poha", "sabudana" to "sago"
    )

    fun normalize(name: String): String {
        val lower = name.lowercase().trim()
        return aliases[lower] ?: lower
    }
}
