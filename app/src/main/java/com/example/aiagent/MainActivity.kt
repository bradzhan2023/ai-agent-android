好的，這是一個完整的 Android 應用程式範例，用於追蹤 Binance PAXGUSDT 的金價，並顯示 24 小時走勢圖。

我會確保以下幾點：
1.  **正確的 Gradle 依賴**: 引入 `OkHttp`, `Gson`, `MPAndroidChart`, `Kotlin Coroutines` 和 `Lifecycle KTX`。
2.  **`settings.gradle.kts` 配置**: 確保 `jitpack.io` 倉庫被包含，因為 `MPAndroidChart` 依賴它。
3.  **`AndroidManifest.xml`**: 添加 `INTERNET` 權限。
4.  **`MainActivity.kt`**: 包含數據抓取、解析、UI 更新和圖表繪製的完整邏輯。
5.  **Coroutines**: 使用 Kotlin Coroutines 進行非同步網路操作，並確保在主線程更新 UI。
6.  **View Binding**: 使用 View Binding 避免 `findViewById`。

---

### **修正步驟及程式碼**

首先，請確認你的 Android Studio 專案是基於 Kotlin 並使用了最新版本的 Gradle。

---

#### 1. `settings.gradle.kts` (專案根目錄)

這個文件通常用於設定專案級別的倉庫。`MPAndroidChart` 庫是透過 `JitPack` 提供的，所以我們需要添加 `jitpack.io` 倉庫。

```kotlin
// settings.gradle.kts

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // <-- **添加這一行**
    }
}
rootProject.name = "GoldTracker" // 你的專案名稱
include(":app")

```

---

#### 2. `build.gradle.kts` (app/build.gradle.kts)

這是應用模組的 Gradle 構建文件，我們將在這裡添加所有必要的依賴庫。

```kotlin
// app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourcompany.goldtracker" // 請將此替換為你的應用程式包名
    compileSdk = 34 // 或更高版本

    defaultConfig {
        applicationId = "com.yourcompany.goldtracker" // 請將此替換為你的應用程式包名
        minSdk = 24
        targetSdk = 34 // 或更高版本
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true // **啟用 View Binding**
    }
}

dependencies {

    // AndroidX 核心庫
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Kotlin Coroutines for asynchronous operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Lifecycle KTX for viewModelScope/lifecycleScope
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")


    // OkHttp for network requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // **最新穩定版本**

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1") // **最新穩定版本**

    // MPAndroidChart for LineChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // **確認版本**

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

```

---

#### 3. `AndroidManifest.xml`

需要添加網路權限。

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- **添加網路權限** -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.GoldTracker"
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

---

#### 4. `activity_main.xml` (佈局文件)

包含一個 `TextView` 顯示即時價格和一個 `LineChart` 顯示走勢圖。

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <TextView
        android:id="@+id/currentPriceTextView"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="16dp"
        android:layout_marginEnd="16dp"
        android:text="@string/loading_price"
        android:textSize="28sp"
        android:textStyle="bold"
        android:textAlignment="center"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/lastUpdatedTextView"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="16dp"
        android:text="@string/last_updated_time"
        android:textSize="14sp"
        android:textAlignment="center"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/currentPriceTextView" />

    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/priceLineChart"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginStart="8dp"
        android:layout_marginTop="16dp"
        android:layout_marginEnd="8dp"
        android:layout_marginBottom="8dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/lastUpdatedTextView" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

請在 `res/values/strings.xml` 中添加這些字符串資源：

```xml
<resources>
    <string name="app_name">GoldTracker</string>
    <string name="loading_price">Loading PAXG Price...</string>
    <string name="last_updated_time">Last updated: --:--</string>
    <string name="error_fetching_data">Error fetching data: %s</string>
    <string name="error_parsing_data">Error parsing data</string>
</resources>
```

---

#### 5. `MainActivity.kt` (核心邏輯)

這是主 Activity 文件，包含所有數據抓取、解析、圖表顯示的邏輯。

