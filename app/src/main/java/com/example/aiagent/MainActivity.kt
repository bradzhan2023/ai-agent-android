package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.example.aiagent.ui.theme.AIAgentTheme // Assuming a default theme setup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import androidx.lifecycle.viewmodel.compose.viewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

// DTOs (Data Transfer Objects) for Binance API responses

/**
 * Data class representing the current price ticker from Binance.
 * Example: {"symbol":"PAXGUSDT","price":"2300.00000000"}
 */
data class PriceTicker(
    val symbol: String,
    val price: String
)

/**
 * Data class representing a parsed Kline (candlestick) data point.
 * The Binance Klines API returns a List<List<Any>> where each inner list
 * contains various data points. We are interested in `openTime` (index 0)
 * and `closePrice` (index 4).
 */
data class KlineData(
    val openTime: Long, // Timestamp in milliseconds
    val closePrice: Double // Closing price
)

// Retrofit Service Interface for Binance API
interface BinanceApiService {
    /**
     * Fetches the current ticker price for a given symbol.
     * @param symbol The trading pair symbol (e.g., "PAXGUSDT").
     * @return PriceTicker object containing the symbol and current price.
     */
    @GET("api/v3/ticker/price")
    suspend fun getPriceTicker(@Query("symbol") symbol: String): PriceTicker

    /**
     * Fetches historical kline (candlestick) data for a given symbol.
     * @param symbol The trading pair symbol (e.g., "PAXGUSDT").
     * @param interval The kline interval (e.g., "1h", "4h", "1d").
     * @param limit The number of klines to retrieve (max 1000).
     * @return A list of lists, where each inner list represents a kline.
     */
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<Any>> // Raw list of lists as returned by Binance
}

// ViewModel to manage data fetching and state for the UI
class GoldPriceViewModel : ViewModel() {
    // StateFlows to expose data to the UI, allowing for reactive updates
    private val _currentPrice = MutableStateFlow<String?>("Loading...")
    val currentPrice: StateFlow<String?> = _currentPrice.asStateFlow()

    private val _historicalPrices = MutableStateFlow<List<KlineData>>(emptyList())
    val historicalPrices: StateFlow<List<KlineData>> = _historicalPrices.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val binanceApiService: BinanceApiService // Retrofit service instance

    init {
        // Configure OkHttpClient with a logging interceptor for debugging network requests
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        // Initialize Retrofit for API calls
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.binance.com/") // Binance API base URL
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create()) // Use Gson for JSON deserialization
            .build()

        binanceApiService = retrofit.create(BinanceApiService::class.java)

        // Fetch initial data when the ViewModel is created
        fetchPriceData()
    }

    /**
     * Fetches current and historical price data from the Binance API.
     * Launches a coroutine in the viewModelScope to perform network operations.
     */
    fun fetchPriceData() {
        viewModelScope.launch {
            try {
                _errorMessage.value = null // Clear any previous errors
                _currentPrice.value = "Loading..." // Show loading state for current price

                // Fetch current price for PAXGUSDT
                val ticker = binanceApiService.getPriceTicker("PAXGUSDT")
                _currentPrice.value = "Current PAXGUSDT Price: ${ticker.price}"

                // Fetch historical Klines (e.g., last 100 4-hour candles) for the chart
                // "4h" interval provides a good balance for recent history
                val klines = binanceApiService.getKlines("PAXGUSDT", "4h", 100)

                // Parse the raw klines data into our structured KlineData objects
                val parsedKlines = klines.map { kline ->
                    // Binance kline response structure:
                    // 0: Open time (Long)
                    // 1: Open price (String)
                    // 2: High price (String)
                    // 3: Low price (String)
                    // 4: Close price (String)
                    // ... other fields
                    KlineData(
                        openTime = (kline[0] as Double).toLong(), // Open time is a Double, cast to Long
                        closePrice = (kline[4] as String).toDouble() // Close price is a String, convert to Double
                    )
                }
                _historicalPrices.value = parsedKlines // Update historical prices state

            } catch (e: Exception) {
                // Handle any exceptions during API calls
                _errorMessage.value = "Failed to fetch data: ${e.message}"
                _currentPrice.value = "Error"
                e.printStackTrace() // Log the error for debugging
            }
        }
    }
}

// MainActivity - Entry point for the Compose UI
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAgentTheme { // Apply the Material3 theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceTrackerScreen() // Display the main UI content
                }
            }
        }
    }
}

