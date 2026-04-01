package com.demo.butler_voice_app.utils

import com.demo.butler_voice_app.ai.UserMood

/**
 * ButlerPersonalityEngine — replaces ButlerPhraseBank for all conversational moments.
 *
 * DESIGN PRINCIPLES:
 *
 * 1. VARIETY — every response pool has 4-6 variants. A session tracker ensures
 *    the same phrase is never repeated back-to-back. Real humans don't read scripts.
 *
 * 2. MOOD-ADAPTIVE — responses get shorter and more direct when the user is
 *    RUSHED or FRUSTRATED. When the user is CALM or HAPPY, Butler can be warmer.
 *    Butler never adds cheerful filler when the user sounds stressed.
 *
 * 3. CONTEXT-AWARE — the cart size, session position (first item vs. third item),
 *    and what the user just said all influence what Butler says next.
 *
 * 4. NATURAL INDIAN CONVERSATIONAL MARKERS — uses "haan", "achha", "theek hai",
 *    "wahi", "bas", "aur kuch" naturally — the way a Guntur/Vijayawada shopkeeper
 *    would actually speak on the phone.
 *
 * 5. NEVER STARTS WITH BUTLER'S NAME — humans don't announce themselves mid-sentence.
 *    "बढ़िया!" not "Butler says: बढ़िया!"
 */
object ButlerPersonalityEngine {

    // ── Session de-duplication ─────────────────────────────────────────────────
    // Tracks the last phrase index used per pool key so we never repeat immediately.
    private val lastUsed = mutableMapOf<String, Int>()

    private fun pick(key: String, variants: List<String>, mood: UserMood = UserMood.CALM): String {
        val last = lastUsed[key] ?: -1
        val available = variants.indices.filter { it != last }
        val idx = if (available.isEmpty()) 0 else available.random()
        lastUsed[key] = idx
        return variants[idx]
    }

    fun resetSession() { lastUsed.clear() }

    // ══════════════════════════════════════════════════════════════════════════
    // PRODUCT ADDED CONFIRMATION
    // Called after user picks a brand. Short, warm, moves forward immediately.
    // ══════════════════════════════════════════════════════════════════════════

