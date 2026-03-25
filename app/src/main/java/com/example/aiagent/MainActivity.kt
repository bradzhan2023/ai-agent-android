package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject

import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.component.shape.shader.verticalGradient
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.entry.diff.MutableLineEntryModelProducer
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.component.shape.shader.BrushShader
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShader
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class to hold relevant Kline data for our chart
data class KlineData(
    val closeTime: Long,
    val closePrice: Double
)

// Helper function to parse Binance kline data from raw JSON string
fun parseBinanceKlineData(jsonString: String): List<KlineData> {
    val klines = mutableListOf<KlineData>()
    try {
        val jsonArray: JsonArray = JsonParser.parseString(jsonString).asJsonArray
        jsonArray.forEach { element ->
            val innerArray = element.asJsonArray
            // Binance kline array structure:
            // [0] Open time
            // [1] Open price
            // [2] High price
            // [3] Low price
            // [4] Close price
            // [5] Volume
            // [6] Close time
            // ... and more.
            if (innerArray.size() > 6) { // Ensure required indices exist
                val closeTime = innerArray[6].asLong
                val closePrice = innerArray[4].asString.toDouble()
                klines.add(KlineData(closeTime, closePrice))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return klines
}

class MainActivity : ComponentActivity() {

    private val client = OkHttpClient()
    private val klineModelProducer = MutableLineEntryModelProducer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // Using MaterialTheme as required
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BinancePriceTrackerScreen(klineModelProducer, client)
                }
            }
        }

        // Fetch historical kline data when the activity is created
        fetchBinanceKlineData()
    }

    private fun fetchBinanceKlineData() {
        // Fetch 24 hours of 1-hour interval data for PAXGUSDT
        val url = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                // Handle network error, e.g., show a toast or update an error state
                lifecycleScope.launch(Dispatchers.Main) {
                    // Could update a UI state here to show an error message
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonString ->
                    val klines = parseBinanceKlineData(jsonString)
                    val entries = klines.mapIndexed { index, kline ->
                        // Using index as the X-axis value for default chart labels as per requirement
                        entryOf(index.toFloat(), kline.closePrice.toFloat())
                    }
                    // Update the chart data on the main thread
                    lifecycleScope.launch(Dispatchers.Main) {
                        klineModelProducer.setEntries(listOf(entries))
                    }
                }
            }
        })
    }
}

@Composable
fun BinancePriceTrackerScreen(
    klineModelProducer: MutableLineEntryModelProducer,
    client: OkHttpClient // OkHttpClient instance for fetching current price
) {
    val currentPriceState = remember { mutableStateOf("Loading...") }
    val lastUpdateTimeState = remember { mutableStateOf("") }
    val chartScrollState = rememberChartScrollState()

    // Fetch current price when the screen is first composed
    LaunchedEffect(Unit) {
        fetchCurrentPrice(client, currentPriceState, lastUpdateTimeState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "PAXGUSDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Current Price: ${currentPriceState.value}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Last Updated: ${lastUpdateTimeState.value}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "24-Hour Price Trend (Hourly)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Chart area for the 24-hour trend
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                .padding(8.dp)
        ) {
            Chart(
                chart = lineChart(
                    lines = listOf(
                        lineSpec(
                            lineColor = MaterialTheme.colorScheme.primary,
                            lineBackgroundShader = DynamicShader(
                                BrushShader(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                modelProducer = klineModelProducer,
                // Using default axis implementations, which use default labels as required
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(),
                chartScrollState = chartScrollState,
                isZoomEnabled = false, // Disable zoom for simplicity
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Function to fetch the most recent price for display
private fun fetchCurrentPrice(
    client: OkHttpClient,
    currentPriceState: MutableState<String>,
    lastUpdateTimeState: MutableState<String>
) {
    // Use the ticker price API for the very latest price
    val url = "https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT"
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
            currentPriceState.value = "Error fetching price"
            lastUpdateTimeState.value = "N/A"
        }

        override fun onResponse(call: Call, response: Response) {
            response.body?.string()?.let { jsonString ->
                try {
                    val jsonObject: JsonObject = JsonParser.parseString(jsonString).asJsonObject
                    val price = jsonObject["price"].asString
                    val timestamp = System.currentTimeMillis()
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val formattedTime = sdf.format(Date(timestamp))

                    currentPriceState.value = "$price USDT"
                    lastUpdateTimeState.value = formattedTime
                } catch (e: Exception) {
                    e.printStackTrace()
                    currentPriceState.value = "Error parsing price"
                    lastUpdateTimeState.value = "N/A"
                }
            }
        }
    })
}