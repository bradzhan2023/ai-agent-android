package com.example.aiagent

import android.graphics.Color
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

// Data Models
data class BinanceTicker(
    val symbol: String,
    val price: String
)

// Binance Klines API returns a list of arrays:
// [
//   [
//     1499040000000,      // Open time
//     "0.01634790",       // Open
//     "0.80000000",       // High
//     "0.01575800",       // Low
//     "0.01577100",       // Close
//     "148976.10704000",  // Volume
//     1499644799999,      // Close time
//     "2434.19055334",    // Quote asset volume
//     308,                // Number of trades
//     "1756.87402397",    // Taker buy base asset volume
//     "28.46694368",      // Taker buy quote asset volume
//     "0"                 // Ignore
//   ]
// ]
data class KLineData(
    val openTime: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String,
    val ignore: String
) {
    companion object {
        fun fromJsonArray(jsonArray: List<Any>): KLineData {
            return KLineData(
                openTime = (jsonArray[0] as Double).toLong(),
                open = jsonArray[1] as String,
                high = jsonArray[2] as String,
                low = jsonArray[3] as String,
                close = jsonArray[4] as String,
                volume = jsonArray[5] as String,
                closeTime = (jsonArray[6] as Double).toLong(),
                quoteAssetVolume = jsonArray[7] as String,
                numberOfTrades = (jsonArray[8] as Double).toInt(),
                takerBuyBaseAssetVolume = jsonArray[9] as String,
                takerBuyQuoteAssetVolume = jsonArray[10] as String,
                ignore = jsonArray[11] as String
            )
        }
    }
}

// Retrofit API Interface
interface BinanceApiService {
    @GET("api/v3/ticker/price")
    suspend fun getTickerPrice(@Query("symbol") symbol: String): BinanceTicker

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<Any>> // Raw list of arrays
}

// Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    val instance: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BinanceApiService::class.java)
    }
}

// ViewModel
class GoldPriceViewModel(private val apiService: BinanceApiService) : ViewModel() {

    private val _currentPrice = MutableStateFlow<String>("Loading...")
    val currentPrice: StateFlow<String> = _currentPrice

    private val _historicalPrices = MutableStateFlow<List<KLineData>>(emptyList())
    val historicalPrices: StateFlow<List<KLineData>> = _historicalPrices

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val SYMBOL = "PAXGUSDT"
    private val INTERVAL = "1h" // 1-hour interval for historical data
    private val LIMIT = 100 // Last 100 data points

    init {
        fetchGoldPrice()
        fetchHistoricalGoldPrices()
    }

    fun fetchGoldPrice() {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                val ticker = apiService.getTickerPrice(SYMBOL)
                _currentPrice.value = String.format(Locale.US, "%.2f", ticker.price.toDouble())
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

    fun fetchHistoricalGoldPrices() {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                val klinesRaw = apiService.getKlines(SYMBOL, INTERVAL, LIMIT)
                val klines = klinesRaw.map { KLineData.fromJsonArray(it) }
                _historicalPrices.value = klines
            } catch (e: HttpException) {
                _errorMessage.value = "HTTP Error fetching historical data: ${e.code()} - ${e.message()}"
                Log.e("GoldPriceViewModel", "HTTP Error fetching historical data: ${e.code()} - ${e.message()}", e)
            } catch (e: IOException) {
                _errorMessage.value = "Network Error fetching historical data: ${e.message}"
                Log.e("GoldPriceViewModel", "Network Error fetching historical data: ${e.message}", e)
            } catch (e: Exception) {
                _errorMessage.value = "An unexpected error occurred fetching historical data: ${e.message}"
                Log.e("GoldPriceViewModel", "Unexpected Error fetching historical data: ${e.message}", e)
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

// MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GoldPriceViewModel = viewModel(
                        factory = GoldPriceViewModelFactory(RetrofitClient.instance)
                    )
                    GoldPriceScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// Composables
@Composable
fun GoldPriceScreen(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PAXGUSDT 金價追蹤",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        PriceDisplay(price = currentPrice)

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Text(
                text = "錯誤: $errorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (historicalPrices.isNotEmpty()) {
            GoldPriceChart(historicalPrices = historicalPrices)
        } else if (errorMessage == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            viewModel.fetchGoldPrice()
            viewModel.fetchHistoricalGoldPrices()
        }) {
            Text("重新整理")
        }
    }
}

@Composable
fun PriceDisplay(price: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "當前 PAXGUSDT 價格",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$$price",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun GoldPriceChart(historicalPrices: List<KLineData>) {
    val context = LocalContext.current
    val selectedValue = remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PAXGUSDT 歷史價格 (最近 ${historicalPrices.size} 小時)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            factory = { ctx ->
                LineChart(ctx).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)
                    setBackgroundColor(Color.WHITE)

                    // X-axis configuration
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.setDrawAxisLine(true)
                    xAxis.textColor = Color.BLACK
                    xAxis.valueFormatter = object : ValueFormatter() {
                        private val mFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            return mFormat.format(Date(value.toLong()))
                        }
                    }
                    xAxis.labelRotationAngle = -45f // Rotate labels for better readability
                    xAxis.setLabelCount(5, true) // Show approximately 5 labels

                    // Left Y-axis configuration
                    axisLeft.setDrawGridLines(true)
                    axisLeft.setDrawAxisLine(true)
                    axisLeft.textColor = Color.BLACK
                    axisLeft.gridColor = Color.LTGRAY
                    axisLeft.axisMinimum = 0f // Start from 0

                    // Right Y-axis configuration (disable)
                    axisRight.isEnabled = false

                    legend.isEnabled = true
                    legend.textColor = Color.BLACK

                    // Set up value selection listener
                    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            if (e != null) {
                                val date = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(e.x.toLong()))
                                selectedValue.value = "時間: $date, 價格: ${String.format(Locale.US, "%.2f", e.y)}"
                            }
                        }

                        override fun onNothingSelected() {
                            selectedValue.value = null
                        }
                    })
                }
            },
            update = { chart ->
                val entries = historicalPrices.mapIndexed { _, kline ->
                    Entry(kline.openTime.toFloat(), kline.close.toFloat())
                }

                if (entries.isNotEmpty()) {
                    val dataSet = LineDataSet(entries, "PAXGUSDT 價格").apply {
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
                    chart.animateX(1000) // Animate chart
                }
            }
        )

        selectedValue.value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}