import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import kotlin.math.abs

package com.example.aiagent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldTrackerScreen()
                }
            }
        }
    }
}

@Composable
fun GoldTrackerScreen() {
    val scope = rememberCoroutineScope()

    var openPrice by remember { mutableStateOf("N/A") }
    var currentPrice by remember { mutableStateOf("N/A") }
    var priceChange by remember { mutableStateOf("N/A") }
    var percentageChange by remember { mutableStateOf("N/A") }
    var chartEntries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var changeColor by remember { mutableStateOf(Color.Gray) } // For price change text color

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            errorMessage = ""
            try {
                val data = fetchGoldPriceData()
                openPrice = data.openPrice
                currentPrice = data.currentPrice
                priceChange = data.priceChange
                percentageChange = data.percentageChange
                chartEntries = data.chartEntries
                changeColor = data.changeColor
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "今日黃金走勢 (PAXG/USDT)",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Text("加載中...")
        } else if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = ""
                    try {
                        val data = fetchGoldPriceData()
                        openPrice = data.openPrice
                        currentPrice = data.currentPrice
                        priceChange = data.priceChange
                        percentageChange = data.percentageChange
                        chartEntries = data.chartEntries
                        changeColor = data.changeColor
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }, modifier = Modifier.padding(top = 8.dp)) {
                Text("重試")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "當前價格: $currentPrice USDT", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "今日漲跌價: $priceChange USDT",
                        color = changeColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "($percentageChange%)",
                        color = changeColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.White),
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false // Hide description label
                        setTouchEnabled(true) // Enable touch gestures
                        isDragEnabled = true // Enable dragging
                        setScaleEnabled(true) // Enable scaling
                        setPinchZoom(true) // Enable pinch zoom
                        setDrawGridBackground(false) // No grid background

                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM // X-axis at the bottom
                            setDrawGridLines(false) // No vertical grid lines
                            setDrawAxisLine(true)
                            valueFormatter = object : IndexAxisValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    // Labels for the last 24 hours, H-23 to H-0
                                    val hour = (24 - 1 - value.toInt()).coerceAtLeast(0)
                                    return if (value.toInt() in 0 until 24) "H-$hour" else ""
                                }
                            }
                            labelCount = 5 // Show roughly 5 labels
                            setAvoidFirstLastVisibleLabel(true)
                            textColor = android.graphics.Color.BLACK
                        }

                        axisLeft.apply {
                            setDrawGridLines(true) // Horizontal grid lines
                            setDrawAxisLine(true)
                            textColor = android.graphics.Color.BLACK
                        }
                        axisRight.isEnabled = false // Disable right Y-axis

                        legend.isEnabled = false // Disable legend
                        animateX(1500) // Animate chart creation
                    }
                },
                update = { chart ->
                    if (chartEntries.isNotEmpty()) {
                        val dataSet = LineDataSet(chartEntries, "PAXG/USDT Price").apply {
                            color = android.graphics.Color.BLUE
                            valueTextColor = android.graphics.Color.BLACK
                            lineWidth = 2f
                            setDrawCircles(false) // No circles on data points
                            setDrawValues(false) // No value text on points
                            mode = LineDataSet.Mode.LINEAR // Straight line segments
                            setDrawFilled(true)
                            fillColor = android.graphics.Color.parseColor("#80ADD8E6") // Light blue fill with transparency
                        }

                        val lineData = LineData(dataSet)
                        chart.data = lineData
                        chart.invalidate() // Refresh chart
                    }
                }
            )
        }
    }
}

data class GoldPriceData(
    val openPrice: String,
    val currentPrice: String,
    val priceChange: String,
    val percentageChange: String,
    val chartEntries: List<Entry>,
    val changeColor: Color
)

suspend fun fetchGoldPriceData(): GoldPriceData {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
        .build()

    return withContext(Dispatchers.IO) {
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Network request failed: ${response.code} - ${response.message}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response body from Binance API")

        val jsonArray = try {
            JSONArray(responseBody)
        } catch (e: Exception) {
            throw Exception("Failed to parse JSON response: ${e.message}")
        }

        if (jsonArray.length() < 2) { // Need at least 2 data points for a meaningful change
            throw Exception("Not enough data received for calculation. Received ${jsonArray.length()} entries.")
        }

        val priceFormat = DecimalFormat("#,##0.00")
        val percentageFormat = DecimalFormat("0.00")

        var calculatedOpenPrice = 0.0
        var calculatedCurrentPrice = 0.0
        val chartDataEntries = mutableListOf<Entry>()

        for (i in 0 until jsonArray.length()) {
            val kline = jsonArray.optJSONArray(i)
            if (kline != null && kline.length() > 4) { // Ensure enough elements in kline array
                val closePriceStr = kline.optString(4, "0.0") // Index 4 is close price
                val closePrice = closePriceStr.toDoubleOrNull()
                    ?: throw Exception("Invalid close price format at index $i: $closePriceStr")

                if (i == 0) {
                    val openPriceStr = kline.optString(1, "0.0") // Index 1 is open price
                    calculatedOpenPrice = openPriceStr.toDoubleOrNull()
                        ?: throw Exception("Invalid open price format at index $i: $openPriceStr")
                }

                if (i == jsonArray.length() - 1) {
                    calculatedCurrentPrice = closePrice
                }
                chartDataEntries.add(Entry(i.toFloat(), closePrice.toFloat()))
            } else {
                // Log or handle malformed kline data, but don't stop processing if possible
                System.err.println("Malformed kline data at index $i: $kline")
            }
        }

        if (calculatedOpenPrice == 0.0 || calculatedCurrentPrice == 0.0) {
            throw Exception("Could not extract valid open or current prices.")
        }

        val rawPriceChange = calculatedCurrentPrice - calculatedOpenPrice
        val rawPercentageChange = (rawPriceChange / calculatedOpenPrice) * 100

        val displayOpenPrice = priceFormat.format(calculatedOpenPrice)
        val displayCurrentPrice = priceFormat.format(calculatedCurrentPrice)
        val displayPriceChange = priceFormat.format(rawPriceChange)
        val displayPercentageChange = percentageFormat.format(rawPercentageChange)

        val changeColor = when {
            rawPriceChange > 0 -> Color.Green
            rawPriceChange < 0 -> Color.Red
            else -> Color.Gray
        }

        GoldPriceData(
            openPrice = displayOpenPrice,
            currentPrice = displayCurrentPrice,
            priceChange = displayPriceChange,
            percentageChange = displayPercentageChange,
            chartEntries = chartDataEntries,
            changeColor = changeColor
        )
    }
}