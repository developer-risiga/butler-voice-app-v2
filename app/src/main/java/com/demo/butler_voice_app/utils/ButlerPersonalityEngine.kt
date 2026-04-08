package com.demo.butler_voice_app.utils

import com.demo.butler_voice_app.EmotionTone
import com.demo.butler_voice_app.ai.UserMood
import java.util.Calendar

/**
 * ButlerPersonalityEngine — warm, professional voice templates.
 *
 * TONE PHILOSOPHY:
 *   - Butler NEVER sounds more energetic than the user.
 *   - CALM / TIRED  → WARM       (calm, reassuring, helpful)
 *   - FRUSTRATED    → EMPATHETIC (never match frustration)
 *   - RUSHED        → NORMAL     (efficient, no fluff)
 *   - EXCITED EmotionTone removed from all standard flows.
 *
 * UserMood values: CALM, FRUSTRATED, RUSHED, TIRED
 */
object ButlerPersonalityEngine {

    // ══════════════════════════════════════════════════════════════════════
    // MOOD-ADAPTIVE TONE FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    fun toneForGreeting(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.WARM
    }

    fun toneForProductList(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.NORMAL
    }

    fun toneForConfirmAdd(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.WARM
    }

    fun toneForItemAdded(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.WARM
    }

    fun toneForConfirmOrder(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.WARM
    }

    fun toneForPaymentAsk(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.WARM
    }

    fun toneForPaymentUPI(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.RUSHED -> EmotionTone.NORMAL
        else            -> EmotionTone.WARM
    }

    // KEY FIX: was hardcoded EXCITED — now mood-adaptive.
    // TIRED user + EXCITED Butler = jarring and unprofessional.
    fun toneForPaymentDone(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.WARM
    }

    fun toneForOrderPlaced(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.WARM
    }

    @Suppress("UNUSED_PARAMETER")
    fun toneForRetry(mood: UserMood = UserMood.CALM): EmotionTone   = EmotionTone.EMPATHETIC
    @Suppress("UNUSED_PARAMETER")
    fun toneForGiveUp(mood: UserMood = UserMood.CALM): EmotionTone  = EmotionTone.EMPATHETIC
    @Suppress("UNUSED_PARAMETER")
    fun toneForError(mood: UserMood = UserMood.CALM): EmotionTone   = EmotionTone.EMPATHETIC
    fun toneForEmergency(): EmotionTone                              = EmotionTone.EMERGENCY

    fun toneForSubstitute(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        else                -> EmotionTone.NORMAL
    }

    fun toneForAskMore(mood: UserMood): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.WARM
    }

    // ── First-time greeting tones ─────────────────────────────────────────
    // Welcome = WARM (slow, personal — first meeting deserves unhurried pace)
    // Teaser  = NORMAL (brisk, informative — category list at slow speed drags)
    fun toneForFirstTimeWelcome(): EmotionTone = EmotionTone.WARM
    fun toneForFirstTimeTeaser():  EmotionTone = EmotionTone.NORMAL

    // ── Show more products tone ───────────────────────────────────────────
    fun toneForShowMore(mood: UserMood = UserMood.CALM): EmotionTone = when (mood) {
        UserMood.FRUSTRATED -> EmotionTone.EMPATHETIC
        UserMood.RUSHED     -> EmotionTone.NORMAL
        else                -> EmotionTone.NORMAL
    }

    // ── Legacy zero-arg wrappers (backward compat with existing call sites) ─
    fun toneForGreeting()    = toneForGreeting(UserMood.CALM)
    fun toneForProductList() = toneForProductList(UserMood.CALM)
    fun toneForConfirmAdd()  = toneForConfirmAdd(UserMood.CALM)
    fun toneForItemAdded()   = toneForItemAdded(UserMood.CALM)
    fun toneForConfirmOrder()= toneForConfirmOrder(UserMood.CALM)
    fun toneForPaymentAsk()  = toneForPaymentAsk(UserMood.CALM)
    fun toneForPaymentUPI()  = toneForPaymentUPI(UserMood.CALM)
    fun toneForPaymentDone() = toneForPaymentDone(UserMood.CALM)
    fun toneForOrderPlaced() = toneForOrderPlaced(UserMood.CALM)
    fun toneForRetry()       = toneForRetry(UserMood.CALM)
    fun toneForGiveUp()      = toneForGiveUp(UserMood.CALM)
    fun toneForError()       = toneForError(UserMood.CALM)
    fun toneForSubstitute()  = toneForSubstitute(UserMood.CALM)

    // ══════════════════════════════════════════════════════════════════════
    // VARIANT PICKER
    // ══════════════════════════════════════════════════════════════════════

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
    // FIRST-TIME USER GREETING
    //
    // Call ONLY when isNewUser = true (first session, zero orders).
    // Returns Pair(welcome, teaser) — speak them sequentially:
    //
    //   val (welcome, teaser) = ButlerPersonalityEngine.firstTimeGreeting(name, lang)
    //   ttsManager.speak(welcome, lang, toneForFirstTimeWelcome()) {
    //       ttsManager.speak(teaser, lang, toneForFirstTimeTeaser()) {
    //           startListening()
    //       }
    //   }
    //
    // WHY TWO CALLS:
    //   Welcome at WARM speed (0.80) is personal and unhurried.
    //   Teaser at NORMAL speed (0.87) is brisk — listing 8 categories
    //   at 0.80 sounds tedious. Natural pause between calls lets the
    //   user absorb the welcome before the product list begins.
    // ══════════════════════════════════════════════════════════════════════

    fun firstTimeGreeting(name: String, lang: String): Pair<String, String> {
        return when {
            lang.startsWith("hi") -> Pair(
                // ── Welcome — warm, personal ──────────────────────────────────
                pick("hi_ft_welcome", listOf(
                    "Haan $name ji... aapka swagat hai. Main aapka Butler hoon, aapki grocery mein madad karne ke liye.",
                    "Haan $name ji... bahut accha hua aap aaye. Main Butler hoon, aapki kirana ki zaroorat ke liye.",
                    "Namaste $name ji. Aapka swagat hai. Main Butler hoon, aapki grocery ki har zaroorat mein saath hoon."
                )),
                // ── Category teaser + open question ──────────────────────────
                pick("hi_ft_teaser", listOf(
                    "Hamare paas chawal, daal, tel, atta, masale, biscuit, doodh aur bahut kuch hai. Aaj kya laana hai $name?",
                    "Chawal, daal, tel, atta se lekar masale, biscuit aur doodh tak, sab milta hai. Kya chahiye aaj $name?",
                    "Hamare paas chawal, daal, tel, atta, chai, masale, doodh aur bahut kuch milega. Batao $name, kya laana hai?"
                ))
            )
            lang.startsWith("te") -> Pair(
                pick("te_ft_welcome", listOf(
                    "Namaskaram $name garu. Meeru raavadam chala santosham. Nenu mee Butler ni, mee grocery ki sahaayam cheyyadaaniki.",
                    "Swagatam $name garu. Nenu Butler ni, mee kirana avasaraalaku."
                )),
                pick("te_ft_teaser", listOf(
                    "Meeru kooda biyyam, pappu, nune, atta, masaalu, biscuit, paalu anni unnayi. Inniki emi kavali $name garu?",
                    "Biyyam, pappu, nune nunchi masaalu, biscuit, paalu varaku anni unnayi. Emi teestara $name?"
                ))
            )
            lang.startsWith("ta") -> Pair(
                pick("ta_ft_welcome", listOf(
                    "Vanakkam $name. Neenga vara kaadha romba santhosham. Naan ungal Butler, ungal grocery thaevaikku.",
                    "Vanakkam $name. Naan Butler, ungal kirana thaevaikku."
                )),
                pick("ta_ft_teaser", listOf(
                    "Engalukkidam arisi, paruppu, ennai, maavu, masala, biscuit, paal ellaam irukku. Indru enna vendum $name?",
                    "Arisi, paruppu, ennai, maavu mudhal masala, paal varai ellaam irukku. Enna vendum $name?"
                ))
            )
            lang.startsWith("kn") -> Pair(
                pick("kn_ft_welcome", listOf(
                    "Swagata $name. Neevu banda santhoshavaaythu. Naanu nimma Butler, nimma grocery ge sahaaya maadalu.",
                    "Namaskara $name. Naanu Butler, nimma kirana avasharakaagi."
                )),
                pick("kn_ft_teaser", listOf(
                    "Naavu akki, bele, enne, atta, masala, biscuit, haalu ellavu idabekaagide. Indhu enu beku $name?",
                    "Akki, bele, enne, atta ninda masala, haalu varegu sab ide. Enu beku $name?"
                ))
            )
            lang.startsWith("ml") -> Pair(
                pick("ml_ft_welcome", listOf(
                    "Swagatam $name. Ningal vannathu valare santhosham. Ente peyar Butler, ningalude grocery avasharangal kai kaaryam cheyyaan.",
                    "Namaskaram $name. Njan Butler aanu, ningalude kirana avasharangalkku."
                )),
                pick("ml_ft_teaser", listOf(
                    "Njangalude pakkal ari, parippu, enna, atta, masala, biscuit, paal ellam und. Innu enthu veno $name?",
                    "Ari, parippu, enna, atta muthal masala, paal vare ellam und. Enthu venam $name?"
                ))
            )
            lang.startsWith("pa") -> Pair(
                pick("pa_ft_welcome", listOf(
                    "Sat sri akal $name ji. Aapda aana bahut changaa lagga. Main aapda Butler haan, aapdi grocery vich madad layi.",
                    "Swagatam $name ji. Main Butler haan, aapdi kirana di zaroorat layi."
                )),
                pick("pa_ft_teaser", listOf(
                    "Saade paas chawal, daal, tel, atta, masale, biscuit, doodh aur bahut kuch hai. Aaj ki chahida $name ji?",
                    "Chawal, daal, tel, atta to masale, biscuit, doodh tak sab milega. Ki mangwaoge $name?"
                ))
            )
            lang.startsWith("gu") -> Pair(
                pick("gu_ft_welcome", listOf(
                    "Jai shri Krishna $name. Tame aavyaa e khub saru thayu. Hun tamaro Butler chhu, tamari grocery ni zaroorat mate.",
                    "Swagatam $name. Hu Butler chhu, tamari kirana ni zaroorat mate."
                )),
                pick("gu_ft_teaser", listOf(
                    "Amari paase chawal, dal, tel, atta, masala, biscuit, doodh ane ghanu badhu che. Aaj shu joiye $name?",
                    "Chawal, dal, tel, atta thi masala, biscuit, doodh sudhi badhu maḷashe. Shu mangaavun $name?"
                ))
            )
            else -> Pair(
                // ── English welcome ───────────────────────────────────────────
                pick("en_ft_welcome", listOf(
                    "Hello $name, welcome! I'm Butler, your grocery assistant. Really glad to have you here.",
                    "Hi $name, welcome! I'm Butler. I'm here to help with all your grocery needs.",
                    "Namaste $name! Welcome. I'm Butler, your personal kirana assistant."
                )),
                // ── English teaser ────────────────────────────────────────────
                pick("en_ft_teaser", listOf(
                    "We carry everything you need. Rice, dal, oil, flour, spices, biscuits, milk and a whole lot more. What would you like to order today $name?",
                    "From rice, dal and oil to spices, biscuits and milk, we have it all. What can I get for you today $name?",
                    "Rice, dal, oil, flour, spices, milk, biscuits and much more, all available. What would you like $name?"
                ))
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SHOW MORE PRODUCTS
    //
    // Called when user asks to see more options in a general way:
    //   "aur kya hai?", "what else do you have?", "show me more",
    //   "kya kya milta hai?", "tell me what you sell"
    //
    // This is NOT the same as the first-time category teaser.
    // Here the user already knows Butler — they want to explore.
    // Response = conversational category overview, grouped naturally,
    // ending with an open question to keep the flow going.
    //
    // HOW TO DETECT in Butler.kt:
    //   AIParser returns intent = "show_catalog" or "what_available"
    //   or you can check for keywords: "aur kya", "kya milta", "what else",
    //   "show more", "sab kya hai", "kya kya hai"
    //
    // HOW TO CALL:
    //   val text = ButlerPersonalityEngine.showMoreProducts(name, lang, mood)
    //   ttsManager.speak(text, lang, toneForShowMore(mood)) { startListening() }
    // ══════════════════════════════════════════════════════════════════════

    fun showMoreProducts(name: String, lang: String, mood: UserMood = UserMood.CALM): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.RUSHED -> pick("hi_more_cat_rush", listOf(
                    // Short, efficient — no fluff for rushed users
                    "Chawal, daal, tel, atta, masale, biscuit, doodh, sabzi, ghee, chai. Kya chahiye $name?",
                    "Grocery sab hai. Kya laana hai $name?"
                ))
                else -> pick("hi_more_cat", listOf(
                    // Group 1: staples. Group 2: snacks+beverages. Group 3: open question.
                    // Grouping makes a long list sound like a conversation, not a robot reading.
                    "Hamare paas staples mein chawal, daal, tel, aur atta hai. Snacks mein biscuit, namkeen, aur chips. Dairy mein doodh, dahi, aur makhan. Aur bhi bahut kuch hai $name. Kya laana hai?",
                    "Chawal, daal, tel, atta, ghee. Aur chai, biscuit, namkeen, doodh, dahi bhi milta hai. Iske alawa masale, sabzi, cleaning ka saman bhi hai. Kya chahiye $name?",
                    "Hamare paas kirana ka pura saman hai. Anaj mein chawal, daal, atta. Tel mein sarson, sunflower, coconut. Dairy mein doodh, dahi, paneer. Snacks bhi bahut hain. Kya laana hai $name?"
                ))
            }
            lang.startsWith("te") -> pick("te_more_cat", listOf(
                "Meeru kooda biyyam, pappu, nune, atta, masaalu, biscuit, paalu, perugu, ghee, kooralu anni unnayi. Emi kavali $name garu?",
                "Biyyam, pappu, nune nunchi biscuit, paalu, perugu varaku anni milatayi. Emi teestara $name?"
            ))
            lang.startsWith("ta") -> pick("ta_more_cat", listOf(
                "Engalukkidam arisi, paruppu, ennai, maavu, masala, biscuit, paal, thayir, ghee ellaam irukku. Enna vendum $name?",
                "Arisi, paruppu, ennai, maavu mudhal biscuit, paal, thayir varai ellaam kidaikum. Enna vendum $name?"
            ))
            lang.startsWith("kn") -> pick("kn_more_cat", listOf(
                "Naavu akki, bele, enne, atta, masala, biscuit, haalu, mosaru, ghee ellavu idabekaagide. Enu beku $name?",
                "Akki, bele, enne ninda biscuit, haalu, mosaru varegu sab ide. Enu beku $name?"
            ))
            lang.startsWith("ml") -> pick("ml_more_cat", listOf(
                "Njangalude pakkal ari, parippu, enna, atta, masala, biscuit, paal, thayir, ghee ellam und. Enthu veno $name?",
                "Ari, parippu, enna muthal biscuit, paal, thayir vare ellam und. Enthu venam $name?"
            ))
            lang.startsWith("pa") -> pick("pa_more_cat", listOf(
                "Saade paas chawal, daal, tel, atta, masale, biscuit, doodh, dahi, ghee sab hai. Ki chahida $name ji?",
                "Chawal, daal, tel, atta to biscuit, doodh, dahi tak sab milega. Ki mangwaoge $name?"
            ))
            lang.startsWith("gu") -> pick("gu_more_cat", listOf(
                "Amari paase chawal, dal, tel, atta, masala, biscuit, doodh, dahi, ghee badhu che. Shu joiye $name?",
                "Chawal, dal, tel, atta thi biscuit, doodh, dahi sudhi badhu maḷashe. Shu mangaavun $name?"
            ))
            else -> when (mood) {
                UserMood.RUSHED -> pick("en_more_cat_rush", listOf(
                    "We carry rice, dal, oil, flour, spices, biscuits, milk, dairy, snacks and more. What do you need $name?",
                    "Full grocery range available $name. What would you like?"
                ))
                else -> pick("en_more_cat", listOf(
                    // Grouped naturally — staples, snacks, dairy, open question
                    "We carry staples like rice, dal, oil and flour. Snacks like biscuits, namkeen and chips. Dairy items like milk, curd and butter. And a lot more besides $name. What would you like?",
                    "We have grains and pulses, cooking oils, flour, spices, biscuits, dairy products, and cleaning supplies too. What can I get for you $name?",
                    "From rice, dal and oil to biscuits, milk, curd, spices and snacks, we have it all $name. What are you looking for?"
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GREETING
    // ══════════════════════════════════════════════════════════════════════

    fun greeting(name: String, lang: String, lastProduct: String?, mood: UserMood): String {
        val time = timeSlot()
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED -> pick("hi_greet_frus", listOf(
                    "Haan $name ji... batao, kya chahiye?",
                    "Haan $name... kya la doon aapke liye?"
                ))
                UserMood.RUSHED -> pick("hi_greet_rush", listOf(
                    "Haan $name, batao.",
                    "Haan $name... kya chahiye?"
                ))
                else -> when {
                    lastProduct != null -> pick("hi_greet_ret", listOf(
                        "Hello $name... btaiye, kya lena hai aaj?",
                        "Hello $name ji... kya chahiye?",
                        "Haan $name... kya la doon?"
                    ))
                    time == "morning" -> pick("hi_greet_morn", listOf(
                        "Good morning $name ji... aaj kya laana hai?",
                        "Namaste $name... subah ka kya chahiye?",
                        "Haan $name ji... aaj kya chahiye?"
                    ))
                    time == "evening" -> pick("hi_greet_eve", listOf(
                        "Haan $name... shaam ko kya la doon?",
                        "Haan $name ji... shaam mein kya chahiye?",
                        "Namaste $name... shaam ka kya laana hai?"
                    ))
                    else -> pick("hi_greet_new", listOf(
                        "Hello $name... btaiye, kya lena hai?"
                    ))
                }
            }
            lang.startsWith("te") -> when {
                lastProduct != null -> pick("te_greet_ret", listOf(
                    "Cheppandi $name garu... emi kavali inniki?",
                    "Haa $name... emi teestara?"
                ))
                else -> pick("te_greet_base", listOf(
                    "Namaskaram $name garu... inniki emi kavali?",
                    "Cheppandi $name garu... emi teestara?",
                    "Haa $name garu... emi kavali?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_greet", listOf(
                "Vanakkam $name... indru enna vendum?",
                "Solunga $name... enna tharen?"
            ))
            lang.startsWith("kn") -> pick("kn_greet", listOf(
                "Namaskara $name... enu beku indhu?",
                "Heli $name... enu tegolabeeku?"
            ))
            lang.startsWith("ml") -> pick("ml_greet", listOf(
                "Namaskaram $name... innu enthu veno?",
                "Parayo $name... enthu venam?"
            ))
            lang.startsWith("pa") -> pick("pa_greet", listOf(
                "Sat sri akal $name ji... aaj ki chahida?",
                "Haan $name ji... ki mangwaiye?"
            ))
            lang.startsWith("gu") -> pick("gu_greet", listOf(
                "Jai shri krishna $name... aaj shu joiye?",
                "Kaho $name... shu mangaavun?"
            ))
            lang.startsWith("mr") -> pick("mr_greet", listOf(
                "Namaskar $name... aaj kaay havay?",
                "Sanga $name... kaay aanu?"
            ))
            else -> when (mood) {
                UserMood.RUSHED -> pick("en_greet_rush", listOf(
                    "Yes $name, go ahead.",
                    "Hi $name, what do you need?"
                ))
                else -> when {
                    lastProduct != null -> pick("en_greet_ret", listOf(
                        "Yes $name... what do you need today?",
                        "Hi $name... what can I get you?"
                    ))
                    time == "morning" -> pick("en_greet_morn", listOf(
                        "Good morning $name... what do you need today?",
                        "Morning $name... what can I get you?"
                    ))
                    else -> pick("en_greet_new", listOf(
                        "Yes $name... what do you need today?",
                        "Hi $name... what can I get you?",
                        "Hello $name... go ahead, I'm listening."
                    ))
                }
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
                    "Kaunsa rice chahiye $name. Basmati, brown ya regular?",
                    "$name, rice mein kya chahiye. Basmati, sona masoori ya normal?",
                    "Rice kaunsa doon $name. Basmati, brown ya regular?"
                ))
                cat.contains("dal") || cat.contains("daal") -> pick("hi_type_dal", listOf(
                    "Kaunsi daal chahiye $name. Arhar, moong ya masoor?",
                    "$name, daal kaunsi doon. Toor, moong, masoor ya urad?",
                    "Daal mein kya chahiye $name. Arhar, moong ya koi aur?"
                ))
                cat.contains("oil") || cat.contains("tel") -> pick("hi_type_oil", listOf(
                    "Kaunsa tel chahiye $name. Sarson, sunflower ya coconut?",
                    "$name, oil kaunsa doon. Mustard, sunflower ya coconut?"
                ))
                cat.contains("atta") || cat.contains("flour") -> pick("hi_type_atta", listOf(
                    "Kaunsa atta chahiye $name. Wheat, multigrain ya maida?",
                    "Atta kaunsa doon $name. Wheat, multigrain ya maida?"
                ))
                cat.contains("milk") || cat.contains("doodh") -> pick("hi_type_milk", listOf(
                    "Doodh kaunsa chahiye $name. Full cream, toned ya skimmed?",
                    "$name, doodh mein full fat chahiye ya toned?"
                ))
                cat.contains("tea") || cat.contains("chai") -> pick("hi_type_tea", listOf(
                    "Kaunsi chai chahiye $name. Loose, tea bags ya kadak masala?",
                    "Chai mein kya chahiye $name. Tata, Red Label ya koi aur?"
                ))
                else -> pick("hi_type_generic", listOf(
                    "Koi specific brand chahiye $name?",
                    "$name, kaunsa $category chahiye aapko?"
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
    // ══════════════════════════════════════════════════════════════════════

    fun confirmAddProduct(name: String, productName: String, price: Int, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_confirm_add", listOf(
                "$short Rs $price ka hai. Add krna hai $name?",
                "$short Rs $price. Add kar loon $name?",
                "$short Rs $price ka hai. Cart mein add kroon $name?"
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
                "$short is Rs $price $name. Shall I add it?",
                "$short, Rs $price. Want this one $name?",
                "$short costs Rs $price. Should I add it $name?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ITEM ADDED
    // ══════════════════════════════════════════════════════════════════════

    fun itemAdded(name: String, productName: String, lang: String, mood: UserMood, cartSize: Int): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        val full = productName.split(" ").take(3)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED -> pick("hi_added_frus", listOf(
                    "Ho gaya $name. Aur kuch chahiye aapko?",
                    "$short le liya. Kuch aur bata dijiye $name."
                ))
                UserMood.RUSHED -> pick("hi_added_rush", listOf(
                    "$short le liya. Aur?",
                    "Ho gaya. Aur kuch $name?",
                    "$short add ho gaya. Kya aur chahiye?"
                ))
                else -> when (cartSize) {
                    1 -> pick("hi_added_1", listOf(
                        "$full add ho gaya. Kuch aur lena hai?",
                        "$full cart mein aa gaya. Aur kuch chahiye?"
                    ))
                    2 -> pick("hi_added_2", listOf(
                        "$full bhi add ho gaya. Kuch aur lena hai?",
                        "$full bhi cart mein aa gaya. Aur kuch?"
                    ))
                    else -> pick("hi_added_n", listOf(
                        "$short bhi add ho gaya. Kuch aur chahiye?",
                        "Ho gaya. Aur kuch lena hai?"
                    ))
                }
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("te_added_r", listOf(
                    "$short ayindi $name. Inkaa emi?",
                    "Sare $name. Inkaa?"
                ))
                else -> pick("te_added", listOf(
                    "Ayindi $name, $full cart lo pettaanu. Inkaa emi kavali?",
                    "Sare $name, $full add chesaanu. Marokkati?"
                ))
            }
            lang.startsWith("ta") -> pick("ta_added", listOf(
                "Sari $name, $full vaanginen. Vera enna vendum?",
                "$full vaanginen $name. Vera enna?"
            ))
            lang.startsWith("kn") -> pick("kn_added", listOf(
                "Aayitu $name, $full tagondi. Bere enu beku?",
                "$full tagondi $name. Bere enu?"
            ))
            lang.startsWith("ml") -> pick("ml_added", listOf(
                "Ayi $name, $full edutthu. Vere enthu veno?",
                "$full edutthu $name. Vere enthu?"
            ))
            lang.startsWith("pa") -> pick("pa_added", listOf(
                "Theek hai $name, $full le litta. Hor ki chahida?",
                "$full le litta $name. Hor ki?"
            ))
            lang.startsWith("gu") -> pick("gu_added", listOf(
                "Saru $name, $full lai lidhun. Biju shu joiye?",
                "$full lai lidhun $name. Biju shu?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED -> pick("en_added_frus", listOf(
                    "Done $name. What else do you need?",
                    "$short added. Anything else $name?"
                ))
                UserMood.RUSHED -> pick("en_added_rush", listOf(
                    "$short added. More?",
                    "Done. Anything else $name?"
                ))
                else -> when (cartSize) {
                    1 -> pick("en_added_1", listOf(
                        "Got it $name, $full is in your cart. Anything else?",
                        "$full added $name. What else do you need?",
                        "Done $name, $full added. Anything else?"
                    ))
                    2 -> pick("en_added_2", listOf(
                        "$full added too $name. Anything else?",
                        "Done $name, $full is in as well. More?"
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
    // ══════════════════════════════════════════════════════════════════════

    fun confirmAddNext(name: String, productName: String, price: Int, lang: String): String {
        val short = productName.split(" ").take(2)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return when {
            lang.startsWith("hi") -> pick("hi_confirm_next", listOf(
                "$short Rs $price. Yeh bhi add krna hai $name?",
                "$short Rs $price ka hai. Ise bhi add kroon $name?",
                "$short Rs $price. Yeh bhi lena hai $name?"
            ))
            lang.startsWith("te") -> "$short Rs $price untundi $name. Idi kooda cart lo pettanaa?"
            lang.startsWith("ta") -> "$short Rs $price irukku $name. Ithayum vangattuma?"
            lang.startsWith("kn") -> "$short Rs $price ide $name. Ithanu kooda cart ge haakona?"
            lang.startsWith("ml") -> "$short Rs $price anu $name. Ithu koodi cart-il idattea?"
            lang.startsWith("pa") -> "$short Rs $price da hai $name. Eda vi la laiye?"
            lang.startsWith("gu") -> "$short Rs $price chhe $name. Aane pan nakhu?"
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
                "$items, total $total. Order krna hai?",
                "$items, total $total. Confirm krna hai?",
                "$items, total $total. Order krna hai?"
            ))
            lang.startsWith("te") -> pick("te_confirm_order", listOf(
                "Sare $name, $items. Total $total. Order pettanaa?",
                "$name, $items, $total. Order ivvana?"
            ))
            lang.startsWith("ta") -> "Sari $name, $items. Mottam $total. Order podanuma?"
            lang.startsWith("kn") -> "Saaku $name, $items. Otha $total. Order madona?"
            lang.startsWith("ml") -> "Mathi $name, $items. Aakoode $total. Order cheyattea?"
            lang.startsWith("pa") -> "Theek hai $name, $items. Kull $total. Order karan?"
            lang.startsWith("gu") -> "Saru $name, $items. Kul $total. Order karun?"
            else -> pick("en_confirm_order", listOf(
                "That's $items, total $total $name. Shall I place the order?",
                "$items, $total total. Place the order $name?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAYMENT ASK
    // ══════════════════════════════════════════════════════════════════════

    fun askPaymentMode(name: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_ask_payment", listOf(
                "Payment kaise karenge $name. यू पी आई, card ya cash?",
                "$name, यू पी आई karenge ya card?",
                "Payment ka tarika batao $name. यू पी आई, card ya cash?"
            ))
            lang.startsWith("te") -> "$name, ela pay chestaru. UPI, card leda cash?"
            lang.startsWith("ta") -> "$name, epdi pay pannuvenga. UPI, card la cash?"
            lang.startsWith("kn") -> "$name, hege pay madtira. UPI, card leda cash?"
            lang.startsWith("ml") -> "$name, engane pay cheyyum. UPI, card allengil cash?"
            lang.startsWith("pa") -> "$name, kiven paisa dena. UPI, card ya cash?"
            lang.startsWith("gu") -> "$name, kem pay karisho. UPI, card ke cash?"
            else -> pick("en_ask_payment", listOf(
                "How would you like to pay $name. UPI, card, or cash?",
                "Payment mode $name. UPI, card, or cash?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UPI INSTRUCTION
    // ══════════════════════════════════════════════════════════════════════

    fun upiInstruction(amount: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_upi", listOf(
                "यू पी आई payment kar dijiye.. done hone par bata dijiyega.",
                "$amount यू पी आई mein bhej dijiye.. ho jaaye toh bata dijiyega.",
                "यू पी आई payment karo $amount ka.. bata dijiyega jab ho jaaye."
            ))
            lang.startsWith("te") -> "Sare, UPI lo $amount pampu. Ayinaaka cheppandi."
            lang.startsWith("ta") -> "Sari, UPI-la $amount anuppu. Aanadhum sollunga."
            lang.startsWith("kn") -> "Sari, UPI nalli $amount kali. Madidamele heli."
            lang.startsWith("ml") -> "Sheri, UPI-l $amount aykku. Cheshal parayo."
            lang.startsWith("pa") -> "UPI ton $amount bhejo. Ho jaaye te dasao."
            lang.startsWith("gu") -> "UPI thi $amount moklo. Thai jaay tyare kaho."
            else -> pick("en_upi", listOf(
                "Please complete payment of $amount via UPI. Let me know once it's done.",
                "Send $amount via UPI and let me know when done."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAYMENT DONE
    // ══════════════════════════════════════════════════════════════════════

    fun paymentDone(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_paydone", listOf(
                "Shukriya. Payment ho gayi.",
                "Achha. Payment confirm ho gayi.",
                "Payment ho gayi."
            ))
            lang.startsWith("te") -> pick("te_paydone", listOf(
                "Sare, payment ayindi.",
                "Chala baagundi. Payment confirm."
            ))
            lang.startsWith("ta") -> "Nandri. Payment aachi."
            lang.startsWith("kn") -> "Channagi. Payment aayitu."
            lang.startsWith("ml") -> "Nandri. Payment ayi."
            lang.startsWith("pa") -> "Shukriya. Payment ho gayi."
            lang.startsWith("gu") -> "Shukriya. Payment thai gayu."
            else -> pick("en_paydone", listOf(
                "Payment received. Thank you.",
                "Got it. Payment confirmed."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ASK IF PAID
    // ══════════════════════════════════════════════════════════════════════

    fun askIfPaid(lang: String, mode: String, amount: String, name: String = ""): String {
        val n = if (name.isNotBlank()) " $name" else ""
        return when {
            lang.startsWith("hi") -> "Kya payment ho gayi$n?"
            lang.startsWith("te") -> "Payment ayindaa$n?"
            lang.startsWith("ta") -> "Payment aachaa$n?"
            lang.startsWith("kn") -> "Payment aytaa$n?"
            lang.startsWith("ml") -> "Payment aayoo$n?"
            lang.startsWith("pa") -> "Payment ho gayi$n?"
            lang.startsWith("gu") -> "Payment thai$n?"
            else -> "Payment done$n?"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ORDER PLACED
    // ══════════════════════════════════════════════════════════════════════

    fun orderPlaced(name: String, orderId: String, amount: String, etaMins: Int, lang: String): String {
        val eta = if (etaMins > 0) etaMins else 30
        return when {
            lang.startsWith("hi") -> pick("hi_order_placed", listOf(
                "Order Place ho gaya $name. $eta minute mein order aap tak pahoch jaayega.",
                "Order Place ho gaya $name. $eta minute mein aap tak pahoch jaayega.",
                "Order Place ho gaya $name. $eta minute mein aapka samaan pahoch jaayega."
            ))
            lang.startsWith("te") -> pick("te_order_placed", listOf(
                "Order confirm ayindi $name garu. $eta nimishaallo vastundi. Dhanyavaadaalu.",
                "$name garu, order confirm. $eta minutes lo deliveri vastundi."
            ))
            lang.startsWith("ta") -> "Order confirm $name. $eta nimidathil varum. Nandri."
            lang.startsWith("kn") -> "Order confirm aayitu $name. $eta nimisha hage baruttade. Dhanyavada."
            lang.startsWith("ml") -> "Order confirmed $name. $eta minuteinu orathe ettum. Nandri."
            lang.startsWith("pa") -> "Order ho gaya $name. $eta minute vich aa jauga. Shukriya."
            lang.startsWith("gu") -> "Order thai gayu $name. $eta minute ma aavse. Shukriya."
            else -> pick("en_order_placed", listOf(
                "Order confirmed $name. Delivery in about $eta minutes. Thank you.",
                "Done $name. Your order is placed. $eta minutes to delivery.",
                "All set $name. Order confirmed, arriving in $eta minutes."
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
                UserMood.FRUSTRATED -> pick("hi_more_frus", listOf(
                    "Aur kuch chahiye $name?",
                    "Kuch aur bata dijiye $name."
                ))
                UserMood.RUSHED -> pick("hi_more_rush", listOf(
                    "Aur kuch $name?", "Kuch aur?", "Bas?"
                ))
                else -> if (suggestion != null && cartSize == 1) {
                    pick("hi_more_sug", listOf(
                        "$suggestion bhi chahiye $name?",
                        "$suggestion bhi saath le loon?",
                        "Kya $suggestion bhi le loon $name?"
                    ))
                } else {
                    pick("hi_more", listOf(
                        "Kuch aur lena hai $name?",
                        "Aur kuch chahiye $name?",
                        "$name, kuch aur bhi ho toh bataiye."
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
                "Hor ki chahida $name?", "Kuch hor?", "Bass $name?"
            ))
            lang.startsWith("gu") -> pick("gu_more", listOf(
                "Biju shu joiye $name?", "Kahi biju?", "Bas $name?"
            ))
            else -> when (mood) {
                UserMood.FRUSTRATED -> pick("en_more_frus", listOf(
                    "Anything else $name?", "What else do you need?"
                ))
                UserMood.RUSHED -> pick("en_more_rush", listOf(
                    "More $name?", "Anything else?", "That it?"
                ))
                else -> pick("en_more", listOf(
                    "Anything else $name?",
                    "What else do you need $name?",
                    "Shall I add anything else $name?"
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SUBSTITUTE / MISMATCH
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
                "$reqShort nahi mila$n. $subShort Rs $price ka hai. Le loon?",
                "$reqShort stock mein nahi hai$n. $subShort Rs $price mein milega. Theek rahega?"
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
                "$subShort Rs $price ka hai $name. Ise le loon?",
                "$requestedItem nahi mila. $subShort Rs $price ka hai. Le loon $name?",
                "$subShort Rs $price. Chalega $name?"
            ))
            lang.startsWith("te") -> "$subShort Rs $price undi $name. Idi teesukuntaaraa?"
            lang.startsWith("ta") -> "$subShort Rs $price irukku $name. Itha vangattuma?"
            lang.startsWith("kn") -> "$subShort Rs $price ide $name. Ithanu tegolabeekaa?"
            lang.startsWith("ml") -> "$subShort Rs $price anu $name. Ithu venam?"
            lang.startsWith("pa") -> "$subShort Rs $price da hai $name. La laiye?"
            lang.startsWith("gu") -> "$subShort Rs $price chhe $name. Levun?"
            else -> pick("en_sub", listOf(
                "$subShort is Rs $price $name. Want this one?",
                "No $requestedItem in stock. Got $subShort for Rs $price $name. Okay?",
                "$subShort, Rs $price. Add it $name?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRODUCT NOT FOUND
    // ══════════════════════════════════════════════════════════════════════

    fun productNotFound(itemName: String, lang: String): String {
        val short = itemName.take(20)
        return when {
            lang.startsWith("hi") -> pick("hi_notfound", listOf(
                "Woh abhi available nahi. Kuch aur chahiye?",
                "$short nahi mila. Koi aur brand dekhoon?",
                "$short stock mein nahi hai abhi. Kuch aur chahiye?"
            ))
            lang.startsWith("te") -> "Adi ippudu ledu. Inkaa emi kavali?"
            lang.startsWith("ta") -> "Adu illai ippo. Vera enna vendum?"
            lang.startsWith("kn") -> "Adu illa ippaga. Bere enu beku?"
            lang.startsWith("ml") -> "Athu ippol illa. Vere enthu veno?"
            lang.startsWith("pa") -> "$short nahi mila. Kuch hor chahida?"
            lang.startsWith("gu") -> "$short na maddyo. Kahi biju joiye?"
            else -> pick("en_notfound", listOf(
                "That's not available right now. Anything else?",
                "$short not in stock. Want something similar?"
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
                    "Kuch sunai nahi diya. Thoda aur oonchi awaaz mein boliye.",
                    "Samajh nahi aaya. Phir se ek baar boliye.",
                    "Awaaz thodi dhimi hai. Paas aakar boliye."
                ))
                retryCount >= 4 -> pick("hi_retry_many", listOf(
                    "Mic ke bilkul paas aakar boliye.",
                    "Thoda jor se boliye please."
                ))
                retryCount >= 2 -> pick("hi_retry_2", listOf(
                    "Samajh nahi aaya. Ek baar aur boliye.",
                    "Sunai nahi diya. Thoda aur boliye."
                ))
                else -> pick("hi_retry", listOf(
                    "Haan?", "Kya kaha?", "Phir se boliye.", "Suna nahi, ek baar aur?"
                ))
            }
            lang.startsWith("te") -> when (mood) {
                UserMood.FRUSTRATED -> "Vinaledu. Clearly cheppandi please."
                else -> pick("te_retry", listOf("Haa?", "Emi antunnaru?", "Malli cheppandi."))
            }
            lang.startsWith("ta") -> pick("ta_retry", listOf("Aa?", "Enna solreengal?", "Oru thadava sollunga."))
            lang.startsWith("kn") -> pick("kn_retry", listOf("Ha?", "Enu heldiri?", "Ond sari heli."))
            lang.startsWith("ml") -> pick("ml_retry", listOf("Ha?", "Enthu paranjhu?", "Oru thavathu koodi parayo."))
            lang.startsWith("pa") -> pick("pa_retry", listOf("Ha?", "Ki kiya?", "Ik baar fir dasao."))
            lang.startsWith("gu") -> pick("gu_retry", listOf("Ha?", "Shu kahyu?", "Ek vaar kaho."))
            else -> when (mood) {
                UserMood.FRUSTRATED -> "Didn't catch that. Could you speak a little louder?"
                else -> pick("en_retry", listOf(
                    "Sorry, didn't catch that.", "Could you say that again?",
                    "Pardon?", "Come again please."
                ))
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
                    "Koi baat nahi. Jab bhi zaroorat ho, hey Butler bolein.",
                    "Chaliye, baad mein baat karte hain. Hey Butler bolein jab ready hon."
                ))
                else -> pick("hi_giveup", listOf(
                    "Koi baat nahi. Jab bhi ready hon, hey Butler bolein.",
                    "Theek hai. Baad mein baat karte hain."
                ))
            }
            lang.startsWith("te") -> "Parvaaledu. Taraavaata hey Butler cheppandi."
            lang.startsWith("ta") -> "Paravailla. Pinna hey Butler sollungal."
            lang.startsWith("kn") -> "Parvaagilla. Naantara hey Butler heli."
            lang.startsWith("ml") -> "Kaaryanilla. Pinne hey Butler parayo."
            lang.startsWith("pa") -> "Koi gal nahi. Baad vich hey Butler kaho."
            lang.startsWith("gu") -> "Kaem nahi. Pachhi hey Butler kaho."
            else -> "No worries. Say hey Butler whenever you're ready."
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SELECTION PROMPT
    // ══════════════════════════════════════════════════════════════════════

    fun askSelection(lang: String, mood: UserMood): String {
        return when {
            lang.startsWith("hi") -> when (mood) {
                UserMood.FRUSTRATED, UserMood.RUSHED -> pick("hi_sel_r", listOf(
                    "Kaun sa chahiye? Naam bataiye.", "Brand ka naam bataiye.", "Kaunsa doon?"
                ))
                else -> pick("hi_sel", listOf(
                    "Kaunsa chahiye. Brand ka naam bataiye.",
                    "Brand ka naam bata dijiye please.",
                    "Kaunsa doon. Naam bataiye.",
                    "Bataiye, kaunsa chahiye aapko."
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
                    "Which brand would you like?",
                    "Say the name and I'll add it."
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
                "Abhi cart khaali hai. Kya laana hai?",
                "Kuch nahi hai abhi. Kya chahiye?"
            ))
            lang.startsWith("te") -> "Ippudu emi ledu. Emi kavali?"
            lang.startsWith("ta") -> "Ippo onnum illa. Enna vendum?"
            lang.startsWith("kn") -> "Enu illa. Enu beku?"
            lang.startsWith("ml") -> "Ippol ontumilla. Enthu veno?"
            lang.startsWith("pa") -> "Cart khaali hai. Ki chahida?"
            lang.startsWith("gu") -> "Cart khaali chhe. Shu joiye?"
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
                "$short hata diya.", "Ho gaya, $short hata diya.", "$short remove kar diya."
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
                "Network thodi slow hai abhi. Ek baar aur try karein?",
                "Ek second please. Phir se koshish karte hain."
            ))
            lang.startsWith("te") -> "Network problem undi. Malli try cheyyana?"
            else -> pick("en_err", listOf(
                "Ran into a small issue. Shall we try again?",
                "Network hiccup. Want to retry?"
            ))
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
                "Haan $name, $items chahiye ya kuch naya?",
                "Pichli baar $items liye the $name. Wahi doon?",
                "Haan $name. $items order karoon phir se?"
            ))
            lang.startsWith("te") -> "$name garu, $items kavala?"
            lang.startsWith("ta") -> "$name, $items venuma?"
            lang.startsWith("kn") -> "$name, $items beku?"
            lang.startsWith("ml") -> "$name, $items venum?"
            lang.startsWith("pa") -> "$name ji, $items chahida?"
            lang.startsWith("gu") -> "$name, $items joiye?"
            else -> pick("en_reorder", listOf(
                "Same as last time $name. $items?",
                "Hey $name. $items again?",
                "Shall I get the usual $name. $items?"
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
                "$short kitna chahiye. Ek kilo ya do kilo?",
                "Kitna laoon $short. Ek packet ya zyada?",
                "$short. Ek kilo, do kilo, ya aur?"
            ))
            lang.startsWith("te") -> "$short entha kavali? Oka kilo, rendo kilo?"
            lang.startsWith("ta") -> "$short evvalavu vendum?"
            lang.startsWith("kn") -> "$short eshtu beku?"
            lang.startsWith("ml") -> "$short ethra veno?"
            lang.startsWith("pa") -> "$short kitna chahida?"
            lang.startsWith("gu") -> "$short ketlun joiye?"
            else -> pick("en_qty", listOf(
                "How much $short. One kilo or two?",
                "$short. One pack or more?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RELATED SUGGESTION (private helper)
    // ══════════════════════════════════════════════════════════════════════

    private fun getRelatedSuggestion(productName: String?, lang: String): String? {
        if (productName == null) return null
        val p = productName.lowercase()
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