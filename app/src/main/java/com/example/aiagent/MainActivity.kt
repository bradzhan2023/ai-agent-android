package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagent.ui.theme.AiAgentTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// --- Data Models ---
data class GoldPrice(
    val currentPrice: Double,
    val timestamp: Long, // Unix timestamp in seconds
    val priceChange: Double = 0.0, // Absolute change from previous day
    val priceChangePercent: Double = 0.0 // Percentage change from previous day
)

data class HistoricalPrice(
    val date: Long, // Unix timestamp in milliseconds for chart X-axis
    val price: Double
)

// --- API Service (Placeholder) ---
// IMPORTANT: Replace with a real API endpoint and your API Key!
// For demonstration, this uses a placeholder and generates dummy data.
// A free API like https://www.goldapi.io/ might require an API key.
// Example API for real data (requires signup and API key):
// Current price: https://www.goldapi.io/api/XAU/USD
// Historical price: Often separate endpoints or part of a premium plan.
object GoldApiService {
    private val client = OkHttpClient()

    // Placeholder URL - This will likely NOT work without a valid API key and endpoint.
    // Replace with a working API endpoint for real data.
    private const val BASE_URL = "https://api.example.com/gold"
    private const val API_KEY = "YOUR_API_KEY_HERE" // Replace with your actual API key

    suspend fun fetchCurrentGoldPrice(): GoldPrice = withContext(Dispatchers.IO) {
        // --- Dummy data for demonstration ---
        // In a real app, you would make an HTTP request here.
        // Example request structure (adjust for your chosen API):
        /*
        val request = Request.Builder()
            .url("$BASE_URL/current")
            .header("x-access-token", API_KEY) // Or "Authorization: Bearer $API_KEY"
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("API Call Failed: ${response.code}")
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            val json = JSONObject(responseBody)
            val currentPrice = json.getDouble("price")
            val timestamp = json.getLong("timestamp")
            val previousDayPrice = 1950.0 // You'd fetch this from historical data
            val priceChange = currentPrice - previousDayPrice
            val priceChangePercent = (priceChange / previousDayPrice) * 100
            return@withContext GoldPrice(currentPrice, timestamp, priceChange, priceChangePercent)
        }
        */

        // Generate dummy current price
        val currentPrice = 1980.00 + Random.nextDouble(-20.0, 20.0) // Simulate fluctuation
        val prevDayPrice = currentPrice - Random.nextDouble(-10.0, 15.0) // Simulate previous day
        val priceChange = currentPrice - prevDayPrice
        val priceChangePercent = (priceChange / prevDayPrice) * 100
        val timestamp = System.currentTimeMillis() / 1000 // Current Unix timestamp
        GoldPrice(currentPrice, timestamp, priceChange, priceChangePercent)
    }

    suspend fun fetchHistoricalGoldPrices(days: Int): List<HistoricalPrice> = withContext(Dispatchers.IO) {
        // --- Dummy data for demonstration ---
        // In a real app, you would make an HTTP request here for historical data.
        // Example request structure (adjust for your chosen API):
        /*
        val calendar = Calendar.getInstance()
        val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        val request = Request.Builder()
            .url("$BASE_URL/history?start_date=$startDate&end_date=$endDate")
            .header("x-access-token", API_KEY)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("API Call Failed: ${response.code}")
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            val jsonArray = JSONArray(responseBody)
            val historicalPrices = mutableListOf<HistoricalPrice>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val dateStr = obj.getString("date")
                val price = obj.getDouble("price")
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val timestamp = dateFormat.parse(dateStr)?.time ?: 0L
                historicalPrices.add(HistoricalPrice(timestamp, price))
            }
            return@withContext historicalPrices.sortedBy { it.date }
        }
        */

        // Generate dummy historical data for the last 'days'
        val historicalData = mutableListOf<HistoricalPrice>()
        val calendar = Calendar.getInstance()
        var basePrice = 1970.0 // Starting point for dummy data
        for (i in days downTo 1) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val date = calendar.timeInMillis // Timestamp for the day
            val price = basePrice + Random.nextDouble(-15.0, 15.0) // Simulate price fluctuation
            historicalData.add(HistoricalPrice(date, price))
            basePrice = price // Next day's price will be based on this
        }
        // Add today's price as the last point (can be fetched from current price API or simulated)
        calendar.timeInMillis = System.currentTimeMillis()
        historicalData.add(HistoricalPrice(calendar.timeInMillis, basePrice + Random.nextDouble(-5.0, 5.0)))

        historicalData.sortedBy { it.date }
    }
}

// --- ViewModel ---
class GoldPriceViewModel : ViewModel() {
    private val _currentPrice = MutableStateFlow<GoldPrice?>(null)
    val currentPrice: StateFlow<GoldPrice?> = _currentPrice.asStateFlow()

