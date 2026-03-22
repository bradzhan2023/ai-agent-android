package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1B2A)) {
                    GoldPriceDashboard()
                }
            }
        }
    }
}

@Composable
fun GoldPriceDashboard() {
    var price by remember { mutableStateOf("載入中...") }
    val client = OkHttpClient()

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT")
                        .build()
                    client.newCall(request).execute().body?.string()
                }
                result?.let {
                    val json = JSONObject(it)
                    price = "$" + String.format("%.2f", json.getString("price").toDouble())
                }
            } catch (e: Exception) {
                price = "連線失敗"
            }
            delay(30000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PAXG 即時金價", color = Color.White, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = price, color = Color.Yellow, fontSize = 48.sp)
    }
}