// Composable for the entire Gold Price Tracker screen
@OptIn(ExperimentalMaterial3Api::class) // Required for using TopAppBar
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel = viewModel()) {
    // Collect state from the ViewModel to trigger UI recompositions
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT Price Tracker") }) // App bar at the top
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
                .padding(16.dp), // Overall screen padding
            verticalArrangement = Arrangement.spacedBy(16.dp) // Spacing between UI elements
        ) {
            // Display current price
            currentPrice?.let {
                Text(it, style = MaterialTheme.typography.headlineMedium)
            }

            // Display error message if present
            if (errorMessage != null) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (historicalPrices.isEmpty() && currentPrice == "Loading...") {
                // Show loading indicator if data is being fetched
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Loading historical data...", style = MaterialTheme.typography.bodyMedium)
            } else {
                // Display the chart only if historical data is available
                if (historicalPrices.isNotEmpty()) {
                    GoldPriceLineChart(historicalPrices = historicalPrices)
                } else {
                    // This case might happen if API returns empty data without error
                    Text("No historical data available yet.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Button to manually refresh data
            Button(onClick = { viewModel.fetchPriceData() }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh Data")
            }
        }
    }
}

// Composable function to display the LineChart using MPAndroidChart
@Composable
fun GoldPriceLineChart(historicalPrices: List<KlineData>) {
    // AndroidView is used to embed a traditional Android View (LineChart) into Compose UI
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp), // Set a fixed height for the chart
        factory = { context ->
            // Initialize the LineChart when the Composable is first laid out
            LineChart(context).apply {
                description.isEnabled = false // Disable description label
                setTouchEnabled(true) // Enable touch interactions
                isDragEnabled = true // Enable dragging
                setScaleEnabled(true) // Enable scaling/zooming
                setPinchZoom(true) // Enable pinch zoom
                setDrawGridBackground(false) // Do not draw background grid
                setBackgroundColor(Color.WHITE) // Set chart background color

                // Configure X-axis (time axis)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM // Place X-axis labels at the bottom
                    setDrawGridLines(false) // Do not draw vertical grid lines
                    setDrawAxisLine(true) // Draw the axis line
                    granularity = 1f // Minimum interval between labels
                    labelRotationAngle = -45f // Rotate labels for better readability
                    textColor = Color.BLACK // Set label text color

                    // Custom ValueFormatter for X-axis labels (timestamps)
                    valueFormatter = object : ValueFormatter() {
                        // Date formatter for displaying time in a readable format
                        private val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            // The 'value' here is the x-coordinate (index in our case)
                            // Map it back to the original timestamp from the historicalPrices list
                            val index = value.toInt()
                            return if (index >= 0 && index < historicalPrices.size) {
                                dateFormat.format(Date(historicalPrices[index].openTime))
                            } else {
                                "" // Return empty string for out-of-bounds indices
                            }
                        }
                    }
                }

                // Configure Left Y-axis (price axis)
                axisLeft.apply {
                    setDrawGridLines(true) // Draw horizontal grid lines
                    textColor = Color.BLACK // Set label text color

                    // Custom ValueFormatter for Y-axis labels (prices)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            // Format price with 2 decimal places and "USDT" currency symbol
                            return String.format(Locale.getDefault(), "%.2f USDT", value)
                        }
                    }
                }

                // Disable the Right Y-axis as we only need one price axis
                axisRight.isEnabled = false

                // Configure chart legend
                legend.apply {
                    form = com.github.mikephil.charting.components.Legend.LegendForm.LINE // Line form for legend
                    textColor = Color.BLACK // Set legend text color
                }
            }
        },
        update = { chart ->
            // Update the chart when historicalPrices data changes
            if (historicalPrices.isNotEmpty()) {
                val entries = ArrayList<Entry>()
                // Convert KlineData into Entry objects for the LineChart
                // X-value is the index, Y-value is the closing price
                historicalPrices.forEachIndexed { index, kline ->
                    entries.add(Entry(index.toFloat(), kline.closePrice.toFloat()))
                }

                // Create a LineDataSet from the entries
                val dataSet = LineDataSet(entries, "PAXGUSDT Close Price").apply {
                    color = Color.BLUE // Line color
                    setCircleColor(Color.BLUE) // Circle color for data points
                    lineWidth = 2f // Line thickness
                    circleRadius = 3f // Radius of data point circles
                    setDrawCircleHole(false) // Do not draw a hole in data point circles
                    valueTextSize = 0f // Hide value labels on individual points
                    setDrawValues(false) // Ensure values are not drawn next to points
                    mode = LineDataSet.Mode.LINEAR // Draw a smooth linear line
                }

                // Create LineData object and set it to the chart
                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh the chart to redraw with new data
                chart.animateX(1000) // Animate the chart along the X-axis for 1 second
            } else {
                chart.data = null // Clear chart data if no historical prices
                chart.invalidate() // Refresh to show empty chart
            }
        }
    )
}

// Preview Composable for design-time visualization in Android Studio
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AIAgentTheme {
        // Create a mock ViewModel with sample data for preview purposes
        val mockViewModel = GoldPriceViewModel().apply {
            _currentPrice.value = "Current PAXGUSDT Price: 2300.50"
            _historicalPrices.value = listOf(
                KlineData(System.currentTimeMillis() - 5 * 24 * 3600 * 1000, 2200.0),
                KlineData(System.currentTimeMillis() - 4 * 24 * 3600 * 1000, 2250.5),
                KlineData(System.currentTimeMillis() - 3 * 24 * 3600 * 1000, 2230.2),
                KlineData(System.currentTimeMillis() - 2 * 24 * 3600 * 1000, 2280.9),
                KlineData(System.currentTimeMillis() - 1 * 24 * 3600 * 1000, 2310.1),
                KlineData(System.currentTimeMillis(), 2300.5)
            )
        }
        GoldPriceTrackerScreen(mockViewModel) // Pass the mock ViewModel to the screen
    }
}