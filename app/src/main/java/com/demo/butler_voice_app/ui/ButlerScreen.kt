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
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlin.math.*
import com.demo.butler_voice_app.api.ProductRecommendation

// ── Data classes ──────────────────────────────────────────────────────────────
data class CartDisplayItem(val name: String, val quantity: Int, val price: Double)

// ── UI State ──────────────────────────────────────────────────────────────────
sealed class ButlerUiState {
    object Idle : ButlerUiState()
    object Listening : ButlerUiState()
    data class Thinking(val transcript: String = "") : ButlerUiState()
    data class Speaking(val text: String, val cart: List<CartDisplayItem> = emptyList()) : ButlerUiState()
    data class ShowingRecommendations(val query: String, val recs: List<ProductRecommendation>) : ButlerUiState()
    data class OrderDone(val orderId: String, val total: Double, val status: String, val userName: String = "") : ButlerUiState()
}

// ── Color palette ─────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF070B14)
private val BgCard      = Color(0xFF0E1520)
private val BgCardAlt   = Color(0xFF111A28)
private val AccentTeal  = Color(0xFF00D4AA)
private val AccentBlue  = Color(0xFF4F9EFF)
private val AccentAmber = Color(0xFFFFB830)
private val AccentRed   = Color(0xFFFF5C5C)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecond  = Color(0xFF8899BB)
private val BorderColor = Color(0xFF1E2D45)

// ── Main Screen ───────────────────────────────────────────────────────────────
@Composable
fun ButlerScreen(state: ButlerUiState) {
    Box(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentAlignment = Alignment.Center
    ) {
        AmbientGlow(state)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                is ButlerUiState.Idle                   -> IdleView()
                is ButlerUiState.Listening              -> ListeningView()
                is ButlerUiState.Thinking               -> ThinkingView(state.transcript)
                is ButlerUiState.Speaking               -> SpeakingView(state.text, state.cart)
                is ButlerUiState.ShowingRecommendations -> RecommendationsView(state.query, state.recs)
                is ButlerUiState.OrderDone              -> OrderDoneView(state.orderId, state.total, state.userName)
            }
        }
    }
}

// ── Ambient Glow ──────────────────────────────────────────────────────────────
@Composable
private fun AmbientGlow(state: ButlerUiState) {
    val glowColor = when (state) {
        is ButlerUiState.Idle            -> AccentTeal.copy(alpha = 0.04f)
        is ButlerUiState.Listening       -> AccentBlue.copy(alpha = 0.08f)
        is ButlerUiState.Thinking        -> AccentAmber.copy(alpha = 0.06f)
        is ButlerUiState.Speaking        -> AccentTeal.copy(alpha = 0.07f)
        is ButlerUiState.ShowingRecommendations -> AccentBlue.copy(alpha = 0.05f)
        is ButlerUiState.OrderDone       -> AccentTeal.copy(alpha = 0.12f)
    }
    val animColor by animateColorAsState(glowColor, animationSpec = tween(800))
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(animColor, Color.Transparent),
                center = Offset(size.width / 2f, size.height * 0.35f),
                radius = size.width * 0.8f
            )
        )
    }
}

// ── IDLE ──────────────────────────────────────────────────────────────────────
@Composable
private fun IdleView() {
    val pulse = rememberInfiniteTransition(label = "idle")
    val scale by pulse.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    val alpha by pulse.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "alpha"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(140.dp).scale(scale).background(
                brush = Brush.radialGradient(listOf(AccentTeal.copy(alpha = alpha * 0.3f), Color.Transparent)),
                shape = CircleShape
            ))
            Box(
                modifier = Modifier.size(88.dp).background(
                    brush = Brush.radialGradient(listOf(AccentTeal.copy(alpha = 0.9f), Color(0xFF005F4B))),
                    shape = CircleShape
                ),
                contentAlignment = Alignment.Center
            ) { Text("✦", fontSize = 28.sp, color = Color.White) }
        }
        Spacer(Modifier.height(28.dp))
        Text("Hey Butler", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(8.dp))
        Text("Waiting for wake word...", fontSize = 14.sp, color = TextSecond)
    }
}

// ── LISTENING ─────────────────────────────────────────────────────────────────
@Composable
private fun ListeningView() {
    val inf = rememberInfiniteTransition(label = "listen")
    val bars = (0..4).map { i ->
        inf.animateFloat(
            initialValue = 0.2f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(400 + i * 80, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "bar$i"
        )
    }
    val ring by inf.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ring"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(130.dp).scale(ring).border(2.dp, AccentBlue.copy(alpha = 1f - ring + 0.1f), CircleShape))
            Box(
                modifier = Modifier.size(90.dp).background(
                    brush = Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.85f), Color(0xFF1A3A6E))),
                    shape = CircleShape
                ),
                contentAlignment = Alignment.Center
            ) { Text("🎤", fontSize = 28.sp) }
        }
        Spacer(Modifier.height(24.dp))
        Text("Listening...", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Speak now", fontSize = 13.sp, color = TextSecond)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            bars.forEach { bar ->
                val h by bar
                Box(modifier = Modifier.width(6.dp).height((8.dp + (h * 36).dp)).clip(RoundedCornerShape(3.dp)).background(
                    brush = Brush.verticalGradient(listOf(AccentBlue, AccentBlue.copy(alpha = 0.3f)))
                ))
            }
        }
    }
}

