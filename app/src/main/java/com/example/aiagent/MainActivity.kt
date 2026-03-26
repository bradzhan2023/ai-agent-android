```kotlin
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagent.ui.theme.AiAgentTheme
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.core.chart.composed.plus
import com.patrykandpatrick.vico.core.chart.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.chart.entry.composed.plus
import com.patrykandpatrick.vico.core.chart.values.AxisValueOverrider
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.formatter.DecimalFormatAxisValueFormatter
import com.patrykandpatrick.vico.core.formatter.ValueFormatter
import java.text.SimpleDateFormat

// Data classes for parsing Binance API responses
data class CurrentPriceResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("price") val price: String
)

// Klines API returns an array of arrays. We'll parse it as a list of JsonArray
// and then extract the relevant fields.
// [
//   [
//     1499040000000,      // Open time
//     "0.01634790",       // Open price
//     "0.80000000",       // High price
//     "0.01575600",       // Low price
//     "0.01577100",       // Close price
//     "148976.10700000",  // Volume
//     1499644799999,      // Close time
//     "2434.19055334",    // Quote asset volume
//     308,                // Number of trades
//     "1756.87402397",    // Taker buy base asset volume
//     "28.46694368",      // Taker buy quote asset volume
//     "0"                 // Ignore
//   ]
// ]
data class KlineData(
    val openTime: Long,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val closeTime: Long
)

// ViewModel for fetching and holding the data
class GoldTrackerViewModel : ViewModel() {
    private val client = OkHttpClient()
    private val gson = Gson()

    private val _currentPrice = MutableStateFlow<String>("Loading...")
    val currentPrice: StateFlow<String> = _currentPrice

    private val _priceChange24h = MutableStateFlow<String>("")
    val priceChange24h: StateFlow<String> = _priceChange24h

    private val _chartEntryModelProducer = ChartEntryModelProducer()
    val chartEntryModelProducer: ChartEntryModelProducer = _chartEntryModelProducer

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchPriceData()
    }

    fun fetchPriceData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val currentPriceRequest = Request.Builder()
                    .url("https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT")
                    .build()

                val klinesRequest = Request.Builder()
                    .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24") // 24 hourly data points
                    .build()

                // Fetch current price
                client.newCall(currentPriceRequest).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code ${response}")
                    val body = response.body?.string()
                    val priceResponse = gson.fromJson(body, CurrentPriceResponse::class.java)
                    _currentPrice.value = String.format("%.2f USDT", priceResponse.price.toDouble())
                }

                // Fetch 24-hour klines data for chart and 24h change
                client.newCall(klinesRequest).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code ${response}")
                    val body = response.body?.string()
                    val klinesJsonArray = gson.fromJson(body, JsonArray::class.java)

                    val klines = klinesJsonArray.mapNotNull { element ->
                        val klineArray = element.asJsonArray
                        if (klineArray.size() >= 7) { // Ensure enough elements
                            KlineData(
                                openTime = klineArray[0].asLong,
                                openPrice = klineArray[1].asString.toDouble(),
                                highPrice = klineArray[2].asString.toDouble(),
                                lowPrice = klineArray[3].asString.toDouble(),
                                closePrice = klineArray[4].asString.toDouble(),
                                closeTime = klineArray[6].asLong
                            )
                        } else {
                            null
                        }
                    }

                    if (klines.isNotEmpty()) {
                        val entries = klines.mapIndexed { index, kline ->
                            FloatEntry(index.toFloat(), kline.closePrice.toFloat())
                        }
                        _chartEntryModelProducer.setEntries(entries)

                        // Calculate 24h change
                        if (klines.size >= 24) { // We need at least 24 data points to calculate a 24-hour change correctly
                            val initialPrice = klines.first().openPrice // Open price of the first kline (24 hours ago)
                            val finalPrice = klines.last().closePrice // Close price of the last kline (current)
                            val change = finalPrice - initialPrice
                            val percentageChange = (change / initialPrice) * 100

                            val changeText = String.format("%.2f (%.2f%%)", change, percentageChange)
                            _priceChange24h.value = changeText
                        } else {
                            _priceChange24h.value = "Not enough data for 24h change."
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch data: ${e.localizedMessage}"
                _currentPrice.value = "Error"
                _priceChange24h.value = "Error"
                e.printStackTrace()
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
            AiAgentTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldTrackerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldTrackerScreen(viewModel: GoldTrackerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val priceChange24h by viewModel.priceChange24h.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val chartEntryModelProducer = viewModel.chartEntryModelProducer

    val timeFormatter = remember {
        object : ValueFormatter {
            private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            override fun formatValue(value: Float, chartValues: com.patrykandpatrick.vico.core.chart.values.ChartValues): CharSequence {
                // Assuming value is the index (0-23) for hourly data, we need to map it back to a time.
                // This is a simplification; in a real app, you'd fetch the actual timestamps for each entry.
                // For this example, we'll assume the 0th entry is 24 hours ago and the 23rd is "now".
                val now = System.currentTimeMillis()
                val oneHourMillis = 60 * 60 * 1000L
                val timeMillis = now - (23 - value.toInt()) * oneHourMillis
                return dateFormat.format(Date(timeMillis))
            }
        }
    }

    val priceFormatter = remember {
        object : ValueFormatter {
            private val numberFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
                currency = Currency.getInstance("USD")
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            override fun formatValue(value: Float, chartValues: com.patrykandpatrick.vico.core.chart.values.ChartValues): CharSequence {
                return numberFormat.format(value)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXG Gold Price Tracker") })
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
                text = "Current PAXG Price:",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentPrice,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "24h Change: $priceChange24h",
                style = MaterialTheme.typography.bodyLarge,
                color = if (priceChange24h.contains("-")) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text("Loading chart data...", modifier = Modifier.padding(top = 8.dp))
            } else if (errorMessage != null) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Button(onClick = { viewModel.fetchPriceData() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Retry")
                }
            } else {
                Text(
                    text = "24-Hour Price Trend (PAXG/USDT)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Chart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    chart = lineChart(
                        lines = listOf(
                            lineSpec(
                                shader = null, // Disable gradient for simplicity, use solid color
                                lineColor = MaterialTheme.colorScheme.primary,
                                point = null, // No points shown
                                thickness = 3.dp
                            )
                        )
                    ),
                    modelProducer = chartEntryModelProducer,
                    startAxis = rememberStartAxis(
                        valueFormatter = priceFormatter,
                        label = rememberTextComponent(
                            color = MaterialTheme.colorScheme.onSurface,
                            background = rememberShapeComponent(shape = Shapes.rect(), color = Color.Transparent),
                            padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp)
                        ),
                        axis = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            thickness = 1.dp
                        ),
                        tick = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp
                        ),
                        guideline = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp
                        ),
                        title = "Price (USDT)",
                        titleComponent = rememberTextComponent(
                            color = MaterialTheme.colorScheme.onSurface,
                            background = rememberShapeComponent(shape = Shapes.rect(), color = Color.Transparent),
                            padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp)
                        ),
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = timeFormatter,
                        label = rememberTextComponent(
                            color = MaterialTheme.colorScheme.onSurface,
                            background = rememberShapeComponent(shape = Shapes.rect(), color = Color.Transparent),
                            padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp)
                        ),
                        axis = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            thickness = 1.dp
                        ),
                        tick = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp
                        ),
                        guideline = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp
                        ),
                        title = "Time",
                        titleComponent = rememberTextComponent(
                            color = MaterialTheme.colorScheme.onSurface,
                            background = rememberShapeComponent(shape = Shapes.rect(), color = Color.Transparent),
                            padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp)
                        ),
                    ),
                    marker = null, // No marker for simplicity, can be added if needed
                    chartScrollState = com.patrykandpatrick.vico.compose.scroll.rememberVicoScrollState(),
                    isZoomEnabled = true
                )
            }
        }
    }
}
```