package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.JsonArray
import java.io.IOException
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PriceDisplayScreen()
                }
            }
        }
    }
}

@Composable
fun PriceDisplayScreen() {
    val currentPrice = remember { mutableStateOf<String?>(null) }
    val klineData = remember { mutableStateOf<List<List<String>>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val fetchedData = fetchBinanceKlineData("PAXGUSDT", "1h", 24)
            klineData.value = fetchedData
            if (fetchedData.isNotEmpty()) {
                currentPrice.value = String.format(Locale.US, "%.2f", fetchedData.last()[4].toFloat())
            }
        } catch (e: Exception) {
            errorMessage.value = "Failed to fetch data: ${e.localizedMessage}"
            e.printStackTrace()
        } finally {
            isLoading.value = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading.value) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading data...")
        } else if (errorMessage.value != null) {
            Text(errorMessage.value!!, color = MaterialTheme.colorScheme.error)
        } else {
            currentPrice.value?.let { price ->
                Text(
                    text = "Current Price: $price USD",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (klineData.value.isNotEmpty()) {
                LineChartComposable(klineData.value)
            } else {
                Text("No chart data available.")
            }
        }
    }
}

@Composable
fun LineChartComposable(klineData: List<List<String>>) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(false)

                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.setDrawLabels(true)
                xAxis.granularity = 1f
                xAxis.labelRotationAngle = 45f
                xAxis.axisMinimum = 0f

                axisLeft.setDrawGridLines(true)
                axisLeft.setDrawLabels(true)
                axisLeft.axisMinimum = klineData.minOf { it[4].toFloat() } * 0.95f
                axisLeft.axisMaximum = klineData.maxOf { it[4].toFloat() } * 1.05f

                axisRight.isEnabled = false

                legend.isEnabled = false

                val entries = klineData.mapIndexed { index, kline ->
                    Entry(index.toFloat(), kline[4].toFloat())
                }

                val dataSet = LineDataSet(entries, "Price").apply {
                    color = android.graphics.Color.BLUE
                    setCircleColor(android.graphics.Color.BLUE)
                    circleRadius = 3f
                    lineWidth = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                }

                data = LineData(dataSet)
                invalidate()
            }
        },
        update = { chart ->
            val entries = klineData.mapIndexed { index, kline ->
                Entry(index.toFloat(), kline[4].toFloat())
            }
            val dataSet = LineDataSet(entries, "Price").apply {
                color = android.graphics.Color.BLUE
                setCircleColor(android.graphics.Color.BLUE)
                circleRadius = 3f
                lineWidth = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
            }

            chart.data = LineData(dataSet)
            chart.axisLeft.axisMinimum = klineData.minOf { it[4].toFloat() } * 0.95f
            chart.axisLeft.axisMaximum = klineData.maxOf { it[4].toFloat() } * 1.05f
            chart.notifyDataSetChanged()
            chart.invalidate()
        }
    )
}

suspend fun fetchBinanceKlineData(symbol: String, interval: String, limit: Int): List<List<String>> {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val url = "https://api.binance.com/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit"
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response}")

            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            val gson = Gson()
            val jsonArray = gson.fromJson(responseBody, JsonArray::class.java)

            val klineList = mutableListOf<List<String>>()
            for (element in jsonArray) {
                if (element.isJsonArray) {
                    val innerArray = element.asJsonArray
                    val klineItem = mutableListOf<String>()
                    // Index 0: Open time, 1: Open, 2: High, 3: Low, 4: Close
                    klineItem.add(innerArray[0].asString)
                    klineItem.add(innerArray[1].asString)
                    klineItem.add(innerArray[2].asString)
                    klineItem.add(innerArray[3].asString)
                    klineItem.add(innerArray[4].asString)
                    klineList.add(klineItem)
                }
            }
            klineList
        }
    }
}