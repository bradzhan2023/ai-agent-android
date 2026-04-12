package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
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
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// 1. Data Models
@Serializable
data class BinanceKline(
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

@Serializable
data class BinanceTicker(
    @SerialName("symbol") val symbol: String,
    @SerialName("priceChange") val priceChange: String,
    @SerialName("priceChangePercent") val priceChangePercent: String,
    @SerialName("weightedAvgPrice") val weightedAvgPrice: String,
    @SerialName("prevClosePrice") val prevClosePrice: String,
    @SerialName("lastPrice") val lastPrice: String,
    @SerialName("lastQty") val lastQty: String,
    @SerialName("bidPrice") val bidPrice: String,
    @SerialName("bidQty") val bidQty: String,
    @SerialName("askPrice") val askPrice: String,
    @SerialName("askQty") val askQty: String,
    @SerialName("openPrice") val openPrice: String,
    @SerialName("highPrice") val highPrice: String,
    @SerialName("lowPrice") val lowPrice: String,
    @SerialName("volume") val volume: String,
    @SerialName("quoteVolume") val quoteVolume: String,
    @SerialName("openTime") val openTime: Long,
    @SerialName("closeTime") val closeTime: Long,
    @SerialName("firstId") val firstId: Long,
    @SerialName("lastId") val lastId: Long,
    @SerialName("count") val count: Long
)

// 2. Retrofit Interface
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<String>> // Binance returns klines as List<List<String>>

    @GET("api/v3/ticker/24hr")
    suspend fun get24hrTicker(@Query("symbol") symbol: String): BinanceTicker
}

// 3. Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val api: BinanceApiService by lazy {
        retrofit.create(BinanceApiService::class.java)
    }
}

// 4. ViewModel
class MainViewModel : ViewModel() {
    private val _currentPrice = MutableStateFlow<String?>("Loading...")
    open val currentPrice: StateFlow<String?> = _currentPrice.asStateFlow()

    private val _klineData = MutableStateFlow<List<BinanceKline>>(emptyList())
    open val klineData: StateFlow<List<BinanceKline>> = _klineData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    open val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    open val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val SYMBOL = "PAXGUSDT"
    private val KLINE_INTERVAL = "1h" // 1-hour interval
    private val KLINE_LIMIT = 24 * 7 // 7 days of 1-hour data

    init {
        fetchPriceData()
        // Start a coroutine to periodically refresh data
        viewModelScope.launch {
            while (true) {
                delay(60_000) // Refresh every 1 minute
                fetchPriceData()
            }
        }
    }

    open fun fetchPriceData() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                // Fetch current price
                val ticker = RetrofitClient.api.get24hrTicker(SYMBOL)
                _currentPrice.value = ticker.lastPrice

                // Fetch kline data
                val rawKlines = RetrofitClient.api.getKlines(SYMBOL, KLINE_INTERVAL, KLINE_LIMIT)
                val parsedKlines = rawKlines.map { kline ->
                    BinanceKline(
                        openTime = kline[0].toLong(),
                        openPrice = kline[1],
                        highPrice = kline[2],
                        lowPrice = kline[3],
                        closePrice = kline[4],
                        volume = kline[5],
                        closeTime = kline[6].toLong(),
                        quoteAssetVolume = kline[7],
                        numberOfTrades = kline[8].toLong(),
                        takerBuyBaseAssetVolume = kline[9],
                        takerBuyQuoteAssetVolume = kline[10],
                        ignore = kline[11]
                    )
                }
                _klineData.value = parsedKlines.sortedBy { it.openTime } // Ensure chronological order

            } catch (e: IOException) {
                _errorMessage.value = "Network error: ${e.message}"
                Log.e("MainViewModel", "Network error", e)
            } catch (e: HttpException) {
                _errorMessage.value = "Server error: ${e.code()}"
                Log.e("MainViewModel", "HTTP error", e)
            } catch (e: Exception) {
                _errorMessage.value = "An unexpected error occurred: ${e.message}"
                Log.e("MainViewModel", "Unexpected error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// 5. MainActivity
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
                    val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    PriceTrackerScreen(viewModel)
                }
            }
        }
    }
}

