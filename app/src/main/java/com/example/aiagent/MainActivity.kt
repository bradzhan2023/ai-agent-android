好的，這是一個完整的 Android 專案解決方案，包含了 `MainActivity.kt` 內容以及 `build.gradle` (Module: app) 和 `settings.gradle` 的修正，確保所有庫（OkHttp, Gson, MPAndroidChart）都正確導入，並能處理 GitHub Actions 可能遇到的編譯問題。

**問題分析與修正：**

1.  **Gradle Dependencies:**
    *   `OkHttp` 和 `Gson` 是標準庫，引用通常不會有大問題。
    *   `MPAndroidChart` 是一個第三方庫，通常需要從 `JitPack` 倉庫獲取。GitHub Actions 若缺少 `maven { url 'https://jitpack.io' }` 的配置，就會找不到這個庫。
    *   Kotlin Coroutines (用於異步操作) 的相關依賴也需要正確引入。
    *   **修正:** 確保在 `settings.gradle` (或較舊的 Gradle 版本中的 `build.gradle` (Project)) 中添加 `maven { url 'https://jitpack.io' }`，並在 `build.gradle` (Module: app) 中引入所有必要庫及其最新穩定版本。

2.  **AndroidManifest.xml:**
    *   網路操作必須有 `INTERNET` 權限。
    *   **修正:** 添加 `<uses-permission android:name="android.permission.INTERNET" />`。

3.  **MainActivity.kt 邏輯:**
    *   **網路請求 (OkHttp):** 必須在背景執行緒進行。使用 Kotlin Coroutines (`lifecycleScope`) 是現代 Android 的最佳實踐。
    *   **JSON 解析 (Gson):** Binance 的 `klines` API 返回的是 `List<List<String>>` 結構，需要正確解析。
    *   **UI 更新:** 任何 UI 操作都必須在主執行緒進行。
    *   **圖表繪製 (MPAndroidChart):** 初始化圖表、準備資料、更新圖表。
    *   **錯誤處理:** 網路錯誤、解析錯誤、API 返回空資料等情況。
    *   **修正:** 實作上述邏輯，包含 loading 狀態、錯誤顯示。為圖表的 X 軸添加日期時間格式化器，使其顯示更清晰。

---

### 專案結構與檔案內容

你需要更新以下檔案。

