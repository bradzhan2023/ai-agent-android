package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data model for a single price point
@Serializable
data class PricePoint(
    val timestamp: Long, // Unix timestamp in milliseconds
    val price: Double
)

class GoldPriceViewModel : ViewModel() {
    private val _goldPrices = MutableStateFlow<List<PricePoint>>(emptyList())
    val goldPrices: StateFlow<List<PricePoint>> = _goldPrices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    init {
        fetchGoldPrices()
    }

    fun fetchGoldPrices() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Fetch raw string response as Binance klines are an array of arrays with mixed types
                val response: String = httpClient.get("https://api.binance.com/api/v3/klines") {
                    parameter("symbol", "PAXGUSDT")
                    parameter("interval", "1h") // 1-hour interval
                    parameter("limit", 100) // Get last 100 data points
                }.body()

                // Manually parse the raw JSON array of arrays
                val jsonArray = Json.parseToJsonElement(response).jsonArray
                val prices = jsonArray.mapNotNull { element ->
                    val kline = element.jsonArray
                    if (kline.size >= 5) { // Ensure enough elements for openTime and closePrice
                        val openTime = kline[0].jsonPrimitive.long
                        val closePrice = kline[4].jsonPrimitive.content.toDoubleOrNull()
                        if (closePrice != null) {
                            PricePoint(timestamp = openTime, price = closePrice)
                        } else null
                    } else null
                }
                _goldPrices.value = prices.sortedBy { it.timestamp } // Ensure chronological order
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch gold prices: ${e.localizedMessage}"
                _goldPrices.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close() // Close the Ktor client when the ViewModel is cleared
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GoldPriceViewModel = viewModel()
                    GoldPriceTrackerScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // For SmallTopAppBar
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    val goldPrices by viewModel.goldPrices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            SmallTopAppBar(title = { Text("PAXGUSDT 金價追蹤") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("載入中...")
            } else if (errorMessage != null) {
                Text(
                    text = "錯誤: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = { viewModel.fetchGoldPrices() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("重試")
                }
            } else if (goldPrices.isNotEmpty()) {
                val latestPrice = goldPrices.last()
                Text(
                    text = "最新價格: ${String.format("%.2f", latestPrice.price)} USDT",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "更新時間: ${formatTimestamp(latestPrice.timestamp)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                GoldPriceLineChart(
                    goldPrices = goldPrices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 8.dp)
                )
            } else {
                Text("沒有金價數據可顯示。", modifier = Modifier.padding(16.dp))
                Button(onClick = { viewModel.fetchGoldPrices() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("載入數據")
                }
            }
        }
    }
}

@Composable
fun GoldPriceLineChart(goldPrices: List<PricePoint>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false // Disable chart description
                setTouchEnabled(true) // Enable touch gestures
                isDragEnabled = true // Enable dragging
                setScaleEnabled(true) // Enable scaling
                setPinchZoom(true) // Enable pinch zoom

                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM // X-axis at the bottom
                    setDrawGridLines(false) // Do not draw grid lines for X-axis
                    setDrawAxisLine(true) // Draw X-axis line
                    valueFormatter = object : ValueFormatter() {
                        private val mFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            // Convert float timestamp back to Long for Date object
                            return mFormat.format(Date(value.toLong()))
                        }
                    }
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(4, true) // Show approximately 4 labels, force these exact labels
                    textColor = android.graphics.Color.GRAY
                    axisLineColor = android.graphics.Color.GRAY
                }

                // Left Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true) // Draw grid lines for Y-axis
                    setDrawAxisLine(true) // Draw Y-axis line
                    textColor = android.graphics.Color.GRAY
                    axisLineColor = android.graphics.Color.GRAY
                    gridColor = android.graphics.Color.LTGRAY
                }

                // Right Y-axis configuration (disable)
                axisRight.isEnabled = false

                legend.isEnabled = true // Enable legend
                legend.textColor = android.graphics.Color.DKGRAY
                animateX(1000) // Animate chart on X-axis for 1 second
            }
        },
        update = { chart ->
            val entries = goldPrices.map { pricePoint ->
                // MPAndroidChart Entry takes float for x and y values
                Entry(pricePoint.timestamp.toFloat(), pricePoint.price.toFloat())
            }

            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = android.graphics.Color.parseColor("#FF6200EE") // Primary color from Material Design
                    setCircleColor(android.graphics.Color.parseColor("#FF6200EE"))
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 9f
                    setDrawValues(false) // Do not draw individual values on the chart
                    mode = LineDataSet.Mode.LINEAR // Linear line connection
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh the chart
            } else {
                chart.clear() // Clear existing data if list is empty
                chart.invalidate()
            }
        }
    )
}

@Composable
fun formatTimestamp(timestamp: Long): String {
    // Use remember to avoid re-creating SimpleDateFormat on every recomposition
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    return sdf.format(Date(timestamp))
}

// Placeholder for theme, typically defined in ui.theme package
@Composable
fun AiAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFBB86FC),
            secondary = Color(0xFF03DAC5),
            tertiary = Color(0xFF3700B3),
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
            error = Color(0xFFCF6679),
            onPrimary = Color.BLACK,
            onSecondary = Color.BLACK,
            onTertiary = Color.WHITE,
            onBackground = Color.WHITE,
            onSurface = Color.WHITE,
            onError = Color.BLACK
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC5),
            tertiary = Color(0xFF3700B3),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            error = Color(0xFFB00020),
            onPrimary = Color.WHITE,
            onSecondary = Color.BLACK,
            onTertiary = Color.WHITE,
            onBackground = Color.BLACK,
            onSurface = Color.BLACK,
            onError = Color.WHITE
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(), // Placeholder, use actual typography
        content = content
    )
}

// Placeholder for Typography, typically defined in ui.theme package
val Typography = androidx.compose.material3.Typography()