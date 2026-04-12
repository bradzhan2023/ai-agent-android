package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.ui.theme.AIAgentTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import patrykandpatrick.vico.compose.axis.current.rememberBottomAxis
import patrykandpatrick.vico.compose.axis.current.rememberStartAxis
import patrykandpatrick.vico.compose.chart.Chart
import patrykandpatrick.vico.compose.chart.line.lineChart
import patrykandpatrick.vico.core.entry.FloatEntry
import patrykandpatrick.vico.core.entry.entryModelOf
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAgentTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Correctly instantiate the ViewModel using viewModel() composable
                    val goldPriceViewModel: GoldPriceViewModel = viewModel(factory = GoldPriceViewModelFactory(RetrofitClient.binanceApiService))
                    GoldPriceTrackerScreen(goldPriceViewModel)
                }
            }
        }
    }
}

// Data models
data class BinanceTickerResponse(
    val lastPrice: String
)

// API Service Interface
interface BinanceApiService {
    @GET("api/v3/ticker/price")
    suspend fun getCurrentPrice(@Query("symbol") symbol: String): BinanceTickerResponse

    // Klines endpoint for historical data
    @GET("api/v3/klines")
    suspend fun getHistoricalPrices(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String, // e.g., "1d" for 1 day
        @Query("limit") limit: Int // number of data points
    ): List<List<String>> // Binance returns a list of lists for klines
}

// Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS) // Set connection timeout
        .readTimeout(30, TimeUnit.SECONDS)    // Set read timeout
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val binanceApiService: BinanceApiService by lazy {
        retrofit.create(BinanceApiService::class.java)
    }
}

// ViewModel
class GoldPriceViewModel(private val apiService: BinanceApiService) : ViewModel() {

    private val _currentPrice = MutableStateFlow("Loading...")
    val currentPrice: StateFlow<String> = _currentPrice

    private val _historicalPrices = MutableStateFlow<List<FloatEntry>>(emptyList())
    val historicalPrices: StateFlow<List<FloatEntry>> = _historicalPrices

    init {
        fetchGoldPrice()
        fetchHistoricalPrices()
    }

    fun fetchGoldPrice() {
        viewModelScope.launch {
            try {
                val response = apiService.getCurrentPrice("PAXGUSDT")
                _currentPrice.value = "$${String.format(Locale.US, "%.2f", response.lastPrice.toFloat())}"
            } catch (e: Exception) {
                _currentPrice.value = "Error: ${e.localizedMessage}"
                e.printStackTrace()
            }
        }
    }

    fun fetchHistoricalPrices() {
        viewModelScope.launch {
            try {
                // Fetch daily klines for the last 30 days
                val response = apiService.getHistoricalPrices("PAXGUSDT", "1d", 30)
                val entries = response.mapIndexed { index, candle ->
                    // Binance kline response: [openTime, open, high, low, close, volume, closeTime, ...]
                    // Close price is at index 4
                    FloatEntry(index.toFloat(), candle[4].toFloat())
                }
                _historicalPrices.value = entries
            } catch (e: Exception) {
                e.printStackTrace()
                _historicalPrices.value = emptyList() // Clear or show error state
            }
        }
    }
}

// ViewModel Factory
class GoldPriceViewModelFactory(private val apiService: BinanceApiService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoldPriceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GoldPriceViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Composable functions
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "PAXG/USDT Price Tracker", style = MaterialTheme.typography.headlineLarge)

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Current Price:", style = MaterialTheme.typography.headlineMedium)
                Text(text = currentPrice, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (historicalPrices.isNotEmpty()) {
            Text(text = "Historical Prices (Last 30 Days)", style = MaterialTheme.typography.headlineSmall)
            Chart(
                chart = lineChart(),
                model = entryModelOf(historicalPrices),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        // Format the x-axis label (index 0 to 29 for 30 days)
                        // This assumes `value` is the float index from `FloatEntry`
                        // +1 to start from Day 1
                        "Day ${(value + 1).toInt()}"
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        } else {
            // Show a loading indicator if historical data is not yet available
            CircularProgressIndicator(modifier = Modifier.align(alignment = androidx.compose.ui.Alignment.CenterHorizontally))
            Text(text = "Loading historical data...", modifier = Modifier.align(alignment = androidx.compose.ui.Alignment.CenterHorizontally))
        }
    }
}

// Preview function
@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerScreenPreview() {
    AIAgentTheme {
        // Create a mock ApiService for preview purposes
        val mockApiService = object : BinanceApiService {
            override suspend fun getCurrentPrice(symbol: String): BinanceTickerResponse {
                return BinanceTickerResponse("2350.75")
            }

            override suspend fun getHistoricalPrices(
                symbol: String,
                interval: String,
                limit: Int
            ): List<List<String>> {
                // Generate some dummy historical data
                return List(30) { index ->
                    val price = 2300f + (index * 5f) + (Math.random() * 10 - 5).toFloat()
                    listOf("0", "0", "0", "0", String.format(Locale.US, "%.2f", price), "0", "0", "0", "0", "0", "0", "0")
                }
            }
        }

        // Create a GoldPriceViewModel instance using the mock service
        // and manually set its StateFlows for an immediate preview state.
        val mockViewModel = GoldPriceViewModel(mockApiService).apply {
            // Manually set flow values for instant preview, as LaunchedEffect won't run in preview
            viewModelScope.launch { // Launch in a dummy scope for preview
                (_currentPrice as MutableStateFlow).value = "$2350.75"
                (_historicalPrices as MutableStateFlow).value = List(30) { index ->
                    FloatEntry(index.toFloat(), 2300f + (index * 5f) + (Math.random() * 10 - 5).toFloat())
                }
            }
        }
        GoldPriceTrackerScreen(mockViewModel)
    }
}