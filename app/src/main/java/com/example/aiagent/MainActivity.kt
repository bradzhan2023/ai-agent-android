package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoldPriceTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoldPriceTrackerApp()
                }
            }
        }
    }
}

sealed interface GoldPriceState {
    object Loading : GoldPriceState
    data class Success(val price: Double) : GoldPriceState
    data class Error(val message: String) : GoldPriceState
}

class GoldPriceViewModel : ViewModel() {
    private val _goldPriceState = MutableStateFlow<GoldPriceState>(GoldPriceState.Loading)
    val goldPriceState: StateFlow<GoldPriceState> = _goldPriceState.asStateFlow()

    private var currentSimulatedPrice = 1950.0 // Starting point for simulation in USD per ounce

    init {
        fetchGoldPrice() // Fetch price on ViewModel initialization
    }

    fun fetchGoldPrice() {
        viewModelScope.launch {
            _goldPriceState.value = GoldPriceState.Loading
            try {
                // Simulate network delay
                delay(2000) // 2 seconds delay

                // Simulate success or error randomly
                if (Random.nextBoolean()) { // 50% chance of success
                    // Simulate price fluctuation
                    currentSimulatedPrice += Random.nextDouble(-10.0, 10.0) // Fluctuate by +/- 10 USD
                    currentSimulatedPrice = "%.2f".format(currentSimulatedPrice).toDouble() // Format to 2 decimal places

                    _goldPriceState.value = GoldPriceState.Success(currentSimulatedPrice)
                    Log.d("GoldPriceViewModel", "Fetched price: $currentSimulatedPrice USD")
                } else {
                    // Simulate a network error
                    _goldPriceState.value = GoldPriceState.Error("無法連接伺服器，請稍後再試。")
                    Log.e("GoldPriceViewModel", "Simulated error: Cannot connect to server.")
                }
            } catch (e: Exception) {
                // Catch any unexpected exceptions during simulation
                _goldPriceState.value = GoldPriceState.Error("發生未知錯誤: ${e.localizedMessage}")
                Log.e("GoldPriceViewModel", "Unexpected error: ${e.localizedMessage}", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPriceTrackerApp(viewModel: GoldPriceViewModel = viewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("黃金價格追蹤") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val goldPriceState by viewModel.goldPriceState.collectAsState()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "目前黃金價格 (每盎司)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    when (goldPriceState) {
                        GoldPriceState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Text(
                                text = "載入中...",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                        is GoldPriceState.Success -> {
                            val price = (goldPriceState as GoldPriceState.Success).price
                            val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
                            Text(
                                text = currencyFormatter.format(price),
                                style = MaterialTheme.typography.displayMedium.copy(fontSize = 50.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "美元",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        is GoldPriceState.Error -> {
                            Text(
                                text = "錯誤: ${(goldPriceState as GoldPriceState.Error).message}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { viewModel.fetchGoldPrice() },
                modifier = Modifier.fillMaxWidth(0.6f),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                Spacer(Modifier.width(8.dp))
                Text("刷新價格")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GoldPriceTrackerAppPreview() {
    GoldPriceTrackerTheme {
        GoldPriceTrackerApp(viewModel = object : GoldPriceViewModel() {
            // Provide a mock state for preview
            override val goldPriceState: StateFlow<GoldPriceState> = MutableStateFlow(GoldPriceState.Success(1987.65)).asStateFlow()
        })
    }
}

@Composable
fun GoldPriceTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}

@Composable
fun lightColorScheme(
    primary: Color = Color(0xFF6200EE),
    secondary: Color = Color(0xFF03DAC5),
    tertiary: Color = Color(0xFF018786),
    background: Color = Color(0xFFFFFBFE),
    surface: Color = Color(0xFFFFFBFE),
    error: Color = Color(0xFFB00020),
    onPrimary: Color = Color.White,
    onSecondary: Color = Color.Black,
    onTertiary: Color = Color.White,
    onBackground: Color = Color(0xFF1C1B1F),
    onSurface: Color = Color(0xFF1C1B1F),
    onError: Color = Color.White,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    tertiary = tertiary,
    onTertiary = onTertiary,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    error = error,
    onError = onError
)

@Composable
fun darkColorScheme(
    primary: Color = Color(0xFFBB86FC),
    secondary: Color = Color(0xFF03DAC6),
    tertiary: Color = Color(0xFF03DAC6),
    background: Color = Color(0xFF121212),
    surface: Color = Color(0xFF121212),
    error: Color = Color(0xFFCF6679),
    onPrimary: Color = Color.Black,
    onSecondary: Color = Color.Black,
    onTertiary: Color = Color.Black,
    onBackground: Color = Color.White,
    onSurface: Color = Color.White,
    onError: Color = Color.Black,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    tertiary = tertiary,
    onTertiary = onTertiary,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    error = error,
    onError = onError
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)