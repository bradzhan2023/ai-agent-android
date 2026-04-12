package com.example.aiagent

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // Correct import for stringResource
import androidx.compose.ui.tooling.preview.Preview // Correct import for Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel // Correct import for viewModel()
import com.example.aiagent.ui.theme.AiAgentTheme // Assuming this is your app's theme
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.* // Correct Ktor Android engine import
import io.ktor.client.plugins.contentnegotiation.* // Correct Ktor ContentNegotiation import
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.* // Correct Ktor kotlinx.serialization.json import
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.* // Correct kotlinx.serialization import
import kotlinx.serialization.descriptors.* // Correct kotlinx.serialization.descriptors import
import kotlinx.serialization.encoding.* // Correct kotlinx.serialization.encoding import
import kotlinx.serialization.json.* // Correct kotlinx.serialization.json import
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Vico Chart imports (all previously unresolved references should be covered here)
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.component.shape.Shapes
import com.patrykandpatrick.vico.compose.dimensions.Dimensions
import com.patrykandpatrick.vico.compose.scroll.rememberVicoScrollState
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.model.lineSeries
import com.patrykandpatrick.vico.core.model.rememberCartesianChartModelProducer

// Custom serializer for Instant (epoch milliseconds for Binance)
@OptIn(ExperimentalSerializationApi::class) // Mark for ExperimentalSerializationApi if using PrimitiveKind directly
object InstantEpochSecondsSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant {
        // Binance kline open/close times are typically in milliseconds
        return Instant.ofEpochMilli(decoder.decodeLong())
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }
}

// Data class to represent a Kline candle, used for strong typing,
// although the Binance API raw response for klines is a List<List<Any>>
// and requires manual parsing of primitives from the array.
// This data class would be used if the API returned objects like {"openTime": 123, "close": "123.45"}
@Serializable
data class Kline(
    @Serializable(with = InstantEpochSecondsSerializer::class) val openTime: Instant,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
    @Serializable(with = InstantEpochSecondsSerializer::class) val closeTime: Instant,
    val quoteAssetVolume: String,
    val numberOfTrades: Long,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String,
    val ignore: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use the viewModel() composable function to create/retrieve the ViewModel
                    PAXGChart(viewModel = viewModel())
                }
            }
        }
    }
}

class PAXGViewModel : ViewModel() {
    // StateFlow to hold the list of (timestamp, price) pairs for the chart
    private val _paxgPrices = MutableStateFlow<List<Pair<Instant, Float>>>(emptyList())
    val paxgPrices: StateFlow<List<Pair<Instant, Float>>> = _paxgPrices

    // Ktor HTTP client setup
    private val httpClient = HttpClient(Android) {
        // Install ContentNegotiation plugin for JSON serialization/deserialization
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // Ignore unknown JSON keys to prevent crashes
                isLenient = true // Allow malformed JSON (e.g., unquoted strings)
            })
        }
    }

    init {
        fetchPAXGPrices()
    }

    private fun fetchPAXGPrices() {
        viewModelScope.launch {
            try {
                // Fetch 1-hour klines for PAXGUSDT from Binance API
                // `limit=500` fetches the last 500 data points (approx. 20 days for 1h interval)
                val response: String = httpClient.get("https://api.binance.com/api/v3/klines") {
                    url {
                        parameters.append("symbol", "PAXGUSDT")
                        parameters.append("interval", "1h")
                        parameters.append("limit", "500")
                    }
                }.body()

                Log.d("PAXGViewModel", "Raw API Response: $response")

                // Binance klines API returns a JSON array of arrays, e.g., [[...],[...]]
                // We need to manually parse elements from each inner array.
                val jsonArray = Json.parseToJsonElement(response).jsonArray

                val klinesData = jsonArray.mapNotNull { element ->
                    element.jsonArray.let { klineArray ->
                        if (klineArray.size > 4) { // Ensure at least openTime and close price exist
                            try {
                                val openTimeMillis = klineArray[0].jsonPrimitive.long // 0: Open time in milliseconds
                                val closePrice = klineArray[4].jsonPrimitive.content.toFloat() // 4: Close price as string, convert to float
                                Instant.ofEpochMilli(openTimeMillis) to closePrice
                            } catch (e: Exception) {
                                Log.e("PAXGViewModel", "Error parsing kline element: $klineArray", e)
                                null // Skip this kline if parsing fails
                            }
                        } else {
                            Log.w("PAXGViewModel", "Skipping kline with insufficient data: $klineArray")
                            null
                        }
                    }
                }
                _paxgPrices.value = klinesData.sortedBy { it.first } // Ensure data is sorted by time
            } catch (e: Exception) {
                Log.e("PAXGViewModel", "Error fetching PAXG prices: ${e.message}", e)
                // In a real app, you might want to update an error state to show on UI
            }
        }
    }
}

