package com.example.aiagent

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// IMPORTANT: Add the following dependencies to your build.gradle.kts (app module):
/*
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.0") // Or newer
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") // Or newer

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // Or newer version

    // Ktor HTTP Client
    implementation("io.ktor:ktor-client-core:2.3.6")
    implementation("io.ktor:ktor-client-android:2.3.6")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.6")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.6")

    // KotlinX Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
*/
// Also, ensure you have internet permission in AndroidManifest.xml:
// <uses-permission android:name="android.permission.INTERNET" />

// --- Data Models ---

// Binance Kline API response is an array of arrays, representing:
// [openTime, open, high, low, close, volume, closeTime, quoteAssetVolume, numberOfTrades, takerBuyBaseAssetVolume, takerBuyQuoteAssetVolume, ignore]
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
    val ignore: Double
) {
    // Helper to parse the raw List<Any> into our data class
    companion object {
        fun fromList(list: List<Any>): KlineData {
            return KlineData(
                openTime = (list[0] as? Long) ?: (list[0] as? Double)?.toLong() ?: 0L,
                openPrice = (list[1] as String).toDouble(),
                highPrice = (list[2] as String).toDouble(),
                lowPrice = (list[3] as String).toDouble(),
                closePrice = (list[4] as String).toDouble(),
                volume = (list[5] as String).toDouble(),
                closeTime = (list[6] as? Long) ?: (list[6] as? Double)?.toLong() ?: 0L,
                quoteAssetVolume = (list[7] as String).toDouble(),
                numberOfTrades = (list[8] as? Long) ?: (list[8] as? Double)?.toLong() ?: 0L,
                takerBuyBaseAssetVolume = (list[9] as String).toDouble(),
                takerBuyQuoteAssetVolume = (list[10] as String).toDouble(),
                ignore = (list[11] as String).toDouble()
            )
        }
    }
}

// --- Network Service ---

object BinanceApiService {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // To handle fields we don't parse
                isLenient = true // For sometimes less strict JSON parsing
            })
        }
    }

    suspend fun getKlineData(symbol: String, interval: String, limit: Int): List<KlineData> {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get("https://api.binance.com/api/v3/klines") {
                    url {
                        parameters.append("symbol", symbol)
                        parameters.append("interval", interval)
                        parameters.append("limit", limit.toString())
                    }
                }

                if (response.status.value == 200) {
                    val rawData: List<List<Any>> = response.body()
                    rawData.map { KlineData.fromList(it) }
                } else {
                    val errorBody = response.bodyAsText()
                    throw Exception("Failed to fetch klines: HTTP ${response.status.value}, $errorBody")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("Network request failed: ${e.message}", e)
            }
        }
    }
}

// --- MainActivity ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoldPriceAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceScreen()
                }
            }
        }
    }
}

// --- Theme and Colors ---

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6), // Light Blue for accents
    secondary = Color(0xFF81C784), // Light Green for positive change
    tertiary = Color(0xFFFFCC80), // Light Orange
    background = Color(0xFF1A237E), // Deep Indigo for background
    surface = Color(0xFF283593), // Slightly lighter Indigo for cards/app bar
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFEF5350), // Red for negative change
    onError = Color.White
)

@Composable
fun GoldPriceAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(), // Uses default Material3 typography
        content = content
    )
}

