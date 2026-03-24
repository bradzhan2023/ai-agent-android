package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shape.shader.verticalGradient
import com.patrykandpatrick.vico.compose.chart.zoom.rememberVicoZoomState
import com.patrykandpatrick.vico.core.chart.composed.plus
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

// Data model to hold the relevant price information
data class PricePoint(
    val timestamp: Long,
    val price: Double
)

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Use MaterialTheme as required, no custom theme or ui.tooling.
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PriceTrackerScreen(
                        fetchData = { fetchBinanceKlines() }
                    )
                }
            }
        }
    }

    /**
     * Fetches PAXGUSDT 1-hour candlestick data from Binance API.
     * Uses OkHttp for network requests and Gson for JSON parsing.
     *
     * @return A list of PricePoint objects containing close time and close price.
     */
    private suspend fun fetchBinanceKlines(): List<PricePoint> {
        // Binance Klines endpoint for PAXGUSDT, 1-hour interval, last 48 data points (2 days)
        val url = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=48"
        val request = Request.Builder().url(url).build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected HTTP code: ${response.code}")
                    }

                    val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                    val jsonArray = gson.fromJson(responseBody, JsonArray::class.java)

                    val pricePoints = mutableListOf<PricePoint>()
                    // Binance klines format:
                    // [
                    //   open_time,
                    //   open_price,
                    //   high_price,
                    //   low_price,
                    //   close_price,         // Index 4
                    //   volume,
                    //   close_time,          // Index 6
                    //   quote_asset_volume,
                    //   number_of_trades,
                    //   taker_buy_base_asset_volume,
                    //   taker_buy_quote_asset_volume,
                    //   ignore
                    // ]
                    for (element in jsonArray) {
                        val kline = element.asJsonArray
                        val closeTime = kline[6].asLong        // Close time in milliseconds
                        val closePrice = kline[4].asString.toDouble() // Close price as String, convert to Double
                        pricePoints.add(PricePoint(closeTime, closePrice))
                    }
                    pricePoints
                }
            } catch (e: Exception) {
                // Log the exception for debugging
                e.printStackTrace()
                emptyList() // Return an empty list on error
            }
        }
    }
}

@Composable
fun PriceTrackerScreen(fetchData: suspend () -> List<PricePoint>) {
    // State for current price display
    var currentPrice by remember { mutableStateOf("Loading...") }
    // State for chart data using Vico's ChartEntryModelProducer
    val chartEntryModelProducer = remember { ChartEntryModelProducer() }
    val scope = rememberCoroutineScope()

    // LaunchedEffect to fetch data when the composable enters the composition
    LaunchedEffect(Unit) {
        scope.launch {
            val data = fetchData()
            if (data.isNotEmpty()) {
                // Update current price with the latest fetched data point
                currentPrice = String.format("%.2f USDT", data.last().price)

                // Convert PricePoint data to Vico ChartEntry format
                // x-axis represents the index of the data point, y-axis represents the price
                val entries = data.mapIndexed { index, pricePoint ->
                    ChartEntry(
                        x = index.toFloat(),
                        y = pricePoint.price.toFloat()
                    )
                }
                chartEntryModelProducer.setEntries(listOf(entries))
            } else {
                currentPrice = "Error fetching data."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "PAXG/USDT Price:",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = currentPrice,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Display the LineChart if data is available
        if (chartEntryModelProducer.getModel().entries.isNotEmpty()) {
            Chart(
                chart = lineChart(
                    lines = listOf(
                        LineComponent(
                            color = MaterialTheme.colorScheme.primary.toArgb(), // Line color
                            thickness = 2.dp,
                            shape = Shapes.pillShape, // Default line shape
                            stroke = null, // No stroke around the line
                            backgroundShader = verticalGradient( // Gradient fill below the line
                                arrayOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f).toArgb(),
                                    Color.Transparent.toArgb()
                                )
                            )
                        )
                    )
                ),
                modelProducer = chartEntryModelProducer,
                // Use default axes, which do not require overriding getAxisLabel
                startAxis = rememberStartAxis(), // Y-axis
                bottomAxis = rememberBottomAxis(), // X-axis
                modifier = Modifier.fillMaxSize(),
                zoomState = rememberVicoZoomState(zoomEnabled = false) // Disable zoom for simplicity
            )
        } else {
            Text(text = "Loading chart data...", modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/**
 * Extension function to convert a Compose Color to an Android graphics Color int.
 * Required for Vico chart components that expect Int color values.
 */
private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}