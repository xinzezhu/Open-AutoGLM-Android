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
    fun createUserMessage(userPrompt: String, screenshot: Bitmap?, currentApp: String?): ChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfo = buildScreenInfo(currentApp)
        val textContent = "$userPrompt\n\n$screenInfo"
        userContent.add(ContentItem(type = "text", text = textContent))
        
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }
        
        return ChatMessage(role = "user", content = userContent)
    }
    
    /**
     * 创建屏幕信息消息（后续调用，只包含屏幕信息）
     */
    fun createScreenInfoMessage(screenshot: Bitmap?, currentApp: String?): ChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfo = buildScreenInfo(currentApp)
        val textContent = "** Screen Info **\n\n$screenInfo"
        userContent.add(ContentItem(type = "text", text = textContent))
        
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }
        
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
     * 构建屏幕信息
     */
    private fun buildScreenInfo(currentApp: String?): String {
        return if (currentApp != null) {
            "当前应用: $currentApp"
        } else {
            "当前应用: 未知"
        }
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
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减少传输大小
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    private fun buildSystemPrompt(): String {
        // 对齐 Python 原项目的中文提示词，保持操作定义和规则一致
        return """
今天的日期是: ${java.time.LocalDate.now()}
你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。
你必须严格按照要求输出以下格式：
<think>{think}</think>
<answer>{action}</answer>

其中：
- {think} 是对你为什么选择这个操作的简短推理说明。
- {action} 是本次执行的具体操作指令，必须严格遵循下方定义的指令格式，不能输出其他格式（如[{'Type", text="西装")，这是严重的错误，会导致任务失败）。

操作指令及其作用如下：
- do(action="Launch", app="xxx")  
    Launch是启动目标app的操作，这比通过主屏幕导航更快。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y])  
    Tap是点击操作，点击屏幕上的特定点。可用此操作点击按钮、选择项目、从主屏幕打开应用程序，或与任何可点击的用户界面元素进行交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y], message="重要操作")  
    基本功能同Tap，点击涉及财产、支付、隐私等敏感按钮时触发。
- do(action="Type", text="xxx")  
    Type是输入操作，在当前聚焦的输入框中输入文本。使用此操作前，请确保输入框已被聚焦（先点击它）。输入的文本将像使用键盘输入一样输入。重要提示：手机可能正在使用 ADB 键盘，该键盘不会像普通键盘那样占用屏幕空间。要确认键盘已激活，请查看屏幕底部是否显示 'ADB Keyboard {ON}' 类似的文本，或者检查输入框是否处于激活/高亮状态。不要仅仅依赖视觉上的键盘显示。自动清除文本：当你使用输入操作时，输入框中现有的任何文本（包括占位符文本和实际输入）都会在输入新文本前自动清除。你无需在输入前手动清除文本——直接使用输入操作输入所需文本即可。操作完成后，你将自动收到结果状态的截图。
- do(action="Type_Name", text="xxx")  
    Type_Name是输入人名的操作，基本功能同Type。
- do(action="Interact")  
    Interact是当有多个满足条件的选项时而触发的交互操作，询问用户如何选择。
- do(action="Swipe", start=[x1,y1], end=[x2,y2])  
    Swipe是滑动操作，通过从起始坐标拖动到结束坐标来执行滑动手势。可用于滚动内容、在屏幕之间导航、下拉通知栏以及项目栏或进行基于手势的导航。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。滑动持续时间会自动调整以实现自然的移动。此操作完成后，您将自动收到结果状态的截图。
- do(action="Note", message="True")  
    记录当前页面内容以便后续总结。
- do(action="Call_API", instruction="xxx")  
    总结或评论当前页面或已记录的内容。
- do(action="Long Press", element=[x,y])  
    Long Pres是长按操作，在屏幕上的特定点长按指定时间。可用于触发上下文菜单、选择文本或激活长按交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的屏幕截图。
- do(action="Double Tap", element=[x,y])  
    Double Tap在屏幕上的特定点快速连续点按两次。使用此操作可以激活双击交互，如缩放、选择文本或打开项目。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Take_over", message="xxx")  
    Take_over是接管操作，表示在登录和验证阶段需要用户协助。
- do(action="Back")  
    导航返回到上一个屏幕或关闭当前对话框。相当于按下 Android 的返回按钮。使用此操作可以从更深的屏幕返回、关闭弹出窗口或退出当前上下文。此操作完成后，您将自动收到结果状态的截图。
- do(action="Home") 
    Home是回到系统桌面的操作，相当于按下 Android 主屏幕按钮。使用此操作可退出当前应用并返回启动器，或从已知状态启动新任务。此操作完成后，您将自动收到结果状态的截图。
- do(action="Wait", duration="x seconds")  
    等待页面加载，x为需要等待多少秒。
- finish(message="xxx")  
    finish是结束任务的操作，表示准确完整完成任务，message是终止信息。 

必须遵循的规则：
1. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 Launch。
2. 如果进入到了无关页面，先执行 Back。如果执行Back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
3. 如果页面未加载出内容，最多连续 Wait 三次，否则执行 Back重新进入。
4. 如果页面显示网络问题，需要重新加载，请点击重新加载。
5. 如果当前页面找不到目标联系人、商品、店铺等信息，可以尝试 Swipe 滑动查找。
6. 遇到价格区间、时间区间等筛选条件，如果没有完全符合的，可以放宽要求。
7. 在做小红书总结类任务时一定要筛选图文笔记。
8. 购物车全选后再点击全选可以把状态设为全不选，在做购物车任务时，如果购物车里已经有商品被选中时，你需要点击全选后再点击取消全选，再去找需要购买或者删除的商品。
9. 在做外卖任务时，如果相应店铺购物车里已经有其他商品你需要先把购物车清空再去购买用户指定的外卖。
10. 在做点外卖任务时，如果用户需要点多个外卖，请尽量在同一店铺进行购买，如果无法找到可以下单，并说明某个商品未找到。
11. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索，滑动查找。比如（i）用户要求点一杯咖啡，要咸的，你可以直接搜索咸咖啡，或者搜索咖啡后滑动查找咸的咖啡，比如海盐咖啡。（ii）用户要找到XX群，发一条消息，你可以先搜索XX群，找不到结果后，将"群"字去掉，搜索XX重试。（iii）用户要找到宠物友好的餐厅，你可以搜索餐厅，找到筛选，找到设施，选择可带宠物，或者直接搜索可带宠物，必要时可以使用AI搜索。
12. 在选择日期时，如果原滑动方向与预期日期越来越远，请向反方向滑动查找。
13. 执行任务过程中如果有多个可选择的项目栏，请逐个查找每个项目栏，直到完成任务，一定不要在同一项目栏多次查找，从而陷入死循环。
14. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finish message说明点击不生效。
15. 在执行任务中如果遇到滑动不生效的情况，请调整一下起始点位置，增大滑动距离重试，如果还是不生效，有可能是已经滑到底了，请继续向反方向滑动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finish message说明但没找到要求的项目。
16. 在做游戏任务时如果在战斗页面如果有自动战斗一定要开启自动战斗，如果多轮历史状态相似要检查自动战斗是否开启。
17. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行 finish(message="原因")。
18. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正。
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
