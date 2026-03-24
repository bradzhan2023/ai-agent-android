好的，這是一個為您提供的 `README.md` 內容，用於說明您描述的應用程式更新功能：

---

# Binance PAXGUSDT 24小時價格趨勢分析 App

## 簡介

此應用程式為一個 Android 專案，旨在即時追蹤並視覺化 PAXGUSDT (PAX Gold / Tether) 交易對在 Binance 交易所的最新 24 小時價格趨勢。它會從 Binance API 抓取歷史 K 線數據，計算期間內的漲跌幅，並以直觀的折線圖 (LineChart) 呈現，幫助使用者快速了解該資產的短期表現。

## 更新說明

本次更新主要引入了以下核心功能，旨在提供用戶更即時、更直觀的資產價格分析工具：

## 功能特色

*   **實時數據獲取**：
    *   使用 `OkHttp` 庫高效地從 Binance API (具體端點：`https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1h&limit=24`) 抓取 PAXGUSDT 過去 24 小時（每小時一個 K 線數據）的價格數據。
    *   **IO 執行緒操作**：確保所有網路請求都在獨立的 IO 執行緒中執行，避免阻塞主執行緒，從而保證流暢的 UI 體驗。

*   **漲跌幅計算**：
    *   根據獲取的 24 小時 K 線數據，自動計算該時間段內的總體漲跌幅（通常為最新收盤價相較於最早開盤價的百分比變化）。

*   **折線圖視覺化**：
    *   利用強大的 LineChart 庫，將過去 24 小時 PAXGUSDT 的收盤價格繪製成清晰的折線圖。
    *   圖表直觀地展示價格波動，讓使用者一目瞭然地看出價格趨勢。

## 技術棧

*   **開發語言**: Kotlin
*   **Android SDK**: 最低支援 API Level XX (請根據您的專案設定填寫)
*   **網路請求**: [OkHttp](https://square.github.io/okhttp/)
*   **JSON 解析**: 例如 `Gson` 或 `Moshi`
*   **圖表庫**: 例如 [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) 或其他 LineChart 庫

## 執行流程

1.  **啟動與請求**: 應用程式啟動後，會立即在後台（IO 執行緒）觸發對 Binance API 的數據請求。
2.  **數據解析**: 接收到 API 的 JSON 響應後，應用程式會解析其中的 K 線數據，提取出每個小時的開盤價、收盤價等關鍵資訊。
3.  **漲跌幅計算**:
    *   從解析後的數據中，獲取第一個 K 線的開盤價 (Open Price) 和最後一個 K 線的收盤價 (Close Price)。
    *   漲跌幅 = `((最後收盤價 - 最早開盤價) / 最早開盤價) * 100%`。
4.  **圖表繪製**: 將 24 個小時的收盤價數據點傳遞給 LineChart 庫，在主執行緒上繪製價格曲線。同時，將計算出的漲跌幅顯示在 UI 上。
5.  **UI 更新**: 所有數據處理和圖表更新都在後台完成後，安全地將結果更新到用戶界面。

## 螢幕截圖

應用程式啟動後，將會顯示 PAXGUSDT 過去 24 小時的價格趨勢折線圖，並在圖表上方或下方顯示計算出的漲跌幅。

![螢幕截圖](screenshot.png)
*(請在此處替換為您的應用程式實際截圖，展示 LineChart 和漲跌幅)*

## 如何運行

1.  **克隆此倉庫**:
    ```bash
    git clone [你的倉庫連結]
    cd [你的專案資料夾名稱]
    ```
2.  **使用 Android Studio 打開專案**:
    打開 Android Studio，選擇 `Open an existing Android Studio project` 並導航到您克隆的專案資料夾。
3.  **同步 Gradle**:
    等待 Android Studio 同步 Gradle 依賴。確保您的開發環境已安裝最新版本的 Kotlin 和 Android SDK。
4.  **運行應用程式**:
    連接您的 Android 設備或啟動模擬器，然後點擊 Android Studio 工具欄上的 'Run' 按鈕 (綠色三角形)。

## 未來增強

*   **用戶自定義交易對與時間週期**: 允許用戶選擇不同的交易對和 K 線時間週期。
*   **實時數據更新**: 整合 WebSocket 以實現價格的實時更新。
*   **錯誤處理與網路狀態**: 增強網路錯誤處理，並提供更友好的加載和錯誤提示。
*   **更多技術指標**: 加入移動平均線 (MA)、相對強弱指數 (RSI) 等常見技術指標。

## 貢獻

歡迎任何形式的貢獻！如果您有任何建議、錯誤報告或想提交新的功能，請隨時開立 Issue 或提交 Pull Request。

## 許可證

此專案採用 [請填寫您的許可證，例如 MIT License] 許可證。詳情請參閱 `LICENSE` 文件。

## 作者

[您的名字/開發者代號]

---