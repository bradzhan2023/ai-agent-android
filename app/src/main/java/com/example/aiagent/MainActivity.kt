根據您提供的錯誤日誌，問題非常明確：您將 Gradle 依賴項的宣告（例如 `implementation '...'`）錯誤地放置在了 `MainActivity.kt` 這個 Kotlin 原始碼檔案中。Kotlin 編譯器無法識別這些 Gradle DSL (Domain Specific Language) 語法，因此將它們報告為「未解析的引用」或「字元常數中字元過多」等錯誤。

Gradle 依賴項必須在專案的 `build.gradle` 檔案（通常是 `app/build.gradle`）中宣告。

以下是修復方案，我將提供：
1.  **修正後的 `app/build.gradle` 檔案**：包含所有必要的依賴項，包括 OkHttp、Gson、MPAndroidChart 和 Compose 相關庫。
2.  **修正後的 `MainActivity.kt` 檔案**：移除了錯誤的依賴項宣告，並包含了實作金價追蹤功能的完整 Kotlin 程式碼。

---

**1. `app/build.gradle` (Module :app) - 修正後的內容**

請將您專案中的 `app/build.gradle` 檔案替換為以下內容。我已經將所有必要的依賴項都加入了進去。

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.aiagent'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.aiagent"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary true
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = '1.8'
    }
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.1' // 確保與您的Android Studio版本相符
    }
    packaging {
        resources {
            excludes '/META-INF/{AL2.0,LGPL2.1}'
        }
    }
}

dependencies {
    // AndroidX & Compose 基礎依賴
    implementation platform('androidx.compose:compose-bom:2023.08.00') // 修正了錯誤日誌中的字元常數問題
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.7.0'

    // OkHttp for 網路請求
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    // Gson for JSON 解析
    implementation 'com.google.code.gson:gson:2.10.1'

    // Kotlin Coroutines for 非同步操作
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // MPAndroidChart for 繪製圖表 (需要通過 AndroidView 嵌入到 Compose)
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // Compose Tooling, 包含 AndroidView 支援等
    debugImplementation 'androidx.compose.ui:ui-tooling' // 通常在 debug 環境下使用

    // 測試依賴
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    androidTestImplementation platform('androidx.compose:compose-bom:2023.08.00')
    androidTestImplementation 'androidx.compose.ui:ui-test-junit4'
    debugImplementation 'androidx.compose.ui:ui-test-manifest'
}
```

---

**2. `MainActivity.kt` - 修正後的內容**

請將您專案中的 `app/src/main/java/com/example/aiagent/MainActivity.kt` 檔案替換為以下內容。我已經移除了所有錯誤的 Gradle 依賴項宣告，並實作了金價追蹤功能。

```kotlin
package com.example.aiagent

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagent.ui.theme.AiAgentTheme
import com.google.gson.Gson
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ViewModel 用於管理數據獲取和狀態
class BinancePriceViewModel : ViewModel() {

    // 當前價格的 StateFlow
    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice

    // 歷史價格數據的 StateFlow (用於圖表)
    private val _priceHistory = MutableStateFlow<List<PricePoint>>(emptyList())
    val priceHistory: StateFlow<List<PricePoint>> = _priceHistory

    // OkHttp 客戶端
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    // Gson 解析器
    private val gson = Gson()

    init {
        // ViewModel 初始化時立即獲取價格數據
        fetchPriceData()
    }

