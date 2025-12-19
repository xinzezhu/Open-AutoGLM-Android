package com.example.open_autoglm_android.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.open_autoglm_android.data.PreferencesRepository
import com.example.open_autoglm_android.domain.ActionExecutor
import com.example.open_autoglm_android.domain.AppRegistry
import com.example.open_autoglm_android.network.ModelClient
import com.example.open_autoglm_android.network.dto.ChatMessage as NetworkChatMessage
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
import com.example.open_autoglm_android.util.BitmapUtils
import com.example.open_autoglm_android.util.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val thinking: String? = null,
    val action: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER, ASSISTANT
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentApp: String? = null,
    val taskCompletedMessage: String? = null // 任务完成消息，用于显示 toast
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepository(application)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var modelClient: ModelClient? = null
    private var actionExecutor: ActionExecutor? = null
    
    // 维护对话上下文（消息历史）
    private val messageContext = mutableListOf<NetworkChatMessage>()
    
    init {
        viewModelScope.launch {
            // 初始化 ModelClient
            val baseUrl = preferencesRepository.getBaseUrlSync()
            val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
            modelClient = ModelClient(baseUrl, apiKey)
            
            // 初始化 ActionExecutor
            AutoGLMAccessibilityService.getInstance()?.let { service ->
                actionExecutor = ActionExecutor(service)
            }
            
            // 监听当前应用变化
            launch {
                AutoGLMAccessibilityService.getInstance()?.currentApp?.collect { app ->
                    _uiState.value = _uiState.value.copy(currentApp = app)
                }
            }
        }
    }
    
    fun sendMessage(userInput: String) {
        if (userInput.isBlank() || _uiState.value.isLoading) return
        
        val accessibilityService = AutoGLMAccessibilityService.getInstance()
        if (accessibilityService == null) {
            _uiState.value = _uiState.value.copy(
                error = "无障碍服务未启用，请前往设置开启"
            )
            return
        }
        
        // 自动清理会话：开始新任务前清空 UI 消息和完成提示
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            taskCompletedMessage = null,
            error = null
        )
        
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            role = MessageRole.USER,
            content = userInput
        )
        
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true,
            error = null
        )
        
        viewModelScope.launch {
            try {
                // 在每次任务开始前，重新加载 AppRegistry，以确保最新的映射配置被加载
                AppRegistry.initialize(getApplication())
                
                // 重新初始化 ModelClient（以防配置变化）
                val baseUrl = preferencesRepository.getBaseUrlSync()
                val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
                val modelName = preferencesRepository.getModelNameSync()
                
                if (apiKey == null || apiKey.isEmpty() || apiKey == "EMPTY") {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "请先在设置页面配置 API Key"
                    )
                    return@launch
                }
                
                modelClient = ModelClient(baseUrl, apiKey)
                actionExecutor = ActionExecutor(accessibilityService)
                
                // 清空消息上下文，开始新的任务
                messageContext.clear()
                
                // 执行任务循环
                executeTaskLoop(userInput, modelName, apiKey)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "错误: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun executeTaskLoop(userPrompt: String, modelName: String, apiKey: String) {
        val accessibilityService = AutoGLMAccessibilityService.getInstance() ?: return
        val client = modelClient ?: return
        val executor = actionExecutor ?: return
        
        var stepCount = 0
        val maxSteps = 50
        
        var retryCount = 0
        
        while (stepCount < maxSteps) {
            Log.d("ChatViewModel", "执行步骤 $stepCount")
            
            // 截图
            val screenshot = accessibilityService.takeScreenshotSuspend()
            
            if (screenshot == null) {
                val androidVersion = android.os.Build.VERSION.SDK_INT
                val errorMessage = if (androidVersion < android.os.Build.VERSION_CODES.R) {
                    "无法获取屏幕截图：需要 Android 11 (API 30) 及以上版本，当前版本: Android ${android.os.Build.VERSION.RELEASE} (API $androidVersion)"
                } else {
                    "无法获取屏幕截图，请确保无障碍服务已启用并授予截图权限。如果已启用，请尝试重启应用。"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMessage
                )
                return
            }
            
            // 检查截图是否全黑（在模拟器上可能无法正常截图，所以跳过检测）
            val isEmulator = DeviceUtils.isEmulator()
            if (BitmapUtils.isBitmapBlack(screenshot)) {
                if (isEmulator) {
                    // 在模拟器上，截图可能无法正常工作，但仍然尝试继续
                    Log.w("ChatViewModel", "检测到模拟器环境，截图是全黑的，但继续执行（模拟器限制）")
                    // 不返回，继续执行
                } else {
                    // 在真机上，如果截图是全黑的，给出错误提示
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "截图是全黑的，可能是应用设置了 FLAG_SECURE 防止截图，或者是应用正在启动中。请稍后再试。\n\n提示：如果在模拟器上运行，截图功能可能无法正常工作，建议在真机上测试。"
                    )
                    return
                }
            }
            
            // 获取当前应用
            val currentApp = accessibilityService.currentApp.value
            Log.d("ChatViewModel", "当前应用: $currentApp")
            
            // 构建消息上下文
            if (stepCount == 0) {
                // 第一次调用：添加系统消息和用户消息（包含原始任务）
                if (messageContext.isEmpty()) {
                    messageContext.add(client.createSystemMessage())
                }
                messageContext.add(client.createUserMessage(userPrompt, screenshot, currentApp))
            } else {
                // 后续调用：只添加屏幕信息
                messageContext.add(client.createScreenInfoMessage(screenshot, currentApp))
            }
            
            // 调用模型（使用消息上下文）
            val messagesList: List<NetworkChatMessage> = messageContext.toList()
            val response = client.request(
                messages = messagesList,
                modelName = modelName,
                apiKey = apiKey
            )
            Log.d("ChatViewModel", "模型响应: thinking=${response.thinking.take(100)}, action=${response.action.take(100)}")
            
            // 添加助手消息到上下文
            messageContext.add(client.createAssistantMessage(response.thinking, response.action))
            
            // 从上下文中移除最后一条用户消息的图片（节省 token，参考原项目）
            // 在执行动作后，移除图片只保留文本，这样可以节省大量 token
            if (messageContext.size >= 2) {
                val lastUserMessageIndex = messageContext.size - 2
                val lastUserMessage = messageContext[lastUserMessageIndex]
                if (lastUserMessage.role == "user") {
                    // 移除图片，只保留文本
                    messageContext[lastUserMessageIndex] = client.removeImagesFromMessage(lastUserMessage)
                    Log.d("ChatViewModel", "已移除最后一条用户消息中的图片，节省 token")
                }
            }
            
            // 添加助手消息到UI
            val assistantMessage = ChatMessage(
                id = "${System.currentTimeMillis()}_$stepCount",
                role = MessageRole.ASSISTANT,
                content = response.action,
                thinking = response.thinking,
                action = response.action
            )
            
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + assistantMessage
            )
            
            // 如果模型返回的是 finish，则直接结束，不再执行动作
            val isFinishAction = response.action.contains("\"_metadata\":\"finish\"") ||
                response.action.contains("\"_metadata\": \"finish\"") ||
                response.action.lowercase().contains("finish(")
            if (isFinishAction) {
                val completionMessage = extractFinishMessage(response.action) ?: resultMessageFallback(response.action)
                //accessibilityService.showToast(completionMessage)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    taskCompletedMessage = completionMessage
                )
                Log.d("ChatViewModel", "任务完成(无需执行动作): $completionMessage")
                return
            }
            
            // 解析并执行动作
            val result = executor.execute(
                response.action,
                screenshot.width,
                screenshot.height
            )
            Log.d("ChatViewModel", "动作执行结果: success=${result.success}, message=${result.message}")
            
            val isFinished = result.message != null && (result.message!!.contains("完成") || 
                result.message!!.contains("finish")) || 
                response.action.contains("\"_metadata\":\"finish\"") ||
                response.action.contains("\"_metadata\": \"finish\"")
            
            if (isFinished) {
                // 任务完成
                val completionMessage = result.message ?: "任务已完成"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    taskCompletedMessage = completionMessage
                )
                Log.d("ChatViewModel", "任务完成: $completionMessage")
                return
            }
            
            if (!result.success) {
                retryCount++
                Log.w("ChatViewModel", "动作执行失败，准备重试 ($retryCount/3): ${result.message}")

                // 如果是 JSON 解析/格式错误，给模型一条明确的反馈消息
                val parseError = result.message?.contains("无法从响应中提取有效的 JSON 动作") == true
                if (parseError) {
                    val feedbackText = buildString {
                        appendLine("上一步你的输出格式错误，无法解析为合法的动作指令。")
                        appendLine("请严格按照系统提示中的格式，仅输出以下两种之一：")
                        appendLine("1) do(action=\"...\", ...)")
                        appendLine("2) finish(message=\"...\")")
                        appendLine("不要输出列表、自然语言说明或其他非规范格式。")
                        appendLine()
                        append("你上一次的输出是：")
                        append(response.action.take(200))
                    }
                    messageContext.add(
                        NetworkChatMessage(
                            role = "user",
                            content = listOf(
                                com.example.open_autoglm_android.network.dto.ContentItem(
                                    type = "text",
                                    text = feedbackText
                                )
                            )
                        )
                    )
                    Log.d("ChatViewModel", "已向模型反馈格式错误，要求按规范重新输出动作")
                }

                if (retryCount >= 10) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "执行动作失败"
                    )
                    Log.e("ChatViewModel", "重试超过上限，结束流程: ${result.message}")
                    return
                }

                // 重试：继续下一轮循环，重新截屏并请求模型
                delay(800)
                continue
            } else {
                // 成功则重置重试计数
                retryCount = 0
            }
            
            // 等待界面稳定
            delay(1000)
            stepCount++
        }
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "达到最大步数限制"
        )
        Log.w("ChatViewModel", "达到最大步数限制")
    }
    
    /**
     * 从模型返回的 finish 动作中提取 message
     */
    private fun extractFinishMessage(action: String): String? {
        // JSON 形式: {"_metadata":"finish","message":"xxx"}
        val jsonPattern = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
        val jsonMatch = jsonPattern.find(action)
        if (jsonMatch != null) return jsonMatch.groupValues[1]
        
        // 函数形式: finish(message="xxx") 或 finish(message='xxx')
        val funcPattern = Regex("finish\\s*\\(\\s*message\\s*=\\s*['\"]([^'\"]+)['\"]\\s*\\)", RegexOption.IGNORE_CASE)
        val funcMatch = funcPattern.find(action)
        if (funcMatch != null) return funcMatch.groupValues[1]
        
        return null
    }
    
    private fun resultMessageFallback(action: String): String {
        // 默认兜底，保留部分动作文本
        return if (action.length > 80) action.take(80) + "..." else action.ifBlank { "任务已完成" }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearTaskCompletedMessage() {
        _uiState.value = _uiState.value.copy(taskCompletedMessage = null)
    }
    
    /**
     * 清理对话历史，开始新的会话
     */
    fun clearMessages() {
        // 清空消息列表
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            error = null,
            taskCompletedMessage = null
        )
        // 清空消息上下文
        messageContext.clear()
        Log.d("ChatViewModel", "已清理对话历史，开始新会话")
    }
    
    fun refreshModelClient() {
        viewModelScope.launch {
            val baseUrl = preferencesRepository.getBaseUrlSync()
            val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
            modelClient = ModelClient(baseUrl, apiKey)
        }
    }
}
