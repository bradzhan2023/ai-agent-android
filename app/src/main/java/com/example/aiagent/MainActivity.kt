package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiagent.ui.theme.AiAgentTheme
import kotlin.random.Random
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

// Data class to hold a gold price record with its timestamp
data class GoldPriceRecord(val price: String, val timestamp: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceTrackerApp() {
    // State to hold the current gold price and its timestamp
    var currentPriceRecord by remember {
        val df = DecimalFormat("$#,##0.00")
        val initialPrice = 2000.0
        val initialTimestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        mutableStateOf(GoldPriceRecord("${df.format(initialPrice)} / oz", initialTimestamp))
    }

    // State to hold a list of recent price records (excluding the current one)
    val pastPriceRecords = remember { mutableStateListOf<GoldPriceRecord>() }

    // Function to simulate fetching a new gold price
    fun fetchNewGoldPrice() {
        // Before updating current, save the *old* current to history
        val oldPriceRecord = currentPriceRecord
        if (pastPriceRecords.size >= 4) { // Keep a maximum of 4 past records + the current one = 5 total displayed
            pastPriceRecords.removeLast() // Remove the oldest record
        }
        pastPriceRecords.add(0, oldPriceRecord) // Add the old current record to the beginning of past records

        val basePrice = 1900.0
        val fluctuation = Random.nextDouble(-50.0, 50.0) // Fluctuate by +/- $50
        val newPrice = basePrice + fluctuation
        val df = DecimalFormat("$#,##0.00")
        val formattedPrice = "${df.format(newPrice)} / oz"
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        currentPriceRecord = GoldPriceRecord(formattedPrice, currentTime)
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
                text = currentPriceRecord.price,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Last updated: ${currentPriceRecord.timestamp}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = { fetchNewGoldPrice() },
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Refresh Price")
            }

            // Display recent prices if any history exists
            if (pastPriceRecords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Recent Updates:",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    pastPriceRecords.forEachIndexed { index, record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${record.price}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = record.timestamp,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Add a Divider between historical entries for better visual separation
                        if (index < pastPriceRecords.lastIndex) {
                            Divider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
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