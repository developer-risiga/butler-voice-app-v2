package com.demo.butler_voice_app.services

/**
 * ServiceVoiceEngine — human-sounding Butler voice for the services flow.
 *
 * ADD this file alongside ServiceVoiceHandler.kt (do NOT replace it).
 * ServiceVoiceHandler handles LOGIC (matching, sub-types, intent detection).
 * ServiceVoiceEngine handles VOICE OUTPUT (what Butler actually speaks).
 *
 * Sector names match the actual ServiceSector enum:
 *   PLUMBER, ELECTRICIAN, CARPENTER, PAINTER, CLEANING, AC_REPAIR,
 *   DOCTOR, MEDICINE, AMBULANCE, FOOD, TAXI, SALON, CAR_SERVICE,
 *   TUTOR, SECURITY, LAUNDRY, PEST_CONTROL
 */
object ServiceVoiceEngine {

    // ── Session de-duplication (never same phrase twice in a row) ─────────
    private val lastUsed = mutableMapOf<String, Int>()

    private fun pick(key: String, variants: List<String>): String {
        val last      = lastUsed[key] ?: -1
        val available = variants.indices.filter { it != last }
        val idx       = if (available.isEmpty()) 0 else available.random()
        lastUsed[key] = idx
        return variants[idx]
    }

    fun reset() { lastUsed.clear() }

