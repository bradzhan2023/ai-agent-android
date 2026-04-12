package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aiagent.ui.theme.AiAgentTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

// Data model for a single price point
@Serializable
data class PriceData(
    val timestamp: Long, // Unix timestamp in milliseconds
    val price: Double
)

// Ktor HTTP client instance
private val httpClient = HttpClient(Android) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PriceTrackerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceTrackerScreen() {
    val coroutineScope = rememberCoroutineScope()
    val currentPrice = remember { mutableStateOf("N/A") }
    val historicalPrices = remember { mutableStateListOf<PriceData>() }
    val isLoading = remember { mutableStateOf(true) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    // Fetch data when the Composable enters the composition
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response: HttpResponse = httpClient.get("https://api.binance.com/api/v3/klines") {
                    parameter("symbol", "PAXGUSDT")
                    parameter("interval", "1h") // 1-hour interval
                    parameter("limit", 100) // Fetch last 100 data points
                }

                if (response.status.value == 200) {
                    val jsonString = response.bodyAsText()
                    val jsonArray = Json.parseToJsonElement(jsonString).jsonArray

                    val newPrices = mutableListOf<PriceData>()
                    jsonArray.forEach { kline ->
                        val klineArray = kline.jsonArray
                        val openTime = klineArray[0].jsonPrimitive.content.toLong()
                        val closePrice = klineArray[4].jsonPrimitive.content.toDouble()
                        newPrices.add(PriceData(openTime, closePrice))
                    }

                    if (newPrices.isNotEmpty()) {
                        currentPrice.value = String.format("%.2f USDT", newPrices.last().price)
                        historicalPrices.clear()
                        historicalPrices.addAll(newPrices)
                    } else {
                        errorMessage.value = "No price data received."
                    }
                } else {
                    errorMessage.value = "Error fetching data: ${response.status.description}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.localizedMessage ?: "Unknown error"}"
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT 金價追蹤") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (isLoading.value) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("載入中...", style = MaterialTheme.typography.bodyLarge)
            } else if (errorMessage.value != null) {
                Text(
                    "錯誤: ${errorMessage.value}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Text(
                    "當前 PAXGUSDT 價格:",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    currentPrice.value,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (historicalPrices.isNotEmpty()) {
                    Text(
                        "歷史價格走勢 (1小時K線)",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PriceLineChart(historicalPrices)
                } else {
                    Text(
                        "無歷史價格數據可顯示。",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PriceLineChart(prices: List<PriceData>) {
    val context = LocalContext.current

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    valueFormatter = DateAxisValueFormatter()
                    labelRotationAngle = -45f // Rotate labels for better readability
                    granularity = 3600000f // 1 hour in milliseconds
                }

                axisLeft.apply {
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    setDrawLabels(true)
                }

                axisRight.isEnabled = false // Disable right Y-axis

                legend.isEnabled = false // Disable legend

                animateX(1500) // Animation over 1.5 seconds
            }
        },
        update = { chart ->
            val entries = prices.mapIndexed { index, priceData ->
                Entry(priceData.timestamp.toFloat(), priceData.price.toFloat())
            }

            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 9f
                    setDrawValues(false) // Hide value labels on the chart itself
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh the chart
            }
        }
    )
}

// Custom ValueFormatter for X-axis to display timestamps as dates
class DateAxisValueFormatter : ValueFormatter() {
    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    override fun getFormattedValue(value: Float): String {
        return dateFormat.format(Date(value.toLong()))
    }
}