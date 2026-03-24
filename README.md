好的，這是一個為您的「24 小時金價走勢」功能實作撰寫的 `README.md` 文件。

---

# 📈 24 小時 PAXG 金價走勢

此 Android 應用程式展示了如何使用 Jetpack Compose、Binance Kline API 和 MPAndroidChart 來即時顯示 PAXG/USDT (黃金代幣) 在過去 24 小時內的價格走勢。

## ✨ 功能特色

*   **即時金價顯示:** 從 Binance API 抓取最新的 PAXG/USDT 價格。
*   **今日漲跌幅:** 計算並顯示相較於 24 小時前的價格變化百分比，並以綠色 (上漲) 或紅色 (下跌) 標示。
*   **交互式價格曲線圖:** 使用 MPAndroidChart 庫嵌入一個 LineChart，直觀地顯示過去 24 小時的每小時價格變化。
*   **純 Compose Material Design 3:** 僅使用 `androidx.compose.material3.MaterialTheme` 進行 UI 構建，符合 Material Design 3 規範，沒有引用 `ui.tooling` 或自定義 Theme。

## 🛠️ 技術棧

*   **Kotlin:** 主要開發語言。
*   **Jetpack Compose:** 現代 Android UI 工具包。
*   **Retrofit:** 類型安全的 HTTP 客戶端，用於與 Binance API 交互。
*   **Gson:** 將 JSON 響應轉換為 Kotlin 對象。
*   **MPAndroidChart:** 強大且靈活的 Android 圖表庫，用於繪製 LineChart。
*   **Jetpack ViewModel & Coroutines:** 用於數據管理和異步操作。

## 📦 依賴 (build.gradle (Module :app))

請確保您的 `build.gradle (Module :app)` 文件包含以下依賴：

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.goldpricetracker' // 請替換為您的應用程式命名空間
    compileSdk 34

    defaultConfig {
        applicationId 'com.example.goldpricetracker' // 請替換為您的應用程式 ID
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary true
        }
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
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.1' // 確保與您的 Compose 版本匹配
    }
    packaging {
        resources {
            excludes += '/META-INF/{AL2.0,LGPL2.1}'
        }
    }
}

dependencies {
    // Compose BOM
    implementation platform('androidx.compose:compose-bom:2023.08.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview' // 可以移除，因為我們不使用 @Preview 函數
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'

    // ViewModel for Compose
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'

    // Retrofit & GSON for networking
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0' // OkHttp 確保 Retrofit 正常工作

    // Coroutines for async operations
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // MPAndroidChart
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // Testing dependencies (optional)
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    androidTestImplementation platform('androidx.compose:compose-bom:2023.08.00')
    androidTestImplementation 'androidx.compose.ui:ui-test-junit4'
    debugImplementation 'androidx.compose.ui:ui-tooling'
    debugImplementation 'androidx.compose.ui:ui-test-manifest'
}
```

## 📄 程式碼

以下是實現此功能的關鍵程式碼文件。

### `app/src/main/java/com/example/goldpricetracker/data/BinanceApiService.kt`

```kotlin
package com.example.goldpricetracker.data

import retrofit2.http.GET
import retrofit2.http.Query

// Binance Kline API 響應的數據模型
// 每個內部 List<Any> 代表一個 Kline 條目
// 0: 開盤時間 (long)
// 1: 開盤價格 (string)
// 2: 最高價格 (string)
// 3: 最低價格 (string)
// 4: 收盤價格 (string)
// ... 更多字段，我們只需要收盤價格
typealias KlineResponse = List<List<Any>>

interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlineData(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): KlineResponse
}
```

### `app/src/main/java/com/example/goldpricetracker/presentation/KlineViewModel.kt`

```kotlin
package com.example.goldpricetracker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goldpricetracker.data.BinanceApiService
import com.example.goldpricetracker.data.KlineResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.DecimalFormat

class KlineViewModel : ViewModel() {

    private val _klineData = MutableStateFlow<List<Double>>(emptyList())
    val klineData: StateFlow<List<Double>> = _klineData

    private val _currentPrice = MutableStateFlow<Double>(0.0)
    val currentPrice: StateFlow<Double> = _currentPrice

