package com.demo.butler_voice_app.utils

object HumanFillerManager {

    private val thinkingFillers = mapOf(
        "en" to listOf("sure...", "let me check...", "one sec...", "okay..."),
        "hi" to listOf("हाँ...", "ठीक है...", "देखता हूँ...", "एक सेकंड..."),
        "te" to listOf("సరే...", "చూస్తాను...", "ఒక్క నిమిషం...")
    )

    private val transitionPhrases = mapOf(
        "en" to listOf("alright...", "okay so...", "got it..."),
        "hi" to listOf("ठीक है, तो...", "अच्छा...", "चलते हैं..."),
        "te" to listOf("సరే, అయితే...", "అలాగే...")
    )

    // Hinglish — used when lang is "en" but user speaks Hindi mix
    private val hinglishFillers = listOf(
        "haan...", "theek hai...", "dekhta hoon...",
        "ek second...", "accha..."
    )

    private val hinglishTransitions = listOf(
        "theek hai, toh...", "accha...", "okay toh...", "chalte hain..."
    )

    fun getThinkingFiller(lang: String): String {
        // If Hindi script detected, use Hindi. Otherwise use Hinglish for Indian context
        return when {
            lang.startsWith("hi") -> (thinkingFillers["hi"] ?: hinglishFillers).random()
            lang.startsWith("te") -> (thinkingFillers["te"] ?: hinglishFillers).random()
            else -> hinglishFillers.random() // default to Hinglish — sounds natural in India
        }
    }

    fun getTransition(lang: String): String {
        return when {
            lang.startsWith("hi") -> (transitionPhrases["hi"] ?: hinglishTransitions).random()
            lang.startsWith("te") -> (transitionPhrases["te"] ?: hinglishTransitions).random()
            else -> hinglishTransitions.random()
        }
    }
}