@Composable
fun PAXGChart(viewModel: PAXGViewModel) {
    // Collect price data from the ViewModel's StateFlow
    val paxgPrices by viewModel.paxgPrices.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            // Use stringResource for localized text
            text = stringResource(R.string.paxg_price_tracker),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (paxgPrices.isEmpty()) {
            CircularProgressIndicator()
            Text("Loading PAXG data...")
        } else {
            // Vico Chart Model Producer
            val modelProducer = rememberCartesianChartModelProducer(
                entries = listOf(
                    lineSeries { // Define a single line series for the chart
                        series(paxgPrices.mapIndexed { index, pair ->
                            entryOf(index.toFloat(), pair.second) // x-axis is index, y-axis is price
                        })
                    }
                )
            )

            // Date and Time formatters for the X-axis labels
            val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
            val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault()) }

            // Formatter for X-axis (time) labels
            val xAxisValueFormatter =
                AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                    val index = value.toInt()
                    if (index >= 0 && index < paxgPrices.size) {
                        val instant = paxgPrices[index].first
                        // Format based on time (e.g., HH:mm)
                        timeFormatter.format(instant.atZone(ZoneId.systemDefault()))
                    } else {
                        ""
                    }
                }

            // Formatter for Y-axis (price) labels
            val yAxisValueFormatter =
                AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
                    "%.2f".format(value) // Format price to two decimal places
                }

            // CartesianChartHost to display the chart
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineSeries { // Define the actual line series data
                            series(paxgPrices.mapIndexed { index, pair ->
                                entryOf(index.toFloat(), pair.second)
                            })
                        },
                        // Customize line appearance
                        line = rememberLineComponent(
                            color = MaterialTheme.colorScheme.primary,
                            thickness = 2.dp,
                            shape = Shapes.pillShape // Apply a pill shape to the line
                        )
                    ),
                    startAxis = rememberStartAxis(
                        valueFormatter = yAxisValueFormatter,
                        label = rememberTextComponent(
                            color = MaterialTheme.colorScheme.onSurface,
                            background = null,
                            padding = Dimensions.of(horizontal = 4.dp, vertical = 2.dp)
                        ),
                        axis = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            thickness = 1.dp
                        ),
                        tick = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp
                        ),
                        guideline = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp
                        )
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = xAxisValueFormatter,
                        label = rememberTextComponent(
                            color = MaterialTheme.colorScheme.onSurface,
                            background = null,
                            padding = Dimensions.of(horizontal = 4.dp, vertical = 2.dp)
                        ),
                        axis = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            thickness = 1.dp
                        ),
                        tick = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp
                        ),
                        guideline = rememberLineComponent(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp
                        )
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp), // Set chart height
                scrollState = rememberVicoScrollState() // Allow horizontal scrolling for the chart
            )
            // Display the latest price below the chart
            val latestPrice = paxgPrices.lastOrNull()?.second
            if (latestPrice != null) {
                Text(
                    text = "Current PAXG price: $%.2f".format(latestPrice),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable // Add @Composable annotation to the preview function
fun GreetingPreview() {
    AiAgentTheme {
        // For preview purposes, we can't easily use a real ViewModel.
        // Provide a placeholder or a mock UI representation.
        Text("PAXG Chart Preview (requires mock ViewModel data)")
    }
}