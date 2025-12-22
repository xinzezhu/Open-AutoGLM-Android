package com.example.open_autoglm_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.open_autoglm_android.navigation.Screen
import com.example.open_autoglm_android.ui.screen.AdvancedAuthScreen
import com.example.open_autoglm_android.ui.screen.MainScreen
import com.example.open_autoglm_android.ui.screen.SettingsScreen
import com.example.open_autoglm_android.ui.screen.PromptLogScreen
import com.example.open_autoglm_android.ui.theme.OpenAutoGLMAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenAutoGLMAndroidTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, Screen.Main.name) {
                    composable(Screen.Main.name) {
                        MainScreen(
                            onNavigateToPromptLog = {
                                navController.navigate(Screen.PromptLog.name)
                            },
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.name)
                            }
                        )
                    }
                    composable(Screen.PromptLog.name) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            PromptLogScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    composable(Screen.AdvancedAuth.name) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            AdvancedAuthScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    composable(Screen.Settings.name){
                        SettingsScreen(
                            onNavigateToAdvancedAuth = {
                                navController.navigate(Screen.AdvancedAuth.name)
                            },
                            onBack = {
                                navController.navigateUp()
                            }
                        )
                    }
                }
            }
        }
    }
}
