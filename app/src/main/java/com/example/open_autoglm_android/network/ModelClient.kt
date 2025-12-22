package com.example.open_autoglm_android.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.open_autoglm_android.network.dto.*
import com.google.gson.JsonObject
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
    private val apiKey: String
) {
    private val api: AutoGLMApi
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Authorization", if (apiKey.isBlank() || apiKey == "EMPTY") "Bearer EMPTY" else "Bearer $apiKey")
                val request = requestBuilder.build()
                chain.proceed(request)
            }
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
        modelName: String
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
        
        val response = api.chatCompletion(request)
        
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
     * 创建消息的通用基础方法
     */
    private fun createMessage(
        text: String,
        screenshot: Bitmap?,
        currentApp: String?,
        quality: Int = 80,
        width: Int? = null,
        height: Int? = null
    ): ChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfoJson = buildScreenInfo(currentApp, width, height)
        val fullText = if (text.isEmpty()) screenInfoJson else "$text\n\n$screenInfoJson"

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

        userContent.add(ContentItem(type = "text", text = fullText))
        return ChatMessage(role = "user", content = userContent)
    }

    /**
     * 创建用户消息（第一次调用，包含原始任务）
     */
    fun createUserMessage(userPrompt: String, screenshot: Bitmap?, currentApp: String?, quality: Int = 80, width: Int? = null, height: Int? = null): ChatMessage {
        return createMessage(userPrompt, screenshot, currentApp, quality, width, height)
    }
    
    /**
     * 创建屏幕信息消息（后续调用，只包含屏幕信息）
     */
    fun createScreenInfoMessage(screenshot: Bitmap?, currentApp: String?, quality: Int = 80, width: Int? = null, height: Int? = null): ChatMessage {
        return createMessage("** Screen Info **", screenshot, currentApp, quality, width, height)
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
     * 构建屏幕信息（使用 JsonObject 确保转义安全）
     */
    private fun buildScreenInfo(currentApp: String?, width: Int? = null, height: Int? = null): String {
        val json = JsonObject()
        json.addProperty("current_app", currentApp ?: "Unknown")
        if (width != null && height != null) {
            json.addProperty("screen_width", width)
            json.addProperty("screen_height", height)
        }
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
        return """
今天的日期是：${java.time.LocalDate.now()}

你是一个移动端任务执行智能体（AutoGLM Mobil Agent）。

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
6. 如果没有屏幕图片执行打开app，或者回到首页

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
        
        var thinking = ""
        var action = ""
        
        if (content.contains("finish(message=")) {
            val parts = content.split("finish(message=", limit = 2)
            thinking = parts[0].trim()
            action = "finish(message=" + parts[1]
        } else if (content.contains("do(action=")) {
            val parts = content.split("do(action=", limit = 2)
            thinking = parts[0].trim()
            action = "do(action=" + parts[1]
        } else if (content.contains("<answer>")) {
            val parts = content.split("<answer>", limit = 2)
            thinking = parts[0]
                .replace("<think>", "")
                .replace("</think>", "")
                .replace("<redacted_reasoning>", "")
                .replace("</redacted_reasoning>", "")
                .trim()
            action = parts[1].replace("</answer>", "").trim()
        } else {
            action = content.trim()
        }
        
        if (!action.startsWith("{") && !action.startsWith("do(") && !action.startsWith("finish(")) {
            val funcMatch = Regex("""(do|finish)\s*\([^)]+\)""", RegexOption.IGNORE_CASE).find(content)
            if (funcMatch != null) {
                action = funcMatch.value
            } else {
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
