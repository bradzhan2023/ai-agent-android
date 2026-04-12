package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.ui.theme.AiAgentTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Ktor and kotlinx.serialization imports for custom serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor

// Vico Chart Imports
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.chart.model.lineSeries
import com.patrykandpatrick.vico.compose.chart.model.rememberCartesianChartModelProducer
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.component.shape.Shapes
import com.patrykandpatrick.vico.compose.dimensions.Dimensions
import com.patrykandpatrick.vico.compose.scroll.rememberVicoScrollState
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.entryOf // Used for creating chart entries

// Data classes for Binance API response
// The Binance Klines API returns a list of lists, where each inner list contains various types.
// A custom serializer for `Any` is needed because Ktor's default JSON serializer doesn't
// handle `List<List<Any>>` directly without type information.
// This serializer attempts to convert string values to Long/Double if possible, otherwise keeps them as String.
object AnyAsStringSerializer : kotlinx.serialization.KSerializer<Any> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AnyAsString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Any {
        val stringValue = decoder.decodeString()
        return stringValue.toLongOrNull() ?: stringValue.toDoubleOrNull() ?: stringValue
    }

    override fun serialize(encoder: Encoder, value: Any) {
        encoder.encodeString(value.toString())
    }
}

// Data class to match Binance Klines API response structure
// The `@Serializable(with = AnyAsStringSerializer::class)` annotation tells Ktor how to handle `Any` type.
@Serializable
data class BinanceKlinesResponse(
    val data: List<List<@Serializable(with = AnyAsStringSerializer::class) Any>>
)


class PAXGViewModel : ViewModel() {
    private val _paxgPrice = MutableStateFlow<String>("Loading...")
    val paxgPrice: StateFlow<String> = _paxgPrice.asStateFlow()

    private val _chartData = MutableStateFlow<List<Pair<Long, Float>>>(emptyList())
    val chartData: StateFlow<List<Pair<Long, Float>>> = _chartData.asStateFlow()

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    init {
        fetchPAXGPriceAndChartData()
    }

    fun fetchPAXGPriceAndChartData() {
        viewModelScope.launch {
            try {
                // Fetch current price
                val currentPriceUrl = "https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT"
                val priceResponse = client.get(currentPriceUrl).bodyAsText()
                val jsonResponse = Json.parseToJsonElement(priceResponse).jsonObject
                val price = jsonResponse["price"]?.jsonPrimitive?.content ?: "N/A"
                _paxgPrice.value = price

                // Fetch kline data for charting (e.g., last 24 hours, 1-hour interval)
                val klinesUrl = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24"
                val klinesResponseRaw = client.get(klinesUrl).bodyAsText()

                // Deserialize raw klines response using the custom serializer for 'Any'
                val rawKlines: List<List<Any>> = Json.decodeFromString(klinesResponseRaw)

                val parsedChartData = rawKlines.mapNotNull { kline ->
                    if (kline.size >= 5) { // Ensure enough elements for timestamp and close price
                        try {
                            // The 0th element is open time (Long), 4th is close price (String)
                            val timestamp = (kline[0] as? Long) ?: return@mapNotNull null
                            val closePriceString = kline[4] as? String ?: return@mapNotNull null
                            val closePrice = closePriceString.toFloat()
                            Pair(timestamp, closePrice)
                        } catch (e: Exception) {
                            println("Error parsing kline data point: $kline, Error: ${e.message}")
                            null
                        }
                    } else {
                        null
                    }
                }
                _chartData.value = parsedChartData

            } catch (e: Exception) {
                _paxgPrice.value = "Error: ${e.localizedMessage}"
                _chartData.value = emptyList() // Clear chart on error
                println("Error fetching PAXG data: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { // This provides the @Composable context
            AiAgentTheme {
                PAXGPriceTrackerApp()
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PAXGPriceTrackerApp(viewModel: PAXGViewModel = viewModel()) {
    val paxgPrice by viewModel.paxgPrice.collectAsState()
    val chartData by viewModel.chartData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "PAXG/USDT Price:",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$$paxgPrice",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            if (paxgPrice == "Loading...") {
                CircularProgressIndicator()
            } else if (chartData.isNotEmpty()) {
                PAXGPriceChart(chartData)
            } else {
                Text("No chart data available or error occurred.", color = Color.Red)
            }
        }
    }
}

@Composable
fun PAXGPriceChart(chartData: List<Pair<Long, Float>>) {
    // Convert List<Pair<Long, Float>> to Vico's entry model
    val modelProducer = rememberCartesianChartModelProducer()
    LaunchedEffect(chartData) {
        modelProducer.tryRunTransaction {
            val entries = chartData.mapIndexed { index, pair ->
                // Vico's x-axis is float. We use index for position and a formatter for actual time labels.
                entryOf(index.toFloat(), pair.second)
            }
            lineSeries { series(entries) }
        }
    }

    // X-axis formatter for timestamps (assuming chartData.first is timestamp)
    val xAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val timestamp = chartData.getOrNull(value.toInt())?.first ?: return@AxisValueFormatter ""
        val date = Date(timestamp)
        // Use UTC for Binance kline open times
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        formatter.format(date)
    }

    // Y-axis formatter for price
    val yAxisValueFormatter = AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
        "%.2f".format(value)
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lines = listOf(
                    rememberLineComponent(
                        color = MaterialTheme.colorScheme.primary,
                        thickness = 2.dp,
                        shape = Shapes.pillShape // Example shape for the line itself
                    )
                )
            ),
            startAxis = rememberStartAxis(
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onBackground,
                    background = null, // No background for labels
                    lineCount = 1,
                    padding = Dimensions.current.axisLabelVerticalPadding,
                    margins = Dimensions.current.axisLabelMargins
                ),
                axis = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 1.dp
                ),
                tick = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                ),
                valueFormatter = yAxisValueFormatter,
                guideline = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            ),
            bottomAxis = rememberBottomAxis(
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onBackground,
                    background = null, // No background for labels
                    lineCount = 1,
                    padding = Dimensions.current.axisLabelVerticalPadding,
                    margins = Dimensions.current.axisLabelMargins
                ),
                axis = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 1.dp
                ),
                tick = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                ),
                valueFormatter = xAxisValueFormatter,
                guideline = rememberLineComponent(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            )
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(), // Allows horizontal scrolling for the chart
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AiAgentTheme {
        PAXGPriceTrackerApp()
    }
}