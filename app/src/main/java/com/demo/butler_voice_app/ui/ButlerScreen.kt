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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.demo.butler_voice_app.api.ProductRecommendation

// ══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ══════════════════════════════════════════════════════════════════════════════

data class CartDisplayItem(
    val name: String,
    val quantity: Int,
    val price: Double
)

// ══════════════════════════════════════════════════════════════════════════════
// BUTLER UI STATE — every screen Butler can show
// ══════════════════════════════════════════════════════════════════════════════

sealed class ButlerUiState {

    // Idle: waiting for "Hey Butler"
    object Idle : ButlerUiState()

    // Auth choice screen (shown when no session)
    object AuthChoice : ButlerUiState()

    // Voice signup steps
    data class VoiceSignupStep(
        val step: SignupStep,
        val collectedName: String  = "",
        val collectedEmail: String = "",
        val collectedPhone: String = "",
        val prompt: String         = ""
    ) : ButlerUiState()

    enum class SignupStep {
        ASK_NEW_OR_RETURNING,
        ASK_NAME,
        ASK_EMAIL,
        ASK_PHONE,
        ASK_PASSWORD,
        CREATING_ACCOUNT,
        LOGGING_IN
    }

    // Thinking / processing
    data class Thinking(val transcript: String = "") : ButlerUiState()

    // Butler is speaking
    data class Speaking(
        val text: String,
        val cart: List<CartDisplayItem> = emptyList()
    ) : ButlerUiState()

    // Listening for voice input
    object Listening : ButlerUiState()

    // Product recommendations
    data class ShowingRecommendations(
        val query: String,
        val recommendations: List<ProductRecommendation>
    ) : ButlerUiState()

    // Active cart with items
    data class CartReview(
        val items: List<CartDisplayItem>,
        val totalAmount: Double,
        val prompt: String = "Shall I place the order?"
    ) : ButlerUiState()

    // Payment mode selection (fully voice driven)
    data class PaymentChoice(
        val totalAmount: Double,
        val orderSummary: String,
        val hasSavedCard: Boolean      = false,
        val savedCardInfo: String      = "",
        val listeningForChoice: Boolean = true
    ) : ButlerUiState()

    // Waiting for user to say "payment done"
    data class WaitingPaymentConfirm(
        val mode: String,        // "card" | "upi" | "qr"
        val totalAmount: Double
    ) : ButlerUiState()

    // QR code display
    data class ShowQRCode(
        val totalAmount: Double,
        val orderSummary: String
    ) : ButlerUiState()

    // Order placed successfully
    data class OrderPlaced(
        val orderId: String,
        val totalAmount: Double,
        val items: List<CartDisplayItem>,
        val estimatedMinutes: Int = 30,
        val userName: String      = ""
    ) : ButlerUiState()

    // Legacy alias — keeps old code that uses OrderDone compiling
    data class OrderDone(
        val orderId: String,
        val totalAmount: Double,
        val status: String    = "placed",
        val userName: String  = ""
    ) : ButlerUiState()

    // Error state
    data class Error(val message: String) : ButlerUiState()
}



// ══════════════════════════════════════════════════════════════════════════════
// DESIGN TOKENS
// ══════════════════════════════════════════════════════════════════════════════

private val BgDeep      = Color(0xFF070B14)
private val BgCard      = Color(0xFF0E1520)
private val BgCardAlt   = Color(0xFF111A28)
private val AccentTeal  = Color(0xFF00D4AA)
private val AccentBlue  = Color(0xFF4F9EFF)
private val AccentGold  = Color(0xFFFFB830)
private val AccentRed   = Color(0xFFFF4444)
private val AccentGreen = Color(0xFF00E676)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecond  = Color(0xFF8899BB)
private val BorderColor = Color(0xFF1E2D45)

