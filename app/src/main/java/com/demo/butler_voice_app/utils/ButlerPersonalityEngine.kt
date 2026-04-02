package com.demo.butler_voice_app.utils

import com.demo.butler_voice_app.ai.UserMood
import java.util.Calendar

/**
 * ButlerPersonalityEngine — every word Butler speaks, humanised.
 *
 * Butler is a sharp, respectful Indian kirana assistant who genuinely
 * knows the customer. Never robotic, never repeats himself, responds to
 * the USER'S energy — not a fixed script.
 *
 * Think of the best customer-facing person at a Vijayawada kirana who
 * also works a phone helpline. Professional, warm, quick, desi.
 *
 * KEY RULE FOR ALL STRINGS:
 *   - Never mix Devanagari + Latin in the same sentence.
 *     Bad:  "दाल chahiye saath mein?"
 *     Good: "daal chahiye saath mein?" (all Hinglish)
 *     Good: "दाल भी चाहिए?" (all Hindi)
 *   - Keep sentences short — TTS reads them better.
 *   - Avoid starting with conjunctions ("aur", "toh") as standalone words.
 */
object ButlerPersonalityEngine {

    // ── Session de-duplication ────────────────────────────────────────────
    private val lastUsed = mutableMapOf<String, Int>()

    private fun pick(key: String, variants: List<String>): String {
        val last      = lastUsed[key] ?: -1
        val available = variants.indices.filter { it != last }
        val idx       = if (available.isEmpty()) 0 else available.random()
        lastUsed[key] = idx
        return variants[idx]
    }

    fun resetSession() { lastUsed.clear() }

