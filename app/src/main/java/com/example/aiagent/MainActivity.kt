您好！根據您提供的錯誤日誌，問題非常明確：`implementation`、`debugImplementation`、`repositoriesMode`、`repositories` 等關鍵字是 **Gradle 建置系統**的配置語法，它們應該被放置在 `build.gradle.kts` 或 `settings.gradle.kts` 這些 Gradle 設定檔中，而不是在 `MainActivity.kt` 這樣的 Kotlin 應用程式邏輯程式碼中。

您看到的 `Unresolved reference` 錯誤，正是因為 Kotlin 編譯器不認識這些 Gradle 語法，所以無法解析它們。

要修復此問題，我們需要做的是：
1.  從 `MainActivity.kt` 中移除所有錯誤的 Gradle 配置語法。
2.  在正確的 Gradle 設定檔 (`app/build.gradle.kts` 和 `settings.gradle.kts`) 中添加必要的依賴庫和倉庫配置。
3.  提供完整的 `MainActivity.kt` 程式碼，實現金價追蹤的邏輯（抓取數據、解析、顯示、繪圖）。
4.  確保 `AndroidManifest.xml` 包含網路權限。

以下是詳細的修復步驟和程式碼：

---

### 1. 錯誤分析與修正方向

錯誤日誌顯示 `MainActivity.kt` 的第 385 行到 419 行出現大量 `Unresolved reference` 錯誤，這些錯誤指向 `implementation`、`debugImplementation`、`repositoriesMode`、`RepositoriesMode`、`repositories`、`google`、`mavenCentral`、`maven`、`url` 等。

這清楚表明這些行是嘗試在 Kotlin 程式碼中直接配置 Gradle 依賴和倉庫。這是錯誤的做法。`MainActivity.kt` 應該只包含 Android 應用程式的 Kotlin 程式碼。

**修正方法**：將這些 Gradle 語法從 `MainActivity.kt` 中刪除，並將其正確地放置到 `app/build.gradle.kts`（用於依賴庫）和 `settings.gradle.kts`（用於倉庫）中。

---

### 2. `MainActivity.kt` 的完整修正程式碼

這個程式碼將包含實現金價追蹤（PAXG/USDT）的邏輯：
*   使用 OkHttp 抓取 Binance API 數據。
*   使用 Gson 解析 JSON 響應。
*   在 Compose UI 中顯示當前價格。
*   使用 MPAndroidChart 繪製 24 小時價格走勢圖。