// ── THINKING ──────────────────────────────────────────────────────────────────
@Composable
private fun ThinkingView(transcript: String) {
    val inf = rememberInfiniteTransition(label = "think")
    val rotation by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "spin"
    )
    val dot1 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), label = "d1")
    val dot2 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = 200, easing = EaseInOutSine), RepeatMode.Reverse), label = "d2")
    val dot3 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = 400, easing = EaseInOutSine), RepeatMode.Reverse), label = "d3")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(100.dp)) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(AccentAmber, AccentAmber.copy(alpha = 0f))),
                    startAngle = rotation, sweepAngle = 270f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Box(
                modifier = Modifier.size(80.dp).background(
                    brush = Brush.radialGradient(listOf(AccentAmber.copy(alpha = 0.7f), Color(0xFF3D2800))),
                    shape = CircleShape
                ),
                contentAlignment = Alignment.Center
            ) { Text("⚡", fontSize = 26.sp) }
        }
        Spacer(Modifier.height(20.dp))
        if (transcript.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp)).background(BgCard).border(1.dp, BorderColor, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("\"$transcript\"", fontSize = 14.sp, color = TextSecond, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
        }
        Text("Processing", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(dot1, dot2, dot3).forEach { a -> Box(modifier = Modifier.size(8.dp).alpha(a).background(AccentAmber, CircleShape)) }
        }
    }
}

// ── SPEAKING ──────────────────────────────────────────────────────────────────
@Composable
private fun SpeakingView(text: String, cart: List<CartDisplayItem>) {
    val inf = rememberInfiniteTransition(label = "speak")
    val waveBars = (0..6).map { i ->
        inf.animateFloat(0.15f, 1f, infiniteRepeatable(tween(300 + i * 60, easing = EaseInOutSine), RepeatMode.Reverse), label = "wave$i")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            listOf(130.dp, 110.dp).forEachIndexed { i, size ->
                val ringAlpha by inf.animateFloat(0.1f, 0.4f, infiniteRepeatable(tween(800 + i * 200, easing = EaseInOutSine), RepeatMode.Reverse), label = "ringa$i")
                Box(modifier = Modifier.size(size).border(1.5.dp, AccentTeal.copy(alpha = ringAlpha), CircleShape))
            }
            Box(
                modifier = Modifier.size(88.dp).background(
                    brush = Brush.radialGradient(listOf(AccentTeal.copy(alpha = 0.9f), Color(0xFF004A37))),
                    shape = CircleShape
                ),
                contentAlignment = Alignment.Center
            ) { Text("🔊", fontSize = 26.sp) }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            waveBars.forEach { bar ->
                val h by bar
                Box(modifier = Modifier.width(5.dp).height((6.dp + (h * 30).dp)).clip(RoundedCornerShape(3.dp)).background(
                    brush = Brush.verticalGradient(listOf(AccentTeal, AccentTeal.copy(alpha = 0.2f)))
                ))
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).border(1.dp, AccentTeal.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(16.dp)) {
            Text(text, fontSize = 15.sp, color = TextPrimary, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (cart.isNotEmpty()) { Spacer(Modifier.height(16.dp)); CartOverlay(cart) }
    }
}

// ── CART OVERLAY ──────────────────────────────────────────────────────────────
@Composable
private fun CartOverlay(cart: List<CartDisplayItem>) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCardAlt).border(1.dp, AccentAmber.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).padding(14.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🛒  Cart", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                Text("${cart.size} item${if (cart.size > 1) "s" else ""}", fontSize = 12.sp, color = TextSecond)
            }
            Spacer(Modifier.height(8.dp))
            cart.take(3).forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("× ${item.quantity}  ${item.name.take(24)}${if (item.name.length > 24) "…" else ""}", fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text("₹%.0f".format(item.price * item.quantity), fontSize = 12.sp, color = AccentAmber, fontWeight = FontWeight.SemiBold)
                }
            }
            if (cart.size > 3) Text("+${cart.size - 3} more", fontSize = 11.sp, color = TextSecond)
            Divider(color = BorderColor, modifier = Modifier.padding(vertical = 6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("₹%.0f".format(cart.sumOf { it.price * it.quantity }), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
            }
        }
    }
}

