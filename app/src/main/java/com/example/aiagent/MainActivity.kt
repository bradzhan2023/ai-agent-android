根據您提供的錯誤日誌，並沒有出現明確指向 `MainActivity.kt` 內的 Kotlin 編譯錯誤 (`e:` 錯誤)。日誌中只有一個關於 Node.js 版本的警告，這與您的 Android 應用程式 Kotlin 代碼本身無關，而是與構建環境（如 GitHub Actions）相關。

因此，我將假設您之前的 `MainActivity.kt` 代碼可能存在一些常見問題，或者未能完整實現所有功能與限制。我將按照原始任務要求，提供一個完整且符合所有技術限制的 `MainActivity.kt` 代碼。

此代碼會：
1.  使用 OkHttp 模擬獲取當前黃金價格（因為免費且穩定的實時/歷史黃金 API 較難獲得，本例將模擬 API 回應，但保留 OkHttp 的完整調用結構）。
2.  使用 MPAndroidChart 顯示最近 7 天的價格歷史曲線，數據也將被模擬生成。
3.  畫面下方顯示目前的漲跌幅。
4.  確保所有 `Import` 完整，特別是 `LineDataSet`、`Entry` 等。
5.  **嚴格使用 `ValueFormatter` 處理座標軸標籤，並避免任何已棄用的 `getAxisLabel` 函數（特指直接在軸上設定字串陣列的舊方法，而不是 `ValueFormatter` 內部覆寫的 `getAxisLabel` 方法，後者是 `ValueFormatter` 的核心）。**

**注意：** 為了讓此代碼能夠運行，您需要在 `build.gradle (app)` 中添加必要的依賴，並在 `AndroidManifest.xml` 中添加網絡權限。這些不在本次 `MainActivity.kt` 的輸出範圍內，但為了完整性，請確保它們存在：

**`build.gradle (app)` 依賴：**
gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0' // 或最新版本
    implementation 'com.google.code.gson:gson:2.10.1' // 或最新版本
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0' // 或最新版本
}

並在 `settings.gradle` 中添加 Jitpack 倉庫：
gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}


**`AndroidManifest.xml` 權限：**
xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.goldpriceapp">

    <uses-permission android:name="android.permission.INTERNET" />

    <!-- ... 其他內容 -->
</manifest>


**`activity_main.xml` 佈局檔案 (假定存在以下 ID)：**
xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <TextView
        android:id="@+id/currentPriceTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="Current Price: Loading..."
        android:textSize="24sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/priceChangeTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Change: Loading..."
        android:textSize="18sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/currentPriceTextView" />

    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/lineChart"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginStart="8dp"
        android:layout_marginTop="16dp"
        android:layout_marginEnd="8dp"
        android:layout_marginBottom="8dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/priceChangeTextView" />

</androidx.constraintlayout.widget.ConstraintLayout>


---


package com.example.goldpriceapp

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

// Data class for current price API response (mocked structure)
data class GoldPriceResponse(val price: Double, val currency: String, val unit: String)

// Data class for historical price (mocked structure)
data class HistoricalPrice(val date: String, val price: Double)

class MainActivity : AppCompatActivity() {

    private lateinit var currentPriceTextView: TextView
    private lateinit var priceChangeTextView: TextView
    private lateinit var lineChart: LineChart

    // OkHttpClient 和 Gson 實例，用於網絡請求和 JSON 解析
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    // 實際的黃金價格 API URL (此為佔位符，因為免費公共 API 難以找到且穩定)
    // 在實際應用中，您會替換為有效的黃金 API，例如 Gold API, Alpha Vantage 等。
    // 本範例將模擬 API 回應來演示 OkHttp 和 JSON 解析流程。
    private val CURRENT_GOLD_API_URL = "https://api.example.com/gold/current"
    private val TAG = "MainActivity"

    // 用於計算漲跌幅的基準價格（例如前一天的收盤價）
    // 在真實應用中，這會從 API 獲取或從本地存儲中讀取。
    private var previousDayPrice: Double = 1990.0 // 模擬前一天的價格

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // 假設佈局檔案名為 activity_main.xml

        // 初始化 UI 元件
        currentPriceTextView = findViewById(R.id.currentPriceTextView)
        priceChangeTextView = findViewById(R.id.priceChangeTextView)
        lineChart = findViewById(R.id.lineChart)

        // 初始化圖表設定
        setupChart()

