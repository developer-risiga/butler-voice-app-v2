package com.demo.butler_voice_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

// ── Colors ────────────────────────────────────────────────────────────────────
private val TrkBg      = Color(0xFF070B14)
private val TrkCard    = Color(0xFF0E1520)
private val TrkCardAlt = Color(0xFF111A28)
private val TrkTeal    = Color(0xFF00D4AA)
private val TrkAmber   = Color(0xFFFFB830)
private val TrkBlue    = Color(0xFF4F9EFF)
private val TrkBorder  = Color(0xFF1E2D45)
private val TrkText    = Color(0xFFF0F4FF)
private val TrkSecond  = Color(0xFF8899BB)

data class DeliveryStep(val label: String, val sublabel: String, val icon: String, val status: String)


class DeliveryTrackingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId  = intent.getStringExtra("order_id") ?: ""
        val publicId = intent.getStringExtra("public_id") ?: ""
        val total    = intent.getDoubleExtra("total", 0.0)
        val summary  = intent.getStringExtra("summary") ?: ""

        setContent {
            Box(modifier = Modifier.fillMaxSize().background(TrkBg)) {
                DeliveryScreen(
                    orderId  = orderId,
                    publicId = publicId,
                    total    = total,
                    summary  = summary,
                    onClose  = { finish() }
                )
            }
        }
    }
}

@Composable
private fun DeliveryScreen(
    orderId: String, publicId: String,
    total: Double, summary: String,
    onClose: () -> Unit
) {
    // Status progression — in production use Supabase Realtime
    val statusOrder = listOf("placed", "confirmed", "packed", "out_for_delivery", "delivered")
    var currentStatus by remember { mutableStateOf("placed") }
    var eta           by remember { mutableStateOf(12) }

    // Demo: auto-progress through statuses
    LaunchedEffect(orderId) {
        delay(1500); currentStatus = "confirmed"
        delay(6000); currentStatus = "packed"
        delay(8000); currentStatus = "out_for_delivery"; eta = 5
        delay(12000); currentStatus = "delivered"; eta = 0
    }

    val currentStep = statusOrder.indexOf(currentStatus).coerceAtLeast(0)
    val isDelivered = currentStatus == "delivered"

    val inf = rememberInfiniteTransition(label = "trk")
    val pulse  by inf.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), label = "p")
    val glowA  by inf.animateFloat(0.05f, 0.18f, infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse), label = "g")

    val steps = listOf(
        DeliveryStep("Order Placed",      "We received your order",        "📋", "placed"),
        DeliveryStep("Confirmed",         "Store confirmed your order",    "✅", "confirmed"),
        DeliveryStep("Packed",            "Items packed & ready to go",   "📦", "packed"),
        DeliveryStep("Out for Delivery",  "Rider is on the way!",         "🛵", "out_for_delivery"),
        DeliveryStep("Delivered",         "Enjoy your fresh groceries!",  "🎉", "delivered")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(TrkTeal.copy(glowA), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.15f),
                    radius = size.width * 0.6f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            // Header row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Live Tracking", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TrkText)
                TextButton(onClick = onClose) { Text("Close ×", fontSize = 13.sp, color = TrkSecond) }
            }
            Spacer(Modifier.height(4.dp))

            // Order ID pill
            Box(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(TrkTeal.copy(0.15f))
                    .border(1.dp, TrkTeal.copy(0.4f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Text(publicId, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TrkTeal, letterSpacing = 1.sp)
            }

            Spacer(Modifier.height(20.dp))

            // ── ETA / Delivered banner ────────────────────────────────────────
            AnimatedContent(targetState = isDelivered, label = "eta_banner") { delivered ->
                Box(
                    modifier = Modifier.fillMaxWidth().scale(if (!delivered) pulse else 1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (delivered) Color(0xFF0A2010) else Color(0xFF0A1F10))
                        .border(1.5.dp, TrkTeal.copy(if (delivered) 0.6f else 0.4f), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(if (delivered) "🎉" else "⚡", fontSize = 28.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (delivered) "Delivered!" else "Arriving in $eta minutes",
                            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TrkTeal
                        )
                        Text(
                            if (delivered) "Enjoy your groceries! 🙏" else "Express dark store delivery",
                            fontSize = 12.sp, color = TrkSecond
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Progress Steps ────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TrkCard)
                    .border(1.dp, TrkBorder, RoundedCornerShape(16.dp)).padding(20.dp)
            ) {
                Column {
                    steps.forEachIndexed { i, step ->
                        val isDone    = i <= currentStep
                        val isCurrent = i == currentStep

                        Row(verticalAlignment = Alignment.Top) {
                            // Left side: icon circle + vertical connector line
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                                Box(
                                    modifier = Modifier.size(44.dp)
                                        .then(if (isCurrent) Modifier.scale(pulse) else Modifier)
                                        .background(
                                            when {
                                                isCurrent -> TrkTeal
                                                isDone    -> TrkTeal.copy(alpha = 0.25f)
                                                else      -> TrkBorder
                                            }, CircleShape
                                        )
                                        .border(if (isCurrent) 2.dp else 0.dp, TrkTeal, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(step.icon, fontSize = if (isCurrent) 18.sp else 16.sp)
                                }
                                if (i < steps.size - 1) {
                                    Box(
                                        modifier = Modifier.width(2.dp).height(36.dp)
                                            .background(
                                                if (isDone && i < currentStep) TrkTeal.copy(0.5f) else TrkBorder
                                            )
                                    )
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.padding(top = 10.dp).weight(1f)) {
                                Text(
                                    step.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else if (isDone) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isCurrent -> TrkText
                                        isDone    -> TrkText.copy(0.7f)
                                        else      -> TrkSecond
                                    }
                                )
                                if (isCurrent) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(step.sublabel, fontSize = 12.sp, color = TrkTeal)
                                } else if (isDone && i < currentStep) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("Done ✓", fontSize = 11.sp, color = TrkSecond)
                                }
                            }

                            // Timestamp for completed steps
                            if (isDone && i < currentStep) {
                                Text("✓", fontSize = 16.sp, color = TrkTeal, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        if (i < steps.size - 1) Spacer(Modifier.height(2.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Order Summary ─────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TrkCard)
                    .border(1.dp, TrkBorder, RoundedCornerShape(14.dp)).padding(16.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Your Order", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TrkText)
                        Text("₹%.0f".format(total), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TrkTeal)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(summary, fontSize = 12.sp, color = TrkSecond, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Divider(color = TrkBorder)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚴", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("Butler Express Delivery · FREE", fontSize = 11.sp, color = TrkTeal, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Butler tip ────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TrkCardAlt)
                    .border(1.dp, TrkBorder, RoundedCornerShape(12.dp)).padding(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("🎤", fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Want to order more?", fontSize = 12.sp, color = TrkSecond)
                    Text("\"Hey Butler\" — I'm always ready!", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TrkTeal)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}