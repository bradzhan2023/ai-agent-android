# 📈 Simple Gold Price Tracker App for Android

✨ 歡迎來到一個簡單而有效的 Android 應用程式，用於追蹤即時黃金價格！這個專案旨在提供一個清晰、易於閱讀的介面，讓使用者可以快速查看當前的黃金價格，並提供一個方便的刷新按鈕來獲取最新數據。

## ✨ 功能特色

*   **大字體顯示黃金價格**: 將當前黃金價格以大字體顯示在螢幕中央，例如 `300sp` 或更大，確保一眼就能看清。
*   **刷新按鈕**: 一個位於介面底部的按鈕，點擊後會重新從 API 獲取最新的黃金價格。
*   **載入指示器**: 在資料獲取期間顯示一個小的進度條，提升使用者體驗。
*   **錯誤處理**: 當 API 呼叫失敗或無網路連接時，會顯示友善的錯誤訊息。
*   **簡潔的 UI/UX**: 專注於核心功能，提供一個無雜亂的介面。

## 📸 螢幕截圖 (概念圖)

```
+-------------------------------------+
| 💰 金價追蹤器                     |
|                                     |
|                                     |
|         $1,987.65                   |
|         (Large Text)                |
|                                     |
|                                     |
|         [ 🔄 刷新價格 ]             |
|                                     |
+-------------------------------------+
```

## 🛠️ 使用技術

*   **語言**: Kotlin (或 Java)
*   **架構**: Android Jetpack (Activity, ViewModel)
*   **網路請求**: Retrofit + OkHttp
*   **JSON 解析**: GSON (或 Moshi)
*   **非同步處理**: Kotlin Coroutines
*   **UI**: Material Design 元件 (TextView, Button, ProgressBar)

## 🚀 設定與安裝

1.  **複製專案**:
    ```bash
    git clone https://github.com/你的用戶名/SimpleGoldPriceTracker.git
    cd SimpleGoldPriceTracker
    ```

2.  **開啟 Android Studio**:
    在 Android Studio 中開啟這個專案。

3.  **取得 API Key**:
    這個應用程式需要一個外部 API 來獲取黃金價格。
    我們建議使用像 `metals-api.com` 或 `goldapi.io` 這樣的服務。請註冊一個免費帳戶 (通常有請求限制)，並獲取你的 API Key。

    > **推薦的 API (範例):** [metals-api.com](https://metals-api.com/)
    >
    > **API Endpoint 範例:**
    > `https://api.metals-api.com/v1/latest?access_key=YOUR_API_KEY&base=USD&symbols=XAU`
    > (這裡 `XAU` 是黃金的 ISO 貨幣代碼)

4.  **配置 API Key**:
    為了安全起見，請勿將 API Key 直接硬編碼在程式碼中。
    在專案的根目錄下創建一個 `local.properties` 檔案（如果它不存在的話），並添加你的 API Key：

    ```properties
    API_KEY="你的實際API_Key"
    ```

    然後在 `app/build.gradle` (Module: app) 中，你可以像這樣讀取它：

    ```gradle
    android {
        // ...
        defaultConfig {
            // ...
            buildConfigField "String", "API_KEY", project.properties["API_KEY"] ?: "\"YOUR_DEFAULT_API_KEY_IF_NOT_SET\""
        }
        // ...
    }
    ```
    現在你就可以在程式碼中透過 `BuildConfig.API_KEY` 來訪問它了。

5.  **執行應用程式**:
    連接你的 Android 設備或啟動模擬器，然後點擊 Android Studio 工具列上的 ▶️ Run 按鈕。

## 💡 使用方式

1.  啟動應用程式。
2.  首次啟動時，應用程式會自動從 API 獲取並顯示當前黃金價格。
3.  要更新價格，只需點擊螢幕底部的「刷新價格」按鈕。
4.  如果在載入過程中出現錯誤，會顯示相應的錯誤訊息。

## 📂 專案結構 (範例)

```
├── app
│   ├── build.gradle
│   └── src
│       └── main
│           ├── AndroidManifest.xml
│           ├── java
│           │   └── com
│           │       └── example
│           │           └── goldtracker
│           │               ├── MainActivity.kt                # 主要活動，顯示UI和處理使用者互動
│           │               ├── MainViewModel.kt               # 處理數據邏輯和狀態管理
│           │               ├── api
│           │               │   ├── GoldPriceResponse.kt       # API 回應的數據模型
│           │               │   └── MetalsApiService.kt        # Retrofit 服務介面
│           │               └── utils
│           │                   └── Resource.kt                # 封裝數據狀態 (Success, Loading, Error)
│           └── res
│               ├── drawable
│               ├── layout
│               │   └── activity_main.xml                      # 主要介面佈局
│               ├── mipmap
│               └── values
├── gradle
│   └── wrapper
├── build.gradle
└── local.properties                 # 存放你的 API Key
```

## 🤝 貢獻

歡迎任何形式的貢獻！如果你有改進的建議、發現了錯誤，或者想添加新功能，請隨時：

1.  Fork 本專案
2.  創建一個新的 Feature 分支 (`git checkout -b feature/AmazingFeature`)
3.  提交你的更改 (`git commit -m 'Add some AmazingFeature'`)
4.  推送到分支 (`git push origin feature/AmazingFeature`)
5.  開一個 Pull Request

## 📝 授權

此專案根據 MIT 授權條款發布。詳情請參閱 [LICENSE](LICENSE) 檔案。

---

**感謝您使用或關注此專案！**