package com.demo.butler_voice_app.utils

import com.demo.butler_voice_app.EmotionTone
import com.demo.butler_voice_app.ai.UserMood
import java.util.Calendar

/**
 * ButlerPersonalityEngine — warm, professional voice templates.
 *
 * WORD-LEVEL PRINCIPLES (latest pass):
 *
 * "daalu"     → "le loon"   — "chuck in cart" → "shall I get this"
 * "mangwaoge" → "chahiye"   — informal 2nd person → neutral/warm
 * "Sahi hai"  → "Achha"     — "that's right" → warm acknowledgment
 * "Bas Roy?"  → "Aur kuch chahiye Roy?" — dismissive → open question
 * "Theek hai."→ "Bilkul."   — flat robotic OK → warm affirming Certainly
 *
 * All other principles from previous passes remain in effect:
 * - No em-dashes, name at sentence end (not "$name," at start)
 * - "karna hai" not "karni hai" (neutral gender)
 * - Comma between items+total in confirmOrder
 * - "ho gaya" not "ho gya", "pahunchega" for delivery
 * - paymentDone uses "Bilkul!", "Bahut accha!", "Perfect."
 */
object ButlerPersonalityEngine {

    // ══════════════════════════════════════════════════════════════════════
    // TONE RECOMMENDATIONS — DO NOT CHANGE
    // ══════════════════════════════════════════════════════════════════════
    fun toneForGreeting()                  = EmotionTone.WARM
    fun toneForProductList()               = EmotionTone.NORMAL
    fun toneForConfirmAdd()                = EmotionTone.NORMAL
    fun toneForItemAdded()                 = EmotionTone.WARM
    fun toneForConfirmOrder()              = EmotionTone.WARM
    fun toneForPaymentAsk()                = EmotionTone.WARM
    fun toneForPaymentUPI()                = EmotionTone.WARM
    fun toneForOrderPlaced()               = EmotionTone.WARM
    fun toneForRetry()                     = EmotionTone.EMPATHETIC
    fun toneForGiveUp()                    = EmotionTone.EMPATHETIC
    fun toneForError()                     = EmotionTone.EMPATHETIC
    fun toneForEmergency()                 = EmotionTone.EMERGENCY
    fun toneForPaymentDone()               = EmotionTone.EXCITED
    fun toneForSubstitute()                = EmotionTone.NORMAL
    fun toneForAskMore(mood: UserMood)     = if (mood == UserMood.FRUSTRATED) EmotionTone.EMPATHETIC else EmotionTone.WARM

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

    // ══════════════════════════════════════════════════════════════════════
    // SUBSTITUTE / MISMATCH PHRASES
    // ══════════════════════════════════════════════════════════════════════

    fun productCategoryMismatch(
        requestedName: String,
        substituteName: String,
        price: Int,
        lang: String,
        name: String = ""
    ): String {
        val reqShort = requestedName.split(" ").take(2).joinToString(" ")
        val subShort = substituteName.split(" ").take(3)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        val n = if (name.isNotBlank()) " $name" else ""
        return when {
            lang.startsWith("hi") -> pick("hi_cat_mismatch", listOf(
                "$reqShort abhi available nahi$n. $subShort Rs $price ka hai. Chalega?",
                "$reqShort nahi mili$n. $subShort Rs $price ka. Le loon?",
                "$reqShort store mein nahi hai$n. $subShort Rs $price mein. Theek rahega?"
            ))
            lang.startsWith("te") -> "$reqShort ledu$n. $subShort Rs $price undi. Chalaa?"
            lang.startsWith("ta") -> "$reqShort illai$n. $subShort Rs $price irukku. Sari-aa?"
            lang.startsWith("kn") -> "$reqShort illa$n. $subShort Rs $price ide. Aaguttaa?"
            lang.startsWith("ml") -> "$reqShort illa$n. $subShort Rs $price undu. Shari-aa?"
            lang.startsWith("pa") -> "$reqShort available nahi$n. $subShort Rs $price hai. Chalega?"
            lang.startsWith("gu") -> "$reqShort nathi$n. $subShort Rs $price chhe. Chalase?"
            else -> pick("en_cat_mismatch", listOf(
                "$reqShort isn't available$n. I have $subShort for Rs $price. Want that?",
                "No $reqShort in stock$n. Closest is $subShort at Rs $price. Okay?",
                "$reqShort not found$n. $subShort, Rs $price. Shall I add that instead?"
            ))
        }
    }

