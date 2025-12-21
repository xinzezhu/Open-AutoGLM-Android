package com.example.open_autoglm_android.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.open_autoglm_android.network.dto.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ModelResponse(
    val thinking: String,
    val action: String
)

class ModelClient(
    baseUrl: String,
    apiKey: String
) {
    private val api: AutoGLMApi
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        api = retrofit.create(AutoGLMApi::class.java)
    }
    
    /**
     * 请求模型（使用消息上下文）
     */
    suspend fun request(
        messages: List<ChatMessage>,
        modelName: String,
        apiKey: String
    ): ModelResponse {
        val request = ChatRequest(
            model = modelName,
            messages = messages,
            maxTokens = 3000,
            temperature = 0.0,
            topP = 0.85,
            frequencyPenalty = 0.2,
            stream = false
        )
        
        val authHeader = if (apiKey.isNotEmpty() && apiKey != "EMPTY") {
            "Bearer $apiKey"
        } else {
            "Bearer EMPTY"
        }
        
        val response = api.chatCompletion(authHeader, request)
        
        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            val content = responseBody.choices.firstOrNull()?.message?.content ?: ""
            return parseResponse(content)
        } else {
            throw Exception("API request failed: ${response.code()} ${response.message()}")
        }
    }
    
    /**
     * 创建系统消息
     */
    fun createSystemMessage(): ChatMessage {
        val systemPrompt = buildSystemPrompt()
        return ChatMessage(
            role = "system",
            content = listOf(ContentItem(type = "text", text = systemPrompt))
        )
    }
    
    /**
     * 创建用户消息（第一次调用，包含原始任务）
     */
    fun createUserMessage(userPrompt: String, screenshot: Bitmap?, currentApp: String?, quality: Int = 80): ChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfoJson = buildScreenInfo(currentApp)
        val textContent = "$userPrompt\n\n$screenInfoJson"

        // 对齐旧项目：先放图片，再放文本
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap, quality)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }

        userContent.add(ContentItem(type = "text", text = textContent))

        return ChatMessage(role = "user", content = userContent)
    }
    
    /**
     * 创建屏幕信息消息（后续调用，只包含屏幕信息）
     */
    fun createScreenInfoMessage(screenshot: Bitmap?, currentApp: String?, quality: Int = 80): ChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfoJson = buildScreenInfo(currentApp)
        val textContent = "** Screen Info **\n\n$screenInfoJson"

        // 对齐旧项目：先放图片，再放文本
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap, quality)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }

        userContent.add(ContentItem(type = "text", text = textContent))

        return ChatMessage(role = "user", content = userContent)
    }
    
    /**
     * 创建助手消息（添加到上下文）
     */
    fun createAssistantMessage(thinking: String, action: String): ChatMessage {
        val content = "<think>$thinking</think><answer>$action</answer>"
        return ChatMessage(
            role = "assistant",
            content = listOf(ContentItem(type = "text", text = content))
        )
    }
    
    /**
     * 构建屏幕信息（与旧项目保持一致，返回 JSON 字符串，如 {"current_app": "System Home"}）
     */
    private fun buildScreenInfo(currentApp: String?): String {
        val appName = currentApp ?: "Unknown"
        // 简单 JSON，字段名与旧项目保持一致
        return """{"current_app": "$appName"}"""
    }
    
    /**
     * 从消息中移除图片内容，只保留文本（节省 token）
     * 参考原项目的 MessageBuilder.remove_images_from_message
     */
    fun removeImagesFromMessage(message: ChatMessage): ChatMessage {
        val textOnlyContent = message.content.filter { it.type == "text" }
        return ChatMessage(
            role = message.role,
            content = textOnlyContent
        )
    }
    
    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减少传输大小
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    private fun buildSystemPrompt(): String {
        // 对齐 Python 原项目的中文提示词，保持操作定义和规则一致
        return """
今天的日期是：${java.time.LocalDate.now()}

你是一个移动端任务执行智能体（Mobile UI Agent）。
你需要根据当前屏幕状态与用户目标，决定并执行**下一步唯一且最合适的操作**。

【输出格式（必须严格遵守）】
你只能输出以下 XML 结构，不能包含任何多余文本：

<think>操作选择的简要理由摘要（不超过 20 字，不展开推理）</think>
<answer>操作指令</answer>

【操作指令白名单】
你只能在 <answer> 中输出以下指令之一，且每次只能输出一个：

- do(action="Launch", app="xxx")
- do(action="Tap", element=[x,y])
- do(action="Tap", element=[x,y], message="重要操作")
- do(action="Type", text="xxx")
- do(action="Type_Name", text="xxx")
- do(action="Swipe", start=[x1,y1], end=[x2,y2])
- do(action="Long Press", element=[x,y])
- do(action="Double Tap", element=[x,y])
- do(action="Wait", duration="x seconds")
- do(action="Back")
- do(action="Home")
- do(action="Interact")
- do(action="Note", message="True")
- do(action="Call_API", instruction="xxx")
- do(action="Take_over", message="xxx")
- finish(message="xxx")

任何未列出的 action、字段、格式，或多个指令同时输出，均视为错误。

【强制执行规则】
1. 执行操作前，必须确认当前 app 是否为目标 app；若不是，先执行 Launch。
2. 页面未加载完成时，最多连续 Wait 三次；仍失败则 Back 重试。
3. 进入无关页面需优先 Back 返回。
4. 执行下一步前必须确认上一步是否生效；点击无效需调整位置或等待重试。
5. 任务完成后必须使用 finish 结束，并说明结果或失败原因。

【执行策略（在不违反上述规则前提下使用）】
- 找不到目标内容时可 Swipe 滑动查找。
- 搜索无结果可调整关键词或返回上一级重试，最多三次。
- 需要用户协助登录或验证时使用 Take_over。
- 若多选项且无法判断，使用 Interact 询问用户。
- 若操作失败但可继续任务，请继续并在 finish 中说明。
""".trimIndent()
    }
    
    
    private fun parseResponse(content: String): ModelResponse {
        Log.d("ModelClient", "解析响应内容: ${content.take(500)}")
        
        // 对齐 Python 版本的解析逻辑：优先按 finish/do 分割，其次 <answer>，最后兜底
        var thinking = ""
        var action = ""
        
        // 规则 1：finish(message= ... )
        if (content.contains("finish(message=")) {
            val parts = content.split("finish(message=", limit = 2)
            thinking = parts[0].trim()
            action = "finish(message=" + parts[1]
            Log.d("ModelClient", "按 finish(message= 分割得到 action: $action")
        }
        // 规则 2：do(action= ... )
        else if (content.contains("do(action=")) {
            val parts = content.split("do(action=", limit = 2)
            thinking = parts[0].trim()
            action = "do(action=" + parts[1]
            Log.d("ModelClient", "按 do(action= 分割得到 action: $action")
        }
        // 规则 3：<answer> 标签
        else if (content.contains("<answer>")) {
            val parts = content.split("<answer>", limit = 2)
            thinking = parts[0]
                .replace("<think>", "")
                .replace("</think>", "")
                .replace("<redacted_reasoning>", "")
                .replace("</redacted_reasoning>", "")
                .trim()
            action = parts[1].replace("</answer>", "").trim()
            Log.d("ModelClient", "按 <answer> 标签解析 action: $action")
        }
        // 规则 4：兜底
        else {
            action = content.trim()
            Log.w("ModelClient", "未命中任何标记，使用全文作为 action")
        }
        
        // 补充：如果 action 仍非 do/finish 且不是 JSON，尝试从正文中再提取一次
        if (!action.startsWith("{") && !action.startsWith("do(") && !action.startsWith("finish(")) {
            val funcMatch = Regex("""(do|finish)\s*\([^)]+\)""", RegexOption.IGNORE_CASE).find(content)
            if (funcMatch != null) {
                action = funcMatch.value
                Log.d("ModelClient", "回退从正文提取函数调用: $action")
            } else {
                val extractedJson = extractJsonFromContent(content)
                if (extractedJson.isNotEmpty()) {
                    action = extractedJson
                    Log.d("ModelClient", "回退从正文提取 JSON: $action")
                }
            }
        }
        
        return ModelResponse(thinking = thinking, action = action)
    }
    
    /**
     * 从内容中提取 JSON 对象
     */
    private fun extractJsonFromContent(content: String): String {
        // 尝试找到 JSON 对象（匹配嵌套的大括号）
        var startIndex = -1
        var braceCount = 0
        val candidates = mutableListOf<String>()
        
        for (i in content.indices) {
            when (content[i]) {
                '{' -> {
                    if (startIndex == -1) {
                        startIndex = i
                    }
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && startIndex != -1) {
                        val candidate = content.substring(startIndex, i + 1)
                        try {
                            // 验证是否是有效的 JSON
                            com.google.gson.JsonParser.parseString(candidate)
                            candidates.add(candidate)
                        } catch (e: Exception) {
                            // 不是有效 JSON，继续查找
                        }
                        startIndex = -1
                    }
                }
            }
        }
        
        // 返回第一个有效的 JSON 对象
        return candidates.firstOrNull() ?: ""
    }
    
    private fun String.ensureTrailingSlash(): String {
        return if (this.endsWith("/")) this else "$this/"
    }
}
