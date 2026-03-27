package com.demo.butler_voice_app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.demo.butler_voice_app.services.*

// ══════════════════════════════════════════════════════════════════════════════
// DESIGN TOKENS (India-inspired palette)
// ══════════════════════════════════════════════════════════════════════════════

private val SBgDeep     = Color(0xFF060B12)
private val SBgCard     = Color(0xFF0C1520)
private val SBgCardAlt  = Color(0xFF101826)
private val STeal       = Color(0xFF00D4AA)
private val SBlue       = Color(0xFF4F9EFF)
private val SGold       = Color(0xFFFFB830)
private val SOrange     = Color(0xFFFF6B35)
private val SRed        = Color(0xFFFF4444)
private val SGreen      = Color(0xFF00E676)
private val SPurple     = Color(0xFFB57BFF)
private val SPink       = Color(0xFFFF6B9D)
private val STextPri    = Color(0xFFF0F4FF)
private val STextSec    = Color(0xFF8899BB)
private val SBorder     = Color(0xFF1A2840)

// ── Sector colour map ─────────────────────────────────────────────────────────
private fun sectorColor(sector: ServiceSector) = when (sector) {
    ServiceSector.GROCERY, ServiceSector.FOOD          -> SGreen
    ServiceSector.MEDICINE, ServiceSector.DOCTOR,
    ServiceSector.DIAGNOSTIC, ServiceSector.AMBULANCE,
    ServiceSector.HOME_NURSING                         -> SRed
    ServiceSector.PLUMBER, ServiceSector.ELECTRICIAN,
    ServiceSector.CARPENTER, ServiceSector.PAINTER,
    ServiceSector.AC_REPAIR, ServiceSector.CLEANING,
    ServiceSector.PEST_CONTROL                         -> SBlue
    ServiceSector.TAXI, ServiceSector.TRUCK,
    ServiceSector.TWO_WHEELER, ServiceSector.DRIVER,
    ServiceSector.COURIER                              -> SOrange
    ServiceSector.INSURANCE, ServiceSector.LOAN,
    ServiceSector.CA_SERVICES                          -> SGold
    ServiceSector.TUTOR, ServiceSector.SKILL_TRAINING  -> SPurple
    ServiceSector.SALON, ServiceSector.SPA,
    ServiceSector.FITNESS                              -> SPink
    ServiceSector.REAL_ESTATE, ServiceSector.LEGAL,
    ServiceSector.GOVT_DOCS                            -> STeal
    ServiceSector.AGRICULTURE                          -> Color(0xFF76C442)
    else                                               -> SBlue
}

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE SECTOR HOME SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ServicesSectorScreen(
    onSectorSelected: (ServiceSector) -> Unit = {},
    onVoiceSearch: () -> Unit = {}
) {
    val sectors = ServiceSector.values().toList()

    // Group sectors into India-relevant buckets
    val groups = listOf(
        "Healthcare" to listOf(ServiceSector.MEDICINE, ServiceSector.DOCTOR, ServiceSector.DIAGNOSTIC, ServiceSector.AMBULANCE, ServiceSector.HOME_NURSING),
        "Home Services" to listOf(ServiceSector.PLUMBER, ServiceSector.ELECTRICIAN, ServiceSector.CARPENTER, ServiceSector.PAINTER, ServiceSector.AC_REPAIR, ServiceSector.CLEANING, ServiceSector.PEST_CONTROL),
        "Food & Grocery" to listOf(ServiceSector.GROCERY, ServiceSector.FOOD, ServiceSector.CATERING, ServiceSector.WATER_GAS),
        "Transport" to listOf(ServiceSector.TAXI, ServiceSector.TRUCK, ServiceSector.TWO_WHEELER, ServiceSector.DRIVER, ServiceSector.COURIER),
        "Finance & Legal" to listOf(ServiceSector.INSURANCE, ServiceSector.LOAN, ServiceSector.CA_SERVICES, ServiceSector.LEGAL, ServiceSector.GOVT_DOCS),
        "Education & Skills" to listOf(ServiceSector.TUTOR, ServiceSector.SKILL_TRAINING, ServiceSector.IT_SUPPORT, ServiceSector.DIGITAL_MARKETING),
        "Beauty & Wellness" to listOf(ServiceSector.SALON, ServiceSector.SPA, ServiceSector.FITNESS, ServiceSector.PET_CARE),
        "Agriculture & More" to listOf(ServiceSector.AGRICULTURE, ServiceSector.REAL_ESTATE, ServiceSector.PHOTOGRAPHY, ServiceSector.EVENT_MGMT, ServiceSector.TAILOR, ServiceSector.PANDIT, ServiceSector.SECURITY, ServiceSector.DAILY_WAGE)
    )

    Column(
        Modifier.fillMaxSize().background(SBgDeep).verticalScroll(rememberScrollState())
    ) {
        // ── Header ──────────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A1628), SBgDeep)))
                .padding(20.dp)
        ) {
            Column {
                Text("Butler Services", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = STextPri)
                Text("India's complete voice-first service platform", fontSize = 12.sp, color = STextSec)
                Spacer(Modifier.height(14.dp))

                // Voice search bar
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(SBgCard).border(1.dp, STeal.copy(0.4f), RoundedCornerShape(16.dp))
                        .clickable { onVoiceSearch() }.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎤", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Say what you need", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = STeal)
                            Text("\"I need a plumber\" · \"Book a doctor\" · \"Medicine delivery\"", fontSize = 11.sp, color = STextSec)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Emergency bar
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SRed.copy(0.15f)).border(1.dp, SRed.copy(0.5f), RoundedCornerShape(12.dp))
                        .clickable { onSectorSelected(ServiceSector.AMBULANCE) }.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚑", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Emergency / Ambulance", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = SRed)
                            Text("Say \"Emergency\" or tap here — 24/7 available", fontSize = 11.sp, color = STextSec)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("108", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SRed)
                    }
                }
            }
        }

        // ── Prescription Upload Banner ─────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF1A0A2E), Color(0xFF0A1828))))
                .border(1.dp, SPurple.copy(0.4f), RoundedCornerShape(16.dp))
                .clickable { onSectorSelected(ServiceSector.MEDICINE) }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋", fontSize = 28.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Upload Prescription", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SPurple)
                    Text("AI reads your prescription & finds medicines nearby", fontSize = 11.sp, color = STextSec)
                }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(SPurple.copy(0.2f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("RAG AI", fontSize = 11.sp, color = SPurple, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Sector groups ──────────────────────────────────────────────────────
        groups.forEach { (groupName, groupSectors) ->
            SectorGroup(groupName, groupSectors, onSectorSelected)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectorGroup(name: String, sectors: List<ServiceSector>, onSelect: (ServiceSector) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = STextSec, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
        val rows = sectors.chunked(4)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sector ->
                    SectorTile(sector, Modifier.weight(1f)) { onSelect(sector) }
                }
                // Fill empty slots in last row
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectorTile(sector: ServiceSector, modifier: Modifier, onClick: () -> Unit) {
    val color = sectorColor(sector)
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(color.copy(0.1f)).border(1.dp, color.copy(0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sector.emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                sector.displayName.split(" & ").first().split(" ").first(),
                fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE PROVIDERS LIST SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ServiceProvidersScreen(
    sector: ServiceSector,
    providers: List<ServiceProvider>,
    query: String = "",
    isLoading: Boolean = false,
    onProviderSelected: (Int) -> Unit = {},
    onFilterChange: (ServiceFilter) -> Unit = {},
    onVoiceFilter: () -> Unit = {}
) {
    val color = sectorColor(sector)

    Column(Modifier.fillMaxSize().background(SBgDeep)) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(color.copy(0.15f), SBgDeep)))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sector.emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(sector.displayName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = STextPri)
                        if (query.isNotBlank()) Text("\"$query\"", fontSize = 12.sp, color = color)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Filter row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip("⭐ Rating", STextSec) { onVoiceFilter() }
                    FilterChip("📍 Nearest", STextSec) { onVoiceFilter() }
                    FilterChip("💰 Price", STextSec) { onVoiceFilter() }
                    FilterChip("🎤 Voice Filter", color) { onVoiceFilter() }
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val inf = rememberInfiniteTransition(label = "load")
                    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "r")
                    Box(Modifier.size(60.dp).rotate(rot).background(color.copy(0.12f), CircleShape).border(2.dp, color.copy(0.5f), CircleShape))
                    Spacer(Modifier.height(16.dp))
                    Text("Finding providers near you…", fontSize = 14.sp, color = STextSec)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("${providers.size} providers found", fontSize = 13.sp, color = STextSec, modifier = Modifier.padding(bottom = 12.dp))

                providers.forEachIndexed { idx, provider ->
                    ServiceProviderCard(idx + 1, provider, color) { onProviderSelected(idx + 1) }
                    Spacer(Modifier.height(12.dp))
                }

                // Voice selection hint
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(color.copy(0.08f)).border(1.dp, color.copy(0.25f), RoundedCornerShape(14.dp)).padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎤", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("Say 1, 2 or 3 to select · Say \"filter by rating\" to sort", fontSize = 12.sp, color = color)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(0.1f)).border(1.dp, color.copy(0.3f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    ) { Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ServiceProviderCard(number: Int, provider: ServiceProvider, accentColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SBgCard)
            .border(1.dp, accentColor.copy(0.2f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Number badge
                Box(
                    Modifier.size(38.dp).background(accentColor.copy(0.15f), CircleShape).border(1.5.dp, accentColor.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("$number", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = accentColor) }

                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = STextPri, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", fontSize = 10.sp)
                        Text(" ${provider.rating}", fontSize = 12.sp, color = SGold, fontWeight = FontWeight.Bold)
                        Text("  (${provider.reviewCount} reviews)", fontSize = 10.sp, color = STextSec)
                    }
                }
                // ETA badge
                Column(horizontalAlignment = Alignment.End) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(accentColor.copy(0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(provider.eta, fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.Bold)
                    }
                    Text("${provider.distanceKm}km away", fontSize = 10.sp, color = STextSec)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Price + distance row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (provider.priceMin > 0) {
                    Text(
                        "₹${provider.priceMin}${if (provider.priceMax > provider.priceMin) "–${provider.priceMax}" else ""} ${provider.priceUnit}",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SGold
                    )
                } else {
                    Text(provider.priceUnit, fontSize = 12.sp, color = STextSec)
                }
                if (provider.experience.isNotBlank()) {
                    Text(provider.experience, fontSize = 11.sp, color = STextSec)
                }
            }

            // Tags
            if (provider.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    provider.tags.take(3).forEach { tag ->
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(SGreen.copy(0.1f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("✓ $tag", fontSize = 10.sp, color = SGreen)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PRESCRIPTION UPLOAD SCREEN (RAG Flow)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PrescriptionUploadScreen(
    status: PrescriptionUploadStatus = PrescriptionUploadStatus.WAITING,
    extractedMedicines: List<String> = emptyList(),
    providers: List<ServiceProvider> = emptyList(),
    onUploadTap: () -> Unit = {},
    onProviderSelected: (Int) -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().background(SBgDeep).verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Header
        Text("📋", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("Medicine from Prescription", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = STextPri)
        Spacer(Modifier.height(6.dp))
        Text("Upload your prescription · AI reads it · Pharmacy delivers", fontSize = 13.sp, color = STextSec, textAlign = TextAlign.Center)

        Spacer(Modifier.height(24.dp))

        when (status) {
            PrescriptionUploadStatus.WAITING -> {
                // Upload area
                Box(
                    Modifier.fillMaxWidth().height(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SPurple.copy(0.08f))
                        .border(2.dp, Brush.linearGradient(listOf(SPurple.copy(0.6f), SBlue.copy(0.4f))), RoundedCornerShape(20.dp))
                        .clickable { onUploadTap() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 40.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Tap to photograph your prescription", fontSize = 14.sp, color = SPurple, fontWeight = FontWeight.SemiBold)
                        Text("or upload from gallery", fontSize = 12.sp, color = STextSec)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Voice alternative
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(STeal.copy(0.08f)).border(1.dp, STeal.copy(0.3f), RoundedCornerShape(14.dp)).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎤", fontSize = 22.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Or say the medicine names", fontSize = 14.sp, color = STeal, fontWeight = FontWeight.SemiBold)
                        Text("\"I need Paracetamol 500mg and Azithromycin\"", fontSize = 11.sp, color = STextSec, textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // How it works
                Text("How it works", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = STextPri)
                Spacer(Modifier.height(10.dp))
                listOf(
                    Triple("1", "📋", "Upload prescription photo"),
                    Triple("2", "🤖", "AI reads & extracts medicine names"),
                    Triple("3", "🏪", "Nearby pharmacies get your request"),
                    Triple("4", "🚴", "Medicine delivered to your door")
                ).forEach { (num, emoji, text) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).background(SPurple.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(num, fontSize = 13.sp, color = SPurple, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(text, fontSize = 13.sp, color = STextPri)
                    }
                }
            }

            PrescriptionUploadStatus.PROCESSING -> {
                val inf = rememberInfiniteTransition(label = "rx")
                val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "r")
                val pulse by inf.animateFloat(0.8f, 1.0f, infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "p")

                Box(Modifier.size((80 * pulse).dp).rotate(rot).background(SPurple.copy(0.15f), CircleShape).border(2.dp, SPurple.copy(0.6f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("🤖", fontSize = 32.sp)
                }
                Spacer(Modifier.height(20.dp))
                Text("AI reading your prescription…", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = STextPri)
                Spacer(Modifier.height(8.dp))
                Text("Extracting medicine names using GPT-4 Vision", fontSize = 13.sp, color = STextSec, textAlign = TextAlign.Center)
            }

            PrescriptionUploadStatus.MEDICINES_FOUND -> {
                // Show extracted medicines
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SGreen.copy(0.1f)).border(1.dp, SGreen.copy(0.3f), RoundedCornerShape(16.dp)).padding(18.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✅", fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("Prescription Read Successfully", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SGreen)
                        }
                        Spacer(Modifier.height(12.dp))
                        extractedMedicines.forEach { med ->
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(SGreen, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(med, fontSize = 14.sp, color = STextPri, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Nearby Pharmacies", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = STextPri)
                Text("All have your medicines in stock", fontSize = 12.sp, color = STextSec)
                Spacer(Modifier.height(12.dp))

                providers.forEachIndexed { idx, provider ->
                    ServiceProviderCard(idx + 1, provider, SRed) { onProviderSelected(idx + 1) }
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(STeal.copy(0.08f)).border(1.dp, STeal.copy(0.25f), RoundedCornerShape(14.dp)).padding(14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("🎤  Say 1, 2 or 3 to choose pharmacy", fontSize = 13.sp, color = STeal, fontWeight = FontWeight.SemiBold) }
            }

            PrescriptionUploadStatus.SENT_TO_PHARMACY -> {
                val inf = rememberInfiniteTransition(label = "sent")
                val scale by inf.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), label = "s")
                Box(Modifier.size((90 * scale).dp).background(SGreen.copy(0.15f), CircleShape).border(2.dp, SGreen.copy(0.5f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", fontSize = 40.sp, color = SGreen, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(20.dp))
                Text("Order Sent to Pharmacy!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SGreen)
                Spacer(Modifier.height(8.dp))
                Text("The pharmacy is preparing your medicines.\nDelivery expected in 30–45 minutes.", fontSize = 14.sp, color = STextSec, textAlign = TextAlign.Center, lineHeight = 20.sp)
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SBgCard).border(1.dp, SBorder, RoundedCornerShape(14.dp)).padding(16.dp)
                ) {
                    Column {
                        listOf("Order received by pharmacy" to true, "Medicines being packed" to true, "Out for delivery" to false, "Delivered" to false).forEach { (step, done) ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (done) "✅" else "⏳", fontSize = 14.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(step, fontSize = 13.sp, color = if (done) STextPri else STextSec)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

enum class PrescriptionUploadStatus {
    WAITING, PROCESSING, MEDICINES_FOUND, SENT_TO_PHARMACY
}

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE BOOKING CONFIRMATION SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ServiceBookingScreen(
    provider: ServiceProvider,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val color = sectorColor(provider.sector)
    val inf   = rememberInfiniteTransition(label = "book")
    val pulse by inf.animateFloat(0.92f, 1.0f, infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), label = "p")

    Column(Modifier.fillMaxSize().background(SBgDeep).padding(22.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))

        Box(Modifier.size((80 * pulse).dp).background(color.copy(0.15f), CircleShape).border(2.dp, color.copy(0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Text(provider.sector.emoji, fontSize = (32 * pulse).sp)
        }

        Spacer(Modifier.height(18.dp))
        Text("Confirm Booking", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = STextPri)
        Spacer(Modifier.height(6.dp))
        Text(provider.sector.displayName, fontSize = 14.sp, color = color)

        Spacer(Modifier.height(20.dp))

        // Provider card
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SBgCard).border(1.dp, color.copy(0.3f), RoundedCornerShape(18.dp)).padding(20.dp)) {
            Column {
                Text(provider.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = STextPri)
                Spacer(Modifier.height(8.dp))
                BookingDetailRow("⭐ Rating", "${provider.rating} (${provider.reviewCount} reviews)")
                BookingDetailRow("📍 Distance", "${provider.distanceKm}km away")
                BookingDetailRow("⏱ Arrival", provider.eta)
                if (provider.priceMin > 0) BookingDetailRow("💰 Charges", "₹${provider.priceMin}–${provider.priceMax} ${provider.priceUnit}")
                if (provider.experience.isNotBlank()) BookingDetailRow("💼 Experience", provider.experience)
                if (provider.tags.isNotEmpty()) BookingDetailRow("✅ Verified", provider.tags.joinToString(", "))
            }
        }

        Spacer(Modifier.height(24.dp))

        // Voice confirm box
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(SGreen.copy(0.12f), STeal.copy(0.06f))))
                .border(1.dp, SGreen.copy(0.4f), RoundedCornerShape(18.dp)).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎤", fontSize = 28.sp)
                Spacer(Modifier.height(10.dp))
                Text("Say to confirm:", fontSize = 13.sp, color = STextSec)
                Spacer(Modifier.height(4.dp))
                Text("\"Yes, book it\"  or  \"Confirm\"", fontSize = 16.sp, color = STextPri, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Say \"No\" or \"Cancel\" to go back", fontSize = 12.sp, color = STextSec)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BookingDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = STextSec)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = STextPri)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE BOOKED SUCCESS SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ServiceBookedScreen(
    provider: ServiceProvider,
    bookingId: String,
    eta: String
) {
    val color = sectorColor(provider.sector)
    val inf   = rememberInfiniteTransition(label = "booked")
    val scale by inf.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), label = "s")

    Column(Modifier.fillMaxSize().background(SBgDeep).padding(22.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))

        Box(Modifier.size((90 * scale).dp).background(SGreen.copy(0.15f), CircleShape).border(2.dp, SGreen.copy(0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 40.sp, color = SGreen, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(18.dp))
        Text("${provider.sector.emoji} Booked!", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = SGreen)
        Spacer(Modifier.height(6.dp))
        Text(provider.name, fontSize = 16.sp, color = STextSec)

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(color.copy(0.1f), SBgCard)))
                .border(1.dp, color.copy(0.3f), RoundedCornerShape(18.dp)).padding(20.dp)
        ) {
            Column {
                BookingDetailRow("🔖 Booking ID", bookingId)
                Spacer(Modifier.height(8.dp))
                BookingDetailRow("⏱ ETA",         eta)
                Spacer(Modifier.height(8.dp))
                BookingDetailRow("📍 Provider",    provider.name)
                Spacer(Modifier.height(8.dp))
                BookingDetailRow("✅ Status",       "Confirmed")
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(STeal.copy(0.08f)).border(1.dp, STeal.copy(0.2f), RoundedCornerShape(14.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚴", fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("On the way!", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = STeal)
                    Text("Arriving in $eta", fontSize = 12.sp, color = STextSec)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(0.08f)).border(1.dp, color.copy(0.2f), RoundedCornerShape(12.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
            Text("💡  Say \"Hey Butler\" for another service", fontSize = 13.sp, color = color, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
    }
}