// 6. Composable Functions
@Composable
fun PriceTrackerScreen(viewModel: MainViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val klineData by viewModel.klineData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PAXGUSDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        CurrentPriceDisplay(currentPrice)

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Text("Loading data...", modifier = Modifier.padding(top = 8.dp))
        } else if (errorMessage != null) {
            Text(
                text = "Error: $errorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(onClick = { viewModel.fetchPriceData() }) {
                Text("Retry")
            }
        } else if (klineData.isNotEmpty()) {
            PriceLineChart(klineData)
        } else {
            Text("No data available. Pull to refresh or check connection.", modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun CurrentPriceDisplay(price: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current PAXGUSDT Price:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = price ?: "N/A",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun PriceLineChart(klineData: List<BinanceKline>) {
    val context = LocalContext.current
    val entries = remember(klineData) {
        klineData.mapIndexed { index, kline ->
            Entry(index.toFloat(), kline.closePrice.toFloat())
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                    valueFormatter = object : ValueFormatter() {
                        private val mFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            // Map chart index back to original kline timestamp
                            val klineIndex = value.toInt()
                            return if (klineIndex >= 0 && klineIndex < klineData.size) {
                                mFormat.format(Date(klineData[klineIndex].openTime))
                            } else {
                                ""
                            }
                        }
                    }
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(4, true) // Show approximately 4 labels, force exact count
                }

                // Left Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                    axisMinimum = entries.minOfOrNull { it.y }?.minus(10f) ?: 0f // Add some padding
                    axisMaximum = entries.maxOfOrNull { it.y }?.plus(10f) ?: 10000f // Add some padding
                }

                // Right Y-axis configuration (disable)
                axisRight.isEnabled = false

                legend.isEnabled = false // Disable legend
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
                    valueTextSize = 0f // Hide value text on points
                    setDrawFilled(true)
                    fillColor = Color.BLUE
                    fillAlpha = 50
                    mode = LineDataSet.Mode.LINEAR
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh chart
                chart.animateX(1000) // Animate chart
            } else {
                chart.clear()
                chart.setNoDataText("No chart data available.")
                chart.invalidate()
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PriceTrackerScreenPreview() {
    AIAgentTheme {
        // Create a mock ViewModel for preview purposes
        val mockViewModel = object : MainViewModel() {
            override val currentPrice: StateFlow<String?> = MutableStateFlow("2300.50").asStateFlow()
            override val klineData: StateFlow<List<BinanceKline>> = MutableStateFlow(
                listOf(
                    BinanceKline(1678886400000, "2200", "2210", "2190", "2205", "100", 1678890000000, "", 0, "", "", ""),
                    BinanceKline(1678890000000, "2205", "2215", "2200", "2212", "120", 1678893600000, "", 0, "", "", ""),
                    BinanceKline(1678893600000, "2212", "2220", "2208", "2218", "110", 1678897200000, "", 0, "", "", ""),
                    BinanceKline(1678897200000, "2218", "2225", "2215", "2222", "130", 1678900800000, "", 0, "", "", ""),
                    BinanceKline(1678900800000, "2222", "2230", "2218", "2228", "140", 1678904400000, "", 0, "", "", "")
                )
            ).asStateFlow()
            override val isLoading: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
            override val errorMessage: StateFlow<String?> = MutableStateFlow(null).asStateFlow()

            override fun fetchPriceData() {
                // Do nothing for preview
            }
        }
        PriceTrackerScreen(mockViewModel)
    }
}

@Preview(showBackground = true)
@Composable
fun CurrentPriceDisplayPreview() {
    AIAgentTheme {
        CurrentPriceDisplay("2300.50")
    }
}

@Preview(showBackground = true)
@Composable
fun PriceLineChartPreview() {
    AIAgentTheme {
        val mockKlineData = listOf(
            BinanceKline(1678886400000, "2200", "2210", "2190", "2205", "100", 1678890000000, "", 0, "", "", ""),
            BinanceKline(1678890000000, "2205", "2215", "2200", "2212", "120", 1678893600000, "", 0, "", "", ""),
            BinanceKline(1678893600000, "2212", "2220", "2208", "2218", "110", 1678897200000, "", 0, "", "", ""),
            BinanceKline(1678897200000, "2218", "2225", "2215", "2222", "130", 1678900800000, "", 0, "", "", ""),
            BinanceKline(1678900800000, "2222", "2230", "2218", "2228", "140", 1678904400000, "", 0, "", "", "")
        )
        PriceLineChart(mockKlineData)
    }
}