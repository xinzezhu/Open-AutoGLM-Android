package com.example.open_autoglm_android.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.open_autoglm_android.data.ConversationRepository
import com.example.open_autoglm_android.data.PreferencesRepository
import com.example.open_autoglm_android.data.SavedChatMessage
import com.example.open_autoglm_android.data.Conversation
import com.example.open_autoglm_android.domain.ActionExecutor
import com.example.open_autoglm_android.domain.AppRegistry
import com.example.open_autoglm_android.domain.ExecuteResult
import com.example.open_autoglm_android.network.ModelClient
import com.example.open_autoglm_android.network.dto.ChatMessage as NetworkChatMessage
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
import com.example.open_autoglm_android.service.FloatingWindowService
import com.example.open_autoglm_android.util.BitmapUtils
import com.example.open_autoglm_android.util.DeviceUtils
import com.example.open_autoglm_android.util.InputMethodHelper
import com.example.open_autoglm_android.util.AuthHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import kotlinx.coroutines.isActive

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val thinking: String? = null,
    val action: String? = null,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER, ASSISTANT
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isPaused: Boolean = false,
    val error: String? = null,
    val currentApp: String? = null,
    val taskCompletedMessage: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String? = null,
    val isDrawerOpen: Boolean = false
)

data class StepTiming(
    val step: Int,
    val screenshotMs: Long = 0,
    val networkMs: Long = 0,
    val executionMs: Long = 0,
    val totalMs: Long = 0,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val imageSizeKb: Double = 0.0,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepository(application)
    private val conversationRepository = ConversationRepository(application)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var modelClient: ModelClient? = null
    private var actionExecutor: ActionExecutor? = null
    private var currentTaskJob: Job? = null
    
    // 维护对话上下文（消息历史，仅在运行时有效，包含图片等大数据）
    private val messageContext = mutableListOf<NetworkChatMessage>()
    // 维护每一步的耗时信息
    private val stepTimings = mutableListOf<StepTiming>()
    
    init {
        setupFloatingWindowListeners()
        viewModelScope.launch {
            // 初始化 ModelClient
            val baseUrl = preferencesRepository.getBaseUrlSync()
            val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
            modelClient = ModelClient(baseUrl, apiKey)
            
            // 初始化 ActionExecutor
            AutoGLMAccessibilityService.getInstance()?.let { service ->
                actionExecutor = ActionExecutor(service)
            }
            
            // 监听对话列表变化
            launch {
                conversationRepository.conversations.collect { conversations ->
                    _uiState.value = _uiState.value.copy(conversations = conversations)
                }
            }
            
            // 监听当前对话变化
            launch {
                conversationRepository.currentConversationId.collect { conversationId ->
                    _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
                    // 加载对话历史
                    loadConversationMessages(conversationId)
                }
            }
            
            // 监听当前应用变化 (UI 实时展示)
            launch {
                AutoGLMAccessibilityService.getInstance()?.currentApp?.collect { app ->
                    _uiState.value = _uiState.value.copy(currentApp = app)
                }
            }
            
            // 如果没有对话，创建一个默认对话
            if (conversationRepository.conversations.value.isEmpty()) {
                conversationRepository.createConversation()
            }
        }
    }

    private fun setupFloatingWindowListeners() {
        FloatingWindowService.onStopClickListener = {
            stopTask()
        }
        FloatingWindowService.onPauseResumeClickListener = {
            togglePause()
        }
    }
    
    private fun loadConversationMessages(conversationId: String?) {
        if (conversationId == null) {
            _uiState.value = _uiState.value.copy(messages = emptyList())
            return
        }
        
        val conversation = conversationRepository.getCurrentConversation()
        if (conversation != null) {
            val messages = conversation.messages.map { saved ->
                ChatMessage(
                    id = saved.id,
                    role = if (saved.role == "USER") MessageRole.USER else MessageRole.ASSISTANT,
                    content = saved.content,
                    thinking = saved.thinking,
                    action = saved.action,
                    imagePath = saved.imagePath,
                    timestamp = saved.timestamp
                )
            }
            _uiState.value = _uiState.value.copy(messages = messages)
        }
    }
    
    private suspend fun saveCurrentMessages() {
        val conversationId = _uiState.value.currentConversationId ?: return
        val savedMessages = _uiState.value.messages.map { msg ->
            SavedChatMessage(
                id = msg.id,
                role = msg.role.name,
                content = msg.content,
                thinking = msg.thinking,
                action = msg.action,
                imagePath = msg.imagePath,
                timestamp = msg.timestamp
            )
        }
        conversationRepository.updateConversationMessages(conversationId, savedMessages)
    }
    
    /**
     * 创建新对话
     */
    fun createNewConversation() {
        viewModelScope.launch {
            conversationRepository.createConversation()
            messageContext.clear()
            stepTimings.clear()
        }
    }
    
    /**
     * 切换对话
     */
    fun switchConversation(conversationId: String) {
        conversationRepository.switchConversation(conversationId)
        messageContext.clear()
        stepTimings.clear()
        _uiState.value = _uiState.value.copy(isDrawerOpen = false)
    }
    
    /**
     * 删除对话
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
            messageContext.clear()
            stepTimings.clear()
        }
    }
    
    /**
     * 打开/关闭侧边栏
     */
    fun toggleDrawer() {
        _uiState.value = _uiState.value.copy(isDrawerOpen = !_uiState.value.isDrawerOpen)
    }
    
    fun closeDrawer() {
        _uiState.value = _uiState.value.copy(isDrawerOpen = false)
    }

    /**
     * 重新执行任务
     */
    fun rerunTask() {
        val lastUserMessage = _uiState.value.messages.lastOrNull { it.role == MessageRole.USER }
        if (lastUserMessage != null) {
            sendMessage(lastUserMessage.content)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
        messageContext.clear()
        stepTimings.clear()
        viewModelScope.launch {
            saveCurrentMessages()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearTaskCompletedMessage() {
        _uiState.value = _uiState.value.copy(taskCompletedMessage = null)
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

        // 任务开始时自动切换输入法
        val context = getApplication<Application>()
        if (AuthHelper.hasWriteSecureSettingsPermission(context)) {
            if (InputMethodHelper.isInputMethodEnabled(context) && !InputMethodHelper.isInputMethodSelected(context)) {
                InputMethodHelper.switchToMyInputMethod(context)
            }
        }
        
        // 清空当前对话的消息（开始新任务）
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            taskCompletedMessage = null,
            error = null,
            isPaused = false
        )
        FloatingWindowService.getInstance()?.updatePauseStatus(false)
        
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
        
        currentTaskJob = viewModelScope.launch {
            try {
                // 在每次任务开始前，重新加载 AppRegistry，以确保最新的映射配置被加载
                AppRegistry.initialize(getApplication())
                
                // 重新初始化 ModelClient（以防配置变化）
                val baseUrl = preferencesRepository.getBaseUrlSync()
                val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
                val modelName = preferencesRepository.getModelNameSync()
                
                if (apiKey == "EMPTY" || apiKey.isEmpty()) {
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
                stepTimings.clear()
                
                // 添加系统消息
                modelClient?.let { messageContext.add(it.createSystemMessage()) }
                
                // 保存用户消息
                saveCurrentMessages()
                
                // 执行任务循环
                executeTaskLoop(userInput, modelName)
                
            } catch (e: CancellationException) {
                Log.d("ChatViewModel", "任务已取消")
                FloatingWindowService.getInstance()?.updateStatus("已停止", 0, "用户手动停止")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "错误: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
                currentTaskJob = null
            }
        }
    }

    /**
     * 停止当前任务
     */
    fun stopTask() {
        currentTaskJob?.cancel()
        currentTaskJob = null
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isPaused = false,
            error = "任务已手动停止"
        )
        FloatingWindowService.getInstance()?.updatePauseStatus(false)
    }

    /**
     * 暂停/继续任务
     */
    fun togglePause() {
        val newState = !_uiState.value.isPaused
        _uiState.value = _uiState.value.copy(isPaused = newState)
        FloatingWindowService.getInstance()?.updatePauseStatus(newState)
    }
    
    private suspend fun executeTaskLoop(userPrompt: String, modelName: String) {
        val accessibilityService = AutoGLMAccessibilityService.getInstance() ?: return
        val client = modelClient ?: return
        val executor = actionExecutor ?: return
        
        var stepCount = 0
        val maxSteps = 50
        
        var retryCount = 0

        // 获取图片压缩配置
        val compressionEnabled = preferencesRepository.getImageCompressionEnabledSync()
        val compressionLevel = if (compressionEnabled) preferencesRepository.getImageCompressionLevelSync() else 80
        
        // 获取图片缩放配置
        val scaleEnabled = preferencesRepository.getScreenScaleEnabledSync()
        val scaleFactor = if (scaleEnabled) preferencesRepository.getScreenScaleFactorSync() else 1.0f
        
        while (stepCount < maxSteps) {
            val stepStartTime = System.currentTimeMillis()
            
            // 检查暂停状态
            while (_uiState.value.isPaused) {
                delay(500)
                yield() // 检查协程是否已被取消，比直接访问 coroutineContext 更安全
            }

            Log.d("ChatViewModel", "执行步骤 $stepCount")
            
            // 更新悬浮窗状态
            FloatingWindowService.getInstance()?.updateStatus("执行中", stepCount, "正在检测当前状态...")

            // 使用 safeCurrentApp 获取当前应用 (包含 rootInActiveWindow 兜底)
            val currentApp = accessibilityService.safeCurrentApp
            Log.d("ChatViewModel", "当前应用: $currentApp")

            val myPackageName = getApplication<Application>().packageName
            val isAutoGLMForeground = currentApp == myPackageName

            // 如果已经在任务中（非第一步）且回到了主应用前台，通常建议等待用户切换回目标应用，避免模型在主应用内乱操作
            if (isAutoGLMForeground && stepCount > 0) {
                Log.d("ChatViewModel", "任务执行中检测到回到本应用，等待用户切换...")
                FloatingWindowService.getInstance()?.updateStatus("执行中", stepCount, "等待切回目标应用...")
                delay(2000)
                continue
            }
            
            // 截图：如果当前在本应用前台，跳过截图发送以保护隐私
            val screenshotStartTime = System.currentTimeMillis()
            val originalScreenshot = if (isAutoGLMForeground) {
                Log.d("ChatViewModel", "当前在本应用前台，跳过截图以保护隐私")
                null
            } else {
                accessibilityService.takeScreenshotSuspend()
            }
            val screenshotDuration = System.currentTimeMillis() - screenshotStartTime
            
            if (originalScreenshot == null && !isAutoGLMForeground) {
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
            if (originalScreenshot != null) {
                val isEmulator = DeviceUtils.isEmulator()
                if (BitmapUtils.isBitmapBlack(originalScreenshot)) {
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
            }
            
            // 处理图片缩放和压缩
            var imgToSend = originalScreenshot
            var finalWidth = originalScreenshot?.width ?: 0
            var finalHeight = originalScreenshot?.height ?: 0
            var originalWidth = originalScreenshot?.width ?: 0
            var originalHeight = originalScreenshot?.height ?: 0
            var imgSizeKb = 0.0

            if (originalScreenshot != null) {
                // 如果开启了缩放
                if (scaleEnabled && scaleFactor < 1.0f) {
                    imgToSend = BitmapUtils.scaleBitmap(originalScreenshot, scaleFactor)
                    finalWidth = imgToSend.width
                    finalHeight = imgToSend.height
                }
                
                // 估算压缩后的大小
                val stream = java.io.ByteArrayOutputStream()
                imgToSend?.compress(Bitmap.CompressFormat.JPEG, compressionLevel, stream)
                imgSizeKb = stream.size() / 1024.0
            }

            // 构建用户/屏幕信息消息
            val userMsg = if (stepCount == 0) {
                client.createUserMessage(userPrompt, imgToSend, currentApp, compressionLevel, finalWidth, finalHeight)
            } else {
                client.createScreenInfoMessage(imgToSend, currentApp, compressionLevel, finalWidth, finalHeight)
            }
            
            // 发送前移除旧消息中的图片以节省 Token
            val messagesToSend = messageContext.map { client.removeImagesFromMessage(it) } + userMsg

            // 发送给模型
            val networkStartTime = System.currentTimeMillis()
            FloatingWindowService.getInstance()?.updateStatus("执行中", stepCount, "正在思考下一步操作...")
            
            val response = try {
                client.request(messagesToSend, modelName)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "网络请求失败", e)
                if (retryCount < 2) {
                    retryCount++
                    FloatingWindowService.getInstance()?.updateStatus("执行中", stepCount, "请求失败，正在重试 ($retryCount/2)...")
                    delay(2000)
                    continue
                }
                throw e
            }
            val networkDuration = System.currentTimeMillis() - networkStartTime
            retryCount = 0

            val assistantMessage = ChatMessage(
                id = System.currentTimeMillis().toString(),
                role = MessageRole.ASSISTANT,
                content = response.action,
                thinking = response.thinking,
                action = response.action
            )

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + assistantMessage
            )
            
            // 更新 Network 上下文
            messageContext.add(userMsg)
            messageContext.add(client.createAssistantMessage(response.thinking, response.action))
            
            // 保存对话历史
            saveCurrentMessages()

            // 执行动作
            val executionStartTime = System.currentTimeMillis()
            FloatingWindowService.getInstance()?.updateStatus("执行中", stepCount, "正在执行动作: ${response.action}")
            
            val executeResult = executor.execute(response.action, finalWidth, finalHeight)
            val executionDuration = System.currentTimeMillis() - executionStartTime
            
            // 记录耗时
            val totalDuration = System.currentTimeMillis() - stepStartTime
            val timing = StepTiming(
                step = stepCount,
                screenshotMs = screenshotDuration,
                networkMs = networkDuration,
                executionMs = executionDuration,
                totalMs = totalDuration,
                imageWidth = finalWidth,
                imageHeight = finalHeight,
                imageSizeKb = imgSizeKb,
                originalWidth = originalWidth,
                originalHeight = originalHeight
            )
            stepTimings.add(timing)

            if (!executeResult.success) {
                _uiState.value = _uiState.value.copy(
                    error = "动作执行失败: ${executeResult.message}"
                )
                FloatingWindowService.getInstance()?.updateStatus("执行失败", stepCount, executeResult.message ?: "未知错误")
                break
            }

            // 如果是结束动作，在执行完 executor.execute 之后再跳出循环
            // 这样 executor 内部的 bringAppToForeground 才有机会被执行
            if (response.action.contains("finish(") || response.action.contains("\"finish\"")) {
                _uiState.value = _uiState.value.copy(
                    taskCompletedMessage = executeResult.message ?: response.action
                )
                FloatingWindowService.getInstance()?.updateStatus("已完成", stepCount, executeResult.message ?: "任务已完成")
                break
            }

            stepCount++
            // 动作执行后等待一小段时间让 UI 响应
            delay(1000)
        }

        if (stepCount >= maxSteps) {
            _uiState.value = _uiState.value.copy(
                error = "达到最大步数限制"
            )
            FloatingWindowService.getInstance()?.updateStatus("已停止", stepCount, "达到最大步数限制")
        }
    }

    /**
     * 获取当前发送给模型的完整提示词日志
     */
    fun getFullPromptLog(): String {
        if (messageContext.isNotEmpty()) {
            return formatMessageContext(messageContext)
        }
        
        val messages = _uiState.value.messages
        if (messages.isNotEmpty()) {
            return buildString {
                append("--- 从历史消息恢复的日志 (不含图片详情) ---\n\n")
                messages.forEach { msg ->
                    append("[${msg.role}]:\n")
                    if (!msg.thinking.isNullOrBlank()) {
                        append("<thinking>\n${msg.thinking}\n</thinking>\n")
                    }
                    append(msg.content)
                    append("\n\n" + "-".repeat(20) + "\n\n")
                }
            }
        }
        
        return ""
    }

    private fun formatMessageContext(context: List<NetworkChatMessage>): String {
        val result = StringBuilder()
        var assistantCount = 0
        
        context.forEachIndexed { index, msg ->
            val role = msg.role.uppercase()
            val content = msg.content.joinToString("\n") { item ->
                if (item.type == "text") item.text ?: ""
                else "[IMAGE CONTENT]"
            }
            
            result.append("[$role]:\n$content")
            
            if (role == "ASSISTANT") {
                if (assistantCount < stepTimings.size) {
                    val timing = stepTimings[assistantCount]
                    result.append("\n\n[TIMING & IMAGE INFO - Step ${timing.step}]:")
                    if (timing.imageWidth > 0) {
                        val scaleText = if (timing.originalWidth > timing.imageWidth) {
                            " (Scaled from ${timing.originalWidth}x${timing.originalHeight})"
                        } else ""
                        result.append("\n- Image: ${timing.imageWidth}x${timing.imageHeight}$scaleText, size: ${"%.1f".format(timing.imageSizeKb)}KB")
                    }
                    result.append("\n- Screenshot: ${timing.screenshotMs}ms")
                    result.append("\n- Network (LLM): ${timing.networkMs}ms")
                    result.append("\n- Action Execution: ${timing.executionMs}ms")
                    result.append("\n- Step Total: ${timing.totalMs}ms")
                }
                assistantCount++
            }
            
            if (index < context.size - 1) {
                result.append("\n\n" + "-".repeat(20) + "\n\n")
            }
        }
        
        return result.toString()
    }

    fun getStepTimings(): List<StepTiming> = stepTimings
}
