package com.demo.butler_voice_app.services

// ══════════════════════════════════════════════════════════════════════════════
// INDIA SERVICES PLATFORM — Every Sector, Every Industry
// ══════════════════════════════════════════════════════════════════════════════

// ── Master service category enum ──────────────────────────────────────────────
enum class ServiceSector(val displayName: String, val emoji: String, val voiceKeywords: List<String>) {

    // ── GROCERY & DAILY ESSENTIALS ─────────────────────────────────────────────
    GROCERY("Grocery & Daily Needs", "🛒",
        listOf("grocery", "sabzi", "vegetables", "fruits", "milk", "bread", "dal", "chawal", "rice", "oil", "masala")),

    // ── HEALTHCARE ─────────────────────────────────────────────────────────────
    MEDICINE("Medicine & Pharmacy", "💊",
        listOf("medicine", "dawa", "tablet", "syrup", "pharmacy", "chemist", "dawai", "prescription", "doctor", "hospital")),

    DOCTOR("Doctor Consultation", "👨‍⚕️",
        listOf("doctor", "physician", "specialist", "dermatologist", "cardiologist", "pediatrician", "gynecologist", "orthopedic", "appointment")),

    DIAGNOSTIC("Lab Tests & Diagnostics", "🔬",
        listOf("blood test", "x-ray", "scan", "ultrasound", "ecg", "urine test", "lab", "diagnostic", "pathology", "report")),

    AMBULANCE("Ambulance & Emergency", "🚑",
        listOf("ambulance", "emergency", "icu", "urgent", "accident", "hospital emergency")),

    HOME_NURSING("Home Nursing & Care", "🏥",
        listOf("nurse", "nursing", "patient care", "elderly care", "physiotherapy", "home care", "caretaker")),

    // ── HOME SERVICES ──────────────────────────────────────────────────────────
    PLUMBER("Plumber", "🔧",
        listOf("plumber", "pipe", "leak", "water", "tap", "drainage", "plumbing", "nal")),

    ELECTRICIAN("Electrician", "⚡",
        listOf("electrician", "wiring", "switch", "fan", "light", "electricity", "bijli", "short circuit")),

    CARPENTER("Carpenter", "🪚",
        listOf("carpenter", "furniture", "wood", "door", "window", "repair", "almira", "table", "chair")),

    PAINTER("Painter", "🎨",
        listOf("painter", "painting", "whitewash", "wall", "colour", "paint", "rang")),

    AC_REPAIR("AC & Appliance Repair", "❄️",
        listOf("ac", "air conditioner", "repair", "service", "washing machine", "refrigerator", "fridge", "tv repair", "microwave")),

    CLEANING("Home Cleaning", "🧹",
        listOf("cleaning", "maid", "housekeeping", "safai", "bai", "cook", "chef", "khana")),

    PEST_CONTROL("Pest Control", "🐛",
        listOf("pest", "cockroach", "rat", "termite", "mosquito", "fumigation", "keeda")),

    SECURITY("Security Guard", "💂",
        listOf("security", "guard", "watchman", "chowkidar", "cctv", "surveillance")),

    // ── TRANSPORTATION ─────────────────────────────────────────────────────────
    TAXI("Cab & Taxi", "🚗",
        listOf("cab", "taxi", "auto", "ride", "car", "uber", "ola", "book taxi", "gaadi")),

    TRUCK("Truck & Tempo", "🚚",
        listOf("truck", "tempo", "moving", "shifting", "load", "packers", "movers", "samaan")),

    TWO_WHEELER("Two Wheeler Service", "🏍️",
        listOf("bike", "scooter", "two wheeler", "motorcycle", "puncture", "petrol")),

    // ── FOOD & RESTAURANTS ─────────────────────────────────────────────────────
    FOOD("Food Delivery", "🍱",
        listOf("food", "khana", "biryani", "pizza", "order food", "restaurant", "tiffin", "dabba", "thali")),