```kotlin
package com.yourcompany.goldtracker // 請替換為你的應用程式包名

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.yourcompany.goldtracker.databinding.ActivityMainBinding // 自動生成的 View Binding 類
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // View Binding 實例
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    // Binance API 相關常量
    private val BINANCE_API_URL = "https://api.binance.com/api/v3/klines"
    private val SYMBOL = "PAXGUSDT"
    private val INTERVAL = "1h" // 1 小時 K 線
    private val LIMIT = 24 // 獲取最近 24 條 K 線 (即 24 小時數據)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart() // 初始化圖表配置
        startPriceUpdates() // 啟動定時價格更新
    }

    private fun setupChart() {
        binding.priceLineChart.apply {
            description.isEnabled = false // 不顯示描述文本
            setTouchEnabled(true) // 允許觸摸交互
            isDragEnabled = true // 允許拖動
            setScaleEnabled(true) // 允許縮放
            setPinchZoom(true) // 允許兩指縮放

            setDrawGridBackground(false) // 不繪製網格背景

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM // X 軸位於底部
                setDrawGridLines(false) // 不繪製 X 軸網格線
                setDrawAxisLine(true) // 繪製 X 軸線
                textColor = Color.WHITE // 文本顏色
                valueFormatter = object : ValueFormatter() {
                    private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        // value 這裡我們會用 K 線的時間戳 (毫秒)
                        // 因為 Entry 的 x 是 float，所以需要轉換，這裡我們直接用 index 0-23
                        // 實際上應該傳入時間戳，然後格式化
                        // 為了簡單，我們假設 value 是 K 線的 index
                        // 更嚴謹的做法是將時間戳作為 Entry 的 X 值，並在這裡轉換
                        // 但 Binance API 返回的是時間戳，轉換成 Float 可能會有精度問題
                        // 所以這裡我們讓 Chart 處理 X 軸的索引，然後在數據中傳入實際時間戳
                        // 這裡我們將在 updateChart 中處理時間戳顯示
                        return "" // 暫時不顯示 X 軸標籤，我們會在數據載入後動態更新 ValueFormatter
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true) // 繪製 Y 軸網格線
                textColor = Color.WHITE
                gridColor = Color.GRAY // 網格線顏色
            }
            axisRight.apply {
                isEnabled = false // 禁用右側 Y 軸
            }

            legend.isEnabled = false // 不顯示圖例
            animateX(1500) // X 軸動畫
            setBackgroundColor(Color.BLACK) // 圖表背景顏色
        }
    }

    private fun startPriceUpdates() {
        // 使用 lifecycleScope 在 Activity 的生命週期內啟動協程
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                fetchPriceData()
                delay(60 * 1000) // 每 1 分鐘更新一次
            }
        }
    }

    private suspend fun fetchPriceData() {
        val url = "$BINANCE_API_URL?symbol=$SYMBOL&interval=$INTERVAL&limit=$LIMIT"
        val request = Request.Builder().url(url).build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (response.isSuccessful) {
                    val jsonString = response.body?.string()
                    jsonString?.let {
                        parseAndDisplayData(it)
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, R.string.error_fetching_data, Toast.LENGTH_SHORT).show()
                            Log.e("GoldTracker", "Empty response body")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.error_fetching_data, response.code.toString()), Toast.LENGTH_SHORT).show()
                        Log.e("GoldTracker", "Binance API error: ${response.code} - ${response.message}")
                    }
                }
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, getString(R.string.error_fetching_data, e.message), Toast.LENGTH_SHORT).show()
                Log.e("GoldTracker", "Network error: ${e.message}", e)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, getString(R.string.error_fetching_data, e.message), Toast.LENGTH_SHORT).show()
                Log.e("GoldTracker", "Unexpected error: ${e.message}", e)
            }
        }
    }

    private suspend fun parseAndDisplayData(jsonString: String) {
        try {
            // Binance Klines API 返回的是一個 JSON 數組，每個元素又是一個數組 (包含多個字符串)
            // e.g., [[1499040000000,"0.01634790","0.01634790","0.01634790","0.01634790","0.00000000",1499644799999,"0.00000000",7,1499040000000,"0.00000000","0"]]
            val klines: JsonArray = gson.fromJson(jsonString, JsonArray::class.java)

            if (klines.size() == 0) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.error_parsing_data, Toast.LENGTH_SHORT).show()
                    Log.w("GoldTracker", "Received empty klines data.")
                }
                return
            }

            val entries = mutableListOf<Entry>()
            val timestamps = mutableListOf<Long>() // 存儲時間戳，用於 X 軸格式化

            // 取最後一個 K 線數據作為當前價格
            val latestKline = klines.last().asJsonArray
            val currentPrice = latestKline[4].asString.toFloat() // 第5個元素是 close price

            // 遍歷 K 線數據，創建圖表 Entry
            klines.forEachIndexed { index, jsonElement ->
                val klineArray = jsonElement.asJsonArray
                val openTime = klineArray[0].asLong // K 線開盤時間，Unix 毫秒
                val closePrice = klineArray[4].asString.toFloat() // K 線收盤價

                entries.add(Entry(index.toFloat(), closePrice))
                timestamps.add(openTime)
            }

            withContext(Dispatchers.Main) {
                // 更新價格和時間
                binding.currentPriceTextView.text = String.format(Locale.getDefault(), "PAXG/USDT: $%.2f", currentPrice)
                val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                binding.lastUpdatedTextView.text = getString(R.string.last_updated_time, currentTime)

                // 更新圖表
                updateChart(entries, timestamps)
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, R.string.error_parsing_data, Toast.LENGTH_SHORT).show()
                Log.e("GoldTracker", "Error parsing JSON: ${e.message}", e)
            }
        }
    }

    private fun updateChart(entries: List<Entry>, timestamps: List<Long>) {
        val dataSet = LineDataSet(entries, "PAXG/USDT Price").apply {
            color = Color.YELLOW // 線條顏色
            lineWidth = 2f // 線條寬度
            setDrawCircles(false) // 不繪製數據點圓圈
            setDrawValues(false) // 不繪製數據值文本
            mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線
            fillAlpha = 65 // 填充區域透明度
            fillColor = Color.YELLOW // 填充區域顏色
            setDrawFilled(true) // 繪製填充區域
        }

        val lineData = LineData(dataSet)
        binding.priceLineChart.data = lineData

        // 更新 X 軸格式化器以顯示時間
        binding.priceLineChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault() // 使用設備時區
            }

            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < timestamps.size) {
                    hourFormat.format(Date(timestamps[index]))
                } else {
                    ""
                }
            }
        }
        binding.priceLineChart.xAxis.granularity = 1f // 確保每個點都顯示標籤 (如果空間允許)
        binding.priceLineChart.setVisibleXRangeMaximum(LIMIT.toFloat()) // 最多顯示 24 個點

        binding.priceLineChart.invalidate() // 刷新圖表
        binding.priceLineChart.animateX(1000) // 動畫
    }
}
```

