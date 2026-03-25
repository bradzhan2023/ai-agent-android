好的，這個問題通常發生在 `build.gradle` 檔案中，缺少或引用了錯誤版本的函式庫。同時，由於網路請求和 JSON 解析涉及非同步操作，我們需要使用 Kotlin Coroutines 來管理這些操作，並在主執行緒上更新 UI。

以下是修正後的 `build.gradle` (Module: app) 配置、`settings.gradle` 配置、`AndroidManifest.xml` 以及完整的 `MainActivity.kt` 檔案。

---

### 1. `settings.gradle` (Project Settings)

**請確保你的 `settings.gradle` 檔案包含 JitPack 倉庫，因為 `MPAndroidChart` 函式庫通常從那裡獲取。**

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' } // <<< 確保有這一行
    }
}
rootProject.name = "GoldTrackerApp" // 你的專案名稱
include ':app'
```

---

### 2. `build.gradle` (Module: app)

**這是最重要的部分，確保所有依賴都正確且版本兼容。**

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android' // Kotlin plugin
}

android {
    namespace 'com.example.goldtrackerapp' // 替換成你的專案包名
    compileSdk 34 // 建議使用最新版本

    defaultConfig {
        applicationId 'com.example.goldtrackerapp' // 替換成你的專案包名
        minSdk 24 // 根據你的目標設備選擇
        targetSdk 34 // 建議使用最新版本
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

    // Kotlin Coroutines (用於非同步操作)
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

    // OkHttp (網路請求)
    implementation 'com.squareup.okhttp3:okhttp:4.12.0' // 建議使用最新穩定版

    // Gson (JSON 解析)
    implementation 'com.google.code.gson:gson:2.10.1' // 建議使用最新穩定版

    // MPAndroidChart (圖表庫)
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0' // 目前最常用穩定版

    // 測試相關
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}

```

---

### 3. `AndroidManifest.xml`

**確保你的應用程式有網路權限。**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 必須要有網路權限 -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.GoldTrackerApp"
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

### 4. `activity_main.xml` (Layout)

**定義 UI 佈局，包含一個 TextView 顯示價格和一個 LineChart 顯示走勢。**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <TextView
        android:id="@+id/priceTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Loading PAXGUSDT price..."
        android:textSize="24sp"
        android:textStyle="bold"
        android:padding="16dp"
        app:layout_constraintBottom_toTopOf="@+id/chart"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_chainStyle="packed" />

    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/chart"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginStart="8dp"
        android:layout_marginTop="16dp"
        android:layout_marginEnd="8dp"
        android:layout_marginBottom="8dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/priceTextView" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

### 5. `MainActivity.kt` (Kotlin Code)

**完整的 `MainActivity` 程式碼，包含了網路請求、JSON 解析和圖表繪製。**

