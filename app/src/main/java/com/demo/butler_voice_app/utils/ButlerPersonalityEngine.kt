package com.demo.butler_voice_app.utils

import com.demo.butler_voice_app.ai.UserMood
import java.util.Calendar

/**
 * ButlerPersonalityEngine — investor-grade voice templates.
 *
 * DESIGN PRINCIPLES (from final voice audit):
 *   1. Always use {name} — every response is personalized
 *   2. "…" pause rhythm — ElevenLabs pauses naturally at ellipsis
 *   3. Short sentences — TTS reads cleanly, no run-ons
 *   4. Devanagari anchor in EVERY Hindi string — prevents TranslationManager
 *      from partial-translating Hinglish → mixed script
 *   5. Warm, unhurried tone — like a helpful kirana assistant
 *
 * RESPONSE TEMPLATES (all 9 implemented):
 *   1. WAKE_RESPONSE       → greeting()
 *   2. ASK_TYPE            → askProductType()
 *   3. CONFIRM_ADD_PRODUCT → confirmAddProduct()
 *   4. ITEM_ADDED          → itemAdded()
 *   5. CONFIRM_ADD_NEXT    → confirmAddNext()
 *   6. CONFIRM_ORDER       → confirmOrder()
 *   7. ASK_PAYMENT         → askPaymentMode()
 *   8. PAYMENT_UPI         → upiInstruction()
 *   9. ORDER_SUCCESS       → orderPlaced()
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
    // 1. WAKE RESPONSE — Template: "Haan, batayein {name}… aaj kya chahiye?"
    // ═════════════════════════════════════════════════════════════════════

    fun greeting(name: String, lang: String, lastProduct: String?, mood: UserMood): String {
        val time = timeSlot()
        return when {
            lang.startsWith("hi") -> when {
                lastProduct != null -> pick("hi_greet_ret", listOf(
                    // Devanagari anchor "हाँ" → TranslationManager skips ✅
                    "हाँ, batayein $name… aaj aapko kya chahiye?",
                    "हाँ $name ji… kya chahiye aaj?",
                    "$name ji, बताइए… क्या मंगवाना है?",
                    "हाँ $name… kya la doon aaj?"
                ))
                time == "morning" -> pick("hi_greet_morn", listOf(
                    "हाँ, batayein $name ji… subah mein kya chahiye?",
                    "हाँ $name… good morning. Kya la doon?"
                ))
                time == "evening" -> pick("hi_greet_eve", listOf(
                    "हाँ, batayein $name… shaam mein kya chahiye?",
                    "हाँ $name ji… kya mangwaaein aaj?"
                ))
                else -> pick("hi_greet_new", listOf(
                    "हाँ, batayein $name… aaj aapko kya chahiye?",
                    "हाँ $name ji… kya chahiye?",
                    "$name, बताइए… क्या मंगवाएं?",
                    "हाँ $name… kya la doon?"
                ))
            }
            lang.startsWith("te") -> when {
                lastProduct != null -> pick("te_greet_ret", listOf(
                    "Cheppandi $name garu… emi kavali inniki?",
                    "Haa $name… emi teestara?"
                ))
                else -> pick("te_greet_base", listOf(
                    "Cheppandi $name garu… inniki emi kavali?",
                    "Namaskaram $name… emi teestara?",
                    "Haa $name garu… emi kavali?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_greet", listOf(
                "Solunga $name… indru enna vendum?",
                "Aamam $name… enna tharen?"
            ))
            lang.startsWith("kn") -> pick("kn_greet", listOf(
                "Heli $name… enu beku indhu?",
                "Aamdu $name… enu tegolabeeku?"
            ))
            lang.startsWith("ml") -> pick("ml_greet", listOf(
                "Parayo $name… innu enthu veno?",
                "Aamm $name… enthu venam?"
            ))
            lang.startsWith("pa") -> pick("pa_greet", listOf(
                "Dasao $name ji… aaj ki chahida?",
                "Haan $name… ki mangwaiye?"
            ))
            lang.startsWith("gu") -> pick("gu_greet", listOf(
                "Kaho $name… aaj shu joiye?",
                "Haan $name… shu mangaavun?"
            ))
            lang.startsWith("mr") -> pick("mr_greet", listOf(
                "Sanga $name… aaj kaay havay?",
                "Haan $name… kaay aanu?"
            ))
            else -> when {
                lastProduct != null -> pick("en_greet_ret", listOf(
                    "Yes, tell me $name… what do you need today?",
                    "Hi $name… what can I get you?",
                    "Yes $name, go ahead… what do you need?"
                ))
                time == "morning" -> pick("en_greet_morn", listOf(
                    "Good morning $name… what do you need today?",
                    "Yes $name, tell me… what can I get you?"
                ))
                else -> pick("en_greet_new", listOf(
                    "Yes, tell me $name… what do you need today?",
                    "Hi $name… what can I get you?",
                    "Yes $name… go ahead."
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 2. ASK PRODUCT TYPE — Template: "Aapko kaunsa rice chahiye {name} —
    //    basmati, brown rice ya normal rice?"
    // ═════════════════════════════════════════════════════════════════════

    fun askProductType(category: String, name: String, lang: String): String {
        val cat = category.lowercase()
        return when {
            lang.startsWith("hi") -> when {
                cat.contains("rice") || cat.contains("chawal") -> pick("hi_type_rice", listOf(
                    "आपको कौन सा rice chahiye $name — basmati, brown rice ya normal?",
                    "कौन सा rice lena hai $name — basmati, brown ya regular?",
                    "$name, rice mein basmati chahiye, brown rice, या normal?"
                ))
                cat.contains("dal") || cat.contains("daal") -> pick("hi_type_dal", listOf(
                    "आपको कौन सी daal chahiye $name — toor, moong ya masoor?",
                    "कौन सी daal $name — arhar, moong, masoor ya urad?",
                    "$name, daal mein toor chahiye, moong, या masoor?"
                ))
                cat.contains("oil") || cat.contains("tel") -> pick("hi_type_oil", listOf(
                    "आपको कौन सा oil chahiye $name — mustard, sunflower ya coconut?",
                    "कौन सा tel $name — sarson, sunflower ya coconut oil?"
                ))
                cat.contains("atta") || cat.contains("flour") -> pick("hi_type_atta", listOf(
                    "कौन सा atta chahiye $name — wheat, multigrain ya maida?",
                    "$name, atta mein wheat hai, multigrain, या maida?"
                ))
                cat.contains("milk") || cat.contains("doodh") -> pick("hi_type_milk", listOf(
                    "कौन सा doodh chahiye $name — full fat, toned ya skimmed?",
                    "$name, milk mein full cream chahiye ya toned?"
                ))
                cat.contains("tea") || cat.contains("chai") -> pick("hi_type_tea", listOf(
                    "कौन सी chai chahiye $name — loose leaf, tea bags ya kadak chai?",
                    "$name, chai mein Tata chahiye, Red Label ya koi aur?"
                ))
                else -> pick("hi_type_generic", listOf(
                    "कौन सा $category chahiye $name — koi specific brand?",
                    "$name, $category mein koi preference hai?"
                ))
            }
            lang.startsWith("te") -> when {
                cat.contains("rice") -> "Meeru $name garu endha rice kavali — basmati, brown rice leda regular?"
                cat.contains("dal")  -> "Endha pappu kavali $name — kandi, pesara leda masoor?"
                cat.contains("oil")  -> "Endha nune kavali $name — avise, sunflower leda coconut?"
                else -> "Endha type kavali $name?"
            }
            lang.startsWith("ta") -> when {
                cat.contains("rice") -> "Enna rice vendum $name — basmati, brown rice, regular?"
                cat.contains("dal")  -> "Enna paruppu vendum $name — toor, moong, masoor?"
                else -> "Enna type vendum $name?"
            }
            lang.startsWith("kn") -> when {
                cat.contains("rice") -> "Yaava akki beku $name — basmati, brown, regular?"
                cat.contains("dal")  -> "Yaava bele beku $name — toor, moong, masoor?"
                else -> "Yaava type beku $name?"
            }
            lang.startsWith("ml") -> when {
                cat.contains("rice") -> "Enth ari veno $name — basmati, brown rice, regular?"
                cat.contains("dal")  -> "Enth parippu veno $name — toor, moong, masoor?"
                else -> "Enth type veno $name?"
            }
            lang.startsWith("pa") -> when {
                cat.contains("rice") -> "Kihra chawal chahida $name — basmati, brown, regular?"
                cat.contains("dal")  -> "Kihra daal chahida $name — toor, moong, masoor?"
                else -> "Kihra type chahida $name?"
            }
            lang.startsWith("gu") -> when {
                cat.contains("rice") -> "Kyun chaval joiye $name — basmati, brown, regular?"
                cat.contains("dal")  -> "Kyu dal joiye $name — toor, moong, masoor?"
                else -> "Kyu type joiye $name?"
            }
            else -> when {
                cat.contains("rice") -> "Which rice would you like $name — basmati, brown rice, or regular?"
                cat.contains("dal")  -> "Which dal $name — toor, moong, masoor, or urad?"
                cat.contains("oil")  -> "Which oil $name — mustard, sunflower, or coconut?"
                else -> "What type of $category $name — any preference?"
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 3. CONFIRM ADD PRODUCT — Template: "{product} ₹{price} ka hai…
    //    kya ise cart mein add karna hai {name}?"
    // ═════════════════════════════════════════════════════════════════════

    fun confirmAddProduct(name: String, productName: String, price: Int, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_confirm_add", listOf(
                // Devanagari "का" → TranslationManager skips ✅
                "$short ₹$price का है… cart mein add karna hai $name?",
                "$short ₹$price का है $name… ise le loon?",
                "$short — ₹$price. $name, add करना है?"
            ))
            lang.startsWith("te") -> pick("te_confirm_add", listOf(
                "$short ₹$price untundi $name… cart lo pettanaa?",
                "$short ₹$price. $name, teesukuntaaraa?"
            ))
            lang.startsWith("ta") -> pick("ta_confirm_add", listOf(
                "$short ₹$price irukku $name… cart-la podanuma?",
                "$short ₹$price — $name, vangattuma?"
            ))
            lang.startsWith("kn") -> pick("kn_confirm_add", listOf(
                "$short ₹$price ide $name… cart ge haakona?",
                "$short ₹$price — $name, tegolabeekaa?"
            ))
            lang.startsWith("ml") -> pick("ml_confirm_add", listOf(
                "$short ₹$price anu $name… cart-il idattea?",
                "$short ₹$price — $name, edukkattea?"
            ))
            lang.startsWith("pa") -> pick("pa_confirm_add", listOf(
                "$short ₹$price da hai $name… cart vich pa deyaan?",
                "$short ₹$price — $name, la laiye?"
            ))
            lang.startsWith("gu") -> pick("gu_confirm_add", listOf(
                "$short ₹$price chhe $name… cart ma nakhun?",
                "$short ₹$price — $name, levun?"
            ))
            else -> pick("en_confirm_add", listOf(
                "$short costs ₹$price… should I add it to your cart $name?",
                "$short is ₹$price $name… want this one?",
                "$short — ₹$price. Add it $name?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 4. ITEM ADDED — Template: "Theek hai, ise cart mein add kar diya…
    //    aur kuch chahiye aapko {name}?"
    //
    // NOTE: This function now includes the "ask more" in one combined string
    // so the voice flow is seamless: confirm + next ask without a pause gap.
    // ═════════════════════════════════════════════════════════════════════

    fun itemAdded(name: String, productName: String, lang: String, mood: UserMood, cartSize: Int): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val full  = productName.split(" ").take(3)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_added_r", listOf(
                    // Short, Devanagari anchor → TranslationManager skips ✅
                    "ठीक है $name… aur kuch?",
                    "$short — हो गया। Kuch aur $name?",
                    "ठीक, cart में आ गया। Bas?"
                ))
                else -> when (cartSize) {
                    1 -> pick("hi_added_1", listOf(
                        // Devanagari "ठीक है" anchor ✅
                        "ठीक है, $full cart mein add kar diya… aur kuch chahiye aapko $name?",
                        "ठीक, $full le liya $name… aur kuch mangwaaein?",
                        "$full — हो गया $name. Aur kuch chahiye?"
                    ))
                    2 -> pick("hi_added_2", listOf(
                        "ठीक है, $full bhi add kar diya… aur kuch chahiye $name?",
                        "$full bhi — हो गया। Kuch aur $name?"
                    ))
                    else -> pick("hi_added_n", listOf(
                        "ठीक, $short bhi. Aur kuch $name?",
                        "हो गया। Kuch aur chahiye $name?"
                    ))
                }
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("te_added_r", listOf(
                    "$short ayindi $name… inkaa emi?",
                    "Sare $name… inkaa?",
                    "Ayindi — inkaa emi kavali?"
                ))
                else -> pick("te_added", listOf(
                    "Ayindi $name, $full cart lo pettaanu… inkaa emi kavali?",
                    "Sare, $full add chesaanu $name… marokkati?",
                    "$full teecchukonnaanu $name… inkaa?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_added", listOf(
                "Sari $name, $full cart-la potturein… vera enna vendum?",
                "Aachi $name, $full vaanginen… innoru enna?",
                "$full — sari. Vera enna vendum $name?"
            ))
            lang.startsWith("kn") -> pick("kn_added", listOf(
                "Aayitu $name, $full cart ge haakondi… bere enu beku?",
                "Sari $name, $full tagondi… inenu?",
                "$full — aayitu. Bere enu beku $name?"
            ))
            lang.startsWith("ml") -> pick("ml_added", listOf(
                "Ayi $name, $full cart-il itthu… vere enthu veno?",
                "Sheri $name, $full edutthu… innum enthu?",
                "$full — ayi. Vere enthu veno $name?"
            ))
            lang.startsWith("pa") -> pick("pa_added", listOf(
                "Theek hai $name, $full cart vich pa ditta… hor ki chahida?",
                "Ho gaya $name, $full le litta… kuch hor?",
                "$full — ho gaya. Hor ki chahida $name?"
            ))
            lang.startsWith("gu") -> pick("gu_added", listOf(
                "Saru $name, $full cart ma nakhi didhun… biju shu joiye?",
                "Thai gayu $name, $full lai lidhun… kahi biju?",
                "$full — thai gayu. Biju shu joiye $name?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_added_r", listOf(
                    "Done $name… anything else?",
                    "$short added. More?",
                    "Got it $name. Anything else?"
                ))
                else -> when (cartSize) {
                    1 -> pick("en_added_1", listOf(
                        "Alright, $full added to your cart… do you need anything else $name?",
                        "Got it $name, $full is in your cart… anything else?",
                        "$full — added. What else do you need $name?"
                    ))
                    2 -> pick("en_added_2", listOf(
                        "Done $name, $full added as well… anything else?",
                        "$full too — added. More $name?",
                        "Got $full too $name… anything else?"
                    ))
                    else -> pick("en_added_n", listOf(
                        "$short added $name. Anything else?",
                        "Done. More $name?",
                        "Got it. Anything else $name?"
                    ))
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 5. CONFIRM ADD NEXT — Template: "{product} ₹{price} ka hai…
    //    kya ise bhi cart mein add karna hai {name}?"
    //
    // Used when suggesting a follow-on item (dal after rice, etc.)
    // ═════════════════════════════════════════════════════════════════════

    fun confirmAddNext(name: String, productName: String, price: Int, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_confirm_next", listOf(
                // "का" is Devanagari anchor ✅
                "$short ₹$price का है… kya ise bhi cart mein add karna hai $name?",
                "$short — ₹$price. $name, yeh bhi le loon?",
                "$short ₹$price का है $name… ise bhi add karoon?"
            ))
            lang.startsWith("te") -> pick("te_confirm_next", listOf(
                "$short ₹$price untundi $name… idi kooda cart lo pettanaa?",
                "$short ₹$price $name — idi kooda teesukuntaaraa?"
            ))
            lang.startsWith("ta") -> pick("ta_confirm_next", listOf(
                "$short ₹$price irukku $name… ithayum cart-la podanuma?",
                "$short ₹$price — indhaiyum vangattuma $name?"
            ))
            lang.startsWith("kn") -> pick("kn_confirm_next", listOf(
                "$short ₹$price ide $name… ithanu cart ge haakona?",
                "$short ₹$price — ithanu kooda $name?"
            ))
            lang.startsWith("ml") -> pick("ml_confirm_next", listOf(
                "$short ₹$price anu $name… ithu koodi cart-il idattea?",
                "$short ₹$price — ithum $name?"
            ))
            lang.startsWith("pa") -> pick("pa_confirm_next", listOf(
                "$short ₹$price da hai $name… eda vi cart vich pa deyaan?",
                "$short ₹$price — eda vi laye $name?"
            ))
            lang.startsWith("gu") -> pick("gu_confirm_next", listOf(
                "$short ₹$price chhe $name… aane pan nakhu?",
                "$short ₹$price — aane pan levun $name?"
            ))
            else -> pick("en_confirm_next", listOf(
                "$short costs ₹$price… should I add this as well $name?",
                "$short is ₹$price $name… add this too?",
                "$short — ₹$price. This one too $name?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 6. CONFIRM ORDER — Template: "Theek hai… bas itna hi hai na {name}?
    //    Kya order place karna hai?"
    // ═════════════════════════════════════════════════════════════════════

    fun confirmOrder(name: String, items: String, total: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_confirm_order", listOf(
                // "ठीक है" Devanagari anchor ✅
                "ठीक है $name… $items. Total $total — order place karna hai?",
                "ठीक है… bas itna hi hai na $name? $items, $total — order karoon?",
                "$items. Total $total. $name, ठीक है — order de doon?"
            ))
            lang.startsWith("te") -> pick("te_confirm_order", listOf(
                "Sare $name… $items. Total $total — order pettanaa?",
                "Antena $name, $items — $total. Order ivvana?"
            ))
            lang.startsWith("ta") -> pick("ta_confirm_order", listOf(
                "Sari $name… $items. Mottam $total — order podanuma?",
                "Porum $name, $items — $total. Order pannanuma?"
            ))
            lang.startsWith("kn") -> pick("kn_confirm_order", listOf(
                "Saaku $name… $items. Otha $total — order madona?",
                "Aayitu $name, $items — $total. Order madana?"
            ))
            lang.startsWith("ml") -> pick("ml_confirm_order", listOf(
                "Mathi $name… $items. Aakoode $total — order cheyattea?",
                "Saadho $name, $items — $total. Order cheyamo?"
            ))
            lang.startsWith("pa") -> pick("pa_confirm_order", listOf(
                "Theek hai $name… $items. Kull $total — order karan?",
                "Bass $name, $items — $total. Order pa deyaan?"
            ))
            lang.startsWith("gu") -> pick("gu_confirm_order", listOf(
                "Saru $name… $items. Kul $total — order karun?",
                "Bas $name, $items — $total. Order mookun?"
            ))
            else -> pick("en_confirm_order", listOf(
                "Alright $name… that's $items. Total $total — should I place your order?",
                "Alright, that's all right $name? $items, $total — shall I order?",
                "Got $items, $total total $name… place the order?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 7. ASK PAYMENT MODE — Template:
    //    "Payment kaise karna chahenge — UPI, card ya cash?"
    // ═════════════════════════════════════════════════════════════════════

    fun askPaymentMode(name: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_ask_payment", listOf(
                // "Payment" doesn't need Devanagari — word is borrowed into Hindi
                // Adding "करना" as anchor ✅
                "$name, payment करना है — UPI, card ya cash?",
                "Payment कैसे करना chahenge $name — UPI, card ya cash?",
                "UPI se doge ya card se $name?"
            ))
            lang.startsWith("te") -> pick("te_ask_payment", listOf(
                "$name, ela pay chestaru — UPI, card leda cash?",
                "Payment ela $name — UPI, card?"
            ))
            lang.startsWith("ta") -> pick("ta_ask_payment", listOf(
                "$name, epdi pay pannuvenga — UPI, card la cash?",
                "Epdi pay $name — UPI, card?"
            ))
            lang.startsWith("kn") -> pick("kn_ask_payment", listOf(
                "$name, hege pay madtira — UPI, card leda cash?",
                "Payment hege $name — UPI, card?"
            ))
            lang.startsWith("ml") -> pick("ml_ask_payment", listOf(
                "$name, engane pay cheyyum — UPI, card allengil cash?",
                "Payment engane $name — UPI, card?"
            ))
            lang.startsWith("pa") -> pick("pa_ask_payment", listOf(
                "$name, kiven paisa dena — UPI, card ya cash?",
                "Payment kiven $name — UPI, card?"
            ))
            lang.startsWith("gu") -> pick("gu_ask_payment", listOf(
                "$name, kem pay karisho — UPI, card ke cash?",
                "Payment kem $name — UPI, card?"
            ))
            else -> pick("en_ask_payment", listOf(
                "How would you like to pay $name — UPI, card, or cash?",
                "$name — UPI, card, or cash?",
                "How do you want to pay $name?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 8. UPI INSTRUCTION — Template:
    //    "Theek hai, UPI se payment kar lijiye… ho jaaye to batayein."
    //
    // Never say "butler@upi" verbally — show on screen.
    // ═════════════════════════════════════════════════════════════════════

    fun upiInstruction(amount: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_upi", listOf(
                // "ठीक है" Devanagari anchor ✅
                "ठीक है, UPI se payment kar lijiye… ho jaaye to batayein.",
                "ठीक है, $amount UPI se bhej dijiye… bata dena ho jaane par.",
                "UPI ID screen पर है. $amount bhejein… ho jaaye to bataiye."
            ))
            lang.startsWith("te") -> pick("te_upi", listOf(
                "Sare, UPI lo $amount pampu… ayinaaka cheppandi.",
                "UPI ID screen lo undi — $amount pampu. Chesaaka cheppandi."
            ))
            lang.startsWith("ta") -> pick("ta_upi", listOf(
                "Sari, UPI-la $amount anuppu… aanadhum sollunga.",
                "UPI ID screen-la irukku — $amount anuppu. Done aana sollunga."
            ))
            lang.startsWith("kn") -> pick("kn_upi", listOf(
                "Sari, UPI nalli $amount kali… madidamele heli.",
                "UPI ID screen mele ide — $amount kali. Aadamele heli."
            ))
            lang.startsWith("ml") -> pick("ml_upi", listOf(
                "Sheri, UPI-l $amount aykku… cheshal parayo.",
                "UPI ID screen-il kaanam — $amount aykku. Ayal parayo."
            ))
            lang.startsWith("pa") -> pick("pa_upi", listOf(
                "Theek hai, UPI ton $amount bhejo… ho jaaye te dasao.",
                "UPI ID screen te hai — $amount bhejo. Done hone te dasao."
            ))
            lang.startsWith("gu") -> pick("gu_upi", listOf(
                "Saru, UPI thi $amount moklo… thai jaay tyare kaho.",
                "UPI ID screen par chhe — $amount moklo. Done thay tyare kaho."
            ))
            else -> pick("en_upi", listOf(
                "Alright, please complete the payment using UPI… let me know once done.",
                "UPI ID is on screen — send $amount and let me know.",
                "Please pay $amount via UPI… say done when it's through."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // 9. ORDER SUCCESS — Template: "Perfect… aapka order place ho gaya
    //    {name}. 30 minute mein aa jayega."
    // ═════════════════════════════════════════════════════════════════════

    fun orderPlaced(name: String, orderId: String, amount: String, etaMins: Int, lang: String): String {
        val eta = if (etaMins > 0) etaMins else 30
        return when {
            lang.startsWith("hi") -> pick("hi_order_placed", listOf(
                // "परफेक्ट" Devanagari anchor ✅
                "परफेक्ट… aapka order place हो गया hai $name. Lagbhag $eta minute mein aa jayega.",
                "हो गया $name! Order $orderId confirm. $eta minute mein aa jayegi delivery.",
                "ठीक है $name… order $orderId place हो गया. $eta minute mein pahunchega.",
                "परफेक्ट $name! $orderId confirm — $eta minute mein delivery aa jayegi."
            ))
            lang.startsWith("te") -> pick("te_order_placed", listOf(
                "Baagundi $name… meeru order confirm ayindi. $eta nimishaallo vastundi. Dhanyavaadaalu.",
                "$name garu, order $orderId confirm — $eta minutes lo deliveri. Chala shukriya."
            ))
            lang.startsWith("ta") -> pick("ta_order_placed", listOf(
                "Perfect $name… ungal order confirm. $eta nimidathil varum. Nandri.",
                "$name, order $orderId ready — $eta nimidam. Nandri."
            ))
            lang.startsWith("kn") -> pick("kn_order_placed", listOf(
                "Chennagi $name… nimage order confirm aayitu. $eta nimisha hage baruttade. Dhanyavada.",
                "$name, order $orderId — $eta nimisha. Dhanyavada."
            ))
            lang.startsWith("ml") -> pick("ml_order_placed", listOf(
                "Perfect $name… ningalude order confirmed. $eta minuteinu orathe ettum. Nandri.",
                "$name, $orderId — $eta minute. Nandri."
            ))
            lang.startsWith("pa") -> pick("pa_order_placed", listOf(
                "Perfect $name… aapda order place ho gaya. $eta minute vich aa jauga. Shukriya.",
                "$name, $orderId confirm — $eta minute. Bahut shukriya."
            ))
            lang.startsWith("gu") -> pick("gu_order_placed", listOf(
                "Perfect $name… aapno order place thai gayu. $eta minute ma aavse. Shukriya.",
                "$name, $orderId confirm — $eta minute. Shukriya."
            ))
            else -> pick("en_order_placed", listOf(
                "Perfect… your order has been placed $name. It will arrive in about $eta minutes.",
                "Done $name! Order $orderId confirmed — arriving in $eta minutes. Thank you.",
                "All set $name… $orderId placed. $eta minutes to delivery. Thanks!"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // REORDER GREETING
    // ═════════════════════════════════════════════════════════════════════

    fun reorderGreeting(name: String, items: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_reorder", listOf(
                "हाँ $name… $items chahiye ya kuch naya?",
                "$name, pichli baar $items liye the — wahi doon?",
                "हाँ $name… $items order karoon?"
            ))
            lang.startsWith("te") -> pick("te_reorder", listOf(
                "$name garu, $items kavala?",
                "Haa $name… $items meeru mandistara?"
            ))
            lang.startsWith("ta") -> pick("ta_reorder", listOf(
                "$name, $items venuma?", "$name, $items poduma?"
            ))
            lang.startsWith("kn") -> pick("kn_reorder", listOf(
                "$name, $items beku?", "$name, $items again beku?"
            ))
            lang.startsWith("ml") -> pick("ml_reorder", listOf(
                "$name, $items venum?", "$name, $items again venam?"
            ))
            lang.startsWith("pa") -> pick("pa_reorder", listOf(
                "$name ji, $items chahida?", "$name, $items phir mangwaiye?"
            ))
            lang.startsWith("gu") -> pick("gu_reorder", listOf(
                "$name, $items joiye?", "$name, $items again joiye?"
            ))
            else -> pick("en_reorder", listOf(
                "$name, same as last time — $items?",
                "Hey $name! $items again?",
                "$name, want the usual — $items?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // QUANTITY ASK
    // ═════════════════════════════════════════════════════════════════════

    fun askQuantity(productName: String, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_qty", listOf(
                "$short कितना chahiye? Ek kilo, do kilo?",
                "कितना loon $short?",
                "$short — ek packet ya zyada?"
            ))
            lang.startsWith("te") -> pick("te_qty", listOf(
                "$short entha kavali? Oka kilo, rendo kilo?", "Enta kavali?"
            ))
            lang.startsWith("ta") -> pick("ta_qty", listOf("$short evvalavu vendum?", "Evvalavu?"))
            lang.startsWith("kn") -> pick("kn_qty", listOf("$short eshtu beku?", "Eshtu?"))
            lang.startsWith("ml") -> pick("ml_qty", listOf("$short ethra veno?", "Ethra?"))
            lang.startsWith("pa") -> pick("pa_qty", listOf("$short kitna chahida?", "Kitna?"))
            lang.startsWith("gu") -> pick("gu_qty", listOf("$short ketlun joiye?", "Ketlun?"))
            else -> pick("en_qty", listOf(
                "How much $short? One kilo, two kilo?",
                "How many $short?",
                "$short — one pack or more?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // PRODUCT SELECTION PROMPT
    // ═════════════════════════════════════════════════════════════════════

    fun askSelection(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_sel_r", listOf(
                    "कौन सा?", "नाम बोलें।", "कौनसा?"
                ))
                else -> pick("hi_sel", listOf(
                    "कौनसा chahiye? Naam bolein.",
                    "ब्रांड ka naam batao.",
                    "कौनसा doon?",
                    "Naam bolein."
                ))
            }
            lang.startsWith("te") -> pick("te_sel", listOf("Edi kavali? Peyru cheppandi.", "Edi?"))
            lang.startsWith("ta") -> pick("ta_sel", listOf("Edu vendum? Peyar sollungal.", "Edu?"))
            lang.startsWith("kn") -> pick("kn_sel", listOf("Yavudu beku? Hesaru heli.", "Yavudu?"))
            lang.startsWith("ml") -> pick("ml_sel", listOf("Etha veno? Peru parayo.", "Eth?"))
            lang.startsWith("pa") -> pick("pa_sel", listOf("Kihra chahida? Naam dasao.", "Kihra?"))
            lang.startsWith("gu") -> pick("gu_sel", listOf("Kyu joiye? Naam bolo.", "Kyu?"))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_sel_r", listOf(
                    "Which one?", "Say the name.", "Which?"
                ))
                else -> pick("en_sel", listOf(
                    "Which one? Say the brand name.",
                    "Which brand?",
                    "Say the name you want."
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // ASK MORE (internal — now only used when suggestion not available)
    // ═════════════════════════════════════════════════════════════════════

    fun askMore(name: String, lang: String, mood: UserMood, cartSize: Int, lastProduct: String?): String {
        val suggestion = getRelatedSuggestion(lastProduct, lang)
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_more_r", listOf(
                    "और कुछ $name?", "कुछ aur?", "बस?", "क्या chahiye?"
                ))
                else -> if (suggestion != null && cartSize == 1) {
                    pick("hi_more_sug", listOf(
                        "$suggestion bhi chahiye $name?",
                        "$suggestion भी saath mein doon?",
                        "Aur $suggestion $name?"
                    ))
                } else {
                    pick("hi_more", listOf(
                        "बस itna $name? Ya kuch aur?",
                        "कुछ aur chahiye $name?",
                        "Aur kya mangwaaein $name?"
                    ))
                }
            }
            lang.startsWith("te") -> pick("te_more", listOf(
                "Inkaa emi kavali $name?", "Marokkati?", "Chaaladaa $name?"
            ))
            lang.startsWith("ta") -> pick("ta_more", listOf(
                "Vera enna $name?", "Innoru?", "Porum $name?"
            ))
            lang.startsWith("kn") -> pick("kn_more", listOf(
                "Inenu $name?", "Bere?", "Saaku $name?"
            ))
            lang.startsWith("ml") -> pick("ml_more", listOf(
                "Innum $name?", "Vere?", "Mathi $name?"
            ))
            lang.startsWith("pa") -> pick("pa_more", listOf(
                "Hor ki $name?", "Kuch hor?", "Bass $name?"
            ))
            lang.startsWith("gu") -> pick("gu_more", listOf(
                "Biju shu $name?", "Kahi biju?", "Bas $name?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_more_r", listOf(
                    "More $name?", "Anything else?", "That it?"
                ))
                else -> pick("en_more", listOf(
                    "Anything else $name?",
                    "What else do you need $name?",
                    "Shall I add anything else $name?",
                    "That all for today $name?"
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // PAYMENT CONFIRMATION ASK
    // ═════════════════════════════════════════════════════════════════════

    fun askIfPaid(lang: String, mode: String, amount: String): String {
        return when {
            lang.startsWith("hi") -> when (mode) {
                "upi"  -> pick("hi_paid_upi", listOf(
                    "हो गया payment? $amount आ गए?",
                    "$amount bhej diya?",
                    "Payment हो गया?"
                ))
                "card" -> pick("hi_paid_card", listOf(
                    "Card से हो गया?", "Payment complete?", "Card done?"
                ))
                else   -> pick("hi_paid_qr", listOf(
                    "QR scan हो गया?", "$amount pay हुआ?", "हो गया?"
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
                "Payment done?", "Did you send $amount?", "All set?", "Through?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // PAYMENT DONE REACTION
    // ═════════════════════════════════════════════════════════════════════

    fun paymentDone(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_paydone", listOf(
                "अच्छा, हो गया।", "ठीक है।", "हो गया।"
            ))
            lang.startsWith("te") -> pick("te_paydone", listOf("Sare, ayindi.", "Ok."))
            lang.startsWith("ta") -> pick("ta_paydone", listOf("Sari.", "Aachi.", "Ok."))
            lang.startsWith("kn") -> pick("kn_paydone", listOf("Sari.", "Aaytu.", "Ok."))
            lang.startsWith("ml") -> pick("ml_paydone", listOf("Sari.", "Ayi.", "Ok."))
            lang.startsWith("pa") -> pick("pa_paydone", listOf("Theek hai.", "Ho gaya.", "Ok."))
            lang.startsWith("gu") -> pick("gu_paydone", listOf("Saru.", "Thai gayu.", "Ok."))
            else -> pick("en_paydone", listOf("Got it.", "Okay.", "Received.", "Done."))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // PRODUCT NOT FOUND
    // ═════════════════════════════════════════════════════════════════════

    fun productNotFound(itemName: String, lang: String): String {
        val short = itemName.take(20)
        return when {
            lang.startsWith("hi") -> pick("hi_notfound", listOf(
                "वो abhi nahi hai. Kuch aur?",
                "$short नहीं mila. Koi aur brand?",
                "$short stock में नहीं. Kya loon phir?"
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
                "$short not in stock. Want something else?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // CART EMPTY
    // ═════════════════════════════════════════════════════════════════════

    fun cartEmpty(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_empty", listOf(
                "Cart खाली है. Kya chahiye?", "अभी kuch nahi. Bolo kya lena hai."
            ))
            lang.startsWith("te") -> pick("te_empty", listOf("Ippudu emi ledu. Emi kavali?"))
            lang.startsWith("ta") -> pick("ta_empty", listOf("Ippo onnum illa. Enna vendum?"))
            lang.startsWith("kn") -> pick("kn_empty", listOf("Enu illa. Enu beku?"))
            lang.startsWith("ml") -> pick("ml_empty", listOf("Ippol ontumilla. Enthu veno?"))
            lang.startsWith("pa") -> pick("pa_empty", listOf("Cart khaali. Ki chahida?"))
            lang.startsWith("gu") -> pick("gu_empty", listOf("Cart khaali. Shu joiye?"))
            else -> pick("en_empty", listOf("Cart's empty. What would you like?"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // DIDN'T HEAR / RETRY
    // ═════════════════════════════════════════════════════════════════════

    fun didntHear(lang: String, mood: UserMood, retryCount: Int): String {
        return when {
            lang.startsWith("hi") -> when {
                mood == UserMood.FRUSTRATED -> pick("hi_retry_frus", listOf(
                    "कुछ suna nahi. Thoda aur oonchi awaaz mein boliye.",
                    "समझ nahi aaya. Phir se boliye.",
                    "आवाज़ thodi kam hai. Paas aakar boliye."
                ))
                retryCount >= 4 -> pick("hi_retry_many", listOf(
                    "Mic के paas aakar boliye.", "थोड़ा jor se boliye."
                ))
                retryCount >= 2 -> pick("hi_retry_2", listOf(
                    "समझ nahi aaya. Phir se boliye.",
                    "कुछ suna nahi. Thoda aur oonchi awaaz mein boliye."
                ))
                else -> pick("hi_retry", listOf(
                    "कुछ suna nahi. Thoda aur oonchi awaaz mein boliye.",
                    "हाँ?", "क्या?", "फिर boliye."
                ))
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED -> pick("te_retry_frus", listOf(
                    "Vinaledu. Clearly cheppandi.", "Malli cheppandi."
                ))
                else -> pick("te_retry", listOf("Haa?", "Emi?", "Malli cheppandi."))
            }
            lang.startsWith("ta") -> pick("ta_retry", listOf("Aa?", "Enna?", "Solunga."))
            lang.startsWith("kn") -> pick("kn_retry", listOf("Ha?", "Enu?", "Heli."))
            lang.startsWith("ml") -> pick("ml_retry", listOf("Ha?", "Enthu?", "Parayo."))
            lang.startsWith("pa") -> pick("pa_retry", listOf("Ha?", "Ki?", "Dasao."))
            lang.startsWith("gu") -> pick("gu_retry", listOf("Ha?", "Shu?", "Kaho."))
            else -> when (mood) {
                UserMood.FRUSTRATED -> pick("en_retry_frus", listOf(
                    "Didn't catch that. A bit louder?",
                    "Sorry, say that again?"
                ))
                else -> pick("en_retry", listOf(
                    "Sorry?", "Come again?", "Say that again?", "Didn't catch that."
                ))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // GIVE UP
    // ═════════════════════════════════════════════════════════════════════

    fun giveUp(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED -> pick("hi_giveup_frus", listOf(
                    "कोई baat nahi. Jab ready hon, hey Butler bolein.",
                    "चलिए, baad mein baat karte hain."
                ))
                else -> pick("hi_giveup", listOf(
                    "ठीक hai. Jab ready hon, hey Butler bolein.",
                    "कोई baat nahi. Baad mein."
                ))
            }
            lang.startsWith("te") -> pick("te_giveup", listOf(
                "Parvaaledu. Taraavaata hey Butler cheppandi.", "Sare. Taraavaata matlaadam."
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
                "That's fine. Talk later."
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // ITEM REMOVED
    // ═════════════════════════════════════════════════════════════════════

    fun itemRemoved(productName: String, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_removed", listOf(
                "$short हटा diya.", "ठीक hai, $short nahi.", "$short remove kar diya."
            ))
            lang.startsWith("te") -> pick("te_removed", listOf(
                "$short tiriyinchaanu.", "$short remove chesaanu."
            ))
            else -> pick("en_removed", listOf("$short removed.", "Done, $short gone."))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // ORDER ERROR
    // ═════════════════════════════════════════════════════════════════════

    fun orderError(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_err", listOf(
                "Network थोड़ी slow hai. Phir try karein?",
                "एक second. Phir koshish karte hain."
            ))
            lang.startsWith("te") -> "Network problem. Malli try cheyyana?"
            else -> pick("en_err", listOf(
                "Hit a snag. Shall we try again?", "Network hiccup. Retry?"
            ))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // SESSION EXPIRED
    // ═════════════════════════════════════════════════════════════════════

    fun sessionExpired(lang: String): String {
        return when {
            lang.startsWith("hi") -> "Session expire हो गई. Hey Butler bolein dobara."
            lang.startsWith("te") -> "Session expire aindi. Hey Butler antunnaru."
            else -> "Session expired. Say hey Butler to start again."
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private fun getRelatedSuggestion(productName: String?, lang: String): String? {
        if (productName == null) return null
        val p  = productName.lowercase()
        // Hindi: Devanagari words so suggestion strings stay pure Devanagari ✅
        val hi = mapOf(
            "rice" to "दाल",    "dal"   to "चावल",  "oil"  to "आटा",
            "atta" to "तेल",    "milk"  to "bread",  "bread" to "मक्खन",
            "tea"  to "चीनी",   "sugar" to "चाय",    "ghee" to "दाल",
            "eggs" to "bread",  "curd"  to "चावल",   "butter" to "bread",
            "chawal" to "दाल",  "daal"  to "चावल",   "tel"   to "आटा"
        )
        val te = mapOf(
            "rice" to "pappu", "dal" to "annam", "oil" to "pindi",
            "milk" to "rotte", "tea" to "chakkera"
        )
        val en = mapOf(
            "rice" to "dal",   "dal" to "rice",    "oil" to "atta",
            "atta" to "oil",   "milk" to "bread",  "bread" to "butter",
            "tea"  to "sugar", "sugar" to "tea",   "ghee" to "dal",
            "eggs" to "bread", "curd" to "rice",   "butter" to "bread"
        )
        val base = lang.substringBefore("-").lowercase().take(2)
        return when (base) {
            "hi" -> hi.entries.firstOrNull { p.contains(it.key) }?.value
            "te" -> te.entries.firstOrNull { p.contains(it.key) }?.value
            else -> en.entries.firstOrNull { p.contains(it.key) }?.value
        }
    }
}