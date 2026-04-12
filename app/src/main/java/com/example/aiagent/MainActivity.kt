package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.* // Import for @Composable, remember, mutableStateOf, State, by
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview // Import for @Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel // Import for viewModel() in Composable functions
import com.example.aiagent.ui.theme.AIAgentTheme // Correct theme import
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor // Import for HttpLoggingInterceptor
import retrofit2.Retrofit // Import for Retrofit
import retrofit2.converter.gson.GsonConverterFactory // Import for GsonConverterFactory
import retrofit2.http.GET // Import for GET annotation
import retrofit2.http.Query // Import for Query annotation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Define Data Models
data class ExchangeInfoSymbol(
    val symbol: String,
    val status: String,
    val baseAsset: String,
    val quoteAsset: String
)

data class ExchangeInfo(
    val timezone: String,
    val serverTime: Long,
    val rateLimits: List<Any>, // Simplified for this example
    val exchangeFilters: List<Any>, // Simplified
    val symbols: List<ExchangeInfoSymbol>
)

data class TickerPrice(
    val symbol: String,
    val price: String
)

// Kline data model
// Example: [ [1499040000000, "0.0010", "0.0012", "0.0009", "0.0011", "100"], ... ]
// timestamp, open, high, low, close, volume, ... (we only care about timestamp and close price here)
typealias KlineData = List<Any>

data class GoldPrice(val timestamp: Long, val price: Double)

// Retrofit API Interface
interface BinanceApiService {
    @GET("api/v3/exchangeInfo")
    suspend fun getExchangeInfo(): ExchangeInfo

    @GET("api/v3/ticker/price")
    suspend fun getTickerPrice(@Query("symbol") symbol: String): TickerPrice

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int? = null
    ): List<KlineData>
}

// Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    // Create a logging interceptor for debugging network requests
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Set log level to BODY to see request and response body
    }

    // Create an OkHttpClient with the logging interceptor
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // Create the Retrofit instance
    val instance: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Add the OkHttpClient
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApiService::class.java)
    }
}

// ViewModel
class GoldPriceViewModel : ViewModel() {
    private val _currentPrice = mutableStateOf("Fetching...")
    // Publicly expose as State to allow observation in Composable, but prevent direct modification from UI
    val currentPrice: State<String> = _currentPrice 

    private val _historicalPrices = mutableStateOf<List<GoldPrice>>(emptyList())
    // Publicly expose as State
    val historicalPrices: State<List<GoldPrice>> = _historicalPrices 

    private val symbol = "PAXGUSDT"

    init {
        fetchGoldPrice()
        fetchHistoricalPrices()
    }

    fun fetchGoldPrice() {
        viewModelScope.launch {
            try {
                val tickerPrice = RetrofitClient.instance.getTickerPrice(symbol)
                _currentPrice.value = "Current Price: ${tickerPrice.price} USDT"
            } catch (e: Exception) {
                _currentPrice.value = "Error: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    fun fetchHistoricalPrices() {
        viewModelScope.launch {
            try {
                // Fetch klines for 24 hours (1 day) using 1-hour interval, limit 24
                val klines = RetrofitClient.instance.getKlines(symbol, "1h", 24)
                val prices = klines.mapNotNull { kline ->
                    // Kline format: [timestamp, open, high, low, close, volume, ...]
                    // timestamp is Long, close is String
                    val timestamp = kline[0] as? Long
                    val closePriceStr = kline[4] as? String
                    if (timestamp != null && closePriceStr != null) {
                        try {
                            GoldPrice(timestamp, closePriceStr.toDouble())
                        } catch (e: NumberFormatException) {
                            null // Skip if price can't be parsed
                        }
                    } else {
                        null // Skip if data is malformed
                    }
                }
                _historicalPrices.value = prices
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error
            }
        }
    }
}

// Composable Functions
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAgentTheme { // Resolved: AIAgentTheme import and usage
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use viewModel() to get an instance of GoldPriceViewModel, correctly imported
                    val goldPriceViewModel: GoldPriceViewModel = viewModel()
                    // GoldPriceTrackerScreen is a @Composable function, correctly invoked within a @Composable context
                    GoldPriceTrackerScreen(viewModel = goldPriceViewModel) 
                }
            }
        }
    }
}

@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    // Observe the State objects directly using `by` delegate
    val currentPrice by viewModel.currentPrice 
    val historicalPrices by viewModel.historicalPrices 

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "PAXG/USDT Price Tracker", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = currentPrice, style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        if (historicalPrices.isNotEmpty()) {
            Text(text = "Last 24 Hours Price Trend", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            // Prepare chart entries
            val entries = historicalPrices.mapIndexed { index, goldPrice ->
                FloatEntry(index.toFloat(), goldPrice.price.toFloat())
            }
            val model = entryModelOf(entries)

            // Format timestamp for x-axis labels
            val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val xLabels = remember(historicalPrices) {
                historicalPrices.map { goldPrice ->
                    dateFormat.format(Date(goldPrice.timestamp))
                }
            }

            Chart(
                chart = lineChart(),
                model = model,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        xLabels.getOrNull(value.toInt()) ?: ""
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        } else {
            CircularProgressIndicator()
            Text("Loading historical data...")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { viewModel.fetchGoldPrice() }) {
            Text("Refresh Current Price")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.fetchHistoricalPrices() }) {
            Text("Refresh Historical Prices")
        }
    }
}

@Preview(showBackground = true) // Resolved: Preview import and usage
@Composable
fun DefaultPreview() {
    AIAgentTheme { // Resolved: AIAgentTheme import and usage
        // For preview, we can mock the ViewModel state by creating an anonymous object that overrides properties
        val mockViewModel = object : GoldPriceViewModel() {
            // Provide mock data for public State properties
            override val currentPrice: State<String> = mutableStateOf("Current Price: 2000.00 USDT (Mock)")
            override val historicalPrices: State<List<GoldPrice>> = mutableStateOf(
                listOf(
                    GoldPrice(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(23), 1950.0),
                    GoldPrice(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(18), 1980.0),
                    GoldPrice(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12), 2020.0),
                    GoldPrice(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6), 2010.0),
                    GoldPrice(System.currentTimeMillis(), 2000.0)
                )
            )
            // Override fetch methods to do nothing or simulate, to prevent actual network calls in preview
            override fun fetchGoldPrice() { /* do nothing for preview */ }
            override fun fetchHistoricalPrices() { /* do nothing for preview */ }
        }
        // GoldPriceTrackerScreen is a @Composable function, correctly invoked within a @Composable context
        GoldPriceTrackerScreen(viewModel = mockViewModel)
    }
}