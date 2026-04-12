package com.example.aiagent

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.line.rememberLineChart
import com.patrykandpatrick.vico.compose.chart.marker.rememberMarker
import com.patrykandpatrick.vico.compose.chart.marker.rememberMarkerLabelFormatter
import com.patrykandpatrick.vico.compose.component.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.component.shape.Shapes
import com.patrykandpatrick.vico.compose.component.shape.shader.verticalGradient
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.formatter.ValueFormatter
import com.patrykandpatrick.vico.core.model.ChartEntryModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data model for a single price entry, based on the assumption from `it.price` usage.
// This will be populated by processing the raw klines data.
data class BinancePriceEntry(
    val price: String, // Close price
    val time: Long // Open time (timestamp in milliseconds)
)

// Binance Klines response is a list of lists of strings, where each inner list represents a kline.
// The relevant fields are index 0 (Open time) and index 4 (Close price).
typealias KlineData = List<List<String>>

interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): KlineData
}

class GoldPriceViewModel : ViewModel() {

    private val _currentPrice = MutableStateFlow("N/A")
    val currentPrice: StateFlow<String> = _currentPrice.asStateFlow()

    private val _statusMessage = MutableStateFlow("Fetching data...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _chartEntryModelProducer = ChartEntryModelProducer()
    val chartEntryModelProducer: ChartEntryModelProducer = _chartEntryModelProducer

    // To hold the raw BinancePriceEntry data for more detailed formatting if needed
    private val _priceHistory = MutableStateFlow<List<BinancePriceEntry>>(emptyList())
    val priceHistory: StateFlow<List<BinancePriceEntry>> = _priceHistory.asStateFlow()

    private val binanceApiService: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApiService::class.java)
    }

    init {
        startPriceTracking()
    }

    private fun startPriceTracking() {
        viewModelScope.launch {
            while (true) {
                fetchGoldPriceHistory()
                delay(300000) // Fetch every 5 minutes (300,000 milliseconds)
            }
        }
    }

    private suspend fun fetchGoldPriceHistory() {
        try {
            _statusMessage.value = "Fetching data..."
            val klines = binanceApiService.getKlines("PAXGUSDT", "1d", 30) // Fetch last 30 daily klines
            if (klines.isNotEmpty()) {
                // Map raw klines data to BinancePriceEntry objects
                val entries = klines.mapNotNull { kline ->
                    if (kline.size >= 5) {
                        val timestamp = kline[0].toLong() // Open time
                        val price = kline[4] // Close price
                        BinancePriceEntry(price = price, time = timestamp)
                    } else null
                }
                _priceHistory.value = entries // Update the raw history state flow

                val latestPrice = entries.lastOrNull()?.price ?: "N/A"
                _currentPrice.value = latestPrice
                _statusMessage.value = "Updated: $latestPrice"

                // Prepare data for the chart
                val chartEntries = entries.mapIndexed { index, entry ->
                    entryOf(index.toFloat(), entry.price.toFloat())
                }
                _chartEntryModelProducer.setEntries(listOf(chartEntries))

            } else {
                _currentPrice.value = "N/A"
                _statusMessage.value = "No data received."
                _chartEntryModelProducer.setEntries(emptyList())
            }
        } catch (e: Exception) {
            Log.e("GoldPriceViewModel", "Error fetching PAXGUSDT price: ${e.message}", e)
            _statusMessage.value = "Error: ${e.message}"
            _currentPrice.value = "Error"
        }
    }
}

class MainActivity : ComponentActivity() {
    private val goldPriceViewModel: GoldPriceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIagentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceApp(goldPriceViewModel) // Pass the viewModel instance
                }
            }
        }
    }
}

