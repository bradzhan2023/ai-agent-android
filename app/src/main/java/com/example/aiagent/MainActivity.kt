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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiagent.ui.theme.AIAgentTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// 1. Data Models
/**
 * Represents a single KLine (candlestick) data point from Binance.
 * The Binance API returns a list of lists of strings, so this data class
 * is primarily for conceptual understanding; direct parsing happens in the repository.
 */
@Serializable
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

/**
 * Simplified data class for a price point, used in the UI state.
 */
data class PricePoint(
    val timestamp: Long, // Unix timestamp in milliseconds
    val price: Float
)

// 2. Network Interface for Binance API
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 100
    ): List<List<String>> // Binance Klines API returns a list of lists of strings
}

// 3. Repository for data fetching logic
class PriceRepository(private val apiService: BinanceApiService) {
    /**
     * Fetches KLine data for PAXGUSDT and converts it into a list of PricePoint objects.
     * @param interval The candlestick interval (e.g., "1m", "1h", "1d").
     * @param limit The number of data points to retrieve.
     * @return A list of PricePoint objects, or an empty list if an error occurs.
     */
    suspend fun getPAXGUSDTKlines(interval: String, limit: Int): List<PricePoint> {
        val rawData = apiService.getKlines("PAXGUSDT", interval, limit)
        // The Binance Klines API returns data in a specific order:
        // [0] Open time
        // [1] Open
        // [2] High
        // [3] Low
        // [4] Close
        // ... and so on. We need Open time and Close price.
        return rawData.map { kline ->
            PricePoint(
                timestamp = kline[0].toLong(), // Open time (milliseconds)
                price = kline[4].toFloat()     // Close price
            )
        }
    }
}

// 4. ViewModel to manage UI-related data and logic
class GoldPriceViewModel(private val repository: PriceRepository) : ViewModel() {

    private val _currentPrice = MutableStateFlow<Float?>(null)
    val currentPrice: StateFlow<Float?> = _currentPrice.asStateFlow()

