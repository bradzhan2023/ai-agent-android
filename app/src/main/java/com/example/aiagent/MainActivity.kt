package com.example.aiagent

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Data Models ---
data class BinanceTickerPrice(
    val symbol: String,
    val price: String
)

data class KlineEntry(val timestamp: Long, val price: Float)

// --- Retrofit Service Interface ---
interface BinanceApiService {
    @GET("api/v3/ticker/price")
    suspend fun getTickerPrice(@Query("symbol") symbol: String): BinanceTickerPrice

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 500
    ): List<List<Any>> // Raw kline data
}

// --- Retrofit Client ---
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    val binanceApiService: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApiService::class.java)
    }
}

// --- Repository ---
class GoldPriceRepository(private val apiService: BinanceApiService) {
    suspend fun getPAXGUSDTPrice(): BinanceTickerPrice {
        return apiService.getTickerPrice("PAXGUSDT")
    }

    suspend fun getPAXGUSDTKlines(interval: String, limit: Int): List<List<Any>> {
        return apiService.getKlines("PAXGUSDT", interval, limit)
    }
}

// --- ViewModel ---
class GoldPriceViewModel(private val repository: GoldPriceRepository) : ViewModel() {
    private val _currentPrice = MutableStateFlow<String?>(null)
    val currentPrice: StateFlow<String?> = _currentPrice

    private val _klineData = MutableStateFlow<List<KlineEntry>>(emptyList())
    val klineData: StateFlow<List<KlineEntry>> = _klineData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchGoldPriceData()
    }

    fun fetchGoldPriceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val price = repository.getPAXGUSDTPrice()
                _currentPrice.value = price.price

                // Fetch klines for the last 24 hours with 1-hour interval
                val klines = repository.getPAXGUSDTKlines("1h", 24)
                _klineData.value = klines.map { kline ->
                    // kline[0] is open time (Long), kline[4] is close price (String)
                    KlineEntry(kline[0] as Long, (kline[4] as String).toFloat())
                }
            } catch (e: HttpException) {
                _errorMessage.value = "API Error: ${e.code()} - ${e.message()}"
                Log.e("GoldPriceViewModel", "HttpException: ${e.code()} - ${e.message()}", e)
            } catch (e: Exception) {
                _errorMessage.value = "Network Error: ${e.localizedMessage ?: "Unknown error"}"
                Log.e("GoldPriceViewModel", "Exception: ${e.localizedMessage}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// --- ViewModel Factory ---
class GoldPriceViewModelFactory(private val repository: GoldPriceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoldPriceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GoldPriceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- MPAndroidChart Custom Formatter ---
class DateAxisValueFormatter : ValueFormatter() {
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        // value is the timestamp in milliseconds
        return dateFormat.format(Date(value.toLong()))
    }
}

// --- Composable UI ---
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val klineData by viewModel.klineData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PAXGUSDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            Text("Loading data...", style = MaterialTheme.typography.bodyMedium)
        }

        errorMessage?.let { message ->
            Text(
                text = "Error: $message",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        currentPrice?.let { price ->
            Text(
                text = "Current Price: $price USDT",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } ?: run {
            if (!isLoading && errorMessage == null) {
                Text(
                    text = "Price data not available.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GoldPriceLineChart(klineEntries = klineData)

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.fetchGoldPriceData() }, enabled = !isLoading) {
            Text("Refresh Data")
        }
    }
}

@Composable
fun GoldPriceLineChart(klineEntries: List<KlineEntry>) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 8.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setNoDataText("No chart data available.")
                setNoDataTextColor(Color.BLACK.toArgb())

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    setDrawAxisLine(true)
                    textColor = Color.BLACK.toArgb()
                    valueFormatter = DateAxisValueFormatter()
                    granularity = 3600000f // 1 hour in milliseconds
                    labelRotationAngle = -45f
                    setLabelCount(5, true) // Show about 5 labels, force interval
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    textColor = Color.BLACK.toArgb()
                    setLabelCount(5, true)
                }

                axisRight.isEnabled = false // Disable right axis

                legend.apply {
                    form = Legend.LegendForm.LINE
                    textColor = Color.BLACK.toArgb()
                    verticalAlignment = Legend.LegendVerticalAlignment.TOP
                    horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }
            }
        },
        update = { chart ->
            if (klineEntries.isNotEmpty()) {
                val entries = klineEntries.map { Entry(it.timestamp.toFloat(), it.price) }
                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = Color.BLUE.toArgb()
                    setCircleColor(Color.BLUE.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on points
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                    setDrawValues(false) // Do not draw values on the line
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh chart
            } else {
                chart.data = null
                chart.invalidate()
            }
        }
    )
}

// --- Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme { // Using a placeholder theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GoldPriceViewModel = viewModel(
                        factory = GoldPriceViewModelFactory(
                            GoldPriceRepository(RetrofitClient.binanceApiService)
                        )
                    )
                    GoldPriceTrackerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// --- Placeholder Theme (for compilation) ---
@Composable
fun AiAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme() // Default dark colors
    } else {
        lightColorScheme() // Default light colors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(), // Default typography
        content = content
    )
}