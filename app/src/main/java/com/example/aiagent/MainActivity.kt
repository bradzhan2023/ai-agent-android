package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import org.json.JSONObject

// 自定義顏色
val DeepBlue = Color(0xFF000080) // 深藍色
val Gold = Color(0xFFFFD700) // 金色

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // 使用 Surface 作為應用程式的背景容器
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background // 使用主題的背景色，或直接移除
                ) {
                    GoldPriceTrackerApp()
                }
            }
        }
    }
}

@Composable
fun GoldPriceTrackerApp() {
    // 狀態變量用於儲存黃金價格和錯誤信息
    val goldPrice = remember { mutableStateOf("正在抓取 PAXG 價格...") }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    
    // 創建一個 OkHttpClient 實例，並使用 remember 確保它在重組時保持不變
    val okHttpClient = remember { OkHttpClient() }
    val API_URL = "https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT"

    // LaunchedEffect 用於在 CoroutineScope 中執行網路請求和定期更新
    LaunchedEffect(Unit) {
        while (isActive) { // 保持循環直到 Composable 離開作用域
            try {
                // 構建網路請求
                val request = Request.Builder()
                    .url(API_URL)
                    .build()

                // 執行網路請求
                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let {
                        // 解析 JSON 響應
                        val jsonObject = JSONObject(it)
                        val price = jsonObject.getString("price")
                        goldPrice.value = "PAXG: $price USDT"
                        errorMessage.value = null // 清除任何先前的錯誤信息
                    } ?: run {
                        errorMessage.value = "接收到空響應體"
                    }
                } else {
                    errorMessage.value = "錯誤: ${response.code} ${response.message}"
                }
            } catch (e: IOException) {
                // 處理網路相關錯誤 (例如，無網路連接)
                errorMessage.value = "網路錯誤: ${e.localizedMessage}"
                e.printStackTrace()
            } catch (e: Exception) {
                // 處理其他意外錯誤 (例如，JSON 解析錯誤)
                errorMessage.value = "發生意外錯誤: ${e.localizedMessage}"
                e.printStackTrace()
            }
            delay(30_000L) // 每 30 秒更新一次
        }
    }

    // UI 佈局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlue), // 應用深藍色背景
        verticalArrangement = Arrangement.Center, // 垂直居中
        horizontalAlignment = Alignment.CenterHorizontally // 水平居中
    ) {
        Text(
            text = goldPrice.value,
            color = Gold, // 價格文字使用金色
            fontSize = 48.sp, // 大字顯示
            fontWeight = FontWeight.Bold, // 粗體
            modifier = Modifier.padding(16.dp)
        )
        // 如果有錯誤信息，則顯示
        errorMessage.value?.let { message ->
            Text(
                text = "錯誤: $message",
                color = Color.Red,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}