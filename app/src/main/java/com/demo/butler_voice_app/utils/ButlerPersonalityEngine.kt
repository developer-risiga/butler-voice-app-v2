package com.demo.butler_voice_app.utils

import com.demo.butler_voice_app.ai.UserMood
import java.util.Calendar

/**
 * ButlerPersonalityEngine — every word Butler speaks, humanised.
 *
 * VOICE DESIGN PRINCIPLE (from demo feedback):
 *   Natural > Enthusiastic. Clean > Expressive.
 *   Butler sounds like a helpful, unhurried kirana assistant —
 *   not a cheerful chatbot. No "Badhiya!", no "Perfect!", no "Welcome back!".
 *   Short sentences. Active verbs. No mixed script.
 *
 * COMPLETE REPLACEMENT MAP (Hindi demo flow):
 *   reorderGreeting  : "Roy, wahi chahiye kya — Daawat Brown, ...?"
 *   itemAdded (1)    : "Daawat Brown rice dal diya."
 *   itemAdded (2)    : "24 Mantra daal dal diya."
 *   askMore sug      : "Daal bhi saath mein du?"
 *   askMore plain    : "Bas itna? / Kuch aur?"
 *   didntHear (1)    : "Kuch suna nahi. Thoda aur oonchi awaaz mein boliye."
 *   didntHear (2)    : "Samajh nahi aaya. Phir se boliye."
 *   confirmOrder     : "Ek Daawat Brown, ek 24 Mantra. Total ₹90. Order kar doon?"
 *   askPaymentMode   : "UPI se doge ya card se?"
 *   upiInstruction   : "UPI ID hai butler@upi. $amount bhej dena."
 *   askIfPaid (UPI)  : "Ho gaya payment? $amount aa gaye?"
 *   paymentDone      : "Acha, ho gaya."
 *   orderPlaced      : "Order BUT-000085 lag gaya. 30 minute mein delivery aa jayegi. Thank you."
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
    // 1. GREETING
    // ═════════════════════════════════════════════════════════════════════

    fun greeting(name: String, lang: String, lastProduct: String?, mood: UserMood): String {
        val time = timeSlot()
        return when {
            lang.startsWith("hi") -> when {
                lastProduct != null -> pick("hi_greet_ret", listOf(
                    "$name, $lastProduct chahiye kya?",
                    "$name, pichli baar $lastProduct liya tha. Wahi doon?",
                    "$name, $lastProduct fir se?",
                    "$name ji, kya chahiye aaj?",
                    "$name, bolo kya laana hai."
                ))
                time == "morning" -> pick("hi_greet_morn", listOf(
                    "$name ji, subah ki zaroorat batao.",
                    "$name, subah mein kya chahiye?",
                    "$name ji, kya laana hai?"
                ))
                time == "evening" -> pick("hi_greet_eve", listOf(
                    "$name ji, shaam mein kya chahiye?",
                    "$name, kya mangwaaein?",
                    "$name ji, bolo kya laana hai."
                ))
                else -> pick("hi_greet_new", listOf(
                    "$name ji, kya chahiye?",
                    "$name, bolo kya laana hai.",
                    "$name ji, kya mangwaaein?",
                    "$name, haan boliye."
                ))
            }
            lang.startsWith("te") -> when {
                lastProduct != null -> pick("te_greet_ret", listOf(
                    "$name garu! $lastProduct meeru tecchukovadam? Ledanta?",
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
    // 2. REORDER SUGGESTION
    //
    // CHANGE: Remove "Welcome back", "Arrey", "Pichli baar X mangaya tha"
    // preamble. Just ask directly: "$name, wahi chahiye kya — $items?"
    // ═════════════════════════════════════════════════════════════════════

    fun reorderGreeting(name: String, items: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_reorder", listOf(
                "$name, wahi chahiye kya — $items?",
                "$name, $items fir se du?",
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
            else -> pick("en_reorder", listOf(
                "$name, same as last time — $items?",
                "Hey $name! $items again?",
                "$name, want the usual — $items?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 3. ITEM ADDED CONFIRMATION
    //
    // CHANGE: "Perfect! $full le liya / cart mein aa gaya"
    //      → "$full dal diya."
    //
    // "dal diya" = "put it in / added it" — active, clear, short.
    // $full = brand + category ("Daawat Brown rice", "24 Mantra daal")
    // $short = brand only ("Daawat Brown") — used when speed matters
    // ═════════════════════════════════════════════════════════════════════

    fun itemAdded(productName: String, lang: String, mood: UserMood, cartSize: Int): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val full = productName.split(" ").take(3)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_added_r", listOf(
                    "Theek.", "$short — ho gaya.", "Dal diya.", "Rakh diya."
                ))
                else -> when (cartSize) {
                    1 -> pick("hi_added_1", listOf(
                        "$full dal diya.",
                        "$full rakh diya.",
                        "$full — ho gaya.",
                        "$full le liya."
                    ))
                    2 -> pick("hi_added_2", listOf(
                        "$full dal diya.",
                        "$full bhi rakh diya.",
                        "$full bhi ho gaya."
                    ))
                    else -> pick("hi_added_n", listOf(
                        "$short bhi.", "$full — ho gaya.", "Dal diya."
                    ))
                }
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("te_added_r", listOf(
                    "Sare.", "$short ayindi.", "Done."
                ))
                else -> pick("te_added", listOf(
                    "$full teecchukonnaanu.",
                    "$full perfect choice!",
                    "Ha! $full.",
                    "Sare, $full."
                ))
            }
            lang.startsWith("ta") -> pick("ta_added", listOf(
                "$full vaanginein.", "$full aachi.", "Ok $full."
            ))
            lang.startsWith("kn") -> pick("kn_added", listOf(
                "$full tagondu.", "$full aaytu.", "Ok $full."
            ))
            lang.startsWith("ml") -> pick("ml_added", listOf(
                "$full eduthu.", "$full ayi.", "Ok $full."
            ))
            lang.startsWith("pa") -> pick("pa_added", listOf(
                "$full rakh dita.", "$full aa gaya.", "Ok $full."
            ))
            lang.startsWith("gu") -> pick("gu_added", listOf(
                "$full cart ma.", "$full thai gayu.", "Ok $full."
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_added_r", listOf(
                    "Done.", "$short added.", "Got it.", "Okay."
                ))
                else -> when (cartSize) {
                    1 -> pick("en_added_1", listOf(
                        "$full added.",
                        "Got it — $full.",
                        "$full — done."
                    ))
                    2 -> pick("en_added_2", listOf(
                        "$full too.", "$full added as well.", "$full — done."
                    ))
                    else -> pick("en_added_n", listOf(
                        "$short too.", "Added.", "Done."
                    ))
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 4. ASK MORE
    //
    // CHANGE: suggestion string from "daal chahiye saath mein?"
    //      → "Daal bhi saath mein du?"
    // "du?" = "shall I give?" — natural kirana counter phrasing
    // ═════════════════════════════════════════════════════════════════════

    fun askMore(lang: String, mood: UserMood, cartSize: Int, lastProduct: String?): String {
        val suggestion = getRelatedSuggestion(lastProduct, lang)
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_more_r", listOf(
                    "Aur?", "Kuch aur?", "Bas?", "Kya chahiye?"
                ))
                else -> if (suggestion != null && cartSize == 1) {
                    pick("hi_more_sug", listOf(
                        "$suggestion bhi saath mein du?",
                        "$suggestion bhi chahiye?",
                        "Aur $suggestion?"
                    ))
                } else {
                    pick("hi_more", listOf(
                        "Bas itna?",
                        "Kuch aur?",
                        "Aur kya chahiye?",
                        "Kuch aur lena hai?",
                        "Kuch aur mangwaaoon?"
                    ))
                }
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("te_more_r", listOf(
                    "Inkaa?", "Maro?", "Antena?"
                ))
                else -> pick("te_more", listOf(
                    "Inkaa emi kavali?", "Marokkati?", "Inka emi?", "Chaaladaa?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_more", listOf(
                "Vera enna?", "Inniku?", "Vera?", "Porum?"
            ))
            lang.startsWith("kn") -> pick("kn_more", listOf(
                "Inenu?", "Bere?", "Innu?", "Saaku?"
            ))
            lang.startsWith("ml") -> pick("ml_more", listOf(
                "Innum?", "Vere?", "Mathi?", "Innum enthu?"
            ))
            lang.startsWith("pa") -> pick("pa_more", listOf(
                "Hor ki?", "Kuch hor?", "Bass?", "Ki lena hai?"
            ))
            lang.startsWith("gu") -> pick("gu_more", listOf(
                "Biju shu?", "Kahi biju?", "Bas?", "Khai joiye?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_more_r", listOf(
                    "More?", "Anything else?", "That it?"
                ))
                else -> pick("en_more", listOf(
                    "Anything else?",
                    "What else do you need?",
                    "More?",
                    "Shall I add anything else?",
                    "That all for today?"
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 5. QUANTITY ASK
    // ═════════════════════════════════════════════════════════════════════

    fun askQuantity(productName: String, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_qty", listOf(
                "$short kitna chahiye? Ek kilo, do kilo?",
                "Kitna loon $short? Ek ya do?",
                "$short — ek packet ya zyada?",
                "Kitna du $short?"
            ))
            lang.startsWith("te") -> pick("te_qty", listOf(
                "$short entha kavali? Oka kilo, rendo kilo?", "Enta kavali?"
            ))
            lang.startsWith("ta") -> pick("ta_qty", listOf(
                "$short evvalavu vendum?", "Evvalavu?"
            ))
            lang.startsWith("kn") -> pick("kn_qty", listOf(
                "$short eshtu beku?", "Eshtu?"
            ))
            lang.startsWith("ml") -> pick("ml_qty", listOf(
                "$short ethra veno?", "Ethra?"
            ))
            lang.startsWith("pa") -> pick("pa_qty", listOf(
                "$short kitna chahida?", "Kitna?"
            ))
            lang.startsWith("gu") -> pick("gu_qty", listOf(
                "$short ketlun joiye?", "Ketlun?"
            ))
            else -> pick("en_qty", listOf(
                "How much $short? One kilo, two kilo?",
                "How many $short?",
                "$short — one pack or more?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 6. PRODUCT SELECTION PROMPT
    // ═════════════════════════════════════════════════════════════════════

    fun askSelection(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_sel_r", listOf(
                    "Kaun sa?", "Naam bolein.", "Kaunsa?"
                ))
                else -> pick("hi_sel", listOf(
                    "Kaunsa chahiye? Naam bolein.",
                    "Brand ka naam batao.",
                    "Kaunsa du?",
                    "Naam bolein.",
                    "Pasand karein."
                ))
            }
            lang.startsWith("te") -> pick("te_sel", listOf(
                "Edi kavali? Peyru cheppandi.", "Brand peyru?", "Edi?"
            ))
            lang.startsWith("ta") -> pick("ta_sel", listOf(
                "Edu vendum? Peyar sollungal.", "Edu?"
            ))
            lang.startsWith("kn") -> pick("kn_sel", listOf(
                "Yavudu beku? Hesaru heli.", "Yavudu?"
            ))
            lang.startsWith("ml") -> pick("ml_sel", listOf(
                "Etha veno? Peru parayo.", "Eth?"
            ))
            lang.startsWith("pa") -> pick("pa_sel", listOf(
                "Kihra chahida? Naam dasao.", "Kihra?"
            ))
            lang.startsWith("gu") -> pick("gu_sel", listOf(
                "Kyu joiye? Naam bolo.", "Kyu?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_sel_r", listOf(
                    "Which one?", "Say the name.", "Which?"
                ))
                else -> pick("en_sel", listOf(
                    "Which one? Say the brand name.",
                    "Which brand?",
                    "Say the name you want.",
                    "Which do you prefer?"
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 7. CART CONFIRMATION
    //
    // CHANGE: "Theek hai — 1 Daawat Brown, 1 24 Mantra — 90 rupees. Lagaoon?"
    //      → "Ek Daawat Brown, ek 24 Mantra. Total ₹90. Order kar doon?"
    //
    // "Order kar doon?" = "Shall I place the order?" — professional, clear.
    // No "Lagaoon?" (slap connotation), no "Chalte hain —" filler.
    // ═════════════════════════════════════════════════════════════════════

    fun confirmOrder(items: String, total: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_confirm", listOf(
                "$items. Total $total. Order kar doon?",
                "$items — $total. Order karoon?",
                "Total $total — $items. Order kar doon?",
                "$items. $total banta hai. Order?",
                "$items — $total. Pakka karoon?"
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
            else -> pick("en_confirm", listOf(
                "$items — $total. Shall I order?",
                "Total $total for $items. Place it?",
                "$items, $total. Go ahead?",
                "Got $items. $total — confirm?",
                "$items, $total. Order?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 8. PAYMENT MODE ASK
    //
    // CHANGE: "Payment kaise karoge? UPI, card, ya QR scan?"
    //      → "UPI se doge ya card se?"
    //
    // Short, direct. Real kirana language. No "Payment kaise karoge"
    // ═════════════════════════════════════════════════════════════════════

    fun askPaymentMode(amount: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_pay_ask", listOf(
                "UPI se doge ya card se?",
                "$amount — UPI ya card?",
                "UPI, card, ya QR — kaise doge?",
                "Kaise dena hai — UPI ya card?"
            ))
            lang.startsWith("te") -> pick("te_pay_ask", listOf(
                "UPI istara leda card?",
                "$amount ela istara? UPI ya card?"
            ))
            lang.startsWith("ta") -> pick("ta_pay_ask", listOf(
                "UPI la kuduppeenga, card la?", "Epdi pay pannuvenga?"
            ))
            lang.startsWith("kn") -> pick("kn_pay_ask", listOf(
                "UPI na card — hege pay madali?", "Hege pay madali?"
            ))
            lang.startsWith("ml") -> pick("ml_pay_ask", listOf(
                "UPI aano card aano?", "Engane pay cheyyam?"
            ))
            lang.startsWith("pa") -> pick("pa_pay_ask", listOf(
                "UPI de ke card de?", "Kiven paisa denaa?"
            ))
            lang.startsWith("gu") -> pick("gu_pay_ask", listOf(
                "UPI ke card?", "Kem pay karishu?"
            ))
            else -> pick("en_pay_ask", listOf(
                "UPI or card?",
                "$amount — how do you want to pay? UPI or card?",
                "UPI, card, or QR?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 9. UPI INSTRUCTION
    //
    // CHANGE: "UPI ID screen pe dikh raha hai..."
    //      → "UPI ID hai butler@upi. $amount bhej dena."
    //
    // User-specified exact phrasing. Clear and direct.
    // ═════════════════════════════════════════════════════════════════════

    fun upiInstruction(amount: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_upi", listOf(
                "UPI ID hai butler@upi. $amount bhej dena.",
                "$amount bhejo — UPI ID screen pe dikh raha hai.",
                "UPI pe $amount bhejein. ID screen pe hai.",
                "Screen pe UPI ID dekh ke $amount bhejo."
            ))
            lang.startsWith("te") -> pick("te_upi", listOf(
                "UPI ID butler@upi. $amount pampu.",
                "$amount pampu — UPI ID screen lo undi."
            ))
            lang.startsWith("ta") -> pick("ta_upi", listOf(
                "UPI ID butler@upi. $amount anuppu.",
                "$amount anuppu — UPI ID screen-la irukku."
            ))
            lang.startsWith("kn") -> pick("kn_upi", listOf(
                "UPI ID butler@upi. $amount kali.",
                "$amount kali — UPI ID screen mele ide."
            ))
            lang.startsWith("ml") -> pick("ml_upi", listOf(
                "UPI ID butler@upi. $amount aykku.",
                "$amount aykku — UPI ID screen-il kaanam."
            ))
            lang.startsWith("pa") -> pick("pa_upi", listOf(
                "UPI ID hai butler@upi. $amount bhejo.",
                "$amount bhejo — UPI ID screen te hai."
            ))
            lang.startsWith("gu") -> pick("gu_upi", listOf(
                "UPI ID chhe butler@upi. $amount moklo.",
                "$amount moklo — UPI ID screen par chhe."
            ))
            else -> pick("en_upi", listOf(
                "UPI ID is butler@upi. Send $amount.",
                "Send $amount to butler@upi. Let me know when done.",
                "UPI ID on screen. Pay $amount and say done."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 10. PAYMENT CONFIRMATION ASK
    //
    // CHANGE: "UPI ho gaya? nabbe rupaye pahuncha?"
    //      → "Ho gaya payment? $amount aa gaye?"
    // ═════════════════════════════════════════════════════════════════════

    fun askIfPaid(lang: String, mode: String, amount: String): String {
        return when {
            lang.startsWith("hi") -> when (mode) {
                "upi"  -> pick("hi_paid_upi", listOf(
                    "Ho gaya payment? $amount aa gaye?",
                    "$amount bhej diya?",
                    "Payment ho gaya?",
                    "UPI ho gaya?"
                ))
                "card" -> pick("hi_paid_card", listOf(
                    "Card se ho gaya?", "Payment complete?", "Card done?"
                ))
                else   -> pick("hi_paid_qr", listOf(
                    "QR scan ho gaya?", "$amount pay hua?", "Ho gaya?"
                ))
            }
            lang.startsWith("te") -> pick("te_paid", listOf(
                "Payment ayindaa?", "$amount pay chesaara?", "Done?"
            ))
            lang.startsWith("ta") -> pick("ta_paid", listOf(
                "Payment aachaa?", "$amount anuppineengala?", "Done?"
            ))
            lang.startsWith("kn") -> pick("kn_paid", listOf(
                "Payment aytaa?", "$amount kottiraa?", "Done?"
            ))
            lang.startsWith("ml") -> pick("ml_paid", listOf(
                "Payment aayoo?", "$amount ayakkyoo?", "Done?"
            ))
            lang.startsWith("pa") -> pick("pa_paid", listOf(
                "Payment ho gayi?", "$amount bhej ditta?", "Done?"
            ))
            lang.startsWith("gu") -> pick("gu_paid", listOf(
                "Payment thai?", "$amount mokli didhun?", "Done?"
            ))
            else -> pick("en_paid", listOf(
                "Payment done?", "Did you pay $amount?", "All set?", "Through?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 11. PAYMENT DONE REACTION
    //
    // CHANGE: "Perfect!", "Shukriya!", "Badhiya!" (too cheerful)
    //      → "Acha, ho gaya." (calm, professional, natural)
    // ═════════════════════════════════════════════════════════════════════

    fun paymentDone(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_paydone", listOf(
                "Acha, ho gaya.",
                "Theek hai.",
                "Ho gaya."
            ))
            lang.startsWith("te") -> pick("te_paydone", listOf(
                "Sare, ayindi.", "Aindi.", "Ok."
            ))
            lang.startsWith("ta") -> pick("ta_paydone", listOf("Sari.", "Aachi.", "Ok."))
            lang.startsWith("kn") -> pick("kn_paydone", listOf("Sari.", "Aaytu.", "Ok."))
            lang.startsWith("ml") -> pick("ml_paydone", listOf("Sari.", "Ayi.", "Ok."))
            lang.startsWith("pa") -> pick("pa_paydone", listOf("Theek hai.", "Ho gaya.", "Ok."))
            lang.startsWith("gu") -> pick("gu_paydone", listOf("Saru.", "Thai gayu.", "Ok."))
            else -> pick("en_paydone", listOf("Got it.", "Okay.", "Received.", "Done."))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 12. ORDER PLACED
    //
    // CHANGE: "बढ़िया Roy! Order BUT-000085 laga. 30 minute mein delivery. Shukriya!"
    //      → "Order BUT-000085 lag gaya. 30 minute mein delivery aa jayegi. Thank you."
    //
    // Cleaner. No "Badhiya", no name, professional tone.
    // Contains Devanagari → TranslationManager skips, no mixed script.
    // ═════════════════════════════════════════════════════════════════════

    fun orderPlaced(name: String, orderId: String, amount: String, etaMins: Int, lang: String): String {
        val eta = if (etaMins > 0) etaMins else 30
        return when {
            lang.startsWith("hi") -> pick("hi_order_placed", listOf(
                "Order $orderId lag गया. $eta minute mein delivery aa jayegi. Thank you.",
                "$orderId confirm हो गया. $eta minute mein aa jayega. Shukriya.",
                "Order $orderId हो गया. $eta minute mein pahunch jayega.",
                "हो गया. Order $orderId — $eta minute mein delivery. Thank you.",
                "$orderId laga. $eta minute mein आ जायेगा. Dhanyavaad."
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
            else -> pick("en_order_placed", listOf(
                "Order $orderId placed. Delivery in about $eta minutes. Thank you.",
                "$orderId confirmed — $eta minutes to your door. Thanks.",
                "Order placed. $orderId, $eta minutes. Thank you $name."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 13. PRODUCT NOT FOUND
    // ═════════════════════════════════════════════════════════════════════

    fun productNotFound(itemName: String, lang: String): String {
        val short = itemName.take(20)
        return when {
            lang.startsWith("hi") -> pick("hi_notfound", listOf(
                "Woh abhi nahi hai. Kuch aur?",
                "$short nahi mila. Koi aur brand?",
                "$short stock mein nahi. Kya loon phir?",
                "$short nahi hai. Kuch aur?"
            ))
            lang.startsWith("te") -> pick("te_notfound", listOf(
                "Adi ippudu ledu. Inkaa emi?", "$short dorakaledu. Vera emi?"
            ))
            lang.startsWith("ta") -> pick("ta_notfound", listOf(
                "Adu illai. Vera enna?", "$short kidaikala. Innum?"
            ))
            lang.startsWith("kn") -> pick("kn_notfound", listOf(
                "Adu illa. Bere enu?", "$short sigalilla. Inenu?"
            ))
            lang.startsWith("ml") -> pick("ml_notfound", listOf(
                "Athu illa. Vere enthu?", "$short kittyilla. Innum?"
            ))
            lang.startsWith("pa") -> pick("pa_notfound", listOf(
                "Oh available nahi. Hor ki?", "$short nahi mila. Kuch hor?"
            ))
            lang.startsWith("gu") -> pick("gu_notfound", listOf(
                "Te available nathi. Biju shu?", "$short na maddyo. Kahi biju?"
            ))
            else -> pick("en_notfound", listOf(
                "That's not available. Anything else?",
                "$short not in stock. Want something else?",
                "Couldn't find $short. What else?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 14. CART EMPTY
    // ═════════════════════════════════════════════════════════════════════

    fun cartEmpty(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_empty", listOf(
                "Abhi kuch nahi hai. Kya loon?",
                "Cart khaali hai. Kya chahiye?",
                "Kuch add nahi hua. Kya mangwaaein?",
                "Kuch nahi. Bolo kya lena hai."
            ))
            lang.startsWith("te") -> pick("te_empty", listOf(
                "Ippudu emi ledu. Emi kavali?", "Cart khaali ga undi. Cheppandi?"
            ))
            lang.startsWith("ta") -> pick("ta_empty", listOf(
                "Ippo onnum illa. Enna vendum?", "Cart kaali. Enna?"
            ))
            lang.startsWith("kn") -> pick("kn_empty", listOf(
                "Enu illa. Enu beku?", "Cart khaali. Enu?"
            ))
            lang.startsWith("ml") -> pick("ml_empty", listOf(
                "Ippol ontumilla. Enthu veno?", "Cart khaali. Enthu?"
            ))
            lang.startsWith("pa") -> pick("pa_empty", listOf(
                "Hor kuch nahi. Ki chahida?", "Cart khaali. Ki?"
            ))
            lang.startsWith("gu") -> pick("gu_empty", listOf(
                "Hu khaali chhe. Shu joiye?", "Cart khaali. Shu?"
            ))
            else -> pick("en_empty", listOf(
                "Cart's empty. What would you like?",
                "Nothing added yet. What do you need?",
                "Shall we start? What do you need?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 15. DIDN'T HEAR / RETRY
    //
    // CHANGE (1st retry): "Sorry, ek baar aur? Thoda jor se."
    //      → "Kuch suna nahi. Thoda aur oonchi awaaz mein boliye."
    //
    // CHANGE (2nd retry): "Fir bolein."
    //      → "Samajh nahi aaya. Phir se boliye."
    //
    // "boliye" = polite imperative. "bolein" = slightly formal.
    // ═════════════════════════════════════════════════════════════════════

    fun didntHear(lang: String, mood: UserMood, retryCount: Int): String {
        return when {
            lang.startsWith("hi") -> when {
                mood == UserMood.FRUSTRATED -> pick("hi_retry_frus", listOf(
                    "Kuch suna nahi. Thoda aur oonchi awaaz mein boliye.",
                    "Samajh nahi aaya. Phir se boliye.",
                    "Awaaz thodi kam hai. Paas aakar boliye."
                ))
                retryCount >= 4 -> pick("hi_retry_many", listOf(
                    "Mic ke paas aakar boliye.",
                    "Thoda jor se boliye.",
                    "Signal weak lag raha. Ek baar aur?"
                ))
                retryCount == 3 -> pick("hi_retry_3", listOf(
                    "Samajh nahi aaya. Phir se boliye.",
                    "Fir se?",
                    "Dobara boliye?"
                ))
                retryCount == 2 -> pick("hi_retry_2", listOf(
                    "Samajh nahi aaya. Phir se boliye.",
                    "Kuch suna nahi. Thoda aur oonchi awaaz mein boliye.",
                    "Dobara?"
                ))
                else -> pick("hi_retry", listOf(
                    "Kuch suna nahi. Thoda aur oonchi awaaz mein boliye.",
                    "Haan?",
                    "Kya?",
                    "Fir boliye.",
                    "Suna nahi."
                ))
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED -> pick("te_retry_frus", listOf(
                    "Vinaledu. Clearly cheppandi.", "Malli cheppandi."
                ))
                else -> pick("te_retry", listOf(
                    "Haa?", "Emi?", "Malli cheppandi.", "Vinaledu."
                ))
            }
            lang.startsWith("ta") -> pick("ta_retry", listOf("Aa?", "Enna?", "Teriyalai.", "Solunga."))
            lang.startsWith("kn") -> pick("kn_retry", listOf("Ha?", "Enu?", "Kaliyalilla.", "Heli."))
            lang.startsWith("ml") -> pick("ml_retry", listOf("Ha?", "Enthu?", "Kekkunilla.", "Parayo."))
            lang.startsWith("pa") -> pick("pa_retry", listOf("Ha?", "Ki?", "Sunnai nahi.", "Dasao."))
            lang.startsWith("gu") -> pick("gu_retry", listOf("Ha?", "Shu?", "Sambhaldhu nahi.", "Kaho."))
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
    // 16. GIVE UP
    // ═════════════════════════════════════════════════════════════════════

    fun giveUp(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED -> pick("hi_giveup_frus", listOf(
                    "Koi baat nahi. Jab ready hon, hey Butler bolein.",
                    "Chaliye, baad mein karte hain.",
                    "Signal problem lag raha. Thodi der baad try karein."
                ))
                else -> pick("hi_giveup", listOf(
                    "Theek hai. Jab ready hon, hey Butler bolein.",
                    "Koi baat nahi. Baad mein.",
                    "Theek hai. Ready hone par bulaiye."
                ))
            }
            lang.startsWith("te") -> pick("te_giveup", listOf(
                "Parvaaledu. Taraavaata hey Butler cheppandi.",
                "Sare. Taraavaata matlaadam."
            ))
            lang.startsWith("ta") -> pick("ta_giveup", listOf(
                "Paravailla. Pinna hey Butler sollungal.", "Sari. Piragu."
            ))
            lang.startsWith("kn") -> pick("kn_giveup", listOf(
                "Parvaagilla. Naantara hey Butler heli.", "Sari. Naantara."
            ))
            lang.startsWith("ml") -> pick("ml_giveup", listOf(
                "Kaaryanilla. Pinne hey Butler parayo.", "Sheri. Pinne."
            ))
            lang.startsWith("pa") -> pick("pa_giveup", listOf(
                "Koi gal nahi. Baad vich hey Butler kaho.", "Sahi. Baad vich."
            ))
            lang.startsWith("gu") -> pick("gu_giveup", listOf(
                "Kaem nahi. Pachhi hey Butler kaho.", "Saru. Pachhi."
            ))
            else -> pick("en_giveup", listOf(
                "No worries. Say hey Butler when you're ready.",
                "That's fine. Talk later.",
                "Okay, just say hey Butler when ready."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 17. ITEM REMOVED
    // ═════════════════════════════════════════════════════════════════════

    fun itemRemoved(productName: String, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_removed", listOf(
                "$short hata diya.", "Theek hai, $short nahi.", "$short remove kar diya."
            ))
            lang.startsWith("te") -> pick("te_removed", listOf(
                "$short tiriyinchaanu.", "$short ledu.", "$short remove chesaanu."
            ))
            else -> pick("en_removed", listOf(
                "$short removed.", "Done, $short gone.", "$short taken out."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 18. ORDER ERROR
    // ═════════════════════════════════════════════════════════════════════

    fun orderError(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_err", listOf(
                "Network thodi slow hai. Phir try karein?",
                "Ek second. Phir koshish karte hain.",
                "Kuch problem aayi. Dobara order karein?"
            ))
            lang.startsWith("te") -> pick("te_err", listOf(
                "Network problem. Malli try cheyyana?", "Oka second. Malli choodam."
            ))
            else -> pick("en_err", listOf(
                "Hit a snag. Shall we try again?",
                "Network hiccup. Retry?",
                "Something went wrong. Try again?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 19. SESSION EXPIRED
    // ═════════════════════════════════════════════════════════════════════

    fun sessionExpired(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_exp", listOf(
                "Session expire ho gayi. Hey Butler bolein dobara.",
                "Thodi der ho gayi. Hey Butler se shuru karein."
            ))
            lang.startsWith("te") -> pick("te_exp", listOf(
                "Session expire aindi. Hey Butler antunnaru."
            ))
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
            "rice" to "daal",   "dal" to "chawal",  "oil" to "atta",
            "atta" to "tel",    "milk" to "bread",   "bread" to "makhan",
            "tea"  to "cheeni", "sugar" to "chai",   "ghee" to "daal",
            "eggs" to "bread",  "curd" to "chawal",  "butter" to "bread"
        )
        val te = mapOf(
            "rice" to "pappu",  "dal" to "annam",    "oil" to "pindi",
            "milk" to "rotte",  "tea" to "chakkera", "atta" to "nune"
        )
        val en = mapOf(
            "rice" to "dal",    "dal" to "rice",     "oil" to "atta",
            "atta" to "oil",    "milk" to "bread",   "bread" to "butter",
            "tea"  to "sugar",  "sugar" to "tea",    "ghee" to "dal",
            "eggs" to "bread",  "curd" to "rice",    "butter" to "bread"
        )
        val base = lang.substringBefore("-").lowercase().take(2)
        return when (base) {
            "hi" -> hi.entries.firstOrNull { p.contains(it.key) }?.value
            "te" -> te.entries.firstOrNull { p.contains(it.key) }?.value
            else -> en.entries.firstOrNull { p.contains(it.key) }?.value
        }
    }
}