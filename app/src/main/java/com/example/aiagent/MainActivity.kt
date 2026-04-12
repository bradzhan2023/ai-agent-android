package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.ui.theme.AiAgentTheme // Assuming your project creates this theme
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.dimensions.Dimensions
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.lineSeries
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * ViewModel for fetching and managing PAXG price data.
 */
class PAXGViewModel : ViewModel() {
    // Current PAXG price (last available close price)
    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice.asStateFlow()

    // Chart data: list of (timestamp_millis, close_price) pairs
    private val _chartData = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val chartData: StateFlow<List<Pair<Long, Double>>> = _chartData.asStateFlow()

    // Ktor HTTP client for API requests
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // Ignore fields we don't need
                isLenient = true // Allow relaxed JSON parsing
            })
        }
    }

    init {
        // Fetch data immediately when the ViewModel is created
        fetchPriceData()
    }

    /**
     * Fetches historical kline data for PAXGUSDT from Binance API.
     * Fetches 24 one-hour klines to display price over the last 24 hours.
     */
    fun fetchPriceData() {
        viewModelScope.launch {
            try {
                // Binance API endpoint for klines (candlestick data)
                // symbol=PAXGUSDT, interval=1h (1 hour), limit=24 (last 24 data points)
                val response: List<List<String>> =
                    httpClient.get("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24").body()

                val klines = response.map { rawKline ->
                    // Binance kline array structure:
                    // [0] open time, [1] open price, [2] high price, [3] low price, [4] close price, ...
                    rawKline[0].toLong() to rawKline[4].toDouble() // Map (timestamp, close_price)
                }

                _chartData.value = klines
                _currentPrice.value = klines.lastOrNull()?.second // The last close price is the current price
            } catch (e: Exception) {
                // Handle errors: log, show error message, or clear data
                _currentPrice.value = null // Indicate error or loading failure
                _chartData.value = emptyList() // Clear chart data on error
                e.printStackTrace()
                // In a real app, you might show a Toast or Snackbar here
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close() // Close the Ktor client to release resources
    }
}

/**
 * Main composable screen for tracking PAXG price.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PAXGTrackerScreen(viewModel: PAXGViewModel = viewModel()) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val chartData by viewModel.chartData.collectAsState()

    // Vico chart model producer to efficiently update chart data
    val modelProducer = remember { CartesianChartModelProducer.build() }

    // Update the chart model whenever chartData changes
    LaunchedEffect(chartData) {
        if (chartData.isNotEmpty()) {
            modelProducer.tryRunTransaction {
                // Create a line series from the list of close prices
                lineSeries {
                    series(chartData.map { it.second })
                }
            }
        }
    }

    // Auto-refresh data every 5 minutes (300,000 milliseconds)
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.fetchPriceData()
            delay(TimeUnit.MINUTES.toMillis(5))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXG Gold Tracker") })
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
                text = "Current PAXG/USDT Price:",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (currentPrice != null) {
                Text(
                    text = "$${String.format("%.2f", currentPrice)}",
                    style = MaterialTheme.typography.displayMedium.copy(
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Text(
                    text = "Loading price...",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Last 24 Hours Price Chart",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (chartData.isNotEmpty()) {
                // Custom formatter for the X-axis (time)
                val timeAxisValueFormatter = remember {
                    AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                        // 'value' here is the index of the chart entry (0 to 23 for 24 data points)
                        val timestampMillis = chartData.getOrNull(value.toInt())?.first ?: 0L
                        val instant = Instant.ofEpochMilli(timestampMillis)
                        // Format to "HH:mm" (e.g., "14:00")
                        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                        formatter.format(instant)
                    }
                }

                // Custom formatter for the Y-axis (price)
                val priceAxisValueFormatter = remember {
                    AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
                        "$%.0f".format(value) // Format price as "$X" (e.g., "$2300")
                    }
                }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lines = listOf(
                                rememberLineComponent(
                                    color = MaterialTheme.colorScheme.primary,
                                    thickness = 2.dp.value,
                                    shape = Shapes.pillShape, // Smooth line style
                                    point = rememberLineComponent( // Optional: points on the line
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = Shapes.pillShape,
                                        thickness = 6.dp.value,
                                        dynamicShader = null
                                    )
                                )
                            )
                        ),
                        // Configure the Y-axis (startAxis)
                        startAxis = rememberStartAxis(
                            valueFormatter = priceAxisValueFormatter,
                            label = rememberTextComponent(
                                color = MaterialTheme.colorScheme.onSurface,
                                textSize = 10.sp,
                                background = rememberLineComponent(
                                    color = MaterialTheme.colorScheme.surface,
                                    thickness = 1.dp.value,
                                    shape = Shapes.roundedCornerShape(2.dp),
                                    strokeColor = MaterialTheme.colorScheme.outlineVariant,
                                    strokeWidth = 1.dp.value
                                ),
                                padding = Dimensions.current.axisLabelVerticalPadding,
                            ),
                        ),
                        // Configure the X-axis (bottomAxis)
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = timeAxisValueFormatter,
                            label = rememberTextComponent(
                                color = MaterialTheme.colorScheme.onSurface,
                                textSize = 10.sp,
                                background = rememberLineComponent(
                                    color = MaterialTheme.colorScheme.surface,
                                    thickness = 1.dp.value,
                                    shape = Shapes.roundedCornerShape(2.dp),
                                    strokeColor = MaterialTheme.colorScheme.outlineVariant,
                                    strokeWidth = 1.dp.value
                                ),
                                padding = Dimensions.current.axisLabelVerticalPadding,
                            ),
                        ),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    scrollState = rememberVicoScrollState(scrollEnabled = true) // Enable horizontal scrolling for dense charts
                )
            } else {
                // Show a loading indicator if chart data is not yet available
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 16.dp)
                )
                Text("Fetching chart data...", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * Main Activity for the Android application.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme { // Apply your application's theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PAXGTrackerScreen() // Display the gold tracker screen
                }
            }
        }
    }
}