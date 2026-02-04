package com.example.open_autoglm_android.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.open_autoglm_android.R
import com.example.open_autoglm_android.network.dto.*
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 模型响应数据类
 * 
 * @property thinking 模型的思考过程或推理过程
 * @property action 模型输出的操作指令（如 do(action=...)、finish(message=...) 等）
 */
data class ModelResponse(
    val thinking: String,
    val action: String
)

/**
 * 模型客户端
 * 
 * 负责与 AI 模型 API 进行通信，支持多种模型（如智谱 AutoGLM、阿里云百炼 GUI-plus）。
 * 使用 OpenAI 兼容的 API 格式进行推理请求。
 * 
 * @param context Android 上下文，用于访问资源
 * @param baseUrl 模型 API 的基础 URL
 * @param apiKey 模型 API 的密钥
 */
class ModelClient(
    private val context: Context,
    baseUrl: String,
    private val apiKey: String
) {
    private val api: AutoGLMApi
    private val baseUrl: String = baseUrl  // 保存 baseUrl 用于判断提供商
    
    init {
        // 配置 HTTP 日志拦截器，用于调试
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // 构建 OkHttp 客户端，配置认证和超时
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    // 添加 Authorization 头，使用 Bearer Token 认证
                    .header("Authorization", if (apiKey.isBlank() || apiKey == "EMPTY") "Bearer EMPTY" else "Bearer $apiKey")
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时 30 秒
            .readTimeout(120, TimeUnit.SECONDS)    // 读取超时 120 秒
            .writeTimeout(120, TimeUnit.SECONDS)   // 写入超时 120 秒
            .build()
        
        // 验证并修复URL格式
        val validatedBaseUrl = validateAndFixUrl(baseUrl)
        
        // 配置 Gson，默认不序列化 null 值（这对于阿里云等不支持某些参数的 API 很重要）
        val gson = com.google.gson.GsonBuilder()
            .create()
        
        // 构建 Retrofit 实例，使用配置好的 Gson 转换器
        val retrofit = Retrofit.Builder()
            .baseUrl(validatedBaseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        api = retrofit.create(AutoGLMApi::class.java)
    }
    
    /**
     * 验证并修复URL格式
     */
    private fun validateAndFixUrl(url: String): String {
        var fixedUrl = url.trim()
        // 如果URL没有协议头，添加默认的https协议
        if (!fixedUrl.startsWith("http://") && !fixedUrl.startsWith("https://")) {
            fixedUrl = "https://$fixedUrl"
        }
        return fixedUrl
    }
    
    /**
     * 判断是否为阿里云百炼 API
     * 通过检查 baseUrl 是否包含 dashscope.aliyuncs.com 来判断
     */
    private fun isAliyunProvider(): Boolean {
        return baseUrl.contains("dashscope.aliyuncs.com", ignoreCase = true)
    }
    
    /**
     * 请求模型进行推理（使用消息上下文）
     * 
     * 此方法向配置的 AI 模型发送推理请求，支持智谱 AutoGLM、阿里云百炼 GUI-plus 等多模态模型。
     * 
     * **重要说明**：不同模型提供商支持的参数有所不同：
     * - 智谱 AutoGLM：支持 frequency_penalty 参数
     * - 阿里云百炼：不支持 frequency_penalty 参数，使用该参数会导致 4000 错误
     * 
     * @param messages 消息列表，包含系统提示、用户输入（文本+截图）、历史对话等
     * @param modelName 模型名称，如 "autoglm-phone"、"gui-plus" 等
     * @return ModelResponse 包含模型的思考过程和操作指令
     */
    suspend fun request(
        messages: List<ChatMessage>,
        modelName: String
    ): ModelResponse {
        // 根据提供商构建符合要求的请求体
        // 阿里云百炼不支持 frequency_penalty 参数，需要特殊处理
        val request = if (isAliyunProvider()) {
            // 阿里云百炼 API：不包含 frequency_penalty 参数
            ChatRequest(
                model = modelName,
                messages = messages,
                maxTokens = 3000,  // 最大生成 token 数量
                temperature = 0.0,  // 温度设为 0，确保输出稳定性
                topP = 0.85,  // Top-p 采样参数
                frequencyPenalty = null,  // 阿里云不支持此参数，设为 null
                stream = false  // 不使用流式输出
            )
        } else {
            // 其他提供商（如智谱 AutoGLM）：包含完整参数
            ChatRequest(
                model = modelName,
                messages = messages,
                maxTokens = 3000,  // 最大生成 token 数量
                temperature = 0.0,  // 温度设为 0，确保输出稳定性
                topP = 0.85,  // Top-p 采样参数
                frequencyPenalty = 0.2,  // 频率惩罚，减少重复内容
                stream = false  // 不使用流式输出
            )
        }
        
        // 发送 HTTP 请求到模型 API
        val response = api.chatCompletion(request)
        
        // 处理响应结果
        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            // 提取模型返回的文本内容
            val content = responseBody.choices.firstOrNull()?.message?.content ?: ""
            // 解析响应内容，提取思考过程和操作指令
            return parseResponse(content)
        } else {
            throw Exception("API request failed: ${response.code()} ${response.message()}")
        }
    }
    
    /**
     * 创建系统消息
     * 
     * 系统消息包含 AI 的角色定义、操作规则、输出格式等指导信息。
     * 模型会根据系统消息的指引来执行任务和生成响应。
     */
    fun createSystemMessage(): ChatMessage {
        val systemPrompt = buildSystemPrompt()
        return ChatMessage(
            role = "system",
            content = listOf(ContentItem(type = "text", text = systemPrompt))
        )
    }
    
    /**
     * 创建消息的通用基础方法
     * 
     * 将用户输入、屏幕截图和当前应用信息组合成一条消息。
     * 支持多模态输入（图片 + 文本），适用于视觉理解模型如 AutoGLM 和 GUI-plus。
     * 
     * @param text 用户输入的文本（任务描述或屏幕信息标识）
     * @param screenshot 当前屏幕的截图，用于模型理解界面内容
     * @param currentApp 当前正在使用的应用包名或名称
     * @param quality 图片压缩质量（0-100），默认 80
     * @return ChatMessage 包含图片和文本的用户消息
     */
    private fun createMessage(
        text: String,
        screenshot: Bitmap?,
        currentApp: String?,
        quality: Int = 80
    ): ChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfoJson = buildScreenInfo(currentApp)
        val fullText = if (text.isEmpty()) screenInfoJson else "$text\n\n$screenInfoJson"

        // 多模态消息格式：先添加图片，再添加文本
        // 这种顺序与 OpenAI Vision API 和阿里云百炼 GUI-plus 模型的推荐格式一致
        screenshot?.let { bitmap ->
            // 将截图转换为 Base64 编码的 JPEG 图片
            val base64Image = bitmapToBase64(bitmap, quality)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }

        // 添加文本内容（包含任务描述和屏幕信息）
        userContent.add(ContentItem(type = "text", text = fullText))
        return ChatMessage(role = "user", content = userContent)
    }

    /**
     * 创建用户消息（首次调用，包含原始任务）
     * 
     * 用于任务的首次请求，包含用户的任务描述、屏幕截图和当前应用信息。
     * 
     * @param userPrompt 用户输入的任务描述，如"打开微信"、"在美团上搜索奶茶"等
     * @param screenshot 当前屏幕的截图
     * @param currentApp 当前正在使用的应用
     * @param quality 图片压缩质量，默认 80
     */
    fun createUserMessage(userPrompt: String, screenshot: Bitmap?, currentApp: String?, quality: Int = 80): ChatMessage {
        return createMessage(userPrompt, screenshot, currentApp, quality)
    }
    
    /**
     * 创建屏幕信息消息（后续调用，只包含屏幕信息）
     * 
     * 用于任务执行过程中的后续请求，只包含最新的屏幕状态，不重复任务描述。
     * 这样可以节省 token 消耗，同时让模型专注于当前屏幕状态的分析。
     * 
     * @param screenshot 当前屏幕的截图
     * @param currentApp 当前正在使用的应用
     * @param quality 图片压缩质量，默认 80
     */
    fun createScreenInfoMessage(screenshot: Bitmap?, currentApp: String?, quality: Int = 80): ChatMessage {
        return createMessage("** Screen Info **", screenshot, currentApp, quality)
    }
    
    /**
     * 创建助手消息（添加到上下文）
     * 
     * 将模型的响应添加到对话历史中，用于维护多轮对话的上下文。
     * 
     * @param thinking 模型的思考过程
     * @param action 模型输出的操作指令
     * @return ChatMessage 助手角色的消息
     */
    fun createAssistantMessage(thinking: String, action: String): ChatMessage {
        val content = "<think>$thinking</think><answer>$action</answer>"
        return ChatMessage(
            role = "assistant",
            content = listOf(ContentItem(type = "text", text = content))
        )
    }
    
    /**
     * 构建屏幕信息（使用 JsonObject 确保转义安全）
     */
    private fun buildScreenInfo(currentApp: String?): String {
        val json = JsonObject()
        json.addProperty("current_app", currentApp ?: "Unknown")
        return json.toString()
    }
    
    /**
     * 从消息中移除图片内容，只保留文本（节省 token）
     */
    fun removeImagesFromMessage(message: ChatMessage): ChatMessage {
        val textOnlyContent = message.content.filter { it.type == "text" }
        return ChatMessage(
            role = message.role,
            content = textOnlyContent
        )
    }
    
    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
        return ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        }
    }
    
    private fun buildSystemPrompt(): String {
        val template = context.getString(R.string.system_prompt_template)
        return String.format(template, java.time.LocalDate.now())
    }
    
    /**
     * 解析模型响应内容
     * 
     * 从模型返回的文本中提取思考过程和操作指令。
     * 支持多种格式：
     * 1. XML格式：<think>思考过程</think><answer>操作指令</answer>
     * 2. 函数调用格式：思考过程 + do(action=...) 或 finish(message=...)
     * 3. 纯JSON格式：直接返回操作指令
     * 
     * @param content 模型返回的原始文本内容
     * @return ModelResponse 包含分离后的思考过程和操作指令
     */
    private fun parseResponse(content: String): ModelResponse {
        Log.d("ModelClient", "解析响应内容: ${content.take(500)}")
        
        var thinking = ""
        var action = ""
        
        // 解析 finish(message=...) 格式
        if (content.contains("finish(message=")) {
            val parts = content.split("finish(message=", limit = 2)
            thinking = parts[0].trim()
            action = "finish(message=" + parts[1]
        } 
        // 解析 do(action=...) 格式
        else if (content.contains("do(action=")) {
            val parts = content.split("do(action=", limit = 2)
            thinking = parts[0].trim()
            action = "do(action=" + parts[1]
        } 
        // 解析 XML 格式：<think>...</think><answer>...</answer>
        else if (content.contains("<answer>")) {
            val parts = content.split("<answer>", limit = 2)
            thinking = parts[0]
                .replace("<think>", "")
                .replace("</think>", "")
                .replace("<redacted_reasoning>", "")
                .replace("</redacted_reasoning>", "")
                .trim()
            action = parts[1].replace("</answer>", "").trim()
        } 
        // 其他格式，直接作为操作指令
        else {
            action = content.trim()
        }
        
        // 如果操作指令不是标准格式，尝试进一步提取
        if (!action.startsWith("{") && !action.startsWith("do(") && !action.startsWith("finish(")) {
            // 使用正则表达式查找函数调用格式
            val funcMatch = Regex("""(do|finish)\s*\([^)]+\)""", RegexOption.IGNORE_CASE).find(content)
            if (funcMatch != null) {
                action = funcMatch.value
            } else {
                // 尝试提取 JSON 格式的操作指令
                val extractedJson = extractJsonFromContent(content)
                if (extractedJson.isNotEmpty()) {
                    action = extractedJson
                }
            }
        }
        
        return ModelResponse(thinking = thinking, action = action)
    }
    
    private fun extractJsonFromContent(content: String): String {
        var startIndex = -1
        var braceCount = 0
        val candidates = mutableListOf<String>()
        
        for (i in content.indices) {
            when (content[i]) {
                '{' -> {
                    if (startIndex == -1) startIndex = i
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && startIndex != -1) {
                        val candidate = content.substring(startIndex, i + 1)
                        try {
                            com.google.gson.JsonParser.parseString(candidate)
                            candidates.add(candidate)
                        } catch (e: Exception) {}
                        startIndex = -1
                    }
                }
            }
        }
        return candidates.firstOrNull() ?: ""
    }
    
    private fun String.ensureTrailingSlash(): String {
        return if (this.endsWith("/")) this else "$this/"
    }
}
