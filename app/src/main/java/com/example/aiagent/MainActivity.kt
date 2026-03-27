這些錯誤日誌清楚地表明，您的 `MainActivity.kt` 檔案的內容被錯誤地替換成了 Gradle 建置腳本的內容。`androidTestImplementation`、`debugImplementation`、`repositories` 等關鍵字都是 Gradle 用來定義依賴和倉庫的，它們不屬於 Kotlin 原始碼檔案。因此，編譯器無法理解這些語句，並報告了大量的 `Unresolved reference` 和 `Function declaration must have a name` 錯誤。

要解決這個問題，我們需要做兩件事：
1.  **徹底重寫 `MainActivity.kt` 檔案**，使其包含正確的 Android 活動邏輯、UI 程式碼以及資料抓取和圖表繪製邏輯。
2.  **確保 `app/build.gradle.kts` 和 `settings.gradle.kts` 檔案包含所有必要的依賴庫** (OkHttp, Gson, MPAndroidChart, Compose 等)。

以下是修復後的 `MainActivity.kt` 檔案內容，以及相應需要修改的 Gradle 檔案片段。

---

### **1. 修復 `MainActivity.kt`**

將 `/app/src/main/java/com/example/aiagent/MainActivity.kt` 的內容完全替換為以下程式碼：

