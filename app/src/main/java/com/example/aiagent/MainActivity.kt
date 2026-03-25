package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Required Gradle dependencies for build.gradle (app module):
// implementation 'androidx.core:core-ktx:1.12.0'
// implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
// implementation 'androidx.activity:activity-compose:1.8.2'
// implementation platform('androidx.compose:compose-bom:2023.08.00')
// implementation 'androidx.compose.ui:ui'
// implementation 'androidx.compose.ui:ui-graphics'
// implementation 'androidx.compose.material3:material3'
//
// // For Gson
// implementation 'com.google.code.gson:gson:2.10.1'
//
// // For OkHttp
// implementation 'com.squareup.okhttp3:okhttp:4.12.0'
//
// // For MPAndroidChart (add 'maven { url 'https://jitpack.io' }' to settings.gradle)
// implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'


// Data class for Binance 24hr ticker (summary)
data class Ticker24hrResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("priceChange") val priceChange: String,
    @SerializedName("priceChangePercent") val priceChangePercent: String,
    @SerializedName("weightedAvgPrice") val weightedAvgPrice: String,
    @SerializedName("prevClosePrice") val prevClosePrice: String,
    @SerializedName("lastPrice") val lastPrice: String,
    @SerializedName("lastQty") val lastQty: String,
    @SerializedName("bidPrice") val bidPrice: String,
    @SerializedName("bidQty") val bidQty: String,
    @SerializedName("askPrice") val askPrice: String,
    @SerializedName("askQty") val askQty: String,
    @SerializedName("openPrice") val openPrice: String,
    @SerializedName("highPrice") val highPrice: String,
    @SerializedName("lowPrice") val lowPrice: String,
    @SerializedName("volume") val volume: String,
    @SerializedName("quoteVolume") val quoteVolume: String,
    @SerializedName("openTime") val openTime: Long,
    @SerializedName("closeTime") val closeTime: Long,
    @SerializedName("firstId") val firstId: Long,
    @SerializedName("lastId") val lastId: Long,
    @SerializedName("count") val count: Long
)

// Data class for Binance Klines (candlestick data)
// Binance kline API returns an array of arrays, each sub-array is a candlestick.
// Example: [ [openTime, open, high, low, close, volume, closeTime, quoteAssetVolume, numberOfTrades, takerBuyBaseAssetVolume, takerBuyQuoteAssetVolume, ignore] ]
data class Kline(
    val openTime: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String,
    val numberOfTrades: Long,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String,
    val ignore: String
)

class MainActivity : ComponentActivity() {

    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BinancePAGXUSTrackerApp(okHttpClient, gson, this.lifecycleScope)
                }
            }
        }
    }
}

