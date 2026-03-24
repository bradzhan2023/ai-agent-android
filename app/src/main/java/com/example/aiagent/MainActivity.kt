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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

// Data class to hold parsed Kline data from Binance API
data class KlineData(
    val openTime: Long,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val volume: Double,
    val closeTime: Long,
    val quoteAssetVolume: Double,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: Double,
    val takerBuyQuoteAssetVolume: Double,
    val ignore: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 严格遵循规范：直接使用 androidx.compose.material3.MaterialTheme
            MaterialTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KlineScreen()
                }
            }
        }
    }
}

@Composable
fun KlineScreen() {
    var currentPrice by remember { mutableStateOf<Double?>(null) }
    var priceChangePercentage by remember { mutableStateOf<Double?>(null) }
    var chartEntries by remember { mutableStateOf(emptyList<Entry>()) }
    var xValueFormatter by remember { mutableStateOf<ValueFormatter?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 使用 LaunchedEffect 在 Composable 进入组合时执行网络请求
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { // 确保网络请求在 IO 调度器上执行
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let {
                        val jsonArray = JSONArray(it)
                        val klines = mutableListOf<KlineData>()
                        val entries = mutableListOf<Entry>()
                        val openTimes = mutableListOf<Long>() // 用于存储时间戳，以便在X轴上格式化显示

                        for (i in 0 until jsonArray.length()) {
                            val klineJson = jsonArray.getJSONArray(i)
                            val kline = KlineData(
                                openTime = klineJson.getLong(0),
                                openPrice = klineJson.getString(1).toDouble(),
                                highPrice = klineJson.getString(2).toDouble(),
                                lowPrice = klineJson.getString(3).toDouble(),
                                closePrice = klineJson.getString(4).toDouble(),
                                volume = klineJson.getString(5).toDouble(),
                                closeTime = klineJson.getLong(6),
                                quoteAssetVolume = klineJson.getString(7).toDouble(),
                                numberOfTrades = klineJson.getInt(8),
                                takerBuyBaseAssetVolume = klineJson.getString(9).toDouble(),
                                takerBuyQuoteAssetVolume = klineJson.getString(10).toDouble(),
                                ignore = klineJson.getString(11)
                            )
                            klines.add(kline)
                            entries.add(Entry(i.toFloat(), kline.closePrice.toFloat()))
                            openTimes.add(kline.openTime)
                        }

                        // 计算当前价格和今日涨跌幅
                        val latestKline = klines.lastOrNull()
                        val firstKline = klines.firstOrNull() // 24小时前的价格

                        withContext(Dispatchers.Main) { // UI更新必须在主线程
                            currentPrice = latestKline?.closePrice
                            if (latestKline != null && firstKline != null && firstKline.closePrice != 0.0) {
                                priceChangePercentage =
                                    ((latestKline.closePrice - firstKline.closePrice) / firstKline.closePrice) * 100
                            }
                            chartEntries = entries

                            // 自定义X轴日期格式化器
                            xValueFormatter = object : ValueFormatter() {
                                private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                override fun getAxisLabel(value: Float, axis: XAxis?): String {
                                    return if (value.toInt() >= 0 && value.toInt() < openTimes.size) {
                                        dateFormat.format(Date(openTimes[value.toInt()]))
                                    } else {
                                        ""
                                    }
                                }
                            }
                            isLoading = false
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Response body is null"
                            isLoading = false
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Error: ${response.code} - ${response.message}"
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to fetch data: ${e.localizedMessage}"
                    isLoading = false
                }
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.wrapContentSize())
        } else if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.headlineSmall
            )
        } else {
            // UI 顶部显示：当前价格
            currentPrice?.let {
                Text(
                    text = "当前价格: %.2f USDT".format(it),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // UI 顶部显示：今日涨跌幅 (%)
            priceChangePercentage?.let {
                val color = when {
                    it > 0 -> Color.GREEN
                    it < 0 -> Color.RED
                    else -> Color.GRAY
                }
                Text(
                    text = "24h 涨跌幅: %.2f%%".format(it),
                    style = MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color(color), // 使用 Compose 的 Color
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // UI 下方嵌入 MPAndroidChart LineChart
            if (chartEntries.isNotEmpty()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            description.isEnabled = false // 不显示描述
                            setTouchEnabled(true)
                            isDragEnabled = true
                            setScaleEnabled(true)
                            setPinchZoom(true)
                            setDrawGridBackground(false) // 不绘制网格背景
                            axisRight.isEnabled = false // 禁用右侧Y轴

                            // 自定义X轴
                            xAxis.position = XAxis.XAxisPosition.BOTTOM
                            xAxis.setDrawGridLines(false) // 不绘制X轴网格线
                            xAxis.granularity = 1f // 最小刻度间隔
                            xAxis.valueFormatter = xValueFormatter // 设置自定义格式化器
                            xAxis.labelRotationAngle = -45f // 旋转X轴标签，防止重叠
                            xAxis.textColor = Color.DKGRAY

                            // 自定义左侧Y轴
                            axisLeft.setDrawGridLines(true)
                            axisLeft.setDrawZeroLine(false)
                            axisLeft.textColor = Color.DKGRAY
                            axisLeft.setDrawAxisLine(true)

                            legend.isEnabled = false // 禁用图例
                        }
                    },
                    update = { chart ->
                        val dataSet = LineDataSet(chartEntries, "PAXGUSDT Price").apply {
                            color = Color.BLUE
                            valueTextColor = Color.BLACK
                            setDrawValues(false) // 不在图表上绘制具体值
                            setDrawCircles(false) // 不绘制数据点上的圆圈
                            lineWidth = 2f
                            mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲线
                            fillColor = Color.BLUE
                            setDrawFilled(true) // 绘制曲线下方的填充颜色
                            fillAlpha = 50 // 填充颜色透明度
                        }

                        chart.data = LineData(dataSet)
                        chart.invalidate() // 刷新图表
                    }
                )
            } else if (!isLoading && errorMessage == null) {
                Text(text = "没有图表数据可用。")
            }
        }
    }
}