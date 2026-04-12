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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

// 1. Data Models
// Data class to represent a single K-line (candlestick) data point from Binance API
@Serializable
data class KlineData(
    val openTime: Long,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val closePrice: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String,
    val numberOfTrades: Long,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String,
    val ignore: String
) {
    // Companion object to provide a factory method for converting the raw List<String> from Binance API
    companion object {
        fun fromList(list: List<String>): KlineData {
            return KlineData(
                openTime = list[0].toLong(),
                openPrice = list[1],
                highPrice = list[2],
                lowPrice = list[3],
                closePrice = list[4],
                volume = list[5],
                closeTime = list[6].toLong(),
                quoteAssetVolume = list[7],
                numberOfTrades = list[8].toLong(),
                takerBuyBaseAssetVolume = list[9],
                takerBuyQuoteAssetVolume = list[10],
                ignore = list[11]
            )
        }
    }
}

// 2. API Interface for Binance
interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 500 // Default to fetch 500 data points
    ): List<List<String>> // Binance Klines API returns a list of lists of strings
}

// 3. Retrofit Client Setup
object RetrofitClient {
    private const val BASE_URL = "https://api.binance.com/"

    // Configure Kotlinx Serialization for JSON parsing
    private val json = Json {
        ignoreUnknownKeys = true // Ignore fields not defined in our data classes
        isLenient = true // Be lenient with JSON parsing
    }

    // Lazy initialization of BinanceApiService using Retrofit
    val binanceApiService: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // Add Kotlinx Serialization converter factory
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BinanceApiService::class.java)
    }
}

// 4. ViewModel
// Sealed class to represent the different states of the gold price data
sealed class GoldPriceState {
    object Loading : GoldPriceState()
    data class Success(val currentPrice: String, val klineData: List<KlineData>) : GoldPriceState()
    data class Error(val message: String) : GoldPriceState()
}

// ViewModel responsible for fetching and managing gold price data
class GoldPriceViewModel(private val apiService: BinanceApiService) : ViewModel() {
    // Mutable state to hold the current UI state, exposed as an immutable State
    private val _goldPriceState = mutableStateOf<GoldPriceState>(GoldPriceState.Loading)
    val goldPriceState: State<GoldPriceState> = _goldPriceState

    init {
        // Fetch data when the ViewModel is initialized
        fetchGoldPriceData()
    }

    // Function to fetch gold price data from the Binance API
    fun fetchGoldPriceData() {
        _goldPriceState.value = GoldPriceState.Loading // Set state to loading
        viewModelScope.launch { // Launch a coroutine in the ViewModel's scope
            try {
                // Fetch raw klines data for PAXGUSDT with 1-hour interval
                val rawKlines = apiService.getKlines(symbol = "PAXGUSDT", interval = "1h")
                // Convert raw list of lists to our structured KlineData objects
                val klineData = rawKlines.map { KlineData.fromList(it) }
                // Get the latest closing price
                val currentPrice = klineData.lastOrNull()?.closePrice ?: "N/A"
                // Update state to success with the fetched data
                _goldPriceState.value = GoldPriceState.Success(currentPrice, klineData)
            } catch (e: Exception) {
                // Update state to error if an exception occurs
                _goldPriceState.value = GoldPriceState.Error("Failed to fetch data: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Companion object to provide a ViewModelProvider.Factory for injecting dependencies
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(GoldPriceViewModel::class.java)) {
                    // Create an instance of GoldPriceViewModel, injecting the BinanceApiService
                    return GoldPriceViewModel(RetrofitClient.binanceApiService) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}

// 5. Compose UI
class MainActivity : ComponentActivity() {
    // Opt-in for experimental Material 3 APIs
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // Apply Material Design theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceTrackerApp() // Main Composable for the app
                }
            }
        }
    }
}

