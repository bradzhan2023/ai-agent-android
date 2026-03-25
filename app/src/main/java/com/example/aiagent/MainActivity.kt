好的，針對您提供的 Android 任務和 GitHub Actions 編譯失敗的問題，最常見的原因是 `build.gradle` 配置不正確，尤其是針對第三方庫（如 OkHttp, Gson, MPAndroidChart）的引用。

我將提供一個完整的解決方案，確保所有庫都正確導入，並包含 `MainActivity.kt`、`activity_main.xml`、`AndroidManifest.xml` 以及 `build.gradle` 的正確配置。

### 問題分析與修正策略：

1.  **Gradle Dependencies (最重要的環節):**
    *   **OkHttp & Gson:** 確保使用最新穩定版並正確聲明 `implementation 'com.squareup.okhttp3:okhttp:...'` 和 `implementation 'com.google.code.gson:gson:...'`。
    *   **MPAndroidChart:** 這是一個常見的錯誤源。它通常需要額外的 Maven 倉庫配置 (`jitpack.io`)。
        *   需要在專案根目錄的 `settings.gradle` (或舊版 Gradle 的 `build.gradle` 頂層) 中添加 `maven { url 'https://jitpack.io' }`。
        *   然後在 `app/build.gradle` 中聲明 `implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'`。
    *   **Kotlin Coroutines:** 為了非同步網路請求，我們將使用 Kotlin Coroutines，這需要 `lifecycle-runtime-ktx` 和 `kotlinx-coroutines-android`。
2.  **AndroidManifest.xml:** 確保有 `INTERNET` 權限。
3.  **MainActivity.kt:**
    *   使用 OkHttp 進行網路請求。
    *   使用 Gson 解析 JSON。
    *   使用 `lifecycleScope` 和 `Dispatchers.IO` 執行背景操作，並在 `Dispatchers.Main` 更新 UI。
    *   初始化並配置 `MPAndroidChart` 來顯示 24 小時走勢。
4.  **activity_main.xml:** 佈局包含一個 `TextView` 顯示當前價格和一個 `LineChart`。

---

### 完整程式碼解決方案

#### 1. `settings.gradle` (專案根目錄)

**這是修正 MPAndroidChart 引用錯誤的關鍵一步。**

```gradle
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
        // !!! 修正點：添加 JitPack 倉庫以引用 MPAndroidChart
        maven { url 'https://jitpack.io' }
    }
}
rootProject.name = "GoldTracker" // 您的專案名稱
include ':app'
```

#### 2. `app/build.gradle` (模組級別)

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.goldtracker' // 替換為您的應用包名
    compileSdk 34

    defaultConfig {
        applicationId "com.example.goldtracker" // 替換為您的應用包名
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
    buildFeatures {
        viewBinding true // 啟用 ViewBinding 方便訪問視圖
    }
}

