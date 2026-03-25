好的，針對您描述的「Build failed on GitHub Actions. Please check Gradle dependencies or Kotlin syntax.」以及「Library 引用錯誤」的問題，我將提供一個完整且經過修正的 Android 專案配置和 `MainActivity.kt` 程式碼。

這個解決方案會：
1.  **確保 Gradle 配置正確**：特別是 `okhttp`, `gson`, `MPAndroidChart` 以及 Kotlin Coroutines 的引入。
2.  **使用 Kotlin Coroutines 處理非同步操作**：這是 Android 上的現代最佳實踐，可以安全地在背景執行網路請求並在主線程更新 UI。
3.  **使用 MPAndroidChart 繪製 LineChart**：這是 Android 上最受歡迎的圖表庫之一。
4.  **提供完整的 `MainActivity.kt` 程式碼**。
5.  **提供必要的 `activity_main.xml` 和 `AndroidManifest.xml` 片段**。

---

### 步驟 1: 配置 `build.gradle.kts` (Project Level)

確保 `settings.gradle.kts` 或專案根目錄下的 `build.gradle.kts` 中包含了 `mavenCentral()` 和 `jitpack.io`，因為 MPAndroidChart 通常是透過 JitPack 分發。

**`settings.gradle.kts` (或舊版 `build.gradle` project-level)**

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
        // MPAndroidChart 依賴於 jitpack.io
        maven("https://jitpack.io")
    }
}
rootProject.name = "GoldPriceTracker" // 你的專案名稱
include(":app")
```

### 步驟 2: 配置 `build.gradle.kts` (Module Level - `:app`)

這是最重要的部分，用於導入所有必要的庫。

**`app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourcompany.goldpricetracker" // 請替換為你的 package 名稱
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourcompany.goldpricetracker" // 請替換為你的 package 名稱
        minSdk = 24
        targetSdk = 34
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
        viewBinding = true // 啟用 ViewBinding
    }
}

