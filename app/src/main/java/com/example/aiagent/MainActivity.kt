package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data models for Binance API responses
@Serializable
data class BinanceTickerResponse(
    val symbol: String,
    val lastPrice: String,
    val priceChangePercent: String
)

// Binance klines API returns an array of arrays.
// Each inner array represents a candlestick:
// [
//   [
//     1499040000000,      // Open time
//     "0.01634790",       // Open
//     "0.80000000",       // High
//     "0.01575800",       // Low
//     "0.01577100",       // Close
//     "148976.10704000",  // Volume
//     1499644799999,      // Close time
//     "2434.19055334",    // Quote asset volume
//     308,                // Number of trades
//     "1756.87402397",    // Taker buy base asset volume
//     "28.46694368",      // Taker buy quote asset volume
//     "17928899.62484339" // Ignore
//   ]
// ]
// We only care about Open time and Close price for a simple line chart.
typealias BinanceKline = List<String>

class GoldPriceViewModel : ViewModel() {
    private val _currentPrice = MutableStateFlow("N/A")
    val currentPrice: StateFlow<String> = _currentPrice.asStateFlow()

    private val _chartEntries = MutableStateFlow<List<Entry>>(emptyList())
    val chartEntries: StateFlow<List<Entry>> = _chartEntries.asStateFlow()

    private val _chartLabels = MutableStateFlow<List<String>>(emptyList())
    val chartLabels: StateFlow<List<String>> = _chartLabels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        fetchPriceAndKlines()
    }

    fun fetchPriceAndKlines() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Fetch current price
                val tickerUrl = URL("https://api.binance.com/api/v3/ticker/24hr?symbol=PAXGUSDT")
                val tickerJsonString = tickerUrl.readText()
                val tickerResponse = json.decodeFromString(BinanceTickerResponse.serializer(), tickerJsonString)
                _currentPrice.value = String.format(Locale.US, "%.2f USDT", tickerResponse.lastPrice.toDouble())

                // Fetch historical klines (e.g., last 24 hours, 1-hour interval)
                val klinesUrl = URL("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
                val klinesJsonString = klinesUrl.readText()
                val klinesResponse = json.decodeFromString(ListSerializer(BinanceKline.serializer()), klinesJsonString)

                val entries = mutableListOf<Entry>()
                val labels = mutableListOf<String>()
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                klinesResponse.forEachIndexed { index, kline ->
                    val openTime = kline[0].toLong() // Timestamp in milliseconds
                    val closePrice = kline[4].toFloat() // Close price
                    entries.add(Entry(index.toFloat(), closePrice))
                    labels.add(dateFormat.format(Date(openTime)))
                }

                _chartEntries.value = entries
                _chartLabels.value = labels

            } catch (e: Exception) {
                Log.e("GoldPriceViewModel", "Error fetching data: ${e.message}", e)
                _currentPrice.value = "Error"
                _chartEntries.value = emptyList()
                _chartLabels.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // Using MaterialTheme for basic styling
                AiAgentApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentApp(viewModel: GoldPriceViewModel = viewModel()) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val chartEntries by viewModel.chartEntries.collectAsState()
    val chartLabels by viewModel.chartLabels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PAXGUSDT 金價追蹤") }
            )
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
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("載入中...", modifier = Modifier.padding(top = 8.dp))
            } else {
                PriceDisplay(currentPrice = currentPrice)
                Spacer(modifier = Modifier.height(16.dp))
                PriceChart(chartEntries = chartEntries, chartLabels = chartLabels)
            }
        }
    }
}

@Composable
fun PriceDisplay(currentPrice: String) {
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
                text = "當前 PAXGUSDT 價格",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentPrice,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 48.sp
            )
        }
    }
}

@Composable
fun PriceChart(chartEntries: List<Entry>, chartLabels: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LineChart(context).apply {
                    // Basic chart configuration
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)

                    // X-axis configuration
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.setDrawAxisLine(true)
                    xAxis.granularity = 1f // only 1 hour interval
                    xAxis.labelRotationAngle = -45f // Rotate labels for better readability
                    xAxis.textColor = Color.BLACK
                    xAxis.valueFormatter = IndexAxisValueFormatter(chartLabels)

                    // Y-axis configuration (left)
                    axisLeft.setDrawGridLines(true)
                    axisLeft.setDrawAxisLine(true)
                    axisLeft.textColor = Color.BLACK
                    axisLeft.axisMinimum = 0f // Start from 0 or adjust based on data range

                    // Y-axis configuration (right)
                    axisRight.isEnabled = false // Disable right Y-axis

                    // Legend configuration
                    legend.isEnabled = true
                    legend.textColor = Color.BLACK
                }
            },
            update = { chart ->
                if (chartEntries.isNotEmpty()) {
                    val dataSet = LineDataSet(chartEntries, "PAXGUSDT 價格 (過去 24 小時)").apply {
                        color = Color.BLUE
                        setCircleColor(Color.BLUE)
                        lineWidth = 2f
                        circleRadius = 3f
                        setDrawCircleHole(false)
                        valueTextSize = 9f
                        setDrawFilled(true) // Fill area below the line
                        fillColor = Color.BLUE
                        fillAlpha = 50 // Transparency for fill color
                        mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve
                    }

                    val lineData = LineData(dataSet)
                    chart.data = lineData

                    // Update X-axis labels
                    chart.xAxis.valueFormatter = IndexAxisValueFormatter(chartLabels)
                    chart.xAxis.setLabelCount(chartLabels.size, true) // Ensure all labels are shown

                    chart.notifyDataSetChanged() // Notify chart that data has changed
                    chart.invalidate() // Redraw the chart
                    chart.animateX(1000) // Animate the chart along the X-axis for 1 second
                } else {
                    chart.clear() // Clear chart if no data
                    chart.setNoDataText("無資料可顯示")
                    chart.invalidate()
                }
            }
        )
    }
}