    CATERING("Catering & Events", "🍽️",
        listOf("catering", "event", "party", "wedding", "function", "shaadi", "reception", "bhandara")),

    // ── FINANCE & BANKING ──────────────────────────────────────────────────────
    INSURANCE("Insurance", "🛡️",
        listOf("insurance", "bima", "health insurance", "life insurance", "car insurance", "claim", "policy")),

    LOAN("Loan & Finance", "💰",
        listOf("loan", "karz", "home loan", "personal loan", "business loan", "emi", "credit", "mudra")),

    CA_SERVICES("CA & Tax Services", "📊",
        listOf("ca", "chartered accountant", "income tax", "itr", "gst", "filing", "return", "tax")),

    // ── EDUCATION ──────────────────────────────────────────────────────────────
    TUTOR("Tutor & Teacher", "📚",
        listOf("tutor", "teacher", "coaching", "classes", "tuition", "maths", "science", "english", "homework")),

    SKILL_TRAINING("Skill Training", "🎓",
        listOf("training", "skill", "course", "certificate", "vocational", "iti", "diploma", "computer class")),

    // ── BEAUTY & WELLNESS ──────────────────────────────────────────────────────
    SALON("Salon & Beauty", "💇",
        listOf("salon", "haircut", "parlour", "beauty", "facial", "waxing", "makeup", "mehendi", "cut")),

    SPA("Spa & Massage", "💆",
        listOf("spa", "massage", "relaxation", "body massage", "head massage", "stress", "ayurveda")),

    FITNESS("Gym & Fitness", "🏋️",
        listOf("gym", "fitness", "trainer", "yoga", "exercise", "diet", "weight loss", "health")),

    // ── LEGAL & GOVERNMENT ─────────────────────────────────────────────────────
    LEGAL("Legal Services", "⚖️",
        listOf("lawyer", "advocate", "legal", "court", "case", "property", "divorce", "rent agreement", "notary")),

    GOVT_DOCS("Government Documents", "📄",
        listOf("aadhar", "pan", "passport", "driving license", "birth certificate", "ration card", "caste certificate", "income certificate")),

    // ── REAL ESTATE ────────────────────────────────────────────────────────────
    REAL_ESTATE("Real Estate", "🏠",
        listOf("house", "flat", "rent", "buy", "property", "plot", "pg", "hostel", "room", "makaan")),

    // ── AGRICULTURE ────────────────────────────────────────────────────────────
    AGRICULTURE("Agriculture & Farming", "🌾",
        listOf("seeds", "fertilizer", "pesticide", "tractor", "irrigation", "soil", "crop", "kisan", "farming", "kheti")),

    // ── EVENTS & PHOTOGRAPHY ──────────────────────────────────────────────────
    PHOTOGRAPHY("Photography & Video", "📸",
        listOf("photographer", "photo", "video", "wedding photo", "event photo", "drone", "videographer")),

    EVENT_MGMT("Event Management", "🎉",
        listOf("event", "decoration", "tent", "mandap", "flowers", "dj", "sound system", "stage")),

    // ── TAILORING & FASHION ────────────────────────────────────────────────────
    TAILOR("Tailoring & Stitching", "🧵",
        listOf("tailor", "stitching", "alteration", "blouse", "suit", "clothes", "darzi", "kapde")),

    // ── IT & DIGITAL ───────────────────────────────────────────────────────────
    IT_SUPPORT("IT & Computer Support", "💻",
        listOf("computer", "laptop", "phone repair", "software", "printer", "wifi", "internet", "virus", "data recovery")),

    DIGITAL_MARKETING("Digital Marketing", "📱",
        listOf("social media", "website", "seo", "marketing", "ads", "facebook", "instagram", "google ads")),

    // ── VEHICLE SERVICES ───────────────────────────────────────────────────────
    CAR_SERVICE("Car Service & Wash", "🚘",
        listOf("car service", "car wash", "oil change", "tyre", "puncture", "mechanic", "garage", "service center")),

