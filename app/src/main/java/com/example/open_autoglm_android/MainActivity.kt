package com.example.open_autoglm_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.open_autoglm_android.ui.screen.AdvancedAuthScreen
import com.example.open_autoglm_android.ui.screen.ChatScreen
import com.example.open_autoglm_android.ui.screen.SettingsScreen
import com.example.open_autoglm_android.ui.screen.PromptLogScreen
import com.example.open_autoglm_android.ui.theme.OpenAutoGLMAndroidTheme
import com.example.open_autoglm_android.ui.viewmodel.AdvancedAuthViewModel
import com.example.open_autoglm_android.ui.viewmodel.ChatViewModel
import com.example.open_autoglm_android.ui.viewmodel.SettingsViewModel
import com.example.open_autoglm_android.util.AccessibilityServiceHelper
import com.example.open_autoglm_android.util.AuthHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenAutoGLMAndroidTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var previousTab by remember { mutableIntStateOf(0) }
    var showAdvancedAuth by remember { mutableStateOf(false) }
    var showPromptLog by remember { mutableStateOf(false) }
    
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val advancedAuthViewModel: AdvancedAuthViewModel = viewModel()
    
    // 启动时检查：如果有写入安全设置权限且无障碍服务没开，自动尝试开启
    LaunchedEffect(Unit) {
        if (AuthHelper.hasWriteSecureSettingsPermission(context)) {
            if (!AccessibilityServiceHelper.isAccessibilityServiceEnabled(context)) {
                AccessibilityServiceHelper.ensureServiceEnabledViaSecureSettings(context)
            }
        }
    }

    // 处理系统返回键
    if (showAdvancedAuth) {
        BackHandler { showAdvancedAuth = false }
    } else if (showPromptLog) {
        BackHandler { showPromptLog = false }
    } else if (chatViewModel.uiState.collectAsState().value.isDrawerOpen) {
        BackHandler { chatViewModel.closeDrawer() }
    }

    // 当切换到设置页面时，刷新无障碍服务状态
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && previousTab != 1) {
            settingsViewModel.checkAccessibilityService()
        }
        previousTab = selectedTab
    }
    
    // 当从高级授权页面返回时，刷新权限状态
    LaunchedEffect(showAdvancedAuth) {
        if (!showAdvancedAuth) {
            advancedAuthViewModel.checkAllPermissions()
        }
    }
    
    // 使用 Box 堆叠页面，这样在查看日志时，主页面状态（如 ViewModel）更加稳定
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("AutoGLM Android") }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Message, contentDescription = null) },
                        label = { Text("对话") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("设置") },
                        selected = selectedTab == 1,
                        onClick = { 
                            selectedTab = 1
                            settingsViewModel.checkAccessibilityService()
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> ChatScreen(
                        viewModel = chatViewModel,
                        onNavigateToPromptLog = { showPromptLog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToAdvancedAuth = { showAdvancedAuth = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 高级授权页面覆盖层
        if (showAdvancedAuth) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AdvancedAuthScreen(
                    viewModel = advancedAuthViewModel,
                    onBack = { showAdvancedAuth = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 提示词日志页面覆盖层
        if (showPromptLog) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PromptLogScreen(
                    viewModel = chatViewModel,
                    onBack = { showPromptLog = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
