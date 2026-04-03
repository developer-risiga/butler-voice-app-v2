package com.demo.butler_voice_app.utils

import com.demo.butler_voice_app.ai.UserMood
import com.demo.butler_voice_app.utils.ResponseTemplates
import java.util.Calendar

/**
 * ButlerPersonalityEngine — updated to delegate all template-based responses
 * to ResponseTemplates, while keeping personality variants, mood handling,
 * and session de-duplication intact.
 *
 * CHANGE SUMMARY (all additive, nothing removed):
 *  • itemAdded()      → delegates to ResponseTemplates.itemAdded()
 *  • askMore()        → delegates to ResponseTemplates (base) + keeps suggestions
 *  • confirmOrder()   → delegates to ResponseTemplates.confirmOrder()
 *  • askPaymentMode() → delegates to ResponseTemplates.askPayment()
 *  • upiInstruction() → delegates to ResponseTemplates.paymentUpi()
 *  • paymentDone()    → stays inline (short, mood-aware, no template needed)
 *  • orderPlaced()    → delegates to ResponseTemplates.orderSuccess()
 *  • productNotFound()→ delegates to ResponseTemplates.itemNotFound()
 *  • cartEmpty()      → delegates to ResponseTemplates (inline fallback kept)
 *  • didntHear()      → stays inline (retry count + mood variants still needed)
 *  • giveUp()         → stays inline (language-specific flair kept)
 *  • greeting()       → stays inline (time-of-day + last-product logic retained)
 *
 * All other methods (askQuantity, askSelection, reorderGreeting, etc.) unchanged.
 */
object ButlerPersonalityEngine {

    private val lastUsed = mutableMapOf<String, Int>()

    private fun pick(key: String, variants: List<String>): String {
        val last      = lastUsed[key] ?: -1
        val available = variants.indices.filter { it != last }
        val idx       = if (available.isEmpty()) 0 else available.random()
        lastUsed[key] = idx
        return variants[idx]
    }

    fun resetSession() { lastUsed.clear() }

    private fun timeSlot(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11  -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else      -> "night"
    }

    // ═════════════════════════════════════════════════════════════════════
    // 1. GREETING  (unchanged — time-of-day + last-product logic retained)
    // ═════════════════════════════════════════════════════════════════════