```kotlin
package com.example.aiagent

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagent.ui.theme.AiAgentTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// --- Data Models for Binance Klines API ---
// Binance Klines API returns a list of lists.
// We parse the raw list into a more structured data class.
data class KlineData(
    val openTime: Long,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val closePrice: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String,
    val ignore: String
)

// Helper function to convert a raw JSON list entry to a KlineData object
fun parseKlineJson(jsonArray: List<Any>): KlineData {
    // Gson might parse numbers as Doubles by default, so we cast them appropriately.
    return KlineData(
        openTime = (jsonArray[0] as Double).toLong(),
        openPrice = jsonArray[1] as String,
        highPrice = jsonArray[2] as String,
        lowPrice = jsonArray[3] as String,
        closePrice = jsonArray[4] as String,
        volume = jsonArray[5] as String,
        closeTime = (jsonArray[6] as Double).toLong(),
        quoteAssetVolume = jsonArray[7] as String,
        numberOfTrades = (jsonArray[8] as Double).toInt(),
        takerBuyBaseAssetVolume = jsonArray[9] as String,
        takerBuyQuoteAssetVolume = jsonArray[10] as String,
        ignore = jsonArray[11] as String
    )
}

// --- ViewModel for fetching data ---
class GoldPriceViewModel : ViewModel() {
    private val client = OkHttpClient()
    private val gson = Gson()

    // State holders for UI, observed by Compose
    val currentPrice = mutableStateOf("Fetching PAXG/USDT...")
    val priceHistory = mutableStateOf<List<KlineData>>(emptyList())
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    init {
        // Fetch data when the ViewModel is initialized
        fetchGoldPriceData()
    }

    fun fetchGoldPriceData() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null // Clear previous errors
            withContext(Dispatchers.IO) {
                try {
                    // 1. Fetch current price (24hr ticker data)
                    val currentPriceUrl = "https://api.binance.com/api/v3/ticker/24hr?symbol=PAXGUSDT"
                    val currentPriceRequest = Request.Builder().url(currentPriceUrl).build()
                    val currentPriceResponse = client.newCall(currentPriceRequest).execute()

                    if (currentPriceResponse.isSuccessful) {
                        val responseBody = currentPriceResponse.body?.string()
                        val tickerData = gson.fromJson(responseBody, object : TypeToken<Map<String, Any>>() {}.type) as Map<String, Any>
                        val lastPrice = tickerData["lastPrice"] as String
                        withContext(Dispatchers.Main) {
                            currentPrice.value = "PAXG/USDT: $lastPrice"
                        }
                    } else {
                        throw IOException("Failed to fetch current price: ${currentPriceResponse.code} - ${currentPriceResponse.body?.string()}")
                    }

                    // 2. Fetch 24-hour historical data (klines/candlesticks)
                    // Using 5-minute interval for 24 hours: 24 hours * (60 mins / 5 mins) = 288 data points.
                    // This fits within Binance's typical API limit of 1000 items per request.
                    val klinesUrl = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=5m&limit=288"
                    val klinesRequest = Request.Builder().url(klinesUrl).build()
                    val klinesResponse = client.newCall(klinesRequest).execute()

                    if (klinesResponse.isSuccessful) {
                        val responseBody = klinesResponse.body?.string()
                        // Binance Klines API returns a List of Lists (e.g., [[timestamp, open, high, low, close, ...]])
                        val type = object : TypeToken<List<List<Any>>>() {}.type
                        val rawKlineList: List<List<Any>> = gson.fromJson(responseBody, type)

                        // Parse raw lists into our structured KlineData objects
                        val parsedKlineList = rawKlineList.map { parseKlineJson(it) }
                        withContext(Dispatchers.Main) {
                            priceHistory.value = parsedKlineList
                        }
                    } else {
                        throw IOException("Failed to fetch historical data: ${klinesResponse.code} - ${klinesResponse.body?.string()}")
                    }

                } catch (e: Exception) {
                    // Log the error for debugging
                    Log.e("GoldPriceViewModel", "Error fetching data: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        errorMessage.value = "Failed to load data: ${e.message}"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading.value = false
                    }
                }
            }
        }
    }
}

// --- Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                // A surface container using the 'background' color from the theme
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

// --- Composable UI for the Gold Price Screen ---
@Composable
fun GoldPriceScreen(viewModel: GoldPriceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val currentPrice by viewModel.currentPrice
    val priceHistory by viewModel.priceHistory
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PAXG/USDT Price Tracker",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Show loading indicator when data is being fetched
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            Text("Loading data...")
        }

        // Display error message if any
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Display current price
        Text(
            text = currentPrice,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Display the chart if historical data is available
        if (priceHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "24-Hour Price Trend (5-min interval)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            PriceLineChart(priceHistory = priceHistory)
        } else if (!isLoading && errorMessage == null) {
            // Only show this if not loading and no error, meaning no data was returned
            Text("No historical data available.", modifier = Modifier.padding(16.dp))
        }

        // Refresh Button
        Button(
            onClick = { viewModel.fetchGoldPriceData() },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Refresh Data")
        }
    }
}

// --- Composable for displaying the Line Chart using MPAndroidChart ---
@Composable
fun PriceLineChart(priceHistory: List<KlineData>) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color.White), // Provide a background for the chart view
        factory = {
            LineChart(it).apply {
                // Basic chart setup
                description.isEnabled = false // Disable chart description
                setTouchEnabled(true) // Enable touch gestures
                isDragEnabled = true // Enable dragging
                setScaleEnabled(true) // Enable zooming (pinch zoom)
                setPinchZoom(true) // Enable pinch zoom

                // X-axis configuration (time)
                xAxis.position = XAxis.XAxisPosition.BOTTOM // X-axis at the bottom
                xAxis.setDrawGridLines(false) // No vertical grid lines
                xAxis.axisMinimum = priceHistory.first().openTime.toFloat() // Set min time
                xAxis.axisMaximum = priceHistory.last().openTime.toFloat() // Set max time
                xAxis.valueFormatter = object : ValueFormatter() {
                    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        return dateFormat.format(Date(value.toLong())) // Format timestamp to HH:mm
                    }
                }
                xAxis.labelRotationAngle = -45f // Rotate X-axis labels for readability
                xAxis.setLabelCount(5, true) // Show approximately 5 labels, force interval

                // Y-axis configuration
                axisRight.isEnabled = false // Disable right Y-axis
                axisLeft.setDrawGridLines(true) // Enable horizontal grid lines
                axisLeft.enableGridDashedLine(10f, 10f, 0f) // Make grid lines dashed

                legend.isEnabled = false // No legend needed for a single line chart
            }
        },
        update = { chart ->
            if (priceHistory.isNotEmpty()) {
                // Convert KlineData to MPAndroidChart Entry objects
                val entries = priceHistory.map { kline ->
                    // X-value is timestamp, Y-value is close price
                    Entry(kline.openTime.toFloat(), kline.closePrice.toFloat())
                }

                // Create a LineDataSet
                val dataSet = LineDataSet(entries, "PAXG/USDT Price").apply {
                    color = android.graphics.Color.BLUE // Line color
                    setCircleColor(android.graphics.Color.BLUE) // Circle color for data points
                    setDrawValues(false) // Do not draw actual price values on the chart
                    lineWidth = 2f // Line thickness
                    circleRadius = 3f // Size of data point circles
                    setDrawCircleHole(false) // No hole in circles
                    mode = LineDataSet.Mode.LINEAR // Linear interpolation between points
                    // Alternatively, use LineDataSet.Mode.CUBIC_BEZIER for smooth curves
                }

                // Apply data to the chart
                val lineData = LineData(dataSet)
                chart.data = lineData

                // Update X-axis range and move view
                chart.xAxis.axisMinimum = priceHistory.first().openTime.toFloat()
                chart.xAxis.axisMaximum = priceHistory.last().openTime.toFloat()
                // Optionally set initial visible range and scroll to end
                chart.setVisibleXRangeMaximum(TimeUnit.HOURS.toMillis(4).toFloat()) // Show about 4 hours initially
                chart.moveViewToX(priceHistory.last().openTime.toFloat()) // Scroll to the latest data point

                chart.invalidate() // Refresh the chart view
            }
        }
    )
}

// --- Preview Composable for Android Studio ---
@Preview(showBackground = true)
@Composable
fun GoldPriceScreenPreview() {
    AiAgentTheme {
        // Provide dummy data for a meaningful preview
        val dummyViewModel = GoldPriceViewModel().apply {
            currentPrice.value = "PAXG/USDT: 2350.00"
            priceHistory.value = listOf(
                // Example data for a short trend (timestamps in milliseconds)
                KlineData(System.currentTimeMillis() - 3000000, "2300", "2310", "2290", "2305", "", 0, "", 0, "", "", ""),
                KlineData(System.currentTimeMillis() - 2400000, "2305", "2315", "2300", "2312", "", 0, "", 0, "", "", ""),
                KlineData(System.currentTimeMillis() - 1800000, "2312", "2320", "2310", "2318", "", 0, "", 0, "", "", ""),
                KlineData(System.currentTimeMillis() - 1200000, "2318", "2325", "2315", "2322", "", 0, "", 0, "", "", ""),
                KlineData(System.currentTimeMillis() - 600000, "2322", "2330", "2320", "2328", "", 0, "", 0, "", "", "")
            )
            isLoading.value = false
            errorMessage.value = null
        }
        GoldPriceScreen(dummyViewModel)
    }
}
```

