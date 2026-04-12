package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

// Data Model
data class PriceData(val timestamp: Long, val price: Double)

// Binance API Service
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<Any>> // Binance Klines return a list of lists
}

// Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    val instance: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApiService::class.java)
    }
}

// ViewModel
class PAXGPriceViewModel(private val apiService: BinanceApiService) : ViewModel() {

    private val _priceData = MutableStateFlow<List<PriceData>>(emptyList())
    val priceData: StateFlow<List<PriceData>> = _priceData.asStateFlow()

    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        fetchPAXGPriceDataPeriodically()
    }

    private fun fetchPAXGPriceDataPeriodically() {
        viewModelScope.launch {
            while (true) {
                fetchPAXGPriceData()
                delay(60_000L) // Fetch every 60 seconds (1 minute)
            }
        }
    }

    private fun fetchPAXGPriceData() {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                val klines = apiService.getKlines(symbol = "PAXGUSDT", interval = "1m", limit = 100)
                val newPriceData = klines.mapNotNull { kline ->
                    if (kline.size >= 5) {
                        val timestamp = (kline[0] as? Double)?.toLong() ?: (kline[0] as? Long) ?: return@mapNotNull null
                        val closePrice = (kline[4] as? String)?.toDoubleOrNull() ?: return@mapNotNull null
                        PriceData(timestamp, closePrice)
                    } else {
                        null
                    }
                }
                _priceData.value = newPriceData
                _currentPrice.value = newPriceData.lastOrNull()?.price

            } catch (e: Exception) {
                _errorMessage.value = "Error fetching data: ${e.localizedMessage}"
                _currentPrice.value = null
                _priceData.value = emptyList()
            }
        }
    }

    // ViewModel Factory to inject dependencies
    class Factory(private val apiService: BinanceApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PAXGPriceViewModel::class.java)) {
                return PAXGPriceViewModel(apiService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// Custom ValueFormatter for X-axis (timestamps)
class DateAxisValueFormatter : ValueFormatter() {
    private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        // value is the timestamp in milliseconds
        return mFormat.format(Date(value.toLong()))
    }
}

// MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val viewModel: PAXGPriceViewModel = viewModel(
                        factory = PAXGPriceViewModel.Factory(RetrofitClient.instance)
                    )
                    PAXGPriceTrackerScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun PAXGPriceTrackerScreen(viewModel: PAXGPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val priceData by viewModel.priceData.collectAsState()
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current PAXGUSDT Price:",
                style = MaterialTheme.typography.h6
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentPrice?.let { String.format("%.2f USDT", it) } ?: "Loading...",
                style = MaterialTheme.typography.h4,
                color = MaterialTheme.colors.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (priceData.isNotEmpty()) {
                PriceLineChart(priceData = priceData, modifier = Modifier.fillMaxSize())
            } else if (errorMessage == null) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PriceLineChart(priceData: List<PriceData>, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true) // Enable pinch zoom to scale both axes

                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    setDrawAxisLine(true)
                    valueFormatter = DateAxisValueFormatter()
                    textColor = Color.BLACK
                    granularity = 300000f // 5 minutes in milliseconds for better readability
                }

                // Left Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                }

                // Right Y-axis configuration (disabled)
                axisRight.isEnabled = false

                // Legend configuration
                legend.apply {
                    isEnabled = true
                    textColor = Color.BLACK
                }

                // Animation
                animateX(1000)
            }
        },
        update = { chart ->
            if (priceData.isNotEmpty()) {
                val entries = priceData.map { data ->
                    Entry(data.timestamp.toFloat(), data.price.toFloat())
                }

                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on chart
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                    setDrawFilled(true) // Fill area below the line
                    fillColor = Color.BLUE
                    fillAlpha = 50
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh the chart
            } else {
                chart.clear() // Clear chart if no data
                chart.invalidate()
            }
        }
    )
}