    // 獲取價格數據的函式
    fun fetchPriceData() {
        viewModelScope.launch { // 在 ViewModel 的作用域中啟動協程
            try {
                // 1. 獲取當前價格
                val currentPriceRequest = Request.Builder()
                    .url("https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT")
                    .build()

                val currentPriceResponse = withContext(Dispatchers.IO) { // 在 IO 執行緒中執行網路請求
                    client.newCall(currentPriceRequest).execute()
                }
                if (currentPriceResponse.isSuccessful) {
                    val json = currentPriceResponse.body?.string()
                    val ticker = gson.fromJson(json, BinanceTicker::class.java)
                    _currentPrice.value = ticker.price.toDoubleOrNull()
                } else {
                    Log.e("BinanceViewModel", "Failed to fetch current price: ${currentPriceResponse.code}")
                }

                // 2. 獲取 24 小時 K 線數據
                // interval=1h (1小時), limit=24 (最近24個數據點)
                val klinesRequest = Request.Builder()
                    .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
                    .build()

                val klinesResponse = withContext(Dispatchers.IO) {
                    client.newCall(klinesRequest).execute()
                }
                if (klinesResponse.isSuccessful) {
                    val json = klinesResponse.body?.string()
                    // Binance K線 API 返回一個陣列的陣列
                    val klines = gson.fromJson(json, Array<Array<String>>::class.java)
                    val history = klines.mapNotNull { klineArray ->
                        if (klineArray.size > 4) {
                            // klineArray[0] 是開盤時間 (Long), klineArray[4] 是收盤價格 (String)
                            val timestamp = klineArray[0].toLong()
                            val price = klineArray[4].toDoubleOrNull()
                            if (price != null) PricePoint(timestamp, price) else null
                        } else null
                    }
                    _priceHistory.value = history
                } else {
                    Log.e("BinanceViewModel", "Failed to fetch klines: ${klinesResponse.code}")
                }
            } catch (e: Exception) {
                Log.e("BinanceViewModel", "Error fetching Binance data: ${e.message}", e)
                _currentPrice.value = null // 清除錯誤時的價格
                _priceHistory.value = emptyList() // 清除錯誤時的歷史數據
            }
        }
    }
}

// 數據模型
data class BinanceTicker(val symbol: String, val price: String)
data class PricePoint(val timestamp: Long, val price: Double)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                // 應用程式的表面容器
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BinanceTrackerApp()
                }
            }
        }
    }
}

