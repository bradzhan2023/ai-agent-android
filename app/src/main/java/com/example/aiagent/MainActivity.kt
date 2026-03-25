package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase // Explicitly requested by user
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

// Data class to represent a single KLine (candlestick) data point
// The Binance API returns an array of arrays, so we need a custom way to parse it.
// This data class holds the parsed fields.
data class KLineData(
    val openTime: Long,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val closePrice: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String,
    val numberOfTrades: Long,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String,
    val ignore: String
)

// Helper class to convert the List<*> (representing a JSON array) into a KLineData object
class KLineResponseConverter {
    fun convert(jsonArray: List<*>?): KLineData? {
        if (jsonArray == null || jsonArray.size < 12) return null
        return KLineData(
            // Gson might parse numbers as Doubles when using List<*>, so explicit casting/conversion is needed
            openTime = (jsonArray[0] as Double).toLong(),
            openPrice = jsonArray[1] as String,
            highPrice = jsonArray[2] as String,
            lowPrice = jsonArray[3] as String,
            closePrice = jsonArray[4] as String,
            volume = jsonArray[5] as String,
            closeTime = (jsonArray[6] as Double).toLong(),
            quoteAssetVolume = jsonArray[7] as String,
            numberOfTrades = (jsonArray[8] as Double).toLong(),
            takerBuyBaseAssetVolume = jsonArray[9] as String,
            takerBuyQuoteAssetVolume = jsonArray[10] as String,
            ignore = jsonArray[11] as String
        )
    }
}

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient() // OkHttp client for network requests
    private val gson = Gson() // Gson instance for JSON parsing
    private val klineResponseConverter = KLineResponseConverter() // Custom converter for Binance KLine data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Using MaterialTheme as required
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BinanceChartScreen(
                        client = client,
                        gson = gson,
                        klineResponseConverter = klineResponseConverter
                    )
                }
            }
        }
    }
}

@Composable
fun BinanceChartScreen(
    client: OkHttpClient,
    gson: Gson,
    klineResponseConverter: KLineResponseConverter
) {
    // State to hold the fetched KLine data
    var chartData by remember { mutableStateOf<List<KLineData>>(emptyList()) }
    // State to display messages to the user (loading, error, success)
    var statusMessage by remember { mutableStateOf("Loading data...") }
    val coroutineScope = rememberCoroutineScope() // Coroutine scope for network operations

    // LaunchedEffect to trigger data fetching when the composable enters the composition
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) { // Perform network request on IO dispatcher
            try {
                val symbol = "BTCUSDT" // Bitcoin/USDT trading pair
                val interval = "1h" // 1-hour candlestick interval
                val limit = 500 // Fetch 500 data points (e.g., 500 hours of data)

                val request = Request.Builder()
                    .url("https://api.binance.com/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected HTTP code: ${response.code}, message: ${response.message}")
                    }

                    val responseBody = response.body?.string()
                    if (responseBody == null) {
                        statusMessage = "Error: Empty response body from Binance API."
                        return@launch
                    }

                    // Binance KLine API returns a JSON array of arrays, e.g.:
                    // [[1499040000000,"0.01634790",...], [...]]
                    // We parse it as a List of Lists of arbitrary types first.
                    val rawKlines = gson.fromJson(responseBody, List::class.java) as List<List<*>>

                    // Then convert each inner list to our KLineData data class
                    val parsedKlines = rawKlines.mapNotNull { klineResponseConverter.convert(it) }
                    chartData = parsedKlines
                    statusMessage = "Successfully loaded ${parsedKlines.size} data points for $symbol ($interval)."
                }
            } catch (e: Exception) {
                // Update status message on error
                statusMessage = "Error fetching data: ${e.message ?: "Unknown error"}"
                e.printStackTrace()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Display status message
        Text(
            text = statusMessage,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.headlineSmall
        )

        // Only display the chart if data is available
        if (chartData.isNotEmpty()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Fills the remaining vertical space
                    .padding(horizontal = 8.dp),
                factory = { context ->
                    // Initialize the LineChart from MPAndroidChart library
                    LineChart(context).apply {
                        description.isEnabled = false // Disable chart description label
                        setTouchEnabled(true) // Enable touch gestures
                        isDragEnabled = true // Enable dragging
                        setScaleEnabled(true) // Enable scaling
                        setPinchZoom(true) // Enable pinch zoom

                        // Configure X-axis
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM // X-axis at the bottom
                            setDrawGridLines(false) // Do not draw vertical grid lines
                            // IMPORTANT: No custom value formatter is set here.
                            // The chart will use its default numerical labels for the X-axis (entry indices).
                        }

                        // Disable the right Y-axis as it's often redundant for single-line charts
                        axisRight.isEnabled = false

                        // Configure Left Y-axis
                        axisLeft.apply {
                            setDrawGridLines(true) // Draw horizontal grid lines
                            // IMPORTANT: No custom value formatter is set here.
                            // The chart will use its default numerical labels for the Y-axis (price values).
                        }

                        legend.isEnabled = true // Show legend
                    }
                },
                update = { chart ->
                    // Convert KLineData into Entry objects for the chart
                    // We use the index as x-value and closing price as y-value
                    val entries = chartData.mapIndexed { index, data ->
                        // Ensure price is a float
                        Entry(index.toFloat(), data.closePrice.toFloatOrNull() ?: 0f)
                    }

                    // Create a LineDataSet with the entries
                    val dataSet = LineDataSet(entries, "BTCUSDT Close Price").apply {
                        color = Color.BLUE // Line color
                        setCircleColor(Color.BLUE) // Circle color for data points
                        lineWidth = 2f // Line width
                        circleRadius = 3f // Radius of the circles
                        setDrawCircleHole(false) // Do not draw a hole in the circles
                        valueTextSize = 0f // Hide actual value labels on the points themselves
                        setDrawValues(false) // Also ensure values are not drawn
                    }

                    // Create LineData and set it to the chart
                    chart.data = LineData(dataSet)
                    chart.invalidate() // Refresh the chart to display new data
                }
            )
        } else if (statusMessage.startsWith("Error")) {
            // Optional: Display a more prominent error message if data loading failed
            Text(
                text = "Failed to load chart data. Please check your network connection and try again.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error // Use error color from MaterialTheme
            )
        }
    }
}