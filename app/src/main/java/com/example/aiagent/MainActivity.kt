package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiagent.ui.theme.AIAgentTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// 1. Data Models
@Serializable
data class BinanceKLineResponse(
    val data: List<List<String>> // Raw data from Binance API
) {
    // Helper to parse the raw string data into a more usable format
    fun toKLineDataList(): List<KLineData> {
        return data.map {
            KLineData(
                openTime = it[0].toLong(),
                openPrice = it[1].toFloat(),
                highPrice = it[2].toFloat(),
                lowPrice = it[3].toFloat(),
                closePrice = it[4].toFloat(),
                volume = it[5].toFloat(),
                closeTime = it[6].toLong(),
                quoteAssetVolume = it[7].toFloat(),
                numberOfTrades = it[8].toInt(),
                takerBuyBaseAssetVolume = it[9].toFloat(),
                takerBuyQuoteAssetVolume = it[10].toFloat(),
                ignore = it[11].toFloat()
            )
        }
    }
}

@Serializable
data class KLineData(
    val openTime: Long,
    val openPrice: Float,
    val highPrice: Float,
    val lowPrice: Float,
    val closePrice: Float,
    val volume: Float,
    val closeTime: Long,
    val quoteAssetVolume: Float,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: Float,
    val takerBuyQuoteAssetVolume: Float,
    val ignore: Float
)

// 2. Binance API Interface
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<String>> // Binance returns a list of lists of strings
}

// 3. Network Client Setup
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val binanceApiService: BinanceApiService by lazy {
        retrofit.create(BinanceApiService::class.java)
    }
}

// 4. Repository
class PriceRepository(private val apiService: BinanceApiService) {
    suspend fun getPAXGUSDTPrice(interval: String, limit: Int): List<KLineData> {
        return apiService.getKlines("PAXGUSDT", interval, limit).map {
            KLineData(
                openTime = it[0].toLong(),
                openPrice = it[1].toFloat(),
                highPrice = it[2].toFloat(),
                lowPrice = it[3].toFloat(),
                closePrice = it[4].toFloat(),
                volume = it[5].toFloat(),
                closeTime = it[6].toLong(),
                quoteAssetVolume = it[7].toFloat(),
                numberOfTrades = it[8].toInt(),
                takerBuyBaseAssetVolume = it[9].toFloat(),
                takerBuyQuoteAssetVolume = it[10].toFloat(),
                ignore = it[11].toFloat()
            )
        }
    }
}

// 5. ViewModel
class GoldPriceViewModel(private val repository: PriceRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentPrice = MutableStateFlow<Float?>(null)
    val currentPrice: StateFlow<Float?> = _currentPrice.asStateFlow()

    private val _historicalPrices = MutableStateFlow<List<KLineData>>(emptyList())
    val historicalPrices: StateFlow<List<KLineData>> = _historicalPrices.asStateFlow()

    init {
        fetchPriceData()
    }

    fun fetchPriceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Fetch 1-hour interval data for the last 100 hours
                val data = repository.getPAXGUSDTPrice("1h", 100)
                _historicalPrices.value = data
                _currentPrice.value = data.lastOrNull()?.closePrice
            } catch (e: HttpException) {
                _errorMessage.value = "Network error: ${e.code()} - ${e.message()}"
            } catch (e: IOException) {
                _errorMessage.value = "Connection error: ${e.message}"
            } catch (e: Exception) {
                _errorMessage.value = "An unexpected error occurred: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ViewModel Factory to inject repository
    class Factory(private val repository: PriceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GoldPriceViewModel::class.java)) {
                return GoldPriceViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// 6. MPAndroidChart Value Formatters
class DateValueFormatter(private val pattern: String) : ValueFormatter() {
    private val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        // value is the timestamp in milliseconds
        return dateFormat.format(Date(value.toLong()))
    }
}

class PriceValueFormatter : ValueFormatter() {
    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        return String.format(Locale.getDefault(), "%.2f", value)
    }
}

