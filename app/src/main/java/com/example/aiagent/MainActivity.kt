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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.example.aiagent.ui.theme.AIAgentTheme // Assuming a theme file exists in ui.theme package
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Data Models
// Binance Klines API response structure:
// Example: [[1499040000000,"0.01634790","0.80000000","0.01575600","0.01577100","148.87948700","1499644799999","2431.90654791","308","175.60000000","2.74747145","1780.00000000"]]
// We are interested in:
// 0: Open time (Long)
// 4: Close price (String)
// The API returns a List<List<Any>> where Any can be String or Long.
// We will fetch it as List<List<String>> and then manually parse the types.
@Serializable
data class KlineData(
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

// Retrofit Interface
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<String>> // Binance Klines API returns a list of lists of strings/numbers
}

// Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true // Allow unquoted strings, etc.
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // Use kotlinx.serialization converter
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val api: BinanceApiService by lazy {
        retrofit.create(BinanceApiService::class.java)
    }
}

// ViewModel
class MainViewModel(private val binanceApiService: BinanceApiService) : ViewModel() {

    private val _currentPrice = MutableStateFlow<String>("N/A")
    val currentPrice: StateFlow<String> = _currentPrice

    private val _klineData = MutableStateFlow<List<KlineData>>(emptyList())
    val klineData: StateFlow<List<KlineData>> = _klineData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchPriceData()
    }

    fun fetchPriceData() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val response = binanceApiService.getKlines(
                    symbol = "PAXGUSDT",
                    interval = "1h", // 1-hour interval
                    limit = 100 // Get last 100 data points
                )

                // Manually parse the List<List<String>> into KlineData
                val parsedData = response.mapNotNull { kline ->
                    if (kline.size >= 12) { // Ensure enough elements
                        try {
                            KlineData(
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
                        } catch (e: Exception) {
                            // Log error or handle malformed kline entry
                            // println("Error parsing kline entry: $kline, Error: $e")
                            null
                        }
                    } else {
                        null
                    }
                }

                _klineData.value = parsedData
                _currentPrice.value = parsedData.lastOrNull()?.closePrice ?: "N/A"

            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch data: ${e.localizedMessage}"
                _currentPrice.value = "Error"
                _klineData.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// ViewModel Factory
class MainViewModelFactory(private val binanceApiService: BinanceApiService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(binanceApiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAgentTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(RetrofitClient.api)
                )
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

// Composable Functions
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val klineData by viewModel.klineData.collectAsState()
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
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Button(
                onClick = { viewModel.fetchPriceData() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = !isLoading
            ) {
                Text("重新整理")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PAXGUSDT 歷史價格 (1小時K線)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            PriceChart(
                klineData = klineData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }
    }
}

@Composable
fun PriceChart(klineData: List<KlineData>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val chart = remember { LineChart(context) }

    AndroidView(
        modifier = modifier,
        factory = {
            chart.apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setBackgroundColor(Color.WHITE)

                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                    valueFormatter = object : ValueFormatter() {
                        private val mFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            // value is the index of the entry, map it back to timestamp
                            val index = value.roundToInt()
                            return if (index >= 0 && index < klineData.size) {
                                mFormat.format(Date(klineData[index].openTime))
                            } else {
                                ""
                            }
                        }
                    }
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(4, true) // Show approximately 4 labels, force interval
                }

                // Left Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    textColor = Color.BLACK
                    axisMinimum = 0f // Start from 0 or adjust based on data
                }

                // Right Y-axis configuration (disable)
                axisRight.isEnabled = false

                // Legend configuration
                legend.apply {
                    isEnabled = true
                    textColor = Color.BLACK
                }

                // Marker for selected values
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        e?.let {
                            val index = it.x.roundToInt()
                            if (index >= 0 && index < klineData.size) {
                                val kline = klineData[index]
                                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(kline.openTime))
                                val price = kline.closePrice
                                chart.centerViewToAnimated(it.x, it.y, chart.yAxis.axisDependency, 500)
                                // You might want to show a custom marker view here
                                // For simplicity, we'll just log or update a state
                                // Log.d("Chart", "Selected: Date=$date, Price=$price")
                            }
                        }
                    }

                    override fun onNothingSelected() {
                        // Do nothing
                    }
                })
            }
        },
        update = { lineChart ->
            updateChart(lineChart, klineData)
        }
    )
}

private fun updateChart(chart: LineChart, klineData: List<KlineData>) {
    if (klineData.isEmpty()) {
        chart.data = null
        chart.invalidate()
        return
    }

    val entries = klineData.mapIndexed { index, data ->
        Entry(index.toFloat(), data.closePrice.toFloat())
    }

    val dataSet = LineDataSet(entries, "PAXGUSDT Close Price").apply {
        color = Color.BLUE
        setCircleColor(Color.BLUE)
        lineWidth = 2f
        circleRadius = 3f
        setDrawCircleHole(false)
        valueTextSize = 9f
        setDrawValues(false) // Hide individual value labels on the chart
        mode = LineDataSet.Mode.LINEAR // Smooth line
    }

    val lineData = LineData(dataSet)
    chart.data = lineData
    chart.invalidate() // Refresh the chart
    chart.animateX(1000) // Animate chart on X-axis
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AIAgentTheme {
        // Create a dummy ViewModel for preview
        val dummyKlines = listOf(
            KlineData(1678886400000, "1800", "1810", "1790", "1805", "", 0, "", 0, "", "", ""),
            KlineData(1678890000000, "1805", "1815", "1800", "1812", "", 0, "", 0, "", "", ""),
            KlineData(1678893600000, "1812", "1820", "1808", "1818", "", 0, "", 0, "", "", ""),
            KlineData(1678897200000, "1818", "1825", "1815", "1822", "", 0, "", 0, "", "", ""),
            KlineData(1678900800000, "1822", "1830", "1819", "1828", "", 0, "", 0, "", "", "")
        )
        val dummyViewModel = object : MainViewModel(RetrofitClient.api) {
            override val currentPrice: StateFlow<String> = MutableStateFlow("1828.50")
            override val klineData: StateFlow<List<KlineData>> = MutableStateFlow(dummyKlines)
            override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)
            override val errorMessage: StateFlow<String?> = MutableStateFlow(null)
            override fun fetchPriceData() { /* Do nothing for preview */ }
        }
        MainScreen(viewModel = dummyViewModel)
    }
}