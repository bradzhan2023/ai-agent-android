# PAXG 金價追蹤器：即時 Binance API 整合與 OkHttp

## 專案簡介

這是一個專為追蹤 PAX Gold (PAXG) 對 USDT 即時價格而設計的應用程式。它透過 OkHttp 請求 Binance API，每 30 秒自動更新一次價格，並以直觀、美觀的方式呈現：深藍色背景搭配醒目的金色大字顯示價格。此應用程式旨在提供一個簡單、高效且視覺友好的方式，讓使用者隨時掌握 PAXG 的最新市場動態。

## 功能特色

*   **即時 PAXG/USDT 價格追蹤**：從 Binance 獲取最新的 PAXG 對 USDT 交易價格。
*   **自動 UI 更新**：每 30 秒自動刷新一次價格數據並更新使用者介面，無需手動操作。
*   **Binance API 整合**：直接連接 Binance 的公開 API，確保數據的準確性和即時性。
*   **高效網路請求**：使用 OkHttp 作為 HTTP 客戶端，提供穩定且高效的網路通訊。
*   **獨特使用者介面**：
    *   **背景**：採用沉穩的深藍色，營造專業且舒適的視覺體驗。
    *   **價格顯示**：價格數值以大字金色字體顯示，極具辨識度，一眼即可掌握。

## 技術棧

*   **Kotlin (或 Java)**：應用程式主要開發語言。
*   **OkHttp**：強大且高效的 HTTP 客戶端，用於 API 請求。
*   **Binance API**：提供即時加密貨幣市場數據。
*   **Android SDK**：構建 Android 應用程式的工具與函式庫 (假設為 Android 應用)。

## API 端點

本專案使用以下 Binance API 端點來獲取 PAXG/USDT 的即時價格：

```
GET https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT
```

### 回應範例 (JSON):

```json
{
    "symbol": "PAXGUSDT",
    "price": "2350.50000000"
}
```

應用程式會解析 `price` 欄位來顯示最新的 PAXG 價格。

## 運行與安裝

### 1. 克隆儲存庫

首先，將本專案克隆到您的本地機器：

```bash
git clone [您的 GitHub 儲存庫 URL]
cd [您的專案目錄名稱]
```

### 2. 開啟專案

使用 Android Studio (或其他相容 IDE) 開啟克隆下來的專案。

### 3. 同步 Gradle

讓 IDE 同步所有依賴項。確保您的 `build.gradle` (Module: app) 中包含了 OkHttp 函式庫：

```gradle
dependencies {
    // ... 其他依賴
    implementation("com.squareup.okhttp3:okhttp:4.9.3") // 請使用最新穩定版本
    // ...
}
```

同時，別忘了在 `AndroidManifest.xml` 中添加網路權限：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.paxgtracker">

    <uses-permission android:name="android.permission.INTERNET" />
    <application
        <!-- ... -->
    </application>
</manifest>
```

### 4. 運行應用程式

選擇一個模擬器或實體 Android 設備，然後點擊 Android Studio 工具列上的運行按鈕 (綠色三角形)。應用程式將會部署並啟動。

## 使用方法

1.  啟動應用程式後，您將立即在螢幕中央看到 PAXG 對 USDT 的最新價格。
2.  價格會每 30 秒自動更新一次，無需手動操作。
3.  深藍色背景與大字金色價格的設計，讓您在任何時候都能輕鬆閱讀。

## 螢幕截圖

(此處可以插入應用程式的截圖)

*描述:* 想像一個深藍色的背景，中央以大約 48sp 的金色粗體字顯示當前價格，例如 "2350.50 USDT"。下方可能會有一個小字顯示上次更新時間。

## 貢獻

歡迎對此專案進行貢獻！如果您有任何建議、錯誤修復或新功能，請按照以下步驟操作：

1.  Fork 本專案。
2.  創建您的功能分支 (`git checkout -b feature/AmazingFeature`)。
3.  提交您的更改 (`git commit -m 'Add some AmazingFeature'`)。
4.  推送到分支 (`git push origin feature/AmazingFeature`)。
5.  開一個 Pull Request。

## 授權條款

本專案採用 MIT 授權條款 - 詳細資訊請參閱 [LICENSE](LICENSE) 文件。

## 作者

[您的名字或 GitHub 用戶名]
[您的聯絡方式，例如：您的網站、LinkedIn 或電子郵件]