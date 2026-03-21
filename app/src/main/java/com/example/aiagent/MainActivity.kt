package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : ComponentActivity() {

    private val client = OkHttpClient()
    private val BINANCE_API_URL = "https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var paxgPrice by remember { mutableStateOf("Loading...") }
            var isLoading by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            val coroutineScope = rememberCoroutineScope()

            suspend fun fetchPrice() {
                isLoading = true
                errorMessage = null
                try {
                    val price = withContext(Dispatchers.IO) {
                        val request = Request.Builder()
                            .url(BINANCE_API_URL)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw IOException("Unexpected code ${response}")
                            }

                            val responseBody = response.body?.string()
                            val json = responseBody?.let { JSONObject(it) }
                            json?.getString("price")?.let { priceString ->
                                String.format("$%,.2f", priceString.toFloat())
                            } ?: run {
                                throw IOException("Price not found in response")
                            }
                        }
                    }
                    paxgPrice = price
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error fetching PAXG price", e)
                    errorMessage = "Failed to load price. Error: ${e.message}"
                    paxgPrice = "Error"
                } finally {
                    isLoading = false
                }
            }

            LaunchedEffect(Unit) {
                fetchPrice()
                while (true) {
                    delay(60 * 1000L)
                    fetchPrice()
                }
            }

            val deepBlue = Color(0xFF0D47A1)
            val goldColor = Color(0xFFFFD700)

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = deepBlue
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading && errorMessage == null) {
                        CircularProgressIndicator(color = goldColor)
                        Spacer(Modifier.height(32.dp))
                    } else if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 32.dp)
                        )
                    } else {
                        Text(
                            text = paxgPrice,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = goldColor,
                            modifier = Modifier.padding(bottom = 64.dp)
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                fetchPrice()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(56.dp),
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = goldColor,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = if (isLoading) "Refreshing..." else "Update Now",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                        )
                    }
                }
            }
        }
    }
}