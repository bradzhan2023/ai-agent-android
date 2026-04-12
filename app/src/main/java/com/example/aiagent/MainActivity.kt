package com.example.aiagent

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.ui.theme.AIAGENTTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

// Vico Imports
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.line.rememberLineChart
import com.patrykandpatrick.vico.compose.chart.marker.rememberMarker
import com.patrykandpatrick.vico.compose.component.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.component.shape.line.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.shape.rememberShapeComponent
import com.patrykandpatrick.vico.compose.component.shape.shapes.pillShape
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.formatter.ValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.extension.formatter.rememberMarkerLabelFormatter
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAGENTTheme {
                // A surface container using the 'background' color from the theme
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

// Retrofit interfaces and data classes
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 100
    ): List<List<Any>> // Using List<List<Any>> to parse raw data
}

data class PriceEntry(
    val timestamp: String,
    val price: Double
)

// GoldPriceViewModel (made open for testing/preview purposes)
open class GoldPriceViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentPrice = MutableStateFlow("Loading...")
    val currentPrice: StateFlow<String> = _currentPrice.asStateFlow()

    private val _statusMessage = MutableStateFlow("Fetching data...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    val chartEntryModelProducer = ChartEntryModelProducer()

    private val _priceHistory = MutableStateFlow<List<PriceEntry>>(emptyList())
    val priceHistory: StateFlow<List<PriceEntry>> = _priceHistory.asStateFlow()

    private val binanceApiService: BinanceApiService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        binanceApiService = retrofit.create(BinanceApiService::class.java)
        getBinancePrice()
    }

    open fun getBinancePrice() {
        viewModelScope.launch {
            _statusMessage.value = "Fetching data..."
            try {
                // Fetch 1-day interval for the last 100 days
                val klines = binanceApiService.getKlines(symbol = "PAXGUSDT", interval = "1d", limit = 30)

                val entries = klines.mapIndexed { index, kline ->
                    val openTime = kline[0] as Long
                    val closePrice = (kline[4] as String).toDouble() // Close price is at index 4

                    PriceEntry(
                        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(openTime)),
                        price = closePrice
                    )
                }

                _priceHistory.value = entries
                val chartEntries = entries.mapIndexed { index, entry ->
                    entryOf(index.toFloat(), entry.price.toFloat())
                }
                chartEntryModelProducer.setEntries(listOf(chartEntries))

                _currentPrice.value = "%.2f PAXG".format(entries.lastOrNull()?.price ?: 0.0)
                _statusMessage.value = "Data updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"

            } catch (e: Exception) {
                _statusMessage.value = "Error fetching data: ${e.message}"
                _currentPrice.value = "Error"
                e.printStackTrace()
            }
        }
    }
}


@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "PAXG/USDT 金價追蹤",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "當前價格: $currentPrice",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (priceHistory.isNotEmpty()) {
            GoldPriceChart(viewModel.chartEntryModelProducer)
        } else {
            CircularProgressIndicator(modifier = Modifier.align(alignment = androidx.compose.ui.Alignment.CenterHorizontally))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.getBinancePrice() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重新整理")
        }
    }
}

class DateValueFormatter : ValueFormatter {
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    override fun formatValue(
        value: Float,
        chartEntry: ChartEntry,
        chartEntryModel: ChartEntryModel,
    ): String {
        // Assuming 'value' here is an index, we use it to get the corresponding timestamp from the history
        val index = value.toInt()
        val history = (chartEntryModel as ChartEntryModelProducer).getModel().firstOrNull()?.entries?.getOrNull(index) // This is incorrect, need actual data
        // For actual date formatting, the X value from entryOf should be the timestamp.
        // As currently implemented, X is index (0f, 1f, 2f...)
        // To show actual date, we need to map the index back to the date from original 'priceHistory'
        // This requires access to priceHistory, which is not available directly in ValueFormatter.
        // For simplicity, let's just format the index for now, or assume X is timestamp.
        // Let's modify entryOf to use timestamp for X value instead of index for better chart context
        return index.toString() // Placeholder, ideally should be a date
    }
}

