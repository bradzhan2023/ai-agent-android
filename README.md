好的，這是一個為您設計的 `README.md` 文件，以及相關的 Python 實作程式碼。

---

# 🪙 PAXGUSDT 金價走勢追蹤器

這個小應用程式旨在提供 PAXG (黃金代幣) 對美元 (USDT) 在幣安交易所的即時價格和 24 小時價格走勢。

## ✨ 功能特色

1.  **即時價格顯示**：在頁面頂部以大字體顯示 PAXG/USDT 的當前美元價格。
2.  **24 小時 K 線數據**：從 Binance API 抓取過去 24 小時的 1 小時 K 線數據。
3.  **直觀趨勢圖**：下方嵌入 Streamlit 的 `line_chart`，清晰展示 24 小時內的價格走勢。
4.  **預設標籤**：圖表軸標籤使用 Streamlit 和 Pandas 的預設值，無需額外配置，確保簡潔和穩定運行。

## 🛠️ 技術棧

*   **Python 3.x**
*   **Streamlit**：用於快速構建網頁應用介面。
*   **python-binance**：用於方便地與 Binance API 交互。
*   **Pandas**：用於數據處理和結構化。

## 🚀 如何運行

### 1. 先決條件

請確保您的系統已安裝 Python 3.7+ 和 `pip`。

### 2. 安裝

首先，將本專案克隆到您的本地機器：

```bash
git clone https://github.com/<你的用戶名>/gold-price-tracker.git
cd gold-price-tracker
```

然後，創建一個虛擬環境（推薦做法）：

```bash
python -m venv venv
# 激活虛擬環境 (macOS/Linux)
source venv/bin/activate
# 激活虛擬環境 (Windows)
.\venv\Scripts\activate
```

安裝所需的 Python 函式庫：

```bash
pip install -r requirements.txt
```

### 3. 執行應用程式

激活虛擬環境後，運行 Streamlit 應用：

```bash
streamlit run gold_tracker.py
```

這將在您的瀏覽器中打開一個新的標籤頁，顯示應用程式介面。

## 📁 檔案結構

```
.
├── gold_tracker.py
├── requirements.txt
└── README.md
```

*   `gold_tracker.py`: 核心 Python 程式碼，負責抓取數據和構建 Streamlit 應用。
*   `requirements.txt`: 列出所有必要的 Python 函式庫。
*   `README.md`: 本說明文件。

## 📸 應用程式截圖 (預覽)

*(這裡通常會放一張運行時的應用程式截圖。目前我無法生成，但您可以運行後自行添加。)*

預期畫面：
*   頂部大字體顯示如 "Current PAXG Price: $XXXX.XX"
*   下方顯示一個帶有 "Time" (X軸) 和 "Price" (Y軸) 預設標籤的折線圖。

## 📜 程式碼 (`gold_tracker.py`)

請將以下內容保存為 `gold_tracker.py` 文件：

```python
import streamlit as st
import pandas as pd
from binance.client import Client
from binance.exceptions import BinanceAPIException
import datetime

# --- 配置 ---
# Binance API 公開數據不需要 API Key 和 Secret，可以直接初始化 Client
# 如果需要交易或私有數據，則需要填寫：
# api_key = "YOUR_BINANCE_API_KEY"
# api_secret = "YOUR_BINANCE_API_SECRET"
# client = Client(api_key, api_secret)
client = Client("", "") # 使用空字串以示範公開數據抓取

SYMBOL = 'PAXGUSDT'
INTERVAL = Client.KLINE_INTERVAL_1HOUR # 1 小時 K 線
LIMIT = 24 # 抓取最近 24 條 K 線 (即過去 24 小時)

# --- 函數：抓取數據 ---
@st.cache_data(ttl=60) # 緩存數據 60 秒，避免頻繁請求 API
def fetch_klines_data(symbol, interval, limit):
    try:
        klines = client.get_klines(symbol=symbol, interval=interval, limit=limit)
        
        # klines 數據格式:
        # [
        #   [
        #     1499040000000,      # 開盤時間
        #     "0.01634790",       # 開盤價
        #     "0.80000000",       # 最高價
        #     "0.01575800",       # 最低價
        #     "0.01577100",       # 收盤價 (我們需要的)
        #     "148976.10704000",  # 成交量
        #     1499644799999,      # 收盤時間
        #     "2434.19055334",    # 成交額
        #     308,                # 成交筆數
        #     "1756.87402000",    # 主動買入成交量
        #     "28.46694368",      # 主動買入成交額
        #     "0"                 # 忽略
        #   ]
        # ]

        df = pd.DataFrame(klines, columns=[
            'open_time', 'open', 'high', 'low', 'close', 'volume', 
            'close_time', 'quote_asset_volume', 'number_of_trades', 
            'taker_buy_base_asset_volume', 'taker_buy_quote_asset_volume', 'ignore'
        ])
        
        # 轉換數據類型
        df['close'] = pd.to_numeric(df['close'])
        # 將 Unix 時間戳轉換為可讀的 datetime 對象
        df['open_time'] = pd.to_datetime(df['open_time'], unit='ms')
        
        # 只保留我們需要的列
        df = df[['open_time', 'close']]
        df.rename(columns={'open_time': 'Time', 'close': 'Price'}, inplace=True)
        
        return df

    except BinanceAPIException as e:
        st.error(f"Binance API 錯誤: {e}")
        return pd.DataFrame() # 返回空 DataFrame
    except Exception as e:
        st.error(f"獲取數據時發生錯誤: {e}")
        return pd.DataFrame()

# --- Streamlit 應用介面 ---
st.set_page_config(
    page_title="PAXGUSDT 金價走勢",
    page_icon="🪙",
    layout="centered",
    initial_sidebar_state="auto"
)

st.title("🪙 PAXG/USDT 金價走勢")

data_df = fetch_klines_data(SYMBOL, INTERVAL, LIMIT)

if not data_df.empty:
    # 顯示當前價格 (最後一條 K 線的收盤價)
    current_price = data_df['Price'].iloc[-1]
    st.markdown(f"## 當前 PAXG 價格: :green[${current_price:.2f}]") # 使用 markdown 語法顯示大字體和顏色

    st.write("---") # 分隔線
    st.subheader(f"過去 {LIMIT} 小時 PAXG/USDT 價格走勢")

    # 繪製 Line Chart
    # Streamlit 會自動使用 DataFrame 的列名作為圖表的軸標籤
    st.line_chart(data_df, x='Time', y='Price')

    # 顯示數據表格 (可選，用於調試或查看詳細數據)
    # st.subheader("原始數據")
    # st.dataframe(data_df)

    st.caption(f"數據來源: Binance | 最後更新: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
else:
    st.warning("未能加載 PAXG/USDT 數據。請檢查網路連接或稍後重試。")

```