dependencies {
    // AndroidX 核心庫
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.10.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // Kotlin Coroutines for asynchronous operations
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2' // For lifecycleScope

    // !!! 修正點：OkHttp for networking
    implementation 'com.squareup.okhttp3:okhttp:4.11.0' // 使用最新穩定版

    // !!! 修正點：Gson for JSON parsing
    implementation 'com.google.code.gson:gson:2.10.1' // 使用最新穩定版

    // !!! 修正點：MPAndroidChart for charting
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0' // 確保版本號與 JitPack 兼容

    // Testing
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

    <!-- !!! 修正點：添加 INTERNET 權限 -->
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

#### 4. `activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp"
    tools:context=".MainActivity">

    <TextView
        android:id="@+id/priceLabel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="PAXGUSDT Current Price:"
        android:textSize="18sp"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/currentPriceTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:text="Loading..."
        android:textSize="24sp"
        android:textStyle="bold"
        app:layout_constraintBaseline_toBaselineOf="@id/priceLabel"
        app:layout_constraintStart_toEndOf="@id/priceLabel"
        tools:text="3000.00 USDT" />

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

    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/lineChart"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/currentPriceTextView" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

#### 5. `MainActivity.kt`

```kotlin
package com.example.goldtracker // 替換為您的應用包名

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private lateinit var currentPriceTextView: TextView
    private lateinit var lineChart: LineChart
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentPriceTextView = findViewById(R.id.currentPriceTextView)
        lineChart = findViewById(R.id.lineChart)
        progressBar = findViewById(R.id.progressBar)

        fetchData()
    }

    private fun fetchData() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentPrice = fetchCurrentPrice()
                val klineData = fetchKlineData()

                withContext(Dispatchers.Main) {
                    currentPriceTextView.text = "${currentPrice?.price} USDT"
                    if (klineData != null) {
                        setupChart(klineData)
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to load chart data", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    currentPriceTextView.text = "Error"
                }
                e.printStackTrace()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    currentPriceTextView.text = "Error"
                }
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun fetchCurrentPrice(): TickerPriceResponse? {
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response}")

            val json = response.body?.string() ?: return null
            return gson.fromJson(json, TickerPriceResponse::class.java)
        }
    }

    // Binance klines API returns a list of lists:
    // [
    //   [
    //     1499040000000,      // Open time
    //     "0.01634790",       // Open
    //     "0.80000000",       // High
    //     "0.01575800",       // Low
    //     "0.01577100",       // Close
    //     "148976.10704000",  // Volume
    //     1499644799999,      // Close time
    //     "2434.19013972",    // Quote asset volume
    //     308,                // Number of trades
    //     "1756.87402397",    // Taker buy base asset volume
    //     "28.46694368",      // Taker buy quote asset volume
    //     "0"                 // Ignore.
    //   ]
    // ]
    // We are interested in Close time (index 6) and Close price (index 4)
    private fun fetchKlineData(): List<Entry>? {
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24") // Last 24 hours, 1-hour interval
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response}")

            val json = response.body?.string() ?: return null
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java,
                List::class.java
            ).type
            val klines: List<List<String>> = gson.fromJson(json, type)

            // Convert kline data to MPAndroidChart Entry objects
            val entries = mutableListOf<Entry>()
            klines.forEachIndexed { index, kline ->
                // x-axis is index (representing 1-hour intervals), y-axis is close price
                val closePrice = kline[4].toFloat() // Close price is at index 4
                entries.add(Entry(index.toFloat(), closePrice))
            }
            return entries
        }
    }

    private fun setupChart(entries: List<Entry>) {
        val dataSet = LineDataSet(entries, "PAXGUSDT 24h Price")
        dataSet.color = Color.rgb(255, 165, 0) // Orange color
        dataSet.valueTextColor = Color.BLACK
        dataSet.setDrawCircles(false) // Don't draw circles on data points
        dataSet.lineWidth = 2f
        dataSet.valueTextSize = 0f // Hide value text on chart

        // Customize Y-axis (left)
        val leftAxis = lineChart.axisLeft
        leftAxis.textColor = Color.BLACK
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E0E0E0") // Light grey grid lines

        // Customize X-axis
        val xAxis = lineChart.xAxis
        xAxis.textColor = Color.BLACK
        xAxis.setDrawGridLines(false) // No vertical grid lines
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM // X-axis at the bottom
        xAxis.setLabelCount(5, true) // Show approx 5 labels, force interval
        // You can add a custom ValueFormatter here to show actual times or relative hours
        // For simplicity, we'll keep the default index labels.

        // Right Y-axis
        lineChart.axisRight.isEnabled = false // Disable right Y-axis

        // Chart general settings
        lineChart.description.isEnabled = false // No description label
        lineChart.legend.textColor = Color.BLACK // Legend text color
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.animateX(1500) // Animation

        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.invalidate() // Refresh chart
    }

    // Data class for current price response
    data class TickerPriceResponse(
        @SerializedName("symbol") val symbol: String,
        @SerializedName("price") val price: String
    )
}
```

### 如何運行和測試：

1.  **複製並替換文件:** 將上述內容複製到您的 Android 專案中的相應文件。請特別注意 `settings.gradle` 的修改。
2.  **替換包名:** 確保 `namespace` 和 `applicationId` 在 `app/build.gradle` 以及 `package com.example.goldtracker` 在 `MainActivity.kt` 中與您的專案實際包名匹配。
3.  **Sync Project with Gradle Files:** 在 Android Studio 中，點擊工具欄上的 "Sync Project with Gradle Files" 按鈕，讓 Gradle 下載並配置所有依賴。
4.  **運行應用:** 在模擬器或實體設備上運行應用。

這樣，您的應用應該就能成功編譯並在 GitHub Actions 上執行，並且能夠抓取 PAXGUSDT 金價，顯示當前價格，並繪製 24 小時走勢圖。