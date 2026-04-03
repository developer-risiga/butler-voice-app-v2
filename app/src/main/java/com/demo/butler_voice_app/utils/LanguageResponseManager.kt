package com.demo.butler_voice_app.utils

import android.util.Log
import com.demo.butler_voice_app.ai.LanguageManager
import com.demo.butler_voice_app.ai.SessionLanguageManager

/**
 * LanguageResponseManager
 * ========================
 * Single entry point for ALL Butler TTS strings.
 * Reads the active language from LanguageManager / SessionLanguageManager
 * and delegates to ResponseTemplates.
 *
 * Usage in MainActivity.kt:
 *
 *   // Replace every hardcoded string call with:
 *   speak(LRM.wake(name))
 *   speak(LRM.confirmAddProduct(name, product.name, price.toInt()))
 *   speak(LRM.orderSuccess(firstName, etaMins))
 *
 * No constructor arguments needed — just call functions directly.
 */
object LRM {  // Short alias used throughout MainActivity

    private val TAG = "LRM"

    // ── Active language (always use this, not LanguageManager directly) ──
    val lang: String
        get() = LanguageManager.getLanguage().let { l ->
            // Hinglish detection: Devanagari words + Latin words mixed
            l  // LanguageManager already holds "hi", "te", "en" etc.
        }

    // Detect Hinglish from a raw transcript
    private fun isHinglish(transcript: String): Boolean {
        if (transcript.isBlank()) return false
        val words = transcript.trim().split("\\s+".toRegex())
        val total = words.size.coerceAtLeast(1)
        val devanagariWords = words.count { w -> w.any { it.code in 0x0900..0x097F } }
        val latinWords      = words.count { w -> w.any { it.isLetter() && it.code < 128 } }
        return (devanagariWords.toFloat() / total > 0.15f) && (latinWords.toFloat() / total > 0.15f)
    }

    // ──────────────────────────────────────────────────────────────────────
    // All 20 response functions — each is a one-liner delegating to Templates
    // ──────────────────────────────────────────────────────────────────────

    fun wake(name: String)                                    = ResponseTemplates.wakeResponse(name, lang)
    fun greetingHelp(name: String)                           = ResponseTemplates.greetingHelp(name, lang)
    fun askTypeRice(name: String)                            = ResponseTemplates.askTypeRice(name, lang)
    fun askTypeOil(name: String)                             = ResponseTemplates.askTypeOil(name, lang)
    fun askTypeDaal(name: String)                            = ResponseTemplates.askTypeDaal(name, lang)
    fun askTypeAtta(name: String)                            = ResponseTemplates.askTypeAtta(name, lang)
    fun askQuantity(name: String, product: String)           = ResponseTemplates.askQuantity(name, product, lang)
    fun confirmAddProduct(name: String, product: String, price: Int) = ResponseTemplates.confirmAddProduct(name, product, price, lang)
    fun itemAdded(name: String)                              = ResponseTemplates.itemAdded(name, lang)
    fun confirmAddNext(name: String, product: String, price: Int)    = ResponseTemplates.confirmAddNext(name, product, price, lang)
    fun itemNotFound(name: String, item: String)             = ResponseTemplates.itemNotFound(name, item, lang)
    fun confirmOrder(name: String)                           = ResponseTemplates.confirmOrder(name, lang)
    fun askPayment(name: String)                             = ResponseTemplates.askPayment(name, lang)
    fun paymentUpi()                                         = ResponseTemplates.paymentUpi(lang)
    fun paymentCard()                                        = ResponseTemplates.paymentCard(lang)
    fun paymentCash()                                        = ResponseTemplates.paymentCash(lang)
    fun orderSuccess(name: String, minutes: Int = 30)        = ResponseTemplates.orderSuccess(name, minutes, lang)
    fun orderFailed(name: String)                            = ResponseTemplates.orderFailed(name, lang)
    fun prescriptionPrompt(name: String)                     = ResponseTemplates.prescriptionPrompt(name, lang)
    fun serviceConfirm(name: String, service: String)        = ResponseTemplates.serviceConfirm(name, service, lang)
    fun repeatRequest(name: String)                          = ResponseTemplates.repeatRequest(name, lang)
    fun fallbackMsg(name: String)                            = ResponseTemplates.fallback(name, lang)
    fun cartSummary(name: String, itemCount: Int, total: Int) = ResponseTemplates.cartSummary(name, itemCount, total, lang)
    fun deliveryTime(name: String, minutes: Int)             = ResponseTemplates.deliveryTime(name, minutes, lang)
    fun storeClosed(name: String)                            = ResponseTemplates.storeClosed(name, lang)

    /**
     * Smart product-type dispatcher.
     * Reads the raw category name (from GPT/STT) and returns the right
     * category-specific question in the active language.
     */
    fun askTypeForCategory(name: String, rawCategory: String): String =
        ResponseTemplates.askTypeForCategory(name, rawCategory, lang)

    /**
     * Payment mode dispatcher — call after detecting which mode the user said.
     */
    fun paymentInstruction(mode: String): String = when (mode.lowercase()) {
        "upi", "gpay", "phonepe", "paytm", "bhim" -> paymentUpi()
        "card", "debit", "credit"                  -> paymentCard()
        "cash", "cod"                              -> paymentCash()
        else                                       -> paymentUpi()
    }
}


typealias LanguageResponseManager = LRM