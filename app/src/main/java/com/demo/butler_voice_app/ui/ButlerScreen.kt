package com.demo.butler_voice_app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── CART DISPLAY MODEL ───────────────────────────────────────
data class CartDisplayItem(
    val name  : String,
    val qty   : Int,
    val price : Double
)

// ─── UI STATES ────────────────────────────────────────────────
sealed class ButlerUiState {
    object Idle      : ButlerUiState()
    object Listening : ButlerUiState()
    data class Thinking(val heard: String) : ButlerUiState()
    data class Speaking(
        val text       : String,
        val isFallback : Boolean               = false,
        val cart       : List<CartDisplayItem> = emptyList()
    ) : ButlerUiState()
    data class OrderDone(
        val shortId     : String,
        val total       : Double,
        val orderStatus : String = "placed"
    ) : ButlerUiState()
    data class Error(val message: String) : ButlerUiState()
}

// ─── COLORS ───────────────────────────────────────────────────
private val BgColor     = Color(0xFF080C10)
private val SurfColor   = Color(0xFF111820)
private val CardColor   = Color(0xFF0D1520)
private val OrbIdle     = Color(0xFF2A3540)
private val OrbListen   = Color(0xFF00E5A0)
private val OrbThink    = Color(0xFF9C7AFF)
private val OrbSpeak    = Color(0xFF3EAAFF)
private val OrbSuccess  = Color(0xFF00E5A0)
private val OrbError    = Color(0xFFFF4D6A)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextMuted   = Color(0xFF556070)
private val TextSub     = Color(0xFF8A9AAA)
private val AccentGreen = Color(0xFF00E5A0)

// ─── MAIN SCREEN ──────────────────────────────────────────────
@Composable
fun ButlerScreen(state: ButlerUiState) {
    val orbColor = when (state) {
        is ButlerUiState.Idle      -> OrbIdle
        is ButlerUiState.Listening -> OrbListen
        is ButlerUiState.Thinking  -> OrbThink
        is ButlerUiState.Speaking  -> OrbSpeak
        is ButlerUiState.OrderDone -> OrbSuccess
        is ButlerUiState.Error     -> OrbError
    }

    Box(
        modifier          = Modifier.fillMaxSize().background(BgColor),
        contentAlignment  = Alignment.Center
    ) {
        // Ambient glow
        Box(
            modifier = Modifier.size(320.dp).background(
                brush = Brush.radialGradient(
                    colors = listOf(orbColor.copy(alpha = 0.07f), Color.Transparent)
                ),
                shape = CircleShape
            )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.fillMaxSize().padding(horizontal = 28.dp)
        ) {
            Text(
                text          = "BUTLER",
                fontSize      = 11.sp,
                fontWeight    = FontWeight.W600,
                color         = TextMuted,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(40.dp))
            PulsingOrb(state = state, orbColor = orbColor)
            Spacer(modifier = Modifier.height(36.dp))

            val headline: String = when (state) {
                is ButlerUiState.Idle      -> "Say \u201cHey Butler\u201d"
                is ButlerUiState.Listening -> "Listening..."
                is ButlerUiState.Thinking  -> "Thinking..."
                is ButlerUiState.Speaking  -> ""
                is ButlerUiState.OrderDone -> "Order Placed!"
                is ButlerUiState.Error     -> "Something went wrong"
            }

            if (headline.isNotBlank()) {
                Text(text = headline, fontSize = 24.sp, fontWeight = FontWeight.W500,
                    color = TextPrimary, textAlign = TextAlign.Center)
            }

            if (state is ButlerUiState.Thinking && state.heard.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "\u201c${state.heard}\u201d", fontSize = 14.sp,
                    color = TextSub, textAlign = TextAlign.Center, lineHeight = 20.sp)
            }

            if (state is ButlerUiState.Speaking) {
                if (state.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SpeakingBubble(text = state.text)
                }
                AnimatedVisibility(
                    visible = state.cart.isNotEmpty(),
                    enter   = fadeIn() + slideInVertically { it / 2 },
                    exit    = fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        LiveCartCard(items = state.cart)
                    }
                }
            }

            if (state is ButlerUiState.OrderDone) {
                Spacer(modifier = Modifier.height(28.dp))
                OrderSuccessCard(shortId = state.shortId, total = state.total, orderStatus = state.orderStatus)
            }

            if (state is ButlerUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = state.message, fontSize = 14.sp, color = OrbError, textAlign = TextAlign.Center)
            }

            if (state is ButlerUiState.Listening) {
                Spacer(modifier = Modifier.height(28.dp))
                AudioWaveform()
            }
        }
    }
}

