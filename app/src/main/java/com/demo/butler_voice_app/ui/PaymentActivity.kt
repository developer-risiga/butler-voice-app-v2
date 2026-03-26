package com.demo.butler_voice_app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.demo.butler_voice_app.api.PaymentManager

// ── Colors (matching ButlerScreen) ───────────────────────────────────────────
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

// ── Intent extras ─────────────────────────────────────────────────────────────
const val EXTRA_ORDER_TOTAL  = "order_total"
const val EXTRA_ORDER_SUMMARY = "order_summary"
const val RESULT_PAY_CARD    = 100
const val RESULT_PAY_QR      = 101
const val RESULT_PAY_CANCEL  = 102
const val RESULT_CARD_SAVED  = 103

/**
 * PaymentActivity — two modes:
 *   MODE_CONFIRM  → show saved card + ask Card or QR
 *   MODE_ADD_CARD → full card entry form
 */
class PaymentActivity : ComponentActivity() {

    companion object {
        const val MODE_CONFIRM  = "confirm"
        const val MODE_ADD_CARD = "add_card"
        const val EXTRA_MODE    = "mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode        = intent.getStringExtra(EXTRA_MODE) ?: MODE_CONFIRM
        val total       = intent.getDoubleExtra(EXTRA_ORDER_TOTAL, 0.0)
        val summary     = intent.getStringExtra(EXTRA_ORDER_SUMMARY) ?: ""
        val savedCard   = PaymentManager.getSavedCard(this)

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDeep)
            ) {
                when (mode) {
                    MODE_ADD_CARD -> AddCardScreen(
                        onSave = { last4, expiry, holder, network ->
                            PaymentManager.saveCard(this@PaymentActivity, last4, expiry, holder, network)
                            setResult(RESULT_CARD_SAVED)
                            finish()
                        },
                        onCancel = { finish() }
                    )
                    else -> PaymentConfirmScreen(
                        total    = total,
                        summary  = summary,
                        card     = savedCard,
                        onCard   = { setResult(RESULT_PAY_CARD); finish() },
                        onQR     = { setResult(RESULT_PAY_QR); finish() },
                        onCancel = { setResult(RESULT_PAY_CANCEL); finish() },
                        onChangeCard = {
                            startActivity(
                                Intent(this@PaymentActivity, PaymentActivity::class.java)
                                    .putExtra(EXTRA_MODE, MODE_ADD_CARD)
                            )
                        }
                    )
                }
            }
        }
    }
}

// ── Payment Confirmation Screen ───────────────────────────────────────────────

@Composable
private fun PaymentConfirmScreen(
    total: Double,
    summary: String,
    card: PaymentManager.SavedCard?,
    onCard: () -> Unit,
    onQR: () -> Unit,
    onCancel: () -> Unit,
    onChangeCard: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "confirm")
    val pulse by inf.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Header
        Text("Confirm Payment", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("₹%.0f".format(total), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = AccentTeal)
        Spacer(Modifier.height(4.dp))
        Text(summary, fontSize = 13.sp, color = TextSecond, textAlign = TextAlign.Center)

        Spacer(Modifier.height(28.dp))

        // Saved card section
        if (card != null) {
            // Visual card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .scale(pulse)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = when (card.network) {
                                "VISA"       -> listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
                                "MASTERCARD" -> listOf(Color(0xFF3E0000), Color(0xFFB71C1C))
                                "RUPAY"      -> listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                                else         -> listOf(Color(0xFF1A1F35), Color(0xFF0D1526))
                            }
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Butler Pay",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            card.network,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Chip icon
                    Box(
                        modifier = Modifier
                            .size(40.dp, 30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.8f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "**** **** **** ${card.last4}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("CARD HOLDER", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                            Text(card.cardHolder.uppercase(), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("EXPIRES", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                            Text(card.expiry, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onChangeCard) {
                Text("Change card", fontSize = 13.sp, color = AccentBlue)
            }
            Spacer(Modifier.height(20.dp))

            // Pay with card button
            Button(
                onClick = onCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
            ) {
                Text(
                    "Pay ₹%.0f with Card".format(total),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(12.dp))
        } else {
            // No card saved
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💳", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No card saved", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Add a card for faster checkout", fontSize = 13.sp, color = TextSecond)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onChangeCard,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Add Card", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
        }

        // Divider with OR
        Row(verticalAlignment = Alignment.CenterVertically) {
            Divider(modifier = Modifier.weight(1f), color = BorderColor)
            Text("  or pay with  ", fontSize = 12.sp, color = TextSecond)
            Divider(modifier = Modifier.weight(1f), color = BorderColor)
        }

        Spacer(Modifier.height(12.dp))

        // UPI / QR button
        OutlinedButton(
            onClick = onQR,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, AccentAmber)
        ) {
            Text("📱  UPI / QR Code", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
        }

        Spacer(Modifier.height(16.dp))

        // Security note
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A1520))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔒", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Your payment is encrypted and secure. Butler never stores your full card number.",
                    fontSize = 11.sp,
                    color = TextSecond,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", fontSize = 14.sp, color = AccentRed)
        }
    }
}

