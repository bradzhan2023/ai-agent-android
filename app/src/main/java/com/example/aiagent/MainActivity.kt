package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
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
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- Theme Definition ---
@Composable
fun AIAgentTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC5),
            tertiary = androidx.compose.ui.graphics.Color(0xFF03DAC5)
        ),
        content = content
    )
}

// --- Data Models ---
@Serializable
data class KlineData(
    @SerialName("0") val openTime: Long,
    @SerialName("1") val openPrice: String,
    @SerialName("2") val highPrice: String,
    @SerialName("3") val lowPrice: String,
    @SerialName("4") val closePrice: String,
    @SerialName("5") val volume: String,
    @SerialName("6") val closeTime: Long,
    @SerialName("7") val quoteAssetVolume: String,
    @SerialName("8") val numberOfTrades: Long,
    @SerialName("9") val takerBuyBaseAssetVolume: String,
    @SerialName("10") val takerBuyQuoteAssetVolume: String,
    @SerialName("11") val ignore: String? = null
)

// --- Network Interface ---
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getPAXGUSDTPrice(
        @Query("symbol") symbol: String = "PAXGUSDT",
        @Query("interval") interval: String = "1h", // 1-hour interval
        @Query("limit") limit: Int = 24 // Last 24 hours
    ): List<List<String>> // Binance API returns List<List<String>> for klines
}

// --- Retrofit Client ---
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val binanceApiService: BinanceApiService by lazy {
        retrofit.create(BinanceApiService::class.java)
    }
}

// --- Repository ---
interface PriceRepository {
    suspend fun getPAXGUSDTPrice(): Result<List<KlineData>>
}

class PriceRepositoryImpl(private val apiService: BinanceApiService) : PriceRepository {
    override suspend fun getPAXGUSDTPrice(): Result<List<KlineData>> {
        return try {
            val response = apiService.getPAXGUSDTPrice()
            val klineDataList = response.map { rawKline ->
                KlineData(
                    openTime = rawKline[0].toLong(),
                    openPrice = rawKline[1],
                    highPrice = rawKline[2],
                    lowPrice = rawKline[3],
                    closePrice = rawKline[4],
                    volume = rawKline[5],
                    closeTime = rawKline[6].toLong(),
                    quoteAssetVolume = rawKline[7],
                    numberOfTrades = rawKline[8].toLong(),
                    takerBuyBaseAssetVolume = rawKline[9],
                    takerBuyQuoteAssetVolume = rawKline[10],
                    ignore = rawKline.getOrNull(11) // Handle optional 'ignore' field
                )
            }
            Result.success(klineDataList)
        } catch (e: HttpException) {
            Log.e("PriceRepository", "HTTP Error: ${e.code()} - ${e.message()}")
            Result.failure(e)
        } catch (e: IOException) {
            Log.e("PriceRepository", "Network Error: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("PriceRepository", "Unknown Error: ${e.message}")
            Result.failure(e)
        }
    }
}

// --- ViewModel ---
class PriceTrackerViewModel(private val repository: PriceRepository) : ViewModel() {

    private val _paxgPriceData = MutableStateFlow<List<KlineData>>(emptyList())
    val paxgPriceData: StateFlow<List<KlineData>> = _paxgPriceData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentPrice = MutableStateFlow<String>("N/A")
    val currentPrice: StateFlow<String> = _currentPrice

    init {
        fetchPAXGUSDTPrice()
    }

    fun fetchPAXGUSDTPrice() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = repository.getPAXGUSDTPrice()
            result.onSuccess { data ->
                _paxgPriceData.value = data
                _currentPrice.value = data.lastOrNull()?.closePrice ?: "N/A"
                _isLoading.value = false
            }.onFailure { throwable ->
                _error.value = "Failed to fetch price: ${throwable.localizedMessage}"
                _isLoading.value = false
                Log.e("PriceTrackerViewModel", "Error fetching price", throwable)
            }
        }
    }
}

// --- ViewModel Factory ---
class PriceTrackerViewModelFactory(private val repository: PriceRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PriceTrackerViewModel::class.java)) {
            return PriceTrackerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = PriceRepositoryImpl(RetrofitClient.binanceApiService)
        val viewModelFactory = PriceTrackerViewModelFactory(repository)
        val viewModel: PriceTrackerViewModel by viewModels { viewModelFactory }

