import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Define R.string for the app for better localization practices
// For simplicity in a single file, I'll define these directly or use hardcoded strings for now.
// In a real app, these would be in res/values/strings.xml
// Example: R.string.app_name = "Gold Price App"
// For this example, I'll use hardcoded strings and just output the code directly.

//region Data Models
data class GoldPriceResponse(val gold: GoldPriceDetails)
data class GoldPriceDetails(val usd: Double)

data class HistoricalPriceEntry(
    val date: Calendar,
    val price: Double
)
//endregion

//region ViewModel
class GoldPriceViewModel : ViewModel() {

    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice

    private val _historicalPrices = MutableStateFlow<List<HistoricalPriceEntry>>(emptyList())
    val historicalPrices: StateFlow<List<HistoricalPriceEntry>> = _historicalPrices

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _priceChangePercentage = MutableStateFlow<Double?>(null)
    val priceChangePercentage: StateFlow<Double?> = _priceChangePercentage

    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    init {
        fetchGoldPrices()
    }

    fun fetchGoldPrices() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch current gold price (using CoinGecko for "gold" commodity price)
                val request = Request.Builder()
                    .url("https://api.coingecko.com/api/v3/simple/price?ids=gold&vs_currencies=usd")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { jsonString ->
                        val goldPriceResponse = gson.fromJson(jsonString, GoldPriceResponse::class.java)
                        _currentPrice.value = goldPriceResponse.gold.usd
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e("GoldPriceViewModel", "API Error: ${response.code} - $errorBody")
                    _errorMessage.value = "Failed to fetch current price: ${response.message}"
                    _currentPrice.value = null // Clear price on error
                }

                // Simulate fetching historical data for 7 days (mock data)
                // In a real app, you would fetch this from another API.
                delay(1000) // Simulate network delay for historical data
                generateMockHistoricalData()

            } catch (e: IOException) {
                Log.e("GoldPriceViewModel", "Network error: ${e.message}", e)
                _errorMessage.value = "Network error: ${e.message}"
                _currentPrice.value = null
                _historicalPrices.value = emptyList()
            } catch (e: Exception) {
                Log.e("GoldPriceViewModel", "An unexpected error occurred: ${e.message}", e)
                _errorMessage.value = "An unexpected error occurred: ${e.message}"
                _currentPrice.value = null
                _historicalPrices.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun generateMockHistoricalData() {
        val prices = mutableListOf<HistoricalPriceEntry>()
        val today = Calendar.getInstance()
        val currentPriceVal = _currentPrice.value ?: 2000.0 // Use current price if available, else a sensible default

        // Generate 7 days of mock data, with fluctuations around the current price
        // Let's assume the current price is the latest point.
        // And yesterday's price is slightly different to show a change.
        for (i in 6 downTo 0) { // 0 for today, 1 for yesterday, ..., 6 for 6 days ago
            val date = today.clone() as Calendar
            date.add(Calendar.DAY_OF_YEAR, -i)

            // Make prices fluctuate around the current price
            val basePrice = currentPriceVal + (Math.random() - 0.5) * 50 // +/- 25 around current
            val price = (basePrice * 100).roundToInt() / 100.0 // Round to 2 decimal places
            prices.add(HistoricalPriceEntry(date, price))
        }
        _historicalPrices.value = prices

        // Calculate price change percentage
        if (prices.size >= 2) {
            val latestPrice = prices.last().price
            val dayBeforePrice = prices[prices.size - 2].price
            if (dayBeforePrice != 0.0) {
                _priceChangePercentage.value = ((latestPrice - dayBeforePrice) / dayBeforePrice) * 100
            } else {
                _priceChangePercentage.value = 0.0
            }
        } else {
            _priceChangePercentage.value = null
        }
    }
}
//endregion

//region MPAndroidChart Formatters
class DayAxisValueFormatter : ValueFormatter() {
    private val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    private var historicalDates: List<Calendar> = emptyList()

    fun setHistoricalDates(dates: List<Calendar>) {
        this.historicalDates = dates
    }

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        // Value corresponds to the index in the data set
        val index = value.roundToInt()
        return if (index >= 0 && index < historicalDates.size) {
            sdf.format(historicalDates[index].time)
        } else {
            ""
        }
    }
}

class PriceValueFormatter : ValueFormatter() {
    private val decimalFormat = DecimalFormat("$#,##0.00")

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        return decimalFormat.format(value.toDouble())
    }

    override fun getFormattedValue(value: Float): String {
        return decimalFormat.format(value.toDouble())
    }
}
//endregion