---

#### **總結與注意事項**

1.  **包名替換**: 確保將 `com.yourcompany.goldtracker` 替換為你實際的應用程式包名。
2.  **Gradle 同步**: 修改 `build.gradle.kts` 和 `settings.gradle.kts` 後，請點擊 Android Studio 右上角的 "Sync Now" 按鈕以同步 Gradle 專案。
3.  **網路連接**: 確保你的設備或模擬器有網路連接。
4.  **錯誤處理**: 程式碼中包含了基本的網路和 JSON 解析錯誤處理，並會透過 `Toast` 和 `Logcat` 顯示錯誤資訊。
5.  **UI 執行緒**: 所有 UI 更新都透過 `withContext(Dispatchers.Main)` 確保在主執行緒上執行，避免 `CalledFromWrongThreadException`。
6.  **K 線數據格式**: Binance 的 `klines` API 返回的是 `Array<Array<String>>` 結構的數據，因此我們使用 `JsonArray` 進行解析，然後手動提取所需的值。
    *   `klineArray[0]` = open time (long)
    *   `klineArray[4]` = close price (string)
7.  **X 軸時間顯示**: 為了讓 `MPAndroidChart` 的 X 軸能顯示正確的時間，我們使用了 `ValueFormatter`。它會根據 K 線的時間戳來格式化顯示。由於 `Entry` 的 `x` 值是 `Float`，我們用 K 線的索引作為 `x` 值，並在 `ValueFormatter` 中透過這個索引查找對應的時間戳。

現在，你的應用程式應該能夠成功編譯並執行，抓取並顯示 PAXG/USDT 的金價走勢了。