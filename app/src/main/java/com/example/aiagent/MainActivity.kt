好的，根據您提供的錯誤日誌，它只指出 `Compilation error. See log for more details` 和 Node.js 版本的警告，但沒有提供具體的 Kotlin 編譯錯誤行號或錯誤類型（例如 `e: ...`）。

在這種情況下，我將根據原始任務描述（使用 OkHttp、MPAndroidChart、顯示漲跌幅、嚴禁 getAxisLabel、使用 valueFormatter）和常見的 Android 開發實踐，提供一個完整且符合要求的 `MainActivity.kt` 代碼。這將包括：

1.  **完整的 Import 語句**。
2.  **ViewModel** 來處理資料獲取和業務邏輯，避免 `NetworkOnMainThreadException`。
3.  **OkHttp** 進行網路請求（我會使用一個模擬的 API 呼叫來演示，因為沒有提供具體的 API 端點）。
4.  **MPAndroidChart** 設定，包括 X 軸的 `DateAxisValueFormatter`。
5.  **漲跌幅計算** 和顯示。
6.  **`valueFormatter`** 的正確使用。

由於缺乏具體的錯誤細節，我會提供一個 *應該* 能順利編譯並運行的完整解決方案，預期它能解決所有潛在的編譯問題。

**請確保您的 `build.gradle (app)` 文件中包含以下依賴：**

gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt' // if using DataBinding/ViewBinding, otherwise remove
}

android {
    namespace 'com.your_package_name.goldpriceapp' // Replace with your actual package name
    compileSdk 34

    defaultConfig {
        applicationId "com.your_package_name.goldpriceapp"
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
        viewBinding true // Enable View Binding
    }
}

dependencies {

    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // Kotlin Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // ViewModel and LiveData
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'
    implementation 'androidx.activity:activity-ktx:1.8.2' // For viewModels() delegate

    // OkHttp
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    // Gson for JSON parsing
    implementation 'com.google.code.gson:gson:2.10.1'

    // MPAndroidChart
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}


**MainActivity.kt**


package com.your_package_name.goldpriceapp // 請替換為您的實際 package 名稱

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.your_package_name.goldpriceapp.databinding.ActivityMainBinding // 請替換為您的實際 package 名稱
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ==============================================================================================
// 1. Data Models (可以放在獨立檔案，這裡為方便展示放在一起)
// ==============================================================================================

/**
 * 代表黃金價格 API 回應的數據模型。
 * 這裡使用模擬的結構，實際應根據 API 文件調整。
 */
data class GoldApiResponse(
    @SerializedName("current_price") val currentPrice: CurrentPrice,
    val history: List<GoldHistoryItem>
)

data class CurrentPrice(
    @SerializedName("usd_per_ounce") val usdPerOunce: Double,
    val timestamp: String // ISO 8601 format
)

data class GoldHistoryItem(
    val date: String, // YYYY-MM-DD
    @SerializedName("usd_per_ounce") val usdPerOunce: Double
)

// ==============================================================================================
// 2. ViewModel
// ==============================================================================================

class GoldViewModel : ViewModel() {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val apiEndpoint = "https://api.example.com/gold/prices" // 請替換為實際的黃金價格 API 端點

    private val _currentPrice = MutableLiveData<Double>()
    val currentPrice: LiveData<Double> = _currentPrice

    private val _priceChange = MutableLiveData<Pair<Double, Double>>() // Pair<ChangeAmount, ChangePercentage>
    val priceChange: LiveData<Pair<Double, Double>> = _priceChange

    private val _chartEntries = MutableLiveData<List<Entry>>()
    val chartEntries: LiveData<List<Entry>> = _chartEntries

    private val _chartLabels = MutableLiveData<List<String>>() // For X-axis labels (dates)
    val chartLabels: LiveData<List<String>> = _chartLabels

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    init {
        fetchGoldPrices()
    }