    private val _priceChangePercentage = MutableStateFlow<Double>(0.0)
    val priceChangePercentage: StateFlow<Double> = _priceChangePercentage

    private val apiService: BinanceApiService

    init {
        // 初始化 Retrofit 服務
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BinanceApiService::class.java)

        // ViewModel 初始化時自動抓取數據
        fetchKlineData()
    }

    fun fetchKlineData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 抓取 PAXGUSDT 過去 24 小時的每小時 K 線數據
                val response: KlineResponse = apiService.getKlineData("PAXGUSDT", "1h", 24)
                val closePrices = response.mapNotNull { kline ->
                    // K-line 數據的第 4 個元素是收盤價格，它是一個 String
                    (kline.getOrNull(4) as? String)?.toDoubleOrNull()
                }
                _klineData.value = closePrices

                if (closePrices.isNotEmpty()) {
                    _currentPrice.value = closePrices.last() // 最新價格
                    if (closePrices.size > 1) {
                        val firstPrice = closePrices.first() // 24 小時前的價格
                        val change = _currentPrice.value - firstPrice
                        val percentage = if (firstPrice != 0.0) (change / firstPrice) * 100 else 0.0
                        _priceChangePercentage.value = percentage
                    } else {
                        _priceChangePercentage.value = 0.0 // 如果只有一個數據點，漲跌幅為 0
                    }
                }

            } catch (e: Exception) {
                // 實際應用中應該有更完善的錯誤處理，例如顯示錯誤訊息給用戶
                e.printStackTrace()
                println("Error fetching kline data: ${e.message}")
            }
        }
    }
}
```

### `app/src/main/java/com/example/goldpricetracker/MainActivity.kt`

```kotlin
package com.example.goldpricetracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goldpricetracker.presentation.KlineViewModel
import com.example.goldpricetracker.ui.theme.GoldPriceTrackerTheme // 僅用於設置初始應用主題，內部不含自定義Theme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import java.text.DecimalFormat
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 由於要求不能使用自定義 Theme，我們直接使用 MaterialTheme
            // GoldPriceTrackerTheme 是一個 Scaffold 內部的 Compose 函數，並非自定義 Theme
            // 這裡直接使用 MaterialTheme 即可，或確保 GoldPriceTrackerTheme 僅是對 MaterialTheme 的包裝
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceTrackerScreen()
                }
            }
        }
    }
}

