package com.example.aiagent

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// --- 1. Data Models ---
@Serializable
data class BinanceTicker(
    @SerialName("symbol") val symbol: String,
    @SerialName("price") val price: String
)

// KLine data structure: [open time, open, high, low, close, volume, close time, quote asset volume, number of trades, taker buy base asset volume, taker buy quote asset volume, ignore]
// We only care about open time and close price for this chart.
typealias KLineData = List<List<String>>

// --- 2. API Service ---
interface BinanceApiService {
    @GET("ticker/price")
    suspend fun getTickerPrice(@Query("symbol") symbol: String): BinanceTicker

    @GET("klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): KLineData
}

// --- 3. Retrofit Instance ---
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/api/v3/"

    private val json = Json { ignoreUnknownKeys = true }

    val apiService: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BinanceApiService::class.java)
    }
}

// --- 4. ViewModel ---
sealed class UiState {
    object Loading : UiState()
    data class Success(val currentPrice: String, val historicalPrices: List<PricePoint>) : UiState()
    data class Error(val message: String) : UiState()
}

data class PricePoint(val timestamp: Long, val price: Float)

class PriceTrackerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    init {
        fetchPriceData()
    }

    fun fetchPriceData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val currentTicker = RetrofitClient.apiService.getTickerPrice("PAXGUSDT")
                // Fetch last 24 hours of 1-hour interval data
                val klines = RetrofitClient.apiService.getKlines("PAXGUSDT", "1h", 24)

                val historicalPrices = klines.mapNotNull { kline ->
                    if (kline.size > 4) { // Ensure enough data points (open time, open, high, low, close)
                        val openTime = kline[0].toLong() // Open time in milliseconds
                        val closePrice = kline[4].toFloatOrNull() // Close price
                        if (closePrice != null) {
                            PricePoint(openTime, closePrice)
                        } else null
                    } else null
                }

                _uiState.value = UiState.Success(currentTicker.price, historicalPrices)
            } catch (e: IOException) {
                _uiState.value = UiState.Error("Network error: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = UiState.Error("HTTP error: ${e.code()} - ${e.message()}")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }
}

// --- 5. MainActivity and Composable UI ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PriceTrackerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceTrackerScreen(viewModel: PriceTrackerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT Price Tracker") })
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
            when (uiState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                    Text("Loading price data...", modifier = Modifier.padding(top = 8.dp))
                }
                is UiState.Success -> {
                    val successState = uiState as UiState.Success
                    Text(
                        text = "Current PAXGUSDT Price: $${successState.currentPrice}",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    PriceLineChart(successState.historicalPrices)
                }
                is UiState.Error -> {
                    val errorState = uiState as UiState.Error
                    Text(
                        text = "Error: ${errorState.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { viewModel.fetchPriceData() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun PriceLineChart(historicalPrices: List<PricePoint>) {
    val context = LocalContext.current
    val chartData = remember(historicalPrices) {
        historicalPrices.mapIndexed { index, pricePoint ->
            Entry(index.toFloat(), pricePoint.price, pricePoint) // Store PricePoint in data for custom marker
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false // Disable chart description
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                    valueFormatter = object : ValueFormatter() {
                        private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < historicalPrices.size) {
                                mFormat.format(Date(historicalPrices[index].timestamp))
                            } else {
                                ""
                            }
                        }
                    }
                    labelCount = 5 // Show roughly 5 labels
                    setCenterAxisLabels(true)
                }

                axisLeft.apply {
                    setDrawGridLines(false)
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                    // Set min/max for better scaling, add some padding
                    axisMinimum = historicalPrices.minOfOrNull { it.price }?.minus(
                        historicalPrices.minOfOrNull { it.price }?.times(0.01f) ?: 0f
                    ) ?: 0f
                    axisMaximum = historicalPrices.maxOfOrNull { it.price }?.plus(
                        historicalPrices.maxOfOrNull { it.price }?.times(0.01f) ?: 0f
                    ) ?: 10000f
                }

                axisRight.isEnabled = false // Disable right Y-axis

                legend.apply {
                    form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
                    textColor = Color.BLACK
                    verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.LEFT
                    orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }

                // Custom marker view for showing details on touch
                val mv = CustomMarkerView(context)
                mv.chartView = this // For proper positioning
                marker = mv
            }
        },
        update = { chart ->
            if (chartData.isNotEmpty()) {
                val dataSet = LineDataSet(chartData, "PAXGUSDT Price").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on points
                    setDrawValues(false) // Don't draw values on the line
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                    setDrawFilled(true) // Fill area below line
                    fillColor = Color.BLUE
                    fillAlpha = 80
                }

                chart.data = LineData(dataSet)
                chart.invalidate() // Refresh chart
                chart.animateX(1000) // Animate chart
            } else {
                chart.data = null
                chart.invalidate()
            }
        }
    )
}

// --- 6. Custom MarkerView for MPAndroidChart ---
// This class creates a simple TextView programmatically for the marker content.
// In a real project, you might inflate a custom XML layout for richer markers.
class CustomMarkerView(context: Context) : MarkerView(context, 0) { // layoutResource 0 as we create view programmatically

    private val tvContent: TextView = TextView(context).apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#CC000000")) // Semi-transparent black
        setPadding(8, 4, 8, 4)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    init {
        // Add the TextView to the MarkerView's internal view hierarchy
        // This is a workaround since we don't have a layout XML to inflate.
        // In a real scenario with XML, you'd use `findViewById`.
        // For this example, we'll just use the TextView directly.
        // The MarkerView's `draw` method will handle drawing this content.
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e?.data is PricePoint) {
            val pricePoint = e.data as PricePoint
            val date = dateFormat.format(Date(pricePoint.timestamp))
            tvContent.text = "Time: $date\nPrice: $${String.format("%.2f", pricePoint.price)}"
        } else {
            tvContent.text = "No data"
        }
        // Measure and layout the TextView so its dimensions are correct for getOffset()
        tvContent.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        tvContent.layout(0, 0, tvContent.measuredWidth, tvContent.measuredHeight)
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // Center the marker horizontally and place it above the point
        return MPPointF(-(tvContent.measuredWidth / 2f), -tvContent.measuredHeight - 10f)
    }

    // Override draw to manually draw the TextView
    override fun draw(canvas: android.graphics.Canvas, posX: Float, posY: Float) {
        val offset = getOffsetForDrawingAtPoint(posX, posY)
        val restoreCount = canvas.save()
        canvas.translate(posX + offset.x, posY + offset.y)
        tvContent.draw(canvas)
        canvas.restoreToCount(restoreCount)
    }
}


// --- 7. Theme (from default Jetpack Compose project) ---
// Define some basic colors for the theme
val Purple80 = androidx.compose.ui.graphics.Color(0xFFD0BCFF)
val PurpleGrey80 = androidx.compose.ui.graphics.Color(0xFFCCC2DC)
val Pink80 = androidx.compose.ui.graphics.Color(0xFFEFB8C8)

val Purple40 = androidx.compose.ui.graphics.Color(0xFF6650a4)
val PurpleGrey40 = androidx.compose.ui.graphics.Color(0xFF625b71)
val Pink40 = androidx.compose.ui.graphics.Color(0xFF7D5260)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun AiAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val systemUiController = rememberSystemUiController()
            systemUiController.setSystemBarsColor(
                color = colorScheme.primary,
                darkIcons = !darkTheme
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(), // Assuming default Typography is fine
        content = content
    )
}

// Dummy Typography (if not defined elsewhere)
@Composable
fun Typography() = androidx.compose.material3.Typography()