    private val _historicalPrices = MutableStateFlow<List<PricePoint>>(emptyList())
    val historicalPrices: StateFlow<List<PricePoint>> = _historicalPrices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        fetchGoldPrices()
    }

    /**
     * Fetches the latest and historical PAXGUSDT prices.
     */
    fun fetchGoldPrices() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Fetch 1-hour interval data for the chart (e.g., last 100 hours)
                val klines = repository.getPAXGUSDTKlines("1h", 100)
                _historicalPrices.value = klines
                _currentPrice.value = klines.lastOrNull()?.price

            } catch (e: HttpException) {
                _errorMessage.value = "Network error: ${e.code()} - ${e.message()}"
            } catch (e: IOException) {
                _errorMessage.value = "Connection error: ${e.message}"
            } catch (e: Exception) {
                _errorMessage.value = "An unexpected error occurred: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Factory for creating [GoldPriceViewModel] with dependencies.
     */
    class Factory(private val repository: PriceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GoldPriceViewModel::class.java)) {
                return GoldPriceViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// 5. MainActivity - Entry point of the application
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup Retrofit and its dependencies for network requests
        val json = Json { ignoreUnknownKeys = true } // Configure JSON parser
        val contentType = "application/json".toMediaType() // Define content type for Retrofit converter
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY } // For network request logging
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS) // Set connection timeout
            .readTimeout(30, TimeUnit.SECONDS)    // Set read timeout
            .writeTimeout(30, TimeUnit.SECONDS)   // Set write timeout
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.binance.com/") // Binance API base URL
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType)) // Use kotlinx.serialization for JSON
            .build()

        val apiService = retrofit.create(BinanceApiService::class.java)
        val repository = PriceRepository(apiService)
        val viewModelFactory = GoldPriceViewModel.Factory(repository)

        setContent {
            AIAgentTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Obtain the ViewModel using the factory
                    val viewModel: GoldPriceViewModel =
                        ViewModelProvider(this, viewModelFactory)[GoldPriceViewModel::class.java]
                    GoldPriceTrackerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// 6. UI Composables
/**
 * The main screen for tracking PAXGUSDT prices.
 * Displays current price, loading state, error messages, and a historical price chart.
 */
@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    // Collect states from ViewModel
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT Price Tracker") })
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
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            errorMessage?.let { message ->
                Text(
                    text = "Error: $message",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            currentPrice?.let { price ->
                Text(
                    text = "Current PAXGUSDT Price: $%.2f".format(price),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } ?: run {
                if (!isLoading && errorMessage == null) {
                    Text(
                        text = "Loading price...",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.fetchGoldPrices() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Price")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (historicalPrices.isNotEmpty()) {
                Text(
                    text = "Historical Prices (Last ${historicalPrices.size} hours)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                PriceLineChart(historicalPrices = historicalPrices)
            } else if (!isLoading && errorMessage == null) {
                Text(
                    text = "No historical data available.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * Composable that displays a LineChart using MPAndroidChart library.
 * @param historicalPrices The list of price points to display on the chart.
 */
@Composable
fun PriceLineChart(historicalPrices: List<PricePoint>) {
    // Convert PricePoint list to MPAndroidChart Entry list.
    // The x-value for Entry will be the index, and y-value will be the price.
    // The actual timestamp will be used for X-axis labels via a ValueFormatter.
    val entries = remember(historicalPrices) {
        historicalPrices.mapIndexed { index, pricePoint ->
            Entry(index.toFloat(), pricePoint.price)
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false // Disable chart description
                setTouchEnabled(true)         // Enable touch gestures
                setPinchZoom(true)            // Enable pinch zoom
                setDrawGridBackground(false)  // Do not draw a background grid

                // X-Axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM // Position X-axis at the bottom
                    setDrawGridLines(false)               // Do not draw grid lines for X-axis
                    setDrawAxisLine(true)                 // Draw the X-axis line
                    textColor = Color.BLACK
                    // Custom formatter for X-axis labels to show date/time
                    valueFormatter = object : ValueFormatter() {
                        private val dateFormat = SimpleDateFormat("HH:mm\ndd/MM", Locale.getDefault())
                        override fun getAxisLabel(value: Float, axis: XAxis?): String {
                            // Map the chart's float index back to the original timestamp
                            // This assumes entries are ordered by time and index corresponds to position in historicalPrices
                            if (value.toInt() >= 0 && value.toInt() < historicalPrices.size) {
                                val timestamp = historicalPrices[value.toInt()].timestamp
                                return dateFormat.format(Date(timestamp))
                            }
                            return ""
                        }
                    }
                    labelRotationAngle = -45f // Rotate labels for better readability
                    granularity = 1f          // Only show integer indices (one label per data point)
                }

                // Left Y-Axis configuration
                axisLeft.apply {
                    setDrawGridLines(true) // Draw grid lines for Y-axis
                    setDrawAxisLine(true)  // Draw the Y-axis line
                    textColor = Color.BLACK
                    // Custom formatter for Y-axis labels to show price with 2 decimal places
                    valueFormatter = object : ValueFormatter() {
                        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.YAxis?): String {
                            return "%.2f".format(value)
                        }
                    }
                }

                // Right Y-Axis configuration (disable it)
                axisRight.isEnabled = false

                // Legend configuration
                legend.apply {
                    form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE
                    textColor = Color.BLACK
                }

                // Animation for chart loading
                animateX(750) // Animate X-axis for 750 milliseconds
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 9f
                    valueTextColor = Color.BLACK
                    mode = LineDataSet.Mode.LINEAR // or CUBIC_BEZIER for smoother lines
                    setDrawValues(false) // Do not draw individual value labels on the chart
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh the chart to display new data
            } else {
                chart.clear() // Clear the chart if no data is available
            }
        }
    )
}

// Preview Composable for Android Studio
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AIAgentTheme {
        // Create a dummy repository and ViewModel for preview purposes
        val dummyRepository = object : PriceRepository(object : BinanceApiService {
            override suspend fun getKlines(symbol: String, interval: String, limit: Int): List<List<String>> {
                // Provide dummy data for preview
                val now = System.currentTimeMillis()
                return (0 until 10).map { i ->
                    val timestamp = now - (10 - i) * 3600 * 1000L // 1 hour intervals
                    val price = 2000f + (i * 5f) + (Math.random() - 0.5).toFloat() * 10f
                    listOf(
                        timestamp.toString(), "%.2f".format(price), "%.2f".format(price + 10),
                        "%.2f".format(price - 10), "%.2f".format(price), "100",
                        (timestamp + 3600 * 1000L).toString(), "1000", "10", "50", "50", "ignore"
                    )
                }
            }
        }) {}
        val dummyViewModel = GoldPriceViewModel(dummyRepository)

        // Manually set some dummy data for the preview to show the chart and current price
        LaunchedEffect(Unit) {
            dummyViewModel._isLoading.value = false
            dummyViewModel._errorMessage.value = null
            val now = System.currentTimeMillis()
            val dummyPrices = (0 until 10).map { i ->
                PricePoint(
                    timestamp = now - (10 - i) * 3600 * 1000L, // 1 hour intervals
                    price = 2000f + (i * 5f) + (Math.random() - 0.5).toFloat() * 10f
                )
            }
            dummyViewModel._historicalPrices.value = dummyPrices
            dummyViewModel._currentPrice.value = dummyPrices.lastOrNull()?.price
        }
        GoldPriceTrackerScreen(viewModel = dummyViewModel)
    }
}