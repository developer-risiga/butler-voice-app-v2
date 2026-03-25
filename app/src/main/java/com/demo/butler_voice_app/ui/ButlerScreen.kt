package com.demo.butler_voice_app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── STATES ───────────────────────────────────────────────────
sealed class ButlerUiState {
    object Idle                                               : ButlerUiState()
    object Listening                                          : ButlerUiState()
    data class Thinking(val heard: String)                    : ButlerUiState()
    data class Speaking(val text: String, val isFallback: Boolean = false) : ButlerUiState()
    data class OrderDone(val shortId: String, val total: Double) : ButlerUiState()
    data class Error(val message: String)                     : ButlerUiState()
}

// ─── COLORS ───────────────────────────────────────────────────
private object Colors {
    val Bg          = Color(0xFF080C10)
    val Surface     = Color(0xFF111820)
    val OrbIdle     = Color(0xFF2A3540)
    val OrbListen   = Color(0xFF00E5A0)
    val OrbThink    = Color(0xFF9C7AFF)
    val OrbSpeak    = Color(0xFF3EAAFF)
    val OrbSuccess  = Color(0xFF00E5A0)
    val OrbError    = Color(0xFFFF4D6A)
    val TextPrimary = Color(0xFFF0F4F8)
    val TextMuted   = Color(0xFF556070)
    val TextSub     = Color(0xFF8A9AAA)
    val AccentGreen = Color(0xFF00E5A0)
    val AccentBlue  = Color(0xFF3EAAFF)
}

// ─── MAIN SCREEN ──────────────────────────────────────────────
@Composable
fun ButlerScreen(state: ButlerUiState) {

    val orbColor = when (state) {
        is ButlerUiState.Idle      -> Colors.OrbIdle
        is ButlerUiState.Listening -> Colors.OrbListen
        is ButlerUiState.Thinking  -> Colors.OrbThink
        is ButlerUiState.Speaking  -> Colors.OrbSpeak
        is ButlerUiState.OrderDone -> Colors.OrbSuccess
        is ButlerUiState.Error     -> Colors.OrbError
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.Bg),
        contentAlignment = Alignment.Center
    ) {

        // Background ambient glow
        Box(
            modifier = Modifier
                .size(400.dp)
                .blur(120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(orbColor.copy(alpha = 0.08f), Color.Transparent),
                        radius = 600f
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp)
        ) {

            // Brand label
            Text(
                text          = "BUTLER",
                fontSize      = 11.sp,
                fontWeight    = FontWeight.W600,
                color         = Colors.TextMuted,
                letterSpacing = 6.sp
            )

            Spacer(Modifier.height(56.dp))

            // Main orb
            PulsingOrb(state, orbColor)

            Spacer(Modifier.height(44.dp))

            // Status headline
            val headline = when (state) {
                is ButlerUiState.Idle      -> "Say  "Hey Butler""
                is ButlerUiState.Listening -> "Listening..."
                is ButlerUiState.Thinking  -> "Thinking..."
                is ButlerUiState.Speaking  -> ""
                is ButlerUiState.OrderDone -> "Order Placed!"
                is ButlerUiState.Error     -> "Something went wrong"
            }

            if (headline.isNotBlank()) {
                Text(
                    text       = headline,
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.W500,
                    color      = Colors.TextPrimary,
                    textAlign  = TextAlign.Center
                )
            }

            // Transcript (Thinking state)
            if (state is ButlerUiState.Thinking && state.heard.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text      = "\"${state.heard}\"",
                    fontSize  = 14.sp,
                    color     = Colors.TextSub,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            // Speaking text
            if (state is ButlerUiState.Speaking && state.text.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                SpeakingBubble(state.text)
            }

            // Order success
            if (state is ButlerUiState.OrderDone) {
                Spacer(Modifier.height(28.dp))
                OrderSuccessCard(state.shortId, state.total)
            }

            // Error
            if (state is ButlerUiState.Error) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text      = state.message,
                    fontSize  = 14.sp,
                    color     = Colors.OrbError,
                    textAlign = TextAlign.Center
                )
            }

            // Waveform bar (Listening state)
            if (state is ButlerUiState.Listening) {
                Spacer(Modifier.height(28.dp))
                AudioWaveform()
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
        else -> 1.03f
    }
    val durationMs = when (state) {
        is ButlerUiState.Listening -> 550
        is ButlerUiState.Speaking  -> 750
        is ButlerUiState.Thinking  -> 900
        else -> 2000
    }

    val infinite = rememberInfiniteTransition(label = "orb")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue  = pulseTarget,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outermost halo
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale * 0.95f)
                .background(orbColor.copy(alpha = 0.05f), CircleShape)
        )
        // Middle halo
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale * 0.9f)
                .background(orbColor.copy(alpha = 0.12f), CircleShape)
        )
        // Inner ring
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(scale * 0.85f)
                .background(orbColor.copy(alpha = 0.22f), CircleShape)
        )
        // Core
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbColor.copy(alpha = 0.95f),
                            orbColor.copy(alpha = 0.7f)
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}

// ─── AUDIO WAVEFORM BARS ──────────────────────────────────────
@Composable
fun AudioWaveform() {
    val infinite = rememberInfiniteTransition(label = "wave")
    val bars = 7

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { i ->
            val height by infinite.animateFloat(
                initialValue = 6f,
                targetValue  = 30f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(
                        durationMillis = 300 + (i * 80),
                        easing         = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .background(Colors.OrbListen.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
            )
        }
    }
}

// ─── SPEAKING BUBBLE ──────────────────────────────────────────
@Composable
fun SpeakingBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Colors.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text       = text,
            fontSize   = 16.sp,
            color      = Colors.TextPrimary,
            textAlign  = TextAlign.Center,
            lineHeight  = 24.sp,
            modifier   = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

// ─── ORDER SUCCESS CARD ───────────────────────────────────────
@Composable
fun OrderSuccessCard(shortId: String, total: Double) {
    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = Color(0xFF061A12),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = "✓  Order confirmed",
                fontSize   = 13.sp,
                fontWeight = FontWeight.W500,
                color      = Color(0xFF4DDFB0)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = "#$shortId",
                fontSize   = 36.sp,
                fontWeight = FontWeight.Bold,
                color      = Colors.AccentGreen
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = "₹%.2f".format(total),
                fontSize = 18.sp,
                color    = Color(0xFF8FDDBE)
            )
        }
    }
}
