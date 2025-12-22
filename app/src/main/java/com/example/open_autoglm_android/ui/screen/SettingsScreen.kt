package com.example.open_autoglm_android.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.open_autoglm_android.data.InputMode
import com.example.open_autoglm_android.ui.viewmodel.SettingsViewModel
import com.example.open_autoglm_android.util.AuthHelper
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToAdvancedAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasWriteSecureSettings = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        hasWriteSecureSettings.value = AuthHelper.hasWriteSecureSettingsPermission(context)
    }
    
    LaunchedEffect(Unit) {
        viewModel.checkAccessibilityService()
        viewModel.checkImeStatus()
    }
    
    DisposableEffect(Unit) {
        viewModel.checkAccessibilityService()
        viewModel.checkOverlayPermission()
        viewModel.checkImeStatus()
        onDispose { }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 无障碍服务状态
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isAccessibilityEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "无障碍服务", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (uiState.isAccessibilityEnabled) "已启用" else "未启用 - 点击前往设置",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!uiState.isAccessibilityEnabled) {
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("前往设置") }
                    }
                }
            }
        }
        
        // 输入模式设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(text = "输入方式 (Type Action)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                InputMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.inputMode == mode,
                            onClick = { viewModel.setInputMode(mode) }
                        )
                        Text(
                            text = when(mode) {
                                InputMode.SET_TEXT -> "直接设置文本 (标准)"
                                InputMode.PASTE -> "复制粘贴 (兼容性好)"
                                InputMode.IME -> "输入法模拟 (最强悍)"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                if (uiState.inputMode == InputMode.IME) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!uiState.isImeEnabled) {
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("1. 启用 AutoGLM 输入法") }
                    } else if (!uiState.isImeSelected) {
                        Button(
                            onClick = {
                                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showInputMethodPicker()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("2. 切换为 AutoGLM 输入法") }
                    } else {
                        Text(
                            text = "✓ 输入法已就绪",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 悬浮窗设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.floatingWindowEnabled && uiState.hasOverlayPermission) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "悬浮窗", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (!uiState.hasOverlayPermission) "需要悬浮窗权限" else if (uiState.floatingWindowEnabled) "已启用" else "未启用",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!uiState.hasOverlayPermission) {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        }) { Text("授权") }
                    } else {
                        Switch(checked = uiState.floatingWindowEnabled, onCheckedChange = { viewModel.setFloatingWindowEnabled(it) })
                    }
                }
            }
        }

        Divider()

        // 实验型功能
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "实验型功能", style = MaterialTheme.typography.titleMedium)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 图片压缩
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "图片质量压缩", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "降低 JPEG 质量，减少流量消耗",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = uiState.imageCompressionEnabled,
                        onCheckedChange = { viewModel.setImageCompressionEnabled(it) }
                    )
                }
                
                if (uiState.imageCompressionEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "压缩级别: ${uiState.imageCompressionLevel}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = uiState.imageCompressionLevel.toFloat(),
                        onValueChange = { viewModel.setImageCompressionLevel(it.roundToInt()) },
                        valueRange = 10f..100f,
                        steps = 8
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 分辨率缩放
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "分辨率等比缩放", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "缩小发送给模型的图片尺寸，大幅提升响应速度",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = uiState.screenScaleEnabled,
                        onCheckedChange = { viewModel.setScreenScaleEnabled(it) }
                    )
                }

                if (uiState.screenScaleEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "缩放比例: ${(uiState.screenScaleFactor * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = uiState.screenScaleFactor,
                        onValueChange = { viewModel.setScreenScaleFactor(it) },
                        valueRange = 0.25f..0.75f,
                        steps = 9 // 0.25, 0.30, ..., 0.75 (每 5% 一个步长)
                    )
                }
            }
        }

        Divider()

        // 高级授权
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (hasWriteSecureSettings.value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "高级授权与无感保活", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (hasWriteSecureSettings.value) "✓ 已授权" else "✗ 未授权",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToAdvancedAuth) { Icon(Icons.Filled.ArrowForward, contentDescription = null) }
                }
            }
        }

        Divider()

        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = { viewModel.updateApiKey(it) },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        OutlinedTextField(
            value = uiState.baseUrl,
            onValueChange = { viewModel.updateBaseUrl(it) },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        OutlinedTextField(
            value = uiState.modelName,
            onValueChange = { viewModel.updateModelName(it) },
            label = { Text("Model Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Button(
            onClick = { viewModel.saveSettings() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("保存设置")
        }
        
        uiState.saveSuccess?.let { if (it) { Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Text(text = "设置已保存", modifier = Modifier.padding(12.dp)) } } }
        uiState.error?.let { error -> Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(text = error); TextButton(onClick = { viewModel.clearError() }) { Text("关闭") } } } }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "说明：\n1. 开启无障碍服务\n2. 若遇到输入框无法输入，请尝试切换输入方式为“复制粘贴”或“输入法模拟”\n3. 使用“输入法模拟”时需要先在系统设置中启用并切换到 AutoGLM 输入法",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