```kotlin
package com.example.goldtrackerapp

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.goldtrackerapp.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // Binance API Endpoint for current price
    private val CURRENT_PRICE_URL = "https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT"
    // Binance API Endpoint for 24-hour klines (candlestick data)
    // interval=1h means 1-hour candles, limit=24 means 24 candles (24 hours)
    private val KLINES_URL = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fetchBinanceData()
    }

    private fun fetchBinanceData() {
        // 使用 lifecycleScope.launch 在背景執行緒 (IO) 執行網路請求
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. 抓取當前金價
                val currentPriceRequest = Request.Builder().url(CURRENT_PRICE_URL).build()
                val currentPriceResponse = client.newCall(currentPriceRequest).execute()

                if (currentPriceResponse.isSuccessful) {
                    val json = currentPriceResponse.body?.string()
                    val priceData = gson.fromJson(json, BinancePriceResponse::class.java)
                    val price = priceData.price.toFloatOrNull() // 轉換為浮點數

                    withContext(Dispatchers.Main) {
                        if (price != null) {
                            binding.priceTextView.text = "PAXG/USDT: $%.2f".format(price)
                        } else {
                            binding.priceTextView.text = "無法獲取價格"
                        }
                    }
                } else {
                    Log.e("BinanceData", "Current price request failed: ${currentPriceResponse.code}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "無法獲取當前價格", Toast.LENGTH_SHORT).show()
                    }
                }

                // 2. 抓取 24 小時 K 線數據
                val klinesRequest = Request.Builder().url(KLINES_URL).build()
                val klinesResponse = client.newCall(klinesRequest).execute()

                if (klinesResponse.isSuccessful) {
                    val json = klinesResponse.body?.string()
                    // Binance Klines API 返回的是 List<List<String>>
                    // [
                    //   [
                    //     1499040000000,      // Open time
                    //     "0.01634790",       // Open
                    //     "0.80000000",       // High
                    //     "0.01575600",       // Low
                    //     "0.01577000",       // Close (我們要用的)
                    //     "148976.10700000",  // Volume
                    //     1499644799999,      // Close time
                    //     "2434.19055334",    // Quote asset volume
                    //     308,                // Number of trades
                    //     "1756.87400000",    // Taker buy base asset volume
                    //     "28.46694368",      // Taker buy quote asset volume
                    //     "17928899.62484339" // Ignore
                    //   ]
                    // ]
                    val klinesList = gson.fromJson(json, Array<Array<Any>>::class.java)

                    val chartEntries = klinesList.mapNotNull { kline ->
                        val openTime = (kline[0] as Double).toLong() // Open time in milliseconds
                        val closePrice = (kline[4] as String).toFloatOrNull() // Close price

                        if (closePrice != null) {
                            // X 軸使用時間戳 (以毫秒為單位)，Y 軸使用價格
                            Entry(openTime.toFloat(), closePrice)
                        } else {
                            null
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (chartEntries.isNotEmpty()) {
                            setupChart(chartEntries)
                        } else {
                            Toast.makeText(this@MainActivity, "沒有足夠的圖表數據", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("BinanceData", "Klines request failed: ${klinesResponse.code}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "無法獲取圖表數據", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: IOException) {
                Log.e("BinanceData", "Network error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "網路錯誤: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("BinanceData", "Data parsing error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "數據處理錯誤: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupChart(entries: List<Entry>) {
        val dataSet = LineDataSet(entries, "PAXG/USDT 24小時走勢").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            valueTextColor = Color.BLACK
            valueTextSize = 0f // 不顯示每個點的數值
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false) // 不在圖表上繪製數據點的值
            mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線
            setDrawFilled(true) // 填充線下區域
            fillColor = Color.parseColor("#80ADD8E6") // 淺藍色半透明填充
        }

        val lineData = LineData(dataSet)
        binding.chart.apply {
            data = lineData
            description.isEnabled = false // 不顯示描述
            legend.isEnabled = false // 不顯示圖例
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setNoDataText("沒有可用的圖表數據")
            animateX(1500) // X軸動畫

            // X 軸設定
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = TimeUnit.HOURS.toMillis(3).toFloat() // 每3小時顯示一個標籤
                valueFormatter = DateAxisFormatter() // 自定義日期時間格式化器
                textColor = Color.BLACK
                setDrawGridLines(false) // 不繪製網格線
            }

            // 左 Y 軸設定
            axisLeft.apply {
                textColor = Color.BLACK
                setDrawGridLines(true) // 繪製網格線
            }

            // 右 Y 軸設定 (禁用)
            axisRight.isEnabled = false

            invalidate() // 刷新圖表
        }
    }

    // JSON 數據模型 for current price
    data class BinancePriceResponse(
        @SerializedName("symbol") val symbol: String,
        @SerializedName("price") val price: String
    )

    // 自定義 X 軸日期時間格式化器
    private class DateAxisFormatter : ValueFormatter() {
        private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault()) // 顯示小時:分鐘

        override fun getFormattedValue(value: Float): String {
            // value 是時間戳 (毫秒)，MPAndroidChart 的 Entry x 值是 float
            return mFormat.format(Date(value.toLong()))
        }
    }
}
```

---

### 如何在 Android Studio 中操作：

1.  **更新 `settings.gradle`：**
    *   在 Android Studio 的 Project Explorer (左側導航欄) 中，找到 `Gradle Scripts` -> `settings.gradle (Project Settings)`。
    *   確保 `maven { url 'https://jitpack.io' }` 存在於 `repositories` 區塊中。

2.  **更新 `build.gradle (Module: app)`：**
    *   在 Android Studio 的 Project Explorer 中，找到 `Gradle Scripts` -> `build.gradle (Module: app)`。
    *   將內容替換為上面提供的程式碼。
    *   `namespace` 和 `applicationId` 請替換為你專案的實際包名。
    *   **點擊 "Sync Now"** (通常在修改 `build.gradle` 後會自動彈出，或手動點擊工具列上的大象圖標)。這會讓 Gradle 下載並配置所有新的函式庫。

3.  **更新 `AndroidManifest.xml`：**
    *   在 Project Explorer 中，找到 `app` -> `manifests` -> `AndroidManifest.xml`。
    *   確保 `uses-permission android:name="android.permission.INTERNET"` 存在於 `<manifest>` 標籤內，但位於 `<application>` 標籤之外。

4.  **更新 `activity_main.xml`：**
    *   在 Project Explorer 中，找到 `app` -> `res` -> `layout` -> `activity_main.xml`。
    *   將內容替換為上面提供的程式碼。

5.  **替換 `MainActivity.kt`：**
    *   在 Project Explorer 中，找到 `app` -> `java` -> 你的包名 (例如 `com.example.goldtrackerapp`) -> `MainActivity.kt`。
    *   將內容替換為上面提供的程式碼。

6.  **執行應用程式：**
    *   在模擬器或實體設備上執行應用程式。它應該能夠成功編譯並顯示 PAXG/USDT 的當前價格和 24 小時走勢圖。

這個方案解決了 GitHub Actions 上編譯失敗的常見原因（Gradle 依賴問題），並提供了完整的 Kotlin 程式碼實現所有要求。