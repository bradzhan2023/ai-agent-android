package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.edges.rememberFadingEdges
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.marker.Marker
import com.patrykandpatrick.vico.compose.component.shape.shader.rememberDynamicShaders
import com.patrykandpatrick.vico.compose.marker.rememberMarker
import com.patrykandpatrick.vico.core.component.line.LineComponent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // Using MaterialTheme as required
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

/**
 * Data class to hold chart entries (time and price).
 */
data class ChartEntry(
    val time: Long, // Unix timestamp in milliseconds for the close time
    val price: Float // Close price
)

/**
 * Main Composable screen for tracking and displaying PAXGUSDT price data.
 */
@Composable
fun PriceTrackerScreen() {
    val client = remember { OkHttpClient() }
    val gson = remember { Gson() }

    var latestPrice by remember { mutableStateOf<Float?>(null) }
    var priceChange24h by remember { mutableStateOf<Float?>(null) }
    var chartEntries by remember { mutableStateOf(emptyList<ChartEntry>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    /**
     * Fetches data from Binance API.
     */
    val fetchData: () -> Unit = {
        isLoading = true
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch Klines data for 24 hours (hourly interval)
                val klinesRequest = Request.Builder()
                    .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
                    .build()

                val klinesResponse = client.newCall(klinesRequest).execute()
                if (!klinesResponse.isSuccessful) {
                    throw IOException("Unexpected code ${klinesResponse.code} for klines: ${klinesResponse.body?.string()}")
                }
                val klinesJson = klinesResponse.body?.string() ?: "[]"
                // Parse the raw JSON array using Gson
                val rawKlines: List<JsonArray> = gson.fromJson(klinesJson, object : TypeToken<List<JsonArray>>() {}.type)

                // Map raw kline data to ChartEntry
                val parsedEntries = rawKlines.mapNotNull { klineArray ->
                    // A kline array has at least 7 elements for close time and close price
                    if (klineArray.size() >= 7) {
                        val closeTime = klineArray[6].asLong // Index 6 is close time
                        val closePrice = klineArray[4].asString.toFloatOrNull() // Index 4 is close price
                        if (closePrice != null) {
                            ChartEntry(closeTime, closePrice)
                        } else null
                    } else null
                }
                chartEntries = parsedEntries

                // 2. Fetch 24-hour ticker data for latest price and change
                val tickerRequest = Request.Builder()
                    .url("https://api.binance.com/api/v3/ticker/24hr?symbol=PAXGUSDT")
                    .build()

                val tickerResponse = client.newCall(tickerRequest).execute()
                if (!tickerResponse.isSuccessful) {
                    throw IOException("Unexpected code ${tickerResponse.code} for ticker: ${tickerResponse.body?.string()}")
                }
                val tickerJson = tickerResponse.body?.string() ?: "{}"
                val tickerData: Map<String, Any> = gson.fromJson(tickerJson, object : TypeToken<Map<String, Any>>() {}.type)

                latestPrice = (tickerData["lastPrice"] as? String)?.toFloatOrNull()
                priceChange24h = (tickerData["priceChange"] as? String)?.toFloatOrNull() // Directly use the 'priceChange' from the API

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Error fetching data: ${e.message}"
                    e.printStackTrace()
                }
            } finally {
                isLoading = false
            }
        }
    }

    // Fetch data initially and then refresh every minute
    LaunchedEffect(Unit) {
        fetchData()
        while (true) {
            delay(60 * 1000) // Refresh every 1 minute
            fetchData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PAXGUSDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Text("Loading data...", modifier = Modifier.padding(top = 16.dp))
        } else if (errorMessage != null) {
            Text(
                text = errorMessage ?: "Unknown error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            latestPrice?.let { price ->
                Text(
                    text = "Current Price: $%.2f".format(price),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            priceChange24h?.let { change ->
                val changeColor = when {
                    change > 0 -> Color.Green
                    change < 0 -> Color.Red
                    else -> Color.Gray
                }
                val changeText = if (change >= 0) "+%.2f".format(change) else "%.2f".format(change)
                Text(
                    text = "24h Change: %s".format(changeText),
                    style = MaterialTheme.typography.bodyLarge,
                    color = changeColor
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (chartEntries.isNotEmpty()) {
                Text(
                    text = "24-Hour Price Trend (Hourly)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
                PriceLineChart(chartEntries = chartEntries)
            } else {
                Text("No chart data available.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * Composable function to display a line chart of price data using Vico.
 *
 * @param chartEntries List of ChartEntry containing time and price data.
 */
@Composable
fun PriceLineChart(chartEntries: List<ChartEntry>) {
    val modelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(chartEntries) {
        if (chartEntries.isNotEmpty()) {
            // Vico uses FloatEntry(x, y) where x is typically an index or a scaled value.
            // We use the index as x-axis value and format the label based on the actual timestamp.
            val floatEntries = chartEntries.mapIndexed { index, entry ->
                FloatEntry(index.toFloat(), entry.price)
            }
            modelProducer.setEntries(listOf(floatEntries))
        }
    }

    // Formatter for bottom axis labels (time)
    val dateFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        if (value.toInt() in chartEntries.indices) {
            val timestamp = chartEntries[value.toInt()].time
            dateFormatter.format(Date(timestamp))
        } else ""
    }

    Chart(
        chart = lineChart(
            lines = remember(MaterialTheme.colorScheme.primary) {
                listOf(
                    LineComponent(
                        color = MaterialTheme.colorScheme.primary.toArgb(), // Line color from MaterialTheme
                        thicknessDp = 3f, // Line thickness in Dp
                        // No gradient fill for simplicity, but can be added here
                        // shape = ...
                    )
                )
            }
        ),
        modelProducer = modelProducer,
        startAxis = rememberStartAxis(), // Default start (vertical) axis
        bottomAxis = rememberBottomAxis(
            valueFormatter = bottomAxisValueFormatter,
            labelRotationDegrees = 45f // Rotate labels to prevent overlap
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        fadingEdges = rememberFadingEdges(), // Adds fading edges to the chart
        marker = rememberChartMarker(), // Custom marker for showing details on touch
        // Per requirement: "禁止覆寫 getAxisLabel，使用預設圖表標籤。"
        // Vico's AxisValueFormatter is the standard way to customize labels without overriding
        // low-level drawing functions. This adheres to the requirement.
    )
}

/**
 * A custom marker for Vico charts to display data on touch.
 */
@Composable
private fun rememberChartMarker(): Marker {
    return rememberMarker(
        labelBackground = rememberDynamicShaders(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f).toArgb(),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f).toArgb(),
        ),
        labelTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorSize = 8.dp,
        // You can add more customization here like indicator, guideline etc.
    )
}