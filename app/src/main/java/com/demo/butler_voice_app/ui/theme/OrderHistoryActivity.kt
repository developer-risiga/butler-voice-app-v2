package com.demo.butler_voice_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.demo.butler_voice_app.api.ApiClient
import com.demo.butler_voice_app.api.AuthManager
import kotlinx.coroutines.launch

class OrderHistoryActivity : ComponentActivity() {

    private val apiClient = ApiClient()
    private val orders = mutableStateOf<List<ApiClient.OrderHistory>>(emptyList())
    private val isLoading = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OrderHistoryScreen(
                orders    = orders.value,
                isLoading = isLoading.value,
                apiClient = apiClient
            )
        }

        lifecycleScope.launch {
            val userId = AuthManager.currentUserId() ?: return@launch
            orders.value  = apiClient.getOrderHistory(userId)
            isLoading.value = false
        }
    }
}

@Composable
fun OrderHistoryScreen(
    orders: List<ApiClient.OrderHistory>,
    isLoading: Boolean,
    apiClient: ApiClient
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
    ) {
        Text(
            text = "Order History",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1D9E75))
            }
            return
        }

        if (orders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No orders yet", color = Color(0xFF888780), fontSize = 16.sp)
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(orders) { order ->
                OrderCard(order = order, apiClient = apiClient)
            }
        }
    }
}

@Composable
fun OrderCard(order: ApiClient.OrderHistory, apiClient: ApiClient) {
    var expanded by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ApiClient.OrderItemHistory>>(emptyList()) }
    var loadingItems by remember { mutableStateOf(false) }

    val statusColor = when (order.order_status.lowercase()) {
        "placed"     -> Color(0xFF378ADD)
        "confirmed"  -> Color(0xFF1D9E75)
        "delivered"  -> Color(0xFF639922)
        "cancelled"  -> Color(0xFFE24B4A)
        else         -> Color(0xFF888780)
    }

    val shortId = order.id.takeLast(6).uppercase()
    val date = order.created_at?.take(10) ?: ""

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A1A),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                if (expanded && items.isEmpty()) {
                    loadingItems = true
                    // Items load is triggered from outside via LaunchedEffect
                }
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Order #$shortId",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = date,
                        fontSize = 12.sp,
                        color = Color(0xFF888780)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹%.2f".format(order.total_amount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D9E75)
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = order.order_status.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Expanded items
            if (expanded) {
                LaunchedEffect(order.id) {
                    if (items.isEmpty()) {
                        items = apiClient.getOrderItems(order.id)
                        loadingItems = false
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = Color(0xFF2A2A2A))
                Spacer(Modifier.height(10.dp))

                if (loadingItems) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF1D9E75),
                        strokeWidth = 2.dp
                    )
                } else {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.quantity}× ${item.product_name}",
                                fontSize = 13.sp,
                                color = Color(0xFFCCCCCC)
                            )
                            Text(
                                text = "₹%.2f".format(item.price * item.quantity),
                                fontSize = 13.sp,
                                color = Color(0xFF888780)
                            )
                        }
                    }
                }
            }
        }
    }
}
