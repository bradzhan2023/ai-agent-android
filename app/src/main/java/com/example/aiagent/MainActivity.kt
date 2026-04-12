package com.example.aiagent

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagent.ui.theme.AIAgentTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// Data model for Binance Klines
// Binance klines endpoint returns List<List<Any>> where numbers are strings.
// We'll parse it from JsonArray directly.
@Serializable
data class KlineData(
    val openTime: Long,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val volume: Double,
    val closeTime: Long,
    val quoteAssetVolume: Double,
    val numberOfTrades: Long,
    val takerBuyBaseAssetVolume: Double,
    val takerBuyQuoteAssetVolume: Double,
    val ignore: Double // This is often 0.0 or similar
)

// Helper function to parse a raw JsonArray entry into KlineData
fun parseKlineData(rawKline: JsonArray): KlineData {
    return KlineData(
        openTime = rawKline[0].long,
        openPrice = rawKline[1].content.toDouble(),
        highPrice = rawKline[2].content.toDouble(),
        lowPrice = rawKline[3].content.toDouble(),
        closePrice = rawKline[4].content.toDouble(),
        volume = rawKline[5].content.toDouble(),
        closeTime = rawKline[6].long,
        quoteAssetVolume = rawKline[7].content.toDouble(),
        numberOfTrades = rawKline[8].long,
        takerBuyBaseAssetVolume = rawKline[9].content.toDouble(),
        takerBuyQuoteAssetVolume = rawKline[10].content.toDouble(),
        ignore = rawKline[11].content.toDouble()
    )
}

// Retrofit API Service
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 500 // Default to 500 data points
    ): JsonArray // Binance returns a JSON array of arrays
}

// Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val api: BinanceApiService by lazy {
        retrofit.create(BinanceApiService::class.java)
    }
}

// ViewModel
class GoldPriceViewModel(private val apiService: BinanceApiService) : ViewModel() {

    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice

    private val _klineData = MutableStateFlow<List<KlineData>>(emptyList())
    val klineData: StateFlow<List<KlineData>> = _klineData

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchGoldPriceData()
    }

    fun fetchGoldPriceData() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                // Fetch recent klines for PAXGUSDT, e.g., 24 hours of 1-hour intervals
                val rawKlines = apiService.getKlines(symbol = "PAXGUSDT", interval = "1h", limit = 24)
                val parsedKlines = rawKlines.map { parseKlineData(it as JsonArray) }
                _klineData.value = parsedKlines

                // Update current price from the latest kline
                _currentPrice.value = parsedKlines.lastOrNull()?.closePrice

            } catch (e: HttpException) {
                _errorMessage.value = "HTTP Error: ${e.code()} - ${e.message()}"
                Log.e("GoldPriceViewModel", "HTTP Error: ${e.code()} - ${e.message()}", e)
            } catch (e: IOException) {
                _errorMessage.value = "Network Error: ${e.message}"
                Log.e("GoldPriceViewModel", "Network Error: ${e.message}", e)
            } catch (e: Exception) {
                _errorMessage.value = "An unexpected error occurred: ${e.message}"
                Log.e("GoldPriceViewModel", "Unexpected Error: ${e.message}", e)
            }
        }
    }
}

// ViewModel Factory
class GoldPriceViewModelFactory(private val apiService: BinanceApiService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoldPriceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GoldPriceViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GoldPriceViewModel = viewModel(
                        factory = GoldPriceViewModelFactory(RetrofitClient.api)
                    )
                    GoldPriceTrackerApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun GoldPriceTrackerApp(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val klineData by viewModel.klineData.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "PAXGUSDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (errorMessage != null) {
            Text(
                text = "Error: ${errorMessage!!}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        CurrentPriceDisplay(currentPrice = currentPrice)

        Spacer(modifier = Modifier.height(16.dp))

        if (klineData.isNotEmpty()) {
            LineChartComposable(klineData = klineData)
        } else if (errorMessage == null) {
            CircularProgressIndicator(modifier = Modifier.fillMaxWidth().wrapContentWidth())
        }
    }
}

@Composable
fun CurrentPriceDisplay(currentPrice: Double?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Current PAXGUSDT Price:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentPrice?.let { String.format("$%.2f", it) } ?: "Loading...",
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
}

@Composable
fun LineChartComposable(klineData: List<KlineData>) {
    val entries = remember(klineData) {
        klineData.mapIndexed { index, data ->
            Entry(index.toFloat(), data.closePrice.toFloat())
        }
    }

    val lineDataSet = remember(entries) {
        LineDataSet(entries, "PAXGUSDT Price").apply {
            color = android.graphics.Color.BLUE
            setCircleColor(android.graphics.Color.BLUE)
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false) // Hide values on the chart
            mode = LineDataSet.Mode.LINEAR // Smooth line
        }
    }

    val lineData = remember(lineDataSet) {
        LineData(lineDataSet)
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    granularity = 1f // Only show labels for whole numbers
                    valueFormatter = object : ValueFormatter() {
                        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        override fun getAxisLabel(value: Float, axis: XAxis?): String {
                            // Map index back to original timestamp
                            val index = value.toInt()
                            return if (index >= 0 && index < klineData.size) {
                                dateFormat.format(Date(klineData[index].openTime))
                            } else {
                                ""
                            }
                        }
                    }
                    labelRotationAngle = -45f // Rotate labels for better readability
                }

                axisLeft.apply {
                    setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                    setDrawAxisLine(true)
                    // Set Y-axis limits dynamically with a small buffer
                    val minPrice = klineData.minOfOrNull { it.closePrice }?.toFloat() ?: 0f
                    val maxPrice = klineData.maxOfOrNull { it.closePrice }?.toFloat() ?: 1000f
                    axisMinimum = minPrice * 0.99f // 1% buffer below min
                    axisMaximum = maxPrice * 1.01f // 1% buffer above max
                }

                axisRight.isEnabled = false // Disable right Y-axis
                setTouchEnabled(true)
                setPinchZoom(true)
                setNoDataText("No chart data available.")
            }
        },
        update = { chart ->
            chart.data = lineData
            chart.invalidate() // Refresh chart
            chart.animateX(500) // Animate chart
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AIAgentTheme {
        // Create a dummy ViewModel for preview
        val dummyKlines = listOf(
            KlineData(1678886400000, 1000.0, 1005.0, 995.0, 1002.0, 100.0, 1678890000000, 100000.0, 100, 50.0, 50.0, 0.0),
            KlineData(1678890000000, 1002.0, 1008.0, 1000.0, 1005.0, 120.0, 1678893600000, 120000.0, 110, 60.0, 60.0, 0.0),
            KlineData(1678893600000, 1005.0, 1010.0, 1003.0, 1007.0, 110.0, 1678897200000, 110000.0, 105, 55.0, 55.0, 0.0),
            KlineData(1678897200000, 1007.0, 1012.0, 1005.0, 1009.0, 130.0, 1678900800000, 130000.0, 120, 65.0, 65.0, 0.0),
            KlineData(1678900800000, 1009.0, 1015.0, 1007.0, 1012.0, 140.0, 1678904400000, 140000.0, 130, 70.0, 70.0, 0.0)
        )
        val dummyViewModel = object : GoldPriceViewModel(RetrofitClient.api) {
            override val currentPrice: StateFlow<Double?> = MutableStateFlow(1012.0)
            override val klineData: StateFlow<List<KlineData>> = MutableStateFlow(dummyKlines)
            override val errorMessage: StateFlow<String?> = MutableStateFlow(null)
        }
        GoldPriceTrackerApp(viewModel = dummyViewModel)
    }
}