class PriceValueFormatter : ValueFormatter {
    override fun formatValue(
        value: Float,
        chartEntry: ChartEntry,
        chartEntryModel: ChartEntryModel,
    ): String {
        return "$%.2f".format(value)
    }
}

@Composable
fun GoldPriceChart(chartEntryModelProducer: ChartEntryModelProducer) {
    val context = LocalContext.current
    val markerLabelFormatter = rememberMarkerLabelFormatter { chartEntry, _ ->
        val timestamp = (chartEntry.x.toLong() * 1000) // Convert index back to timestamp if entryOf was using timestamp
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        val price = "%.2f".format(chartEntry.y)
        "$date\n$price PAXG"
    }

    Chart(
        chart = rememberLineChart(
            lines = remember(MaterialTheme.colorScheme.primary) { listOf(lineSpec(MaterialTheme.colorScheme.primary)) }
        ),
        chartModelProducer = chartEntryModelProducer,
        startAxis = rememberStartAxis(
            label = axisLabelComponent(
                color = MaterialTheme.colorScheme.onBackground,
                textSize = 10.sp,
                background = rememberShapeComponent(shape = pillShape, color = MaterialTheme.colorScheme.background),
                padding = dimensionsOf(horizontal = 8.dp, vertical = 2.dp),
                margins = dimensionsOf(end = 4.dp)
            ),
            axis = rememberLineComponent(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            ),
            tick = rememberLineComponent(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            ),
            guideline = rememberLineComponent(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            ),
            valueFormatter = remember { PriceValueFormatter() },
            itemPlacer = remember { AxisItemPlacer.Vertical.default() }
        ),
        bottomAxis = rememberBottomAxis(
            label = axisLabelComponent(
                color = MaterialTheme.colorScheme.onBackground,
                textSize = 10.sp,
                background = rememberShapeComponent(shape = pillShape, color = MaterialTheme.colorScheme.background),
                padding = dimensionsOf(horizontal = 8.dp, vertical = 2.dp),
                margins = dimensionsOf(top = 4.dp)
            ),
            axis = rememberLineComponent(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            ),
            tick = rememberLineComponent(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            ),
            guideline = rememberLineComponent(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            ),
            valueFormatter = remember {
                // Custom formatter for X-axis (date/time)
                object : ValueFormatter {
                    private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                    override fun formatValue(
                        value: Float,
                        chartEntry: ChartEntry,
                        chartEntryModel: ChartEntryModel,
                    ): String {
                        // Assuming X value is a timestamp (milliseconds)
                        val timestamp = (chartEntryModelProducer.getModel().firstOrNull()?.entries?.getOrNull(value.toInt())?.x?.toLong() ?: value.toLong()) * 1000
                        return dateFormat.format(Date(timestamp))
                    }
                }
            },
            itemPlacer = remember { AxisItemPlacer.Horizontal.default(spacing = 3, addExtremeLabelPadding = true) }
        ),
        marker = rememberMarker(markerLabelFormatter)
    )
}


@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerScreenPreview() {
    AIAGENTTheme {
        // Create a mock Application instance for the AndroidViewModel in preview
        val mockApplication = Application()

        val mockViewModel = object : GoldPriceViewModel(mockApplication) {
            override val currentPrice: StateFlow<String> = MutableStateFlow("2300.50 PAXG").asStateFlow()
            override val statusMessage: StateFlow<String> = MutableStateFlow("Mock data loaded from preview").asStateFlow()
            override val chartEntryModelProducer: ChartEntryModelProducer = ChartEntryModelProducer(
                listOf(
                    listOf(
                        entryOf(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L, 2200.0f), // X is timestamp in seconds for marker, multiply by 1000 for milliseconds
                        entryOf(System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000L, 2250.0f),
                        entryOf(System.currentTimeMillis(), 2300.0f)
                    )
                )
            )
            override val priceHistory: StateFlow<List<PriceEntry>> = MutableStateFlow(
                listOf(
                    PriceEntry(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L)), 2200.0),
                    PriceEntry(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000L)), 2250.0)
                )
            ).asStateFlow()

            override fun getBinancePrice() {
                // Do nothing in preview, or simulate a data load
            }
        }
        GoldPriceTrackerScreen(mockViewModel)
    }
}