package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.* // Using Material 3 components
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// --- Data Models ---
data class BinancePriceResponse(
    val symbol: String,
    val price: String
)

// Binance klines API returns a list of lists.
// We only care about Open time (index 0) and Close price (index 4) for a simple line chart.
// The timestamp is in milliseconds.
// The close price is a String, needs to be converted to Double.

// --- Retrofit Interface ---
interface BinanceApiService {
    @GET("api/v3/ticker/price")
    suspend fun getCurrentPrice(@Query("symbol") symbol: String): BinancePriceResponse

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String, // e.g., "1h", "4h", "1d"
        @Query("limit") limit: Int // Number of data points
    ): List<List<Any>> // Raw response for klines
}

// --- Retrofit Client ---
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    val apiService: BinanceApiService by lazy {
        val gson = GsonBuilder()
            .setLenient() // Be lenient with JSON parsing
            .create()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BinanceApiService::class.java)
    }
}

// --- ViewModel ---
class MainViewModel(private val apiService: BinanceApiService) : ViewModel() {

    private val _currentPrice = MutableStateFlow<String>("N/A")
    val currentPrice: StateFlow<String> = _currentPrice

    private val _historicalData = MutableStateFlow<List<Entry>>(emptyList())
    val historicalData: StateFlow<List<Entry>> = _historicalData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _selectedChartValue = MutableStateFlow<Pair<String, String>?>(null)
    val selectedChartValue: StateFlow<Pair<String, String>?> = _selectedChartValue

    init {
        fetchPriceAndKlines()
    }

    fun fetchPriceAndKlines() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Fetch current price for PAXGUSDT
                val priceResponse = apiService.getCurrentPrice("PAXGUSDT")
                _currentPrice.value = priceResponse.price

                // Fetch historical klines (e.g., last 24 hours, 1-hour interval)
                val klinesResponse = apiService.getKlines("PAXGUSDT", "1h", 24)
                val entries = klinesResponse.mapIndexed { index, kline ->
                    val openTime = kline[0] as Long // Timestamp in milliseconds
                    val closePrice = (kline[4] as String).toDouble() // Close price as String
                    // Use index as x-value for MPAndroidChart, store timestamp in Entry.data for custom formatter
                    Entry(index.toFloat(), closePrice.toFloat(), openTime)
                }
                _historicalData.value = entries

            } catch (e: HttpException) {
                _error.value = "網路錯誤: ${e.code()} - ${e.message()}"
                e.printStackTrace()
            } catch (e: IOException) {
                _error.value = "連線錯誤: 請檢查您的網路連線"
                e.printStackTrace()
            } catch (e: Exception) {
                _error.value = "發生未知錯誤: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSelectedChartValue(timestamp: Long, price: Float) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        _selectedChartValue.value = Pair(dateFormat.format(date), String.format("%.2f", price))
    }

    fun clearSelectedChartValue() {
        _selectedChartValue.value = null
    }
}

// ViewModel Factory for injecting BinanceApiService
class MainViewModelFactory(private val apiService: BinanceApiService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- MainActivity ---
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
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(RetrofitClient.apiService)
                    )
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// --- Composable Functions ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalData by viewModel.historicalData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedChartValue by viewModel.selectedChartValue.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PAXGUSDT 金價追蹤") }
            )
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
                text = "當前 PAXGUSDT 價格:",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$currentPrice USDT",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text("載入中...", modifier = Modifier.padding(top = 8.dp))
            } else if (error != null) {
                Text(
                    text = "錯誤: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = { viewModel.fetchPriceAndKlines() }) {
                    Text("重試")
                }
            } else {
                selectedChartValue?.let { (time, price) ->
                    Text(
                        text = "選定時間: $time",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "選定價格: $price USDT",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                GoldPriceChart(
                    entries = historicalData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    onValueSelected = { entry ->
                        if (entry != null && entry.data is Long) {
                            viewModel.setSelectedChartValue(entry.data as Long, entry.y)
                        } else {
                            viewModel.clearSelectedChartValue()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun GoldPriceChart(
    entries: List<Entry>,
    modifier: Modifier = Modifier,
    onValueSelected: (Entry?) -> Unit
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface // Not directly used for chart background, but good to have

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
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    textColor = onSurfaceColor.toArgb()
                    valueFormatter = object : ValueFormatter() {
                        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            // 'value' here is the index (0, 1, 2...), we stored timestamp in Entry.data
                            // Find the corresponding entry to get the timestamp
                            val entry = entries.getOrNull(value.toInt())
                            return entry?.let {
                                dateFormat.format(Date(it.data as Long))
                            } ?: ""
                        }
                    }
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(4, true) // Show approximately 4 labels, force exactly
                }

                // Left Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = onSurfaceColor.copy(alpha = 0.2f).toArgb()
                    textColor = onSurfaceColor.toArgb()
                    setDrawAxisLine(true)
                }

                // Right Y-axis configuration (disable it)
                axisRight.isEnabled = false

                // Legend configuration
                legend.apply {
                    isEnabled = true
                    textColor = onSurfaceColor.toArgb()
                    form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE
                    horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.LEFT
                    verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                    orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }

                // Add a listener for value selection
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        onValueSelected(e)
                    }

                    override fun onNothingSelected() {
                        onValueSelected(null)
                    }
                })
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = primaryColor.toArgb()
                    valueTextColor = onSurfaceColor.toArgb()
                    setDrawCircles(false) // Don't draw individual circles for each point
                    setDrawValues(false) // Don't draw individual values on the chart
                    lineWidth = 2f
                    mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve
                    setDrawFilled(true) // Fill the area below the line
                    fillColor = primaryColor.copy(alpha = 0.3f).toArgb()
                    fillAlpha = 100
                }
                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh the chart
            } else {
                chart.clear()
                chart.setNoDataText("無資料可顯示")
                chart.setNoDataTextColor(onSurfaceColor.toArgb())
                chart.invalidate()
            }
        }
    )
}

// Extension function to convert Compose Color to Android Color Int
fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

// --- Theme (Placeholder for ui.theme.Theme.kt and ui.theme.Type.kt) ---
// In a real project, these would be in separate files within the ui.theme package.
@Composable
fun AiAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Use system dark theme preference
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFBB86FC),
            secondary = Color(0xFF03DAC5),
            tertiary = Color(0xFF3700B3),
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onTertiary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            error = Color(0xFFCF6679)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC5),
            tertiary = Color(0xFF3700B3),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onTertiary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            error = Color(0xFFB00020)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(), // Assuming default or custom Typography
        content = content
    )
}

// Placeholder for Typography, usually defined in ui.theme.Type.kt
@Composable
fun Typography() = androidx.compose.material3.Typography()

// Placeholder for isSystemInDarkTheme, usually from androidx.compose.foundation.isSystemInDarkTheme
@Composable
fun isSystemInDarkTheme(): Boolean {
    // This is a simplified placeholder. In a real app, you'd use:
    // return androidx.compose.foundation.isSystemInDarkTheme()
    // For now, we'll just return false to always use light theme unless explicitly set.
    return false
}