package com.example.open_autoglm_android.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.open_autoglm_android.ui.viewmodel.AdvancedAuthViewModel
import com.example.open_autoglm_android.ui.viewmodel.AdvancedAuthUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedAuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AdvancedAuthViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // 显示消息提示
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            // 消息会在 UI 中显示，不需要 Toast
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("高级授权与无感保活") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限状态总览
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.hasWriteSecureSettings) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "WRITE_SECURE_SETTINGS 权限",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (uiState.hasWriteSecureSettings) {
                                    "✓ 已授权 - 无感保活功能可用"
                                } else {
                                    "✗ 未授权 - 请选择下方任一方式授权"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (uiState.hasWriteSecureSettings) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // 无感保活说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "无感保活机制说明",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "• 将 AutoGLM 快捷开关添加到通知栏快速设置面板\n" +
                                "• 打开 Tile 内的\"保活开关\"，会启动前台保活服务\n" +
                                "• 当系统杀死无障碍服务或应用崩溃后，下拉通知栏会触发服务检查\n" +
                                "• 若检测到无障碍实例已被回收且已具备 WRITE_SECURE_SETTINGS 权限，会自动重新启用无障碍服务\n" +
                                "• 整个过程无需再次进入系统设置重新授权，用户只感知为一次普通的下拉通知栏操作",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    if (!uiState.hasWriteSecureSettings) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "⚠️ 提示：需要先完成下方任一授权方式，才能启用无感保活功能",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            // Shizuku 授权
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1. Shizuku 授权（推荐，免 Root）",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (!uiState.shizukuAvailable) {
                                    "Shizuku 未安装或未启动"
                                } else if (!uiState.shizukuAuthorized) {
                                    "Shizuku 已安装，但未授权"
                                } else {
                                    "Shizuku 已授权"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.shizukuAuthorized && uiState.hasWriteSecureSettings) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Text(
                        text = "• 在应用商店或官网安装 Shizuku\n" +
                                "• 按 Shizuku 内部引导完成一次性 ADB 启动\n" +
                                "• 在 Shizuku 中授予 AutoGLM Android 所需的系统设置权限\n" +
                                "• 完成后即可使用下拉通知栏\"无感保活\"功能",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!uiState.shizukuAvailable) {
                            Button(
                                onClick = { viewModel.openShizukuApp() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("安装 Shizuku")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.requestShizukuPermission() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isLoading
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (uiState.shizukuAuthorized) "重新授权" else "请求授权")
                                }
                            }
                        }
                    }
                }
            }
            
            // ADB 授权
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "2. ADB 授权（开发者/测试推荐）",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "在已连接电脑且开启开发者模式与 USB 调试的前提下，在终端执行以下命令：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.example.open_autoglm_android.util.AuthHelper.getAdbGrantCommand(context),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.copyAdbCommand() }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "复制命令")
                            }
                        }
                    }
                    
                    Text(
                        text = "授权成功后，下拉通知栏渲染 AutoGLM 快捷开关时，将自动检查并通过系统设置尝试重启无障碍服务，实现\"无感保活\"。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = { viewModel.copyAdbCommand() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制 ADB 命令")
                    }
                }
            }
            
            // Root 授权
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "3. Root 授权（高级用户）",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (uiState.rootAvailable) {
                                    "检测到 Root 权限可用"
                                } else {
                                    "未检测到 Root 权限"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.rootAvailable && uiState.hasWriteSecureSettings) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Text(
                        text = "• 在已 Root 的设备中，可使用 Magisk / 权限管理工具将本应用提升为系统应用或直接授予 WRITE_SECURE_SETTINGS 等系统权限\n" +
                                "• 具体操作因 ROM 而异，建议仅高级用户或专业人士尝试\n" +
                                "• 完成授权后，本应用同样会在下拉通知栏渲染快捷开关时，自动尝试恢复无障碍服务",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Button(
                        onClick = { viewModel.requestRootPermission() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.rootAvailable && !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("请求 Root 授权")
                        }
                    }
                }
            }
            
            // 消息提示
            uiState.message?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (uiState.messageType) {
                            AdvancedAuthUiState.MessageType.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                            AdvancedAuthUiState.MessageType.ERROR -> MaterialTheme.colorScheme.errorContainer
                            AdvancedAuthUiState.MessageType.INFO -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearMessage() }) {
                            Text("关闭")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