**1. `settings.gradle.kts` (Project Level)**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_NON_TRANSITIVE_DEPENDENCIES)
    repositories {
        google()
        mavenCentral()
        // *** 確保為 MPAndroidChart 添加此行 ***
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "BinancePAXGTracker" // 你的專案名稱
include(":app")
```

**2. `app/build.gradle.kts` (Module Level)**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourcompany.binancepaxtracker" // 修改為你的包名
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourcompany.binancepaxtracker" // 修改為你的包名
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
    // 啟用 View Binding
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Kotlin Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0") // For lifecycleScope

    // OkHttp for network requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // MPAndroidChart for line chart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

**3. `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 必需的網路權限 -->
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

**4. `app/src/main/res/layout/activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    tools:context=".MainActivity">

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="PAXGUSDT 價格追蹤 (24H)"
        android:textSize="20sp"
        android:textStyle="bold"
        android:layout_gravity="center_horizontal"
        android:layout_marginBottom="16dp"/>

    <TextView
        android:id="@+id/tvCurrentPriceLabel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="當前價格:"
        android:textSize="16sp"
        android:layout_marginBottom="4dp"/>

    <TextView
        android:id="@+id/tvCurrentPrice"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="---"
        android:textSize="24sp"
        android:textStyle="bold"
        android:textColor="@color/design_default_color_primary"
        android:layout_marginBottom="16dp"/>

    <ProgressBar
        android:id="@+id/progressBar"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:visibility="gone"
        android:indeterminateTint="@color/design_default_color_primary"
        />

    <TextView
        android:id="@+id/tvError"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="@android:color/holo_red_dark"
        android:textSize="16sp"
        android:textAlignment="center"
        android:visibility="gone"
        android:layout_marginBottom="16dp"
        tools:text="錯誤訊息顯示在這裡"/>

    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/priceChart"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:visibility="gone"/> <!-- 初始隱藏，載入完成後顯示 -->

</LinearLayout>
```

**5. `app/src/main/java/com/yourcompany/binancepaxtracker/MainActivity.kt`**

```kotlin
package com.yourcompany.binancepaxtracker

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourcompany.binancepaxtracker.databinding.ActivityMainBinding // 確保你的包名正確
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart()
        fetchBinanceData()
    }

    private fun setupChart() {
        binding.priceChart.apply {
            description.isEnabled = false // 不顯示描述文字
            setTouchEnabled(true)       // 允許觸摸互動
            isDragEnabled = true        // 允許拖曳
            setScaleEnabled(true)       // 允許縮放
            setPinchZoom(true)          // 允許手勢縮放

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM // X軸顯示在底部
                setDrawGridLines(false)       // 不繪製X軸網格線
                textColor = Color.BLACK
                valueFormatter = DateAxisFormatter() // 自定義X軸格式化器
            }

            axisLeft.apply {
                setDrawGridLines(true)        // 繪製Y軸網格線
                textColor = Color.BLACK
            }
            axisRight.isEnabled = false   // 不顯示右側Y軸

            legend.isEnabled = false      // 不顯示圖例
            animateX(1000)                // X軸動畫
        }
    }

    private fun fetchBinanceData() {
        // 顯示載入指示器，隱藏圖表和錯誤訊息
        binding.progressBar.visibility = View.VISIBLE
        binding.priceChart.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tvCurrentPrice.text = "載入中..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Binance Klines API:
                // symbol: 交易對 (PAXGUSDT)
                // interval: K線間隔 (1h 代表 1 小時)
                // limit: 返回 K 線的數量 (24 代表 24 小時的數據)
                val url = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonString = response.body?.string()
                    val type = object : TypeToken<List<List<String>>>() {}.type
                    val klines: List<List<String>> = gson.fromJson(jsonString, type)

                    if (klines.isNotEmpty()) {
                        // Klines 數據格式:
                        // [
                        //   [0] open time,
                        //   [1] open,
                        //   [2] high,
                        //   [3] low,
                        //   [4] close,
                        //   [5] volume,
                        //   [6] close time,
                        //   ...
                        // ]

                        // 提取收盤價和時間戳
                        val entries = ArrayList<Entry>()
                        val timestamps = ArrayList<Long>() // 用於X軸格式化
                        var lastPrice = ""

                        klines.forEachIndexed { index, kline ->
                            val closePrice = kline[4].toFloat() // 收盤價
                            val openTime = kline[0].toLong()   // 開盤時間 (毫秒)
                            entries.add(Entry(index.toFloat(), closePrice)) // X軸使用索引，Y軸使用價格
                            timestamps.add(openTime)

                            if (index == klines.size - 1) {
                                lastPrice = kline[4] // 最後一個數據點為當前價格
                            }
                        }

                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.priceChart.visibility = View.VISIBLE
                            binding.tvCurrentPrice.text = String.format("%.2f USDT", lastPrice.toFloat()) // 顯示當前價格
                            updateChartData(entries, timestamps)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.tvError.visibility = View.VISIBLE
                            binding.tvError.text = "未獲取到數據。"
                            binding.tvCurrentPrice.text = "---"
                        }
                    }
                } else {
                    val errorMessage = response.body?.string() ?: response.message
                    throw IOException("HTTP Error: ${response.code} - $errorMessage")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "載入失敗: ${e.localizedMessage}"
                    binding.tvCurrentPrice.text = "---"
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateChartData(entries: ArrayList<Entry>, timestamps: ArrayList<Long>) {
        val dataSet = LineDataSet(entries, "PAXGUSDT Price").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            setDrawCircles(false) // 不繪製數據點圓圈
            lineWidth = 2f
            valueTextSize = 0f    // 不在圖表上顯示值
            mode = LineDataSet.Mode.LINEAR // 平滑曲線
            setDrawFilled(true) // 填充線下方區域
            fillColor = Color.parseColor("#ADD8E6") // 淺藍色填充
            fillAlpha = 80 // 填充透明度
        }

        val lineData = LineData(dataSet)
        binding.priceChart.data = lineData

        // 更新X軸格式化器中的時間戳
        (binding.priceChart.xAxis.valueFormatter as DateAxisFormatter).setTimestamps(timestamps)

        binding.priceChart.invalidate() // 刷新圖表
    }

    // 自定義X軸格式化器，用於將索引轉換為時間
    private class DateAxisFormatter : ValueFormatter() {
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private var timestamps: List<Long> = emptyList()

        fun setTimestamps(timestamps: List<Long>) {
            this.timestamps = timestamps
        }

        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            val index = value.toInt()
            return if (index >= 0 && index < timestamps.size) {
                val date = Date(timestamps[index])
                dateFormat.format(date)
            } else {
                ""
            }
        }
    }
}
```

---

### 如何使用：

1.  **新建 Android 專案：** 在 Android Studio 中創建一個新的 "Empty Activity" 專案。
    *   選擇 `Kotlin` 作為語言。
    *   `Minimum SDK` 建議 `API 24` 或更高。
2.  **更新 `settings.gradle.kts`：** 將上面提供的 `settings.gradle.kts` 內容複製貼上，特別注意 `maven { url = uri("https://jitpack.io") }` 這行。
3.  **更新 `app/build.gradle.kts`：** 將上面提供的 `app/build.gradle.kts` 內容複製貼上。請務必將 `namespace` 和 `applicationId` 改為你的專案包名。
4.  **更新 `AndroidManifest.xml`：** 將上面提供的 `AndroidManifest.xml` 內容複製貼上，確保 `INTERNET` 權限已添加。
5.  **更新 `activity_main.xml`：** 將上面提供的 `activity_main.xml` 內容複製貼上到你的 `app/src/main/res/layout/activity_main.xml` 檔案中。
6.  **更新 `MainActivity.kt`：** 將上面提供的 `MainActivity.kt` 內容複製貼上到你的 `app/src/main/java/你的包名/MainActivity.kt` 檔案中。請注意修改 `package` 行為你的實際包名，以及 `import com.yourcompany.binancepaxtracker.databinding.ActivityMainBinding` 這行中的包名。
7.  **Sync Project with Gradle Files：** 在 Android Studio 中點擊 "Sync Project with Gradle Files" 按鈕 (通常在右上角，或透過 `File -> Sync Project with Gradle Files`)，讓 Gradle 下載所有依賴庫。
8.  **運行應用程式：** 在模擬器或實體設備上運行應用程式。它應該會顯示 PAXGUSDT 的當前價格和過去 24 小時的價格走勢圖。

透過這些步驟，你的 Android 專案將能夠正確編譯並執行金價追蹤功能，同時也解決了 GitHub Actions 上可能遇到的庫引用問題。