    // ── DELIVERY & COURIER ─────────────────────────────────────────────────────
    COURIER("Courier & Delivery", "📦",
        listOf("courier", "delivery", "parcel", "send", "shipping", "delhivery", "blue dart", "dtdc")),

    // ── BLUE COLLAR WORKERS ────────────────────────────────────────────────────
    DAILY_WAGE("Daily Wage Workers", "👷",
        listOf("labour", "worker", "helper", "loading", "unloading", "construction", "mazdoor", "kaamgar")),

    DRIVER("Driver on Demand", "🚗",
        listOf("driver", "chauffeur", "personal driver", "dd driver", "drunk driver", "late night")),

    // ── WELLNESS & SPIRITUAL ──────────────────────────────────────────────────
    PANDIT("Pandit & Puja Services", "🙏",
        listOf("pandit", "puja", "pooja", "hawan", "kundali", "astrology", "jyotish", "muhurat", "griha pravesh")),

    // ── PET SERVICES ──────────────────────────────────────────────────────────
    PET_CARE("Pet Care", "🐾",
        listOf("pet", "dog", "cat", "vet", "veterinary", "grooming", "pet food", "animal doctor")),

    // ── WATER & GAS ───────────────────────────────────────────────────────────
    WATER_GAS("Water & Gas Delivery", "💧",
        listOf("water", "pani", "water can", "gas cylinder", "lpg", "cylinder", "aquaguard"))
}

// ── Service provider model ────────────────────────────────────────────────────
data class ServiceProvider(
    val id: String,
    val name: String,
    val sector: ServiceSector,
    val rating: Double,           // 1.0 - 5.0
    val reviewCount: Int,
    val priceMin: Int,            // in rupees
    val priceMax: Int,
    val priceUnit: String,        // "per hour", "per visit", "per kg", etc.
    val distanceKm: Double,
    val isAvailable: Boolean,
    val eta: String,              // "15 min", "1 hour", etc.
    val tags: List<String>,       // ["verified", "trained", "background checked"]
    val location: String,
    val phone: String = "",
    val languages: List<String> = listOf("Hindi", "English"),
    val experience: String = "",  // "5 years"
    val description: String = ""
)

// ── Prescription upload model ──────────────────────────────────────────────────
data class PrescriptionOrder(
    val id: String,
    val userId: String,
    val imageUri: String,         // local URI of uploaded prescription image
    val uploadedAt: Long,
    val status: PrescriptionStatus,
    val medicines: List<PrescriptionMedicine> = emptyList(),
    val shopName: String = "",
    val totalAmount: Double = 0.0,
    val deliveryEta: String = ""
)

enum class PrescriptionStatus {
    UPLOADING, SENT_TO_SHOPS, QUOTE_RECEIVED, CONFIRMED, OUT_FOR_DELIVERY, DELIVERED
}

data class PrescriptionMedicine(
    val name: String,
    val quantity: String,
    val price: Double,
    val available: Boolean
)

// ── Voice-detected service intent ─────────────────────────────────────────────
data class ServiceIntent(
    val sector: ServiceSector?,
    val query: String,
    val isPrescription: Boolean = false,
    val isEmergency: Boolean    = false,
    val location: String        = "",
    val budget: Int             = 0    // 0 means not specified
)

// ── Service search filters ────────────────────────────────────────────────────
data class ServiceFilter(
    val maxDistanceKm: Double  = 10.0,
    val minRating: Double      = 3.0,
    val maxPricePerHour: Int   = Int.MAX_VALUE,
    val availableOnly: Boolean = true,
    val sortBy: ServiceSort    = ServiceSort.RELEVANCE
)

enum class ServiceSort { RELEVANCE, RATING, PRICE_LOW, PRICE_HIGH, DISTANCE, FASTEST }