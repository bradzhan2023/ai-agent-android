package com.example.aiagent

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.rememberLineChart
import com.patrykandpatrick.vico.compose.chart.marker.rememberMarker
import com.patrykandpatrick.vico.compose.chart.scroll.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.chart.zoom.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.marker.rememberMarkerLabelFormatter
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.ValueFormatter
import com.patrykandpatrick.vico.core.chart.entry.ChartEntry
import com.patrykandpatrick.vico.core.chart.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.chart.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.chart.entry.entryOf
import com.patrykandpatrick.vico.core.component.marker.MarkerComponent
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.shape.cornered.Corner
import com.patrykandpatrick.vico.core.component.shape.cornered.CutCornerShape
import com.patrykandpatrick.vico.core.extension.forEachPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * --- Retrofit API Service for Binance ---
 */
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<Any>> // Binance API returns array of arrays with mixed types
}

/**
 * --- Data Model for Kline (Candlestick) Data ---
 */
data class KlineData(
    val openTime: Long,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val volume: Double,
    val closeTime: Long
) {
    companion object {
        // Parses a single kline response from Binance API
        // Format: [
        //   [0] openTime (Long),
        //   [1] openPrice (String),
        //   [2] highPrice (String),
        //   [3] lowPrice (String),
        //   [4] closePrice (String),
        //   [5] volume (String),
        //   [6] closeTime (Long),
        //   ... other fields
        // ]
        fun fromBinanceResponse(response: List<Any>): KlineData {
            return KlineData(
                openTime = (response[0] as Number).toLong(), // Timestamps are Long or Double sometimes
                openPrice = (response[1] as String).toDouble(),
                highPrice = (response[2] as String).toDouble(),
                lowPrice = (response[3] as String).toDouble(),
                closePrice = (response[4] as String).toDouble(),
                volume = (response[5] as String).toDouble(),
                closeTime = (response[6] as Number).toLong()
            )
        }
    }
}

/**
 * --- ViewModel for Gold Price Tracking ---
 */
class GoldPriceViewModel : ViewModel() {

    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice.asStateFlow()

    private val _historicalPrices = MutableStateFlow<List<KlineData>>(emptyList())
    val historicalPrices: StateFlow<List<KlineData>> = _historicalPrices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApiService::class.java)
    }

    init {
        fetchPrices()
    }

    fun fetchPrices() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Fetch 100 1-hour candles for PAXGUSDT
                val klinesResponse = apiService.getKlines(
                    symbol = "PAXGUSDT",
                    interval = "1h",
                    limit = 100
                )
                val klines = klinesResponse.map { KlineData.fromBinanceResponse(it) }
                _historicalPrices.value = klines
                _currentPrice.value = klines.lastOrNull()?.closePrice

            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch prices: ${e.localizedMessage ?: "Unknown error"}"
                _currentPrice.value = null
                _historicalPrices.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

/**
 * --- Main Activity ---
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme { // Your application's Material3 theme
                GoldPriceTrackerApp()
            }
        }
    }
}

/**
 * --- Gold Price Tracker Composable App ---
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceTrackerApp(
    viewModel: GoldPriceViewModel = viewModel()
) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Vico ChartEntryModelProducer to update chart data
    val chartEntryModelProducer = remember { ChartEntryModelProducer() }

    // Update chart entries whenever historicalPrices changes
    LaunchedEffect(historicalPrices) {
        val entries = historicalPrices.map { kline ->
            // Use timestamp (milliseconds) for x-axis, close price for y-axis
            entryOf(x = kline.closeTime.toFloat(), y = kline.closePrice.toFloat())
        }
        chartEntryModelProducer.setEntries(listOf(entries))
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
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Fetching prices...", modifier = Modifier.padding(top = 8.dp))
            } else if (errorMessage != null) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = { viewModel.fetchPrices() }) {
                    Text("Retry")
                }
            } else {
                currentPrice?.let {
                    Text(
                        text = "Current PAXG Price: $%.2f".format(it),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (historicalPrices.isNotEmpty()) {
                    // Y-axis value formatter for price (e.g., "$1234.56")
                    val yAxisValueFormatter = remember {
                        object : ValueFormatter {
                            override fun formatValue(value: Float, entry: ChartEntryModel): CharSequence {
                                return "$%.2f".format(value)
                            }
                        }
                    }

                    // X-axis value formatter for timestamp (e.g., "Jan 01 15:30")
                    val xAxisValueFormatter = remember {
                        object : ValueFormatter {
                            private val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
                            override fun formatValue(value: Float, entry: ChartEntryModel): CharSequence {
                                // 'value' here is the timestamp in milliseconds (as Float)
                                return dateFormat.format(Date(value.toLong()))
                            }
                        }
                    }

                    // Marker label formatter for detailed info on touch
                    val markerLabelFormatter = rememberMarkerLabelFormatter { entries ->
                        val entry = entries.firstOrNull()?.chartEntry
                        if (entry != null) {
                            val date = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(entry.x.toLong()))
                            val price = "%.2f".format(entry.y)
                            "Date: $date\nPrice: $$price"
                        } else {
                            ""
                        }
                    }
                    val marker = rememberMarker(labelFormatter = markerLabelFormatter)

                    Chart(
                        chart = rememberLineChart(),
                        modelProducer = chartEntryModelProducer,
                        startAxis = rememberStartAxis(
                            valueFormatter = yAxisValueFormatter,
                            label = axisLabelComponent(color = MaterialTheme.colorScheme.onSurface),
                            tick = rememberLineComponent(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                thickness = 1.dp
                            ),
                            guideline = rememberLineComponent(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                thickness = 1.dp
                            ),
                            titleComponent = rememberTextComponent(
                                color = MaterialTheme.colorScheme.onBackground,
                                textSize = 12.sp,
                                padding = dimensionsOf(end = 4.dp)
                            ),
                            title = "Price (USDT)"
                        ),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = xAxisValueFormatter,
                            labelRotationDegrees = 45f, // Rotate labels for better readability
                            label = axisLabelComponent(color = MaterialTheme.colorScheme.onSurface),
                            tick = rememberLineComponent(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                thickness = 1.dp
                            ),
                            guideline = rememberLineComponent(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                thickness = 1.dp
                            ),
                            titleComponent = rememberTextComponent(
                                color = MaterialTheme.colorScheme.onBackground,
                                textSize = 12.sp,
                                padding = dimensionsOf(top = 4.dp)
                            ),
                            title = "Time"
                        ),
                        marker = marker,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(top = 8.dp)
                    )
                } else if (currentPrice == null) {
                    Text(
                        text = "No price data available. Please check your network connection or try again.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * --- Minimal Theme for AiAgent App ---
 * This provides a basic Material3 theme structure for direct compilation.
 * In a real project, these would typically be in separate files (Theme.kt, Color.kt, Type.kt).
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF673AB7), // DeepPurple 500
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0BCFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF9C27B0), // Purple 500
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFF3F51B5), // Indigo 500
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEADDFF),
    onTertiaryContainer = Color(0xFF381E72),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    inverseOnSurface = Color(0xFFF4EFF4),
    inverseSurface = Color(0xFF313033),
    inversePrimary = Color(0xFFD0BCFF),
    surfaceTint = Color(0xFF673AB7),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
)

// A default typography for the theme
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    )
)

@Composable
fun AiAgentTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * --- Preview ---
 */
@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerPreview() {
    AiAgentTheme {
        GoldPriceTrackerApp()
    }
}