// --- UI Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceScreen() {
    val context = LocalContext.current
    var currentPrice by remember { mutableStateOf("N/A") }
    var priceChange24h by remember { mutableStateOf("N/A") }
    var changeColor by remember { mutableStateOf(Color.White) }
    val chartEntries = remember { mutableStateListOf<Entry>() }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        coroutineScope.launch {
            try {
                // Fetch 24 data points, each representing a 1-hour interval for 24 hours
                val data = BinanceApiService.getKlineData(
                    symbol = "PAXGUSDT",
                    interval = "1h",
                    limit = 24
                )
                if (data.isNotEmpty()) {
                    val firstPrice = data.first().openPrice // Use opening price of the first interval for 24h calculation
                    val latestPrice = data.last().closePrice

                    val change = latestPrice - firstPrice
                    val percentChange = (change / firstPrice) * 100

                    currentPrice = DecimalFormat("#,##0.00").format(latestPrice)
                    priceChange24h = String.format("%.2f%%", percentChange)
                    changeColor = when {
                        percentChange > 0 -> MaterialTheme.colorScheme.secondary // Green
                        percentChange < 0 -> MaterialTheme.colorScheme.error // Red
                        else -> Color.White
                    }

                    chartEntries.clear()
                    data.forEachIndexed { index, kline ->
                        // X-axis value is index for simplicity, Y-axis is closing price
                        chartEntries.add(Entry(index.toFloat(), kline.closePrice.toFloat()))
                    }
                } else {
                    Toast.makeText(context, "No PAXG data received.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading data: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PAXG Gold Price Tracker", color = MaterialTheme.colorScheme.onSurface) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Current PAXG Price (USDT)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(32.dp))
            } else {
                Text(
                    text = "$$currentPrice",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "24h Change: ",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = priceChange24h,
                        style = MaterialTheme.typography.titleMedium,
                        color = changeColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(300.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    if (chartEntries.isNotEmpty()) {
                        GoldPriceLineChart(entries = chartEntries)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No chart data available.", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun GoldPriceLineChart(entries: List<Entry>) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            LineChart(it).apply {
                setBackgroundColor(Color.TRANSPARENT) // Chart background
                description.isEnabled = false // No description text
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true) // Enable pinch zoom for two-axis scaling

                // Customize X-axis
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    typeface = Typeface.DEFAULT
                    setDrawGridLines(false) // No vertical grid lines
                    setDrawAxisLine(true)
                    textColor = Color.WHITE
                    textSize = 10f
                    axisLineColor = Color.GRAY
                    gridColor = Color.DKGRAY // Grid lines color
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            // Assuming 'value' is an index from 0 to 23 for 24 hours.
                            // Map it to a human-readable hour format relative to the start.
                            return "Hr ${value.toInt() + 1}"
                        }
                    }
                    setLabelCount(6, true) // Force 6 labels, more or less evenly distributed
                }

                // Customize Left Y-axis
                axisLeft.apply {
                    typeface = Typeface.DEFAULT
                    textColor = Color.WHITE
                    setDrawGridLines(true) // Horizontal grid lines
                    setDrawAxisLine(true)
                    axisLineColor = Color.GRAY
                    gridColor = Color.DKGRAY
                    valueFormatter = object : ValueFormatter() {
                        private val format = DecimalFormat("###,###,##0.00")
                        override fun getFormattedValue(value: Float): String {
                            return "$${format.format(value)}"
                        }
                    }
                    setLabelCount(6, false) // 6 labels, not necessarily forced evenly
                }

                // Customize Right Y-axis
                axisRight.apply {
                    isEnabled = false // Disable right Y-axis for a cleaner look
                }

                // Customize Legend
                legend.apply {
                    form = Legend.LegendForm.NONE // No legend for a single line
                    textColor = Color.WHITE
                }

                animateX(1500) // Animation for X-axis over 1.5 seconds
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "PAXG Price").apply {
                    color = MaterialTheme.colorScheme.primary.toArgb() // Line color (Light Blue)
                    setCircleColor(MaterialTheme.colorScheme.primary.toArgb()) // Circle color
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextSize = 0f // Hide value labels on points
                    setDrawFilled(true) // Enable fill below the line
                    fillFormatter = IFillFormatter { dataSet, dataProvider -> chart.axisLeft.axisMinimum } // Fill to bottom of chart
                    // IMPORTANT: You need to create this drawable in your res/drawable folder.
                    // Create a file named `fade_blue.xml` in `app/src/main/res/drawable` with content:
                    /*
                    <?xml version="1.0" encoding="utf-8"?>
                    <shape xmlns:android="http://schemas.android.com/apk/res/android">
                        <gradient
                            android:angle="90"
                            android:startColor="#3364B5F6" // Semi-transparent primary color
                            android:endColor="#001A237E" // Transparent background color
                            android:type="linear" />
                    </shape>
                    */
                    fillDrawable = context.getDrawable(com.example.aiagent.R.drawable.fade_blue)
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // Refresh chart
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GoldPriceAppTheme {
        GoldPriceScreen()
    }
}