    // ── Human sector display name ─────────────────────────────────────────
    private fun sectorLabel(sectorName: String, lang: String): String {
        val base = lang.substringBefore("-").take(2)
        return when (sectorName) {
            "ELECTRICIAN"  -> if (base == "hi") "electrician" else if (base == "te") "electrician"      else "electrician"
            "PLUMBER"      -> if (base == "hi") "plumber"     else if (base == "te") "plumber"          else "plumber"
            "DOCTOR"       -> if (base == "hi") "doctor"      else if (base == "te") "doctor"           else "doctor"
            "AMBULANCE"    -> if (base == "hi") "ambulance"   else if (base == "te") "ambulance"        else "ambulance"
            "MEDICINE"     -> if (base == "hi") "pharmacy"    else if (base == "te") "pharmacy"         else "pharmacy"
            "CARPENTER"    -> if (base == "hi") "carpenter"   else if (base == "te") "carpenter"        else "carpenter"
            "PAINTER"      -> if (base == "hi") "painter"     else if (base == "te") "painter"          else "painter"
            "CLEANING"     -> if (base == "hi") "cleaning service" else if (base == "te") "cleaning"    else "cleaning service"
            "AC_REPAIR"    -> if (base == "hi") "AC mechanic" else if (base == "te") "AC mechanic"      else "AC technician"
            "FOOD"         -> if (base == "hi") "restaurant"  else if (base == "te") "restaurant"       else "restaurant"
            "TAXI"         -> if (base == "hi") "taxi"        else if (base == "te") "taxi"             else "taxi"
            "SALON"        -> if (base == "hi") "salon"       else if (base == "te") "salon"            else "salon"
            "CAR_SERVICE"  -> if (base == "hi") "mechanic"    else if (base == "te") "mechanic"         else "mechanic"
            "TUTOR"        -> if (base == "hi") "tutor"       else if (base == "te") "tutor"            else "tutor"
            "SECURITY"     -> if (base == "hi") "security guard" else                "security guard"
            "LAUNDRY"      -> if (base == "hi") "laundry"     else                  "laundry"
            "PEST_CONTROL" -> if (base == "hi") "pest control" else                 "pest control"
            else           -> sectorName.lowercase().replace("_", " ")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. CATEGORY PROMPT — "what service do you need?"
    // ══════════════════════════════════════════════════════════════════════

    fun categoryPrompt(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_cat", listOf(
                "Batao, kya chahiye? Electrician, plumber, doctor, taxi, ya kuch aur?",
                "Kaun si service chahiye? Bijli, paani, doctor, ya kuch aur?",
                "Kya kaam hai? Electrician, plumber, doctor, taxi, cleaning, ya koi aur service?",
                "Kya chahiye — electrician, plumber, doctor, taxi, salon, pest control?"
            ))
            lang.startsWith("te") -> pick("te_cat", listOf(
                "Cheppandi, em kavali? Electrician, plumber, doctor, taxi, inkaa emi?",
                "Em service kavali? Electrician, plumber, doctor, leda taxi?",
                "Meeru em antunnaru? Em cheyali?"
            ))
            lang.startsWith("ta") -> pick("ta_cat", listOf(
                "Enna vendum? Electrician, plumber, doctor, taxi?",
                "Etha service vendum sollunga."
            ))
            lang.startsWith("kn") -> pick("kn_cat", listOf(
                "Enu beku? Electrician, plumber, doctor, taxi?",
                "Yavudu service beku?"
            ))
            lang.startsWith("ml") -> pick("ml_cat", listOf(
                "Enthu veno? Electrician, plumber, doctor, taxi?",
                "Etha service veno?"
            ))
            else -> pick("en_cat", listOf(
                "What do you need? Electrician, plumber, doctor, taxi, or something else?",
                "Which service? Electrician, plumber, doctor, taxi, cleaning, AC repair?",
                "What can I get for you? Say electrician, plumber, doctor, or any service."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. SECTOR DETECTED — "finding X near you..." (spoken while loading)
    // ══════════════════════════════════════════════════════════════════════

    fun sectorDetected(sector: ServiceSector, lang: String): String {
        val sName = sectorLabel(sector.name, lang)
        return when {
            lang.startsWith("hi") -> when (sector.name) {
                "ELECTRICIAN"  -> pick("hi_det_elec", listOf(
                    "Theek hai, electrician dhundh raha hoon aapke paas.",
                    "Electrician — ek second, paas waale dhundh raha hoon.",
                    "Abhi electrician khojta hoon aapke area mein."
                ))
                "PLUMBER"      -> pick("hi_det_plum", listOf(
                    "Theek hai, plumber dhundh raha hoon aapke paas.",
                    "Plumber — ek second, paas waale dhundh raha hoon.",
                    "Abhi plumber khojta hoon."
                ))
                "DOCTOR"       -> pick("hi_det_doc", listOf(
                    "Doctor dhundh raha hoon aapke paas.",
                    "Theek hai, paas ke doctor dhundh raha hoon.",
                    "Abhi doctor khojta hoon."
                ))
                "AMBULANCE"    -> pick("hi_det_amb", listOf(
                    "Ambulance bhej raha hoon — abhi!",
                    "Theek hai, ambulance bula raha hoon.",
                    "Ambulance ke liye dhundh raha hoon."
                ))
                "CLEANING"     -> pick("hi_det_clean", listOf(
                    "Cleaning service dhundh raha hoon aapke paas.",
                    "Theek hai, paas ki cleaning team dhundh raha hoon."
                ))
                "AC_REPAIR"    -> pick("hi_det_ac", listOf(
                    "AC technician dhundh raha hoon aapke paas.",
                    "Theek hai, AC mechanic khojta hoon."
                ))
                "TAXI"         -> pick("hi_det_taxi", listOf(
                    "Taxi dhundh raha hoon aapke liye.",
                    "Cab — ek second, dhundh raha hoon.",
                    "Abhi nearby taxi khojta hoon."
                ))
                "CAR_SERVICE"  -> pick("hi_det_car", listOf(
                    "Mechanic dhundh raha hoon aapke paas.",
                    "Theek hai, paas ke mechanic dhundh raha hoon."
                ))
                "FOOD"         -> pick("hi_det_food", listOf(
                    "Restaurant dhundh raha hoon aapke paas.",
                    "Theek hai, nearby restaurants dhundh raha hoon."
                ))
                "SALON"        -> pick("hi_det_salon", listOf(
                    "Salon dhundh raha hoon aapke paas.",
                    "Theek hai, paas ka salon dhundh raha hoon."
                ))
                "MEDICINE"     -> pick("hi_det_med", listOf(
                    "Pharmacy dhundh raha hoon aapke paas.",
                    "Paas ki pharmacy khojta hoon."
                ))
                else           -> "Theek hai, $sName dhundh raha hoon aapke paas."
            }
            lang.startsWith("te") -> when (sector.name) {
                "ELECTRICIAN"  -> "Sare, mee daggara electrician chustunnaanu."
                "PLUMBER"      -> "Sare, mee daggara plumber chustunnaanu."
                "DOCTOR"       -> "Sare, mee daggara doctor chustunnaanu."
                "AMBULANCE"    -> "Sare, ambulance pamputunnaanu — ippude!"
                "TAXI"         -> "Sare, mee kosam taxi chustunnaanu."
                "FOOD"         -> "Sare, mee daggara restaurants chustunnaanu."
                else           -> "Sare, mee daggara $sName chustunnaanu."
            }
            lang.startsWith("ta") -> "Sari, ungal pakkathil $sName thedi paarkirein."
            lang.startsWith("kn") -> "Sari, neemma hattira $sName hoadutide."
            else -> when (sector.name) {
                "ELECTRICIAN"  -> "Got it, finding an electrician near you."
                "PLUMBER"      -> "Got it, finding a plumber near you."
                "DOCTOR"       -> "Got it, finding a doctor near you."
                "AMBULANCE"    -> "On it — calling an ambulance now!"
                "TAXI"         -> "Got it, finding a cab near you."
                "CAR_SERVICE"  -> "Got it, finding a mechanic near you."
                "AC_REPAIR"    -> "Got it, finding an AC technician near you."
                "FOOD"         -> "Got it, finding restaurants near you."
                "CLEANING"     -> "Got it, finding a cleaning service near you."
                "SALON"        -> "Got it, finding a salon near you."
                else           -> "Got it, finding a $sName near you."
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. PROVIDER LIST — reads 1-3 providers naturally
    // ══════════════════════════════════════════════════════════════════════

    fun providerList(
        sector:    ServiceSector,
        providers: List<ServiceProvider>,
        lang:      String
    ): String {
        if (providers.isEmpty()) return noProviders(sector, lang)

        val count = providers.size.coerceAtMost(3)
        val sName = sectorLabel(sector.name, lang)
        val base  = lang.substringBefore("-").take(2)

        // Build name-only list — NO numbers, user selects by saying the name
        val items = providers.take(count).joinToString(", ") { p ->
            val name = p.name.split(" ").take(3).joinToString(" ")
            val eta  = p.eta.ifBlank { "" }
            val dist = try { if (p.distanceKm > 0) "${p.distanceKm}km" else "" } catch (_: Exception) { "" }
            val extra = when (base) {
                "hi" -> listOfNotNull(dist.ifBlank { null }, if (eta.isNotBlank()) "$eta mein" else null)
                "te" -> listOfNotNull(dist.ifBlank { null }, if (eta.isNotBlank()) "$eta lo" else null)
                else -> listOfNotNull(dist.ifBlank { null }, if (eta.isNotBlank()) "$eta away" else null)
            }
            "$name${if (extra.isNotEmpty()) " — ${extra.joinToString(", ")}" else ""}"
        }

        return when {
            lang.startsWith("hi") -> pick("hi_prov_list", listOf(
                "$count $sName mile hain paas mein — $items. Kaun sa chahiye? Naam bolein.",
                "$count options hain — $items. Kaunsa $sName chahiye?",
                "Mil gaye — $items. Naam bolein.",
                "Paas mein hain — $items. Kaunsa bhejoon?"
            ))
            lang.startsWith("te") -> pick("te_prov_list", listOf(
                "$count $sName dorukunnaru — $items. Edi kavali? Peyru cheppandi.",
                "Ilaa — $items. Meeru edi antaru?"
            ))
            lang.startsWith("ta") -> pick("ta_prov_list", listOf(
                "$count $sName kidaichal — $items. Edu vendum? Peyar sollunga.",
                "$items — edu vendum?"
            ))
            lang.startsWith("kn") -> "$count $sName sigitu — $items. Yavudu beku? Hesaru heli."
            lang.startsWith("ml") -> "$count $sName kitti — $items. Etha veno? Peru parayo."
            else -> pick("en_prov_list", listOf(
                "Found $count $sName nearby — $items. Which one? Say the name.",
                "$count options — $items. Which would you like?",
                "Here you go — $items. Say the name you want."
            ))
        }
    }

    private fun noProviders(sector: ServiceSector, lang: String): String {
        val sName = sectorLabel(sector.name, lang)
        return when {
            lang.startsWith("hi") -> pick("hi_no_prov", listOf(
                "Sorry, abhi paas mein koi $sName available nahi. Thodi der baad try karein.",
                "$sName nahi mila abhi. Dusri service lein ya baad mein try karein?",
                "Koi $sName nahi mila paas mein."
            ))
            lang.startsWith("te") -> "$sName daggara daggaraga ledu. Inkaa emi kavali?"
            else -> "No $sName found nearby right now. Want to try something else?"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. PROVIDER SELECTED — "you chose X, shall I book?"
    // ══════════════════════════════════════════════════════════════════════

    fun providerSelected(provider: ServiceProvider, lang: String): String {
        val name = provider.name.split(" ").take(3).joinToString(" ")
        val eta  = provider.eta.ifBlank { "15 min" }
        return when {
            lang.startsWith("hi") -> pick("hi_prov_sel", listOf(
                "$name — book karoon? $eta mein aa jayenge.",
                "Theek hai, $name. Confirm karoon? $eta ka kaam hai.",
                "$name select kiya. $eta mein aayenge — book karoon?",
                "Badhiya! $name — $eta mein. Haan bolein toh book ho jayega.",
                "$name — $eta mein. Lagaoon?"
            ))
            lang.startsWith("te") -> pick("te_prov_sel", listOf(
                "$name — book cheyyana? $eta lo vastaru.",
                "Sare, $name. Confirm cheyyana? $eta lo.",
                "$name select chesaru. Book cheyyana?"
            ))
            lang.startsWith("ta") -> pick("ta_prov_sel", listOf(
                "$name — book panna? $eta la varuvaanga.",
                "Sari, $name. Confirm pannana?"
            ))
            lang.startsWith("kn") -> "$name — book madana? $eta li baruttare."
            lang.startsWith("ml") -> "$name — book cheyyatte? $eta il etum."
            else -> pick("en_prov_sel", listOf(
                "$name — shall I book? They'll be there in $eta.",
                "Got it, $name. Confirm the booking? $eta away.",
                "$name selected. Book them? Arriving in $eta.",
                "Great choice — $name, $eta. Shall I confirm?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. BOOKING CONFIRMED — celebratory, with ID and ETA
    // ══════════════════════════════════════════════════════════════════════

    fun bookingConfirmed(provider: ServiceProvider, bookingId: String, lang: String): String {
        val name = provider.name.split(" ").take(3).joinToString(" ")
        val eta  = provider.eta.ifBlank { "15-20 min" }
        return when {
            lang.startsWith("hi") -> pick("hi_book_conf", listOf(
                "Ho gaya! $name aa rahe hain. Booking ID: $bookingId. $eta mein pahunch jayenge.",
                "Badhiya! $name book ho gaye. $bookingId — $eta mein aayenge.",
                "Done! $name confirm. ID $bookingId. $eta mein pahunch jayenge. Shukriya!",
                "Perfect! $name aa rahe hain — $eta. Booking: $bookingId.",
                "Ho gaya booking! $name — $eta mein. ID: $bookingId. Kuch aur chahiye?"
            ))
            lang.startsWith("te") -> pick("te_book_conf", listOf(
                "Aindi! $name vastunnaaru. Booking ID: $bookingId. $eta lo vastaru.",
                "Baagundi! $name confirm. $bookingId — $eta lo. Dhanyavaadaalu!",
                "Done! $name — $eta. ID: $bookingId. Inkaa emi kavali?"
            ))
            lang.startsWith("ta") -> pick("ta_book_conf", listOf(
                "Aachi! $name varuvaanga. Booking ID: $bookingId. $eta la.",
                "Sari! $name confirm. $bookingId — $eta. Nandri!"
            ))
            lang.startsWith("kn") -> "Aaytu! $name baruttare. Booking ID: $bookingId. $eta li. Dhanyavada!"
            lang.startsWith("ml") -> "Ayi! $name varum. Booking ID: $bookingId. $eta il. Nandri!"
            else -> pick("en_book_conf", listOf(
                "Done! $name is on the way. Booking ID: $bookingId. Arriving in $eta.",
                "Booked! $name confirmed — $eta. ID: $bookingId. Thank you!",
                "All set! $name is coming. $eta. Booking: $bookingId.",
                "Great! $name booked. ID $bookingId — should be there in $eta."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. SUBTYPE PROMPT — "which type of service?"
    //    More human than ServiceVoiceHandler.buildSubTypePrompt()
    // ══════════════════════════════════════════════════════════════════════

    fun subTypePrompt(sector: ServiceSector, lang: String): String {
        return when {
            lang.startsWith("hi") -> when (sector.name) {
                "ELECTRICIAN" -> pick("hi_sub_elec", listOf(
                    "Kaunsa kaam chahiye? AC repair, fan, wiring, geyser, ya switch?",
                    "Bijli ka kaunsa kaam? AC, fan, switch, wiring, inverter?",
                    "Electrician ke liye kya — AC repair, fan fitting, wiring, ya socket?"
                ))
                "PLUMBER" -> pick("hi_sub_plum", listOf(
                    "Kaunsa kaam — pipe leak, drain, tap repair, motor, ya bath fitting?",
                    "Paani ka kaunsa problem? Leak, drain jam, tap, motor, ya toilet?",
                    "Plumber ke liye kya kaam — leak, drain, tap, tank, ya bathroom?"
                ))
                "DOCTOR" -> pick("hi_sub_doc", listOf(
                    "Kaunsa doctor chahiye? General, bachche ka, skin, haddi, ya mahila doctor?",
                    "Kya takleef hai? Bukhar, pet dard, baccha, ya specialist — batao.",
                    "General physician, pediatrician, dermatologist, ya gynecologist?"
                ))
                "CARPENTER" -> pick("hi_sub_carp", listOf(
                    "Carpenter ke liye kya kaam — furniture repair, darwaza, almirah, ya bed?",
                    "Kaunsa carpenter kaam — furniture, door/window, almari, ya false ceiling?"
                ))
                "PAINTER" -> pick("hi_sub_paint", listOf(
                    "Painting ka kaunsa kaam — poora kamra, touch-up, waterproofing, ya bahar se?",
                    "Painter ke liye — andar, bahar, waterproof, ya texture painting?"
                ))
                "CLEANING" -> pick("hi_sub_clean", listOf(
                    "Kaunsi cleaning chahiye — poora ghar, deep clean, sofa/carpet, ya kitchen?",
                    "Cleaning ke liye kya — regular, deep cleaning, bathroom, ya post-construction?"
                ))
                "AC_REPAIR" -> pick("hi_sub_ac", listOf(
                    "AC ka kya kaam — servicing, ठंडा nahi, install, gas refill, ya fridge?",
                    "Kaunsa appliance — AC, fridge, washing machine, microwave, ya geyser?"
                ))
                "FOOD" -> pick("hi_sub_food", listOf(
                    "Kya khaana chahiye — biryani, thali, tiffin, Chinese, pizza, ya mithai?",
                    "Khaane mein kya — biryani, dosa, pizza, rolls, ya kuch bhi?"
                ))
                "TAXI" -> pick("hi_sub_taxi", listOf(
                    "Kahan jaana hai? Local, outstation, airport, ya hourly rental?",
                    "Taxi ke liye — local trip, bahar city, ya airport drop?"
                ))
                "SALON" -> pick("hi_sub_salon", listOf(
                    "Salon mein kya chahiye — haircut, facial, waxing, mehendi, ya makeup?",
                    "Beauty ke liye — hair, face, wax, ya bridal?"
                ))
                "CAR_SERVICE" -> pick("hi_sub_car", listOf(
                    "Gaadi ka kaunsa kaam — car wash, oil change, puncture, ya mechanic?",
                    "Mechanic ke liye — car service, two-wheeler, puncture, battery, ya dent?"
                ))
                "TUTOR" -> pick("hi_sub_tutor", listOf(
                    "Kaunsa subject — Maths, Science, English, Computer, ya sab subjects?",
                    "Tutor ke liye kya — Maths, Science, English, ya coaching?"
                ))
                "MEDICINE" -> pick("hi_sub_med", listOf(
                    "Prescription hai? Camera se photo lein ya gallery se upload karein.",
                    "Doctor ki parchi hai? Upload karein — nahin toh medicine ka naam bolein."
                ))
                else -> "Theek hai, kaunsa kaam chahiye bilkul?"
            }
            lang.startsWith("te") -> when (sector.name) {
                "ELECTRICIAN" -> pick("te_sub_elec", listOf(
                    "Em cheyali? AC repair, fan, wiring, geyser, leda switch?",
                    "Electrician ki em — AC, fan, wiring, leda socket repair?"
                ))
                "PLUMBER" -> pick("te_sub_plum", listOf(
                    "Em problem — pipe leak, drain, tap, motor, leda bath fitting?",
                    "Plumber ki em — leak, drain jam, tap repair, leda motor?"
                ))
                "DOCTOR" -> pick("te_sub_doc", listOf(
                    "Em doctor kavali? General, children's, skin, leda specialist?",
                    "Em issue — doctor ki em cheptaru?"
                ))
                "TAXI" -> "Taxi ki — local, outstation, leda airport?"
                "FOOD" -> "Em tinnali? Biryani, thali, tiffin, Chinese, leda pizza?"
                "AC_REPAIR" -> "Em appliance — AC, fridge, washing machine, leda geyser?"
                "CLEANING" -> "Em cleaning — full home, deep clean, sofa, leda kitchen?"
                else -> "Em cheyali?"
            }
            lang.startsWith("ta") -> when (sector.name) {
                "ELECTRICIAN" -> "Em velai? AC, fan, wiring, geyser, switch?"
                "PLUMBER" -> "Em pirachanai? Pipe leak, drain, tap, motor?"
                "DOCTOR" -> "Edu doctor — general, ballan doctor, specialist?"
                else -> "Enna velai?"
            }
            else -> when (sector.name) {
                "ELECTRICIAN" -> pick("en_sub_elec", listOf(
                    "What work? AC repair, fan, wiring, geyser, switch, or inverter?",
                    "Which electrical work — AC, fan installation, wiring, or sockets?",
                    "AC repair, fan, wiring, switch, or something else?"
                ))
                "PLUMBER" -> pick("en_sub_plum", listOf(
                    "What's the issue? Pipe leak, drain blocked, tap, motor, or bath fitting?",
                    "Pipe leak, drain, tap, water tank, toilet, or motor repair?",
                    "What needs fixing? Pipe, drain, tap, or motor?"
                ))
                "DOCTOR" -> pick("en_sub_doc", listOf(
                    "Which doctor? General physician, pediatrician, dermatologist, gynecologist, or dentist?",
                    "What's the concern? General, child, skin, bone, or women's health?",
                    "General, child doctor, specialist, or dentist?"
                ))
                "CARPENTER" -> "Furniture repair, door/window, wardrobe, bed, or false ceiling?"
                "PAINTER" -> "Full room, touch-up, waterproofing, exterior, or texture painting?"
                "CLEANING" -> "Full home clean, deep clean, sofa/carpet, kitchen, or post-construction?"
                "AC_REPAIR" -> "AC service, AC not cooling, install, gas refill, fridge, washing machine, or geyser?"
                "FOOD" -> "Biryani, thali, tiffin, Chinese, pizza, or sweets?"
                "TAXI" -> "Local ride, outstation, airport, or hourly rental?"
                "SALON" -> "Haircut, facial, waxing, mehendi, or makeup?"
                "CAR_SERVICE" -> "Car wash, oil change, tyre/puncture, or general mechanic?"
                "TUTOR" -> "Maths, Science, English, Computer, or all subjects?"
                "MEDICINE" -> "Do you have a prescription? I can read it from a photo."
                else -> "What specifically do you need?"
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. SUBTYPE RETRY — when user's response doesn't match
    // ══════════════════════════════════════════════════════════════════════

    fun subTypeRetry(sector: ServiceSector, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_sub_retry", listOf(
                "Samajh nahi aaya. Kya kaam chahiye — bilkul seedha batao.",
                "Dobara batao — kaunsa kaam exactly?",
                "Clear nahi hua. Kya chahiye?"
            ))
            lang.startsWith("te") -> pick("te_sub_retry", listOf(
                "Artham kaaledu. Meeru em cheppadam?",
                "Malli cheppandi — em kaam?"
            ))
            else -> pick("en_sub_retry", listOf(
                "Didn't catch that. What exactly do you need?",
                "Say the type again — what work?",
                "Not clear. Please say what you need."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. BOOKING CONFIRM PROMPT — "AC repair today — shall I book?"
    // ══════════════════════════════════════════════════════════════════════

    fun bookingConfirmPrompt(sector: ServiceSector, subType: String, timeSlot: String?, lang: String): String {
        val time = timeSlot ?: when {
            lang.startsWith("hi") -> "aaj"
            lang.startsWith("te") -> "ippude"
            else -> "today"
        }
        val sName = sectorLabel(sector.name, lang)
        return when {
            lang.startsWith("hi") -> pick("hi_bconf", listOf(
                "$subType — $time. Confirm karoon?",
                "Theek hai, $subType $time ko. Lagaoon?",
                "$sName — $subType — $time. Book karoon?"
            ))
            lang.startsWith("te") -> "$subType — $time. Confirm cheyyana?"
            else -> "$subType — $time. Shall I book?"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. FILTER RESULT — after user says "nearest" / "cheapest"
    // ══════════════════════════════════════════════════════════════════════

    fun filterResult(sortName: String, count: Int, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_filter", listOf(
                "$sortName wale $count option hain. Naam bolein.",
                "$sortName providers — $count options. Kaunsa chahiye?",
                "$sortName filter — $count mila. Provider ka naam batao."
            ))
            lang.startsWith("te") -> "$sortName providers — $count options. Peyru cheppandi."
            else -> pick("en_filter", listOf(
                "Showing $sortName — $count options. Say the name you want.",
                "$sortName providers: $count nearby. Which one?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 10. GO BACK to provider list
    // ══════════════════════════════════════════════════════════════════════

    fun goBack(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_goback", listOf(
                "Theek hai, pehle wali list pe wapas.",
                "Wapas aa gaye. Phir batao.",
                "Chalo, dobara dekhte hain."
            ))
            lang.startsWith("te") -> "Sare, tirigi chustam."
            else -> pick("en_goback", listOf(
                "Going back to the list.", "Back to providers.", "OK, let's try again."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 11. RETURN TO MAIN — after booking, back to grocery / main flow
    // ══════════════════════════════════════════════════════════════════════

    fun returnToMain(bookingId: String, lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_return", listOf(
                "Booking ho gayi! ID: $bookingId. Kuch grocery bhi mangwaaoon?",
                "Service book ho gayi — $bookingId. Grocery order karein?",
                "Done! $bookingId. Kuch aur chahiye — grocery ya koi aur service?"
            ))
            lang.startsWith("te") -> pick("te_return", listOf(
                "Booking aindi! ID: $bookingId. Grocery kuda kavala?",
                "Service book aindi — $bookingId. Inkaa grocery?"
            ))
            lang.startsWith("ta") -> "Booking aachi! $bookingId. Grocery vendum?"
            lang.startsWith("kn") -> "Booking aaytu! $bookingId. Grocery beku?"
            lang.startsWith("ml") -> "Booking ayi! $bookingId. Grocery veno?"
            else -> pick("en_return", listOf(
                "Booking confirmed! ID: $bookingId. Want to order groceries too?",
                "Done! $bookingId confirmed. Anything else — groceries or another service?",
                "Service booked — $bookingId. What else can I get you?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 12. RETURN WITH NO BOOKING — came back without completing a booking
    // ══════════════════════════════════════════════════════════════════════

    fun returnNoBooking(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_no_book", listOf(
                "Koi baat nahi. Kya grocery order karein?",
                "Theek hai. Kuch aur chahiye?",
                "Okay. Kya la doon?"
            ))
            lang.startsWith("te") -> "Parvaaledu. Inkaa emi kavali?"
            else -> pick("en_no_book", listOf(
                "No problem. Want to order something else?",
                "That's fine. Anything else I can help with?",
                "OK. What else can I get you?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 13. PRESCRIPTION MESSAGES
    // ══════════════════════════════════════════════════════════════════════

    fun prescriptionFound(medicines: List<String>, pharmacyCount: Int, lang: String): String {
        val list = medicines.take(4).joinToString(", ")
        val more = if (medicines.size > 4) " aur ${medicines.size - 4} aur" else ""
        return when {
            lang.startsWith("hi") -> pick("hi_rx_found", listOf(
                "Prescription se mila: $list$more. $pharmacyCount pharmacy paas mein. Naam bolein.",
                "Padhh liya — $list$more. $pharmacyCount pharmacy available. Kaunsi chahiye?",
                "$list$more — $pharmacyCount pharmacy mili. Pharmacy ka naam bolein."
            ))
            lang.startsWith("te") -> "$list$more dorukunnaayi. $pharmacyCount pharmacy daggara undi. Peyru cheppandi."
            else -> pick("en_rx_found", listOf(
                "Read your prescription: $list$more. $pharmacyCount pharmacy nearby. Say the name.",
                "Found $list$more. $pharmacyCount pharmacies available — which one?"
            ))
        }
    }

    fun prescriptionManual(medicines: List<String>, pharmacyCount: Int, lang: String): String {
        val list = medicines.joinToString(", ")
        return when {
            lang.startsWith("hi") -> pick("hi_rx_manual", listOf(
                "Aapne bataya: $list. $pharmacyCount pharmacy paas mein. Naam bolein.",
                "Theek hai — $list. $pharmacyCount pharmacy available. Kaunsi chahiye?",
                "$list — $pharmacyCount pharmacy mili. Pharmacy ka naam batao."
            ))
            lang.startsWith("te") -> "Meeru chepparu: $list. $pharmacyCount pharmacy daggaraga undi. Peyru cheppandi."
            else -> pick("en_rx_manual", listOf(
                "You said: $list. $pharmacyCount pharmacy nearby. Say the name.",
                "Found $pharmacyCount pharmacy for $list — which one?"
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 14. YES/NO CONFIRM PROMPT (during booking)
    // ══════════════════════════════════════════════════════════════════════

    fun confirmYesNoPrompt(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_yn", listOf(
                "Haan bolein toh book, nahi bolein toh wapas.",
                "Confirm karein? Haan ya nahi?",
                "Book karoon? Haan ya nahi batao."
            ))
            lang.startsWith("te") -> "Confirm cheyyana? Avunu leda kaadu?"
            else -> pick("en_yn", listOf(
                "Say yes to confirm, no to go back.",
                "Confirm the booking? Yes or no?",
                "Shall I book? Say yes or no."
            ))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 15. SELECTION RETRY (when user doesn't say 1/2/3)
    // ══════════════════════════════════════════════════════════════════════

    fun selectionRetry(lang: String): String {
        return when {
            lang.startsWith("hi") -> pick("hi_sel_retry", listOf(
                "Naam bolein — kaun sa chahiye?",
                "Kaunsa? Provider ka naam batao.",
                "Naam bolein please."
            ))
            lang.startsWith("te") -> pick("te_sel_retry", listOf(
                "Peyru cheppandi — edi kavali?",
                "Provider peyru cheppandi."
            ))
            lang.startsWith("ta") -> "Peyar sollunga — edu vendum?"
            lang.startsWith("kn") -> "Hesaru heli — yavudu beku?"
            lang.startsWith("ml") -> "Peru parayo — etha veno?"
            else -> pick("en_sel_retry", listOf(
                "Say the name — which one do you want?",
                "Which provider? Say the name.",
                "Please say the name."
            ))
        }
    }
}