@Composable
fun BinanceTrackerApp(viewModel: BinancePriceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    // 收集 ViewModel 中的 StateFlow 數據
    val currentPrice by viewModel.currentPrice.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top // 內容從頂部開始排列
    ) {
        Text(
            text = "PAXGUSDT 追蹤器",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        PriceDisplay(currentPrice = currentPrice)

        Spacer(modifier = Modifier.height(16.dp)) // 間距

        if (priceHistory.isNotEmpty()) {
            ChartDisplay(pricePoints = priceHistory)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) // 顯示加載指示器
            Text("正在加載圖表數據...", modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun PriceDisplay(currentPrice: Double?) {
    Card(modifier = Modifier.fillMaxWidth()) { // 卡片樣式顯示價格
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "當前 PAXG/USDT 價格:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentPrice?.let { String.format("$%.2f", it) } ?: "加載中...", // 格式化價格，如果為空則顯示"加載中..."
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ChartDisplay(pricePoints: List<PricePoint>) {
    // 使用 AndroidView 將傳統 View 嵌入到 Compose UI 中
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp), // 設定圖表高度
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false // 不顯示描述
                setTouchEnabled(true) // 允許觸摸操作
                isDragEnabled = true // 允許拖動
                setScaleEnabled(true) // 允許縮放
                setPinchZoom(true) // 允許捏合縮放
                setBackgroundColor(Color.TRANSPARENT) // 圖表背景透明
                setDrawGridBackground(false) // 不繪製網格背景

                xAxis.apply {
                    setDrawGridLines(false) // 不繪製X軸網格線
                    setDrawAxisLine(true) // 繪製X軸線
                    position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM // X軸在底部
                    textColor = Color.WHITE // X軸標籤文字顏色 (適合深色主題)
                    // X軸數值格式化，顯示時間
                    valueFormatter = object : IndexAxisValueFormatter() {
                        private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault()) // 時間格式
                        override fun getFormattedValue(value: Float): String {
                            // 'value' 是 Entry 的索引
                            return if (value >= 0 && value < pricePoints.size) {
                                val timestamp = pricePoints[value.toInt()].timestamp
                                mFormat.format(Date(timestamp))
                            } else ""
                        }
                    }
                    granularity = 1f // X軸標籤最小間隔
                    setLabelCount(4, true) // 大約顯示4個標籤
                }

                axisLeft.apply {
                    setDrawGridLines(true) // 繪製Y軸網格線
                    setDrawAxisLine(true) // 繪製Y軸線
                    textColor = Color.WHITE // Y軸標籤文字顏色
                }

                axisRight.isEnabled = false // 禁用右側Y軸

                legend.isEnabled = false // 禁用圖例
            }
        },
        update = { chart ->
            if (pricePoints.isNotEmpty()) {
                // 將價格數據轉換為 MPAndroidChart 的 Entry 列表
                val entries = pricePoints.mapIndexed { index, point ->
                    Entry(index.toFloat(), point.price.toFloat())
                }

                // 創建 LineDataSet
                val dataSet = LineDataSet(entries, "PAXG Price").apply {
                    color = Color.CYAN // 線條顏色
                    setCircleColor(Color.CYAN) // 數據點圓圈顏色
                    lineWidth = 2f // 線條寬度
                    circleRadius = 3f // 數據點圓圈半徑
                    setDrawCircleHole(false) // 不繪製數據點中間的洞
                    valueTextSize = 0f // 隱藏數據點上的數值標籤
                    setDrawFilled(true) // 填充線下區域
                    fillColor = Color.CYAN // 填充顏色
                    fillAlpha = 50 // 填充透明度
                    mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線模式
                }

                val lineData = LineData(dataSet)
                chart.data = lineData
                chart.invalidate() // 刷新圖表
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun BinanceTrackerAppPreview() {
    AiAgentTheme {
        // 提供預覽數據
        BinanceTrackerApp(viewModel = object : BinancePriceViewModel() {
            override val currentPrice = MutableStateFlow(2400.50)
            override val priceHistory = MutableStateFlow(
                listOf(
                    PricePoint(System.currentTimeMillis() - 23 * 3600 * 1000, 2350.0),
                    PricePoint(System.currentTimeMillis() - 22 * 3600 * 1000, 2360.5),
                    PricePoint(System.currentTimeMillis() - 21 * 3600 * 1000, 2370.2),
                    PricePoint(System.currentTimeMillis() - 20 * 3600 * 1000, 2380.1),
                    PricePoint(System.currentTimeMillis() - 19 * 3600 * 1000, 2390.0),
                    PricePoint(System.currentTimeMillis() - 18 * 3600 * 1000, 2385.5),
                    PricePoint(System.currentTimeMillis() - 17 * 3600 * 1000, 2395.2),
                    PricePoint(System.currentTimeMillis() - 16 * 3600 * 1000, 2405.1),
                    PricePoint(System.currentTimeMillis() - 15 * 3600 * 1000, 2410.0),
                    PricePoint(System.currentTimeMillis() - 14 * 3600 * 1000, 2400.5),
                    PricePoint(System.currentTimeMillis() - 13 * 3600 * 1000, 2390.2),
                    PricePoint(System.currentTimeMillis() - 12 * 3600 * 1000, 2400.1),
                    PricePoint(System.currentTimeMillis() - 11 * 3600 * 1000, 2410.0),
                    PricePoint(System.currentTimeMillis() - 10 * 3600 * 1000, 2420.5),
                    PricePoint(System.currentTimeMillis() - 9 * 3600 * 1000, 2430.2),
                    PricePoint(System.currentTimeMillis() - 8 * 3600 * 1000, 2440.1),
                    PricePoint(System.currentTimeMillis() - 7 * 3600 * 1000, 2450.0),
                    PricePoint(System.currentTimeMillis() - 6 * 3600 * 1000, 2445.5),
                    PricePoint(System.currentTimeMillis() - 5 * 3600 * 1000, 2455.2),
                    PricePoint(System.currentTimeMillis() - 4 * 3600 * 1000, 2465.1),
                    PricePoint(System.currentTimeMillis() - 3 * 3600 * 1000, 2475.0),
                    PricePoint(System.currentTimeMillis() - 2 * 3600 * 1000, 2480.5),
                    PricePoint(System.currentTimeMillis() - 1 * 3600 * 1000, 2490.2),
                    PricePoint(System.currentTimeMillis(), 2500.0)
                )
            )
        })
    }
}
```

---

**修復步驟總結：**

1.  **開啟您的 Android 專案。**
2.  **導航到 `app/build.gradle` 檔案** (通常在 Project 視窗的 `app` -> `build.gradle (Module :app)`)。
3.  **將其內容完全替換**為上面提供的 `app/build.gradle` 內容。
4.  **導航到 `app/src/main/java/com/example/aiagent/MainActivity.kt` 檔案。**
5.  **將其內容完全替換**為上面提供的 `MainActivity.kt` 內容。
6.  **同步 Gradle 專案**：您可能會在 Android Studio 頂部看到一個提示，要求您同步專案（"Sync Now"），或者您可以手動點擊 `File -> Sync Project with Gradle Files`。

完成這些步驟後，專案應該能夠成功編譯。如果您在 `MainActivity.kt` 中發現任何其他未解析的引用錯誤，那可能是因為您缺少相應的 `import` 語句，我已經在提供的 `MainActivity.kt` 中包含了所有必要的導入。