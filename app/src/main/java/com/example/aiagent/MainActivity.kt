package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.ui.theme.AIAGENTTheme
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.edges.rememberFadingEdges
import com.patrykandpatrick.vico.compose.chart.line.LineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.marker.rememberMarker
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.values.ChartValues
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.formatter.ValueFormatter
import com.patrykandpatrick.vico.core.marker.Marker
import com.patrykandpatrick.vico.core.shape.Shapes
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.patrykandpatrick.vico.compose.chart.marker.rememberMarkerLabelFormatter // For marker label formatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAGENTTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GoldPriceViewModel = viewModel()
                    GoldPriceTrackerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// Data models for Binance API response
data class BinancePriceResponse(
    val symbol: String,
    val price: String
)

data class KLineData(
    val openTime: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String,
    val numberOfTrades: Long,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String,
    val ignore: String
)

// Retrofit Interface
interface BinanceApiService {
    @GET("api/v3/ticker/price")
    suspend fun getCurrentPrice(@Query("symbol") symbol: String): BinancePriceResponse

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<Any>> // KLineData is complex, use List<List<Any>> for raw parsing initially
}

// Retrofit instance
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
class GoldPriceViewModel : ViewModel() {
    open val currentPrice = mutableStateOf("Loading...")
    open val statusMessage = mutableStateOf("Fetching data...")
    open val chartEntryModelProducer = ChartEntryModelProducer()
    // Use mutableStateListOf for priceHistory if you want to observe changes to the list itself within a Composable
    // For Vico, setting entries to ChartEntryModelProducer is the primary way to update the chart.
    open val priceHistory = mutableStateListOf<ChartEntry>()

    init {
        fetchCurrentPrice()
        fetchPriceHistory()
    }

    private fun fetchCurrentPrice() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.getCurrentPrice("PAXGUSDT")
                currentPrice.value = String.format(Locale.US, "%.2f USDT", response.price.toDouble())
                statusMessage.value = "Last updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
            } catch (e: Exception) {
                currentPrice.value = "Error"
                statusMessage.value = "Failed to fetch current price: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    private fun fetchPriceHistory() {
        viewModelScope.launch {
            try {
                // Fetch last 24 hours of 1-hour interval KLines (24 entries)
                val klinesRaw = RetrofitClient.instance.getKlines("PAXGUSDT", "1h", 24)

                val entries = klinesRaw.mapIndexed { index, kline ->
                    // KLineData format: [openTime, open, high, low, close, volume, ...]
                    val closePrice = (kline[4] as String).toFloat()
                    entryOf(index.toFloat(), closePrice) // X-axis as index, Y-axis as close price
                }
                priceHistory.clear()
                priceHistory.addAll(entries)
                chartEntryModelProducer.setEntries(listOf(entries)) // Set entries for the chart
                statusMessage.value = "History loaded. Last updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"

            } catch (e: Exception) {
                statusMessage.value = "Failed to fetch price history: ${e.message}"
                e.printStackTrace()
            }
        }
    }
}

