package com.example.familytreeplatform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.familytreeplatform.navigation.AppNavigation
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyTreePlatformTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    AppNavigation(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
