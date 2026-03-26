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

// ── Colors ────────────────────────────────────────────────────────────────────
private val PayBgDeep      = Color(0xFF070B14)
private val PayBgCard      = Color(0xFF0E1520)
private val PayBgCardAlt   = Color(0xFF111A28)
private val PayAccentTeal  = Color(0xFF00D4AA)
private val PayAccentBlue  = Color(0xFF4F9EFF)
private val PayAccentAmber = Color(0xFFFFB830)
private val PayAccentRed   = Color(0xFFFF5C5C)
private val PayTextPrimary = Color(0xFFF0F4FF)
private val PayTextSecond  = Color(0xFF8899BB)
private val PayBorderColor = Color(0xFF1E2D45)

// NOTE: All constants (EXTRA_ORDER_TOTAL, EXTRA_ORDER_SUMMARY, RESULT_PAY_CARD etc.)
// are defined in Constants.kt — do NOT redeclare them here.

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
        val mode     = intent.getStringExtra(EXTRA_MODE) ?: MODE_CONFIRM
        val total    = intent.getDoubleExtra(EXTRA_ORDER_TOTAL, 0.0)
        val summary  = intent.getStringExtra(EXTRA_ORDER_SUMMARY) ?: ""
        val savedCard = PaymentManager.getSavedCard(this)
        val activity  = this

        setContent {
            Box(modifier = Modifier.fillMaxSize().background(PayBgDeep)) {
                when (mode) {
                    MODE_ADD_CARD -> AddCardScreen(
                        onSave = { last4, expiry, holder, network ->
                            PaymentManager.saveCard(activity, last4, expiry, holder, network)
                            activity.setResult(RESULT_CARD_SAVED)
                            activity.finish()
                        },
                        onCancel = { activity.finish() }
                    )
                    else -> PaymentConfirmScreen(
                        total    = total,
                        summary  = summary,
                        card     = savedCard,
                        onCard   = { activity.setResult(RESULT_PAY_CARD);   activity.finish() },
                        onQR     = { activity.setResult(RESULT_PAY_QR);     activity.finish() },
                        onCancel = { activity.setResult(RESULT_PAY_CANCEL); activity.finish() },
                        onChangeCard = {
                            activity.startActivity(
                                Intent(activity, PaymentActivity::class.java)
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
    total: Double, summary: String, card: PaymentManager.SavedCard?,
    onCard: () -> Unit, onQR: () -> Unit, onCancel: () -> Unit, onChangeCard: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "confirm")
    val pulse by inf.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Text("Confirm Payment", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PayTextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("₹%.0f".format(total), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = PayAccentTeal)
        Spacer(Modifier.height(4.dp))
        Text(summary, fontSize = 13.sp, color = PayTextSecond, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        if (card != null) {
            // Visual card
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp).scale(pulse)
                    .clip(RoundedCornerShape(20.dp)).background(
                        brush = Brush.linearGradient(
                            colors = when (card.network) {
                                "VISA"       -> listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
                                "MASTERCARD" -> listOf(Color(0xFF3E0000), Color(0xFFB71C1C))
                                "RUPAY"      -> listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                                else         -> listOf(Color(0xFF1A1F35), Color(0xFF0D1526))
                            }
                        )
                    ).padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Butler Pay", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(0.9f))
                        Text(card.network, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))
                    }
                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.size(40.dp, 30.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFD700).copy(0.8f)))
                    Spacer(Modifier.height(12.dp))
                    Text("**** **** **** ${card.last4}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Column {
                            Text("CARD HOLDER", fontSize = 9.sp, color = Color.White.copy(0.5f))
                            Text(card.cardHolder.uppercase(), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("EXPIRES", fontSize = 9.sp, color = Color.White.copy(0.5f))
                            Text(card.expiry, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onChangeCard) { Text("Change card", fontSize = 13.sp, color = PayAccentBlue) }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onCard,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PayAccentTeal)
            ) {
                Text("Pay ₹%.0f with Card".format(total), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PayBgCard)
                    .border(1.dp, PayBorderColor, RoundedCornerShape(16.dp)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💳", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No card saved", fontSize = 15.sp, color = PayTextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Add a card for faster checkout", fontSize = 13.sp, color = PayTextSecond)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onChangeCard,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PayAccentBlue)
            ) { Text("Add Card", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Spacer(Modifier.height(12.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Divider(modifier = Modifier.weight(1f), color = PayBorderColor)
            Text(" or pay with ", fontSize = 12.sp, color = PayTextSecond)
            Divider(modifier = Modifier.weight(1f), color = PayBorderColor)
        }
        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onQR,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, PayAccentAmber)
        ) { Text("📱 UPI / QR Code", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PayAccentAmber) }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0A1520)).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔒", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text("Your payment is encrypted and secure. Butler never stores your full card number.", fontSize = 11.sp, color = PayTextSecond, lineHeight = 16.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", fontSize = 14.sp, color = PayAccentRed)
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

    fun formatCard(input: String) = input.filter { it.isDigit() }.take(16).chunked(4).joinToString(" ")
    fun formatExpiry(input: String): String {
        val d = input.filter { it.isDigit() }.take(4)
        return if (d.length >= 3) "${d.take(2)}/${d.drop(2)}" else d
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Text("←", fontSize = 20.sp, color = PayTextPrimary) }
            Spacer(Modifier.width(8.dp))
            Text("Add Payment Card", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PayTextPrimary)
        }
        Spacer(Modifier.height(28.dp))

        // Live card preview
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(18.dp)).background(
                brush = Brush.linearGradient(
                    colors = when (network) {
                        "VISA"       -> listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
                        "MASTERCARD" -> listOf(Color(0xFF3E0000), Color(0xFFB71C1C))
                        "RUPAY"      -> listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                        else         -> listOf(Color(0xFF1A1F35), Color(0xFF0D1526))
                    }
                )
            ).padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Butler Pay", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.9f))
                    Text(if (network == "CARD") "" else network, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (cardNumber.isBlank()) "**** **** **** ****" else formatCard(cardNumber),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(holder.ifBlank { "CARD HOLDER" }.uppercase().take(20), fontSize = 12.sp, color = Color.White.copy(0.8f))
                    Text(if (expiry.isBlank()) "MM/YY" else formatExpiry(expiry), fontSize = 12.sp, color = Color.White.copy(0.8f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        PayInputField("Card Number", formatCard(cardNumber), "1234 5678 9012 3456", KeyboardType.Number) {
            cardNumber = it.filter { c -> c.isDigit() }.take(16)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                PayInputField("Expiry", formatExpiry(expiry), "MM/YY", KeyboardType.Number) {
                    expiry = it.filter { c -> c.isDigit() }.take(4)
                }
            }
            Box(Modifier.weight(1f)) {
                PayInputField("CVV", cvv, "•••", KeyboardType.Number, PasswordVisualTransformation()) {
                    cvv = it.filter { c -> c.isDigit() }.take(4)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PayInputField("Card Holder Name", holder, "As printed on card", KeyboardType.Text) {
            holder = it.take(30)
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, fontSize = 13.sp, color = PayAccentRed, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
            PaySecurityBadge("🔒 Encrypted")
            Spacer(Modifier.width(12.dp))
            PaySecurityBadge("🛡️ Secure")
            Spacer(Modifier.width(12.dp))
            PaySecurityBadge("✓ PCI DSS")
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
                    else -> { error = ""; onSave(clean.takeLast(4), formatExpiry(expiry), holder.trim(), network) }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PayAccentTeal)
        ) { Text("Save Card Securely", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black) }

        Spacer(Modifier.height(12.dp))
        Text(
            "Your card details are encrypted with AES-256 and stored only on this device. Butler never uploads your card number to any server.",
            fontSize = 11.sp, color = PayTextSecond, textAlign = TextAlign.Center, lineHeight = 16.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PayInputField(
    label: String, value: String, placeholder: String, keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, fontSize = 12.sp, color = PayTextSecond, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            textStyle = TextStyle(fontSize = 16.sp, color = PayTextPrimary, fontWeight = FontWeight.Medium),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(PayBgCard).border(1.dp, PayBorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = PayTextSecond.copy(0.5f))
                    inner()
                }
            }
        )
    }
}

@Composable
private fun PaySecurityBadge(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF0A1520))
            .border(1.dp, PayBorderColor, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 10.sp, color = PayTextSecond) }
}