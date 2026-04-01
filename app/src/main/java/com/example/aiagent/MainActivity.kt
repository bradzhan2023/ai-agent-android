package com.example.goldpriceapp

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis // 完整匯入 YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// 數據模型用於解析 API 回應
data class GoldPriceResponse(
    @SerializedName("currentPriceUsdPerOz") val currentPriceUsdPerOz: Double,
    @SerializedName("prices") val prices: List<PriceEntry>
)

data class PriceEntry(
    @SerializedName("date") val date: String, // 例如: "YYYY-MM-DD"
    @SerializedName("price") val price: Double
)

class MainActivity : AppCompatActivity() {

    private lateinit var currentPriceTextView: TextView
    private lateinit var priceChangeTextView: TextView
    private lateinit var goldPriceChart: LineChart

    // 初始化 OkHttpClient，設定超時時間
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // 儲存歷史日期，用於 X 軸標籤顯示
    private val historicalDates = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // 假設佈局檔案為 activity_main.xml

        // 初始化 UI 元件
        currentPriceTextView = findViewById(R.id.currentPriceTextView)
        priceChangeTextView = findViewById(R.id.priceChangeTextView)
        goldPriceChart = findViewById(R.id.goldPriceChart)

        // 啟動資料獲取
        fetchGoldPrices()
    }

    private fun fetchGoldPrices() {
        // 使用 CoroutineScope 在 IO 執行緒中執行網路請求
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // *** 模擬網路延遲和 API 回應 ***
                // 在真實應用中，請將以下模擬內容替換為實際的 API 請求
                val mockApiResponse = """
                    {
                      "currentPriceUsdPerOz": 2350.75,
                      "prices": [
                        {"date": "2024-03-26", "price": 2200.50},
                        {"date": "2024-03-27", "price": 2210.25},
                        {"date": "2024-03-28", "price": 2230.10},
                        {"date": "2024-03-29", "price": 2250.80},
                        {"date": "2024-03-30", "price": 2245.90},
                        {"date": "2024-03-31", "price": 2300.30},
                        {"date": "2024-04-01", "price": 2350.75}
                      ]
                    }
                """.trimIndent()

                // *** 真實 API 請求的範例 (需替換 URL 和可能存在的 API Key) ***
                /*
                val request = Request.Builder()
                    .url("YOUR_GOLD_API_ENDPOINT_HERE?days=7") // 替換為實際的 API 端點
                    .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw IOException("Unexpected code ${response.code}")
                }

                val responseBody = response.body?.string()
                if (responseBody == null) {
                    throw IOException("Empty response body")
                }
                val goldData = gson.fromJson(responseBody, GoldPriceResponse::class.java)
                 */
                // 目前使用模擬數據
                val goldData = gson.fromJson(mockApiResponse, GoldPriceResponse::class.java)

                // 切換回主執行緒更新 UI
                withContext(Dispatchers.Main) {
                    updateUI(goldData)
                }

            } catch (e: Exception) {
                // 記錄錯誤日誌
                Log.e("MainActivity", "Error fetching gold prices: ${e.message}", e)
                // 在主執行緒更新 UI 顯示錯誤訊息
                withContext(Dispatchers.Main) {
                    currentPriceTextView.text = "錯誤: 無法載入價格"
                    priceChangeTextView.text = ""
                }
            }
        }
    }

    private fun updateUI(goldData: GoldPriceResponse) {
        // 1. 更新當前價格
        val currentPrice = goldData.currentPriceUsdPerOz
        currentPriceTextView.text = String.format(Locale.US, "%.2f USD/oz", currentPrice)

        // 2. 計算並顯示漲跌幅
        // 需要至少兩天的數據才能計算漲跌幅 (最新價格 vs. 前一天價格)
        if (goldData.prices.size >= 2) {
            val latestPrice = goldData.prices.last().price
            val previousDayPrice = goldData.prices[goldData.prices.size - 2].price

            val change = latestPrice - previousDayPrice
            val percentageChange = (change / previousDayPrice) * 100

            val decimalFormat = DecimalFormat("0.00")
            val changeText = if (change >= 0) {
                priceChangeTextView.setTextColor(Color.GREEN) // 上漲顯示綠色
                "+${decimalFormat.format(percentageChange)}%"
            } else {
                priceChangeTextView.setTextColor(Color.RED) // 下跌顯示紅色
                "${decimalFormat.format(percentageChange)}%"
            }
            priceChangeTextView.text = changeText
        } else {
            priceChangeTextView.text = "N/A"
            priceChangeTextView.setTextColor(Color.GRAY)
        }

        // 3. 使用歷史數據填充圖表
        setupChart(goldData.prices)
    }

    private fun setupChart(prices: List<PriceEntry>) {
        historicalDates.clear() // 清除之前的日期數據
        val entries = ArrayList<Entry>()
        for ((index, priceEntry) in prices.withIndex()) {
            // Entry(X軸位置, Y軸數值)
            entries.add(Entry(index.toFloat(), priceEntry.price.toFloat()))
            historicalDates.add(priceEntry.date) // 儲存日期字串用於 X 軸標籤
        }

        val dataSet = LineDataSet(entries, "黃金價格 (USD/oz)")
        dataSet.color = Color.BLUE
        dataSet.valueTextColor = Color.BLACK
        dataSet.setDrawCircles(true) // 顯示數據點圓圈
        dataSet.setCircleColor(Color.BLUE)
        dataSet.lineWidth = 2f
        dataSet.valueTextSize = 10f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線
        dataSet.setDrawValues(false) // 不在曲線上顯示每個數據點的數值

        val lineData = LineData(dataSet)
        goldPriceChart.data = lineData

        // 配置 X 軸
        val xAxis = goldPriceChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM // X 軸位於底部
        xAxis.setDrawGridLines(false) // 不繪製網格線
        xAxis.granularity = 1f // 最小間隔為 1 (每個數據點一個標籤)
        // 使用自定義的 DateAxisValueFormatter 處理 X 軸日期標籤
        xAxis.valueFormatter = DateAxisValueFormatter(historicalDates)
        xAxis.labelRotationAngle = -45f // 旋轉標籤以避免重疊

        // 配置左側 Y 軸
        val leftYAxis = goldPriceChart.axisLeft
        leftYAxis.setDrawGridLines(true) // 繪製網格線
        // 使用自定義的 PriceYAxisValueFormatter 處理 Y 軸價格標籤
        leftYAxis.valueFormatter = PriceYAxisValueFormatter()
        leftYAxis.setStartAtZero(false) // 不強制 Y 軸從 0 開始，以更好地顯示價格波動

        // 禁用右側 Y 軸
        val rightYAxis = goldPriceChart.axisRight
        rightYAxis.isEnabled = false

        // 圖表通用設置
        goldPriceChart.description.isEnabled = false // 不顯示描述標籤
        goldPriceChart.legend.isEnabled = true // 顯示圖例
        goldPriceChart.setTouchEnabled(true) // 允許觸摸互動 (縮放、拖動)
        goldPriceChart.setPinchZoom(true) // 允許雙指縮放
        goldPriceChart.setNoDataText("正在載入數據...") // 無數據時顯示的文字

        goldPriceChart.invalidate() // 刷新圖表以顯示最新數據
    }

    // 自定義 ValueFormatter，用於格式化 X 軸日期標籤
    class DateAxisValueFormatter(private val dates: List<String>) : ValueFormatter() {
        private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val outputDateFormat = SimpleDateFormat("MM/dd", Locale.US) // 輸出格式為 月/日

        override fun getAxisLabel(value: Float, axis: XAxis?): String {
            val index = value.toInt()
            return if (index >= 0 && index < dates.size) {
                try {
                    val date: Date? = inputDateFormat.parse(dates[index])
                    date?.let { outputDateFormat.format(it) } ?: dates[index]
                } catch (e: Exception) {
                    Log.e("DateFormatter", "Error parsing date: ${dates[index]}", e)
                    dates[index] // 解析失敗時返回原始字串
                }
            } else {
                "" // 超出範圍返回空字串
            }
        }
    }

    // 自定義 ValueFormatter，用於格式化 Y 軸價格標籤
    class PriceYAxisValueFormatter : ValueFormatter() {
        private val decimalFormat = DecimalFormat("0.00") // 價格格式化為兩位小數

        override fun getAxisLabel(value: Float, axis: YAxis?): String {
            return decimalFormat.format(value)
        }
    }
}