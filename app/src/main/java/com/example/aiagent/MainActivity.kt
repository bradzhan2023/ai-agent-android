package com.example.aiagent

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

// 1. Data Models
data class PriceData(val timestamp: Long, val price: Double)

// 2. Network Layer (Retrofit)
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<Any>> // Binance Klines API returns a list of lists
}

object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    val apiService: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApiService::class.java)
    }
}

// 3. Repository
class PriceRepository(private val apiService: BinanceApiService) {
    suspend fun getPAXGUSDTPriceHistory(): List<PriceData> {
        val klines = apiService.getKlines("PAXGUSDT", "1h", 100) // Fetch last 100 1-hour candles
        return klines.map { kline ->
            // kline[0] is openTime (Long), kline[4] is close price (String)
            PriceData(
                timestamp = kline[0] as Long,
                price = (kline[4] as String).toDouble()
            )
        }
    }
}

// 4. ViewModel
class PriceViewModel(private val repository: PriceRepository) : ViewModel() {
    private val _priceHistory = MutableStateFlow<List<PriceData>>(emptyList())
    val priceHistory: StateFlow<List<PriceData>> = _priceHistory

    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchPriceHistory()
    }

    fun fetchPriceHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val history = repository.getPAXGUSDTPriceHistory()
                _priceHistory.value = history
                _currentPrice.value = history.lastOrNull()?.price
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch price: ${e.localizedMessage}"
                Log.e("PriceViewModel", "Error fetching price", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// ViewModel Factory for dependency injection
class PriceViewModelFactory(private val repository: PriceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PriceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PriceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Helper function to create a gradient drawable for the chart fill
fun createChartGradientDrawable(): Drawable {
    val colors = intArrayOf(
        Color.parseColor("#80ADD8E6"), // Light blue with 50% alpha
        Color.TRANSPARENT
    )
    return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
        cornerRadius = 0f
    }
}

// 5. UI (Jetpack Compose)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceTrackerApp(viewModel: PriceViewModel = viewModel(factory = PriceViewModelFactory(PriceRepository(RetrofitClient.apiService)))) {
    val priceHistory by viewModel.priceHistory.collectAsState()
    val currentPrice by viewModel.currentPrice.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            currentPrice?.let { price ->
                Text(
                    text = "Current PAXGUSDT Price: $%.2f".format(price),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } ?: run {
                if (!isLoading && errorMessage == null) {
                    Text(
                        text = "No price data available.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            Button(
                onClick = { viewModel.fetchPriceHistory() },
                modifier = Modifier.padding(bottom = 16.dp),
                enabled = !isLoading
            ) {
                Text("Refresh Price")
            }

            PriceLineChart(priceHistory = priceHistory)
        }
    }
}

@Composable
fun PriceLineChart(priceHistory: List<PriceData>) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                // Basic chart setup
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setBackgroundColor(Color.TRANSPARENT) // Make chart background transparent

                // X-axis setup
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    setDrawAxisLine(true)
                    textColor = Color.GRAY
                    valueFormatter = object : ValueFormatter() {
                        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            // MPAndroidChart uses float for x-values, convert back to Long for Date
                            return dateFormat.format(Date(value.toLong()))
                        }
                    }
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(4, true) // Show approx 4 labels, force exact
                }

                // Left Y-axis setup
                axisLeft.apply {
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    enableGridDashedLine(10f, 10f, 0f)
                    textColor = Color.GRAY
                }

                // Right Y-axis setup (disable)
                axisRight.isEnabled = false

                // Legend setup
                legend.apply {
                    form = Legend.LegendForm.LINE
                    verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    textColor = Color.GRAY
                }
            }
        },
        update = { chart ->
            if (priceHistory.isNotEmpty()) {
                val entries = priceHistory.mapIndexed { index, data ->
                    // Use timestamp as x-value
                    Entry(data.timestamp.toFloat(), data.price.toFloat())
                }

                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = Color.BLUE // Line color
                    setDrawCircles(false) // Do not draw circles on data points
                    lineWidth = 2f
                    valueTextSize = 0f // Hide value text on chart
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                    setDrawFilled(true) // Fill area below the line
                    fillDrawable = createChartGradientDrawable() // Use the gradient drawable
                    fillAlpha = 85 // Alpha for the fill color (0-255)
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh chart
                chart.animateX(1000) // Animate chart along X-axis
            } else {
                chart.clear() // Clear chart if no data
                chart.invalidate()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(vertical = 8.dp)
    )
}

// 6. MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // A simple MaterialTheme for the app.
            // In a real app, you'd define your custom theme in ui.theme package.
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PriceTrackerApp()
                }
            }
        }
    }
}