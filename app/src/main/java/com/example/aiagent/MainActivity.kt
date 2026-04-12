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
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// --- Data Models ---

// Binance Klines API response structure
// Example: [1499040000000, "0.01634790", "0.80000000", "0.01575600", "0.01577100", "148.87976595", 1499644799999, "2434.19055334", 308, "1756.87402397", "1000.00000000", "0"]
// We are interested in:
// 0: Open time (Long)
// 4: Close price (String)
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
) {
    // Custom deserializer for the list of Any type
    companion object {
        fun fromList(list: List<Any>): BinanceKline {
            return BinanceKline(
                openTime = (list[0] as? Long) ?: (list[0] as? Int)?.toLong() ?: 0L,
                openPrice = list[1] as String,
                highPrice = list[2] as String,
                lowPrice = list[3] as String,
                closePrice = list[4] as String,
                volume = list[5] as String,
                closeTime = (list[6] as? Long) ?: (list[6] as? Int)?.toLong() ?: 0L,
                quoteAssetVolume = list[7] as String,
                numberOfTrades = (list[8] as? Long) ?: (list[8] as? Int)?.toLong() ?: 0L,
                takerBuyBaseAssetVolume = list[9] as String,
                takerBuyQuoteAssetVolume = list[10] as String,
                ignore = list[11] as String
            )
        }
    }
}

data class PricePoint(
    val timestamp: Long, // Milliseconds
    val price: Float
)

// --- Network Service ---

class BinanceApiService {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    // Fetches klines (candlestick data) for a given symbol and interval
    // interval: 1m, 5m, 1h, 1d, etc.
    // limit: Number of data points to fetch
    suspend fun getKlines(symbol: String, interval: String, limit: Int): List<BinanceKline> {
        return try {
            val response: List<List<Any>> = client.get("https://api.binance.com/api/v3/klines") {
                url {
                    parameters.append("symbol", symbol)
                    parameters.append("interval", interval)
                    parameters.append("limit", limit.toString())
                }
            }.body()
            response.map { BinanceKline.fromList(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

// --- ViewModel ---

class GoldPriceViewModel(private val apiService: BinanceApiService) : ViewModel() {

    private val _currentPrice = MutableStateFlow<String>("N/A")
    val currentPrice: StateFlow<String> = _currentPrice.asStateFlow()

    private val _priceHistory = MutableStateFlow<List<PricePoint>>(emptyList())
    val priceHistory: StateFlow<List<PricePoint>> = _priceHistory.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val SYMBOL = "PAXGUSDT"
    private val INTERVAL = "1h" // 1-hour interval
    private val CHART_LIMIT = 24 * 7 // 7 days of 1-hour data

    init {
        fetchGoldPriceData()
    }

    fun fetchGoldPriceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val klines = apiService.getKlines(SYMBOL, INTERVAL, CHART_LIMIT)
                if (klines.isNotEmpty()) {
                    val latestKline = klines.last()
                    _currentPrice.value = String.format("%.2f", latestKline.closePrice.toFloat())

                    val history = klines.map {
                        PricePoint(
                            timestamp = it.openTime,
                            price = it.closePrice.toFloat()
                        )
                    }
                    _priceHistory.value = history
                } else {
                    _errorMessage.value = "No data received from Binance."
                    _currentPrice.value = "N/A"
                    _priceHistory.value = emptyList()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch data: ${e.localizedMessage}"
                _currentPrice.value = "Error"
                _priceHistory.value = emptyList()
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Factory for ViewModel with custom constructor
    class Factory(private val apiService: BinanceApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GoldPriceViewModel::class.java)) {
                return GoldPriceViewModel(apiService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// --- MainActivity ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAgentTheme {
                val apiService = remember { BinanceApiService() }
                val viewModel: GoldPriceViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = GoldPriceViewModel.Factory(apiService)
                    )
                GoldPriceTrackerScreen(viewModel = viewModel)
            }
        }
    }
}

// --- Composables ---

@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT 金價追蹤") })
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
            Text(
                text = "當前 PAXGUSDT 價格:",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "$currentPrice USDT",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (errorMessage != null) {
                Text(
                    text = "錯誤: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = { viewModel.fetchGoldPriceData() }) {
                    Text("重試")
                }
            } else if (priceHistory.isNotEmpty()) {
                Text(
                    text = "過去 7 天價格走勢 (1小時K線)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                PriceLineChart(priceHistory = priceHistory)
            } else {
                Text(
                    text = "沒有可用的歷史數據。",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun PriceLineChart(priceHistory: List<PricePoint>) {
    val context = LocalContext.current
    val entries = remember(priceHistory) {
        priceHistory.mapIndexed { index, pricePoint ->
            Entry(index.toFloat(), pricePoint.price)
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    setDrawAxisLine(true)
                    textColor = Color.GRAY
                    valueFormatter = object : ValueFormatter() {
                        private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            val index = value.roundToInt()
                            return if (index >= 0 && index < priceHistory.size) {
                                dateFormat.format(Date(priceHistory[index].timestamp))
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
                    textColor = Color.GRAY
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.2f", value)
                        }
                    }
                }

                // Right Y-axis configuration (disable)
                axisRight.isEnabled = false

                // Legend configuration
                legend.apply {
                    form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
                    textColor = Color.GRAY
                }

                // Initial data setup (can be empty)
                data = LineData()
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT 價格").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on points
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                    setDrawFilled(true) // Fill area below the line
                    fillColor = Color.BLUE
                    fillAlpha = 50
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh the chart
                chart.animateX(1000) // Animate chart on X-axis
            } else {
                chart.clear() // Clear chart if no data
                chart.invalidate()
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerScreenPreview() {
    AIAgentTheme {
        // Create a dummy ViewModel for preview
        val dummyApiService = object : BinanceApiService() {
            override suspend fun getKlines(symbol: String, interval: String, limit: Int): List<BinanceKline> {
                // Return some dummy data for preview
                val now = System.currentTimeMillis()
                return (0 until limit).map { i ->
                    val timestamp = now - (limit - 1 - i) * 3600 * 1000L // 1 hour interval
                    val price = 2000f + (i % 20) * 0.5f + (i % 10) * -0.2f
                    BinanceKline(
                        openTime = timestamp,
                        openPrice = price.toString(),
                        highPrice = (price + 1).toString(),
                        lowPrice = (price - 1).toString(),
                        closePrice = String.format("%.2f", price),
                        volume = "100",
                        closeTime = timestamp + 3600 * 1000L - 1,
                        quoteAssetVolume = "200000",
                        numberOfTrades = 100,
                        takerBuyBaseAssetVolume = "50",
                        takerBuyQuoteAssetVolume = "100000",
                        ignore = "0"
                    )
                }
            }
        }
        val dummyViewModel = GoldPriceViewModel(dummyApiService)
        // Manually set some state for preview
        LaunchedEffect(Unit) {
            dummyViewModel._currentPrice.value = "2050.75"
            val history = (0..24).map { i ->
                PricePoint(
                    timestamp = System.currentTimeMillis() - (24 - i) * 3600 * 1000L,
                    price = 2000f + (i * 5).toFloat() / 24f + (i % 5) * 0.5f
                )
            }
            dummyViewModel._priceHistory.value = history
            dummyViewModel._isLoading.value = false
            dummyViewModel._errorMessage.value = null
        }
        GoldPriceTrackerScreen(viewModel = dummyViewModel)
    }
}