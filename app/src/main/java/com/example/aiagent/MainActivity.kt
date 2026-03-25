好的，我理解了問題的核心：`build.gradle` 依賴配置不正確，導致 GitHub Actions 編譯失敗，以及需要提供完整的 Kotlin `MainActivity.kt` 程式碼來實現 Binance PAXGUSDT 金價追蹤。

**核心問題點和修正方案：**

1.  **Gradle 依賴錯誤：**
    *   **MPAndroidChart:** 這是一個第三方庫，需要添加 `maven { url 'https://jitpack.io' }` 到 `settings.gradle` (或舊版 Gradle 的 project-level `build.gradle`)。
    *   **Kotlin Coroutines 和 Lifecycle KTX:** 為了方便異步操作和 UI 更新，將會引入 Coroutines 和 `lifecycleScope`，需要對應的依賴。
    *   **ViewBinding:** 推薦使用 ViewBinding 來替代 `findViewById`，需要啟用並添加相應依賴。
    *   **OkHttp 和 Gson:** 確認版本號並正確引入。
    *   **AndroidX Libraries:** 確保核心 AndroidX 庫的版本兼容。

2.  **`MainActivity.kt` 實現細節：**
    *   **網絡請求 (OkHttp):** 創建 `OkHttpClient` 實例，構造 `Request`，在 `Dispatchers.IO` 中執行。
    *   **JSON 解析 (Gson):** 創建 `Gson` 實例，定義對應 Binance API 響應的 `data class`。
        *   獲取即時價格 (`ticker/price`)。
        *   獲取 K 線數據 (`klines`)，需要解析 `List<List<Any>>` 並轉換為自定義的 `Candlestick` 對象。
    *   **UI 更新 (Coroutines & Lifecycle):** 使用 `lifecycleScope.launch` 啟動協程，在 `Dispatchers.Main` 中更新 UI。
    *   **LineChart 繪製 (MPAndroidChart):**
        *   初始化 `LineChart`，設置其屬性（描述、圖例、軸等）。
        *   將 K 線數據（這裡主要用 `closePrice`）轉換為 `Entry` 對象列表。
        *   創建 `LineDataSet` 並設定樣式。
        *   創建 `LineData` 並賦值給 `LineChart`。
        *   刷新圖表 (`invalidate()`) 並添加動畫。
        *   自定義 X 軸格式化器，以便將時間戳顯示為可讀的日期時間。
    *   **錯誤處理:** 簡單的 `try-catch` 和 `Toast` 提示。
    *   **Internet 權限:** 勿忘在 `AndroidManifest.xml` 中添加。

---

### 修正後的檔案

以下是修正後的 `build.gradle` (Module: app), `settings.gradle`, `AndroidManifest.xml` 和完整的 `MainActivity.kt`。

#### 1. `settings.gradle` (或舊版 Gradle 的 project-level `build.gradle`)

```gradle
// settings.gradle
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
        // 🚨 IMPORTANT: For MPAndroidChart
        maven { url 'https://jitpack.io' }
    }
}
rootProject.name = "BinancePAXGTracker"
include ':app'
```

#### 2. `build.gradle` (Module: app)

```gradle
// app/build.gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.binancepaxgtracker' // 替換為你的 package name
    compileSdk 34 // 使用最新的 compileSdk

    defaultConfig {
        applicationId "com.example.binancepaxgtracker"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
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
    // 啟用 View Binding
    buildFeatures {
        viewBinding true
    }
}

dependencies {

    // AndroidX Core & UI
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.10.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // 🚀 OkHttp for Networking
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    // 🚀 Gson for JSON Parsing
    implementation 'com.google.code.gson:gson:2.10.1'

    // 🚀 MPAndroidChart for Line Chart
    // 確保已在 settings.gradle 中添加 jitpack.io
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // 🚀 Kotlin Coroutines for Asynchronous Operations
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // 🚀 Android Lifecycle KTX (for lifecycleScope)
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    implementation 'androidx.activity:activity-ktx:1.8.1' // For component activity extensions like lifecycleScope

    // Test dependencies
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}

```

#### 3. `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 🚀 請求網絡權限 -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.BinancePAXGTracker"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

