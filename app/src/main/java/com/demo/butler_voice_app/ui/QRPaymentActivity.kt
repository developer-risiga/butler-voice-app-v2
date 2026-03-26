package com.demo.butler_voice_app.ui

import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

// ── Colors ────────────────────────────────────────────────────────────────────
private val QrBgDeep      = androidx.compose.ui.graphics.Color(0xFF070B14)
private val QrBgCard      = androidx.compose.ui.graphics.Color(0xFF0E1520)
private val QrAccentTeal  = androidx.compose.ui.graphics.Color(0xFF00D4AA)
private val QrAccentBlue  = androidx.compose.ui.graphics.Color(0xFF4F9EFF)
private val QrAccentAmber = androidx.compose.ui.graphics.Color(0xFFFFB830)
private val QrTextPrimary = androidx.compose.ui.graphics.Color(0xFFF0F4FF)
private val QrTextSecond  = androidx.compose.ui.graphics.Color(0xFF8899BB)
private val QrBorderColor = androidx.compose.ui.graphics.Color(0xFF1E2D45)

// NOTE: RESULT_QR_PAID, RESULT_QR_CANCEL, EXTRA_ORDER_TOTAL, EXTRA_ORDER_SUMMARY
// are all defined in Constants.kt — do NOT redeclare them here.

/**
 * QRPaymentActivity — shows a real UPI QR code.
 * Replace MERCHANT_UPI_ID with your actual UPI merchant ID for production.
 */
class QRPaymentActivity : ComponentActivity() {

    companion object {
        private const val MERCHANT_UPI_ID = "butler@paytm"  // Replace with real UPI ID
        private const val MERCHANT_NAME   = "Butler Groceries"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val total   = intent.getDoubleExtra(EXTRA_ORDER_TOTAL, 0.0)
        val summary = intent.getStringExtra(EXTRA_ORDER_SUMMARY) ?: ""
        val activity = this

        setContent {
            Box(modifier = Modifier.fillMaxSize().background(QrBgDeep)) {
                QRScreen(
                    total    = total,
                    summary  = summary,
                    onPaid   = { activity.setResult(RESULT_QR_PAID);   activity.finish() },
                    onCancel = { activity.setResult(RESULT_QR_CANCEL); activity.finish() }
                )
            }
        }
    }

    private fun generateUpiQR(amount: Double): Bitmap? {
        return try {
            val upiUrl = "upi://pay?pa=$MERCHANT_UPI_ID&pn=${MERCHANT_NAME.replace(" ", "%20")}&am=%.2f&cu=INR&tn=Butler%20Grocery%20Order".format(amount)
            val hints  = mapOf(EncodeHintType.MARGIN to 1)
            val bits   = QRCodeWriter().encode(upiUrl, BarcodeFormat.QR_CODE, 512, 512, hints)
            val bmp    = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
            for (x in 0 until 512) for (y in 0 until 512) {
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
            bmp
        } catch (e: Exception) { null }
    }

    @Composable
    private fun QRScreen(total: Double, summary: String, onPaid: () -> Unit, onCancel: () -> Unit) {
        val qrBitmap = remember { generateUpiQR(total) }
        val inf = rememberInfiniteTransition(label = "qr")
        val pulse by inf.animateFloat(
            initialValue = 0.95f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "pulse"
        )
        val scanLine by inf.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
            label = "scan"
        )
        var countdown by remember { mutableStateOf(300) }
        LaunchedEffect(Unit) {
            while (countdown > 0) { kotlinx.coroutines.delay(1000); countdown-- }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Scan & Pay", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = QrTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("₹%.0f".format(total), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = QrAccentTeal)
            Spacer(Modifier.height(4.dp))
            Text(summary, fontSize = 12.sp, color = QrTextSecond, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            // UPI logos row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("GPay", "PhonePe", "Paytm", "BHIM").forEach { app ->
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(QrBgCard)
                            .border(1.dp, QrBorderColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text(app, fontSize = 11.sp, color = QrTextSecond, fontWeight = FontWeight.SemiBold) }
                }
            }

            Spacer(Modifier.height(20.dp))

            // QR Code with scan animation
            Box(
                modifier = Modifier.size(280.dp).scale(pulse * 0.97f + 0.03f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .border(3.dp, QrAccentTeal, RoundedCornerShape(20.dp)).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "UPI QR Code", modifier = Modifier.fillMaxSize())
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = QrAccentTeal.copy(alpha = 0.7f),
                            start = androidx.compose.ui.geometry.Offset(0f, size.height * scanLine),
                            end   = androidx.compose.ui.geometry.Offset(size.width, size.height * scanLine),
                            strokeWidth = 3f
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📱", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("QR Loading...", fontSize = 14.sp, color = QrTextSecond)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val mins = countdown / 60; val secs = countdown % 60
            Text("Expires in %02d:%02d".format(mins, secs), fontSize = 13.sp,
                color = if (countdown < 60) QrAccentAmber else QrTextSecond)
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(QrBgCard)
                    .border(1.dp, QrBorderColor, RoundedCornerShape(12.dp)).padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Or pay to UPI ID", fontSize = 12.sp, color = QrTextSecond)
                    Spacer(Modifier.height(4.dp))
                    Text(MERCHANT_UPI_ID, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = QrAccentBlue, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("🔒", fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text("100% secure · Powered by NPCI UPI", fontSize = 11.sp, color = QrTextSecond)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onPaid,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QrAccentTeal)
            ) { Text("I've Paid ✓", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = androidx.compose.ui.graphics.Color.Black) }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, QrBorderColor)
            ) { Text("Cancel", fontSize = 14.sp, color = QrTextSecond) }
            Spacer(Modifier.height(24.dp))
        }
    }
}