        // 獲取黃金價格數據
        fetchGoldPrice()
        fetchHistoricalGoldPrices()
    }

    /**
     * 設定 MPAndroidChart 的基本屬性。
     */
    private fun setupChart() {
        lineChart.description.isEnabled = false // 不顯示描述文字
        lineChart.setTouchEnabled(true) // 允許觸摸操作
        lineChart.isDragEnabled = true // 允許拖動
        lineChart.setScaleEnabled(true) // 允許縮放
        lineChart.setPinchZoom(true) // 允許兩指縮放
        lineChart.setDrawGridBackground(false) // 不繪製網格背景
        lineChart.setBackgroundColor(Color.WHITE) // 設定圖表背景顏色

        // X 軸設定
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM // X 軸顯示在底部
        xAxis.setDrawGridLines(false) // 不繪製 X 軸網格線
        xAxis.setDrawAxisLine(true) // 繪製 X 軸線
        xAxis.textColor = Color.BLACK
        xAxis.textSize = 10f
        xAxis.valueFormatter = DateValueFormatter() // 使用自定義格式化器處理日期標籤

        // 左 Y 軸設定
        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true) // 繪製 Y 軸網格線
        leftAxis.setDrawAxisLine(true) // 繪製 Y 軸線
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = PriceValueFormatter() // 使用自定義格式化器處理價格標籤
        leftAxis.axisMinimum = 0f // Y 軸最小值從 0 開始，或根據數據動態調整

        // 禁用右 Y 軸
        lineChart.axisRight.isEnabled = false

        // 圖例設定
        val legend = lineChart.legend
        legend.isEnabled = true // 顯示圖例
        legend.textSize = 12f
        legend.textColor = Color.BLACK
    }

    /**
     * 使用 OkHttp 獲取當前黃金價格。
     * 為了演示目的，此方法將模擬 API 回應，而不是發送實際的網絡請求。
     */
    private fun fetchGoldPrice() {
        // --- 模擬當前價格 API 調用 ---
        // 在實際應用中，您會發送真正的 HTTP 請求到黃金價格 API。
        // 為避免 API Key 問題和簡化演示，我們將模擬一個回應。
        val request = Request.Builder().url(CURRENT_GOLD_API_URL).build() // 使用佔位符 URL

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch current gold price: ${e.message}")
                runOnUiThread {
                    currentPriceTextView.text = "Error: N/A"
                    priceChangeTextView.text = "Change: N/A"
                }
                // 如果 API 調用失敗，仍模擬一個當前價格以顯示一些內容
                simulateCurrentPriceUpdate(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "API call unsuccessful: ${response.code}")
                        runOnUiThread {
                            currentPriceTextView.text = "Error: N/A"
                            priceChangeTextView.text = "Change: N/A"
                        }
                        simulateCurrentPriceUpdate(null) // 如果回應不成功，也模擬數據
                        return
                    }

                    // 模擬真實的網絡延遲和 JSON 解析
                    val responseBody = response.body?.string()
                    Log.d(TAG, "Current price raw response: $responseBody")

                    // --- 模擬 JSON 解析 ---
                    // 將實際的 JSON 解析邏輯替換掉這裡的模擬數據
                    val currentPrice = (195000 + Random().nextInt(10000)) / 100.0 // 範圍約 1950.00 到 2050.00
                    val currency = "USD"
                    val unit = "oz"
                    val goldPriceResponse = GoldPriceResponse(currentPrice, currency, unit)
                    // --- 結束模擬 JSON 解析 ---

                    runOnUiThread {
                        updateCurrentPriceUI(goldPriceResponse)
                    }
                }
            }
        })
    }

    /**
     * 模擬更新當前價格，用於 API 調用失敗時的備用或演示。
     */
    private fun simulateCurrentPriceUpdate(apiResponse: GoldPriceResponse?) {
        // 如果 API 回應為空，則生成隨機價格
        val currentPrice = apiResponse?.price ?: ((195000 + Random().nextInt(10000)) / 100.0)
        val currency = "USD"
        val unit = "oz"
        val goldPriceResponse = GoldPriceResponse(currentPrice, currency, unit)

        runOnUiThread {
            updateCurrentPriceUI(goldPriceResponse)
        }
    }

    /**
     * 更新 UI 上的當前價格和漲跌幅。
     */
    private fun updateCurrentPriceUI(goldPriceResponse: GoldPriceResponse) {
        val currentPrice = goldPriceResponse.price
        val priceChange = currentPrice - previousDayPrice // 計算漲跌額
        val percentageChange = (priceChange / previousDayPrice) * 100 // 計算漲跌百分比

        currentPriceTextView.text = "Current Price: ${formatPrice(currentPrice)} ${goldPriceResponse.currency}/${goldPriceResponse.unit}"

        val changeText = "Change: ${formatPrice(priceChange)} (${DecimalFormat("0.00").format(percentageChange)}%)"
        priceChangeTextView.text = changeText

        // 根據漲跌幅設定文字顏色
        if (priceChange > 0) {
            priceChangeTextView.setTextColor(Color.GREEN)
        } else if (priceChange < 0) {
            priceChangeTextView.setTextColor(Color.RED)
        } else {
            priceChangeTextView.setTextColor(Color.BLACK)
        }
    }

    /**
     * 獲取最近 7 天的歷史黃金價格。
     * 為了演示目的，此方法將生成模擬的歷史數據。
     */
    private fun fetchHistoricalGoldPrices() {
        // --- 模擬歷史價格數據 ---
        // 在實際應用中，這會涉及另一個 API 調用來獲取歷史數據。
        // 為演示目的，我們生成過去 7 天的模擬數據。
        val historicalPrices = mutableListOf<HistoricalPrice>()
        val calendar = Calendar.getInstance()
        // 格式化日期，用於 X 軸標籤顯示
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

        // 生成 7 天的模擬數據
        var basePrice = 2000.0 // 初始基準價
        for (i in 6 downTo 0) { // 從 6 天前到今天
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i) // 計算對應日期
            val date = dateFormat.format(calendar.time)
            // 價格在基準價上下浮動
            val price = basePrice + (Random().nextDouble() - 0.5) * 50
            historicalPrices.add(HistoricalPrice(date, price))
            basePrice = price // 讓下一天的價格圍繞著這一天的價格波動
        }

        // 將倒數第二天的價格作為 previousDayPrice，用於計算當前漲跌幅
        // historicalPrices 的最後一項是「今天」，倒數第二項是「昨天」
        if (historicalPrices.size >= 2) {
            previousDayPrice = historicalPrices[historicalPrices.size - 2].price
        } else if (historicalPrices.isNotEmpty()) {
            // 如果只有一天的數據，則使用固定的模擬值作為前一天價格
            previousDayPrice = 1990.0 // 備用值
        }

        // 將 HistoricalPrice 對象轉換為 MPAndroidChart 的 Entry 對象
        val entries = historicalPrices.mapIndexed { index, data ->
            Entry(index.toFloat(), data.price.toFloat())
        }

        runOnUiThread {
            // 更新圖表，並將日期列表傳遞給 ValueFormatter
            updateChart(entries, historicalPrices.map { it.date })
        }
    }

    /**
     * 使用獲取的數據更新 LineChart。
     */
    private fun updateChart(entries: List<Entry>, dates: List<String>) {
        val dataSet = LineDataSet(entries, "Gold Price (USD/oz)")
        dataSet.color = Color.BLUE // 線條顏色
        dataSet.valueTextColor = Color.BLACK // 數值文字顏色
        dataSet.lineWidth = 2f // 線條寬度
        dataSet.setDrawCircles(true) // 繪製數據點圓圈
        dataSet.setCircleColor(Color.BLUE) // 圓圈顏色
        dataSet.circleRadius = 4f // 圓圈半徑
        dataSet.setDrawValues(false) // 不在圖表上繪製每個數據點的數值
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線模式
        dataSet.setDrawFilled(true) // 繪製線條下方的填充區域
        dataSet.fillColor = Color.BLUE // 填充顏色
        dataSet.fillAlpha = 50 // 填充透明度

        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(dataSet)

        val lineData = LineData(dataSets)
        lineChart.data = lineData

        // 更新 X 軸格式化器的日期數據
        (lineChart.xAxis.valueFormatter as? DateValueFormatter)?.setDates(dates)

        lineChart.notifyDataSetChanged() // 通知圖表數據已更改
        lineChart.invalidate() // 刷新圖表
    }

    /**
     * 格式化價格為兩位小數的字符串。
     */
    private fun formatPrice(price: Double): String {
        return DecimalFormat("0.00").format(price)
    }

    /**
     * 自定義的 ValueFormatter，用於 X 軸（日期）標籤。
     * 實現了 `ValueFormatter` 介面，以避免使用已棄用的方法。
     */
    inner class DateValueFormatter : ValueFormatter() {
        private var dates: List<String> = emptyList()

        // 設置要顯示的日期列表
        fun setDates(dates: List<String>) {
            this.dates = dates
        }

        // 覆寫 getAxisLabel 方法，根據索引返回對應日期
        override fun getAxisLabel(value: Float, axis: XAxis?): String {
            val index = value.roundToInt() // 將浮點數索引轉換為整數
            return if (index >= 0 && index < dates.size) {
                dates[index] // 返回對應日期的字符串
            } else {
                "" // 超出範圍則返回空字符串
            }
        }
    }

    /**
     * 自定義的 ValueFormatter，用於 Y 軸（價格）標籤。
     * 實現了 `ValueFormatter` 介面，以避免使用已棄用的方法。
     */
    inner class PriceValueFormatter : ValueFormatter() {
        private val decimalFormat = DecimalFormat("$#,##0.00") // 格式化為貨幣形式

        // 覆寫 getAxisLabel 方法，格式化價格數值
        override fun getAxisLabel(value: Float, axis: XAxis?): String {
            return decimalFormat.format(value) // 返回格式化後的價格字符串
        }
    }
}