    fun fetchGoldPrices() {
        viewModelScope.launch {
            try {
                // Simulate network request if no real API is used, otherwise use actual OkHttp call
                val responseBody = if (apiEndpoint.contains("example.com")) {
                    Log.d("GoldViewModel", "Using mock API response.")
                    delay(1000) // Simulate network delay
                    mockGoldApiResponse()
                } else {
                    Log.d("GoldViewModel", "Attempting to fetch from actual API: $apiEndpoint")
                    val request = Request.Builder().url(apiEndpoint).build()
                    val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                    if (!response.isSuccessful) throw IOException("Unexpected code ${response}")
                    response.body?.string() ?: throw IOException("Empty response body")
                }

                val apiResponse = gson.fromJson(responseBody, GoldApiResponse::class.java)

                val history = apiResponse.history.sortedBy { it.date } // Ensure history is sorted by date
                val entries = mutableListOf<Entry>()
                val labels = mutableListOf<String>()

                // Filter for the latest 7 days if history is longer
                val last7DaysHistory = history.takeLast(7)

                last7DaysHistory.forEachIndexed { index, item ->
                    entries.add(Entry(index.toFloat(), item.usdPerOunce.toFloat()))
                    labels.add(item.date)
                }

                _chartEntries.postValue(entries)
                _chartLabels.postValue(labels)

                val currentPriceValue = apiResponse.currentPrice.usdPerOounce
                _currentPrice.postValue(currentPriceValue)

                if (last7DaysHistory.size >= 2) {
                    val yesterdayPrice = last7DaysHistory[last7DaysHistory.size - 2].usdPerOunce
                    val changeAmount = currentPriceValue - yesterdayPrice
                    val changePercentage = (changeAmount / yesterdayPrice) * 100
                    _priceChange.postValue(Pair(changeAmount, changePercentage))
                } else {
                    _priceChange.postValue(Pair(0.0, 0.0)) // No enough data for comparison
                }

            } catch (e: Exception) {
                Log.e("GoldViewModel", "Error fetching gold prices: ${e.message}", e)
                _errorMessage.postValue("Failed to load gold prices: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 模擬黃金價格 API 回應的 JSON 字串。
     * 這裡生成過去7天的模擬數據。
     */
    private fun mockGoldApiResponse(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val historyList = mutableListOf<GoldHistoryItem>()
        var basePrice = 2000.0 // Starting price for simulation

        // Generate 7 days of history
        for (i in 6 downTo 0) { // 6 days ago up to today
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_MONTH, -i)
            val date = dateFormat.format(calendar.time)
            val price = basePrice + (Math.random() - 0.5) * 50 // +/- 25 fluctuation
            historyList.add(GoldHistoryItem(date, String.format(Locale.US, "%.2f", price).toDouble()))
        }

        val latestPrice = historyList.last().usdPerOunce
        val currentPrice = CurrentPrice(latestPrice + (Math.random() - 0.5) * 5, SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date()))

        val apiResponse = GoldApiResponse(currentPrice, historyList)
        return gson.toJson(apiResponse)
    }
}

// ==============================================================================================
// 3. MainActivity
// ==============================================================================================

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GoldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart()
        observeViewModel()

        // Optionally, refresh button
        binding.refreshButton.setOnClickListener {
            viewModel.fetchGoldPrices()
        }
    }

    private fun setupChart() {
        binding.goldPriceChart.apply {
            description.isEnabled = false // Disable description text
            setTouchEnabled(true) // Enable touch gestures
            isDragEnabled = true // Enable dragging
            setScaleEnabled(true) // Enable scaling
            setPinchZoom(true) // Enable pinch zoom

            setDrawGridBackground(false) // Do not draw grid background

            // X-axis configuration
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM // X-axis at the bottom
                setDrawGridLines(false) // Do not draw X-axis grid lines
                setDrawAxisLine(true) // Draw X-axis line
                granularity = 1f // Only zoom to whole numbers
                valueFormatter = DateAxisValueFormatter(emptyList()) // Will be updated by ViewModel
                textColor = Color.BLACK
                textSize = 10f
            }

            // Left Y-axis configuration
            axisLeft.apply {
                setDrawGridLines(true) // Draw Y-axis grid lines
                setDrawAxisLine(true) // Draw Y-axis line
                textColor = Color.BLACK
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    private val decimalFormat = DecimalFormat("###,###,##0.00")
                    override fun getFormattedValue(value: Float): String {
                        return "$${decimalFormat.format(value)}"
                    }
                }
            }

            // Right Y-axis configuration (disable or hide)
            axisRight.isEnabled = false // Disable right Y-axis