// Composable for the entire screen
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice
    val statusMessage by viewModel.statusMessage
    val chartEntryModelProducer = viewModel.chartEntryModelProducer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PAXG/USDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Current Price: $currentPrice",
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

        // Chart display
        Chart(
            chart = LineChart(
                lines = listOf(
                    lineSpec(
                        lineColor = MaterialTheme.colorScheme.primary,
                        pointColor = MaterialTheme.colorScheme.primary,
                        pointSize = 4.dp
                    )
                )
            ),
            chartModelProducer = chartEntryModelProducer,
            startAxis = rememberStartAxis(
                valueFormatter = remember { GoldPriceValueFormatter() },
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onBackground,
                    background = rememberShapeComponent(shape = Shapes.pillShape, color = MaterialTheme.colorScheme.surfaceVariant),
                    padding = dimensionsOf(horizontal = 8.dp, vertical = 2.dp),
                    margins = dimensionsOf(end = 8.dp)
                ),
                axis = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                ),
                guideline = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = remember { HourAxisValueFormatter() },
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onBackground,
                    background = rememberShapeComponent(shape = Shapes.pillShape, color = MaterialTheme.colorScheme.surfaceVariant),
                    padding = dimensionsOf(horizontal = 8.dp, vertical = 2.dp),
                    margins = dimensionsOf(top = 8.dp)
                ),
                axis = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                ),
                guideline = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
            ),
            marker = rememberMarker(
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    background = rememberShapeComponent(shape = Shapes.pillShape, color = MaterialTheme.colorScheme.primaryContainer),
                    padding = dimensionsOf(horizontal = 8.dp, vertical = 4.dp),
                    margins = dimensionsOf(bottom = 4.dp)
                ),
                labelFormatter = rememberMarkerLabelFormatter { entries: List<ChartEntry> ->
                    entries.joinToString { entry ->
                        val index = entry.x.toInt()
                        // Assuming the entries are indexed from 0 to 23, where 23 is the latest (current hour)
                        // and 0 is 23 hours ago.
                        val hoursAgo = (23 - index) // Adjust this if your x-axis mapping is different
                        val simulatedDate = Date(System.currentTimeMillis() - hoursAgo * 60 * 60 * 1000)
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                            timeZone = TimeZone.getDefault()
                        }
                        val price = "%.2f".format(Locale.US, entry.y)
                        "${timeFormat.format(simulatedDate)}: ${price} USDT"
                    }
                },
                indicator = rememberLineComponent(color = MaterialTheme.colorScheme.onSurfaceVariant, thickness = 1.dp),
                guideline = rememberLineComponent(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), thickness = 1.dp)
            ),
            isZoomEnabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(top = 16.dp),
            fadingEdges = rememberFadingEdges()
        )
    }
}

// Value Formatter for the Y-axis (price)
private class GoldPriceValueFormatter : AxisValueFormatter<AxisPosition.Vertical> {
    override fun formatValue(value: Float, chartValues: ChartValues, axisPosition: AxisPosition.Vertical): String {
        return "%.2f".format(Locale.US, value)
    }
}

// Value Formatter for the X-axis (time/index)
private class HourAxisValueFormatter : AxisValueFormatter<AxisPosition.Horizontal> {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault() // Ensure correct timezone
    }

    override fun formatValue(value: Float, chartValues: ChartValues, axisPosition: AxisPosition.Horizontal): String {
        // The value (x) is an index from 0 to 23 (for 24 hours history)
        // We map this back to a relative time. Assuming index 23 is the current hour.
        val index = value.toInt()
        val hoursAgo = (23 - index)
        val date = Date(System.currentTimeMillis() - hoursAgo * 60 * 60 * 1000)
        return timeFormat.format(date)
    }
}

@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerPreview() {
    AIAGENTTheme {
        // Create a mock ViewModel or pass dummy data directly
        val mockViewModel = object : GoldPriceViewModel() {
            // Override properties to provide sample data for the preview
            override val currentPrice = mutableStateOf("3000.00 USDT")
            override val statusMessage = mutableStateOf("Preview data (Last updated: 12:34:56)")
            override val priceHistory = mutableStateListOf(
                entryOf(0f, 2900f),
                entryOf(1f, 2950f),
                entryOf(2f, 3000f),
                entryOf(3f, 2980f),
                entryOf(4f, 3010f),
                entryOf(5f, 3050f),
                entryOf(6f, 3020f),
                entryOf(7f, 3030f),
                entryOf(8f, 3010f),
                entryOf(9f, 2990f),
                entryOf(10f, 2970f),
                entryOf(11f, 2960f),
                entryOf(12f, 2980f),
                entryOf(13f, 3000f),
                entryOf(14f, 3020f),
                entryOf(15f, 3010f),
                entryOf(16f, 3005f),
                entryOf(17f, 3015f),
                entryOf(18f, 3025f),
                entryOf(19f, 3030f),
                entryOf(20f, 3035f),
                entryOf(21f, 3040f),
                entryOf(22f, 3038f),
                entryOf(23f, 3050f)
            )
            override val chartEntryModelProducer = ChartEntryModelProducer().apply {
                setEntries(listOf(priceHistory))
            }
        }
        GoldPriceTrackerScreen(viewModel = mockViewModel)
    }
}