    private val _historicalPrices = MutableStateFlow<List<HistoricalPrice>>(emptyList())
    val historicalPrices: StateFlow<List<HistoricalPrice>> = _historicalPrices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        fetchGoldPrices()
    }

    fun fetchGoldPrices() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                // Fetch current price
                val fetchedCurrentPrice = GoldApiService.fetchCurrentGoldPrice()
                _currentPrice.value = fetchedCurrentPrice

                // Fetch 7 days of historical prices
                val fetchedHistoricalPrices = GoldApiService.fetchHistoricalGoldPrices(7)
                _historicalPrices.value = fetchedHistoricalPrices
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch gold prices: ${e.localizedMessage}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                GoldPriceApp()
            }
        }
    }
}

// --- Composable UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceApp(viewModel: GoldPriceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("黃金現價 App", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在加載黃金價格...", style = MaterialTheme.typography.bodyLarge)
            }

            errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            currentPrice?.let { priceData ->
                CurrentPriceDisplay(priceData)
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (historicalPrices.isNotEmpty()) {
                GoldPriceChart(historicalPrices)
            } else if (!isLoading && errorMessage == null) {
                Text(
                    text = "無法獲取歷史價格數據。",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Optional: A refresh button
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { viewModel.fetchGoldPrices() }, enabled = !isLoading) {
                Text("刷新價格")
            }
        }
    }
}

@Composable
fun CurrentPriceDisplay(price: GoldPrice) {
    val decimalFormat = DecimalFormat("$#,##0.00")
    val percentFormat = DecimalFormat("0.00'%'")
    val priceChangeColor = if (price.priceChange >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336") // Green for up, Red for down
    val priceChangeArrow = if (price.priceChange >= 0) "▲" else "▼"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardModifier(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "當前黃金價格 (USD/oz)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = decimalFormat.format(price.currentPrice),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 48.sp // Larger font size for prominence
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$priceChangeArrow ",
                color = priceChangeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "${decimalFormat.format(price.priceChange)} (${percentFormat.format(price.priceChangePercent)})",
                color = priceChangeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Text(
            text = "更新時間: ${sdf.format(Date(price.timestamp * 1000L))}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GoldPriceChart(historicalPrices: List<HistoricalPrice>) {
    val context = LocalContext.current
    val entries = historicalPrices.mapIndexed { index, data ->
        Entry(index.toFloat(), data.price.toFloat())
    }

    val chartDates = historicalPrices.map { it.date }.toLongArray()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardModifier()
            .height(300.dp) // Fixed height for the chart
    ) {
        Text(
            text = "最近 7 天價格歷史",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Allow chart to fill remaining height
            factory = { ctx ->
                LineChart(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    description.isEnabled = false // No description text
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setNoDataText("沒有數據可顯示")
                    setNoDataTextColor(Color.GRAY)

                    // X-axis configuration
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM // Labels at the bottom
                        granularity = 1f // Only whole numbers for days
                        setDrawGridLines(true)
                        setDrawAxisLine(true)
                        labelRotationAngle = -45f // Rotate labels for better readability
                        textColor = Color.DKGRAY
                        valueFormatter = DayAxisValueFormatter(chartDates) // Custom formatter for dates
                    }

                    // Left Y-axis configuration
                    axisLeft.apply {
                        setDrawGridLines(true)
                        setDrawAxisLine(true)
                        textColor = Color.DKGRAY
                        valueFormatter = PriceAxisValueFormatter() // Custom formatter for prices
                    }

                    // Right Y-axis configuration (disable or hide if not needed)
                    axisRight.isEnabled = false

                    // Legend configuration
                    legend.apply {
                        form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE
                        verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                        horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                        orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                        setDrawInside(false)
                        textColor = Color.DKGRAY
                    }
                }
            },
            update = { chart ->
                if (entries.isNotEmpty()) {
                    val dataSet = LineDataSet(entries, "黃金價格").apply {
                        color = Color.parseColor("#FFC107") // Gold color
                        setCircleColor(Color.parseColor("#FFC107"))
                        valueTextColor = Color.DKGRAY
                        valueTextSize = 10f
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(false) // Hide individual data point values
                        mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve
                    }

                    val lineData = LineData(dataSet)
                    chart.data = lineData
                    chart.invalidate() // Refresh chart
                    chart.animateX(700) // Animate chart for 700ms along X-axis
                } else {
                    chart.data = null
                    chart.invalidate()
                }
            }
        )
    }
}

// --- MPAndroidChart ValueFormatters ---

// Formatter for X-axis (Dates)
class DayAxisValueFormatter(private val timestamps: LongArray) : ValueFormatter() {
    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        val index = value.toInt()
        return if (index >= 0 && index < timestamps.size) {
            dateFormat.format(Date(timestamps[index]))
        } else {
            ""
        }
    }
}

// Formatter for Y-axis (Prices)
class PriceAxisValueFormatter : ValueFormatter() {
    private val decimalFormat = DecimalFormat("$#,##0.00")

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        return decimalFormat.format(value.toDouble())
    }

    override fun getPointLabel(entry: Entry?): String {
        return decimalFormat.format(entry?.y?.toDouble() ?: 0.0)
    }
}

// --- Modifier for card styling ---
@Composable
fun Modifier.cardModifier() = this
    .fillMaxWidth()
    .padding(vertical = 8.dp)
    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
    .padding(16.dp)

// --- Preview ---
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AiAgentTheme {
        GoldPriceApp()
    }
}