// ══════════════════════════════════════════════════════════════════════════════
// ROOT COMPOSABLE
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ButlerScreen(state: ButlerUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "butler_screen"
        ) { s ->
            when (s) {
                is ButlerUiState.Idle                  -> IdleScreen()
                is ButlerUiState.AuthChoice            -> AuthChoiceScreen()
                is ButlerUiState.VoiceSignupStep       -> VoiceSignupScreen(s)
                is ButlerUiState.Thinking              -> ThinkingScreen(s.transcript)
                is ButlerUiState.Speaking              -> SpeakingScreen(s.text, s.cart)
                is ButlerUiState.Listening             -> ListeningScreen()
                is ButlerUiState.ShowingRecommendations -> RecommendationsScreen(s.query, s.recommendations)
                is ButlerUiState.CartReview            -> CartReviewScreen(s.items, s.totalAmount, s.prompt)
                is ButlerUiState.PaymentChoice         -> PaymentChoiceScreen(s)
                is ButlerUiState.WaitingPaymentConfirm -> WaitingPaymentScreen(s)
                is ButlerUiState.ShowQRCode            -> QRCodeScreen(s.totalAmount, s.orderSummary)
                is ButlerUiState.OrderPlaced           -> OrderPlacedScreen(s)
                is ButlerUiState.OrderDone             -> OrderDoneScreen(s)
                is ButlerUiState.Error                 -> ErrorScreen(s.message)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// IDLE SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun IdleScreen() {
    val inf    = rememberInfiniteTransition(label = "idle")
    val pulse  by inf.animateFloat(0.85f, 1.0f, infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse), label = "pulse")
    val glow   by inf.animateFloat(0.04f, 0.12f, infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse), label = "glow")

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush  = Brush.radialGradient(listOf(AccentTeal.copy(glow), Color.Transparent), center = center, radius = size.width * 0.6f)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size((96 * pulse).dp)
                    .background(Brush.radialGradient(listOf(AccentTeal.copy(0.9f), Color(0xFF003D2E))), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✦", fontSize = (38 * pulse).sp, color = Color.White) }

            Spacer(Modifier.height(24.dp))
            Text("Butler", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Say  \"Hey Butler\"  to start", fontSize = 15.sp, color = TextSecond, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(40.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val dotPulse by rememberInfiniteTransition(label = "dot$i").animateFloat(
                        0.4f, 1f,
                        infiniteRepeatable(tween(600, delayMillis = i * 200, easing = EaseInOutSine), RepeatMode.Reverse),
                        label = "dp$i"
                    )
                    Box(Modifier.size(8.dp).alpha(dotPulse).background(AccentTeal, CircleShape))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// AUTH CHOICE SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AuthChoiceScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(72.dp)
                    .background(Brush.radialGradient(listOf(AccentTeal.copy(0.9f), Color(0xFF003D2E))), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✦", fontSize = 28.sp, color = Color.White) }

            Text("Welcome to Butler", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text("How would you like to sign in?", fontSize = 14.sp, color = TextSecond)
            Spacer(Modifier.height(8.dp))

            AuthOptionCard("🎤", "Voice Signup",      "Just speak — Butler guides you", AccentTeal)
            AuthOptionCard("G",  "Google Sign-In",    "Quick and secure",               Color(0xFF4285F4))
            AuthOptionCard("✉️", "Email & Password",  "Manual sign in",                 AccentBlue)
        }
    }
}

@Composable
private fun AuthOptionCard(icon: String, title: String, subtitle: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title,    fontSize = 16.sp, fontWeight = FontWeight.Bold,    color = TextPrimary)
                Text(subtitle, fontSize = 12.sp,                                  color = TextSecond)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// VOICE SIGNUP SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceSignupScreen(state: ButlerUiState.VoiceSignupStep) {
    val inf      = rememberInfiniteTransition(label = "signup")
    val micPulse by inf.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "mic")

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(brush = Brush.radialGradient(listOf(AccentBlue.copy(0.06f), Color.Transparent), center = center, radius = size.width * 0.7f))
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            SignupStepIndicator(state.step)
            Spacer(Modifier.height(40.dp))

            Box(
                Modifier.size((80 * micPulse).dp)
                    .background(AccentBlue.copy(0.15f), CircleShape)
                    .border(2.dp, AccentBlue.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("🎤", fontSize = 32.sp) }

            Spacer(Modifier.height(28.dp))
            Text(
                state.prompt.ifBlank { stepPrompt(state.step) },
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                textAlign = TextAlign.Center, lineHeight = 28.sp
            )
            Spacer(Modifier.height(32.dp))

            if (state.collectedName.isNotBlank())  InfoChip("👤 Name",  state.collectedName)
            if (state.collectedEmail.isNotBlank()) InfoChip("✉️ Email", state.collectedEmail)
            if (state.collectedPhone.isNotBlank()) InfoChip("📱 Phone", state.collectedPhone)

            Spacer(Modifier.height(32.dp))
            Text("Speak clearly — Butler is listening", fontSize = 12.sp, color = TextSecond, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SignupStepIndicator(step: ButlerUiState.SignupStep) {
    val steps = listOf(
        ButlerUiState.SignupStep.ASK_NAME,
        ButlerUiState.SignupStep.ASK_EMAIL,
        ButlerUiState.SignupStep.ASK_PHONE,
        ButlerUiState.SignupStep.ASK_PASSWORD
    )
    val currentIdx = steps.indexOf(step).coerceAtLeast(0)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { idx, _ ->
            Box(
                Modifier.size(if (idx == currentIdx) 12.dp else 8.dp)
                    .background(if (idx <= currentIdx) AccentTeal else BorderColor, CircleShape)
            )
        }
    }
}

private fun stepPrompt(step: ButlerUiState.SignupStep) = when (step) {
    ButlerUiState.SignupStep.ASK_NEW_OR_RETURNING -> "Are you a new customer\nor have you ordered before?"
    ButlerUiState.SignupStep.ASK_NAME             -> "What is your full name?"
    ButlerUiState.SignupStep.ASK_EMAIL            -> "Spell your email address\nletter by letter"
    ButlerUiState.SignupStep.ASK_PHONE            -> "What is your 10-digit\nmobile number?"
    ButlerUiState.SignupStep.ASK_PASSWORD         -> "Choose a password —\nspell each character clearly"
    ButlerUiState.SignupStep.CREATING_ACCOUNT     -> "Creating your account…"
    ButlerUiState.SignupStep.LOGGING_IN           -> "Logging you in…"
}

@Composable
private fun InfoChip(label: String, value: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AccentTeal.copy(0.1f))
            .border(1.dp, AccentTeal.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row {
            Text(label, fontSize = 12.sp, color = AccentTeal, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// THINKING SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThinkingScreen(transcript: String) {
    val inf = rememberInfiniteTransition(label = "think")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "rot")

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).rotate(rot)
                    .background(AccentTeal.copy(0.15f), CircleShape)
                    .border(2.dp, AccentTeal.copy(0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✦", fontSize = 28.sp, color = AccentTeal) }

            Spacer(Modifier.height(24.dp))
            Text("Thinking…", fontSize = 18.sp, color = TextSecond)

            if (transcript.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .padding(16.dp)
                ) {
                    Text(
                        "\"$transcript\"",
                        fontSize = 14.sp, color = TextSecond,
                        textAlign = TextAlign.Center,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// LISTENING SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ListeningScreen() {
    val inf = rememberInfiniteTransition(label = "listen")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(brush = Brush.radialGradient(listOf(AccentBlue.copy(0.08f), Color.Transparent), center = center, radius = size.width * 0.5f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(20, 35, 55, 40, 60, 45, 25, 50, 35, 20).forEachIndexed { i, baseH ->
                    val h by inf.animateFloat(
                        baseH * 0.4f, baseH.toFloat(),
                        infiniteRepeatable(tween(400 + i * 80, easing = EaseInOutSine), RepeatMode.Reverse),
                        label = "wave$i"
                    )
                    Box(Modifier.width(4.dp).height(h.dp).clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(AccentBlue, AccentTeal))))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Listening…", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Speak clearly", fontSize = 14.sp, color = TextSecond)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SPEAKING SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SpeakingScreen(text: String, cart: List<CartDisplayItem>) {
    val inf  = rememberInfiniteTransition(label = "speak")
    val glow by inf.animateFloat(0.05f, 0.15f, infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), label = "glow")

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp))
        Box(
            Modifier.size(80.dp).background(AccentTeal.copy(glow * 2), CircleShape).border(2.dp, AccentTeal.copy(0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(12, 22, 32, 22, 12).forEachIndexed { i, baseH ->
                    val h by inf.animateFloat(
                        baseH * 0.5f, baseH.toFloat(),
                        infiniteRepeatable(tween(300 + i * 100, easing = EaseInOutSine), RepeatMode.Reverse),
                        label = "sb$i"
                    )
                    Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Butler says", fontSize = 13.sp, color = TextSecond)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BgCard)
                .border(1.dp, AccentTeal.copy(0.2f), RoundedCornerShape(20.dp)).padding(20.dp)
        ) {
            Text(text, fontSize = 18.sp, color = TextPrimary, textAlign = TextAlign.Center, lineHeight = 26.sp, modifier = Modifier.fillMaxWidth())
        }

        if (cart.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Your Cart", fontSize = 13.sp, color = AccentTeal, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            cart.forEach { CartItemRow(it); Spacer(Modifier.height(6.dp)) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("₹%.0f".format(cart.sumOf { it.price * it.quantity }), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = AccentGold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RECOMMENDATIONS SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun RecommendationsScreen(query: String, recs: List<ProductRecommendation>) {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(40.dp))
        Text("Found for \"$query\"", fontSize = 13.sp, color = TextSecond)
        Spacer(Modifier.height(4.dp))
        Text("${recs.size} Options", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(Modifier.height(20.dp))
        recs.forEachIndexed { idx, rec ->
            RecommendationCard(idx + 1, rec)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(AccentBlue.copy(0.1f)).border(1.dp, AccentBlue.copy(0.3f), RoundedCornerShape(14.dp)).padding(16.dp),
            contentAlignment = Alignment.Center
        ) { Text("🎤  Say 1, 2 or 3 to select", fontSize = 15.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun RecommendationCard(number: Int, rec: ProductRecommendation) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(AccentTeal.copy(0.15f), CircleShape).border(1.dp, AccentTeal.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$number", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AccentTeal) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.productName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 2)
                Text(rec.storeName,   fontSize = 12.sp, color = TextSecond)
            }
            Spacer(Modifier.width(12.dp))
            Text(rec.priceLabel, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AccentGold)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CART REVIEW SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CartReviewScreen(items: List<CartDisplayItem>, total: Double, prompt: String) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("Your Cart", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text("${items.size} item${if (items.size > 1) "s" else ""}", fontSize = 14.sp, color = TextSecond)
        Spacer(Modifier.height(20.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            items.forEach { CartItemRow(it); Spacer(Modifier.height(10.dp)) }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)
                .border(1.dp, AccentGold.copy(0.3f), RoundedCornerShape(16.dp)).padding(20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Amount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("₹%.0f".format(total), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = AccentGold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(AccentTeal.copy(0.1f)).border(1.dp, AccentTeal.copy(0.3f), RoundedCornerShape(14.dp)).padding(16.dp),
            contentAlignment = Alignment.Center
        ) { Text("🎤  $prompt", fontSize = 14.sp, color = AccentTeal, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Shared cart item row ───────────────────────────────────────────────────────
@Composable
private fun CartItemRow(item: CartDisplayItem) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCard)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(AccentGold.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Text("🛒", fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 2)
                Text("Qty: ${item.quantity}", fontSize = 11.sp, color = TextSecond)
            }
            Text("₹%.0f".format(item.price * item.quantity), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentGold)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PAYMENT CHOICE SCREEN (fully voice driven)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PaymentChoiceScreen(state: ButlerUiState.PaymentChoice) {
    val inf      = rememberInfiniteTransition(label = "pay")
    val micPulse by inf.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "mp")

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Payment", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text("Total: ₹%.0f".format(state.totalAmount), fontSize = 20.sp, color = AccentGold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)
                .border(1.dp, AccentTeal.copy(0.3f), RoundedCornerShape(16.dp)).padding(20.dp)
        ) {
            Text("How would you like to pay?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(20.dp))

        if (state.hasSavedCard)
            PaymentOptionCard("💳", "Saved Card", state.savedCardInfo,  "Say  \"card\"",  AccentBlue)
        else
            PaymentOptionCard("💳", "New Card",   "Add a new card",     "Say  \"card\"",  AccentBlue)

        Spacer(Modifier.height(10.dp))
        PaymentOptionCard("📱", "UPI",      "GPay / PhonePe / Paytm", "Say  \"UPI\"",  AccentTeal)
        Spacer(Modifier.height(10.dp))
        PaymentOptionCard("📷", "QR Code",  "Scan to pay",            "Say  \"QR\"",   AccentGold)

        Spacer(Modifier.height(24.dp))

        if (state.listeningForChoice) {
            Box(
                Modifier.size((64 * micPulse).dp).background(AccentTeal.copy(0.15f), CircleShape).border(2.dp, AccentTeal.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("🎤", fontSize = 24.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Listening for your choice…", fontSize = 13.sp, color = TextSecond)
        }
    }
}

@Composable
private fun PaymentOptionCard(icon: String, title: String, subtitle: String, voiceHint: String, color: Color) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard)
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title,    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(subtitle, fontSize = 12.sp,                               color = TextSecond)
            }
            Text(voiceHint, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// WAITING PAYMENT CONFIRM SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun WaitingPaymentScreen(state: ButlerUiState.WaitingPaymentConfirm) {
    val inf   = rememberInfiniteTransition(label = "waitpay")
    val pulse by inf.animateFloat(0.85f, 1.0f, infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), label = "wp")

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(when (state.mode) { "card" -> "💳"; "upi" -> "📱"; "qr" -> "📷"; else -> "💰" }, fontSize = (56 * pulse).sp)
            Spacer(Modifier.height(24.dp))
            Text(
                when (state.mode) { "card" -> "Complete the card payment"; "upi" -> "Complete the UPI payment"; "qr" -> "Scan the QR code and pay"; else -> "Complete your payment" },
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text("₹%.0f".format(state.totalAmount), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = AccentGold)
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(AccentGreen.copy(0.1f)).border(1.dp, AccentGreen.copy(0.3f), RoundedCornerShape(16.dp)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎤", fontSize = 24.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "After paying, say:\n\"Payment done\"  or  \"I paid\"",
                        fontSize = 15.sp, color = AccentGreen, textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold, lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// QR CODE SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun QRCodeScreen(totalAmount: Double, orderSummary: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("Scan to Pay", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text("₹%.0f".format(totalAmount), fontSize = 20.sp, color = AccentGold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // QR placeholder
        Box(Modifier.size(220.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp), contentAlignment = Alignment.Center) {
            Column {
                repeat(8) { row ->
                    Row {
                        repeat(8) { col ->
                            Box(Modifier.size(22.dp).padding(1.dp).background(if ((row + col) % 3 != 0) Color.Black else Color.White))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(orderSummary, fontSize = 13.sp, color = TextSecond, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(AccentGreen.copy(0.1f)).border(1.dp, AccentGreen.copy(0.3f), RoundedCornerShape(14.dp)).padding(16.dp),
            contentAlignment = Alignment.Center
        ) { Text("🎤  After paying, say: \"Payment done\"", fontSize = 14.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold) }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ORDER PLACED SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun OrderPlacedScreen(state: ButlerUiState.OrderPlaced) {
    val inf   = rememberInfiniteTransition(label = "order")
    val scale by inf.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), label = "os")

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size((88 * scale).dp).background(AccentGreen.copy(0.15f), CircleShape).border(2.dp, AccentGreen.copy(0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("✓", fontSize = 40.sp, color = AccentGreen, fontWeight = FontWeight.ExtraBold) }

        Spacer(Modifier.height(20.dp))
        Text("Order Placed!", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = AccentGreen)
        if (state.userName.isNotBlank()) Text("Thank you, ${state.userName}!", fontSize = 16.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)
                .border(1.dp, AccentGold.copy(0.3f), RoundedCornerShape(16.dp)).padding(20.dp)
        ) {
            Column {
                OrderDetailRow("Order ID",  state.orderId)
                Spacer(Modifier.height(10.dp))
                OrderDetailRow("Total Paid", "₹%.0f".format(state.totalAmount))
                Spacer(Modifier.height(10.dp))
                OrderDetailRow("Delivery",   "~${state.estimatedMinutes} minutes")
                Spacer(Modifier.height(10.dp))
                OrderDetailRow("Status",     "✅ Confirmed")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Items Ordered", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        state.items.forEach { CartItemRow(it); Spacer(Modifier.height(8.dp)) }

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(AccentTeal.copy(0.08f)).border(1.dp, AccentTeal.copy(0.2f), RoundedCornerShape(14.dp)).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚀", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Out for delivery soon!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                    Text("Estimated: ${state.estimatedMinutes} minutes", fontSize = 12.sp, color = TextSecond)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// Legacy OrderDone screen
@Composable
private fun OrderDoneScreen(state: ButlerUiState.OrderDone) {
    OrderPlacedScreen(
        ButlerUiState.OrderPlaced(
            orderId    = state.orderId,
            totalAmount = state.totalAmount,
            items      = emptyList(),
            userName   = state.userName
        )
    )
}

@Composable
private fun OrderDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextSecond)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ERROR SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorScreen(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("Something went wrong", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 14.sp, color = TextSecond, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text("Say \"Hey Butler\" to restart", fontSize = 14.sp, color = AccentTeal)
        }
    }
}