```kotlin
package com.example.aiagent

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
import androidx.compose.ui.viewinterop.AndroidView // 用于在Compose中嵌入传统View

import com.example.aiagent.ui.theme.AiAgentTheme

import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.animation.Easing

import okhttp3.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// =====================================
// Data Models for Binance API Responses
// =====================================

// For https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT
data class TickerPriceResponse(
    val symbol: String,
    val price: String
)

// For https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24
// Binance Klines API returns an array of arrays. Each inner array is a candlestick:
// [
//   [
//     1499040000000,      // Open time (milliseconds)
//     "0.01634790",       // Open price
//     "0.80000000",       // High price
//     "0.01575800",       // Low price
//     "0.01577100",       // Close price
//     "148976.10700000",  // Volume
//     1499644799999,      // Close time (milliseconds)
//     "2434.19023972",    // Quote asset volume
//     308,                // Number of trades
//     "1756.87400000",    // Taker buy base asset volume
//     "28.46694368",      // Taker buy quote asset volume
//     "1792.00000000"     // Ignore
//   ]
// ]
// We need to parse this raw array into a structured data class.
data class KlineData(
    val openTime: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String,
    val ignore: String
) {
    companion object {
        // Helper function to parse the raw List<Any> from Gson into KlineData
        fun fromJsonArray(jsonArray: List<Any>): KlineData {
            return KlineData(
                openTime = (jsonArray[0] as Double).toLong(), // Gson parses numbers as Double by default
                open = jsonArray[1] as String,
                high = jsonArray[2] as String,
                low = jsonArray[3] as String,
                close = jsonArray[4] as String,
                volume = jsonArray[5] as String,
                closeTime = (jsonArray[6] as Double).toLong(),
                quoteAssetVolume = jsonArray[7] as String,
                numberOfTrades = (jsonArray[8] as Double).toInt(),
                takerBuyBaseAssetVolume = jsonArray[9] as String,
                takerBuyQuoteAssetVolume = jsonArray[10] as String,
                ignore = jsonArray[11] as String
            )
        }
    }
}

// =====================================
// MainActivity - Entry point of the app
// =====================================

class MainActivity : ComponentActivity() {

    // OkHttp client for network requests
    private val client = OkHttpClient()
    // Gson for JSON parsing
    private val gson = Gson()
    // Coroutine scope for background operations (fetching data)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // State holders for Compose UI, updated on the main thread
    private val _currentPrice = mutableStateOf("Loading...")
    // Using mutableStateOf for chart entries, so Compose recomposes when data changes
    private val _chartEntries = mutableStateOf<List<Entry>>(emptyList())
    // Store kline data to use timestamps for X-axis labels
    private val _klineTimestamps = mutableStateOf<List<Long>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceTrackerScreen(
                        currentPrice = _currentPrice.value,
                        chartEntries = _chartEntries.value,
                        klineTimestamps = _klineTimestamps.value
                    )
                }
            }
        }

        // Fetch data when the activity is created
        fetchPriceData()
        fetchKlineData()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutines when the activity is destroyed to prevent leaks
        coroutineScope.cancel()
    }

    // =====================================
    // Network Data Fetching Functions
    // =====================================

    private fun fetchPriceData() {
        coroutineScope.launch {
            val request = Request.Builder()
                .url("https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("MainActivity", "Failed to fetch current price data: ${e.message}", e)
                    runOnUiThread {
                        _currentPrice.value = "Error: ${e.message}"
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string()
                            Log.e("MainActivity", "Unexpected code ${response.code} for price data. Body: $errorBody")
                            runOnUiThread {
                                _currentPrice.value = "Error: ${response.code}"
                            }
                            return
                        }

                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            try {
                                val ticker = gson.fromJson(responseBody, TickerPriceResponse::class.java)
                                runOnUiThread {
                                    // Format to 2 decimal places for price
                                    val formattedPrice = String.format(Locale.US, "%.2f", ticker.price.toDouble())
                                    _currentPrice.value = "$formattedPrice USDT"
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error parsing current price data: ${e.message}", e)
                                runOnUiThread {
                                    _currentPrice.value = "Error parsing price"
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    private fun fetchKlineData() {
        coroutineScope.launch {
            // Fetch 24 hourly klines for a 24-hour chart
            val request = Request.Builder()
                .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("MainActivity", "Failed to fetch kline data: ${e.message}", e)
                    runOnUiThread {
                        _chartEntries.value = emptyList() // Clear chart on error
                        _klineTimestamps.value = emptyList()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string()
                            Log.e("MainActivity", "Unexpected code ${response.code} for kline data. Body: $errorBody")
                            runOnUiThread {
                                _chartEntries.value = emptyList()
                                _klineTimestamps.value = emptyList()
                            }
                            return
                        }

                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            try {
                                // Binance klines response is a List<List<Any>>, use TypeToken for generic parsing
                                val type = com.google.gson.reflect.TypeToken.getParameterized(
                                    List::class.java,
                                    List::class.java,
                                    Any::class.java // Inner list elements can be Double or String
                                ).type
                                val klineRawData: List<List<Any>> = gson.fromJson(responseBody, type)

                                val klineDataList = klineRawData.map { KlineData.fromJsonArray(it) }

                                val entries = mutableListOf<Entry>()
                                val timestamps = mutableListOf<Long>()

                                klineDataList.forEachIndexed { index, kline ->
                                    // Use index as X-value for evenly spaced hourly data
                                    // MPAndroidChart expects float for X and Y
                                    entries.add(Entry(index.toFloat(), kline.close.toFloat()))
                                    timestamps.add(kline.openTime) // Store open time for X-axis labels
                                }

                                runOnUiThread {
                                    _chartEntries.value = entries
                                    _klineTimestamps.value = timestamps
                                }

                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error parsing kline data: ${e.message}", e)
                                runOnUiThread {
                                    _chartEntries.value = emptyList()
                                    _klineTimestamps.value = emptyList()
                                }
                            }
                        }
                    }
                }
            })
        }
    }
}

// =====================================
// Compose UI for Gold Price Tracker
// =====================================

@Composable
fun GoldPriceTrackerScreen(currentPrice: String, chartEntries: List<Entry>, klineTimestamps: List<Long>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "PAXG/USDT (Gold) Price",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Current Price: $currentPrice",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // AndroidView allows embedding traditional Android Views into Compose UI
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            factory = { context ->
                // Initialize the LineChart
                LineChart(context).apply {
                    description.isEnabled = false // No description text
                    setTouchEnabled(true) // Enable touch gestures
                    isDragEnabled = true // Enable dragging
                    setScaleEnabled(true) // Enable scaling
                    setPinchZoom(true) // Enable pinch zoom
                    setDrawGridBackground(false) // Don't draw a background grid
                    setBackgroundColor(android.graphics.Color.WHITE) // Set background color

                    // Customize X-axis
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM // X-axis at the bottom
                        setDrawGridLines(false) // No vertical grid lines
                        setDrawAxisLine(true)
                        granularity = 1f // Minimum interval between labels is 1 unit
                        labelRotationAngle = -45f // Rotate labels to prevent overlap
                        valueFormatter = object : ValueFormatter() {
                            private val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                            override fun getFormattedValue(value: Float): String {
                                // Map the float index back to the actual timestamp
                                val index = value.roundToInt()
                                return if (index >= 0 && index < klineTimestamps.size) {
                                    format.format(Date(klineTimestamps[index]))
                                } else {
                                    ""
                                }
                            }
                        }
                    }

                    // Customize Left Y-axis
                    axisLeft.apply {
                        setDrawGridLines(true) // Horizontal grid lines
                        setDrawAxisLine(true)
                        setDrawZeroLine(false)
                        // Customize value format for Y-axis (e.g., to 2 decimal places)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return String.format(Locale.US, "%.2f", value)
                            }
                        }
                    }

                    // Customize Right Y-axis (disable)
                    axisRight.isEnabled = false

                    // Add some animation for a smoother look
                    animateX(1000, Easing.EaseInOutQuart)
                }
            },
            update = { chart ->
                // Update the chart data whenever `chartEntries` changes
                if (chartEntries.isNotEmpty()) {
                    val dataSet = LineDataSet(chartEntries, "PAXG/USDT Price (24h)").apply {
                        color = android.graphics.Color.parseColor("#007bff") // A nice blue color
                        valueTextColor = android.graphics.Color.BLACK
                        setDrawValues(false) // Don't draw individual value numbers on the line
                        setDrawCircles(false) // Don't draw circles on data points
                        lineWidth = 2f
                        mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve line
                        fillAlpha = 65
                        fillColor = android.graphics.Color.parseColor("#007bff") // Fill color
                        setDrawFilled(true) // Draw shaded area below the line
                    }
                    val lineData = LineData(dataSet)
                    chart.data = lineData
                    chart.invalidate() // Refresh chart view
                } else {
                    chart.clear() // Clear chart if no data
                    chart.setNoDataText("Loading chart data or no data available...")
                    chart.invalidate()
                }
            }
        )
    }
}

// =====================================
// Preview Function for Compose (Optional)
// =====================================

@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerScreenPreview() {
    AiAgentTheme {
        GoldPriceTrackerScreen(
            currentPrice = "2350.78 USDT",
            // Example data for preview
            chartEntries = listOf(
                Entry(0f, 2300f), Entry(1f, 2310f), Entry(2f, 2305f),
                Entry(3f, 2320f), Entry(4f, 2315f), Entry(5f, 2330f),
                Entry(6f, 2325f), Entry(7f, 2340f), Entry(8f, 2335f),
                Entry(9f, 2350f), Entry(10f, 2345f), Entry(11f, 2360f),
                Entry(12f, 2355f), Entry(13f, 2370f), Entry(14f, 2365f),
                Entry(15f, 2380f), Entry(16f, 2375f), Entry(17f, 2390f),
                Entry(18f, 2385f), Entry(19f, 2400f), Entry(20f, 2395f),
                Entry(21f, 2410f), Entry(22f, 2405f), Entry(23f, 2420f)
            ),
            klineTimestamps = List(24) { index ->
                System.currentTimeMillis() - (23 - index) * 60 * 60 * 1000L
            }
        )
    }
}
```