//region MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GoldPriceViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            if (modelClass.isAssignableFrom(GoldPriceViewModel::class.java)) {
                                @Suppress("UNCHECKED_CAST")
                                return GoldPriceViewModel() as T
                            }
                            throw IllegalArgumentException("Unknown ViewModel class")
                        }
                    })
                    GoldPriceApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceApp(viewModel: GoldPriceViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsState()
    val historicalPrices by viewModel.historicalPrices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val priceChangePercentage by viewModel.priceChangePercentage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("黃金現價 App", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
            // Current Price Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "當前黃金價格 (USD/oz)",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    } else if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.fetchGoldPrices() }) {
                            Text("重試")
                        }
                    } else if (currentPrice != null) {
                        Text(
                            text = String.format(Locale.US, "$%.2f", currentPrice),
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "未能獲取價格",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }

            // Historical Chart Section
            Text(
                "最近 7 天價格歷史",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp) // Fixed height for the chart
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (isLoading && historicalPrices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (historicalPrices.isNotEmpty()) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        factory = { context ->
                            LineChart(context).apply {
                                description.isEnabled = false // Disable description label
                                setTouchEnabled(true)
                                setPinchZoom(false) // Disable pinch zoom
                                setDrawGridBackground(false)

                                // X-axis configuration
                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    setDrawGridLines(false)
                                    setDrawAxisLine(true)
                                    granularity = 1f // only whole numbers on x-axis
                                    textColor = android.graphics.Color.BLACK
                                    valueFormatter = DayAxisValueFormatter() // Custom formatter
                                }

                                // Left Y-axis configuration
                                axisLeft.apply {
                                    setDrawGridLines(true)
                                    setDrawAxisLine(true)
                                    textColor = android.graphics.Color.BLACK
                                    valueFormatter = PriceValueFormatter() // Custom formatter
                                }

                                // Right Y-axis (disabled for this chart)
                                axisRight.isEnabled = false

                                legend.isEnabled = false // Disable legend
                                animateX(1000) // Animate chart appearance
                            }
                        },
                        update = { chart ->
                            val entries = historicalPrices.mapIndexed { index, data ->
                                Entry(index.toFloat(), data.price.toFloat())
                            }

                            if (entries.isNotEmpty()) {
                                val dataSet = LineDataSet(entries, "Gold Price").apply {
                                    color = MaterialTheme.colorScheme.primary.toArgb()
                                    setCircleColor(MaterialTheme.colorScheme.primary.toArgb())
                                    lineWidth = 2f
                                    circleRadius = 4f
                                    setDrawValues(false) // Don't draw individual values on chart
                                    mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve
                                    setDrawFilled(true) // Fill area below line
                                    fillDrawable = chart.context.getDrawable(android.R.drawable.screen_background_light_transparent)
                                    // Use a gradient for fill
                                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f).toArgb()
                                }

                                chart.data = LineData(dataSet)

                                // Update X-axis formatter with actual dates
                                (chart.xAxis.valueFormatter as? DayAxisValueFormatter)?.setHistoricalDates(
                                    historicalPrices.map { it.date }
                                )

                                chart.invalidate() // Refresh chart
                            } else {
                                chart.clear() // Clear chart if no data
                                chart.invalidate()
                            }
                        }
                    )
                } else if (!isLoading && errorMessage == null) {
                    Text("無歷史數據", style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Price Change Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "昨日至今漲跌幅",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (priceChangePercentage != null) {
                        val isPositive = priceChangePercentage!! >= 0
                        val changeColor = if (isPositive) Color.Green else Color.Red
                        val changeSymbol = if (isPositive) "▲" else "▼"

                        Text(
                            text = String.format(Locale.US, "%s %.2f%%", changeSymbol, Math.abs(priceChangePercentage!!)),
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                            color = changeColor
                        )
                    } else if (!isLoading) {
                        Text(
                            text = "未能計算漲跌幅",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Convert Compose Color to Android Color Int
fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

@Preview(showBackground = true)
@Composable
fun GoldPriceAppPreview() {
    MaterialTheme {
        // Create a dummy ViewModel for preview purposes
        val previewViewModel = object : GoldPriceViewModel() {
            init {
                // Manually set some mock data for preview
                _isLoading.value = false
                _currentPrice.value = 2050.75
                val today = Calendar.getInstance()
                val mockHistory = listOf(
                    HistoricalPriceEntry(today.apply { add(Calendar.DAY_OF_YEAR, -6) }, 2000.0),
                    HistoricalPriceEntry(today.apply { add(Calendar.DAY_OF_YEAR, 1) }, 2010.5),
                    HistoricalPriceEntry(today.apply { add(Calendar.DAY_OF_YEAR, 1) }, 2005.2),
                    HistoricalPriceEntry(today.apply { add(Calendar.DAY_OF_YEAR, 1) }, 2020.8),
                    HistoricalPriceEntry(today.apply { add(Calendar.DAY_OF_YEAR, 1) }, 2035.1),
                    HistoricalPriceEntry(today.apply { add(Calendar.DAY_OF_YEAR, 1) }, 2028.9),
                    HistoricalPriceEntry(today.apply { add(Calendar.DAY_OF_YEAR, 1) }, 2050.75)
                )
                _historicalPrices.value = mockHistory
                _priceChangePercentage.value = 1.07 // Example change
            }
        }
        GoldPriceApp(previewViewModel)
    }
}
//endregion