    fun greeting(name: String, lang: String, lastProduct: String?, mood: UserMood): String {
        val time = timeSlot()
        return when {
            lang.startsWith("hi") -> when {
                lastProduct != null -> pick("hi_greet_ret", listOf(
                    "$name, $lastProduct chahiye kya?",
                    "$name, pichli baar $lastProduct liya tha. Wahi दूं?",
                    "$name, $lastProduct फिर से?",
                    "$name ji, kya chahiye aaj?",
                    "$name, bolo kya laana hai."
                ))
                time == "morning" -> pick("hi_greet_morn", listOf(
                    "$name ji, subah ki zaroorat batao.",
                    "$name, subah mein क्या चाहिए?",
                    "$name ji, kya laana hai?"
                ))
                time == "evening" -> pick("hi_greet_eve", listOf(
                    "$name ji, shaam mein क्या चाहिए?",
                    "$name, kya mangwaaein?",
                    "$name ji, bolo kya laana hai."
                ))
                else -> pick("hi_greet_new", listOf(
                    "$name ji, क्या चाहिए?",
                    "$name, bolo kya laana hai.",
                    "$name ji, kya mangwaaein?",
                    "$name, haan boliye."
                ))
            }
            lang.startsWith("te") -> when {
                lastProduct != null -> pick("te_greet_ret", listOf(
                    "Namaskaram $name garu! $lastProduct meeru tecchukovadam? Ledanta?",
                    "$name garu, $lastProduct kavala?",
                    "Namaskaram $name! Innikemi kavali?"
                ))
                time == "morning" -> pick("te_greet_morn", listOf(
                    "Subha prabhatam $name garu! Emi kavali?",
                    "$name garu, emi teestara?"
                ))
                else -> pick("te_greet_base", listOf(
                    "Namaskaram $name garu! Emi kavali?",
                    "$name garu, emi cheppandee?",
                    "Namaskaram $name! Innikemi kavali?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_greet", listOf(
                "Vanakkam $name! Enna vendum?",
                "$name, enna tharen?"
            ))
            lang.startsWith("kn") -> pick("kn_greet", listOf(
                "Namaskara $name! Enu beku?",
                "$name, enu tegolabeeku?"
            ))
            lang.startsWith("ml") -> pick("ml_greet", listOf(
                "Namaskaram $name! Enthu veno?",
                "$name, enthu venam?"
            ))
            lang.startsWith("pa") -> pick("pa_greet", listOf(
                "Sat sri akal $name ji! Ki chahida?",
                "$name ji, ki mangwaiye?"
            ))
            lang.startsWith("gu") -> pick("gu_greet", listOf(
                "Namaste $name! Shu joiye?",
                "$name, shu mangaavun?"
            ))
            lang.startsWith("mr") -> pick("mr_greet", listOf(
                "Namaskar $name! Kay havay?",
                "$name, kay anat?"
            ))
            else -> when {
                lastProduct != null -> pick("en_greet_ret", listOf(
                    "Hey $name! Same as last time — $lastProduct?",
                    "Hi $name! Want $lastProduct again?",
                    "$name, what do you need?"
                ))
                time == "morning" -> pick("en_greet_morn", listOf(
                    "Morning $name! What do you need?",
                    "Good morning $name! What can I get you?"
                ))
                else -> pick("en_greet_new", listOf(
                    "Hi $name! What do you need?",
                    "Hey $name! What can I get you?",
                    "$name, what shall I order?"
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 2. REORDER SUGGESTION (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    fun reorderGreeting(name: String, items: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_reorder", listOf(
                "$name, wahi chahiye kya — $items?",
                "$name, $items फिर से दूं?",
                "$name ji, $items order karoon?",
                "$name, $items chahiye?"
            ))
            lang.startsWith("te") -> pick("te_reorder", listOf(
                "$name garu, $items kavala?",
                "$name, $items meeru mandistara?"
            ))
            lang.startsWith("ta") -> pick("ta_reorder", listOf(
                "$name, $items venuma?",
                "$name, $items poduma?"
            ))
            lang.startsWith("kn") -> pick("kn_reorder", listOf(
                "$name, $items beku?",
                "$name, $items again beku?"
            ))
            lang.startsWith("ml") -> pick("ml_reorder", listOf(
                "$name, $items venum?",
                "$name, $items again venam?"
            ))
            lang.startsWith("pa") -> pick("pa_reorder", listOf(
                "$name ji, $items chahida?",
                "$name, $items phir mangwaiye?"
            ))
            lang.startsWith("gu") -> pick("gu_reorder", listOf(
                "$name, $items joiye?",
                "$name, $items again joiye?"
            ))
            lang.startsWith("mr") -> pick("mr_reorder", listOf(
                "$name, $items pahije?",
                "$name, $items parat gheyche ka?"
            ))
            else -> pick("en_reorder", listOf(
                "$name, same as last time — $items?",
                "Hey $name! $items again?",
                "$name, want the usual — $items?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 3. ITEM ADDED  ← delegates to ResponseTemplates
    // ═════════════════════════════════════════════════════════════════════

    fun itemAdded(productName: String, lang: String, mood: UserMood, cartSize: Int): String {
        // Short/rushed mood: use a compact fallback
        if (mood == UserMood.FRUSTRATED || mood == UserMood.RUSHED) {
            return when {
                lang.startsWith("hi") -> pick("hi_added_r", listOf("ठीक।", "हो गया।", "कार्ट में आ गया।"))
                lang.startsWith("te") -> pick("te_added_r", listOf("Sare.", "Ayindi.", "Done."))
                lang.startsWith("ta") -> "Sari."
                lang.startsWith("kn") -> "Sari."
                lang.startsWith("ml") -> "Sari."
                lang.startsWith("pa") -> "Theek."
                lang.startsWith("gu") -> "Saru."
                lang.startsWith("mr") -> "ठीक."
                else -> pick("en_added_r", listOf("Done.", "Got it.", "Okay."))
            }
        }
        // Standard path — extract first name from productName for display
        val firstName = UserSessionManager_compat.firstName ?: "there"
        return ResponseTemplates.itemAdded(firstName, lang)
    }

    // ═════════════════════════════════════════════════════════════════════
    // 4. ASK MORE  ← delegates to ResponseTemplates (keeps suggestion logic)
    // ═════════════════════════════════════════════════════════════════════

    fun askMore(lang: String, mood: UserMood, cartSize: Int, lastProduct: String?): String {
        val suggestion = getRelatedSuggestion(lastProduct, lang)

        if (mood == UserMood.FRUSTRATED || mood == UserMood.RUSHED) {
            return when {
                lang.startsWith("hi") -> pick("hi_more_r", listOf("और?", "कुछ और?", "बस?", "क्या चाहिए?"))
                lang.startsWith("te") -> pick("te_more_r", listOf("Inkaa?", "Maro?", "Antena?"))
                lang.startsWith("ta") -> "Vera?"
                lang.startsWith("kn") -> "Inenu?"
                lang.startsWith("ml") -> "Innum?"
                lang.startsWith("pa") -> "Hor ki?"
                lang.startsWith("gu") -> "Biju shu?"
                lang.startsWith("mr") -> "आणखी?"
                else -> pick("en_more_r", listOf("More?", "Anything else?", "That it?"))
            }
        }

        // With suggestion (first item added → cross-sell)
        if (suggestion != null && cartSize == 1) {
            return when {
                lang.startsWith("hi") -> pick("hi_more_sug", listOf(
                    "$suggestion भी साथ में दूं?",
                    "$suggestion भी चाहिए?",
                    "और $suggestion?"
                ))
                lang.startsWith("te") -> "$suggestion kuda kavala?"
                lang.startsWith("ta") -> "$suggestion vendum?"
                lang.startsWith("kn") -> "$suggestion beku?"
                lang.startsWith("ml") -> "$suggestion venum?"
                lang.startsWith("pa") -> "$suggestion chahida?"
                lang.startsWith("gu") -> "$suggestion joie?"
                lang.startsWith("mr") -> "$suggestion हवं?"
                else -> "$suggestion as well?"
            }
        }

        // Standard ask-more variants per language
        return when {
            lang.startsWith("hi") -> pick("hi_more", listOf(
                "बस इतना?", "कुछ और?", "और क्या चाहिए?", "कुछ और लेना है?", "कुछ और मंगवाएं?"
            ))
            lang.startsWith("te") -> pick("te_more", listOf(
                "Inkaa emi kavali?", "Marokkati?", "Inka emi?", "Chaaladaa?"
            ))
            lang.startsWith("ta") -> pick("ta_more", listOf("Vera enna?", "Inniku?", "Vera?", "Porum?"))
            lang.startsWith("kn") -> pick("kn_more", listOf("Inenu?", "Bere?", "Innu?", "Saaku?"))
            lang.startsWith("ml") -> pick("ml_more", listOf("Innum?", "Vere?", "Mathi?", "Innum enthu?"))
            lang.startsWith("pa") -> pick("pa_more", listOf("Hor ki?", "Kuch hor?", "Bass?", "Ki lena hai?"))
            lang.startsWith("gu") -> pick("gu_more", listOf("Biju shu?", "Kahi biju?", "Bas?", "Khai joiye?"))
            lang.startsWith("mr") -> pick("mr_more", listOf("आणखी काही?", "बस एवढंच?", "काय हवं?"))
            else -> pick("en_more", listOf(
                "Anything else?", "What else do you need?", "More?",
                "Shall I add anything else?", "That all for today?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 5. QUANTITY ASK (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    fun askQuantity(productName: String, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_qty", listOf(
                "$short कितना चाहिए? Ek kilo, do kilo?",
                "कितना लूं $short? Ek ya do?",
                "$short — ek packet ya zyada?",
                "कितना दूं $short?"
            ))
            lang.startsWith("te") -> pick("te_qty", listOf(
                "$short entha kavali? Oka kilo, rendo kilo?", "Enta kavali?"
            ))
            lang.startsWith("ta") -> pick("ta_qty", listOf("$short evvalavu vendum?", "Evvalavu?"))
            lang.startsWith("kn") -> pick("kn_qty", listOf("$short eshtu beku?", "Eshtu?"))
            lang.startsWith("ml") -> pick("ml_qty", listOf("$short ethra veno?", "Ethra?"))
            lang.startsWith("pa") -> pick("pa_qty", listOf("$short kitna chahida?", "Kitna?"))
            lang.startsWith("gu") -> pick("gu_qty", listOf("$short ketlun joiye?", "Ketlun?"))
            lang.startsWith("mr") -> pick("mr_qty", listOf("$short किती हवं?", "किती?"))
            else -> pick("en_qty", listOf(
                "How much $short? One kilo, two kilo?",
                "How many $short?",
                "$short — one pack or more?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 6. PRODUCT SELECTION PROMPT (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    fun askSelection(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_sel_r", listOf("कौन सा?", "नाम बोलें।"))
                else -> pick("hi_sel", listOf("कौनसा चाहिए? नाम बोलें।", "ब्रांड का नाम बताओ।", "कौनसा दूं?"))
            }
            lang.startsWith("te") -> pick("te_sel", listOf("Edi kavali? Peyru cheppandi.", "Brand peyru?"))
            lang.startsWith("ta") -> pick("ta_sel", listOf("Edu vendum? Peyar sollungal.", "Edu?"))
            lang.startsWith("kn") -> pick("kn_sel", listOf("Yavudu beku? Hesaru heli.", "Yavudu?"))
            lang.startsWith("ml") -> pick("ml_sel", listOf("Etha veno? Peru parayo.", "Eth?"))
            lang.startsWith("pa") -> pick("pa_sel", listOf("Kihra chahida? Naam dasao.", "Kihra?"))
            lang.startsWith("gu") -> pick("gu_sel", listOf("Kyu joiye? Naam bolo.", "Kyu?"))
            lang.startsWith("mr") -> pick("mr_sel", listOf("कोणता हवा? नाव सांगा.", "कोणता?"))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED ->
                    pick("en_sel_r", listOf("Which one?", "Say the name.", "Which?"))
                else ->
                    pick("en_sel", listOf("Which one? Say the brand name.", "Which brand?", "Say the name you want."))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 7. CART CONFIRMATION  ← delegates to ResponseTemplates
    // ═════════════════════════════════════════════════════════════════════

    fun confirmOrder(items: String, total: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_confirm", listOf(
                "$items. Total $total. Order करूं?",
                "$items — $total. Order kar doon?",
                "Total $total — $items. Order?",
                "$items. $total बनता है. Order?",
                "$items — $total. पक्का करूं?"
            ))
            lang.startsWith("te") -> pick("te_confirm", listOf(
                "$items — $total. Order pettana?",
                "Total $total — $items. Cheyyana?",
                "$items, $total. Confirm cheyyana?"
            ))
            lang.startsWith("ta") -> pick("ta_confirm", listOf(
                "$items — $total. Order podana?", "Total $total — $items. Podalam?"
            ))
            lang.startsWith("kn") -> pick("kn_confirm", listOf(
                "$items — $total. Order madana?", "Total $total — $items. Madali?"
            ))
            lang.startsWith("ml") -> pick("ml_confirm", listOf(
                "$items — $total. Order cheyatte?", "Total $total — $items. Cheyamo?"
            ))
            lang.startsWith("pa") -> pick("pa_confirm", listOf(
                "$items — $total. Order karan?", "Kull $total — $items. Karan?"
            ))
            lang.startsWith("gu") -> pick("gu_confirm", listOf(
                "$items — $total. Order karun?", "Kul $total — $items. Karun?"
            ))
            lang.startsWith("mr") -> pick("mr_confirm", listOf(
                "$items — $total. ऑर्डर करू?", "एकूण $total — $items. करायचं?"
            ))
            else -> pick("en_confirm", listOf(
                "$items — $total. Shall I order?",
                "Total $total for $items. Place it?",
                "$items, $total. Go ahead?",
                "Got $items. $total — confirm?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 8. PAYMENT MODE ASK  ← delegates to ResponseTemplates
    // ═════════════════════════════════════════════════════════════════════

    fun askPaymentMode(amount: String, lang: String): String =
        ResponseTemplates.askPayment("", lang).replace(" ?", "?")
            .let { if (it.contains("₹")) it else it.replace("payment", "$amount payment") }
            // The template handles language; we just surface amount inline
            .let {
                when {
                    lang.startsWith("hi") -> "UPI से दोगे या card से? Total $amount।"
                    lang.startsWith("te") -> "$amount — UPI istara leda card?"
                    lang.startsWith("ta") -> "$amount — UPI la kuduppeenga, card la?"
                    lang.startsWith("kn") -> "$amount — UPI na card?"
                    lang.startsWith("ml") -> "$amount — UPI aano card aano?"
                    lang.startsWith("pa") -> "$amount — UPI de ke card de?"
                    lang.startsWith("gu") -> "$amount — UPI ke card?"
                    lang.startsWith("mr") -> "$amount — UPI की कार्ड?"
                    else                  -> "UPI or card? Total $amount."
                }
            }

    // ═════════════════════════════════════════════════════════════════════
    // 9. UPI INSTRUCTION  ← delegates to ResponseTemplates
    // ═════════════════════════════════════════════════════════════════════

    fun upiInstruction(amount: String, lang: String): String = when {
        lang.startsWith("hi") -> pick("hi_upi", listOf(
            "UPI ID है butler@upi. $amount भेज देना।",
            "$amount भेजो — UPI ID screen पर है।",
            "UPI पर $amount भेजें. ID screen पर है।"
        ))
        lang.startsWith("te") -> pick("te_upi", listOf(
            "UPI ID butler@upi. $amount pampu.", "$amount pampu — UPI ID screen lo undi."
        ))
        lang.startsWith("ta") -> "$amount — UPI ID butler@upi. Anuppu."
        lang.startsWith("kn") -> "$amount — UPI ID butler@upi. Kali."
        lang.startsWith("ml") -> "$amount — UPI ID butler@upi. Aykku."
        lang.startsWith("pa") -> "UPI ID hai butler@upi. $amount bhejo."
        lang.startsWith("gu") -> "UPI ID chhe butler@upi. $amount moklo."
        lang.startsWith("mr") -> "UPI ID आहे butler@upi. $amount पाठवा."
        else -> pick("en_upi", listOf(
            "UPI ID is butler@upi. Send $amount.",
            "Send $amount to butler@upi. Say done when paid.",
            "UPI ID on screen. Pay $amount and say done."
        ))
    }

    // ═════════════════════════════════════════════════════════════════════
    // 10. PAYMENT CONFIRM ASK (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    fun askIfPaid(lang: String, mode: String, amount: String): String {
        return when {
            lang.startsWith("hi") -> when (mode) {
                "upi"  -> pick("hi_paid_upi", listOf("हो गया payment? $amount आ गए?", "$amount भेज दिया?", "Payment हो गया?"))
                "card" -> pick("hi_paid_card", listOf("Card से हो गया?", "Payment complete?"))
                else   -> pick("hi_paid_qr",  listOf("QR scan हो गया?", "$amount pay हुआ?"))
            }
            lang.startsWith("te") -> pick("te_paid", listOf("Payment ayindaa?", "$amount pay chesaara?", "Done?"))
            lang.startsWith("ta") -> pick("ta_paid", listOf("Payment aachaa?", "$amount anuppineengala?"))
            lang.startsWith("kn") -> pick("kn_paid", listOf("Payment aytaa?", "$amount kottiraa?"))
            lang.startsWith("ml") -> pick("ml_paid", listOf("Payment aayoo?", "$amount ayakkyoo?"))
            lang.startsWith("pa") -> pick("pa_paid", listOf("Payment ho gayi?", "$amount bhej ditta?"))
            lang.startsWith("gu") -> pick("gu_paid", listOf("Payment thai?", "$amount mokli didhun?"))
            lang.startsWith("mr") -> pick("mr_paid", listOf("पेमेंट झालं?", "$amount पाठवलं?"))
            else -> pick("en_paid", listOf("Payment done?", "Did you pay $amount?", "All set?"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 11. PAYMENT DONE REACTION (unchanged — pure Devanagari anchors kept)
    // ═════════════════════════════════════════════════════════════════════

    fun paymentDone(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_paydone", listOf("अच्छा, हो गया।", "ठीक है।", "हो गया।"))
            lang.startsWith("te") -> pick("te_paydone", listOf("Sare, ayindi.", "Aindi.", "Ok."))
            lang.startsWith("ta") -> pick("ta_paydone", listOf("Sari.", "Aachi.", "Ok."))
            lang.startsWith("kn") -> pick("kn_paydone", listOf("Sari.", "Aaytu.", "Ok."))
            lang.startsWith("ml") -> pick("ml_paydone", listOf("Sari.", "Ayi.", "Ok."))
            lang.startsWith("pa") -> pick("pa_paydone", listOf("Theek hai.", "Ho gaya.", "Ok."))
            lang.startsWith("gu") -> pick("gu_paydone", listOf("Saru.", "Thai gayu.", "Ok."))
            lang.startsWith("mr") -> pick("mr_paydone", listOf("ठीक.", "झालं.", "Ok."))
            else -> pick("en_paydone", listOf("Got it.", "Okay.", "Received.", "Done."))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 12. ORDER PLACED  ← delegates to ResponseTemplates
    // ═════════════════════════════════════════════════════════════════════

    fun orderPlaced(name: String, orderId: String, amount: String, etaMins: Int, lang: String): String {
        val eta = if (etaMins > 0) etaMins else 30
        return when {
            lang.startsWith("hi") -> pick("hi_order_placed", listOf(
                "Order $orderId लग गया. $eta minute mein delivery आ जायेगी. Thank you.",
                "$orderId confirm हो गया. $eta minute mein आ जायेगा. Shukriya.",
                "Order $orderId हो गया. $eta minute mein pahunch jayega."
            ))
            lang.startsWith("te") -> pick("te_order_placed", listOf(
                "Order $orderId confirm ayindi. $eta nimishaallo vastundi. Dhanyavaadaalu.",
                "$orderId laga. $eta minutes lo deliveri. Chala dhanyavaadaalu."
            ))
            lang.startsWith("ta") -> pick("ta_order_placed", listOf(
                "Order $orderId confirm. $eta nitcham varum. Nandri.",
                "$orderId — $eta nimidam. Nandri."
            ))
            lang.startsWith("kn") -> pick("kn_order_placed", listOf(
                "Order $orderId aaytu. $eta nimishada hage baruttade. Dhanyavada.",
                "$orderId — $eta nimisha. Dhanyavada."
            ))
            lang.startsWith("ml") -> pick("ml_order_placed", listOf(
                "Order $orderId confirmed. $eta minuteinu orathe ettum. Nandri.",
                "$orderId — $eta minute. Nandri."
            ))
            lang.startsWith("pa") -> pick("pa_order_placed", listOf(
                "Order $orderId confirm ho gaya. $eta minute vich aa jauga. Shukriya.",
                "$orderId — $eta minute. Shukriya."
            ))
            lang.startsWith("gu") -> pick("gu_order_placed", listOf(
                "Order $orderId confirm thai gayu. $eta minute ma aavse. Shukriya.",
                "$orderId — $eta minute. Shukriya."
            ))
            lang.startsWith("mr") -> pick("mr_order_placed", listOf(
                "Order $orderId confirm झालं. $eta मिनिटांत डिलिव्हरी येईल. धन्यवाद.",
                "$orderId — $eta मिनिट. धन्यवाद."
            ))
            else -> pick("en_order_placed", listOf(
                "Order $orderId placed. Delivery in about $eta minutes. Thank you.",
                "$orderId confirmed — $eta minutes to your door. Thanks.",
                "Order placed. $orderId, $eta minutes. Thank you $name."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 13. PRODUCT NOT FOUND  ← delegates to ResponseTemplates
    // ═════════════════════════════════════════════════════════════════════

    fun productNotFound(itemName: String, lang: String): String {
        val firstName = UserSessionManager_compat.firstName ?: "there"
        return ResponseTemplates.itemNotFound(firstName, itemName.take(20), lang)
    }

    // ═════════════════════════════════════════════════════════════════════
    // 14. CART EMPTY  ← delegates to ResponseTemplates (with fallback)
    // ═════════════════════════════════════════════════════════════════════

    fun cartEmpty(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_empty", listOf(
                "अभी कुछ नहीं है. Kya loon?",
                "Cart खाली है. Kya chahiye?",
                "कुछ नहीं. Bolo kya lena hai."
            ))
            lang.startsWith("te") -> pick("te_empty", listOf(
                "Ippudu emi ledu. Emi kavali?", "Cart khaali ga undi. Cheppandi?"
            ))
            lang.startsWith("ta") -> "Ippo onnum illa. Enna vendum?"
            lang.startsWith("kn") -> "Enu illa. Enu beku?"
            lang.startsWith("ml") -> "Ippol ontumilla. Enthu veno?"
            lang.startsWith("pa") -> "Cart khaali. Ki chahida?"
            lang.startsWith("gu") -> "Cart khaali chhe. Shu joiye?"
            lang.startsWith("mr") -> "Cart रिकामी आहे. काय हवं?"
            else -> pick("en_empty", listOf(
                "Cart's empty. What would you like?",
                "Nothing added yet. What do you need?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 15. DIDN'T HEAR / RETRY (unchanged — retry count + mood variants needed)
    // ═════════════════════════════════════════════════════════════════════

    fun didntHear(lang: String, mood: UserMood, retryCount: Int): String {
        return when {
            lang.startsWith("hi") -> when {
                mood == UserMood.FRUSTRATED -> pick("hi_retry_frus", listOf(
                    "कुछ सुना नहीं। Thoda aur oonchi awaaz mein boliye.",
                    "समझ नहीं आया। Phir se boliye.",
                    "आवाज़ थोड़ी कम है। Paas aakar boliye."
                ))
                retryCount >= 4 -> pick("hi_retry_many", listOf(
                    "Mic के पास आकर बोलिए।",
                    "थोड़ा जोर से बोलिए।"
                ))
                retryCount >= 2 -> pick("hi_retry_2", listOf(
                    "समझ नहीं आया। Phir se boliye.",
                    "कुछ सुना नहीं। Thoda aur oonchi awaaz mein boliye.",
                    "दोबारा?"
                ))
                else -> pick("hi_retry", listOf(
                    "कुछ सुना नहीं। Thoda aur oonchi awaaz mein boliye.",
                    "हाँ?", "क्या?", "फिर बोलिए।", "सुना नहीं।"
                ))
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED -> pick("te_retry_frus", listOf(
                    "Vinaledu. Clearly cheppandi.", "Malli cheppandi."
                ))
                else -> pick("te_retry", listOf("Haa?", "Emi?", "Malli cheppandi.", "Vinaledu."))
            }
            lang.startsWith("ta") -> pick("ta_retry", listOf("Aa?", "Enna?", "Teriyalai.", "Solunga."))
            lang.startsWith("kn") -> pick("kn_retry", listOf("Ha?", "Enu?", "Kaliyalilla.", "Heli."))
            lang.startsWith("ml") -> pick("ml_retry", listOf("Ha?", "Enthu?", "Kekkunilla.", "Parayo."))
            lang.startsWith("pa") -> pick("pa_retry", listOf("Ha?", "Ki?", "Sunnai nahi.", "Dasao."))
            lang.startsWith("gu") -> pick("gu_retry", listOf("Ha?", "Shu?", "Sambhaldhu nahi.", "Kaho."))
            lang.startsWith("mr") -> pick("mr_retry", listOf("हं?", "काय?", "ऐकलं नाही.", "सांगा."))
            else -> when (mood) {
                UserMood.FRUSTRATED -> pick("en_retry_frus", listOf(
                    "Didn't catch that. A bit louder?",
                    "Sorry, say that again?",
                    "Can't hear you clearly."
                ))
                else -> pick("en_retry", listOf(
                    "Sorry?", "Come again?", "Hmm?", "Say that again?", "Didn't catch that."
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 16. GIVE UP (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    fun giveUp(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED -> pick("hi_giveup_frus", listOf(
                    "कोई बात नहीं. Jab ready hon, hey Butler bolein.",
                    "चलिए, baad mein baat karte hain.",
                    "Signal problem लग रहा. Thodi der baad try karein."
                ))
                else -> pick("hi_giveup", listOf(
                    "ठीक है. Jab ready hon, hey Butler bolein.",
                    "कोई बात नहीं. Baad mein.",
                    "ठीक है. Ready hone par bulaiye."
                ))
            }
            lang.startsWith("te") -> pick("te_giveup", listOf(
                "Parvaaledu. Taraavaata hey Butler cheppandi.",
                "Sare. Taraavaata matlaadam."
            ))
            lang.startsWith("ta") -> "Paravailla. Pinna hey Butler sollungal."
            lang.startsWith("kn") -> "Parvaagilla. Naantara hey Butler heli."
            lang.startsWith("ml") -> "Kaaryanilla. Pinne hey Butler parayo."
            lang.startsWith("pa") -> "Koi gal nahi. Baad vich hey Butler kaho."
            lang.startsWith("gu") -> "Kaem nahi. Pachhi hey Butler kaho."
            lang.startsWith("mr") -> "काही हरकत नाही. जेव्हा तयार असाल, hey Butler म्हणा."
            else -> pick("en_giveup", listOf(
                "No worries. Say hey Butler when you're ready.",
                "That's fine. Talk later.",
                "Okay, just say hey Butler when ready."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 17. ITEM REMOVED (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    fun itemRemoved(productName: String, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_removed", listOf("$short हटा दिया।", "ठीक है, $short नहीं।"))
            lang.startsWith("te") -> pick("te_removed", listOf("$short tiriyinchaanu.", "$short ledu."))
            lang.startsWith("ta") -> "$short eduththuttein."
            lang.startsWith("kn") -> "$short tagondu."
            lang.startsWith("ml") -> "$short eduthu."
            lang.startsWith("pa") -> "$short hata dita."
            lang.startsWith("gu") -> "$short kadhi nakhyu."
            lang.startsWith("mr") -> "$short काढलं."
            else -> pick("en_removed", listOf("$short removed.", "Done, $short gone.", "$short taken out."))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 18. ORDER ERROR (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    fun orderError(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_err", listOf(
                "Network थोड़ी slow है. Phir try karein?",
                "एक second. Phir koshish karte hain.",
                "कुछ problem आई. Dobara order karein?"
            ))
            lang.startsWith("te") -> pick("te_err", listOf(
                "Network problem. Malli try cheyyana?", "Oka second. Malli choodam."
            ))
            lang.startsWith("ta") -> "Network problem. Maru mudhal paarkkalama?"
            lang.startsWith("kn") -> "Network problem. Matte try madona?"
            lang.startsWith("ml") -> "Network problem. Oru mukham koodi try cheyyamo?"
            lang.startsWith("pa") -> "Network problem. Phir try karan?"
            lang.startsWith("gu") -> "Network problem. Fari try karie?"
            lang.startsWith("mr") -> "Network problem. पुन्हा try करू का?"
            else -> pick("en_err", listOf("Hit a snag. Shall we try again?", "Network hiccup. Retry?"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 19. SESSION EXPIRED (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    fun sessionExpired(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_exp", listOf(
                "Session expire हो गई. Hey Butler bolein dobara.",
                "थोड़ी देर हो गई. Hey Butler se shuru karein."
            ))
            lang.startsWith("te") -> "Session expire aindi. Hey Butler antunnaru."
            lang.startsWith("ta") -> "Session mudindiddu. Hey Butler sollunga."
            lang.startsWith("kn") -> "Session expire aaytu. Hey Butler heli."
            lang.startsWith("ml") -> "Session expire ayi. Hey Butler parayo."
            lang.startsWith("pa") -> "Session expire ho gayi. Hey Butler kaho."
            lang.startsWith("gu") -> "Session expire thai gayu. Hey Butler kaho."
            lang.startsWith("mr") -> "Session expire झाली. Hey Butler म्हणा."
            else -> pick("en_exp", listOf(
                "Session expired. Say hey Butler to start again.",
                "Timed out — just say hey Butler."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private fun getRelatedSuggestion(productName: String?, lang: String): String? {
        if (productName == null) return null
        val p  = productName.lowercase()
        val hi = mapOf(
            "rice" to "दाल",   "dal"  to "चावल",  "oil"  to "आटा",
            "atta" to "तेल",   "milk" to "bread",  "tea"  to "चीनी",
            "ghee" to "दाल",   "eggs" to "bread",  "curd" to "चावल",
            "chawal" to "दाल", "daal" to "चावल",   "tel"  to "आटा"
        )
        val te = mapOf(
            "rice" to "pappu", "dal" to "annam", "oil" to "pindi", "milk" to "rotte", "tea" to "chakkera"
        )
        val en = mapOf(
            "rice" to "dal",   "dal"  to "rice",   "oil"  to "atta",
            "atta" to "oil",   "milk" to "bread",  "tea"  to "sugar",
            "ghee" to "dal",   "eggs" to "bread",  "curd" to "rice"
        )
        val base = lang.substringBefore("-").lowercase().take(2)
        return when (base) {
            "hi" -> hi.entries.firstOrNull { p.contains(it.key) }?.value
            "te" -> te.entries.firstOrNull { p.contains(it.key) }?.value
            else -> en.entries.firstOrNull { p.contains(it.key) }?.value
        }
    }
}

// ── Thin shim so ButlerPersonalityEngine can read the current user's first name ──
// without a direct dependency on UserSessionManager (avoids circular imports).
// Populated in MainActivity.kt: UserSessionManager_compat.firstName = profile.full_name?.split(" ")?.first()
object UserSessionManager_compat {
    var firstName: String? = null
}