// Main Composable function for the Gold Price Tracker application
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceTrackerApp(
    // Obtain GoldPriceViewModel using the custom factory
    goldPriceViewModel: GoldPriceViewModel = viewModel(factory = GoldPriceViewModel.Factory)
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT 金價追蹤") }) // Top app bar
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(16.dp), // Additional padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val state = goldPriceViewModel.goldPriceState.value // Observe the ViewModel state

            when (state) {
                is GoldPriceState.Loading -> {
                    CircularProgressIndicator() // Show loading indicator
                    Text("載入中...")
                }
                is GoldPriceState.Success -> {
                    Text(
                        text = "當前 PAXGUSDT 價格: ${state.currentPrice}",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LineChartCompose(klineData = state.klineData) // Display the chart
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { goldPriceViewModel.fetchGoldPriceData() }) {
                        Text("重新整理") // Refresh button
                    }
                }
                is GoldPriceState.Error -> {
                    Text(
                        text = "錯誤: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { goldPriceViewModel.fetchGoldPriceData() }) {
                        Text("重試") // Retry button
                    }
                }
            }
        }
    }
}

// Composable function to display the MPAndroidChart LineChart
@Composable
fun LineChartCompose(klineData: List<KlineData>) {
    val context = LocalContext.current // Get the current Android context
    // Convert KlineData to MPAndroidChart Entry objects
    val entries = remember(klineData) {
        klineData.mapIndexed { index, data ->
            Entry(index.toFloat(), data.closePrice.toFloat())
        }
    }

    // Generate X-axis labels from close times
    val xAxisLabels = remember(klineData) {
        klineData.map {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.closeTime))
        }
    }

    // Use AndroidView to embed the traditional Android View (LineChart) into Compose
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false // Disable chart description
                setTouchEnabled(true) // Enable touch interactions
                isDragEnabled = true // Enable dragging
                setScaleEnabled(true) // Enable scaling
                setPinchZoom(true) // Enable pinch zoom

                // Customize X-axis
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM // Position X-axis at the bottom
                    setDrawGridLines(false) // Disable X-axis grid lines (MPAndroidChart 3.1.0 syntax)
                    setDrawAxisLine(true) // Draw X-axis line
                    textColor = Color.BLACK
                    valueFormatter = IndexAxisValueFormatter(xAxisLabels) // Set custom labels
                    granularity = 1f // Only show integer values on the X-axis
                    labelRotationAngle = -45f // Rotate labels for better readability
                    setLabelCount(4, true) // Show approximately 4 labels, force true for exact count
                }

                // Customize Y-axis (left)
                axisLeft.apply {
                    setDrawGridLines(true) // Enable Y-axis grid lines
                    setDrawAxisLine(true) // Draw Y-axis line
                    textColor = Color.BLACK
                }

                // Customize Y-axis (right)
                axisRight.isEnabled = false // Disable the right Y-axis

                // Customize Legend
                legend.apply {
                    isEnabled = true // Enable legend
                    textColor = Color.BLACK
                }

                // Add a listener for value selection (optional)
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        // Handle value selection if needed, e.g., show a tooltip
                    }

                    override fun onNothingSelected() {
                        // Handle nothing selected if needed
                    }
                })
            }
        },
        update = { chart ->
            // Update the chart data when klineData changes
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXGUSDT Close Price").apply {
                    color = Color.BLUE
                    setCircleColor(Color.BLUE)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value text on points
                    setDrawFilled(true) // Fill area below the line
                    fillColor = Color.BLUE
                    fillAlpha = 50 // Set fill transparency
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.xAxis.valueFormatter = IndexAxisValueFormatter(xAxisLabels) // Update formatter
                chart.invalidate() // Refresh chart to redraw with new data
            } else {
                chart.clear() // Clear chart if no data
                chart.setNoDataText("沒有數據可顯示") // Display no data text
                chart.invalidate()
            }
        }
    )
}

// Preview Composable for GoldPriceTrackerApp
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerAppPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text("PAXGUSDT 金價追蹤 (預覽)") })
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("預覽模式", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Text("載入中...")
                }
            }
        }
    }
}