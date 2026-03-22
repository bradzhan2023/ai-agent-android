# 💎 Binance PAXG 價格抓取器 (Android)

這個 Android 應用程式展示了如何使用 Kotlin Coroutines、`Dispatchers.IO` 和 Retrofit 從 Binance API 抓取 PAXG/USDT 的即時價格，並將結果顯示在畫面上。它特別處理了僅包含 `{'price':'123.4'}` 格式的 JSON 回傳。

## ✨ 特點

*   **Kotlin Coroutines:** 使用協程進行非同步操作，提高程式碼可讀性與維護性。
*   **`Dispatchers.IO`:** 網路請求在專門為 I/O 操作設計的執行緒池上執行，避免阻塞主執行緒。
*   **Retrofit:** 類型安全的 HTTP 客戶端，簡化 API 呼叫。
*   **Gson:** 用於將 JSON 回應解析成 Kotlin 資料物件。
*   **簡潔的 UI 更新:** 使用 `withContext(Dispatchers.Main)` 安全地更新 UI。

## 🚀 環境要求

*   Android Studio (Flamingo 或更高版本建議)
*   Kotlin 1.8.0 或更高版本
*   Gradle 8.0 或更高版本
*   一部運行 Android 5.0 (API level 21) 或更高版本的設備/模擬器

## 🛠️ 安裝與設置

請遵循以下步驟來設置並運行這個專案：

### 1. 建立新的 Android 專案

在 Android Studio 中建立一個新的「Empty Activity」專案。

### 2. 添加依賴 (Dependencies)

打開你的模組級別 `build.gradle (app)` 檔案，並在 `dependencies` 區塊中添加以下依賴：

```gradle
dependencies {
    // Kotlin Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

    // Retrofit
    implementation "com.squareup.retrofit2:retrofit:2.9.0"
    // Gson Converter (用於 JSON 解析)
    implementation "com.squareup.retrofit2:converter-gson:2.9.0"

    // AndroidX Lifecycle for lifecycleScope
    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.6.2"

    // UI
    implementation "androidx.core:core-ktx:1.12.0"
    implementation "androidx.appcompat:appcompat:1.6.1"
    implementation "com.google.android.material:material:1.10.0"
    implementation "androidx.constraintlayout:constraintlayout:2.1.4"
}
```

記得點擊 Android Studio 右上角的 "Sync Now" 來同步 Gradle 檔案。

### 3. 添加網路權限

在你的 `AndroidManifest.xml` 檔案中，緊鄰 `<application>` 標籤上方添加 INTERNET 權限：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/full_backup_content"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.YourProjectName"
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

## 💻 程式碼說明

以下是實現功能的關鍵程式碼片段。

### 1. UI 佈局 (`activity_main.xml`)

我們需要一個 `TextView` 來顯示價格。

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
        android:text="載入中..."
        android:textSize="28sp"
        android:textStyle="bold"
        android:padding="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### 2. 資料模型 (`PriceResponse.kt`)

根據要求，我們需要解析 `{'price':'123.4'}` 格式的 JSON。
**注意:** 實際的 Binance API 回傳會包含 `symbol` 和其他欄位，但我們這裡只專注於符合要求的 `price` 欄位。

```kotlin
package com.example.binancepaxgprice // 替換為你的套件名稱

import com.google.gson.annotations.SerializedName

data class PriceResponse(
    @SerializedName("price")
    val price: String // 價格通常以字串形式從 API 回傳，方便處理小數點精度
)
```

### 3. API 服務接口 (`BinanceApiService.kt`)

使用 Retrofit 定義一個接口來發送網路請求。

```kotlin
package com.example.binancepaxgprice // 替換為你的套件名稱

import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApiService {
    @GET("api/v3/ticker/price")
    suspend fun getPaxgPrice(
        @Query("symbol") symbol: String = "PAXGUSDT" // 指定交易對為 PAXGUSDT
    ): PriceResponse
}
```

### 4. 主要活動 (`MainActivity.kt`)

這是所有邏輯匯集的地方。我們將在 `onCreate` 中初始化 Retrofit，並在一個 `lifecycleScope` 的協程中呼叫 API。

```kotlin
package com.example.binancepaxgprice // 替換為你的套件名稱

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope // 用於啟動綁定生命週期的協程
import kotlinx.coroutines.Dispatchers // 導入 Coroutines Dispatchers
import kotlinx.coroutines.launch // 用於啟動協程
import kotlinx.coroutines.withContext // 用於切換協程上下文
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // 用於 Gson JSON 轉換器
import java.lang.Exception // 處理可能的異常

class MainActivity : AppCompatActivity() {

    private lateinit var priceTextView: TextView
    private lateinit var binanceApiService: BinanceApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 UI 元件
        priceTextView = findViewById(R.id.priceTextView)

        // 建立 Retrofit 實例
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.binance.com/") // Binance API 的基礎 URL
            .addConverterFactory(GsonConverterFactory.create()) // 添加 Gson 轉換器
            .build()

        // 建立 BinanceApiService 實例
        binanceApiService = retrofit.create(BinanceApiService::class.java)

        // 立即抓取 PAXG 價格
        fetchPaxgPrice()
    }

    /**
     * 異步抓取 Binance PAXG 價格並更新 UI。
     * 網路操作在 Dispatchers.IO 上執行，UI 更新在 Dispatchers.Main 上執行。
     */
    private fun fetchPaxgPrice() {
        // 使用 lifecycleScope 啟動一個協程，該協程會綁定到 Activity 的生命週期
        // 當 Activity 被銷毀時，協程也會被取消。
        // Dispatchers.IO 適用於磁碟或網路 I/O 操作。
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 呼叫 API 獲取 PAXGUSDT 的價格
                val response = binanceApiService.getPaxgPrice("PAXGUSDT")
                val price = response.price // 從回應中提取價格字串

                // 將協程切換到主執行緒 (Main Dispatcher) 來更新 UI
                withContext(Dispatchers.Main) {
                    priceTextView.text = "PAXG 價格: $${price}"
                }
            } catch (e: Exception) {
                // 如果發生錯誤，切換到主執行緒顯示錯誤訊息
                withContext(Dispatchers.Main) {
                    priceTextView.text = "載入失敗: ${e.localizedMessage}"
                }
                e.printStackTrace() // 將錯誤堆棧列印到 Logcat
            }
        }
    }
}
```

## 運行應用程式

1.  在 Android Studio 中選擇你的設備或模擬器。
2.  點擊運行按鈕 (綠色三角形)。

應用程式啟動後，你應該會看到 PAXG/USDT 的即時價格顯示在畫面上。如果網路連接有問題或 API 返回錯誤，則會顯示相應的錯誤訊息。

## 🎯 Binance API 端點

*   **基礎 URL:** `https://api.binance.com/`
*   **PAXG 價格端點:** `GET /api/v3/ticker/price?symbol=PAXGUSDT`
*   **回應範例 (簡化後符合要求):**
    ```json
    {
        "price": "2345.678"
    }
    ```
    (實際 Binance API 回應會包含 `symbol` 和 `time` 等更多欄位，但此範例僅提取 `price` 欄位以符合請求解析 `{'price':'123.4'}` 的要求)

## 📜 許可證

這個專案是根據 MIT 許可證發布的。詳情請參閱 `LICENSE` 檔案 (如果有的話)。