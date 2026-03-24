package com.demo.butler_voice_app.ai

object ItemNormalizer {

    private val map = mapOf(
        // Hindi
        "चावल" to "rice",
        "दाल" to "dal",
        "तेल" to "oil",
        "चीनी" to "sugar",
        "दूध" to "milk",

        // Telugu
        "బియ్యం" to "rice",
        "పప్పు" to "dal",
        "నూనె" to "oil",
        "చక్కెర" to "sugar",
        "పాలు" to "milk"
    )

    fun normalize(name: String): String {
        return map[name.lowercase()] ?: name.lowercase()
    }
}
