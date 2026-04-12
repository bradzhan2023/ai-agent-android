package com.example.aiagent

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.ui.theme.AiAgentTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import android.graphics.Color // For MPAndroidChart colors

// Data model for Binance KLine
// Binance KLines API returns a list of lists, where each inner list represents a KLine.
// The types within the inner list are mixed (Double for timestamps, String for prices).
// We define a data class to hold the parsed values.
data class KLine(
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

// Helper function to parse the raw list of Any from Binance API into a KLine object.
// This is necessary because Gson struggles with direct deserialization of mixed types in a list.
fun parseKLine(rawList: List<Any>): KLine {
    return KLine(
        openTime = (rawList[0] as Double).toLong(),
        openPrice = rawList[1] as String,
        highPrice = rawList[2] as String,
        lowPrice = rawList[3] as String,
        closePrice = rawList[4] as String,
        volume = rawList[5] as String,
        closeTime = (rawList[6] as Double).toLong(),
        quoteAssetVolume = rawList[7] as String,
        numberOfTrades = (rawList[8] as Double).toLong(),
        takerBuyBaseAssetVolume = rawList[9] as String,
        takerBuyQuoteAssetVolume = rawList[10] as String,
        ignore = rawList[11] as String
    )
}

// Retrofit API Service Interface for Binance
interface BinanceApiService {
    @GET("api/v3/klines")
    fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 500 // Default limit for number of klines
    ): Call<List<List<Any>>> // The API returns a list of lists of various types
}

// Retrofit Client for API calls
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    val apiService: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApiService::class.java)
    }
}

// ViewModel for fetching and managing gold price data
class GoldPriceViewModel : ViewModel() {
    private val _currentPrice = MutableStateFlow<String>("Loading...")
    val currentPrice: StateFlow<String> = _currentPrice

    private val _chartEntries = MutableStateFlow<List<Entry>>(emptyList())
    val chartEntries: StateFlow<List<Entry>> = _chartEntries

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchGoldPriceHistory()
    }

    fun fetchGoldPriceHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            RetrofitClient.apiService.getKlines("PAXGUSDT", "1h", 100).enqueue(object : Callback<List<List<Any>>> {
                override fun onResponse(call: Call<List<List<Any>>>, response: Response<List<List<Any>>>) {
                    _isLoading.value = false
                    if (response.isSuccessful) {
                        val rawKlines = response.body()
                        if (rawKlines != null && rawKlines.isNotEmpty()) {
                            val klines = rawKlines.map { parseKLine(it) }
                            // Convert KLine data to MPAndroidChart Entry objects
                            val entries = klines.mapIndexed { index, kline ->
                                Entry(index.toFloat(), kline.closePrice.toFloat())
                            }
                            _chartEntries.value = entries
                            _currentPrice.value = klines.last().closePrice // Display the latest close price
                        } else {
                            _errorMessage.value = "No data received."
                        }
                    } else {
                        _errorMessage.value = "Error: ${response.code()} - ${response.message()}"
                        Log.e("GoldPriceViewModel", "API Error: ${response.code()} - ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<List<List<Any>>>, t: Throwable) {
                    _isLoading.value = false
                    _errorMessage.value = "Network error: ${t.message}"
                    Log.e("GoldPriceViewModel", "Network error fetching klines", t)
                }
            })
        }
    }
}

// Main Activity for the Jetpack Compose app
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceTrackerScreen()
                }
            }
        }
    }
}

// Composable for the main gold price tracker screen
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel = viewModel()) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val chartEntries by viewModel.chartEntries.collectAsState()
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

        Text(
            text = "Current Price: $currentPrice USDT",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = { viewModel.fetchGoldPriceHistory() },
            enabled = !isLoading // Disable button while loading
        ) {
            Text("Refresh Price")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (chartEntries.isNotEmpty()) {
            LineChartCompose(entries = chartEntries, modifier = Modifier.fillMaxWidth().height(300.dp))
        } else if (!isLoading && errorMessage == null) {
            Text("No chart data available.", modifier = Modifier.padding(16.dp))
        }
    }
}

// Composable wrapper for MPAndroidChart LineChart
@Composable
fun LineChartCompose(entries: List<Entry>, modifier: Modifier = Modifier) {
    // LocalContext is needed to create the LineChart view
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                // Basic chart configuration
                description.isEnabled = false // Disable chart description
                setTouchEnabled(true) // Enable touch gestures
                isDragEnabled = true // Enable dragging
                setScaleEnabled(true) // Enable scaling
                setPinchZoom(true) // Enable pinch zoom

                // Configure X-axis
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM // X-axis at the bottom
                    setDrawGridLines(false) // Do not draw grid lines for X-axis
                    setDrawAxisLine(true) // Draw axis line
                    granularity = 1f // Only intervals of 1 on the axis
                    valueFormatter = IndexAxisValueFormatter() // Simple index formatter for now
                    textColor = Color.BLACK // Set text color for better visibility
                }

                // Configure Left Y-axis
                axisLeft.apply {
                    setDrawGridLines(true) // Draw grid lines for Y-axis
                    setDrawAxisLine(true) // Draw axis line
                    textColor = Color.BLACK // Set text color for better visibility
                }

                // Disable Right Y-axis
                axisRight.isEnabled = false

                // Configure Legend
                legend.apply {
                    isEnabled = true
                    verticalAlignment = Legend.LegendVerticalAlignment.TOP
                    horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    form = Legend.LegendForm.CIRCLE
                    textColor = Color.BLACK // Set text color for better visibility
                }
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT Close Price").apply {
                    color = Color.BLUE // Line color
                    setCircleColor(Color.RED) // Circle color for data points
                    lineWidth = 2f // Line width
                    circleRadius = 3f // Radius of the circles
                    setDrawCircleHole(false) // Do not draw a hole in the circles
                    valueTextSize = 9f // Size of the value text
                    setDrawValues(false) // Hide value text on chart for a cleaner look
                    setDrawFilled(false) // Disable fill under the line to avoid drawable dependency
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh chart to display new data
            } else {
                chart.clear() // Clear chart if entries are empty
                chart.invalidate()
            }
        }
    )
}