@Composable
fun BinancePAGXUSTrackerApp(
    okHttpClient: OkHttpClient,
    gson: Gson,
    lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope
) {
    var tickerData by remember { mutableStateOf<Ticker24hrResponse?>(null) }
    var klineData by remember { mutableStateOf<List<Kline>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Using LaunchedEffect to trigger data fetching when the composable enters the composition
    LaunchedEffect(Unit) {
        isLoading = true
        lifecycleScope.launch(Dispatchers.IO) { // Perform network operations on IO dispatcher
            try {
                // Fetch 24hr ticker summary for current price and summary stats
                val tickerRequest = Request.Builder()
                    .url("https://api.binance.com/api/v3/ticker/24hr?symbol=PAXGUSDT")
                    .build()
                val tickerResponse = okHttpClient.newCall(tickerRequest).execute()
                val tickerBody = tickerResponse.body?.string()

                if (tickerResponse.isSuccessful && tickerBody != null) {
                    tickerData = gson.fromJson(tickerBody, Ticker24hrResponse::class.java)
                } else {
                    errorMessage = "Error fetching ticker: ${tickerResponse.code} - ${tickerBody ?: "Unknown error"}"
                }

                // Fetch 24 hours of 1-hour klines for the chart
                val klinesRequest = Request.Builder()
                    .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
                    .build()
                val klinesResponse = okHttpClient.newCall(klinesRequest).execute()
                val klinesBody = klinesResponse.body?.string()

                if (klinesResponse.isSuccessful && klinesBody != null) {
                    // Binance klines API returns an array of arrays
                    val jsonArray = gson.fromJson(klinesBody, Array<Array<Any>>::class.java)
                    klineData = jsonArray.map { rawKline ->
                        // Safely cast elements from the raw JSON array
                        Kline(
                            openTime = (rawKline[0] as Double).toLong(), // Gson might parse numbers as Double
                            open = rawKline[1] as String,
                            high = rawKline[2] as String,
                            low = rawKline[3] as String,
                            close = rawKline[4] as String,
                            volume = rawKline[5] as String,
                            closeTime = (rawKline[6] as Double).toLong(),
                            quoteAssetVolume = rawKline[7] as String,
                            numberOfTrades = (rawKline[8] as Double).toLong(),
                            takerBuyBaseAssetVolume = rawKline[9] as String,
                            takerBuyQuoteAssetVolume = rawKline[10] as String,
                            ignore = rawKline[11] as String
                        )
                    }
                } else {
                    errorMessage = "Error fetching klines: ${klinesResponse.code} - ${klinesBody ?: "Unknown error"}"
                }

            } catch (e: IOException) {
                errorMessage = "Network error: ${e.message}"
            } catch (e: Exception) {
                errorMessage = "An unexpected error occurred: ${e.message}"
            } finally {
                // Ensure UI updates are on the main thread
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Binance PAXGUSDT Tracker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Loading data...", modifier = Modifier.padding(top = 8.dp))
        } else if (errorMessage != null) {
            Text(
                text = "Error: $errorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            tickerData?.let { ticker ->
                Text("Symbol: ${ticker.symbol}", style = MaterialTheme.typography.titleLarge)
                Text("Current Price: ${ticker.lastPrice} USDT", style = MaterialTheme.typography.bodyLarge)
                val changeColor = if (ticker.priceChangePercent.startsWith("-")) Color.Red else Color.Green
                Text(
                    text = "24h Change: ${ticker.priceChangePercent}%",
                    color = changeColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("24h High: ${ticker.highPrice} USDT", style = MaterialTheme.typography.bodyMedium)
                Text("24h Low: ${ticker.lowPrice} USDT", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (klineData.isNotEmpty()) {
                Text(
                    text = "PAXGUSDT 24-Hour Price Chart (Hourly)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                // Pass all kline timestamps for accurate X-axis formatting
                KlineLineChart(
                    klineData = klineData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Makes the chart fill remaining vertical space
                )
            } else if (tickerData != null) { // Only show this if ticker data loaded but kline didn't
                Text("No 24-hour kline data available for charting.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun KlineLineChart(klineData: List<Kline>, modifier: Modifier = Modifier) {
    val context = LocalContext.current // Used implicitly by AndroidView to create a View

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false // No description text for the chart
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true) // Enable pinch zoom for both axes
                setNoDataText("Loading Chart Data...") // Fallback text when no data is set

                // Configure X-axis
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false) // No vertical grid lines
                xAxis.setDrawAxisLine(true)
                xAxis.labelRotationAngle = -45f // Rotate labels to prevent overlap
                xAxis.granularity = 1f // Ensures labels are shown for each entry
                xAxis.setLabelCount(5, true) // Attempt to show around 5 labels, including start/end
                xAxis.textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                xAxis.axisLineColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                xAxis.gridColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()


                // Configure Y-axis (left)
                axisLeft.setDrawGridLines(true)
                axisLeft.setDrawAxisLine(true)
                axisLeft.valueFormatter = PriceAxisValueFormatter() // Custom formatter for prices
                axisLeft.textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                axisLeft.axisLineColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                axisLeft.gridColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

                // Configure Y-axis (right) - disable as we only use the left Y-axis
                axisRight.isEnabled = false

                // Configure Legend
                legend.isEnabled = true
                legend.textColor = MaterialTheme.colorScheme.onSurface.toArgb()
            }
        },
        update = { chart ->
            if (klineData.isNotEmpty()) {
                // Create Entry objects: x-value is an index, y-value is the closing price
                val entries = klineData.mapIndexed { index, kline ->
                    Entry(index.toFloat(), kline.close.toFloat())
                }

                // Create a DataSet for the line chart
                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = Color.parseColor("#FFC107") // A prominent amber color for the line
                    setCircleColor(Color.parseColor("#FFC107"))
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 9f
                    setDrawValues(false) // Do not draw value text on the data points
                    mode = LineDataSet.Mode.LINEAR // Draw straight lines between points
                }

                val lineData = LineData(dataSet)
                chart.data = lineData

                // Update X-axis formatter with the actual timestamps for accurate labels
                // The formatter uses the index (float value) to look up the correct timestamp
                chart.xAxis.valueFormatter = DateAxisValueFormatter(klineData.map { it.openTime })

                // Refresh the chart to display new data
                chart.invalidate()
            } else {
                chart.data = null // Clear any existing data
                chart.setNoDataText("No chart data available.")
                chart.invalidate()
            }
        }
    )
}

/**
 * Custom ValueFormatter for the X-axis to display human-readable timestamps.
 * It maps the float x-value (which is an index) back to the actual timestamp from the kline data.
 */
class DateAxisValueFormatter(private val timestamps: List<Long> = emptyList()) : ValueFormatter() {
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getFormattedValue(value: Float): String {
        val index = value.toInt()
        return if (index >= 0 && index < timestamps.size) {
            dateFormat.format(Date(timestamps[index]))
        } else {
            "" // Return empty string for out-of-bounds indices
        }
    }
}

/**
 * Custom ValueFormatter for the Y-axis to format price values.
 */
class PriceAxisValueFormatter : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        return String.format(Locale.getDefault(), "%.2f USDT", value)
    }
}