    fun itemAdded(productName: String, lang: String, mood: UserMood, cartSize: Int): String {
        val short = productName.split(" ").take(2).joinToString(" ") {
            it.replaceFirstChar { c -> c.uppercase() }
        }
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_added_rushed", listOf(
                    "हो गया।",
                    "$short रख दिया।",
                    "Done।",
                    "ठीक।"
                ))
                else -> when (cartSize) {
                    1 -> pick("hi_added_first", listOf(
                        "बढ़िया! $short ले लिया।",
                        "हाँ! $short। और कुछ?",
                        "$short हो गया। और?",
                        "ठीक है, $short। क्या और?"
                    ))
                    2 -> pick("hi_added_second", listOf(
                        "और $short भी।",
                        "$short भी रख दिया।",
                        "हाँ, $short।",
                        "$short — done।"
                    ))
                    else -> pick("hi_added_more", listOf(
                        "$short भी।",
                        "ठीक।",
                        "हो गया।",
                        "रख दिया।"
                    ))
                }
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("te_added_rushed", listOf(
                    "అయింది.",
                    "$short పెట్టాను.",
                    "Done.",
                    "సరే."
                ))
                else -> pick("te_added", listOf(
                    "బాగుంది! $short తీసుకున్నాను.",
                    "హా! $short. ఇంకా?",
                    "$short అయింది. మరేమైనా?",
                    "ఓకే, $short."
                ))
            }
            lang.startsWith("ta") -> pick("ta_added", listOf(
                "சரி! $short வாங்கினேன்.",
                "$short ஆச்சு. வேற?",
                "ஓகே $short."
            ))
            lang.startsWith("kn") -> pick("kn_added", listOf(
                "ಆಯ್ತು! $short ತೆಗೆದುಕೊಂಡೆ.",
                "$short ಆಯ್ತು. ಮತ್ತೇನಾದರೂ?",
                "ಓಕೆ $short."
            ))
            lang.startsWith("ml") -> pick("ml_added", listOf(
                "ശരി! $short എടുത്തു.",
                "$short ആയി. വേറേ?",
                "ഓകെ $short."
            ))
            lang.startsWith("pa") -> pick("pa_added", listOf(
                "ਚੰਗਾ! $short ਲੈ ਲਿਆ।",
                "$short ਹੋ ਗਿਆ। ਹੋਰ?",
                "ਠੀਕ $short."
            ))
            lang.startsWith("gu") -> pick("gu_added", listOf(
                "સારું! $short લઈ લીધું.",
                "$short થઈ ગયું. વધુ?",
                "ઓકે $short."
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_added_rushed", listOf(
                    "Done.",
                    "$short added.",
                    "Got it.",
                    "Okay."
                ))
                else -> when (cartSize) {
                    1 -> pick("en_added_first", listOf(
                        "Got it! $short added.",
                        "$short — done! Anything else?",
                        "Nice, $short added. What else?",
                        "Okay! $short. More?"
                    ))
                    2 -> pick("en_added_second", listOf(
                        "$short added too.",
                        "And $short.",
                        "Got $short as well.",
                        "Done, $short added."
                    ))
                    else -> pick("en_added_more", listOf(
                        "$short too.",
                        "Added.",
                        "Done.",
                        "Got it."
                    ))
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ASK MORE — "anything else?"
    // ══════════════════════════════════════════════════════════════════════════

    fun askMore(lang: String, mood: UserMood, cartSize: Int, lastProduct: String? = null): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_more_rushed", listOf(
                    "और?",
                    "कुछ और?",
                    "बस?"
                ))
                else -> {
                    val suggestion = getRelatedSuggestion(lastProduct, "hi")
                    if (suggestion != null && cartSize == 1) {
                        pick("hi_more_suggest", listOf(
                            "$suggestion भी चाहिए?",
                            "$suggestion लेना है?",
                            "और $suggestion?"
                        ))
                    } else {
                        pick("hi_more", listOf(
                            "और क्या चाहिए?",
                            "कुछ और?",
                            "और कोई चीज़?",
                            "बाकी?",
                            "और कुछ लेना है?"
                        ))
                    }
                }
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("te_more_rushed", listOf(
                    "ఇంకా?", "మరేమైనా?", "అంతేనా?"
                ))
                else -> pick("te_more", listOf(
                    "ఇంకా ఏం కావాలి?",
                    "మరేమైనా కావాలా?",
                    "ఇంకా?",
                    "మరి?",
                    "ఇంకేమైనా?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_more", listOf(
                "இன்னும் என்ன வேணும்?", "வேற ஏதாவது?", "இன்னும்?"
            ))
            lang.startsWith("kn") -> pick("kn_more", listOf(
                "ಇನ್ನೇನು ಬೇಕು?", "ಬೇರೆ ಏನಾದರೂ?", "ಮತ್ತೆ?"
            ))
            lang.startsWith("ml") -> pick("ml_more", listOf(
                "ഇനി എന്ത് വേണം?", "വേറേ ഏതെങ്കിലും?", "ഇനിയും?"
            ))
            lang.startsWith("pa") -> pick("pa_more", listOf(
                "ਹੋਰ ਕੀ ਚਾਹੀਦਾ?", "ਕੁਝ ਹੋਰ?", "ਬੱਸ?"
            ))
            lang.startsWith("gu") -> pick("gu_more", listOf(
                "બીજું શું જોઈએ?", "કંઈ વધુ?", "બસ?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_more_rushed", listOf(
                    "More?", "Anything else?", "That's it?"
                ))
                else -> pick("en_more", listOf(
                    "Anything else?",
                    "What else do you need?",
                    "More?",
                    "Anything more?",
                    "That all?"
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRODUCT SELECTION PROMPT — replaces "1, 2 ya 3 bolein"
    // ══════════════════════════════════════════════════════════════════════════

    fun askSelection(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_sel_rushed", listOf(
                    "कौन सा?", "बोलो?", "कौन?"
                ))
                else -> pick("hi_sel", listOf(
                    "कौन सा चाहिए?",
                    "नाम बोलें।",
                    "कौन लेना है?",
                    "कौन सा?",
                    "पसंद कीजिए।"
                ))
            }
            lang.startsWith("te") -> pick("te_sel", listOf(
                "ఏది కావాలి?", "పేరు చెప్పండి.", "ఏది?", "ఎంచుకోండి."
            ))
            lang.startsWith("ta") -> pick("ta_sel", listOf(
                "எது வேணும்?", "பேர் சொல்லுங்கள்.", "எது?"
            ))
            lang.startsWith("kn") -> pick("kn_sel", listOf(
                "ಯಾವುದು ಬೇಕು?", "ಹೆಸರು ಹೇಳಿ.", "ಯಾವುದು?"
            ))
            lang.startsWith("ml") -> pick("ml_sel", listOf(
                "ഏത് വേണം?", "പേര് പറയൂ.", "ഏത്?"
            ))
            lang.startsWith("pa") -> pick("pa_sel", listOf(
                "ਕਿਹੜਾ ਚਾਹੀਦਾ?", "ਨਾਮ ਦੱਸੋ।", "ਕਿਹੜਾ?"
            ))
            lang.startsWith("gu") -> pick("gu_sel", listOf(
                "કયું જોઈએ?", "નામ બોલો.", "કયું?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_sel_rushed", listOf(
                    "Which one?", "Say the name.", "Which?"
                ))
                else -> pick("en_sel", listOf(
                    "Which one do you want?",
                    "Say the brand name.",
                    "Which brand?",
                    "Which one?",
                    "Your choice?"
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIDN'T HEAR — retry prompts
    // ══════════════════════════════════════════════════════════════════════════

    fun didntHear(lang: String, mood: UserMood, retryCount: Int): String {
        return when {
            lang.startsWith("hi") -> when {
                mood == UserMood.FRUSTRATED -> pick("hi_retry_frustrated", listOf(
                    "माफ करना, सुन नहीं पाया। जरा जोर से?",
                    "माफी। फिर बोलें।",
                    "Sorry, सुना नहीं। दोबारा?"
                ))
                retryCount >= 3 -> pick("hi_retry_many", listOf(
                    "माइक के पास आकर बोलें।",
                    "थोड़ा जोर से बोलिए।",
                    "नेटवर्क है? फिर try करें।"
                ))
                retryCount == 2 -> pick("hi_retry_2", listOf(
                    "एक बार और बोलें।",
                    "फिर?",
                    "हाँ?"
                ))
                else -> pick("hi_retry_1", listOf(
                    "सुना नहीं। फिर बोलें।",
                    "हाँ?",
                    "क्या?",
                    "फिर बोलो।",
                    "दोबारा?"
                ))
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED -> pick("te_retry_frustrated", listOf(
                    "క్షమించండి, వినలేదు. కొంచెం గట్టిగా?",
                    "మళ్ళీ చెప్పండి."
                ))
                else -> pick("te_retry", listOf(
                    "వినలేదు. మళ్ళీ?",
                    "హా?",
                    "ఏమిటి?",
                    "మళ్ళీ చెప్పండి."
                ))
            }
            lang.startsWith("ta") -> pick("ta_retry", listOf(
                "கேக்கல. மீண்டும்?", "ஹா?", "என்னன்னு?"
            ))
            lang.startsWith("kn") -> pick("kn_retry", listOf(
                "ಕೇಳಿಸಲಿಲ್ಲ. ಮತ್ತೆ?", "ಹಾ?", "ಏನು?"
            ))
            lang.startsWith("ml") -> pick("ml_retry", listOf(
                "കേൾക്കുന്നില്ല. വീണ്ടും?", "ഹ?", "എന്ത്?"
            ))
            lang.startsWith("pa") -> pick("pa_retry", listOf(
                "ਸੁਣਿਆ ਨਹੀਂ। ਫਿਰ?", "ਹਾਂ?", "ਕੀ?"
            ))
            lang.startsWith("gu") -> pick("gu_retry", listOf(
                "સંભળાયું નહીં. ફરી?", "હ?", "શું?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED -> pick("en_retry_frustrated", listOf(
                    "Sorry, didn't catch that. Louder?",
                    "My bad, say again?"
                ))
                else -> pick("en_retry", listOf(
                    "Didn't catch that.",
                    "Sorry?",
                    "Come again?",
                    "Say that again?",
                    "Hmm?"
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WHAT DO YOU WANT — open question
    // ══════════════════════════════════════════════════════════════════════════

    fun askWhatYouWant(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_want_rushed", listOf(
                    "क्या चाहिए?",
                    "बोलो?",
                    "हाँ?"
                ))
                else -> pick("hi_want", listOf(
                    "क्या चाहिए?",
                    "क्या लेना है?",
                    "बोलिए।",
                    "बताइए, क्या मँगाना है?",
                    "हाँ, बोलो?"
                ))
            }
            lang.startsWith("te") -> pick("te_want", listOf(
                "ఏం కావాలి?", "చెప్పండి.", "ఏమి?", "బోలండి."
            ))
            lang.startsWith("ta") -> pick("ta_want", listOf(
                "என்ன வேணும்?", "சொல்லுங்கள்.", "என்ன?"
            ))
            lang.startsWith("kn") -> pick("kn_want", listOf(
                "ಏನು ಬೇಕು?", "ಹೇಳಿ.", "ಏನು?"
            ))
            lang.startsWith("ml") -> pick("ml_want", listOf(
                "എന്ത് വേണം?", "പറയൂ.", "എന്ത്?"
            ))
            lang.startsWith("pa") -> pick("pa_want", listOf(
                "ਕੀ ਚਾਹੀਦਾ?", "ਦੱਸੋ।", "ਕੀ?"
            ))
            lang.startsWith("gu") -> pick("gu_want", listOf(
                "શું જોઈએ?", "કહો.", "શું?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("en_want_rushed", listOf(
                    "What do you need?", "Go ahead.", "Yes?"
                ))
                else -> pick("en_want", listOf(
                    "What would you like?",
                    "Go ahead.",
                    "What can I get you?",
                    "Yes, tell me?",
                    "What do you need?"
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT DONE — reaction after user says "paid"
    // ══════════════════════════════════════════════════════════════════════════

    fun paymentDone(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_paydone", listOf(
                "हो गया!",
                "परफेक्ट!",
                "ठीक है!",
                "बढ़िया!"
            ))
            lang.startsWith("te") -> pick("te_paydone", listOf(
                "అయింది!", "పర్ఫెక్ట్!", "సరే!"
            ))
            lang.startsWith("ta") -> pick("ta_paydone", listOf("ஆச்சு!", "சரி!"))
            lang.startsWith("kn") -> pick("kn_paydone", listOf("ಆಯ್ತು!", "ಸರಿ!"))
            lang.startsWith("ml") -> pick("ml_paydone", listOf("ആയി!", "ശരി!"))
            lang.startsWith("pa") -> pick("pa_paydone", listOf("ਹੋ ਗਿਆ!", "ਵਧੀਆ!"))
            lang.startsWith("gu") -> pick("gu_paydone", listOf("થઈ ગયું!", "બઢિયા!"))
            else -> pick("en_paydone", listOf("Got it!", "Perfect!", "Great!"))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT ASK — "did you pay?"
    // ══════════════════════════════════════════════════════════════════════════

    fun askIfPaid(lang: String, mode: String, amount: String): String {
        return when {
            lang.startsWith("hi") -> when (mode) {
                "upi" -> pick("hi_ask_paid_upi", listOf(
                    "payment हो गई? $amount UPI पर?",
                    "$amount भेज दिया?",
                    "UPI done?"
                ))
                "card" -> pick("hi_ask_paid_card", listOf(
                    "card से $amount हो गया?",
                    "payment complete?",
                    "card done?"
                ))
                else -> pick("hi_ask_paid_qr", listOf(
                    "QR scan हो गया?",
                    "$amount pay हुआ?",
                    "done?"
                ))
            }
            lang.startsWith("te") -> pick("te_ask_paid", listOf(
                "payment అయిందా?", "$amount pay చేశారా?", "done?"
            ))
            else -> pick("en_ask_paid", listOf(
                "Did you pay? $amount via $mode?",
                "Payment done?",
                "All set?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ORDER CONFIRM — before placing order
    // ══════════════════════════════════════════════════════════════════════════

    fun confirmOrder(items: String, total: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_confirm", listOf(
                "$items — $total। Order करूँ?",
                "Total $total — $items। हाँ?",
                "$items, $total। Place करूँ?",
                "देखो — $items। $total बनता है। करूँ?"
            ))
            lang.startsWith("te") -> pick("te_confirm", listOf(
                "$items — $total. Order చేయనా?",
                "Total $total — $items. చేయాలా?",
                "$items, $total. Place చేయనా?"
            ))
            lang.startsWith("ta") -> pick("ta_confirm", listOf(
                "$items — $total. Order போடனா?", "Total $total — $items. போடலாமா?"
            ))
            lang.startsWith("kn") -> pick("kn_confirm", listOf(
                "$items — $total. Order ಮಾಡಲಾ?", "Total $total — $items. ಮಾಡಲಾ?"
            ))
            lang.startsWith("ml") -> pick("ml_confirm", listOf(
                "$items — $total. Order ചെയ്യട്ടെ?", "Total $total — $items. ചെയ്യട്ടെ?"
            ))
            lang.startsWith("pa") -> pick("pa_confirm", listOf(
                "$items — $total. Order ਕਰਾਂ?", "ਕੁੱਲ $total — $items. ਕਰਾਂ?"
            ))
            lang.startsWith("gu") -> pick("gu_confirm", listOf(
                "$items — $total. Order કરું?", "કુલ $total — $items. કરું?"
            ))
            else -> pick("en_confirm", listOf(
                "$items — $total. Shall I order?",
                "Total $total for $items. Place the order?",
                "$items, $total. Go ahead?",
                "Confirm — $items, $total?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT MODE INSTRUCTIONS
    // ══════════════════════════════════════════════════════════════════════════

    fun upiInstruction(amount: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_upi", listOf(
                "UPI से $amount भेजो — butler@upi पर।",
                "$amount UPI से। butler@upi।",
                "butler@upi पर $amount।"
            ))
            lang.startsWith("te") -> pick("te_upi", listOf(
                "UPI ద్వారా $amount పంపండి — butler@upi కి.",
                "$amount UPI లో. butler@upi."
            ))
            else -> pick("en_upi", listOf(
                "Send $amount via UPI to butler@upi.",
                "Pay $amount — UPI ID is butler@upi.",
                "$amount to butler@upi on UPI."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NO PRODUCT FOUND
    // ══════════════════════════════════════════════════════════════════════════

    fun productNotFound(itemName: String, lang: String): String {
        val short = itemName.take(20)
        return when {
            lang.startsWith("hi") -> pick("hi_notfound", listOf(
                "$short अभी नहीं मिला। और क्या चाहिए?",
                "वो नहीं है। कुछ और?",
                "$short नहीं है। बाकी?",
                "Sorry, $short नहीं। और?"
            ))
            lang.startsWith("te") -> pick("te_notfound", listOf(
                "$short దొరకలేదు. ఇంకా?",
                "అది లేదు. వేరేది?",
                "Sorry, $short లేదు."
            ))
            else -> pick("en_notfound", listOf(
                "Couldn't find $short. Anything else?",
                "$short not available. Something else?",
                "Sorry, no $short. What else?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CART EMPTY
    // ══════════════════════════════════════════════════════════════════════════

    fun cartEmpty(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_empty", listOf(
                "अभी कुछ नहीं है। क्या चाहिए?",
                "cart खाली है। बोलो?",
                "कुछ add नहीं है। क्या लेना है?"
            ))
            lang.startsWith("te") -> pick("te_empty", listOf(
                "ఇంకా ఏమీ లేదు. ఏం కావాలి?",
                "Cart ఖాళీగా ఉంది. చెప్పండి?"
            ))
            else -> pick("en_empty", listOf(
                "Cart's empty. What would you like?",
                "Nothing added yet. What do you need?",
                "Cart is empty. Go ahead?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GIVE UP / TOO MANY RETRIES
    // ══════════════════════════════════════════════════════════════════════════

    fun giveUp(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED -> pick("hi_giveup_frus", listOf(
                    "कोई बात नहीं। माइक चेक करें। hey butler से दोबारा शुरू करें।",
                    "ठीक है, बाद में बात करते हैं।"
                ))
                else -> pick("hi_giveup", listOf(
                    "ठीक है। जब तैयार हों, hey butler बोलें।",
                    "कोई बात नहीं। बाद में।",
                    "ठीक। ready होने पर बुलाएं।"
                ))
            }
            lang.startsWith("te") -> pick("te_giveup", listOf(
                "సరే. తర్వాత hey butler చెప్పండి.",
                "ఫర్వాలేదు. తర్వాత మాట్లాడదాం."
            ))
            else -> pick("en_giveup", listOf(
                "No worries. Say hey butler when you're ready.",
                "That's fine. Talk later.",
                "Okay, just say hey butler when ready."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERNAL: related product suggestion
    // ══════════════════════════════════════════════════════════════════════════

    private fun getRelatedSuggestion(productName: String?, lang: String): String? {
        if (productName == null) return null
        val p = productName.lowercase()
        val pairs = mapOf(
            "rice" to mapOf("hi" to "दाल", "te" to "పప్పు", "en" to "dal"),
            "dal"  to mapOf("hi" to "चावल", "te" to "అన్నం", "en" to "rice"),
            "oil"  to mapOf("hi" to "आटा", "te" to "గోధుమ పిండి", "en" to "atta"),
            "atta" to mapOf("hi" to "तेल", "te" to "నూనె", "en" to "oil"),
            "milk" to mapOf("hi" to "ब्रेड", "te" to "రొట్టె", "en" to "bread"),
            "bread" to mapOf("hi" to "मक्खन", "te" to "వెన్న", "en" to "butter"),
            "tea"  to mapOf("hi" to "चीनी", "te" to "చక్కెర", "en" to "sugar")
        )
        val baseLang = lang.substringBefore("-").lowercase().take(2)
        return pairs.entries.firstOrNull { p.contains(it.key) }?.value?.let { map ->
            map[baseLang] ?: map["en"]
        }
    }
}