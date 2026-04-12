package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.ui.theme.AIAgentTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import kotlin.time.Duration.Companion.seconds
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use a ViewModel for managing UI-related data and lifecycle
                    val viewModel: GoldPriceViewModel = viewModel()
                    GoldPriceApp(viewModel)
                }
            }
        }
    }
}

// Binance API Interface
interface BinanceApiService {
    @GET("api/v3/ticker/price")
    suspend fun getCurrentPrice(@Query("symbol") symbol: String): BinancePriceResponse

    @GET("api/v3/klines")
    suspend fun getHistoricalPrices(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<Any>> // Klines return a list of lists, we'll parse it
}

// Data Models
data class BinancePriceResponse(val symbol: String, val price: String)

// Retrofit Client Setup
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    // Interceptor for logging network requests and responses
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // Retrofit instance, lazily initialized
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // Use Gson for JSON serialization/deserialization
            .build()
    }

    // BinanceApiService instance, lazily initialized
    val binanceService: BinanceApiService by lazy {
        retrofit.create(BinanceApiService::class.java)
    }
}

// ViewModel for fetching and holding price data
class GoldPriceViewModel : ViewModel() {
    // Current PAXG price as a StateFlow for UI observation
    private val _currentPrice = MutableStateFlow("Fetching...")
    val currentPrice: StateFlow<String> = _currentPrice.asStateFlow()

    // Historical PAXG prices as a StateFlow for UI observation (for the chart)
    private val _historicalPrices = MutableStateFlow<List<FloatEntry>>(emptyList())
    val historicalPrices: StateFlow<List<FloatEntry>> = _historicalPrices.asStateFlow()

    init {
        fetchPrices() // Start fetching prices when the ViewModel is created
    }

    private fun fetchPrices() {
        viewModelScope.launch {
            while (true) {
                try {
                    // Fetch current price for PAXGUSDT
                    val currentPriceResponse = RetrofitClient.binanceService.getCurrentPrice("PAXGUSDT")
                    _currentPrice.value = String.format("%.2f", currentPriceResponse.price.toFloat())

                    // Fetch historical prices (e.g., last 24 hours, 1-hour interval)
                    // klines return: [open time, open, high, low, close, volume, close time, quote asset volume, number of trades, taker buy base asset volume, taker buy quote asset volume, ignore]
                    val klines = RetrofitClient.binanceService.getHistoricalPrices("PAXGUSDT", "1h", 24)
                    val entries = klines.mapIndexed { index, kline ->
                        // Assuming close price is at index 4 and is a String
                        FloatEntry(index.toFloat(), (kline[4] as String).toFloat())
                    }
                    _historicalPrices.value = entries
                } catch (e: Exception) {
                    _currentPrice.value = "Error: ${e.message}"
                    e.printStackTrace()
                }
                delay(30.seconds) // Update every 30 seconds
            }
        }
    }
}

// Composable function for the main UI of the gold price tracker
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceApp(viewModel: GoldPriceViewModel) {
    // Collect StateFlows as Compose State
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("PAXG/USDT Tracker") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Current PAXG Price: $currentPrice USDT",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Historical Data (Last 24 hours - 1h interval)",
                style = MaterialTheme.typography.titleLarge
            )

            // Display the line chart if historical data is available
            if (historicalPrices.isNotEmpty()) {
                Chart(
                    chart = lineChart(),
                    model = entryModelOf(*historicalPrices.toTypedArray()), // Use spread operator for vararg
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            // Format x-axis labels. For 24 entries (1h interval), show hours ago.
                            // This is a simplified formatter; a real app might map to actual timestamps.
                            val klineIndex = value.toInt()
                            val totalHours = historicalPrices.size
                            if (totalHours > 0) {
                                // Display label for every 4th hour for clarity
                                if ((totalHours - 1 - klineIndex) % 4 == 0) {
                                    "${totalHours - 1 - klineIndex}h ago"
                                } else ""
                            } else ""
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            } else {
                Text("Loading historical data...", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// Preview function for the GoldPriceApp Composable
@Preview(showBackground = true)
@Composable
fun GoldPriceAppPreview() {
    AIAgentTheme {
        // Create a mock ViewModel for preview purposes
        val mockViewModel = object : GoldPriceViewModel() {
            // Override the StateFlows with mock data
            override val currentPrice: StateFlow<String> = MutableStateFlow("1850.50").asStateFlow()
            override val historicalPrices: StateFlow<List<FloatEntry>> = MutableStateFlow(
                listOf(
                    FloatEntry(0f, 1800f),
                    FloatEntry(1f, 1810f),
                    FloatEntry(2f, 1825f),
                    FloatEntry(3f, 1820f),
                    FloatEntry(4f, 1835f),
                    FloatEntry(5f, 1840f),
                    FloatEntry(6f, 1850f),
                    FloatEntry(7f, 1845f),
                    FloatEntry(8f, 1855f),
                    FloatEntry(9f, 1860f),
                    FloatEntry(10f, 1850f),
                    FloatEntry(11f, 1852f),
                    FloatEntry(12f, 1858f),
                    FloatEntry(13f, 1865f),
                    FloatEntry(14f, 1870f),
                    FloatEntry(15f, 1860f),
                    FloatEntry(16f, 1855f),
                    FloatEntry(17f, 1862f),
                    FloatEntry(18f, 1868f),
                    FloatEntry(19f, 1875f),
                    FloatEntry(20f, 1880f),
                    FloatEntry(21f, 1870f),
                    FloatEntry(22f, 1865f),
                    FloatEntry(23f, 1872f)
                )
            ).asStateFlow()
        }
        GoldPriceApp(mockViewModel)
    }
}