---

### **2. 更新 Gradle 依賴**

這些依賴庫是為了讓 `MainActivity.kt` 中的程式碼能夠正常運作所必需的。

#### **A. 更新 `app/build.gradle.kts` (模組級別的 Gradle 檔案)**

在 `dependencies` 區塊中添加或確認以下依賴：

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.aiagent"
    compileSdk = 34 // 使用最新的穩定 API 等級
    defaultConfig {
        applicationId = "com.example.aiagent"
        minSdk = 24 // 支援 Android 7.0 及更高版本
        targetSdk = 34 // 使用最新的穩定 API 等級
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
        kotlinCompilerExtensionVersion = "1.5.1" // 請檢查您的 Android Studio 版本兼容的最新穩定版本
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
    implementation(platform("androidx.compose:compose-bom:2023.08.00")) // 請檢查最新的穩定 BOM 版本
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // ViewModel for Jetpack Compose (provides viewModel() composable)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // OkHttp for網路請求
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // 使用最新的穩定版本

    // Gson for JSON解析
    implementation("com.google.code.gson:gson:2.10.1") // 使用最新的穩定版本

    // MPAndroidChart for繪製圖表 (通常在 jitpack.io 上託管)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // v3.1.0 是常見版本，如果遇到問題可以嘗試其他版本或最新版本

    // 測試依賴 (保留現有)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

#### **B. 更新 `settings.gradle.kts` (專案級別的 Gradle 檔案)**

由於 `MPAndroidChart` 依賴通常託管在 `jitpack.io`，您需要確保在 `settings.gradle.kts` 中添加 `jitpack.io` 倉庫。

```kotlin
// settings.gradle.kts
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
        // 添加 JitPack 倉庫，用於MPAndroidChart
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "AiAgent"
include(":app")
```

---

**修復步驟總結：**

1.  開啟你的 Android Studio 專案。
2.  導航到 `app/src/main/java/com/example/aiagent/MainActivity.kt` 檔案，將其內容替換為上面提供的「修復 `MainActivity.kt`」部分的程式碼。
3.  導航到 `app/build.gradle.kts` 檔案，確保其 `dependencies` 區塊與上面提供的「更新 `app/build.gradle.kts`」部分相符。特別是添加 OkHttp, Gson 和 MPAndroidChart 的依賴。
4.  導航到專案根目錄下的 `settings.gradle.kts` 檔案，確保 `dependencyResolutionManagement` 區塊中包含 `maven { url = uri("https://jitpack.io") }`。
5.  同步您的 Gradle 專案（通常 Android Studio 會自動提示，或手動點擊 "Sync Project with Gradle Files" 按鈕）。

完成這些步驟後，所有 `Unresolved reference` 和 `Function declaration must have a name` 的錯誤都應該會消失，因為 `MainActivity.kt` 現在包含的是有效的 Kotlin 程式碼，並且所需的依賴庫也已正確配置。