好的，這是一個關於更新黃金追蹤 App 具有今日走勢功能的 `README.md` 文件。

---

# 黃金追蹤 App - 今日走勢

一個簡單的 Android 應用程式，用於追蹤黃金 (PAXGUSDT) 的即時價格、顯示今日漲跌幅，並以走勢圖呈現過去 24 小時的價格變化。數據來源為 Binance API。

## 目錄

*   [功能](#功能)
*   [技術棧](#技術棧)
*   [設置與運行](#設置與運行)
    *   [環境要求](#環境要求)
    *   [步驟 1: 克隆專案](#步驟-1-克隆專案)
    *   [步驟 2: 更新 `settings.gradle`](#步驟-2-更新-settingsgradle)
    *   [步驟 3: 更新 `app/build.gradle`](#步驟-3-更新-appbuildgradle)
    *   [步驟 4: 更新 `AndroidManifest.xml`](#步驟-4-更新-androidmanifestxml)
    *   [步驟 5: 佈局文件 `activity_main.xml`](#步驟-5-佈局文件-activity_mainxml)
    *   [步驟 6: 主要邏輯 `MainActivity.kt`](#步驟-6-主要邏輯-mainactivitykt)
    *   [步驟 7: 運行應用程式](#步驟-7-運行應用程式)
*   [螢幕截圖 (預留)](#螢幕截圖-預留)
*   [未來增強](#未來增強)
*   [貢獻](#貢獻)
*   [許可證](#許可證)

## 功能

*   **即時價格顯示**: 顯示 PAXGUSDT 的當前價格。
*   **今日漲跌計算**: 計算並顯示今日的漲跌價 (USD) 和漲跌幅 (%)。
*   **24 小時價格走勢圖**: 使用 MPAndroidChart 繪製過去 24 小時的價格數據。
*   **數據來源**: 從 Binance API 獲取最新的 Klines 數據。
*   **非同步操作**: 利用 Kotlin Coroutines 在背景執行緒處理網路請求和數據解析。

## 技術棧

*   **Kotlin**：Android 應用程式開發語言。
*   **Android SDK**：開發工具包。
*   **Coroutines**：用於簡化非同步編程。
*   **MPAndroidChart**：強大的 Android 圖表庫。
*   **Binance API**：數據來源。

## 設置與運行

### 環境要求

*   Android Studio (Flamingo 或更高版本推薦)
*   JDK 11 或更高版本
*   穩定的網路連接

### 步驟 1: 克隆專案

```bash
git clone https://github.com/yourusername/gold-tracker-app.git
cd gold-tracker-app
```

### 步驟 2: 更新 `settings.gradle`

開啟專案根目錄下的 `settings.gradle` 文件，確保 `jitpack.io` 倉庫已經加入。這對於下載 `MPAndroidChart` 依賴是必要的。

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 添加 JitPack 倉庫
        maven { url 'https://jitpack.io' }
    }
}
rootProject.name = "GoldTrackerApp"
include ':app'
```

### 步驟 3: 更新 `app/build.gradle`

開啟 `app` 模塊下的 `build.gradle` 文件，在 `dependencies` 區塊加入 `MPAndroidChart` 繪圖庫依賴。

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.goldtrackerapp' // 替換為你的 package name
    compileSdk 34 // 或你的目標 SDK 版本

    defaultConfig {
        applicationId 'com.example.goldtrackerapp' // 替換為你的 application ID
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
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // Coroutines 依賴
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1'

    // MPAndroidChart 繪圖庫
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // JSON 解析 (如果你沒有使用 Retrofit/Gson，則需要手動解析)
    // 通常 Android SDK 內建的 org.json.JSONArray 足夠簡單使用
    // implementation 'com.google.code.gson:gson:2.10.1' // 如果你選擇使用 Gson

    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

同步你的專案 (Sync Project with Gradle Files)。

### 步驟 4: 更新 `AndroidManifest.xml`

開啟 `app/src/main/AndroidManifest.xml` 文件，確保在 `<application>` 標籤之前加入網路權限。

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 確保有網路權限 -->
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

### 步驟 5: 佈局文件 `activity_main.xml`

創建或修改 `app/src/main/res/layout/activity_main.xml`，添加用於顯示價格、漲跌幅和圖表的元件。

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
        android:id="@+id/titleTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="PAXGUSDT 黃金價格追蹤"
        android:textSize="24sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/priceTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="當前價格: 載入中..."
        android:textSize="36sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/titleTextView" />

    <TextView
        android:id="@+id/changeValueTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="今日漲跌價: 載入中..."
        android:textSize="20sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/priceTextView" />

    <TextView
        android:id="@+id/changePercentTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="今日漲跌幅: 載入中..."
        android:textSize="20sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/changeValueTextView" />

    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/lineChart"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="24dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/changePercentTextView" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

在 `app/src/main/res/values/colors.xml` 中定義一些顏色，以便在圖表中使用：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>

    <!-- 自定義圖表顏色 -->
    <color name="green">#4CAF50</color> <!-- 漲 -->
    <color name="red">#F44336</color>   <!-- 跌 -->
    <color name="chartLineColor">#2196F3</color> <!-- 圖表線條顏色 (藍色) -->
    <color name="chartValueTextColor">#000000</color> <!-- 圖表數值文字顏色 -->
    <color name="axisLabelColor">#424242</color> <!-- 軸標籤顏色 -->
</resources>
```

### 步驟 6: 主要邏輯 `MainActivity.kt`

修改 `app/src/main/java/com/example/goldtrackerapp/MainActivity.kt` (請將 `com.example.goldtrackerapp` 替換為你的實際 package name)。

```kotlin
package com.example.goldtrackerapp

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URL
import java.text.DecimalFormat
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var priceTextView: TextView
    private lateinit var changeValueTextView: TextView
    private lateinit var changePercentTextView: TextView
    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化視圖元件
        priceTextView = findViewById(R.id.priceTextView)
        changeValueTextView = findViewById(R.id.changeValueTextView)
        changePercentTextView = findViewById(R.id.changePercentTextView)
        lineChart = findViewById(R.id.lineChart)

        // 啟動協程獲取數據
        fetchGoldPriceData()
    }

    private fun fetchGoldPriceData() {
        // 在 IO 執行緒中執行網路請求
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Binance 24小時 Kline API (PAXGUSDT, 1小時K線, 24條數據)
                val apiUrl = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24"
                val jsonString = URL(apiUrl).readText()
                val klines = JSONArray(jsonString)

                if (klines.length() >= 24) { // 確保數據足夠
                    // 提取開盤價 (第一條 K 線的開盤價)
                    val firstKline = klines.getJSONArray(0)
                    val openPrice = firstKline.getString(1).toDouble() // 開盤價是 JSON 陣列中的第2個元素 (索引1)

                    // 提取當前價格 (最後一條 K 線的收盤價)
                    val lastKline = klines.getJSONArray(klines.length() - 1)
                    val currentPrice = lastKline.getString(4).toDouble() // 收盤價是 JSON 陣列中的第5個元素 (索引4)

                    // 計算漲跌價和漲跌幅
                    val priceChanges = currentPrice - openPrice
                    val priceChangePercent = (priceChanges / openPrice) * 100

                    // 準備圖表數據
                    val chartEntries = ArrayList<Entry>()
                    val hours = ArrayList<String>() // 用於 X 軸標籤

                    for (i in 0 until klines.length()) {
                        val kline = klines.getJSONArray(i)
                        val closePrice = kline.getString(4).toDouble() // 每一小時的收盤價
                        chartEntries.add(Entry(i.toFloat(), closePrice.toFloat()))
                        hours.add("${i+1}h") // 簡單的小時標籤
                    }

                    // 切換到主執行緒更新 UI
                    withContext(Dispatchers.Main) {
                        val df = DecimalFormat("#,##0.00") // 格式化為兩位小數
                        priceTextView.text = "當前價格: $${df.format(currentPrice)}"
                        changeValueTextView.text = "今日漲跌價: ${df.format(priceChanges)} USD"
                        changePercentTextView.text = "今日漲跌幅: ${df.format(priceChangePercent)}%"

                        // 根據漲跌設置文字顏色
                        val color = if (priceChanges >= 0) resources.getColor(R.color.green, theme) else resources.getColor(R.color.red, theme)
                        changeValueTextView.setTextColor(color)
                        changePercentTextView.setTextColor(color)

                        // 配置並繪製 LineChart
                        setupChart(chartEntries, hours)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        priceTextView.text = "錯誤: 數據不足，無法計算。"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    priceTextView.text = "錯誤: 無法獲取數據: ${e.message}"
                }
            }
        }
    }

    private fun setupChart(entries: ArrayList<Entry>, hours: ArrayList<String>) {
        val dataSet = LineDataSet(entries, "PAXGUSDT 24小時走勢")
        dataSet.color = resources.getColor(R.color.chartLineColor, theme)
        dataSet.valueTextColor = resources.getColor(R.color.chartValueTextColor, theme)
        dataSet.setDrawCircles(false) // 不顯示數據點上的圓圈
        dataSet.setDrawValues(false) // 不顯示數據點上的數值
        dataSet.lineWidth = 2f // 線條粗細
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // 自定義圖表外觀
        lineChart.description.isEnabled = false // 不顯示描述標籤
        lineChart.setTouchEnabled(true) // 允許觸摸互動
        lineChart.isDragEnabled = true // 允許拖動
        lineChart.setScaleEnabled(true) // 允許縮放
        lineChart.setPinchZoom(true) // 允許雙指縮放

        // X 軸配置
        val xAxis = lineChart.xAxis
        xAxis.setDrawGridLines(false) // 不顯示 X 軸網格線
        xAxis.position = XAxis.XAxisPosition.BOTTOM // X 軸顯示在底部
        xAxis.textColor = resources.getColor(R.color.axisLabelColor, theme)
        xAxis.valueFormatter = IndexAxisValueFormatter(hours) // 設置 X 軸標籤格式
        xAxis.granularity = 1f // 最小間隔為 1

        // 左 Y 軸配置
        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.setDrawGridLines(true) // 顯示 Y 軸網格線
        yAxisLeft.textColor = resources.getColor(R.color.axisLabelColor, theme)
        yAxisLeft.setLabelCount(5, true) // 設置標籤數量，force=true 強制顯示

        // 禁用右 Y 軸
        lineChart.axisRight.isEnabled = false

        lineChart.animateX(1500) // X 軸動畫 (1.5秒)
        lineChart.invalidate() // 刷新圖表
    }
}
```

### 步驟 7: 運行應用程式

在 Android Studio 中點擊 "Run 'app'" 按鈕 (綠色播放圖標)，選擇一個模擬器或連接的實體設備來運行應用程式。

## 螢幕截圖 (預留)

*   [這裡放置應用程式運行時的螢幕截圖]

## 未來增強

*   **自動刷新**: 定時刷新數據以獲取最新價格。
*   **多種時間間隔**: 允許用戶選擇查看不同時間範圍的數據 (例如 1 天, 1 週, 1 月)。
*   **更多黃金資產**: 支援追蹤其他黃金相關資產。
*   **警報功能**: 設定價格警報。
*   **本地數據緩存**: 減少 API 請求並在離線時顯示舊數據。
*   **錯誤處理與 UI 反饋**: 更友善的錯誤訊息和載入狀態提示。

## 貢獻

歡迎任何形式的貢獻！如果您有任何建議、錯誤報告或想提交新功能，請隨時提交 Pull Request 或開啟 Issue。

## 許可證

此專案在 MIT 許可證下發布。詳情請參閱 [LICENSE](LICENSE) 文件。

---