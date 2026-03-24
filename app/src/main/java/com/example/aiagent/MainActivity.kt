package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aiagent.ui.theme.AiAgentTheme // 确保此主题文件存在
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    PriceChartScreen()
                }
            }
        }
    }
}

@Composable
fun PriceChartScreen() {
    // 使用 mutableStateListOf 存储价格数据，以便Compose能够跟踪列表内容的变化
    val prices = remember { mutableStateListOf<Float>() }
    // 使用 mutableStateOf 存储涨跌幅文本
    val priceChangeText = remember { mutableStateOf("加载中...") }

    // LaunchedEffect 在 Composable 首次进入组合时执行一次，用于触发数据加载
    LaunchedEffect(Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
            .build()

        // 确保网络请求在 IO 调度器上执行
        withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val gson = Gson()
                    // 定义解析 Klines 数据的类型：一个包含字符串列表的列表
                    val type = object : TypeToken<List<List<String>>>() {}.type
                    val klines: List<List<String>> = gson.fromJson(responseBody, type)

                    if (klines.isNotEmpty()) {
                        // 提取每个 KLine 数据的收盘价 (索引4)
                        val fetchedPrices = klines.map { it[4].toFloat() }
                        prices.addAll(fetchedPrices)

                        // 计算24小时涨跌幅：(最后收盘价 - 最初开盘价) / 最初开盘价 * 100%
                        val firstOpenPrice = klines.first()[1].toFloat() // 开盘价在索引1
                        val lastClosePrice = klines.last()[4].toFloat() // 收盘价在索引4
                        val change = ((lastClosePrice - firstOpenPrice) / firstOpenPrice) * 100

                        priceChangeText.value = "PAXGUSDT 24小时涨跌幅: ${DecimalFormat("0.00").format(change)}%"
                    } else {
                        priceChangeText.value = "没有数据。"
                    }
                } else {
                    priceChangeText.value = "请求失败: ${response.code} ${response.message}"
                }
            } catch (e: IOException) {
                priceChangeText.value = "网络错误: ${e.message}"
            } catch (e: Exception) {
                priceChangeText.value = "发生未知错误: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PAXGUSDT 价格走势") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 显示计算出的涨跌幅文本
            Text(
                text = priceChangeText.value,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 使用 AndroidView 嵌入 MPAndroidChart 的 LineChart
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp), // 设置图表高度
                factory = { context ->
                    LineChart(context).apply {
                        // 图表基础配置
                        description.isEnabled = false // 不显示描述文本
                        setTouchEnabled(true) // 允许触摸交互
                        isDragEnabled = true // 允许拖动
                        setScaleEnabled(true) // 允许缩放
                        setPinchZoom(true) // 允许捏合缩放
                        setDrawGridBackground(false) // 不绘制网格背景

                        // X 轴配置
                        xAxis.position = XAxis.XAxisPosition.BOTTOM // X 轴显示在底部
                        xAxis.setDrawGridLines(false) // 不绘制 X 轴网格线
                        xAxis.setDrawAxisLine(true) // 绘制 X 轴线
                        xAxis.granularity = 1f // X 轴最小间隔为 1
                        // 为 X 轴设置标签，从1到24小时
                        xAxis.valueFormatter = IndexAxisValueFormatter(List(24) { (it + 1).toString() })
                        xAxis.textColor = Color.BLACK

                        // 左 Y 轴配置
                        axisLeft.setDrawGridLines(true) // 绘制左 Y 轴网格线
                        axisLeft.setDrawAxisLine(true) // 绘制左 Y 轴线
                        axisLeft.textColor = Color.BLACK

                        // 右 Y 轴配置，禁用
                        axisRight.isEnabled = false

                        // 图例配置
                        legend.isEnabled = true // 显示图例
                        legend.textSize = 12f
                        legend.textColor = Color.BLACK
                    }
                },
                update = { lineChart ->
                    // 当 prices 列表发生变化时，更新图表数据
                    if (prices.isNotEmpty()) {
                        // 将价格数据转换为 MPAndroidChart 的 Entry 对象
                        val entries = prices.mapIndexed { index, price ->
                            Entry(index.toFloat(), price)
                        }

                        // 创建 LineDataSet
                        val dataSet = LineDataSet(entries, "PAXGUSDT 价格 (USD)").apply {
                            color = Color.BLUE // 设置线条颜色
                            valueTextColor = Color.BLACK
                            lineWidth = 2f // 线条宽度
                            setDrawValues(false) // 不在图表上绘制具体数值
                            setDrawCircles(false) // 不绘制数据点上的圆圈
                            mode = LineDataSet.Mode.CUBIC_BEZIER // 曲线平滑模式
                            setDrawFilled(true) // 绘制填充区域
                            fillColor = Color.parseColor("#80ADD8E6") // 填充颜色（半透明浅蓝色）
                        }

                        // 将 LineDataSet 封装到 LineData 中
                        val lineData = LineData(dataSet)
                        // 设置图表数据
                        lineChart.data = lineData
                        lineChart.animateX(1500) // X轴动画
                        lineChart.invalidate() // 刷新图表
                    }
                }
            )
        }
    }
}