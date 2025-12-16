package com.example.open_autoglm_android.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.open_autoglm_android.asr.AudioRecorder
import com.example.open_autoglm_android.asr.WhisperAsrEngine
import com.example.open_autoglm_android.ui.viewmodel.ChatViewModel
import com.example.open_autoglm_android.ui.viewmodel.MessageRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var userInput by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorder() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch {
                startOrStopRecording(
                    context = context,
                    audioRecorder = audioRecorder,
                    isRecording = isRecording,
                    setIsRecording = { isRecording = it },
                    setIsTranscribing = { isTranscribing = it },
                    setUserInput = { userInput = it }
                )
            }
        } else {
            Toast.makeText(context, "录音权限被拒绝，无法使用本地语音输入", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 显示任务完成 toast
    LaunchedEffect(uiState.taskCompletedMessage) {
        uiState.taskCompletedMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            // 清除消息，避免重复显示
            viewModel.clearTaskCompletedMessage()
        }
    }
    
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部工具栏：当前应用显示和清理按钮
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前应用显示
                uiState.currentApp?.let { app ->
                    Text(
                        text = "当前应用: $app",
                        style = MaterialTheme.typography.bodySmall
                    )
                } ?: run {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                // 清理对话按钮
                if (uiState.messages.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearMessages() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清理对话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 错误提示
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
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
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("关闭")
                    }
                }
            }
        }
        
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.messages) { message ->
                ChatMessageItem(message = message)
            }
            
            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在思考...")
                            }
                        }
                    }
                }
            }
        }
        
        // 输入框
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入任务描述...") },
                    enabled = !uiState.isLoading,
                    maxLines = 3
                )
                IconButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            coroutineScope.launch {
                                startOrStopRecording(
                                    context = context,
                                    audioRecorder = audioRecorder,
                                    isRecording = isRecording,
                                    setIsRecording = { isRecording = it },
                                    setIsTranscribing = { isTranscribing = it },
                                    setUserInput = { userInput = it }
                                )
                            }
                        }
                    },
                    enabled = !uiState.isLoading && !isTranscribing
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (isRecording) "停止录音" else "开始录音"
                    )
                }
                Button(
                    onClick = {
                        viewModel.sendMessage(userInput)
                        userInput = ""
                    },
                    enabled = !uiState.isLoading && userInput.isNotBlank()
                ) {
                    Text(
                        when {
                            isTranscribing -> "识别中..."
                            isRecording -> "录音中..."
                            else -> "发送"
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: com.example.open_autoglm_android.ui.viewmodel.ChatMessage) {
    val isUser = message.role == MessageRole.USER
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 思考过程（可展开）
                if (!isUser && !message.thinking.isNullOrBlank()) {
                    var expanded by remember { mutableStateOf(false) }
                    
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (expanded) "收起思考过程" else "展开思考过程",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    
                    if (expanded) {
                        Text(
                            text = message.thinking,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                
                // 消息内容
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isUser) FontWeight.Normal else FontWeight.Medium
                )
                
                // 动作 JSON（如果是助手消息）
                if (!isUser && message.action != null && message.action.contains("{")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = message.action,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

private suspend fun startOrStopRecording(
    context: android.content.Context,
    audioRecorder: AudioRecorder,
    isRecording: Boolean,
    setIsRecording: (Boolean) -> Unit,
    setIsTranscribing: (Boolean) -> Unit,
    setUserInput: (String) -> Unit
) {
    if (!isRecording) {
        setIsRecording(true)
        Toast.makeText(context, "开始录音，请讲话...", Toast.LENGTH_SHORT).show()
        audioRecorder.start()
    } else {
        setIsRecording(false)
        setIsTranscribing(true)
        Toast.makeText(context, "录音结束，正在识别...", Toast.LENGTH_SHORT).show()
        val pcm = audioRecorder.stop()
        if (pcm.isNotEmpty()) {
            val text = WhisperAsrEngine.transcribe(
                context = context,
                pcm = pcm,
                sampleRate = audioRecorder.getSampleRate(),
                language = null
            )
            setUserInput(text)
        } else {
            Toast.makeText(context, "未录到有效语音", Toast.LENGTH_SHORT).show()
        }
        setIsTranscribing(false)
    }
}
