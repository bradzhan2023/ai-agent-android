package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response

// Minimal Theme definition for this single file output
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC5),
    tertiary = Color(0xFF3700B3)
)

@Composable
fun AiAgentTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

/**
 * Retrofit Interface for Binance API.
 * Fetches Klines (candlestick data) for a given symbol and interval.
 */
interface BinanceApiService {
    @GET("api/v3/klines")
    fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 500 // Default limit
    ): Call<List<List<Any>>> // Binance API returns a list of lists, where inner lists contain mixed types
}

/**
 * Singleton object for Retrofit client setup.
 */
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    val binanceApiService: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApiService::class.java)
    }
}

/**
 * ViewModel to fetch and manage gold price data.
 */
class GoldPriceViewModel : ViewModel() {
    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice

    private val _priceHistory = MutableStateFlow<List<Entry>>(emptyList())
    val priceHistory: StateFlow<List<Entry>> = _priceHistory

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchGoldPriceHistory()
    }

    /**
     * Fetches PAXGUSDT klines data from Binance API.
     */
    fun fetchGoldPriceHistory() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response: Response<List<List<Any>>> = RetrofitClient.binanceApiService.getKlines(
                    symbol = "PAXGUSDT",
                    interval = "1h", // 1-hour interval
                    limit = 100 // Get last 100 data points
                ).execute()

                if (response.isSuccessful) {
                    val klines = response.body()
                    if (klines != null && klines.isNotEmpty()) {
                        val entries = mutableListOf<Entry>()
                        klines.forEachIndexed { index, kline ->
                            // kline[4] is the close price (String)
                            // kline[0] is the open time (Long) - not used for X-axis in this simple chart
                            val closePrice = (kline[4] as String).toFloat()
                            entries.add(Entry(index.toFloat(), closePrice))
                        }
                        _priceHistory.value = entries
                        _currentPrice.value = (klines.last()[4] as String).toDouble()
                    } else {
                        _errorMessage.value = "No data received from Binance API."
                    }
                } else {
                    _errorMessage.value = "Error fetching data: ${response.code()} - ${response.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

/**
 * Main Activity for the Gold Price Tracker application.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceTrackerApp()
                }
            }
        }
    }
}

/**
 * Main Composable for the Gold Price Tracker application.
 * Observes data from ViewModel and displays UI.
 */
@Composable
fun GoldPriceTrackerApp(viewModel: GoldPriceViewModel = viewModel()) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PAXGUSDT 金價追蹤",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            Text("載入中...")
        } else if (errorMessage != null) {
            Text(
                text = "錯誤: $errorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = { viewModel.fetchGoldPriceHistory() }) {
                Text("重試")
            }
        } else {
            currentPrice?.let { price ->
                Text(
                    text = "目前價格: $%.2f".format(price),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } ?: Text(
                text = "目前價格: N/A",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            GoldPriceChart(priceHistory = priceHistory)
        }
    }
}

/**
 * Composable to display the gold price history using MPAndroidChart LineChart.
 */
@Composable
fun GoldPriceChart(priceHistory: List<Entry>) {
    val context = LocalContext.current
    // Use remember to keep the chart instance across recompositions
    val chart = remember { LineChart(context) }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = {
            // Configure the chart initially
            chart.apply {
                description.isEnabled = false // Disable chart description
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true) // Enable pinch zoom for both axes

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    granularity = 1f // Only 1 unit on the x-axis
                    valueFormatter = IndexAxisValueFormatter() // Simple index formatter for x-axis
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                }

                axisRight.isEnabled = false // Disable right axis

                legend.apply {
                    form = Legend.LegendForm.LINE
                    verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }
            }
        },
        update = { lineChart ->
            // Update chart data when priceHistory changes
            if (priceHistory.isNotEmpty()) {
                val dataSet = LineDataSet(priceHistory, "PAXGUSDT Price").apply {
                    color = Color.Blue.toArgb()
                    setCircleColor(Color.Blue.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 9f
                    setDrawFilled(true)
                    fillColor = Color.Blue.toArgb()
                    fillAlpha = 50 // Transparency of the fill color
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                }

                val lineData = LineData(dataSet)
                lineChart.data = lineData
                lineChart.invalidate() // Refresh the chart view
            } else {
                lineChart.data = null // Clear data if history is empty
                lineChart.invalidate()
            }
        }
    )
}