// ── RECOMMENDATIONS ───────────────────────────────────────────────────────────
@Composable
private fun RecommendationsView(query: String, recs: List<ProductRecommendation>) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(8.dp).background(AccentBlue, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Top picks for \"$query\"", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text("Say 1, 2 or 3 to add", fontSize = 12.sp, color = TextSecond)
        Spacer(Modifier.height(16.dp))
        recs.take(3).forEachIndexed { index, rec ->
            val isBest = index == 0
            Box(modifier = Modifier.padding(bottom = 10.dp)) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (isBest) Color(0xFF0A1F18) else BgCard).border(if (isBest) 1.5.dp else 1.dp, if (isBest) AccentTeal else BorderColor, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(26.dp).background(if (isBest) AccentTeal else BorderColor, CircleShape), contentAlignment = Alignment.Center) {
                                        Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isBest) Color.Black else TextSecond)
                                    }
                                    if (isBest) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AccentTeal.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("BEST VALUE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = AccentTeal, letterSpacing = 0.8.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(rec.productName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(rec.storeName, fontSize = 12.sp, color = TextSecond)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("₹%.0f".format(rec.priceRs), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (isBest) AccentTeal else AccentBlue)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📍", fontSize = 10.sp)
                                    Spacer(Modifier.width(2.dp))
                                    Text(rec.distanceLabel, fontSize = 11.sp, color = TextSecond)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── ORDER DONE — Warm & Celebratory ──────────────────────────────────────────
@Composable
private fun OrderDoneView(orderId: String, total: Double, userName: String) {
    val inf = rememberInfiniteTransition(label = "done")
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val checkScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale"
    )
    val pulse by inf.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    // Sparkle stars animation
    val sparkle by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "sparkle"
    )

    val firstName = if (userName.isNotBlank()) userName else "Friend"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))

        // Animated success orb with sparkles
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
            // Sparkle particles
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height / 2
                for (i in 0..7) {
                    val angle = (i * 45f + sparkle * 360f) * PI.toFloat() / 180f
                    val radius = size.width * 0.45f
                    val px = cx + radius * cos(angle)
                    val py = cy + radius * sin(angle)
                    drawCircle(
                        color = AccentTeal.copy(alpha = 0.6f * ((i % 3 + 1) / 3f)),
                        radius = 4.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }
            Box(modifier = Modifier.size(100.dp).scale(checkScale * pulse), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxSize().background(
                    brush = Brush.radialGradient(listOf(AccentTeal.copy(alpha = 0.25f), Color.Transparent)),
                    shape = CircleShape
                ))
                Box(
                    modifier = Modifier.size(80.dp).background(
                        brush = Brush.radialGradient(listOf(AccentTeal, Color(0xFF005F4B))),
                        shape = CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) { Text("✓", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Order Placed! 🎉", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))

        // Warm personal greeting
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(
                brush = Brush.linearGradient(listOf(Color(0xFF0A1F18), Color(0xFF071420)))
            ).border(1.dp, AccentTeal.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🙏", fontSize = 24.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Thank you, $firstName!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentTeal
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your order is confirmed and on its way. Butler is always here whenever you need groceries — just say \"Hey Butler\" anytime!",
                    fontSize = 13.sp,
                    color = TextSecond,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Order ID pill
        Box(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(AccentTeal.copy(alpha = 0.12f)).border(1.dp, AccentTeal.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(orderId, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentTeal, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(16.dp))

        // Receipt card
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(20.dp)) {
            Column {
                ReceiptRow("Subtotal", "₹%.0f".format(total))
                ReceiptRow("Delivery", "FREE ⚡")
                ReceiptRow("Platform fee", "₹0")
                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL PAID", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("₹%.0f".format(total), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AccentTeal)
                }
                Spacer(Modifier.height(14.dp))
                // Delivery estimate
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentAmber.copy(alpha = 0.08f)).border(1.dp, AccentAmber.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Express Delivery", fontSize = 12.sp, color = AccentAmber, fontWeight = FontWeight.Bold)
                            Text("Arriving in 10–15 minutes", fontSize = 11.sp, color = TextSecond)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Call to action
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCardAlt).padding(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Want to order more?", fontSize = 13.sp, color = TextSecond)
                Spacer(Modifier.height(4.dp))
                Text(
                    "\"Hey Butler\" — I'm always listening!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentTeal
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextSecond)
        Text(value, fontSize = 13.sp, color = if (value.startsWith("FREE") || value == "₹0") AccentTeal else TextPrimary, fontWeight = if (value.startsWith("FREE")) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Extensions ────────────────────────────────────────────────────────────────
val ProductRecommendation.priceLabel: String get() = "₹%.0f".format(priceRs)
val ProductRecommendation.distanceLabel: String get() =
    if (distanceKm < 1.0) "${(distanceKm * 1000).toInt()} m" else "%.1f km".format(distanceKm)