    fun confirmSubstitute(
        name: String,
        requestedItem: String,
        substituteName: String,
        price: Int,
        lang: String
    ): String {
        val subShort = substituteName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_sub", listOf(
                "$subShort Rs $price ka hai $name... ise le loon?",
                "$requestedItem nahi mila. $subShort Rs $price ka hai. Le loon $name?",
                "$subShort Rs $price. $name, yeh chalega?"
            ))
            lang.startsWith("te") -> "$subShort Rs $price undi $name... idi teesukuntaaraa?"
            lang.startsWith("ta") -> "$subShort Rs $price irukku $name... itha vangattuma?"
            lang.startsWith("kn") -> "$subShort Rs $price ide $name... ithanu tegolabeekaa?"
            lang.startsWith("ml") -> "$subShort Rs $price anu $name... ithu venam?"
            lang.startsWith("pa") -> "$subShort Rs $price da hai $name... la laiye?"
            lang.startsWith("gu") -> "$subShort Rs $price chhe $name... levun?"
            else -> pick("en_sub", listOf(
                "$subShort is Rs $price $name. Want this one?",
                "No $requestedItem. Got $subShort for Rs $price $name. Okay?",
                "$subShort, Rs $price. Add it $name?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GREETING
    // ══════════════════════════════════════════════════════════════════════

    fun greeting(name: String, lang: String, lastProduct: String?, mood: UserMood): String {
        val time = timeSlot()
        return when {
            lang.startsWith("hi") -> when {
                lastProduct != null -> pick("hi_greet_ret", listOf(
                    "Haan $name... batao, kya laana hai aaj?",
                    "Haan $name ji... kya mangwaoge aaj?",
                    "$name, batao... kya chahiye?",
                    "Haan $name... kya la doon?"
                ))
                time == "morning"  -> pick("hi_greet_morn", listOf(
                    "Good morning $name... aaj kya laana hai?",
                    "Haan $name ji... kya chahiye aaj?",
                    "$name, batao... kya mangwaoge aaj?"
                ))
                time == "evening"  -> pick("hi_greet_eve", listOf(
                    "Haan $name... shaam ko kya la doon?",
                    "$name ji, batao... kya chahiye aaj?",
                    "Haan $name... kya chahiye?"
                ))
                else -> pick("hi_greet_new", listOf(
                    "Haan $name... batao, kya laana hai?",
                    "Haan $name ji... kya chahiye?",
                    "$name, batao... kya mangwaoge?",
                    "Haan $name... kya la doon aaj?"
                ))
            }
            lang.startsWith("te") -> when {
                lastProduct != null -> pick("te_greet_ret", listOf(
                    "Cheppandi $name garu... emi kavali inniki?",
                    "Haa $name... emi teestara?"
                ))
                else -> pick("te_greet_base", listOf(
                    "Cheppandi $name garu... inniki emi kavali?",
                    "Namaskaram $name... emi teestara?",
                    "Haa $name garu... emi kavali?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_greet", listOf(
                "Solunga $name... indru enna vendum?",
                "Aamam $name... enna tharen?"
            ))
            lang.startsWith("kn") -> pick("kn_greet", listOf(
                "Heli $name... enu beku indhu?",
                "Aamdu $name... enu tegolabeeku?"
            ))
            lang.startsWith("ml") -> pick("ml_greet", listOf(
                "Parayo $name... innu enthu veno?",
                "Aamm $name... enthu venam?"
            ))
            lang.startsWith("pa") -> pick("pa_greet", listOf(
                "Dasao $name ji... aaj ki chahida?",
                "Haan $name... ki mangwaiye?"
            ))
            lang.startsWith("gu") -> pick("gu_greet", listOf(
                "Kaho $name... aaj shu joiye?",
                "Haan $name... shu mangaavun?"
            ))
            lang.startsWith("mr") -> pick("mr_greet", listOf(
                "Sanga $name... aaj kaay havay?",
                "Haan $name... kaay aanu?"
            ))
            else -> when {
                lastProduct != null -> pick("en_greet_ret", listOf(
                    "Yes $name... what do you need today?",
                    "Hi $name... what can I get you?",
                    "Yes $name, go ahead..."
                ))
                time == "morning"  -> pick("en_greet_morn", listOf(
                    "Good morning $name... what do you need today?",
                    "Morning $name... what can I get you?"
                ))
                else -> pick("en_greet_new", listOf(
                    "Yes $name... what do you need today?",
                    "Hi $name... what can I get you?",
                    "Yes $name, go ahead."
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRODUCT TYPE QUESTION
    // ══════════════════════════════════════════════════════════════════════

    fun askProductType(category: String, name: String, lang: String): String {
        val cat = category.lowercase()
        return when {
            lang.startsWith("hi") -> when {
                cat.contains("rice") || cat.contains("chawal") -> pick("hi_type_rice", listOf(
                    "$name, kaunsa rice chahiye. Basmati, brown ya regular?",
                    "Kaunsa rice chahiye $name. Basmati, brown ya sona masoori?",
                    "$name, kaunsa rice chahiye. Basmati, brown ya normal?"
                ))
                cat.contains("dal")  || cat.contains("daal")  -> pick("hi_type_dal", listOf(
                    "$name, kaunsi daal chahiye. Arhar, moong ya masoor?",
                    "Kaunsi daal chahiye $name. Toor, moong, masoor ya urad?",
                    "$name, kaunsi daal chahiye. Arhar, moong ya masoor?"
                ))
                cat.contains("oil")  || cat.contains("tel")   -> pick("hi_type_oil", listOf(
                    "$name, kaunsa tel chahiye. Sarson, sunflower ya coconut?",
                    "Kaunsa oil chahiye $name. Mustard, sunflower ya coconut?"
                ))
                cat.contains("atta") || cat.contains("flour") -> pick("hi_type_atta", listOf(
                    "$name, kaunsa atta chahiye. Wheat, multigrain ya maida?",
                    "Atta mein kya chahiye $name. Wheat, multigrain ya maida?"
                ))
                cat.contains("milk") || cat.contains("doodh") -> pick("hi_type_milk", listOf(
                    "$name, kaunsa doodh chahiye. Full cream, toned ya skimmed?",
                    "Doodh mein $name. Full fat chahiye ya toned?"
                ))
                cat.contains("tea")  || cat.contains("chai")  -> pick("hi_type_tea", listOf(
                    "$name, kaunsi chai chahiye. Loose, tea bags ya kadak?",
                    "Chai mein $name. Tata chahiye, Red Label ya koi aur?"
                ))
                else -> pick("hi_type_generic", listOf(
                    "$name, koi specific brand chahiye?",
                    "$name, kaunsa $category chahiye?"
                ))
            }
            lang.startsWith("te") -> when {
                cat.contains("rice") -> "$name, endha rice kavali. Basmati, brown rice leda regular?"
                cat.contains("dal")  -> "Endha pappu kavali $name. Kandi, pesara leda masoor?"
                cat.contains("oil")  -> "Endha nune kavali $name. Avise, sunflower leda coconut?"
                else -> "Endha type kavali $name?"
            }
            lang.startsWith("ta") -> when {
                cat.contains("rice") -> "Enna rice vendum $name. Basmati, brown rice, regular?"
                cat.contains("dal")  -> "Enna paruppu vendum $name. Toor, moong, masoor?"
                else -> "Enna type vendum $name?"
            }
            lang.startsWith("kn") -> when {
                cat.contains("rice") -> "Yaava akki beku $name. Basmati, brown, regular?"
                cat.contains("dal")  -> "Yaava bele beku $name. Toor, moong, masoor?"
                else -> "Yaava type beku $name?"
            }
            lang.startsWith("ml") -> when {
                cat.contains("rice") -> "Enth ari veno $name. Basmati, brown rice, regular?"
                cat.contains("dal")  -> "Enth parippu veno $name. Toor, moong, masoor?"
                else -> "Enth type veno $name?"
            }
            lang.startsWith("pa") -> when {
                cat.contains("rice") -> "Kihra chawal chahida $name. Basmati, brown, regular?"
                cat.contains("dal")  -> "Kihra daal chahida $name. Toor, moong, masoor?"
                else -> "Kihra type chahida $name?"
            }
            lang.startsWith("gu") -> when {
                cat.contains("rice") -> "Kyun chaval joiye $name. Basmati, brown, regular?"
                cat.contains("dal")  -> "Kyu dal joiye $name. Toor, moong, masoor?"
                else -> "Kyu type joiye $name?"
            }
            else -> when {
                cat.contains("rice") -> "Which rice $name. Basmati, brown, or regular?"
                cat.contains("dal")  -> "Which dal $name. Toor, moong, masoor, or urad?"
                cat.contains("oil")  -> "Which oil $name. Mustard, sunflower, or coconut?"
                else -> "What type of $category $name?"
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONFIRM ADD PRODUCT
    //
    // "daalu" (chuck/throw in) → "le loon" (shall I get this)
    // "daalu" was informal and sounded rough to native ears.
    // "le loon" = shall I take/add it — natural, warm, professional.
    // Name stays at sentence end throughout — avoids sharp "$name," opening.
    // ══════════════════════════════════════════════════════════════════════

    fun confirmAddProduct(name: String, productName: String, price: Int, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_confirm_add", listOf(
                "$short Rs $price ka hai... le loon $name?",   // was "lena hai" — "le loon" warmer
                "$short Rs $price. Le loon $name?",             // was "Cart mein daalu" — informal
                "$short Rs $price. Lena hai $name?"             // alternate warm phrasing
            ))
            lang.startsWith("te") -> pick("te_confirm_add", listOf(
                "$short Rs $price untundi $name... cart lo pettanaa?",
                "$short Rs $price. $name, teesukuntaaraa?"
            ))
            lang.startsWith("ta") -> "$short Rs $price irukku $name... cart-la podanuma?"
            lang.startsWith("kn") -> "$short Rs $price ide $name... cart ge haakona?"
            lang.startsWith("ml") -> "$short Rs $price anu $name... cart-il idattea?"
            lang.startsWith("pa") -> "$short Rs $price da hai $name... cart vich pa deyaan?"
            lang.startsWith("gu") -> "$short Rs $price chhe $name... cart ma nakhun?"
            else -> pick("en_confirm_add", listOf(
                "$short is Rs $price $name. Want this one?",
                "$short, Rs $price. Add it $name?",
                "$short costs Rs $price. Should I add it $name?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ITEM ADDED
    //
    // "mangwaoge" (informal 2nd person) → "chahiye" (warm, neutral)
    // "Sahi hai" (that's right) → "Achha" (good/alright — natural warm ack)
    // FRUSTRATED V3 "Bas Roy?" (dismissive) → "Aur kuch chahiye Roy?"
    // ══════════════════════════════════════════════════════════════════════

    fun itemAdded(name: String, productName: String, lang: String, mood: UserMood, cartSize: Int): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        val full  = productName.split(" ").take(3)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_added_r", listOf(
                    "$short le liya. Aur kuch chahiye $name?",
                    "Theek hai $name... kuch aur chahiye?",
                    "Cart mein aa gaya. Aur kuch chahiye $name?" // was "Bas Roy?" — dismissive
                ))
                else -> when (cartSize) {
                    1    -> pick("hi_added_1", listOf(
                        "Ho gaya $name, $full le liya... aur kuch chahiye?",   // was "mangwaoge" — informal
                        "Theek hai $name, $full cart mein aa gaya... kuch aur chahiye?",
                        "Achha $name, $full le liya. Aur kuch chahiye?"         // was "Sahi hai" — odd
                    ))
                    2    -> pick("hi_added_2", listOf(
                        "Ho gaya $name, $full bhi le liya... kuch aur chahiye?",
                        "$full bhi aa gaya $name... aur kuch chahiye?"
                    ))
                    else -> pick("hi_added_n", listOf(
                        "$short bhi le liya. Kuch aur chahiye $name?",
                        "Ho gaya... aur kuch chahiye $name?"
                    ))
                }
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("te_added_r", listOf(
                    "$short ayindi $name... inkaa emi?",
                    "Sare $name... inkaa?"
                ))
                else -> pick("te_added", listOf(
                    "Ayindi $name, $full cart lo pettaanu... inkaa emi kavali?",
                    "Sare $name, $full add chesaanu... marokkati?",
                    "$full teecchukonnaanu $name... inkaa?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_added", listOf(
                "Sari $name, $full vaanginen... vera enna vendum?",
                "$full vaanginen $name. Vera enna vendum?"
            ))
            lang.startsWith("kn") -> pick("kn_added", listOf(
                "Aayitu $name, $full tagondi... bere enu beku?",
                "$full tagondi $name. Bere enu beku?"
            ))
            lang.startsWith("ml") -> pick("ml_added", listOf(
                "Ayi $name, $full edutthu... vere enthu veno?",
                "$full edutthu $name. Vere enthu veno?"
            ))
            lang.startsWith("pa") -> pick("pa_added", listOf(
                "Theek hai $name, $full le litta... hor ki chahida?",
                "$full le litta $name. Hor ki chahida?"
            ))
            lang.startsWith("gu") -> pick("gu_added", listOf(
                "Saru $name, $full lai lidhun... biju shu joiye?",
                "$full lai lidhun $name. Biju shu joiye?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_added_r", listOf(
                    "Done $name. Anything else?",
                    "$short added. More?"
                ))
                else -> when (cartSize) {
                    1    -> pick("en_added_1", listOf(
                        "Got it $name, $full is in your cart. Anything else?",
                        "$full added $name. What else do you need?",
                        "Alright $name, $full added. Anything else?"
                    ))
                    2    -> pick("en_added_2", listOf(
                        "Done $name, $full added as well. Anything else?",
                        "$full too $name, added. More?"
                    ))
                    else -> pick("en_added_n", listOf(
                        "$short added $name. Anything else?",
                        "Got it. More $name?"
                    ))
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONFIRM ADD NEXT
    //
    // "ise bhi daalu" → "ise bhi le loon" — same "daalu" fix as above
    // ══════════════════════════════════════════════════════════════════════

    fun confirmAddNext(name: String, productName: String, price: Int, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_confirm_next", listOf(
                "$short Rs $price. Yeh bhi le loon $name?",
                "$short Rs $price. Ise bhi le loon $name?",   // was "ise bhi daalu" — informal
                "$short Rs $price ka hai... yeh bhi lena hai $name?"
            ))
            lang.startsWith("te") -> "$short Rs $price untundi $name... idi kooda cart lo pettanaa?"
            lang.startsWith("ta") -> "$short Rs $price irukku $name... ithayum vangattuma?"
            lang.startsWith("kn") -> "$short Rs $price ide $name... ithanu kooda cart ge haakona?"
            lang.startsWith("ml") -> "$short Rs $price anu $name... ithu koodi cart-il idattea?"
            lang.startsWith("pa") -> "$short Rs $price da hai $name... eda vi la laiye?"
            lang.startsWith("gu") -> "$short Rs $price chhe $name... aane pan nakhu?"
            else -> pick("en_confirm_next", listOf(
                "$short is Rs $price $name. Add this too?",
                "$short, Rs $price. This one too $name?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONFIRM ORDER
    // ══════════════════════════════════════════════════════════════════════

    fun confirmOrder(name: String, items: String, total: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_confirm_order", listOf(
                "Theek hai... $items, total $total. Order kar doon?",
                "$items, total $total. Pakka karoon?",
                "Bas itna hai na. $items, total $total. Order lagaoon?"
            ))
            lang.startsWith("te") -> pick("te_confirm_order", listOf(
                "Sare $name... $items. Total $total. Order pettanaa?",
                "Antena $name, $items, $total. Order ivvana?"
            ))
            lang.startsWith("ta") -> "Sari $name... $items. Mottam $total. Order podanuma?"
            lang.startsWith("kn") -> "Saaku $name... $items. Otha $total. Order madona?"
            lang.startsWith("ml") -> "Mathi $name... $items. Aakoode $total. Order cheyattea?"
            lang.startsWith("pa") -> "Theek hai $name... $items. Kull $total. Order karan?"
            lang.startsWith("gu") -> "Saru $name... $items. Kul $total. Order karun?"
            else -> pick("en_confirm_order", listOf(
                "Alright $name... $items, total $total. Shall I place your order?",
                "That's $items, $total total $name. Place the order?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAYMENT ASK
    // ══════════════════════════════════════════════════════════════════════

    fun askPaymentMode(name: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_ask_payment", listOf(
                "Payment kaise karna hai. UPI, card ya cash?",
                "UPI se payment karenge, ya card se?",
                "Payment kaise karenge. UPI, card ya cash?"
            ))
            lang.startsWith("te") -> "$name, ela pay chestaru. UPI, card leda cash?"
            lang.startsWith("ta") -> "$name, epdi pay pannuvenga. UPI, card la cash?"
            lang.startsWith("kn") -> "$name, hege pay madtira. UPI, card leda cash?"
            lang.startsWith("ml") -> "$name, engane pay cheyyum. UPI, card allengil cash?"
            lang.startsWith("pa") -> "$name, kiven paisa dena. UPI, card ya cash?"
            lang.startsWith("gu") -> "$name, kem pay karisho. UPI, card ke cash?"
            else -> pick("en_ask_payment", listOf(
                "How would you like to pay $name. UPI, card, or cash?",
                "$name. UPI, card, or cash?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UPI INSTRUCTION
    //
    // "Theek hai." → "Bilkul." in all 3 variants.
    // "Theek hai" = flat OK / robotic.
    // "Bilkul" = certainly / of course — warm, affirming, professional.
    // ══════════════════════════════════════════════════════════════════════

    fun upiInstruction(amount: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_upi", listOf(
                "UPI se payment kar dijiye... ho jaaye toh bata dijiyega.",
                "Bilkul. UPI se $amount bhej dijiye... ho jaaye toh bata dijiyega.",
                "Bilkul. UPI se payment kar dijiye... bata dijiyega."  // was "Theek hai."
            ))
            lang.startsWith("te") -> "Sare, UPI lo $amount pampu... ayinaaka cheppandi."
            lang.startsWith("ta") -> "Sari, UPI-la $amount anuppu... aanadhum sollunga."
            lang.startsWith("kn") -> "Sari, UPI nalli $amount kali... madidamele heli."
            lang.startsWith("ml") -> "Sheri, UPI-l $amount aykku... cheshal parayo."
            lang.startsWith("pa") -> "Theek hai, UPI ton $amount bhejo... ho jaaye te dasao."
            lang.startsWith("gu") -> "Saru, UPI thi $amount moklo... thai jaay tyare kaho."
            else -> pick("en_upi", listOf(
                "Please pay $amount via UPI... let me know once done.",
                "UPI ID is on screen. Send $amount and say done."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ORDER PLACED
    // ══════════════════════════════════════════════════════════════════════

    fun orderPlaced(name: String, orderId: String, amount: String, etaMins: Int, lang: String): String {
        val eta = if (etaMins > 0) etaMins else 30
        return when {
            lang.startsWith("hi") -> pick("hi_order_placed", listOf(
                "Order confirm ho gaya. $eta minute mein delivery ho jaayegi.",
                "Shukriya $name. Order ho gaya, $eta minute mein pahunchega.",
                "Perfect. Aapka order confirm ho gaya. $eta minute mein pahunch jaayega."
            ))
            lang.startsWith("te") -> pick("te_order_placed", listOf(
                "Baagundi $name... order confirm ayindi. $eta nimishaallo vastundi. Dhanyavaadaalu.",
                "$name garu, order confirm. $eta minutes lo deliveri. Chala shukriya."
            ))
            lang.startsWith("ta") -> "Perfect $name... ungal order confirm. $eta nimidathil varum. Nandri."
            lang.startsWith("kn") -> "Chennagi $name... order confirm aayitu. $eta nimisha hage baruttade. Dhanyavada."
            lang.startsWith("ml") -> "Perfect $name... order confirmed. $eta minuteinu orathe ettum. Nandri."
            lang.startsWith("pa") -> "Perfect $name... order ho gaya. $eta minute vich aa jauga. Shukriya."
            lang.startsWith("gu") -> "Perfect $name... order thai gayu. $eta minute ma aavse. Shukriya."
            else -> pick("en_order_placed", listOf(
                "Perfect $name... your order is placed. Arriving in about $eta minutes.",
                "Done $name. Order confirmed. $eta minutes to delivery. Thank you.",
                "All set $name... order placed. $eta minutes away. Thanks!"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ASK MORE
    // ══════════════════════════════════════════════════════════════════════

    fun askMore(name: String, lang: String, mood: UserMood, cartSize: Int, lastProduct: String?): String {
        val suggestion = getRelatedSuggestion(lastProduct, lang)
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_more_r", listOf(
                    "Aur kuch $name?", "Kuch aur?", "Bas $name?", "Kya chahiye?"
                ))
                else -> if (suggestion != null && cartSize == 1) {
                    pick("hi_more_sug", listOf(
                        "$suggestion bhi chahiye $name?",
                        "$suggestion bhi saath mein laoon?",
                        "Kya $suggestion bhi le loon $name?"
                    ))
                } else {
                    pick("hi_more", listOf(
                        "Bas itna $name? Ya kuch aur?",
                        "Kuch aur chahiye $name?",
                        "Aur kya laana hai $name?"
                    ))
                }
            }
            lang.startsWith("te") -> pick("te_more", listOf("Inkaa emi kavali $name?", "Marokkati?", "Chaaladaa $name?"))
            lang.startsWith("ta") -> pick("ta_more", listOf("Vera enna $name?", "Innoru?", "Porum $name?"))
            lang.startsWith("kn") -> pick("kn_more", listOf("Inenu $name?", "Bere?", "Saaku $name?"))
            lang.startsWith("ml") -> pick("ml_more", listOf("Innum $name?", "Vere?", "Mathi $name?"))
            lang.startsWith("pa") -> pick("pa_more", listOf("Hor ki $name?", "Kuch hor?", "Bass $name?"))
            lang.startsWith("gu") -> pick("gu_more", listOf("Biju shu $name?", "Kahi biju?", "Bas $name?"))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_more_r", listOf("More $name?", "Anything else?", "That it?"))
                else -> pick("en_more", listOf(
                    "Anything else $name?",
                    "What else do you need $name?",
                    "Shall I add anything else $name?"
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAYMENT CONFIRMED
    // ══════════════════════════════════════════════════════════════════════

    fun askIfPaid(lang: String, mode: String, amount: String, name: String = ""): String {
        val n = if (name.isNotBlank()) " $name" else ""
        return when {
            lang.startsWith("hi") -> when (mode) {
                "upi"  -> "Payment ho gaya$n?"
                "card" -> "Card se ho gaya$n?"
                else   -> "Payment ho gaya$n?"
            }
            lang.startsWith("te") -> "Payment ayindaa?"
            lang.startsWith("ta") -> "Payment aachaa?"
            lang.startsWith("kn") -> "Payment aytaa?"
            lang.startsWith("ml") -> "Payment aayoo?"
            lang.startsWith("pa") -> "Payment ho gayi?"
            lang.startsWith("gu") -> "Payment thai?"
            else -> "Payment done$n?"
        }
    }

    fun paymentDone(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_paydone", listOf(
                "Bilkul!",
                "Bahut accha!",
                "Perfect."
            ))
            lang.startsWith("te") -> "Sare, ayindi."
            lang.startsWith("ta") -> "Sari."
            lang.startsWith("kn") -> "Aaytu."
            lang.startsWith("ml") -> "Ayi."
            lang.startsWith("pa") -> "Ho gaya."
            lang.startsWith("gu") -> "Thai gayu."
            else -> pick("en_paydone", listOf("Got it.", "Received.", "Done."))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRODUCT NOT FOUND
    // ══════════════════════════════════════════════════════════════════════

    fun productNotFound(itemName: String, lang: String): String {
        val short = itemName.take(20)
        return when {
            lang.startsWith("hi") -> pick("hi_notfound", listOf(
                "Woh abhi nahi hai. Kuch aur chahiye?",
                "$short nahi mila. Koi aur brand?",
                "$short stock mein nahi hai. Kuch aur chahiye?"
            ))
            lang.startsWith("te") -> "Adi ippudu ledu. Inkaa emi?"
            lang.startsWith("ta") -> "Adu illai. Vera enna?"
            lang.startsWith("kn") -> "Adu illa. Bere enu?"
            lang.startsWith("ml") -> "Athu illa. Vere enthu?"
            lang.startsWith("pa") -> "$short nahi mila. Kuch hor?"
            lang.startsWith("gu") -> "$short na maddyo. Kahi biju?"
            else -> pick("en_notfound", listOf(
                "That's not available right now. Anything else?",
                "$short not in stock. Want something else?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RETRY / DIDN'T HEAR
    // ══════════════════════════════════════════════════════════════════════

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
                    "Thoda jor se boliye."
                ))
                retryCount >= 2 -> pick("hi_retry_2", listOf(
                    "Samajh nahi aaya. Phir se boliye.",
                    "Kuch suna nahi. Thoda aur boliye."
                ))
                else -> pick("hi_retry", listOf(
                    "Haan?", "Kya?", "Phir boliye.", "Suna nahi, phir boliye."
                ))
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED -> "Vinaledu. Clearly cheppandi."
                else -> pick("te_retry", listOf("Haa?", "Emi?", "Malli cheppandi."))
            }
            lang.startsWith("ta") -> pick("ta_retry", listOf("Aa?", "Enna?", "Solunga."))
            lang.startsWith("kn") -> pick("kn_retry", listOf("Ha?", "Enu?", "Heli."))
            lang.startsWith("ml") -> pick("ml_retry", listOf("Ha?", "Enthu?", "Parayo."))
            lang.startsWith("pa") -> pick("pa_retry", listOf("Ha?", "Ki?", "Dasao."))
            lang.startsWith("gu") -> pick("gu_retry", listOf("Ha?", "Shu?", "Kaho."))
            else -> when (mood) {
                UserMood.FRUSTRATED -> "Didn't catch that. A bit louder?"
                else -> pick("en_retry", listOf("Sorry?", "Come again?", "Say that again?", "Didn't catch that."))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GIVE UP
    // ══════════════════════════════════════════════════════════════════════

    fun giveUp(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED -> pick("hi_giveup_frus", listOf(
                    "Koi baat nahi. Jab ready hon, hey Butler bolein.",
                    "Chaliye, baad mein baat karte hain."
                ))
                else -> pick("hi_giveup", listOf(
                    "Theek hai. Jab ready hon, hey Butler bolein.",
                    "Koi baat nahi. Baad mein."
                ))
            }
            lang.startsWith("te") -> "Parvaaledu. Taraavaata hey Butler cheppandi."
            lang.startsWith("ta") -> "Paravailla. Pinna hey Butler sollungal."
            lang.startsWith("kn") -> "Parvaagilla. Naantara hey Butler heli."
            lang.startsWith("ml") -> "Kaaryanilla. Pinne hey Butler parayo."
            lang.startsWith("pa") -> "Koi gal nahi. Baad vich hey Butler kaho."
            lang.startsWith("gu") -> "Kaem nahi. Pachhi hey Butler kaho."
            else -> "No worries. Say hey Butler when you're ready."
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SELECTION PROMPT
    // ══════════════════════════════════════════════════════════════════════

    fun askSelection(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_sel_r", listOf(
                    "Kaun si brand chahiye?", "Naam bataiye.", "Kaunsa?"
                ))
                else -> pick("hi_sel", listOf(
                    "Kaunsa chahiye. Brand ka naam bataiye.",
                    "Brand ka naam bataiye.",
                    "Kaunsa doon?",
                    "Bataiye, kaunsa chahiye."
                ))
            }
            lang.startsWith("te") -> "Edi kavali? Peyru cheppandi."
            lang.startsWith("ta") -> "Edu vendum? Peyar sollungal."
            lang.startsWith("kn") -> "Yavudu beku? Hesaru heli."
            lang.startsWith("ml") -> "Etha veno? Peru parayo."
            lang.startsWith("pa") -> "Kihra chahida? Naam dasao."
            lang.startsWith("gu") -> "Kyu joiye? Naam bolo."
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> "Which one? Say the name."
                else -> pick("en_sel", listOf(
                    "Which one? Say the brand name.",
                    "Which brand?",
                    "Say the name you want."
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CART EMPTY
    // ══════════════════════════════════════════════════════════════════════

    fun cartEmpty(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_empty", listOf(
                "Cart khaali hai. Kya chahiye?",
                "Abhi kuch nahi. Kya laana hai?"
            ))
            lang.startsWith("te") -> "Ippudu emi ledu. Emi kavali?"
            lang.startsWith("ta") -> "Ippo onnum illa. Enna vendum?"
            lang.startsWith("kn") -> "Enu illa. Enu beku?"
            lang.startsWith("ml") -> "Ippol ontumilla. Enthu veno?"
            lang.startsWith("pa") -> "Cart khaali. Ki chahida?"
            lang.startsWith("gu") -> "Cart khaali. Shu joiye?"
            else -> "Cart is empty. What would you like?"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ITEM REMOVED
    // ══════════════════════════════════════════════════════════════════════

    fun itemRemoved(productName: String, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_removed", listOf(
                "$short hata diya.", "Theek hai, $short hata diya.", "$short remove kar diya."
            ))
            lang.startsWith("te") -> "$short remove chesaanu."
            else -> "$short removed."
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ORDER ERROR
    // ══════════════════════════════════════════════════════════════════════

    fun orderError(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_err", listOf(
                "Network thodi slow hai. Phir try karein?",
                "Ek second. Phir koshish karte hain."
            ))
            lang.startsWith("te") -> "Network problem. Malli try cheyyana?"
            else -> pick("en_err", listOf("Hit a snag. Shall we try again?", "Network hiccup. Retry?"))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SESSION EXPIRED
    // ══════════════════════════════════════════════════════════════════════

    fun sessionExpired(lang: String): String {
        return when {
            lang.startsWith("hi") -> "Session expire ho gayi. Hey Butler bolein dobara."
            lang.startsWith("te") -> "Session expire aindi. Hey Butler antunnaru."
            else -> "Session expired. Say hey Butler to start again."
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // REORDER GREETING
    // ══════════════════════════════════════════════════════════════════════

    fun reorderGreeting(name: String, items: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_reorder", listOf(
                "Haan $name... $items chahiye ya kuch naya?",
                "$name, pichli baar $items liye the. Wahi doon?",
                "Haan $name... $items order karoon?"
            ))
            lang.startsWith("te") -> "$name garu, $items kavala?"
            lang.startsWith("ta") -> "$name, $items venuma?"
            lang.startsWith("kn") -> "$name, $items beku?"
            lang.startsWith("ml") -> "$name, $items venum?"
            lang.startsWith("pa") -> "$name ji, $items chahida?"
            lang.startsWith("gu") -> "$name, $items joiye?"
            else -> pick("en_reorder", listOf(
                "$name, same as last time. $items?",
                "Hey $name. $items again?",
                "$name, want the usual. $items?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // QUANTITY ASK
    // ══════════════════════════════════════════════════════════════════════

    fun askQuantity(productName: String, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_qty", listOf(
                "$short kitna chahiye. Ek kilo, do kilo?",
                "Kitna laoon $short?",
                "$short. Ek packet ya zyada?"
            ))
            lang.startsWith("te") -> "$short entha kavali? Oka kilo, rendo kilo?"
            lang.startsWith("ta") -> "$short evvalavu vendum?"
            lang.startsWith("kn") -> "$short eshtu beku?"
            lang.startsWith("ml") -> "$short ethra veno?"
            lang.startsWith("pa") -> "$short kitna chahida?"
            lang.startsWith("gu") -> "$short ketlun joiye?"
            else -> pick("en_qty", listOf(
                "How much $short. One kilo, two kilo?",
                "$short. One pack or more?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RELATED SUGGESTION (private helper)
    // ══════════════════════════════════════════════════════════════════════

    private fun getRelatedSuggestion(productName: String?, lang: String): String? {
        if (productName == null) return null
        val p  = productName.lowercase()
        val hi = mapOf(
            "rice"   to "daal",   "dal"    to "chawal", "oil"    to "atta",
            "atta"   to "tel",    "milk"   to "bread",  "bread"  to "makhan",
            "tea"    to "cheeni", "sugar"  to "chai",   "ghee"   to "daal",
            "eggs"   to "bread",  "curd"   to "chawal", "butter" to "bread",
            "chawal" to "daal",   "daal"   to "chawal", "tel"    to "atta"
        )
        val en = mapOf(
            "rice"   to "dal",    "dal"    to "rice",   "oil"    to "atta",
            "atta"   to "oil",    "milk"   to "bread",  "bread"  to "butter",
            "tea"    to "sugar",  "sugar"  to "tea",    "ghee"   to "dal",
            "eggs"   to "bread",  "curd"   to "rice",   "butter" to "bread"
        )
        val base = lang.substringBefore("-").lowercase().take(2)
        return when (base) {
            "hi" -> hi.entries.firstOrNull { p.contains(it.key) }?.value
            else -> en.entries.firstOrNull { p.contains(it.key) }?.value
        }
    }
}