    // ── Time of day ───────────────────────────────────────────────────────
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
                    "$name ji, namaste! $lastProduct phir se mangwaoon?",
                    "Arrey $name ji! Kya haal hai? $lastProduct chahiye aaj?",
                    "$name ji, aagaye! Pichla $lastProduct tha. Wahi lein?",
                    "Namaste $name ji! $lastProduct ya kuch naya?",
                    "$name bhai, swagat! Aaj kya mangwaaoon?"
                ))
                time == "morning" -> pick("hi_greet_morn", listOf(
                    "Good morning $name ji! Subah ki zaroorat batao.",
                    "Namaste $name ji! Subah subah kya chahiye?",
                    "$name ji, good morning! Kya la doon?"
                ))
                time == "evening" -> pick("hi_greet_eve", listOf(
                    "Namaste $name ji! Shaam ko kya chahiye?",
                    "$name ji, good evening! Kya mangwaaoon?",
                    "Arrey $name ji! Shaam ka kya lana hai?"
                ))
                else -> pick("hi_greet_new", listOf(
                    "Namaste $name ji! Kya la doon aaj?",
                    "$name ji, swagat hai! Kya chahiye?",
                    "Arrey $name ji! Kaise hain? Kya mangwaaoon?",
                    "Namaste $name ji! Batao, kya chahiye?"
                ))
            }
            lang.startsWith("te") -> when {
                lastProduct != null -> pick("te_greet_ret", listOf(
                    "Namaskaram $name garu! $lastProduct meeru tecchukovadam? Ledanta?",
                    "$name garu, bayalderaaru! Pichade $lastProduct. Meeru tistara?",
                    "Namaskaram $name! Innikemi kavali?"
                ))
                time == "morning" -> pick("te_greet_morn", listOf(
                    "Subha prabhatam $name garu! Emi kavali?",
                    "$name garu, mangalavasaram! Emi teestara?"
                ))
                else -> pick("te_greet_base", listOf(
                    "Namaskaram $name garu! Emi kavali?",
                    "$name garu, bayalderaaru! Emi cheppandee?",
                    "Namaskaram $name! Innikemi kavali?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_greet", listOf(
                "Vanakkam $name! Enna vendum?",
                "$name, vanakkam! Enna tharen?"
            ))
            lang.startsWith("kn") -> pick("kn_greet", listOf(
                "Namaskara $name! Enu beku?",
                "$name, namaskara! Enu tegolabeeku?"
            ))
            lang.startsWith("ml") -> pick("ml_greet", listOf(
                "Namaskaram $name! Enthu veno?",
                "$name, namaskaram! Enthu venam?"
            ))
            lang.startsWith("pa") -> pick("pa_greet", listOf(
                "Sat sri akal $name ji! Ki chahida?",
                "$name ji, welcome! Ki mangwaiye?"
            ))
            lang.startsWith("gu") -> pick("gu_greet", listOf(
                "Namaste $name! Shu joiye?",
                "$name, namaste! Shu mangaavun?"
            ))
            else -> when {
                lastProduct != null -> pick("en_greet_ret", listOf(
                    "Hey $name! Last time you had $lastProduct — same again?",
                    "Welcome back $name! $lastProduct or something new today?",
                    "Hi $name! Good to have you. What do you need?"
                ))
                time == "morning" -> pick("en_greet_morn", listOf(
                    "Good morning $name! What can I get you?",
                    "Morning $name! What do you need today?"
                ))
                else -> pick("en_greet_new", listOf(
                    "Hey $name! What can I get you?",
                    "Hi $name! What do you need?",
                    "Welcome $name! What shall I order?"
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 2. REORDER SUGGESTION
    //
    // FIX: Old version produced "Roy, Daawat Brown, Dhara Life, Archies Rice
    // again — yes or no?" which sounds robotic and reads like a CSV.
    //
    // New version uses natural sentence structures per language.
    // Items are described conversationally, not comma-listed cold.
    // ═════════════════════════════════════════════════════════════════════

    fun reorderGreeting(name: String, items: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_reorder", listOf(
                "$name ji, pichli baar $items mangaya tha. Wahi lagaoon?",
                "Arrey $name! $items phir se chahiye?",
                "$name, $items ka order taiyaar karoon?",
                "$name ji, $items phir se order karein?"
            ))
            lang.startsWith("te") -> pick("te_reorder", listOf(
                "$name garu, chivari sari $items teecchukonnaaru. Ippudu kavala?",
                "$name, $items meeru mandistara?"
            ))
            lang.startsWith("ta") -> pick("ta_reorder", listOf(
                "$name, kadalasiya $items vaanginenga. Innum venuma?",
                "$name, $items again poduma?"
            ))
            lang.startsWith("kn") -> pick("kn_reorder", listOf(
                "$name, kelasa sari $items tegonaaru. Ippudu beku?",
                "$name, $items again beku?"
            ))
            lang.startsWith("ml") -> pick("ml_reorder", listOf(
                "$name, kzhinja thavanaye $items vaanghi. Ippo venum?",
                "$name, $items again venam?"
            ))
            lang.startsWith("pa") -> pick("pa_reorder", listOf(
                "$name ji, pichhli baar $items mangwaaya si. Wahi chahida?",
                "$name, $items phir mangwaiye?"
            ))
            lang.startsWith("gu") -> pick("gu_reorder", listOf(
                "$name, cheli vaar $items mangavyu hatu. Pharthi joiye?",
                "$name, $items again joiye?"
            ))
            else -> pick("en_reorder", listOf(
                "Welcome back $name! Want the usual — $items?",
                "Hey $name! Shall I reorder $items?",
                "$name, same as last time — $items?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 3. ITEM ADDED CONFIRMATION
    // ═════════════════════════════════════════════════════════════════════

    fun itemAdded(productName: String, lang: String, mood: UserMood, cartSize: Int): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_added_r", listOf(
                    "Theek.", "$short done.", "Ho gaya.", "Rakh diya."
                ))
                else -> when (cartSize) {
                    1 -> pick("hi_added_1", listOf(
                        "Perfect! $short le liya.",
                        "Badhiya! $short ho gaya.",
                        "$short bilkul sahi choice!",
                        "Haan! $short.",
                        "Theek hai, $short."
                    ))
                    2 -> pick("hi_added_2", listOf(
                        "$short bhi rakh diya.",
                        "$short done.",
                        "Haan, $short bhi."
                    ))
                    else -> pick("hi_added_n", listOf(
                        "$short bhi.", "Done.", "Ho gaya."
                    ))
                }
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("te_added_r", listOf(
                    "Sare.", "$short ayindi.", "Done."
                ))
                else -> pick("te_added", listOf(
                    "Bagundi! $short teecchukonnaanu.",
                    "$short perfect choice!",
                    "Ha! $short.",
                    "Sare, $short le tecchaanu."
                ))
            }
            lang.startsWith("ta") -> pick("ta_added", listOf(
                "Sari! $short vaanginein.", "$short aachi.", "Ok $short."
            ))
            lang.startsWith("kn") -> pick("kn_added", listOf(
                "Aaytu! $short tagondu.", "$short aaytu.", "Ok $short."
            ))
            lang.startsWith("ml") -> pick("ml_added", listOf(
                "Sheri! $short eduthu.", "$short ayi.", "Ok $short."
            ))
            lang.startsWith("pa") -> pick("pa_added", listOf(
                "Changaa! $short le lita.", "$short ho gaya.", "Ok $short."
            ))
            lang.startsWith("gu") -> pick("gu_added", listOf(
                "Saaru! $short lai lidhun.", "$short thai gayu.", "Ok $short."
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_added_r", listOf(
                    "Done.", "$short added.", "Got it.", "Okay."
                ))
                else -> when (cartSize) {
                    1 -> pick("en_added_1", listOf(
                        "Perfect! $short added.",
                        "Great choice — $short it is!",
                        "Got it! $short added."
                    ))
                    2 -> pick("en_added_2", listOf(
                        "$short too.", "$short added as well.", "Done, $short."
                    ))
                    else -> pick("en_added_n", listOf(
                        "$short too.", "Added.", "Done.", "Got it."
                    ))
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 4. ASK MORE
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
                        "$suggestion bhi le lein?",
                        "$suggestion chahiye saath mein?",
                        "Aur $suggestion?"
                    ))
                } else {
                    pick("hi_more", listOf(
                        "Kuch aur?",
                        "Aur kya chahiye?",
                        "Kuch aur lena hai?",
                        "Bas itna?",
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
                    "Anything else?", "What else do you need?", "More?",
                    "Shall I add anything else?", "That all for today?"
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
                "Kitna lein $short? Ek ya do?",
                "$short — ek packet ya zyada?",
                "Kitna mangwaaoon $short?"
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
                    "Kaunsa lena hai? Naam bolein.",
                    "Brand ka naam batao.",
                    "Kaunsa chahiye?",
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
    // ═════════════════════════════════════════════════════════════════════

    fun confirmOrder(items: String, total: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_confirm", listOf(
                "$items — $total. Lagaoon?",
                "Total $total — $items. Order karoon?",
                "Theek hai — $items, $total. Pakka?",
                "$items, $total banta hai. Lagaoon?",
                "Bas — $items. $total. Order?",
                "$items. Jod ke $total. Lagaon?"
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
                "Got $items. $total total — confirm?",
                "Looks good — $items, $total. Order?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 8. PAYMENT MODE ASK
    // ═════════════════════════════════════════════════════════════════════

    fun askPaymentMode(amount: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_pay_ask", listOf(
                "$amount ka payment kaise karein? UPI, card, ya QR?",
                "Payment kaise karoge? UPI, card, ya QR scan?",
                "$amount — UPI bhejein ya card? QR bhi hai.",
                "Kaise pay karein? UPI, card, ya QR?"
            ))
            lang.startsWith("te") -> pick("te_pay_ask", listOf(
                "$amount payment ela cheyali? UPI, card, leda QR?",
                "Payment ela? UPI, card, QR?"
            ))
            lang.startsWith("ta") -> pick("ta_pay_ask", listOf(
                "$amount payment epdi? UPI, card, QR?", "Epdi pay pannuvenga?"
            ))
            lang.startsWith("kn") -> pick("kn_pay_ask", listOf(
                "$amount payment hege? UPI, card, QR?", "Hege pay madali?"
            ))
            lang.startsWith("ml") -> pick("ml_pay_ask", listOf(
                "$amount payment engane? UPI, card, QR?", "Engane pay cheyyam?"
            ))
            lang.startsWith("pa") -> pick("pa_pay_ask", listOf(
                "$amount payment kiven karan? UPI, card, QR?", "Kiven paisa denaa?"
            ))
            lang.startsWith("gu") -> pick("gu_pay_ask", listOf(
                "$amount payment kem? UPI, card, QR?", "Kem pay karishu?"
            ))
            else -> pick("en_pay_ask", listOf(
                "How would you like to pay $amount? UPI, card, or QR?",
                "$amount — UPI, card, or scan QR?",
                "Payment for $amount — how? UPI, card, or QR?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 9. UPI INSTRUCTION
    // ═════════════════════════════════════════════════════════════════════

    fun upiInstruction(amount: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_upi", listOf(
                "butler@upi pe $amount bhejo. Ho jaaye toh batana.",
                "$amount — butler@upi pe transfer kar do.",
                "UPI ID hai butler@upi. $amount bhejo aur bata dena.",
                "butler@upi pe $amount bhejein. Done hone pe bolein."
            ))
            lang.startsWith("te") -> pick("te_upi", listOf(
                "butler@upi ki $amount pampu. Ayinaaka cheppandi.",
                "$amount — butler@upi. Chesaaka cheppandi."
            ))
            lang.startsWith("ta") -> pick("ta_upi", listOf(
                "butler@upi ku $amount anuppu. Aanadhum sollunga.",
                "$amount — butler@upi. Done aana sollunga."
            ))
            lang.startsWith("kn") -> pick("kn_upi", listOf(
                "butler@upi ge $amount kali. Aadamele heli.",
                "$amount — butler@upi. Madidamele heli."
            ))
            lang.startsWith("ml") -> pick("ml_upi", listOf(
                "butler@upi il $amount aykku. Ayal parayo.",
                "$amount — butler@upi. Cheshal parayo."
            ))
            lang.startsWith("pa") -> pick("pa_upi", listOf(
                "butler@upi te $amount bhejo. Ho jaaye te dasao.",
                "$amount — butler@upi. Done hone te dasao."
            ))
            lang.startsWith("gu") -> pick("gu_upi", listOf(
                "butler@upi ne $amount moklo. Thai jaay tyare kaho.",
                "$amount — butler@upi. Done thay tyare kaho."
            ))
            else -> pick("en_upi", listOf(
                "Send $amount to butler@upi. Let me know when done.",
                "$amount to butler@upi — tell me once it's through.",
                "UPI is butler@upi. Pay $amount and say done."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 10. PAYMENT CONFIRMATION ASK
    // ═════════════════════════════════════════════════════════════════════

    fun askIfPaid(lang: String, mode: String, amount: String): String {
        return when {
            lang.startsWith("hi") -> when (mode) {
                "upi"  -> pick("hi_paid_upi", listOf(
                    "UPI ho gaya? $amount pahuncha?",
                    "$amount bhej diya?",
                    "UPI done?",
                    "Ho gayi payment?"
                ))
                "card" -> pick("hi_paid_card", listOf(
                    "Card se $amount ho gaya?", "Payment complete?", "Card done?"
                ))
                else   -> pick("hi_paid_qr", listOf(
                    "QR scan ho gaya?", "$amount pay hua?", "Done?"
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
    // ═════════════════════════════════════════════════════════════════════

    fun paymentDone(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_paydone", listOf(
                "Perfect!", "Shukriya!", "Badhiya!", "Ho gaya!"
            ))
            lang.startsWith("te") -> pick("te_paydone", listOf(
                "Bagundi!", "Dhanyavaadaalu!", "Perfect!"
            ))
            lang.startsWith("ta") -> pick("ta_paydone", listOf("Nandri!", "Perfect!", "Sari!"))
            lang.startsWith("kn") -> pick("kn_paydone", listOf("Dhanyavada!", "Perfect!", "Aaytu!"))
            lang.startsWith("ml") -> pick("ml_paydone", listOf("Nandri!", "Perfect!", "Ayi!"))
            lang.startsWith("pa") -> pick("pa_paydone", listOf("Shukriya!", "Changaa!", "Ho gaya!"))
            lang.startsWith("gu") -> pick("gu_paydone", listOf("Shukriya!", "Saaru!", "Thai gayu!"))
            else -> pick("en_paydone", listOf("Perfect!", "Got it!", "Great, thank you!", "Received!"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 12. ORDER PLACED — celebratory
    // ═════════════════════════════════════════════════════════════════════

    fun orderPlaced(name: String, orderId: String, amount: String, etaMins: Int, lang: String): String {
        val eta = if (etaMins > 0) etaMins else 30
        return when {
            lang.startsWith("hi") -> pick("hi_order_placed", listOf(
                "Ho gaya $name ji! Order ID $orderId. Kareeb $eta minute mein pahunch jayega. Shukriya!",
                "$name ji, order confirm ho gaya! $orderId. $eta minute mein aa jayega.",
                "Badhiya! Order lag gaya $name ji. $orderId. $eta minute ka kaam hai. Dhanyavaad!",
                "Done $name! $orderId se track karein. $eta minutes. Shukriya!",
                "Perfect $name ji! Order $orderId confirm. $eta minute mein delivery. Bahut shukriya!"
            ))
            lang.startsWith("te") -> pick("te_order_placed", listOf(
                "$name garu, order confirm ayindi! $orderId. $eta nimishaallo vastundi. Dhanyavaadaalu!",
                "Baagundi $name! Order $orderId. $eta minutes lo deliveri. Chala dhanyavaadaalu!"
            ))
            lang.startsWith("ta") -> pick("ta_order_placed", listOf(
                "$name, order confirm! $orderId. $eta nitcham varum. Nandri!",
                "Sari $name! $orderId — $eta nimidam. Nandri!"
            ))
            lang.startsWith("kn") -> pick("kn_order_placed", listOf(
                "$name, order aaytu! $orderId. $eta nimishada hage baruttade. Dhanyavada!",
                "Aaytu $name! $orderId — $eta nimisha. Dhanyavada!"
            ))
            lang.startsWith("ml") -> pick("ml_order_placed", listOf(
                "$name, order confirmed! $orderId. $eta minuteinu orathe ettum. Nandri!",
                "Ayi $name! $orderId — $eta minute. Nandri!"
            ))
            lang.startsWith("pa") -> pick("pa_order_placed", listOf(
                "$name ji, order confirm ho gaya! $orderId. $eta minute vich aa jauga. Shukriya!",
                "Ho gaya $name! $orderId — $eta minute. Bahut shukriya!"
            ))
            lang.startsWith("gu") -> pick("gu_order_placed", listOf(
                "$name, order confirm thai gayu! $orderId. $eta minute ma aavse. Shukriya!",
                "Thai gayu $name! $orderId — $eta minute. Shukriya!"
            ))
            else -> pick("en_order_placed", listOf(
                "Done $name! Order $orderId confirmed. Arriving in about $eta minutes. Thank you!",
                "All set $name! $orderId — $eta minutes to your door. Thanks!",
                "Order placed! $orderId. Should be there in $eta minutes. Thank you $name!",
                "Confirmed $name! $orderId — delivery in $eta minutes. Cheers!"
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
                "Woh abhi available nahi. Kuch aur?",
                "$short nahi mila. Koi aur brand?",
                "Sorry, $short stock mein nahi. Kya lein phir?",
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
                "That's not available right now. Anything else?",
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
                "Abhi kuch nahi hai. Kya lein?",
                "Cart khaali hai. Kya mangwaaoon?",
                "Kuch add nahi hua. Kya chahiye?",
                "Abhi kuch nahi. Bolo kya lena hai."
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
    // ═════════════════════════════════════════════════════════════════════

    fun didntHear(lang: String, mood: UserMood, retryCount: Int): String {
        return when {
            lang.startsWith("hi") -> when {
                mood == UserMood.FRUSTRATED -> pick("hi_retry_frus", listOf(
                    "Maafi — zara aur seedha bolein.",
                    "Sorry, ek baar aur? Thoda jor se.",
                    "Suna nahi. Paas aakar bolein."
                ))
                retryCount >= 4 -> pick("hi_retry_many", listOf(
                    "Mic ke paas aakar bolein.",
                    "Thoda jor se bolein.",
                    "Signal weak lag raha. Ek baar aur?"
                ))
                retryCount == 3 -> pick("hi_retry_3", listOf(
                    "Ek baar aur bolein.", "Fir se?", "Dobara?"
                ))
                else -> pick("hi_retry", listOf(
                    "Haan?", "Kya?", "Dobara?", "Fir bolein.", "Suna nahi.", "Ek baar aur?"
                ))
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED -> pick("te_retry_frus", listOf(
                    "Maafi. Inkaa clearly cheppandi.", "Vinaledu. Kastapadandi."
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
                    "Sorry, say that again?",
                    "Didn't catch that — louder?",
                    "My bad, one more time?"
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
                    "Koi baat nahi. Mic check karein aur jab ready ho tab hey Butler bolein.",
                    "Chaliye, baad mein baat karte hain.",
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
                "Kuch problem aayi. Dobara lagaoon?"
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
            "rice" to "daal", "dal" to "chawal", "oil" to "atta",
            "atta" to "tel", "milk" to "bread", "bread" to "makhan",
            "tea"  to "cheeni", "sugar" to "chai", "ghee" to "daal", "eggs" to "bread"
        )
        val te = mapOf(
            "rice" to "pappu", "dal" to "annam", "oil" to "pindi",
            "milk" to "rotte", "tea" to "chakkera"
        )
        val en = mapOf(
            "rice" to "dal", "dal" to "rice", "oil" to "atta",
            "atta" to "oil", "milk" to "bread", "bread" to "butter",
            "tea"  to "sugar", "sugar" to "tea", "ghee" to "dal", "eggs" to "bread"
        )
        val base = lang.substringBefore("-").lowercase().take(2)
        return when (base) {
            "hi" -> hi.entries.firstOrNull { p.contains(it.key) }?.value
            "te" -> te.entries.firstOrNull { p.contains(it.key) }?.value
            else -> en.entries.firstOrNull { p.contains(it.key) }?.value
        }
    }
}