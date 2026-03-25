package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

// Vico Chart Imports for Jetpack Compose
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry

/**
 * Data model for a single candlestick (Kline) from Binance API.
 * Only includes fields relevant for a simple price line chart.
 */
data class KlineData(
    val openTime: Long,      // Open time of the candlestick in milliseconds
    val openPrice: Double,   // Open price
    val highPrice: Double,   // Highest price during the interval
    val lowPrice: Double,    // Lowest price during the interval
    val closePrice: Double,  // Close price
    val volume: Double       // Volume traded during the interval
    // Other fields from Binance klines API (e.g., closeTime, quoteAssetVolume, numberOfTrades)
    // are omitted for brevity as they are not used for this simple line chart.
)

/**
 * ViewModel for fetching and managing PAXGUSDT price data.
 * Uses OkHttp for network requests and Gson for JSON parsing.
 */
class PriceTrackerViewModel : ViewModel() {
    private val client = OkHttpClient()
    private val gson = Gson()

    // State holders for UI updates
    var latestPrice by mutableStateOf<Double?>(null)
        private set
    var priceEntries by mutableStateOf(ChartEntryModelProducer())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Fetches PAXGUSDT klines (candlestick) data from the Binance API.
     * It requests data for the last 24 hours with a 1-hour interval, resulting in 24 data points.
     * The fetched JSON is parsed into a list of [KlineData] and used to update the UI state.
     */
    fun fetchBinanceData() {
        viewModelScope.launch(Dispatchers.IO) { // Perform network operation on IO dispatcher
            // Binance API endpoint for klines data
            val url = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24"
            val request = Request.Builder().url(url).build()

            try {
                val response: Response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let {
                        // Gson parsing: Binance klines API returns a list of lists of strings
                        val klineListType = object : TypeToken<List<List<String>>>() {}.type
                        val rawKlineData: List<List<String>> = gson.fromJson(it, klineListType)

                        // Map the raw string data into our structured KlineData objects
                        val parsedData = rawKlineData.mapNotNull { klineArray ->
                            // A valid Binance kline array has at least 12 elements. We only need the first 6.
                            if (klineArray.size >= 6) {
                                try {
                                    KlineData(
                                        openTime = klineArray[0].toLong(),
                                        openPrice = klineArray[1].toDouble(),
                                        highPrice = klineArray[2].toDouble(),
                                        lowPrice = klineArray[3].toDouble(),
                                        closePrice = klineArray[4].toDouble(),
                                        volume = klineArray[5].toDouble()
                                    )
                                } catch (e: NumberFormatException) {
                                    // Handle cases where number conversion fails (e.g., malformed data)
                                    errorMessage = "Data parsing error: ${e.message}"
                                    null // Skip this malformed data point
                                }
                            } else {
                                errorMessage = "Malformed data received from Binance API."
                                null // Skip malformed kline arrays
                            }
                        }

                        if (parsedData.isNotEmpty()) {
                            // Update the latest price with the close price of the last data point
                            latestPrice = parsedData.last().closePrice

                            // Prepare chart entries for Vico.
                            // X-axis values are simply indices (0 to 23 for 24 hours),
                            // Y-axis values are the close prices.
                            val entries = parsedData.mapIndexed { index, data ->
                                FloatEntry(x = index.toFloat(), y = data.closePrice.toFloat())
                            }
                            priceEntries.setEntries(listOf(entries)) // Update chart data
                            errorMessage = null // Clear any previous error messages
                        } else {
                            errorMessage = "No PAXGUSDT data received or successfully parsed."
                            latestPrice = null
                            priceEntries.setEntries(emptyList())
                        }
                    } ?: run {
                        errorMessage = "Empty response body from Binance API."
                        latestPrice = null
                        priceEntries.setEntries(emptyList())
                    }
                } else {
                    errorMessage = "Error fetching data: ${response.code} ${response.message}"
                    latestPrice = null
                    priceEntries.setEntries(emptyList())
                }
            } catch (e: IOException) {
                errorMessage = "Network error: Please check your internet connection. (${e.message})"
                latestPrice = null
                priceEntries.setEntries(emptyList())
            } catch (e: Exception) {
                // Catch any other unexpected errors during parsing or state updates
                errorMessage = "An unexpected error occurred: ${e.message}"
                latestPrice = null
                priceEntries.setEntries(emptyList())
            }
        }
    }
}

/**
 * Main Activity for the Android application.
 * It sets up the Jetpack Compose UI to display PAXGUSDT price and its 24-hour trend.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply MaterialTheme as required by the technical specification
            MaterialTheme {
                // A surface container using the 'background' color from the theme
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
 * Composable function for the main price tracker screen.
 * It displays the current price, potential error messages, and a 24-hour line chart.
 */
@Composable
fun PriceTrackerScreen(viewModel: PriceTrackerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    // LaunchedEffect ensures data fetching occurs only once when the composable enters composition
    LaunchedEffect(Unit) {
        viewModel.fetchBinanceData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App title
        Text(
            text = "PAXGUSDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Display the latest fetched price or a loading message
        viewModel.latestPrice?.let { price ->
            Text(
                text = "Current PAXGUSDT Price: $%.2f".format(price),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        } ?: run {
            Text(
                text = "Fetching price...",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Display any error messages from the ViewModel
        viewModel.errorMessage?.let { error ->
            Text(
                text = "Error: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title for the chart section
        Text(
            text = "24-Hour Price Trend (Hourly)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Vico Chart implementation
        // Only display the chart if there is data available
        if (viewModel.priceEntries.entryCollections.isNotEmpty()) {
            val chartModel = viewModel.priceEntries
            Chart(
                chart = lineChart(), // Use a simple line chart
                model = chartModel,
                // Use default start (vertical) axis labels for price values
                startAxis = rememberStartAxis(),
                // Use default bottom (horizontal) axis labels.
                // The requirement is to "禁止覆寫 getAxisLabel，使用預設圖表標籤",
                // so no custom valueFormatter is applied to display indices 0-23 directly.
                bottomAxis = rememberBottomAxis(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp) // Set a fixed height for the chart
                    .padding(vertical = 8.dp)
            )
        } else {
            // Show a message if no chart data is available (e.g., during initial load or after an error)
            Text(
                text = "No chart data available.",
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}