@Composable
fun GoldPriceApp(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val chartEntryModelProducer = viewModel.chartEntryModelProducer
    val priceHistory by viewModel.priceHistory.collectAsState() // Observe raw price history for formatters

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Binance PAXGUSDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Current Price: $currentPrice USDT",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Vico Chart
        if (priceHistory.isNotEmpty() && chartEntryModelProducer.getModel()?.entries?.isNotEmpty() == true) {
            val dateTimeFormatter = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

            // Y-axis value formatter
            val yAxisValueFormatter = remember {
                object : ValueFormatter {
                    override fun formatValue(value: Float, chartEntry: ChartEntry, chartEntryModel: ChartEntryModel): CharSequence {
                        return "$%.2f".format(value)
                    }
                }
            }

            // X-axis value formatter
            val xAxisValueFormatter = remember(priceHistory) {
                object : ValueFormatter {
                    override fun formatValue(value: Float, chartEntry: ChartEntry, chartEntryModel: ChartEntryModel): CharSequence {
                        // value corresponds to the index (0, 1, 2...)
                        // Get the actual timestamp from priceHistory using this index
                        val index = value.toInt()
                        return if (index >= 0 && index < priceHistory.size) {
                            dateTimeFormatter.format(Date(priceHistory[index].time))
                        } else ""
                    }
                }
            }

            val markerLabelFormatter = rememberMarkerLabelFormatter { entry, model ->
                val timestamp = (entry as? ChartEntry)?.let {
                    if (it.x.toInt() >= 0 && it.x.toInt() < priceHistory.size) {
                        priceHistory[it.x.toInt()].time
                    } else null
                }
                val dateString = timestamp?.let { dateTimeFormatter.format(Date(it)) } ?: "N/A"
                "Date: $dateString\nPrice: $%.2f".format(entry.y)
            }


            Chart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                chart = rememberLineChart(
                    lines = listOf(
                        lineSpec(
                            lineColor = MaterialTheme.colorScheme.primary,
                            lineBrush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                )
                            ),
                        )
                    )
                ),
                chartModelProducer = chartEntryModelProducer,
                startAxis = rememberStartAxis(
                    label = axisLabelComponent(
                        color = MaterialTheme.colorScheme.onSurface,
                        textSize = 10.sp,
                        background = rememberShapeComponent(Shapes.pillShape, MaterialTheme.colorScheme.surfaceVariant),
                        padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp),
                        margins = dimensionsOf(end = 8.dp)
                    ),
                    axis = rememberLineComponent(MaterialTheme.colorScheme.outline, 1.dp),
                    tick = rememberLineComponent(MaterialTheme.colorScheme.outlineVariant, 1.dp),
                    guideline = rememberLineComponent(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), 1.dp),
                    valueFormatter = yAxisValueFormatter,
                    itemPlacer = AxisItemPlacer.default(maxItemCount = 5) // Limit number of labels on Y-axis
                ),
                bottomAxis = rememberBottomAxis(
                    label = axisLabelComponent(
                        color = MaterialTheme.colorScheme.onSurface,
                        textSize = 10.sp,
                        background = rememberShapeComponent(Shapes.pillShape, MaterialTheme.colorScheme.surfaceVariant),
                        padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp),
                        margins = dimensionsOf(top = 8.dp)
                    ),
                    axis = rememberLineComponent(MaterialTheme.colorScheme.outline, 1.dp),
                    tick = rememberLineComponent(MaterialTheme.colorScheme.outlineVariant, 1.dp),
                    guideline = rememberLineComponent(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), 1.dp),
                    valueFormatter = xAxisValueFormatter,
                    itemPlacer = AxisItemPlacer.default(maxItemCount = 7, addEuclideanAxisValue = true) // Limit number of labels on X-axis
                ),
                marker = rememberMarker(labelFormatter = markerLabelFormatter)
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Loading chart data...", modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// Placeholder for Theme, if not provided. Assumes a basic Material3 theme.
@Composable
fun AIagentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(), // Or darkColorScheme()
        content = content
    )
}

// Add a sample preview for the Composable
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AIagentTheme {
        // Create a dummy ViewModel for preview purposes
        val previewViewModel = object : GoldPriceViewModel() {
            override val currentPrice = MutableStateFlow("1950.75").asStateFlow()
            override val statusMessage = MutableStateFlow("Preview data").asStateFlow()
            // Populate chart data for preview
            private val _previewChartEntryModelProducer = ChartEntryModelProducer()
            override val chartEntryModelProducer = _previewChartEntryModelProducer
            private val _previewPriceHistory = MutableStateFlow<List<BinancePriceEntry>>(emptyList())
            override val priceHistory = _previewPriceHistory.asStateFlow()

            init {
                viewModelScope.launch {
                    val dummyEntries = List(30) { i ->
                        BinancePriceEntry(
                            price = (1900 + i * 2 + Math.random() * 20).toFloat().toString(),
                            time = System.currentTimeMillis() - (29 - i) * 24 * 60 * 60 * 1000L
                        )
                    }
                    _previewPriceHistory.value = dummyEntries
                    _previewChartEntryModelProducer.setEntries(listOf(
                        dummyEntries.mapIndexed { index, entry -> entryOf(index.toFloat(), entry.price.toFloat()) }
                    ))
                }
            }
        }
        GoldPriceApp(previewViewModel)
    }
}