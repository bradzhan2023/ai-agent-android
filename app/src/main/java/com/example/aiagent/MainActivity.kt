package com.example.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 深藍色背景
            val deepBlue = Color(0xFF0D47A1) // Material Design's Blue 900
            // 金色文字
            val goldColor = Color(0xFFFFD700) // Standard Gold color

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = deepBlue
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$1,380.50", // 示例金價
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 80.sp, // 巨大字體
                            fontWeight = FontWeight.Bold
                        ),
                        color = goldColor, // 金色文字
                        modifier = Modifier.padding(bottom = 64.dp) // 與按鈕的間距
                    )

                    Button(
                        onClick = {
                            // TODO: Add actual update logic here
                            // Log.d("MainActivity", "Update Now button clicked!")
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.6f) // 佔寬度60%
                            .height(56.dp), // 固定高度
                        shape = RoundedCornerShape(50.dp), // 圓角按鈕
                        colors = ButtonDefaults.buttonColors(
                            containerColor = goldColor, // 按鈕背景色為金色
                            contentColor = Color.Black // 按鈕文字顏色為黑色
                        )
                    ) {
                        Text(
                            text = "Update Now",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                        )
                    }
                }
            }
        }
    }
}