// ─── LIVE CART ────────────────────────────────────────────────
@Composable
fun LiveCartCard(items: List<CartDisplayItem>) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardColor, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(text = "CART", fontSize = 10.sp, fontWeight = FontWeight.W600,
                    color = TextMuted, letterSpacing = 2.sp)
                Text(text = "${items.size} item${if (items.size > 1) "s" else ""}",
                    fontSize = 11.sp, color = TextMuted)
            }
            Spacer(modifier = Modifier.height(10.dp))
            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = AccentGreen.copy(alpha = 0.15f)) {
                            Text(text = "${item.qty}x", fontSize = 11.sp, color = AccentGreen,
                                fontWeight = FontWeight.W600,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = item.name, fontSize = 14.sp, color = TextPrimary,
                            modifier = Modifier.weight(1f))
                    }
                    Text(text = "\u20B9%.0f".format(item.price * item.qty),
                        fontSize = 14.sp, color = TextSub, fontWeight = FontWeight.W500)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                color = Color(0xFF1A2530), thickness = 1.dp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "\u20B9%.0f".format(items.sumOf { it.price * it.qty }),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
            }
        }
    }
}

// ─── PULSING ORB ──────────────────────────────────────────────
@Composable
fun PulsingOrb(state: ButlerUiState, orbColor: Color) {
    val pulseTarget = when (state) {
        is ButlerUiState.Listening -> 1.35f
        is ButlerUiState.Speaking  -> 1.18f
        is ButlerUiState.Thinking  -> 1.10f
        else                       -> 1.03f
    }
    val durationMs = when (state) {
        is ButlerUiState.Listening -> 550
        is ButlerUiState.Speaking  -> 750
        is ButlerUiState.Thinking  -> 900
        else                       -> 2000
    }
    val infinite = rememberInfiniteTransition(label = "orb")
    val scale by infinite.animateFloat(
        initialValue  = 1f, targetValue = pulseTarget,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(160.dp).scale(scale * 0.95f)
            .background(orbColor.copy(alpha = 0.05f), CircleShape))
        Box(modifier = Modifier.size(120.dp).scale(scale * 0.9f)
            .background(orbColor.copy(alpha = 0.12f), CircleShape))
        Box(modifier = Modifier.size(88.dp).scale(scale * 0.85f)
            .background(orbColor.copy(alpha = 0.22f), CircleShape))
        Box(modifier = Modifier.size(64.dp).background(
            brush = Brush.radialGradient(colors = listOf(
                orbColor.copy(alpha = 0.95f), orbColor.copy(alpha = 0.70f))),
            shape = CircleShape))
    }
}

// ─── AUDIO WAVEFORM ───────────────────────────────────────────
@Composable
fun AudioWaveform() {
    val infinite = rememberInfiniteTransition(label = "wave")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(7) { i ->
            val height by infinite.animateFloat(
                initialValue  = 6f, targetValue = 30f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(300 + (i * 80), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "bar$i"
            )
            Box(modifier = Modifier.width(4.dp).height(height.dp)
                .background(OrbListen.copy(alpha = 0.7f), RoundedCornerShape(2.dp)))
        }
    }
}

// ─── SPEAKING BUBBLE ──────────────────────────────────────────
@Composable
fun SpeakingBubble(text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfColor, modifier = Modifier.fillMaxWidth()) {
        Text(text = text, fontSize = 16.sp, color = TextPrimary, textAlign = TextAlign.Center,
            lineHeight = 24.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
    }
}

// ─── ORDER SUCCESS CARD ───────────────────────────────────────
@Composable
fun OrderSuccessCard(shortId: String, total: Double, orderStatus: String = "placed") {
    val statusColor = when (orderStatus.lowercase()) {
        "placed"     -> Color(0xFF378ADD)
        "confirmed"  -> Color(0xFF1D9E75)
        "preparing"  -> Color(0xFFFFB347)
        "out_for_delivery" -> Color(0xFF9C7AFF)
        "delivered"  -> Color(0xFF00E5A0)
        "cancelled"  -> Color(0xFFFF4D6A)
        else         -> Color(0xFF8A9AAA)
    }
    val statusLabel = when (orderStatus.lowercase()) {
        "placed"           -> "ORDER PLACED"
        "confirmed"        -> "CONFIRMED \u2713"
        "preparing"        -> "PREPARING..."
        "out_for_delivery" -> "ON THE WAY"
        "delivered"        -> "DELIVERED \u2713"
        "cancelled"        -> "CANCELLED"
        else               -> orderStatus.uppercase()
    }
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF061A12),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\u2713  Order confirmed", fontSize = 13.sp, fontWeight = FontWeight.W500, color = Color(0xFF4DDFB0))
            Spacer(modifier = Modifier.height(8.dp))
            Text("#$shortId", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = OrbSuccess)
            Spacer(modifier = Modifier.height(4.dp))
            Text("\u20B9%.2f".format(total), fontSize = 18.sp, color = Color(0xFF8FDDBE))
            Spacer(modifier = Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.18f)) {
                Text(text = statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
            }
        }
    }
}