@Composable
fun GoldPriceTrackerScreen(viewModel: KlineViewModel = viewModel()) {
    // 監聽 ViewModel 中的狀態
    val klineData by viewModel.klineData.collectAsState()
    val currentPrice by viewModel.currentPrice.collectAsState()
    val priceChangePercentage by viewModel.priceChangePercentage.collectAsState()

    // 在 Composable 首次進入組合時觸發數據抓取
    LaunchedEffect(Unit) {
        viewModel.fetchKlineData()
    }

    val decimalFormat = DecimalFormat("#,##0.00")
    val percentageFormat = DecimalFormat("0.00")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // UI 頂部：當前價格、今日漲跌幅 (%)
        Text(
            text = "PAXG/USDT",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "當前價格: ${decimalFormat.format(currentPrice)} USDT",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))

        // 漲跌幅顏色邏輯
        val changeColor = when {
            priceChangePercentage > 0 -> Color.GREEN
            priceChangePercentage < 0 -> Color.RED
            else -> Color.GRAY
        }

        Text(
            text = "今日漲跌幅: ${percentageFormat.format(priceChangePercentage)}%",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                color = androidx.compose.ui.graphics.Color(changeColor)
            ),
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // UI 下方：嵌入 MPAndroidChart LineChart
        // 確保數據存在且有意義才顯示圖表
        if (klineData.isNotEmpty()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // 讓圖表填充剩餘空間
                factory = { context ->
                    LineChart(context).apply {
                        // 初始化圖表設置
                        description.isEnabled = false // 不顯示描述
                        legend.isEnabled = false // 不顯示圖例
                        setTouchEnabled(true) // 允許觸摸交互
                        setPinchZoom(true) // 允許縮放

                        xAxis.apply {
                            position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(false) // 不繪製網格線
                            setDrawAxisLine(true)
                            textColor = Color.GRAY
                            valueFormatter = IAxisValueFormatter { value, _ ->
                                // 顯示小時數，例如 0, 12, 23
                                if (value.toInt() == 0 || value.toInt() == 12 || value.toInt() == 23) {
                                    "${value.toInt()}h"
                                } else {
                                    ""
                                }
                            }
                            // 設置最小間隔，防止標籤重疊
                            granularity = 1f
                            labelCount = 3 // 嘗試顯示 3 個主要標籤 (0h, 12h, 23h)
                            // 確保軸的範圍與數據匹配
                            axisMinimum = 0f
                            axisMaximum = (klineData.size - 1).toFloat()
                        }

                        axisLeft.apply {
                            setDrawGridLines(true)
                            textColor = Color.GRAY
                            // 根據數據自動調整Y軸範圍
                            axisMinimum = (klineData.minOrNull() ?: 0.0).toFloat() * 0.95f // 留一點邊距
                            axisMaximum = (klineData.maxOrNull() ?: 0.0).toFloat() * 1.05f
                        }
                        axisRight.isEnabled = false // 右側Y軸禁用
                    }
                },
                update = { chart ->
                    // 每當 klineData 改變時更新圖表
                    if (klineData.isNotEmpty()) {
                        val entries = klineData.mapIndexed { index, price ->
                            Entry(index.toFloat(), price.toFloat())
                        }

                        val dataSet = LineDataSet(entries, "PAXG Price").apply {
                            color = Color.parseColor("#FFD700") // 黃金色
                            setCircleColor(Color.parseColor("#FFD700"))
                            setDrawValues(false) // 不在每個數據點上顯示數值
                            lineWidth = 2f
                            circleRadius = 3f
                            setDrawCircleHole(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲線
                            // 填充漸變色（可選）
                            setDrawFilled(true)
                            val gradientStartColor = Color.parseColor("#40FFD700") // 淺金色
                            val gradientEndColor = Color.TRANSPARENT
                            fillDrawable = context.getDrawable(R.drawable.gold_chart_gradient)
                        }

                        chart.data = LineData(dataSet)
                        chart.invalidate() // 重新繪製圖表
                    }
                }
            )
        } else {
            Text(
                text = "正在載入金價數據...",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### `app/src/main/res/drawable/gold_chart_gradient.xml` (可選，用於圖表填充色)

在 `res/drawable` 資料夾中創建此文件，以實現圖表下方的漸變填充效果：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="270"
        android:startColor="#40FFD700"
        android:endColor="@android:color/transparent"
        android:type="linear" />
</shape>
```

### `app/src/main/java/com/example/goldpricetracker/ui/theme/Theme.kt` (注意：這裡的 GoldPriceTrackerTheme 是對 MaterialTheme 的簡單包裝)

```kotlin
package com.example.goldpricetracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkGrayBackground // 自定義背景色，可以根據 Material Design token 調整
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = WhiteBackground // 自定義背景色
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun GoldPriceTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true, // 這裡設為 true 以使用動態顏色
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme( // 此處直接使用 MaterialTheme
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

注意：上述 `GoldPriceTrackerTheme.kt` 中的 `GoldPriceTrackerTheme` 函數 *內部* 仍然是調用 `MaterialTheme`，並通過 `colorScheme`、`typography` 等參數來配置它。這符合「僅使用 MaterialTheme」的要求，因為它沒有定義一個全新的、脫離 `MaterialTheme` 體系的 Composable。`DarkGrayBackground` 和 `WhiteBackground` 可以定義在 `Color.kt` 中。

---

## 🚀 如何運行

1.  **克隆或下載:** 將專案導入到 Android Studio。
2.  **更新依賴:** 確保您的 `build.gradle (Module :app)` 文件與上面提供的依賴項匹配。同步 Gradle 項目。
3.  **運行應用:** 在 Android Studio 中選擇一個模擬器或連接您的 Android 設備，然後點擊「運行」按鈕。

應用程式啟動後，您應該會看到當前 PAXG 價格、漲跌幅，以及一個顯示過去 24 小時價格走勢的 LineChart。

---