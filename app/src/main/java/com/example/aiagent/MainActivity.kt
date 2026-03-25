好的，我理解了問題的關鍵：`Unresolved reference: getAxisLabel` 錯誤，要求不使用 `getAxisLabel` 覆寫，改用預設標籤，並確保導入 `com.github.mikephil.charting.components.AxisBase`。這通常意味著在設置 `XAxis` 或 `YAxis` 的 `valueFormatter` 時，嘗試以不正確的方式覆寫了該方法。

解決方案是**移除任何自定義的 `ValueFormatter` 設置**，讓 MPAndroidChart 使用其內建的數值標籤格式化功能，並確保所有必要的庫都已導入。

以下是修正後的 `MainActivity.kt` 完整程式碼。我將確保所有相關的庫（Gson, OkHttp, MPAndroidChart）都正確導入，並且不會有 `getAxisLabel` 的自定義覆寫。

**MainActivity.kt**

```kotlin
package com.example.yourprojectname // 請將 'yourprojectname' 替換為你的實際專案名稱

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase // 確保導入 AxisBase
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
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

class MainActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private val client = OkHttpClient()
    private val gson = Gson()

    // 替換為你的 API URL
    private val apiUrl = "https://api.example.com/data" // 請替換為你實際的 API 端點

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // 確保你已經有 activity_main.xml 佈局文件

        lineChart = findViewById(R.id.lineChart) // 確保你的佈局文件中包含一個 id 為 lineChart 的 LineChart

        setupChart()
        fetchData()
    }

    private fun setupChart() {
        lineChart.setBackgroundColor(Color.WHITE)
        lineChart.description.isEnabled = false // 禁用描述
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)

        // X 軸設置
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(true)
        xAxis.textColor = Color.BLACK
        xAxis.textSize = 10f
        // !!! 重點：移除或不設置自定義的 valueFormatter 以使用預設標籤 !!!
        // 如果之前這裡有類似 xAxis.valueFormatter = object : ValueFormatter() { ... } 的代碼，請移除。
        // 現在它將使用MPAndroidChart的預設數值標籤。
        // 如果你仍需要日期格式，且不想手動計算，可以考慮使用 IndexAxisValueFormatter，但它與 getAxisLabel 錯誤無關

        // 左 Y 軸設置
        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.setDrawZeroLine(false)
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.axisMinimum = 0f // 設置最小 Y 值為 0

        // 右 Y 軸設置
        val rightAxis = lineChart.axisRight
        rightAxis.isEnabled = false // 禁用右 Y 軸

        // 圖例設置
        val legend = lineChart.legend
        legend.isEnabled = true
        legend.textColor = Color.BLACK
        legend.textSize = 12f

        // 動畫
        lineChart.animateX(1500)
    }

    private fun fetchData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(apiUrl).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val apiResponse = gson.fromJson(json, ApiResponse::class.java)

                    withContext(Dispatchers.Main) {
                        if (apiResponse.data.isNotEmpty()) {
                            updateChart(apiResponse.data)
                        } else {
                            Toast.makeText(this@MainActivity, "No data received", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val errorMessage = "API call failed: ${response.code} - ${response.message}"
                    Log.e("MainActivity", errorMessage)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("MainActivity", "Network error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "An unexpected error occurred: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "An error occurred: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateChart(data: List<SensorData>) {
        val entries = ArrayList<Entry>()
        // 假設你的 API 返回的 timestamp 是 UNIX 時間戳 (秒或毫秒)
        // 這裡我們假設它是一個 float 值，適合直接放入 Entry
        // 如果 timestamp 是 Long，你需要將其轉換為 float
        // 如果需要顯示日期時間，通常需要一個自定義的 IAxisValueFormatter，
        // 但為了避免 getAxisLabel 錯誤，我們現在不設置它，只顯示數值。

        for ((index, item) in data.withIndex()) {
            // 將時間戳作為 X 值，實際數值作為 Y 值
            // 注意：LineChart 的 X 軸需要遞增的 float 值
            // 如果你的 timestamp 是實際時間戳，你可能需要將其正規化，或使用 IndexAxisValueFormatter
            // 這裡為了簡單起見，直接使用索引作為 X 值，或者假設 item.timestamp 已經是適合圖表顯示的 float
            entries.add(Entry(item.timestamp.toFloat(), item.value.toFloat())) // 假設 timestamp 和 value 都是 Float 或可以轉為 Float
            // 如果 timestamp 是 Long (毫秒)，通常會這樣做，但這會讓 X 軸數值很大，需要 IAxisValueFormatter
            // entries.add(Entry(item.timestamp.toFloat(), item.value.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Sensor Data")
        dataSet.color = Color.BLUE
        dataSet.setCircleColor(Color.BLUE)
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 3f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextSize = 0f // 隱藏數值標籤在點上，如果需要顯示，設置大於0的值

        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.invalidate() // 重新繪製圖表
    }

    // API 響應的數據模型
    data class ApiResponse(
        @SerializedName("data")
        val data: List<SensorData>
    )

    data class SensorData(
        @SerializedName("timestamp")
        val timestamp: Long, // 假設是 UNIX 時間戳 (秒或毫秒)
        @SerializedName("value")
        val value: Double
    )
}
```