#### 4. `activity_main.xml` (佈局檔案)

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="PAXG/USDT 即時價格"
        android:textSize="24sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/tvCurrentPrice"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:textSize="36sp"
        android:textStyle="bold"
        tools:text="PAXG: $2000.00"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/tvTitle" />

    <TextView
        android:id="@+id/tvChartTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="32dp"
        android:text="PAXG/USDT 24小時走勢"
        android:textSize="20sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/tvCurrentPrice" />

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
        app:layout_constraintTop_toBottomOf="@+id/tvChartTitle" />

    <ProgressBar
        android:id="@+id/progressBar"
        style="?android:attr/progressBarStyle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

#### 5. `MainActivity.kt`

```kotlin
package com.example.binancepaxgtracker

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.binancepaxgtracker.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 數據模型定義
// 1. 即時價格數據模型
data class TickerPrice(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("price") val price: String // 價格通常是字符串，以便處理精度
)

// 2. K 線數據模型 (來自 /api/v3/klines 的解析後數據)
data class Candlestick(
    val openTime: Long,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val volume: Double,
    val closeTime: Long
    // ... 其他 K 線數據字段如果需要，但對於折線圖，closePrice 和 openTime 足夠
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    private val priceFormat = DecimalFormat("#,##0.00") // 格式化價格顯示

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLineChart()
        fetchPAXGData()
    }

    private fun setupLineChart() {
        binding.lineChart.apply {
            description.isEnabled = false // 不顯示描述
            setTouchEnabled(true) // 允許觸摸交互
            isDragEnabled = true // 允許拖動
            setScaleEnabled(true) // 允許縮放
            setPinchZoom(true) // 允許雙指縮放

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM // X軸位置在底部
                setDrawGridLines(false) // 不繪製X軸網格線
                textColor = Color.BLACK
                valueFormatter = TimestampAxisFormatter() // 自定義X軸標籤格式
                labelRotationAngle = -45f // 標籤旋轉，避免重疊
                setLabelCount(4, true) // 顯示大約4個標籤，強制精確間隔
            }

            axisLeft.apply {
                setDrawGridLines(true) // 繪製左Y軸網格線
                textColor = Color.BLACK
                gridColor = Color.LTGRAY
            }

            axisRight.isEnabled = false // 不顯示右Y軸

            legend.apply {
                form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
                textColor = Color.BLACK
                textSize = 12f
            }
        }
    }

    private fun fetchPAXGData() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                // 在 IO 協程中執行網絡請求
                val (currentPrice, historicalData) = withContext(Dispatchers.IO) {
                    val price = getCurrentPAXGPrice()
                    val historical = getHistoricalPAXGData()
                    Pair(price, historical)
                }

                // 回到主線程更新 UI
                withContext(Dispatchers.Main) {
                    currentPrice?.let { updateCurrentPriceUI(it) } ?: run {
                        Toast.makeText(this@MainActivity, "無法獲取即時價格", Toast.LENGTH_SHORT).show()
                    }

                    historicalData?.let { updateChartUI(it) } ?: run {
                        Toast.makeText(this@MainActivity, "無法獲取歷史數據", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "獲取數據失敗: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "獲取數據失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun getCurrentPAXGPrice(): TickerPrice? {
        val url = "https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT"
        val request = Request.Builder().url(url).build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { jsonString ->
                    gson.fromJson(jsonString, TickerPrice::class.java)
                }
            } else {
                Log.e("MainActivity", "即時價格請求失敗: ${response.code} ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "即時價格請求異常: ${e.message}", e)
            null
        }
    }

    private suspend fun getHistoricalPAXGData(): List<Candlestick>? {
        // 獲取過去 24 小時的每小時 K 線數據
        val url = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24"
        val request = Request.Builder().url(url).build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { jsonString ->
                    // Binance KLines API 返回的是一個列表的列表
                    // e.g., [[1499040000000,"0.00000100","0.00001000",...]]
                    val type = object : TypeToken<List<List<Any>>>() {}.type
                    val rawKlines: List<List<Any>> = gson.fromJson(jsonString, type)

                    rawKlines.mapNotNull { rawKline ->
                        try {
                            // 根據 Binance KLines API 的順序解析字段
                            // [
                            //   0: Open time
                            //   1: Open price
                            //   2: High price
                            //   3: Low price
                            //   4: Close price
                            //   5: Volume
                            //   6: Close time
                            //   ...
                            // ]
                            Candlestick(
                                openTime = (rawKline[0] as Double).toLong(), // 可能會返回 Double
                                openPrice = (rawKline[1] as String).toDouble(),
                                highPrice = (rawKline[2] as String).toDouble(),
                                lowPrice = (rawKline[3] as String).toDouble(),
                                closePrice = (rawKline[4] as String).toDouble(),
                                volume = (rawKline[5] as String).toDouble(),
                                closeTime = (rawKline[6] as Double).toLong() // 可能會返回 Double
                            )
                        } catch (e: Exception) {
                            Log.e("MainActivity", "解析單條 K 線數據失敗: ${e.message}", e)
                            null
                        }
                    }
                }
            } else {
                Log.e("MainActivity", "歷史數據請求失敗: ${response.code} ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "歷史數據請求異常: ${e.message}", e)
            null
        }
    }

    private fun updateCurrentPriceUI(tickerPrice: TickerPrice) {
        val formattedPrice = priceFormat.format(tickerPrice.price.toDouble())
        binding.tvCurrentPrice.text = "${tickerPrice.symbol}: $${formattedPrice}"
    }

    private fun updateChartUI(data: List<Candlestick>) {
        val entries = data.mapIndexed { index, candlestick ->
            Entry(index.toFloat(), candlestick.closePrice.toFloat()) // X軸是索引，Y軸是收盤價
        }

        val dataSet = LineDataSet(entries, "PAXG/USDT 收盤價").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            circleRadius = 3f
            setDrawCircleHole(false)
            lineWidth = 2f
            valueTextSize = 0f // 不在圖表上顯示數值
            mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線
            setDrawFilled(true) // 填充區域
            fillColor = Color.parseColor("#80ADD8E6") // 淺藍色填充，透明度 50%
            fillAlpha = 100 // 填充透明度 (0-255)
        }

        val lineData = LineData(dataSet)
        binding.lineChart.data = lineData

        // 設置 X 軸為時間戳
        binding.lineChart.xAxis.valueFormatter = TimestampAxisFormatter(data.map { it.openTime })
        binding.lineChart.xAxis.setLabelCount(data.size / 4, true) // 根據數據量調整 X 軸標籤數量

        binding.lineChart.invalidate() // 刷新圖表
        binding.lineChart.animateX(1000) // X軸動畫
    }

    // 自定義 X 軸時間戳格式化器
    inner class TimestampAxisFormatter(private val timestamps: List<Long> = emptyList()) : ValueFormatter() {
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            val index = value.toInt()
            if (index >= 0 && index < timestamps.size) {
                return dateFormat.format(Date(timestamps[index]))
            }
            return ""
        }
    }
}
```

---

### 使用說明和測試

1.  **創建新項目：** 在 Android Studio 中創建一個新的 "Empty Activity" 專案。
2.  **更新 `settings.gradle`：** 將上述 `settings.gradle` 的內容替換掉你專案中的同名檔案。
3.  **更新 `build.gradle (Module: app)`：** 將上述 `build.gradle` 的內容替換掉你專案中 `app` 模組的同名檔案。**注意：** 確保 `namespace` 和 `applicationId` 與你的專案設定一致。
4.  **更新 `AndroidManifest.xml`：** 添加 `uses-permission android:name="android.permission.INTERNET"`。
5.  **創建 `activity_main.xml`：** 將上述 `activity_main.xml` 的內容複製到 `res/layout/activity_main.xml`。
6.  **更新 `MainActivity.kt`：** 將上述 `MainActivity.kt` 的內容替換掉你專案中的同名檔案。
7.  **同步 Gradle：** 點擊 Android Studio 右上角的 "Sync Project with Gradle Files" 按鈕。
8.  **運行應用程式：** 在模擬器或真實設備上運行應用程式。

應用程式啟動後，應該會顯示 PAXG/USDT 的即時價格，並繪製過去 24 小時的價格走勢圖。如果網絡請求失敗，會有 Toast 提示。