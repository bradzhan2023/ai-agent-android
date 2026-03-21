package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiagent.ui.theme.AiAgentTheme
import kotlin.random.Random
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiAgentTheme {
                GoldPriceTrackerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceTrackerApp() {
    // State to hold the current gold price
    var goldPrice by remember { mutableStateOf("$2000.00 / oz") }

    // Function to simulate fetching a new gold price
    fun fetchNewGoldPrice() {
        val basePrice = 1900.0
        val fluctuation = Random.nextDouble(-50.0, 50.0) // Fluctuate by +/- $50
        val newPrice = basePrice + fluctuation
        val df = DecimalFormat("$#,##0.00")
        goldPrice = "${df.format(newPrice)} / oz"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gold Price Tracker") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current Gold Price:",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = goldPrice,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = { fetchNewGoldPrice() },
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Refresh Price")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerAppPreview() {
    AiAgentTheme {
        GoldPriceTrackerApp()
    }
}