---

### 3. `app/build.gradle.kts` 修正程式碼

這個檔案負責應用程式模組的依賴庫配置。請確保您的 `app/build.gradle.kts` 包含以下內容，特別是在 `dependencies { ... }` 區塊中加入 OkHttp、Gson 和 MPAndroidChart：

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.aiagent" // 確保命名空間正確
    compileSdk = 34 // 或您專案中使用的最新SDK版本

    defaultConfig {
        applicationId = "com.example.aiagent"
        minSdk = 24 // 最低SDK版本
        targetSdk = 34 // 或您專案中使用的最新SDK版本
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true // 啟用 Jetpack Compose
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // 請檢查您Android Studio建議的最新穩定版本
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX & Compose 核心依賴
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00")) // 請檢查最新穩定版本
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // OkHttp for networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // 推薦使用最新穩定版本

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1") // 推薦使用最新穩定版本

    // MPAndroidChart for line chart
    // MPAndroidChart usually comes from Jitpack.io, ensure it's in settings.gradle.kts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

### 4. `settings.gradle.kts` 修正程式碼

這個檔案負責整個專案的 Gradle 設置，包括定義 Gradle 插件和依賴庫的來源倉庫。MPAndroidChart 通常從 Jitpack 獲取，所以需要在這裡添加 Jitpack 倉庫。

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 添加 Jitpack 倉庫，MPAndroidChart 需要此倉庫
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ai-agent-android" // 您的專案名稱
include(":app")
```

---

### 5. `AndroidManifest.xml` 更新

由於需要進行網路請求，請確保您的 `AndroidManifest.xml` 包含網路權限：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 這是必不可少的網路權限 -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AiAgent"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.AiAgent">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

### 修復步驟總結：

1.  **開啟 `app/src/main/java/com/example/aiagent/MainActivity.kt` 檔案。**
2.  **刪除** 第 385 行到 419 行（或者任何包含 `implementation`, `repositories` 等 Gradle 語法的行）。
3.  **將上面提供的「`MainActivity.kt` 的完整修正程式碼」複製並貼上，替換掉您現有的 `MainActivity.kt` 內容。**
4.  **開啟 `app/build.gradle.kts` 檔案。**
5.  **將上面提供的「`app/build.gradle.kts` 修正程式碼」複製並貼上，替換掉您現有的 `app/build.gradle.kts` 內容（或者至少將 `dependencies` 區塊更新為包含 OkHttp, Gson, MPAndroidChart 的內容）。**
6.  **開啟 `settings.gradle.kts` 檔案。**
7.  **將上面提供的「`settings.gradle.kts` 修正程式碼」複製並貼上，替換掉您現有的 `settings.gradle.kts` 內容（確保 `dependencyResolutionManagement` 區塊包含 `maven { url = uri("https://jitpack.io") }`）。**
8.  **開啟 `app/src/main/AndroidManifest.xml` 檔案。**
9.  **在 `<application>` 標籤上方（作為 `<manifest>` 的子元素）添加 `<uses-permission android:name="android.permission.INTERNET" />`。**
10. **同步 Gradle 專案。** 在 Android Studio 中，這通常會自動觸發，或者您可以手動點擊 "Sync Project with Gradle Files" 按鈕（通常在工具列上）。

完成這些步驟後，您的專案應該能夠正確編譯並執行，實現金價追蹤功能。