            animateX(1000) // Animate chart drawing on X-axis
        }
    }

    private fun observeViewModel() {
        viewModel.currentPrice.observe(this) { price ->
            val decimalFormat = DecimalFormat("###,###,##0.00")
            binding.currentPriceTextView.text = getString(R.string.current_price_format, decimalFormat.format(price))
        }

        viewModel.priceChange.observe(this) { (changeAmount, changePercentage) ->
            val decimalFormat = DecimalFormat("###,###,##0.00")
            val percentageFormat = DecimalFormat("0.00")

            val sign = if (changeAmount >= 0) "+" else ""
            val color = if (changeAmount >= 0) Color.GREEN else Color.RED

            binding.priceChangeTextView.apply {
                text = getString(
                    R.string.price_change_format,
                    sign,
                    decimalFormat.format(abs(changeAmount)),
                    sign,
                    percentageFormat.format(abs(changePercentage))
                )
                setTextColor(color)
            }
        }

        viewModel.chartEntries.observe(this) { entries ->
            viewModel.chartLabels.observe(this) { labels ->
                updateChart(entries, labels)
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            // Display error message to user, e.g., using a Toast or Snackbar
            Log.e("MainActivity", "Error: $message")
            // Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            binding.currentPriceTextView.text = getString(R.string.error_loading_data)
            binding.priceChangeTextView.text = ""
        }
    }

    private fun updateChart(entries: List<Entry>, labels: List<String>) {
        if (entries.isEmpty()) {
            binding.goldPriceChart.clear()
            binding.goldPriceChart.setNoDataText("沒有資料可顯示。")
            binding.goldPriceChart.invalidate()
            return
        }

        val dataSet = LineDataSet(entries, "黃金價格 (USD/oz)").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 0f // Hide value labels on points
            setDrawFilled(true) // Draw filled area below the line
            fillColor = Color.parseColor("#ADD8E6") // Light blue fill
            fillAlpha = 50 // Transparency for fill
            mode = LineDataSet.Mode.LINEAR // Make the line smooth
        }

        val lineData = LineData(dataSet)
        binding.goldPriceChart.data = lineData

        // Update X-axis labels
        val xAxis = binding.goldPriceChart.xAxis
        xAxis.valueFormatter = DateAxisValueFormatter(labels)

        // Refresh chart
        binding.goldPriceChart.notifyDataSetChanged()
        binding.goldPriceChart.invalidate()
    }

    // ==============================================================================================
    // 4. ValueFormatter for X-axis (Dates)
    // ==============================================================================================
    class DateAxisValueFormatter(private val labels: List<String>) : ValueFormatter() {
        private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index >= 0 && index < labels.size) {
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(labels[index])
                    date?.let { dateFormat.format(it) } ?: labels[index]
                } catch (e: Exception) {
                    labels[index]
                }
            } else {
                ""
            }
        }
    }
}


**res/layout/activity_main.xml** (用於 `ActivityMainBinding`)

xml
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
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:text="@string/app_name_title"
        android:textSize="24sp"
        android:textStyle="bold"
        android:gravity="center"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginBottom="8dp"/>

    <TextView
        android:id="@+id/currentPriceLabel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/current_gold_price_label"
        android:textSize="16sp"
        app:layout_constraintTop_toBottomOf="@id/titleTextView"
        app:layout_constraintStart_toStartOf="parent"
        android:layout_marginTop="16dp" />

    <TextView
        android:id="@+id/currentPriceTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/loading_price"
        android:textSize="28sp"
        android:textStyle="bold"
        app:layout_constraintTop_toBottomOf="@id/currentPriceLabel"
        app:layout_constraintStart_toStartOf="parent"
        android:layout_marginTop="4dp" />

    <TextView
        android:id="@+id/priceChangeTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text=""
        android:textSize="18sp"
        android:textStyle="bold"
        app:layout_constraintTop_toBottomOf="@id/currentPriceTextView"
        app:layout_constraintStart_toStartOf="parent"
        android:layout_marginTop="4dp" />

    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/goldPriceChart"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="16dp"
        app:layout_constraintTop_toBottomOf="@id/priceChangeTextView"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toTopOf="@id/refreshButton" />

    <Button
        android:id="@+id/refreshButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/refresh"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="16dp" />

</androidx.constraintlayout.widget.ConstraintLayout>


**res/values/strings.xml**

xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">黃金現價 App</string>
    <string name="app_name_title">黃金價格查詢</string>
    <string name="current_gold_price_label">當前黃金價格 (USD/oz):</string>
    <string name="loading_price">載入中...</string>
    <string name="current_price_format">$%s</string>
    <string name="price_change_format">%s$%.2f (%s%.2f%%)</string>
    <string name="refresh">刷新</string>
    <string name="error_loading_data">無法載入資料</string>
</resources>


**AndroidManifest.xml** (確保有網路權限)

xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.GoldPriceApp"
        android:usesCleartextTraffic="true"
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


**重要提示：**
1.  **`package com.your_package_name.goldpriceapp`**: 請務必將此替換為您專案的實際套件名稱。這適用於 `MainActivity.kt` 頂部和 `activity_main.xml` 中的 `tools:context` 以及 `build.gradle (app)` 中的 `namespace` 和 `applicationId`。
2.  **API 端點**: `GoldViewModel` 中的 `apiEndpoint` 變數目前指向 `https://api.example.com/gold/prices`。如果您有實際的黃金價格 API，請將其替換為實際的 URL。如果沒有，它將使用我提供的模擬數據。
3.  **App 主題**: 我假設您的 `styles.xml` 或 `themes.xml` 中有一個名為 `Theme.GoldPriceApp` 的主題。如果不是，請根據您的專案調整 `AndroidManifest.xml` 中的 `android:theme`。

這個完整的 `MainActivity.kt` 檔案及其相關輔助程式碼應該能夠解決常見的編譯問題，並實現您所有列出的功能。