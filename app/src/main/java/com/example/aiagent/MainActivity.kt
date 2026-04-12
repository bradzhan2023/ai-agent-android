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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagent.ui.theme.AiAgentTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.gson.GsonBuilder
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
import java.text.SimpleDateFormat
import java.util.*

// Data Models
data class KlineDataPoint(
    val openTime: Long,
    val closePrice: Double
)

// Retrofit Interface for Binance API
interface BinanceApiService {
    @GET("api/v3/klines")
    fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): Call<List<List<String>>> // Binance API returns list of lists of strings
}

// Retrofit Client Singleton
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    val apiService: BinanceApiService by lazy {
        val gson = GsonBuilder().setLenient().create() // Use lenient for potentially malformed JSON
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BinanceApiService::class.java)
    }
}

// ViewModel to manage UI state and data fetching
class GoldPriceViewModel : ViewModel() {
    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice

    private val _klineData = MutableStateFlow<List<KlineDataPoint>>(emptyList())
    val klineData: StateFlow<List<KlineDataPoint>> = _klineData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchGoldPriceData()
    }

    fun fetchGoldPriceData() {
        _isLoading.value = true
        _errorMessage.value = null

        RetrofitClient.apiService.getKlines("PAXGUSDT", "1h", 100).enqueue(object : Callback<List<List<String>>> {
            override fun onResponse(call: Call<List<List<String>>>, response: Response<List<List<String>>>) {
                viewModelScope.launch {
                    _isLoading.value = false
                    if (response.isSuccessful) {
                        val rawKlines = response.body()
                        if (rawKlines != null && rawKlines.isNotEmpty()) {
                            val parsedKlines = rawKlines.mapNotNull { kline ->
                                try {
                                    // Kline data structure: [openTime, openPrice, highPrice, lowPrice, closePrice, ...]
                                    val openTime = kline[0].toLong()
                                    val closePrice = kline[4].toDouble()
                                    KlineDataPoint(openTime, closePrice)
                                } catch (e: Exception) {
                                    Log.e("GoldPriceViewModel", "Error parsing kline data: $kline", e)
                                    null
                                }
                            }
                            _klineData.value = parsedKlines
                            _currentPrice.value = parsedKlines.lastOrNull()?.closePrice
                        } else {
                            _errorMessage.value = "No data received or empty response."
                        }
                    } else {
                        _errorMessage.value = "Error: ${response.code()} - ${response.message()}"
                    }
                }
            }

            override fun onFailure(call: Call<List<List<String>>>, t: Throwable) {
                viewModelScope.launch {
                    _isLoading.value = false
                    _errorMessage.value = "Network error: ${t.message}"
                    Log.e("GoldPriceViewModel", "Network error fetching klines", t)
                }
            }
        })
    }
}

// Main Activity for the Android application
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                // A surface container using the 'background' color from the theme
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

// Composable function for the main screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel = viewModel()) {
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("載入中...", style = MaterialTheme.typography.bodyLarge)
            } else if (errorMessage != null) {
                Text(
                    text = "錯誤: ${errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = { viewModel.fetchGoldPriceData() }) {
                    Text("重試")
                }
            } else {
                currentPrice?.let { price ->
                    CurrentPriceDisplay(price = price)
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (klineData.isNotEmpty()) {
                    GoldPriceChart(klineData = klineData)
                } else {
                    Text("沒有圖表數據。", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// Composable to display the current price
@Composable
fun CurrentPriceDisplay(price: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "當前 PAXGUSDT 價格",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = String.format("%.2f USDT", price),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// Composable to display the LineChart using MPAndroidChart
@Composable
fun GoldPriceChart(klineData: List<KlineDataPoint>) {
    val entries = remember(klineData) {
        klineData.mapIndexed { index, dataPoint ->
            Entry(index.toFloat(), dataPoint.closePrice.toFloat())
        }
    }

    val xAxisFormatter = remember(klineData) {
        object : IndexAxisValueFormatter() {
            private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < klineData.size) {
                    dateFormat.format(Date(klineData[index].openTime))
                } else {
                    ""
                }
            }
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false // Disable description label
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true) // Enable pinch zoom

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // Disable vertical grid lines
                    valueFormatter = xAxisFormatter
                    granularity = 1f // Only show integer values on the axis
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(4, true) // Show approximately 4 labels, force integers
                    textColor = android.graphics.Color.BLACK
                }

                axisRight.isEnabled = false // Disable right Y-axis
                axisLeft.apply {
                    setDrawGridLines(true) // Enable horizontal grid lines
                    enableGridDashedLine(10f, 10f, 0f) // Dashed grid lines
                    textColor = android.graphics.Color.BLACK
                }

                legend.isEnabled = false // Disable legend
                setNoDataText("沒有圖表數據可顯示")
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = android.graphics.Color.BLUE
                    setCircleColor(android.graphics.Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on points
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                    setDrawFilled(true) // Fill area below the line
                    fillColor = android.graphics.Color.BLUE
                    fillAlpha = 50 // Transparency for fill
                }
                chart.data = LineData(dataSet)
                chart.invalidate() // Refresh chart
            } else {
                chart.clear() // Clear existing data if entries are empty
                chart.invalidate()
            }
        }
    )
}