**重要的修正點：**

1.  **`AxisBase` 導入：** `import com.github.mikephil.charting.components.AxisBase` 已被明確導入。
2.  **移除 `ValueFormatter` 覆寫：** 在 `setupChart()` 方法中，我已**故意沒有為 `xAxis.valueFormatter` 或 `lineChart.axisLeft.valueFormatter` 設置任何自定義的 `ValueFormatter`。**這將導致圖表使用 MPAndroidChart 內建的預設數值格式來顯示 X 軸和 Y 軸的標籤，從而避免了 `getAxisLabel` 相關的編譯錯誤。
3.  **`MainActivity.kt` 完整性：** 提供了完整的 `MainActivity.kt` 檔案，包含了 `OkHttp` 和 `Gson` 的導入及使用。
4.  **`AndroidManifest.xml` (重要提醒):** 確保你在 `AndroidManifest.xml` 中添加了網絡權限：
    ```xml
    <uses-permission android:name="android.permission.INTERNET" />
    ```
5.  **`build.gradle (Module: app)` (重要提醒):** 確保你的 `build.gradle (Module: app)` 文件中包含了以下依賴：
    ```gradle
    plugins {
        id 'com.android.application'
        id 'org.jetbrains.kotlin.android'
    }

    android {
        // ... (其他配置)
    }

    dependencies {
        // Standard libraries
        implementation 'androidx.core:core-ktx:1.10.1'
        implementation 'androidx.appcompat:appcompat:1.6.1'
        implementation 'com.google.android.material:material:1.9.0'
        implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

        // Kotlin Coroutines
        implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
        implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
        implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.1' // For lifecycleScope

        // OkHttp
        implementation 'com.squareup.okhttp3:okhttp:4.11.0'

        // Gson
        implementation 'com.google.code.gson:gson:2.10.1'

        // MPAndroidChart
        implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0' // 或者你當前使用的最新版本

        // Test implementations
        testImplementation 'junit:junit:4.13.2'
        androidTestImplementation 'androidx.test.ext:junit:1.1.5'
        androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    }
    ```
6.  **佈局文件 `activity_main.xml` (重要提醒):** 確保你有名為 `activity_main.xml` 的佈局文件，其中包含一個 `LineChart` 元件，且其 `id` 為 `lineChart`：
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <androidx.constraintlayout.widget.ConstraintLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        xmlns:tools="http://schemas.android.com/tools"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        tools:context=".MainActivity">

        <com.github.mikephil.charting.charts.LineChart
            android:id="@+id/lineChart"
            android:layout_width="0dp"
            android:layout_height="0dp"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

    </androidx.constraintlayout.widget.ConstraintLayout>
    ```

請務必將 `com.example.yourprojectname` 替換為你的實際專案包名，並將 `apiUrl` 替換為你實際的 API 端點。