dependencies {
    // AndroidX 核心庫
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // OkHttp for network requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Kotlin Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0") // For lifecycleScope

    // MPAndroidChart for LineChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // 請檢查最新穩定版本

    // 測試庫
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

### 步驟 3: 配置 `AndroidManifest.xml`

為了執行網路請求，需要添加 `INTERNET` 權限。

**`app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 網絡權限 -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.GoldPriceTracker"
        android:usesCleartextTraffic="false"
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

### 步驟 4: 設計 UI (`activity_main.xml`)

我們需要一個 `TextView` 來顯示當前金價，以及一個 `LineChart` 來顯示走勢圖。

**`app/src/main/res/layout/activity_main.xml`**

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
        android:id="@+id/currentPriceTextView"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_marginEnd="8dp"
        android:gravity="center"
        android:text="載入中..."
        android:textSize="24sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/lastUpdatedTextView"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:gravity="center"
        android:text="最後更新: "
        android:textSize="14sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/currentPriceTextView" />

    <!-- MPAndroidChart -->
    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/lineChart"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/lastUpdatedTextView" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### 步驟 5: `MainActivity.kt` 完整程式碼

```kotlin
package com.yourcompany.goldpricetracker // 請替換為你的 package 名稱

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.yourcompany.goldpricetracker.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    // 儲存 KLine 數據，方便 X 軸格式化
    private var klineDataList: List<KlineData> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart()
        fetchBinanceData()
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false // 禁用描述
            setTouchEnabled(true) // 允許觸摸互動
            isDragEnabled = true // 允許拖動
            setScaleEnabled(true) // 允許縮放
            setPinchZoom(true) // 允許捏合縮放

            setDrawGridBackground(false) // 不繪製網格背景

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM // X 軸顯示在底部
                setDrawGridLines(false) // 不繪製 X 軸網格線
                textColor = Color.WHITE // 設置文字顏色
                valueFormatter = object : ValueFormatter() { // 定義 X 軸值格式化器
                    private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        // 確保索引在 klineDataList 範圍內
                        val index = value.toInt()
                        return if (index >= 0 && index < klineDataList.size) {
                            val timestamp = klineDataList[index].timestamp
                            mFormat.format(Date(timestamp))
                        } else {
                            "" // 超出範圍則不顯示
                        }
                    }
                }
                labelCount = 4 // 設置 X 軸顯示標籤的數量
                granularity = 1f // 設置 X 軸最小間隔，防止標籤重疊
            }

            axisLeft.apply {
                setDrawGridLines(true) // 繪製 Y 軸網格線
                textColor = Color.WHITE
                gridColor = Color.GRAY
                gridLineWidth = 0.5f
            }
            axisRight.isEnabled = false // 禁用右側 Y 軸

            legend.isEnabled = false // 禁用圖例 (通常不需要)
            animateX(1500) // X 軸動畫
        }
    }

    private fun fetchBinanceData() {
        binding.currentPriceTextView.text = "載入中..."
        binding.lastUpdatedTextView.text = "最後更新: "

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Binance KLines API (PAXGUSDT, 1小時K線, 最近24條數據)
                val url = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonString = response.body?.string()
                    val klines = gson.fromJson(jsonString, JsonArray::class.java)

                    val parsedData = mutableListOf<KlineData>()
                    klines?.forEach { klineArrayElement ->
                        val kline = klineArrayElement.asJsonArray
                        if (kline.size() > 4) { // 確保有足夠的數據
                            val openTime = kline[0].asLong
                            val closePrice = kline[4].asString.toDouble()
                            parsedData.add(KlineData(openTime, closePrice))
                        }
                    }
                    klineDataList = parsedData // 更新全局 klineDataList

                    withContext(Dispatchers.Main) {
                        updateUI(parsedData)
                    }
                } else {
                    val errorMessage = "API 請求失敗: ${response.code} ${response.message}"
                    Log.e("BinanceData", errorMessage)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                        binding.currentPriceTextView.text = "載入失敗"
                    }
                }

            } catch (e: IOException) {
                Log.e("BinanceData", "網路錯誤: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "網路錯誤，請檢查連接", Toast.LENGTH_LONG).show()
                    binding.currentPriceTextView.text = "載入失敗"
                }
            } catch (e: Exception) {
                Log.e("BinanceData", "解析錯誤: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "數據解析錯誤", Toast.LENGTH_LONG).show()
                    binding.currentPriceTextView.text = "載入失敗"
                }
            }
        }
    }

    private fun updateUI(data: List<KlineData>) {
        if (data.isEmpty()) {
            binding.currentPriceTextView.text = "無可用數據"
            binding.lineChart.setNoDataText("無數據可顯示")
            binding.lineChart.invalidate()
            return
        }

        // 顯示當前價格 (最後一條數據的收盤價)
        val latestKline = data.last()
        binding.currentPriceTextView.text = "PAXGUSDT: $%.2f USD".format(latestKline.closePrice)

        // 顯示最後更新時間
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        binding.lastUpdatedTextView.text = "最後更新: ${dateFormat.format(Date(latestKline.timestamp))}"


        // 準備 LineChart 的數據
        val entries = data.mapIndexed { index, kline ->
            // X 軸使用索引，方便 MPAndroidChart 處理等間隔數據
            // Y 軸使用價格
            Entry(index.toFloat(), kline.closePrice.toFloat())
        }

        val dataSet = LineDataSet(entries, "PAXGUSDT 24小時走勢").apply {
            color = Color.rgb(255, 165, 0) // 橙色線條
            lineWidth = 2.5f
            setCircleColor(Color.rgb(255, 165, 0)) // 數據點顏色
            circleRadius = 4f
            setDrawValues(false) // 不在數據點上顯示數值
            setDrawCircles(true) // 繪製數據點
            setDrawFilled(true) // 填充圖表下方區域
            fillColor = Color.rgb(255, 165, 0)
            fillAlpha = 50 // 填充區域透明度
            mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線
        }

        val lineData = LineData(dataSet)
        binding.lineChart.data = lineData
        binding.lineChart.invalidate() // 刷新圖表
    }

    // KLine 數據模型
    data class KlineData(
        val timestamp: Long, // 開盤時間戳 (毫秒)
        val closePrice: Double // 收盤價
    )
}
```

---

### 如何在 Android Studio 中使用

1.  **建立新專案**：選擇 "Empty Activity" 模板。
2.  **更新 Gradle 檔案**：將上述 `settings.gradle.kts` 和 `app/build.gradle.kts` 的內容複製貼上到你的專案中對應的位置。記得將 `com.yourcompany.goldpricetracker` 替換為你的實際 package 名稱。
3.  **同步 Gradle**：點擊 Android Studio 右上角的 "Sync Project with Gradle Files" 按鈕。
4.  **更新 `AndroidManifest.xml`**：添加 `INTERNET` 權限。
5.  **更新 `activity_main.xml`**：複製貼上 UI 佈局代碼。
6.  **更新 `MainActivity.kt`**：複製貼上 Kotlin 代碼。
7.  **執行應用程式**：在模擬器或實體設備上運行。

### 修正的重點

1.  **Gradle Dependencies**：確保了所有庫（尤其是 `okhttp`, `gson`, `MPAndroidChart`, `kotlinx-coroutines-android`, `lifecycle-runtime-ktx`）都以正確的方式和版本號被 `implementation`。`jitpack.io` 倉庫的引入對於 `MPAndroidChart` 至關重要。
2.  **Kotlin Coroutines**：使用了 `lifecycleScope.launch(Dispatchers.IO)` 在 IO 線程執行網路請求，並使用 `withContext(Dispatchers.Main)` 安全地回到主線程更新 UI，避免了 `NetworkOnMainThreadException` 和 UI 凍結。
3.  **View Binding**：使用了 `ViewBinding` 替代了 `findViewById`，使代碼更安全、簡潔。
4.  **Binance API 選擇**：選用了 `/api/v3/klines` 端點來獲取歷史 K 線數據，`interval=1h` 和 `limit=24` 確保獲取最近 24 小時的數據。
5.  **JSON 解析**：由於 Binance KLines API 返回的是一個 `JsonArray` (數組的數組)，所以使用 `gson.fromJson(jsonString, JsonArray::class.java)` 進行初步解析，然後手動遍歷 `JsonArray` 提取數據。
6.  **MPAndroidChart 配置**：對圖表進行了基本的初始化和美化，包括 X 軸時間格式化，使之顯示可讀的時間。
7.  **錯誤處理**：增加了 `try-catch` 塊來處理網路錯誤 (`IOException`) 和 JSON 解析錯誤。

這個版本應該能解決您在 GitHub Actions 上遇到的編譯失敗和庫引用錯誤，並實現所有功能。