        setContent {
            AIAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PriceTrackerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// --- Composable UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceTrackerScreen(viewModel: PriceTrackerViewModel) {
    val paxgPriceData by viewModel.paxgPriceData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentPrice by viewModel.currentPrice.collectAsState()

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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "當前 PAXGUSDT 價格: $currentPrice",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text("載入中...", modifier = Modifier.padding(top = 8.dp))
            } else if (error != null) {
                Text(
                    text = "錯誤: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = { viewModel.fetchPAXGUSDTPrice() }) {
                    Text("重試")
                }
            } else if (paxgPriceData.isNotEmpty()) {
                PriceChart(paxgPriceData = paxgPriceData, modifier = Modifier.fillMaxSize())
            } else {
                Text("沒有數據可顯示。", modifier = Modifier.padding(bottom = 16.dp))
                Button(onClick = { viewModel.fetchPAXGUSDTPrice() }) {
                    Text("載入數據")
                }
            }
        }
    }
}

@Composable
fun PriceChart(paxgPriceData: List<KlineData>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)

                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false) // MPAndroidChart 3.1.0 syntax
                xAxis.setDrawAxisLine(true)
                xAxis.textColor = Color.BLACK
                xAxis.valueFormatter = object : IndexAxisValueFormatter() {
                    private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        // Ensure value is within bounds of paxgPriceData list
                        val index = value.toInt()
                        if (index < 0 || index >= paxgPriceData.size) {
                            return "" // Or some default value
                        }
                        val timestamp = paxgPriceData[index].openTime
                        return mFormat.format(Date(timestamp))
                    }
                }
                xAxis.labelRotationAngle = -45f // Rotate labels for better readability
                xAxis.setLabelCount(4, true) // Show approximately 4 labels, force these to be exactly 4

                axisLeft.setDrawGridLines(true)
                axisLeft.textColor = Color.BLACK
                axisRight.isEnabled = false // Disable right Y-axis

                legend.isEnabled = true
                legend.textColor = Color.BLACK
            }
        },
        update = { chart ->
            if (paxgPriceData.isNotEmpty()) {
                val entries = paxgPriceData.mapIndexed { index, kline ->
                    Entry(index.toFloat(), kline.closePrice.toFloat())
                }

                val dataSet = LineDataSet(entries, "PAXGUSDT Close Price").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on chart
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

// --- Preview ---
@Preview(showBackground = true)
@Composable
fun PreviewPriceTrackerScreen() {
    AIAgentTheme {
        // Create a mock repository for preview
        val mockRepository = object : PriceRepository {
            override suspend fun getPAXGUSDTPrice(): Result<List<KlineData>> {
                // Simulate some dummy data for preview
                val dummyData = listOf(
                    KlineData(1678886400000, "1800.0", "1805.0", "1795.0", "1802.0", "100", 1678890000000, "180200", 50, "50", "90100"),
                    KlineData(1678890000000, "1802.0", "1810.0", "1800.0", "1808.0", "120", 1678893600000, "216960", 60, "60", "108480"),
                    KlineData(1678893600000, "1808.0", "1815.0", "1805.0", "1812.0", "110", 1678897200000, "199320", 55, "55", "99660"),
                    KlineData(1678897200000, "1812.0", "1820.0", "1810.0", "1818.0", "130", 1678900800000, "236340", 65, "65", "118170"),
                    KlineData(1678900800000, "1818.0", "1825.0", "1815.0", "1822.0", "140", 1678904400000, "255080", 70, "70", "127540"),
                    KlineData(1678904400000, "1822.0", "1830.0", "1820.0", "1828.0", "150", 1678908000000, "274200", 75, "75", "137100")
                )
                return Result.success(dummyData)
            }
        }
        val viewModel = PriceTrackerViewModel(mockRepository)
        // Manually trigger data fetch for preview, as init block won't run in isolation
        LaunchedEffect(Unit) {
            viewModel.fetchPAXGUSDTPrice()
        }
        PriceTrackerScreen(viewModel = viewModel)
    }
}