// ── Add Card Screen ───────────────────────────────────────────────────────────

@Composable
private fun AddCardScreen(
    onSave: (last4: String, expiry: String, holder: String, network: String) -> Unit,
    onCancel: () -> Unit
) {
    var cardNumber by remember { mutableStateOf("") }
    var expiry     by remember { mutableStateOf("") }
    var cvv        by remember { mutableStateOf("") }
    var holder     by remember { mutableStateOf("") }
    var error      by remember { mutableStateOf("") }

    val network = PaymentManager.detectNetwork(cardNumber)

    fun formatCardNumber(input: String): String {
        val digits = input.filter { it.isDigit() }.take(16)
        return digits.chunked(4).joinToString(" ")
    }

    fun formatExpiry(input: String): String {
        val digits = input.filter { it.isDigit() }.take(4)
        return if (digits.length >= 3) "${digits.take(2)}/${digits.drop(2)}"
        else digits
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Text("←", fontSize = 20.sp, color = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text("Add Payment Card", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        }

        Spacer(Modifier.height(28.dp))

        // Live card preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = when (network) {
                            "VISA"       -> listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
                            "MASTERCARD" -> listOf(Color(0xFF3E0000), Color(0xFFB71C1C))
                            "RUPAY"      -> listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                            else         -> listOf(Color(0xFF1A1F35), Color(0xFF0D1526))
                        }
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Butler Pay", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.9f))
                    Text(if (network == "CARD") "" else network, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (cardNumber.isBlank()) "**** **** **** ****"
                    else formatCardNumber(cardNumber).padEnd(19, '*').take(19),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(holder.ifBlank { "CARD HOLDER" }.uppercase().take(20), fontSize = 12.sp, color = Color.White.copy(0.8f))
                    Text(if (expiry.isBlank()) "MM/YY" else expiry, fontSize = 12.sp, color = Color.White.copy(0.8f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Card number field
        PaymentField(
            label = "Card Number",
            value = formatCardNumber(cardNumber),
            placeholder = "1234 5678 9012 3456",
            keyboardType = KeyboardType.Number,
            onValueChange = { cardNumber = it.filter { c -> c.isDigit() }.take(16) }
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                PaymentField(
                    label = "Expiry",
                    value = formatExpiry(expiry),
                    placeholder = "MM/YY",
                    keyboardType = KeyboardType.Number,
                    onValueChange = { expiry = it.filter { c -> c.isDigit() }.take(4) }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PaymentField(
                    label = "CVV",
                    value = cvv,
                    placeholder = "•••",
                    keyboardType = KeyboardType.Number,
                    visualTransformation = PasswordVisualTransformation(),
                    onValueChange = { cvv = it.filter { c -> c.isDigit() }.take(4) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        PaymentField(
            label = "Card Holder Name",
            value = holder,
            placeholder = "As printed on card",
            keyboardType = KeyboardType.Text,
            onValueChange = { holder = it.take(30) }
        )

        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, fontSize = 13.sp, color = AccentRed, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))

        // Security badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecurityBadge("🔒 Encrypted")
            Spacer(Modifier.width(12.dp))
            SecurityBadge("🛡️ Secure")
            Spacer(Modifier.width(12.dp))
            SecurityBadge("✓ PCI DSS")
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val clean = cardNumber.replace(" ", "")
                when {
                    clean.length < 16    -> error = "Enter a valid 16-digit card number"
                    expiry.length < 4    -> error = "Enter valid expiry (MM/YY)"
                    cvv.length < 3       -> error = "Enter valid CVV"
                    holder.isBlank()     -> error = "Enter card holder name"
                    else -> {
                        error = ""
                        onSave(clean.takeLast(4), formatExpiry(expiry), holder.trim(), network)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
        ) {
            Text("Save Card Securely", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Your card details are encrypted with AES-256 and stored only on this device. Butler never uploads your card number to any server.",
            fontSize = 11.sp,
            color = TextSecond,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PaymentField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecond, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Medium),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 15.sp, color = TextSecond.copy(alpha = 0.5f))
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun SecurityBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A1520))
            .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 10.sp, color = TextSecond)
    }
}