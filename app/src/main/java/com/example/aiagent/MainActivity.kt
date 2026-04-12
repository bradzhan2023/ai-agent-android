package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1. Data Model
// Binance KLine API returns an array of arrays. We'll parse it manually for simplicity.
data class KLineItem(
    val openTime: Long,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val volume: Double,
    val closeTime: Long
)

// 2. Network Layer
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 100
    ): List<List<String>> // Raw list of string arrays
}

// Retrofit setup
private val json = Json { ignoreUnknownKeys = true }
private val retrofit = Retrofit.Builder()
    .baseUrl("https://api.binance.com/")
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

val binanceApiService: BinanceApiService = retrofit.create(BinanceApiService::class.java)

// 3. ViewModel
class GoldPriceViewModel(private val apiService: BinanceApiService) : ViewModel() {

    private val _klineData = MutableStateFlow<List<KLineItem>>(emptyList())
    open val klineData: StateFlow<List<KLineItem>> = _klineData

    private val _loading = MutableStateFlow(false)
    open val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    open val error: StateFlow<String?> = _error

    init {
        fetchGoldPrices()
    }

    fun fetchGoldPrices() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val rawKlines = apiService.getKlines(symbol = "PAXGUSDT", interval = "1h", limit = 100)
                val parsedKlines = rawKlines.mapNotNull { rawItem ->
                    if (rawItem.size >= 7) { // Ensure enough elements for basic parsing
                        try {
                            KLineItem(
                                openTime = rawItem[0].toLong(),
                                openPrice = rawItem[1].toDouble(),
                                highPrice = rawItem[2].toDouble(),
                                lowPrice = rawItem[3].toDouble(),
                                closePrice = rawItem[4].toDouble(),
                                volume = rawItem[5].toDouble(),
                                closeTime = rawItem[6].toLong()
                            )
                        } catch (e: NumberFormatException) {
                            _error.value = "Error parsing KLine data: ${e.message}"
                            null
                        }
                    } else {
                        _error.value = "Malformed KLine data received."
                        null
                    }
                }
                _klineData.value = parsedKlines
            } catch (e: Exception) {
                _error.value = "Failed to fetch gold prices: ${e.message}"
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    // Factory for ViewModel with dependencies
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

// 4. UI (MainActivity.kt)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // Using MaterialTheme for basic styling
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GoldPriceViewModel = viewModel(
                        factory = GoldPriceViewModel.Factory(binanceApiService)
                    )
                    GoldPriceTrackerApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceTrackerApp(viewModel: GoldPriceViewModel) {
    val klineData by viewModel.klineData.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT Gold Price Tracker") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Loading gold prices...", modifier = Modifier.padding(top = 8.dp))
            }

            error?.let { errorMessage ->
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(onClick = { viewModel.fetchGoldPrices() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Retry")
                }
            }

            klineData.lastOrNull()?.let { latestPrice ->
                Text(
                    text = "Current PAXGUSDT Price: ${"%.2f".format(latestPrice.closePrice)}",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (klineData.isNotEmpty()) {
                GoldPriceLineChart(klineData = klineData)
            } else if (!loading && error == null) {
                Text("No data available. Check network or try again.", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun GoldPriceLineChart(klineData: List<KLineItem>) {
    val entries = remember(klineData) {
        klineData.mapIndexed { index, item ->
            Entry(index.toFloat(), item.closePrice.toFloat())
        }
    }

    val timestamps = remember(klineData) {
        klineData.map { it.openTime }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                    valueFormatter = object : ValueFormatter() {
                        private val mFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            val timestamp = timestamps.getOrNull(value.toInt()) ?: return ""
                            return mFormat.format(Date(timestamp))
                        }
                    }
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(4, true) // Show approximately 4 labels, force exactly 4
                }

                axisLeft.apply {
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                }

                axisRight.isEnabled = false // Disable right Y-axis

                legend.apply {
                    isEnabled = true
                    textColor = Color.BLACK
                    form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE
                }

                animateX(1500) // Animate chart data on X-axis
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT Close Price").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on points
                    setDrawValues(false) // Hide values on the chart
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh chart
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        // For preview, we can create a dummy ViewModel with mock data
        val dummyKlines = listOf(
            KLineItem(1678886400000, 1800.0, 1810.0, 1790.0, 1805.0, 100.0, 1678890000000),
            KLineItem(1678890000000, 1805.0, 1815.0, 1800.0, 1812.0, 120.0, 1678893600000),
            KLineItem(1678893600000, 1812.0, 1820.0, 1808.0, 1818.0, 110.0, 1678897200000),
            KLineItem(1678897200000, 1818.0, 1825.0, 1815.0, 1822.0, 130.0, 1678900800000),
            KLineItem(1678900800000, 1822.0, 1830.0, 1819.0, 1828.0, 140.0, 1678904400000)
        )
        val viewModel = object : GoldPriceViewModel(binanceApiService) {
            override val klineData: StateFlow<List<KLineItem>> = MutableStateFlow(dummyKlines)
            override val loading: StateFlow<Boolean> = MutableStateFlow(false)
            override val error: StateFlow<String?> = MutableStateFlow(null)
        }
        GoldPriceTrackerApp(viewModel)
    }
}