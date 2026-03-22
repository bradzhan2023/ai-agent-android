package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.aiagent.ui.theme.AiAgentTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BinancePriceScreen()
                }
            }
        }
    }
}

@Composable
fun BinancePriceScreen() {
    var price by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val lifecycleScope = rememberCoroutineScope() // Use rememberCoroutineScope for composable scope

    // Trigger data fetch when the composable enters the composition
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        price = null
        lifecycleScope.launch {
            try {
                val fetchedPrice = fetchBinancePaxgPrice()
                price = fetchedPrice
            } catch (e: IOException) {
                error = "網路錯誤: ${e.message}"
                e.printStackTrace()
            } catch (e: Exception) {
                error = "發生錯誤: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            Text("正在加載...")
        } else if (error != null) {
            Text("錯誤: $error", color = MaterialTheme.colorScheme.error)
        } else if (price != null) {
            Text("PAXG/USDT 價格: $price", style = MaterialTheme.typography.headlineMedium)
        } else {
            Text("未獲取到價格資訊")
        }
    }
}

suspend fun fetchBinancePaxgPrice(): String? {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    // Binance API returns {"symbol":"PAXGUSDT","price":"2350.20000000"}
                    val jsonObject = JSONObject(responseBody)
                    // The prompt asked for {'price':'123.4'} structure, assuming the actual Binance
                    // response is handled to extract "price" field.
                    return@withContext jsonObject.optString("price", "N/A")
                }
            }
            throw IOException("請求失敗: ${response.code} ${response.message}")
        } catch (e: Exception) {
            throw IOException("網路請求錯誤或解析失敗", e)
        }
    }
}

// rememberCoroutineScope() import workaround for the provided structure.
// This is typically managed by an Activity/ViewModel's lifecycleScope directly or using rememberCoroutineScope()
// within a Composable. For pure Composable function examples, rememberCoroutineScope is appropriate.
@Composable
fun rememberCoroutineScope() = remember {
    // This is a simplified way to get a CoroutineScope tied to the composable's lifecycle.
    // In a real app, you might use an AndroidViewModel.viewModelScope or
    // rememberCoroutineScope() provided by androidx.compose.runtime.
    // For this specific example to meet the "import all" rule and avoid direct Activity lifecycle coupling in a Composable function,
    // we'll explicitly use the runtime's rememberCoroutineScope.
    androidx.compose.runtime.rememberCoroutineScope()
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AiAgentTheme {
        BinancePriceScreen()
    }
}