// 7. Composable for Line Chart
@Composable
fun GoldPriceLineChart(historicalPrices: List<KLineData>) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description = Description().apply { text = "" } // Hide description label
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setBackgroundColor(Color.WHITE)

                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // As per requirement
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                    valueFormatter = DateValueFormatter("HH:mm") // Format time
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(5, true) // Show approximately 5 labels, force interval
                }

                // Left Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    textColor = Color.BLACK
                    valueFormatter = PriceValueFormatter()
                }

                // Right Y-axis configuration (disable it)
                axisRight.isEnabled = false

                // Legend configuration
                legend.apply {
                    isEnabled = true
                    textColor = Color.BLACK
                }

                // No data text
                setNoDataText("Loading chart data...")
                setNoDataTextColor(Color.GRAY)
            }
        },
        update = { chart ->
            if (historicalPrices.isNotEmpty()) {
                val entries = historicalPrices.mapIndexed { index, data ->
                    Entry(data.openTime.toFloat(), data.closePrice)
                }

                val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on points
                    mode = LineDataSet.Mode.LINEAR // Smooth line
                    setDrawFilled(true) // Fill area below the line
                    fillColor = Color.BLUE
                    fillAlpha = 50
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh chart
            } else {
                chart.clear()
                chart.invalidate()
            }
        }
    )
}

// 8. Main Screen Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceTrackerScreen(viewModel: GoldPriceViewModel) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT 金價追蹤") })
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
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("載入中...", style = MaterialTheme.typography.bodyLarge)
            }

            errorMessage?.let {
                Text(
                    text = "錯誤: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }

            currentPrice?.let {
                Text(
                    text = "當前 PAXGUSDT 價格: $%.2f".format(Locale.getDefault(), it),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } ?: run {
                if (!isLoading && errorMessage == null) {
                    Text(
                        text = "無價格數據",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.fetchPriceData() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("重新整理")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (historicalPrices.isNotEmpty()) {
                GoldPriceLineChart(historicalPrices = historicalPrices)
            } else if (!isLoading && errorMessage == null) {
                Text(
                    text = "無歷史數據可顯示圖表",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

// 9. MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = PriceRepository(RetrofitClient.binanceApiService)
        val viewModelFactory = GoldPriceViewModel.Factory(repository)

        setContent {
            AIAgentTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GoldPriceViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel(factory = viewModelFactory)
                    GoldPriceTrackerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// 10. Preview
@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerScreenPreview() {
    AIAgentTheme {
        // For preview, we can create a mock ViewModel or pass mock data directly
        // Here, we'll create a simple mock repository and ViewModel for demonstration
        val mockRepository = object : PriceRepository(RetrofitClient.binanceApiService) {
            override suspend fun getPAXGUSDTPrice(interval: String, limit: Int): List<KLineData> {
                // Return some mock data for preview
                val now = System.currentTimeMillis()
                return (0..10).map { i ->
                    KLineData(
                        openTime = now - (10 - i) * 3600 * 1000, // 1 hour intervals
                        openPrice = 2000f + i * 5f,
                        highPrice = 2010f + i * 5f,
                        lowPrice = 1990f + i * 5f,
                        closePrice = 2005f + i * 5f,
                        volume = 100f,
                        closeTime = now - (10 - i) * 3600 * 1000 + 3600 * 1000 - 1,
                        quoteAssetVolume = 100f,
                        numberOfTrades = 10,
                        takerBuyBaseAssetVolume = 50f,
                        takerBuyQuoteAssetVolume = 50f,
                        ignore = 0f
                    )
                }
            }
        }
        val mockViewModel = GoldPriceViewModel(mockRepository)
        // Manually trigger data fetch for preview if needed, or set initial state
        // For a simple preview, we can just show the UI structure
        GoldPriceTrackerScreen(viewModel = mockViewModel)
    }
}