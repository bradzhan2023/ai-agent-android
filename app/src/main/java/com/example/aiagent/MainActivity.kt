好的，根據您提供的編譯器錯誤日誌，所有的 `Unresolved reference` 錯誤都指向了 `Vico` 圖表庫的組件。這通常是因為缺少必要的 `import` 語句，或者 `build.gradle` 中沒有正確引入 `Vico` 庫。

由於錯誤日誌只提供了 `MainActivity.kt` 的資訊，我將會修復 `MainActivity.kt` 中的 `import` 語句。請注意，如果新增這些 `import` 語句後仍然無法編譯，您需要檢查並確認您的 `app/build.gradle` 文件中是否已添加了 `Vico` 圖表庫的依賴。

以下是修復後的 `MainActivity.kt` 內容，主要是在文件頂部添加了所有缺失的 `Vico` 相關 `import` 語句：

```kotlin
package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aiagent.ui.theme.AIAgentTheme
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.toArgb // 通常用於顏色轉換，即使 Vico 可能直接支持 Compose Color

// --- Vico Charting Library Imports ---
// 如果在添加這些 import 語句後仍然遇到編譯錯誤，請務必檢查您的 app/build.gradle 文件
// 並確保已添加以下 Vico 庫依賴：
/*
dependencies {
    // ... 其他依賴
    implementation "com.patrykandpatrick.vico:core:<latest_version>"
    implementation "com.patrykandpatrick.vico:compose:<latest_version>"
    implementation "com.patrykandpatrick.vico:compose-m3:<latest_version>" // 如果使用 Material 3，否則為 compose-m2
}
// 請將 <latest_version> 替換為當前 Vico 庫的最新穩定版本，例如 "1.13.0"
*/
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis // 假設您也使用了左側的縱軸
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.core.component.shape.Shapes // 用於 Shapes.rect
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryModelOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PriceTrackerScreen()
                }
            }
        }
    }
}

// Data class to represent a single candlestick entry for the chart
data class CandlestickEntry(val timestamp: Long, val closePrice: Float)

// ViewModel-like structure for handling data fetching and state
@Composable
fun rememberPriceTrackerState(): PriceTrackerState {
    return remember { PriceTrackerState() }
}

class PriceTrackerState {
    var currentPrice by mutableStateOf("Loading...")
    var priceChange24h by mutableStateOf("Loading...")
    var priceChangePercent24h by mutableStateOf("Loading...")
    var chartEntries by mutableStateOf<List<CandlestickEntry>>(emptyList())
    val chartEntryModelProducer = ChartEntryModelProducer()

    private val client = OkHttpClient()
    private val gson = Gson()

    // Function to fetch current price and 24h stats
    suspend fun fetchCurrentPriceAndStats() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.binance.com/api/v3/ticker/24hr?symbol=PAXGUSDT")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                responseBody?.let {
                    val jsonObject = JSONObject(it)
                    val lastPrice = jsonObject.getString("lastPrice").toFloat()
                    val priceChange = jsonObject.getString("priceChange").toFloat()
                    val priceChangePercent = jsonObject.getString("priceChangePercent").toFloat()

                    withContext(Dispatchers.Main) {
                        currentPrice = String.format("%.2f USDT", lastPrice)
                        priceChange24h = String.format("%.2f USDT", priceChange)
                        priceChangePercent24h = String.format("%.2f%%", priceChangePercent)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    currentPrice = "Error"
                    priceChange24h = "Error"
                    priceChangePercent24h = "Error"
                }
            }
        }
    }

    // Function to fetch 24-hour historical data (kline)
    suspend fun fetch24HourChartData() {
        withContext(Dispatchers.IO) {
            try {
                // Interval: 1 hour (1h), Limit: 24 (for 24 hours)
                val request = Request.Builder()
                    .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                responseBody?.let {
                    val jsonArray = JSONArray(it)
                    val entries = mutableListOf<CandlestickEntry>()

                    for (i in 0 until jsonArray.length()) {
                        val kline = jsonArray.getJSONArray(i)
                        val openTime = kline.getLong(0) // Open time
                        val closePrice = kline.getString(4).toFloat() // Close price

                        entries.add(CandlestickEntry(openTime, closePrice))
                    }

                    // Sort by timestamp if not already sorted
                    entries.sortBy { it.timestamp }

                    withContext(Dispatchers.Main) {
                        chartEntries = entries
                        // Prepare data for Vico chart
                        val vicoEntries = entries.mapIndexed { index, entry ->
                            // Vico's entryModelOf takes x and y coordinates.
                            // x: index or a normalized time value
                            // y: closePrice
                            com.patrykandpatrick.vico.core.entry.ChartEntry(index.toFloat(), entry.closePrice)
                        }
                        chartEntryModelProducer.setEntries(listOf(vicoEntries))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    chartEntries = emptyList()
                }
            }
        }
    }

    init {
        // Initial data fetch
        // CoroutineScope needed for launch in non-Composable context,
        // but for init in PriceTrackerState, it will run on the current dispatcher
        // (which is Main implicitly for init, but withContext switches it).
        // It's better to launch these from a Composable's LaunchedEffect or ViewModelScope.
        // For simplicity here, we'll assume it's called from a Composable.
    }
}


@Composable
fun PriceTrackerScreen(modifier: Modifier = Modifier) {
    val state = rememberPriceTrackerState()

    // Fetch data when the Composable is first launched
    LaunchedEffect(Unit) {
        state.fetchCurrentPriceAndStats()
        state.fetch24HourChartData()
        // Optionally, set up a refresh mechanism
        while (true) {
            kotlinx.coroutines.delay(60000L) // Refresh every 60 seconds
            state.fetchCurrentPriceAndStats()
            state.fetch24HourChartData()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "PAXGUSDT Price Tracker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(text = "Current Price: ${state.currentPrice}", style = MaterialTheme.typography.titleLarge)
        Text(text = "24h Change: ${state.priceChange24h}", style = MaterialTheme.typography.titleMedium)
        Text(text = "24h Change %: ${state.priceChangePercent24h}", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "24-Hour Price Trend (1h interval)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Vico Line Chart
        if (state.chartEntries.isNotEmpty()) {
            val chartScrollState = rememberChartScrollState()
            val marker = rememberMarker() // Use the marker for interaction

            Chart(
                chart = lineChart(
                    lines = listOf(
                        rememberLineComponent( // First Line (main price line)
                            color = Color.Blue,
                            thickness = 2.dp,
                            shape = Shapes.pill, // Optional: shape for line ends
                        )
                    ),
                ),
                chartModelProducer = state.chartEntryModelProducer,
                startAxis = rememberStartAxis(
                    title = "Price (USDT)",
                    titleComponent = rememberTextComponent(
                        color = Color.Black,
                        background = rememberShapeComponent(shape = Shapes.pill, color = Color.LightGray),
                        padding = dimensionsOf(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        margins = dimensionsOf(end = 8.dp)
                    ),
                    label = rememberTextComponent(
                        color = Color.Black,
                        textSize = 10.sp, // Use sp for text size
                        background = rememberShapeComponent(shape = Shapes.rect, color = Color.LightGray.copy(alpha = 0.3f)),
                        padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp),
                    ),
                    axis = rememberLineComponent(
                        color = Color.Gray,
                        thickness = 1.dp
                    ),
                    tick = rememberLineComponent(
                        color = Color.Gray,
                        thickness = 1.dp
                    ),
                    guideline = rememberLineComponent(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                ),
                bottomAxis = rememberBottomAxis(
                    title = "Time",
                    titleComponent = rememberTextComponent(
                        color = Color.Black,
                        background = rememberShapeComponent(shape = Shapes.pill, color = Color.LightGray),
                        padding = dimensionsOf(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        margins = dimensionsOf(top = 8.dp)
                    ),
                    label = rememberTextComponent(
                        color = Color.Black,
                        textSize = 10.sp, // Use sp for text size
                        background = rememberShapeComponent(shape = Shapes.rect, color = Color.LightGray.copy(alpha = 0.3f)),
                        padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp),
                    ),
                    axis = rememberLineComponent(
                        color = Color.Gray,
                        thickness = 1.dp
                    ),
                    tick = rememberLineComponent(
                        color = Color.Gray,
                        thickness = 1.dp
                    ),
                    guideline = rememberLineComponent(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    ),
                    valueFormatter = { value, _ -> // Custom formatter for X-axis labels
                        val entryIndex = value.toInt()
                        if (entryIndex >= 0 && entryIndex < state.chartEntries.size) {
                            val timestamp = state.chartEntries[entryIndex].timestamp
                            // Format timestamp to a readable time (e.g., HH:mm)
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                        } else ""
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                scrollState = chartScrollState,
                marker = marker,
                isZoomEnabled = true // Enable pinch-to-zoom
            )
        } else {
            Text(text = "Loading chart data...", modifier = Modifier.fillMaxWidth())
        }
    }
}

// Marker Composable for Vico charts (for user interaction)
@Composable
private fun rememberMarker(): com.patrykandpatrick.vico.compose.chart.marker.Marker {
    val labelBackgroundShape = rememberShapeComponent(Shapes.rect, Color.Black)
    val labelTextColor = Color.White
    val labelTextPadding = dimensionsOf(all = 8.dp)
    val labelTextComponent = rememberTextComponent(
        color = labelTextColor,
        background = labelBackgroundShape,
        padding = labelTextPadding,
        margins = dimensionsOf(all = 4.dp)
    )
    val indicatorShape = rememberShapeComponent(Shapes.pill, Color.Blue)
    val indicator = rememberLineComponent(Color.Black, 2.dp, Shapes.pill)
    val guideline = rememberLineComponent(Color.Gray.copy(alpha = 0.5f), 1.dp, Shapes.dashed(10.dp, 5.dp))

    return remember(labelTextComponent, indicatorShape, guideline) {
        com.patrykandpatrick.vico.compose.chart.marker.rememberMarker(
            label = labelTextComponent,
            labelPosition = com.patrykandpatrick.vico.core.chart.marker.Marker.LabelPosition.Top,
            indicator = indicatorShape, // Or use indicator for a line at the point
            guideline = guideline
        )
    }
}

// Preview function
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AIAgentTheme {
        PriceTrackerScreen()
    }
}
```