package com.example.open_autoglm_android.domain

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.delay
import java.io.StringReader

data class ExecuteResult(
    val success: Boolean,
    val message: String? = null
)

class ActionExecutor(private val service: AutoGLMAccessibilityService) {
    
    suspend fun execute(actionJson: String, screenWidth: Int, screenHeight: Int): ExecuteResult {
        return try {
            Log.d("ActionExecutor", "开始解析动作: ${actionJson.take(500)}")
            
            // 尝试从文本中提取 JSON 对象
            val jsonString = extractJsonFromText(actionJson)
            Log.d("ActionExecutor", "提取的 JSON: ${jsonString.take(200)}")
            
            // 如果提取的 JSON 为空或与原始文本相同，说明提取失败
            if (jsonString.isEmpty() || jsonString == actionJson.trim()) {
                // 再次尝试修复
                val fixedJson = tryFixMalformedJson(actionJson)
                if (fixedJson.isNotEmpty()) {
                    try {
                        val jsonElement = JsonParser.parseString(fixedJson)
                        if (jsonElement.isJsonObject) {
                            val actionObj = jsonElement.asJsonObject
                            Log.d("ActionExecutor", "修复后解析成功，对象: $actionObj")
                            return processActionObject(actionObj, screenWidth, screenHeight)
                        }
                    } catch (e: Exception) {
                        Log.w("ActionExecutor", "修复后的 JSON 仍然无法解析", e)
                    }
                }
                
                // 如果修复也失败，返回错误
                return ExecuteResult(
                    success = false,
                    message = "无法从响应中提取有效的 JSON 动作。响应内容: ${actionJson.take(200)}"
                )
            }
            
            // 使用 lenient 模式解析 JSON
            val jsonElement = try {
                JsonParser.parseString(jsonString)
            } catch (e: Exception) {
                // 如果标准解析失败，尝试使用 lenient 模式
                Log.w("ActionExecutor", "标准解析失败，尝试 lenient 模式", e)
                try {
                    val reader = JsonReader(StringReader(jsonString))
                    reader.isLenient = true
                    JsonParser.parseReader(reader)
                } catch (e2: Exception) {
                    Log.e("ActionExecutor", "Lenient 模式也失败", e2)
                    // 最后尝试修复
                    val fixedJson = tryFixMalformedJson(jsonString)
                    if (fixedJson.isNotEmpty()) {
                        try {
                            return processActionObject(JsonParser.parseString(fixedJson).asJsonObject, screenWidth, screenHeight)
                        } catch (e3: Exception) {
                            Log.e("ActionExecutor", "修复后仍然无法解析", e3)
                        }
                    }
                    throw e2
                }
            }
            
            // 检查是否是 JSON 对象
            if (!jsonElement.isJsonObject) {
                val errorMsg = if (jsonElement.isJsonPrimitive) {
                    "响应不是 JSON 对象，而是: ${jsonElement.asString.take(100)}"
                } else {
                    "响应不是 JSON 对象"
                }
                throw IllegalStateException(errorMsg)
            }
            
            val actionObj = jsonElement.asJsonObject
            Log.d("ActionExecutor", "解析成功，对象: $actionObj")
            
            processActionObject(actionObj, screenWidth, screenHeight)
        } catch (e: Exception) {
            Log.e("ActionExecutor", "解析动作失败", e)
            ExecuteResult(success = false, message = "解析动作失败: ${e.message}")
        }
    }
    
    private suspend fun processActionObject(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val metadata = actionObj.get("_metadata")?.asString ?: ""
        
        return when (metadata) {
            "finish" -> {
                val message = actionObj.get("message")?.asString ?: "任务完成"
                ExecuteResult(success = true, message = message)
            }
            "do" -> {
                val action = actionObj.get("action")?.asString ?: ""
                executeAction(action, actionObj, screenWidth, screenHeight)
            }
            else -> {
                ExecuteResult(success = false, message = "未知的动作类型: $metadata")
            }
        }
    }
    
    /**
     * 从文本中提取 JSON 对象
     * 尝试找到第一个有效的 JSON 对象，如果找不到则尝试修复格式错误的 JSON
     */
    private fun extractJsonFromText(text: String): String {
        val trimmed = text.trim()
        Log.d("ActionExecutor", "extractJsonFromText 输入: ${trimmed.take(200)}")
        
        // 如果文本已经是有效的 JSON，直接返回
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                // 验证是否是有效的 JSON
                JsonParser.parseString(trimmed)
                Log.d("ActionExecutor", "文本已经是有效的 JSON")
                return trimmed
            } catch (e: Exception) {
                Log.d("ActionExecutor", "文本看起来像 JSON 但解析失败，继续处理")
            }
        }
        
        // 尝试找到所有可能的 JSON 对象
        val jsonCandidates = mutableListOf<String>()
        var startIndex = -1
        var braceCount = 0
        
        for (i in trimmed.indices) {
            when (trimmed[i]) {
                '{' -> {
                    if (startIndex == -1) {
                        startIndex = i
                    }
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && startIndex != -1) {
                        // 找到了一个完整的 JSON 对象
                        val candidate = trimmed.substring(startIndex, i + 1)
                        // 验证是否是有效的 JSON
                        try {
                            JsonParser.parseString(candidate)
                            jsonCandidates.add(candidate)
                            Log.d("ActionExecutor", "找到有效的 JSON 候选: ${candidate.take(100)}")
                        } catch (e: Exception) {
                            // 不是有效 JSON，忽略
                        }
                        startIndex = -1
                    }
                }
            }
        }
        
        // 返回第一个有效的 JSON 对象
        if (jsonCandidates.isNotEmpty()) {
            Log.d("ActionExecutor", "返回第一个有效的 JSON 候选")
            return jsonCandidates.first()
        }
        
        // 如果找不到完整的 JSON，尝试修复格式错误的 JSON
        // 例如：do(action="Launch", app="QQ") -> {"_metadata": "do", "action": "Launch", "app": "QQ"}
        val fixedJson = tryFixMalformedJson(trimmed)
        if (fixedJson.isNotEmpty()) {
            try {
                // 验证修复后的 JSON 是否有效
                JsonParser.parseString(fixedJson)
                Log.d("ActionExecutor", "修复后的 JSON 有效: $fixedJson")
                return fixedJson
            } catch (e: Exception) {
                Log.w("ActionExecutor", "修复后的 JSON 仍然无效", e)
            }
        }
        
        // 如果找不到完整的 JSON，尝试提取第一个 { } 之间的内容
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val candidate = trimmed.substring(firstBrace, lastBrace + 1)
            try {
                JsonParser.parseString(candidate)
                Log.d("ActionExecutor", "从第一个和最后一个大括号提取到有效 JSON")
                return candidate
            } catch (e: Exception) {
                // 不是有效 JSON，尝试修复
                val fixed = tryFixMalformedJson(candidate)
                if (fixed.isNotEmpty()) {
                    try {
                        JsonParser.parseString(fixed)
                        Log.d("ActionExecutor", "修复提取的 JSON 成功")
                        return fixed
                    } catch (e2: Exception) {
                        // 修复失败
                    }
                }
            }
        }
        
        // 如果都找不到，最后再尝试一次修复整个文本
        val finalFixed = tryFixMalformedJson(trimmed)
        if (finalFixed.isNotEmpty()) {
            try {
                JsonParser.parseString(finalFixed)
                Log.d("ActionExecutor", "最后修复尝试成功")
                return finalFixed
            } catch (e: Exception) {
                Log.w("ActionExecutor", "最后修复尝试失败")
            }
        }
        
        // 如果都找不到，返回原始文本（让调用者处理错误）
        Log.w("ActionExecutor", "无法提取或修复 JSON，返回原始文本")
        return trimmed
    }
    
    /**
     * 尝试修复格式错误的 JSON
     * 参考原始项目的 parse_action 函数，支持 do(action="Tap", element=[389,116]) 格式
     * 支持多种格式：
     * 1. do(action="Tap", element=[389,116]) -> {"_metadata": "do", "action": "Tap", "element": [389, 116]}
     * 2. do(action="Launch", app="QQ") -> {"_metadata": "do", "action": "Launch", "app": "QQ"}
     * 3. do(action="Type", text="奶茶") -> {"_metadata": "do", "action": "Type", "text": "奶茶"}
     * 4. finish(message="完成") -> {"_metadata": "finish", "message": "完成"}
     */
    private fun tryFixMalformedJson(text: String): String {
        Log.d("ActionExecutor", "尝试修复格式错误的 JSON: ${text.take(200)}")
        
        // 首先尝试提取 do(...) 或 finish(...) 函数调用
        // 模式: do(...) 或 finish(...)
        val functionCallPattern = Regex("""(do|finish)\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
        val functionMatch = functionCallPattern.find(text)
        
        if (functionMatch != null) {
            val functionName = functionMatch.groupValues[1].lowercase()
            val paramsStr = functionMatch.groupValues[2]
            
            Log.d("ActionExecutor", "找到函数调用: $functionName, 参数: $paramsStr")
            
            if (functionName == "finish") {
                // finish(message="完成") 格式
                val messagePattern = Regex("""message\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val messageMatch = messagePattern.find(paramsStr)
                val message = messageMatch?.groupValues?.get(1) ?: paramsStr.trim().trim('"', '\'')
                val fixed = """{"_metadata": "finish", "message": "$message"}"""
                Log.d("ActionExecutor", "修复 finish 调用: $fixed")
                return fixed
            } else if (functionName == "do") {
                // do(action="Tap", element=[389,116]) 格式
                val action = mutableMapOf<String, Any>("_metadata" to "do")
                
                // 解析参数：key=value 格式，支持字符串和数组
                // 匹配: key="value" 或 key=[1,2,3] 或 key=123
                val paramPattern = Regex("""(\w+)\s*=\s*("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\[[^\]]+\]|\d+\.?\d*|true|false)""", RegexOption.IGNORE_CASE)
                val paramMatches = paramPattern.findAll(paramsStr)
                
                for (match in paramMatches) {
                    val key = match.groupValues[1]
                    var valueStr = match.groupValues[2].trim()
                    
                    // 解析值
                    val value: Any = when {
                        valueStr.startsWith("[") -> {
                            // 数组值，解析为 JSON 数组字符串
                            // 例如: [389,116] -> [389,116]
                            // 确保格式正确（移除空格，如果有）
                            val arrayContent = valueStr.substring(1, valueStr.length - 1)
                            val arrayValues = arrayContent.split(",").map { it.trim() }
                            "[" + arrayValues.joinToString(",") + "]"
                        }
                        valueStr.startsWith("\"") || valueStr.startsWith("'") -> {
                            // 字符串值，移除引号并处理转义
                            valueStr.trim('"', '\'').replace("\\\"", "\"").replace("\\'", "'")
                        }
                        valueStr == "true" -> true
                        valueStr == "false" -> false
                        valueStr.contains(".") -> valueStr.toDoubleOrNull() ?: valueStr
                        else -> valueStr.toIntOrNull() ?: valueStr
                    }
                    
                    action[key] = value
                }
                
                // 转换为 JSON 字符串
                val jsonBuilder = StringBuilder()
                jsonBuilder.append("{")
                jsonBuilder.append("\"_metadata\": \"do\"")
                
                for ((key, value) in action) {
                    if (key == "_metadata") continue
                    
                    jsonBuilder.append(", ")
                    jsonBuilder.append("\"$key\": ")
                    
                    when (value) {
                        is String -> {
                            // 检查是否是数组字符串
                            if (value.startsWith("[")) {
                                jsonBuilder.append(value)
                            } else {
                                jsonBuilder.append("\"${value.replace("\"", "\\\"")}\"")
                            }
                        }
                        is Number -> jsonBuilder.append(value)
                        is Boolean -> jsonBuilder.append(value)
                        else -> {
                            val valueStr = value.toString()
                            if (valueStr.startsWith("[")) {
                                jsonBuilder.append(valueStr)
                            } else {
                                jsonBuilder.append("\"${valueStr.replace("\"", "\\\"")}\"")
                            }
                        }
                    }
                }
                
                jsonBuilder.append("}")
                val fixed = jsonBuilder.toString()
                Log.d("ActionExecutor", "修复 do 调用: $fixed")
                return fixed
            }
        }
        
        // 如果找不到函数调用，尝试其他格式
        // 模式1: do(action="Launch", app="QQ") 或 do(action='Launch', app='QQ')
        val pattern1 = Regex("""do\s*\(\s*action\s*=\s*["']([^"']+)["']\s*,\s*app\s*=\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(text)
        if (match1 != null) {
            val actionName = match1.groupValues[1]
            val appName = match1.groupValues[2]
            val fixed = """{"_metadata": "do", "action": "$actionName", "app": "$appName"}"""
            Log.d("ActionExecutor", "模式1匹配，修复为: $fixed")
            return fixed
        }
        
        // 模式2: do(action="Launch") 只有 action，没有 app
        val pattern1b = Regex("""do\s*\(\s*action\s*=\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE)
        val match1b = pattern1b.find(text)
        if (match1b != null) {
            val actionName = match1b.groupValues[1]
            val fixed = """{"_metadata": "do", "action": "$actionName"}"""
            Log.d("ActionExecutor", "模式1b匹配，修复为: $fixed")
            return fixed
        }
        
        // 模式3: 尝试从文本中提取 action 和 app 的关键词
        // 例如："打开QQ" -> {"_metadata": "do", "action": "Launch", "app": "QQ"}
        val launchPattern = Regex("""(?:打开|启动|运行|launch)\s*([^\s，,。.]+)""", RegexOption.IGNORE_CASE)
        val launchMatch = launchPattern.find(text)
        if (launchMatch != null) {
            val appName = launchMatch.groupValues[1].trim()
            val fixed = """{"_metadata": "do", "action": "Launch", "app": "$appName"}"""
            Log.d("ActionExecutor", "模式3匹配（打开应用），修复为: $fixed")
            return fixed
        }
        
        Log.w("ActionExecutor", "无法修复格式错误的 JSON")
        return ""
    }
    
    /**
     * 将相对坐标（0-1000）转换为绝对像素坐标
     * 参考原项目的 _convert_relative_to_absolute 方法
     */
    private fun convertRelativeToAbsolute(
        element: List<Float>,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Float, Float> {
        val x = (element[0] / 1000f) * screenWidth
        val y = (element[1] / 1000f) * screenHeight
        return Pair(x, y)
    }
    
    private suspend fun executeAction(
        action: String,
        actionObj: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        return when (action.lowercase()) {
            "launch" -> launchApp(actionObj)
            "tap" -> tap(actionObj, screenWidth, screenHeight)
            "type" -> type(actionObj)
            "swipe" -> swipe(actionObj, screenWidth, screenHeight)
            "back" -> back()
            "home" -> home()
            "longpress", "long press" -> longPress(actionObj, screenWidth, screenHeight)
            "doubletap", "double tap" -> doubleTap(actionObj, screenWidth, screenHeight)
            "wait" -> wait(actionObj)
            else -> ExecuteResult(success = false, message = "不支持的操作: $action")
        }
    }
    
    private suspend fun launchApp(actionObj: JsonObject): ExecuteResult {
        val appName = actionObj.get("app")?.asString ?: return ExecuteResult(
            success = false,
            message = "Launch 操作缺少 app 参数"
        )
        
        return try {
            val packageManager = service.packageManager
            val intent = packageManager.getLaunchIntentForPackage(getPackageName(appName))
                ?: return ExecuteResult(success = false, message = "找不到应用: $appName")
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            delay(2000) // 等待应用启动
            ExecuteResult(success = true)
        } catch (e: Exception) {
            ExecuteResult(success = false, message = "启动应用失败: ${e.message}")
        }
    }
    
    private suspend fun tap(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val element = actionObj.get("element")
        
        return if (element?.isJsonArray == true) {
            // 坐标形式 [x, y] - 模型返回的是相对坐标（0-1000），需要转换为绝对像素
            val array = element.asJsonArray
            if (array.size() >= 2) {
                val relativeX = array[0].asFloat
                val relativeY = array[1].asFloat
                
                // 转换为绝对坐标
                val (absoluteX, absoluteY) = convertRelativeToAbsolute(
                    listOf(relativeX, relativeY),
                    screenWidth,
                    screenHeight
                )
                
                Log.d("ActionExecutor", "Tap: 相对坐标 ($relativeX, $relativeY) -> 绝对坐标 ($absoluteX, $absoluteY)")
                service.tap(absoluteX, absoluteY)
                delay(500)
                ExecuteResult(success = true, message = "已点击坐标: ($absoluteX, $absoluteY)")
            } else {
                ExecuteResult(success = false, message = "坐标格式错误")
            }
        } else {
            // 尝试通过文本查找元素
            val text = actionObj.get("text")?.asString
            if (text != null) {
                val node = service.findNodeByText(text)
                if (node != null) {
                    val success = service.performClick(node)
                    node.recycle()
                    delay(500)
                    ExecuteResult(success = success)
                } else {
                    ExecuteResult(success = false, message = "找不到元素: $text")
                }
            } else {
                ExecuteResult(success = false, message = "Tap 操作缺少 element 或 text 参数")
            }
        }
    }
    
    private suspend fun type(actionObj: JsonObject): ExecuteResult {
        val text = actionObj.get("text")?.asString ?: return ExecuteResult(
            success = false,
            message = "Type 操作缺少 text 参数"
        )
        
        // 尝试查找输入框
        val root = service.getRootNode() ?: return ExecuteResult(
            success = false,
            message = "无法获取根节点"
        )
        
        // 查找可编辑的节点 - 使用递归查找
        val inputNode = findEditableNode(root)
        
        if (inputNode != null) {
            val success = service.setText(inputNode, text)
            inputNode.recycle()
            delay(500)
            root.recycle()
            return ExecuteResult(success = success)
        }
        
        root.recycle()
        return ExecuteResult(success = false, message = "找不到输入框")
    }
    
    private suspend fun swipe(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val start = actionObj.get("start")?.asJsonArray
        val end = actionObj.get("end")?.asJsonArray
        
        if (start == null || end == null || start.size() < 2 || end.size() < 2) {
            return ExecuteResult(success = false, message = "Swipe 操作缺少 start 或 end 参数")
        }
        
        // 转换为绝对坐标
        val (startX, startY) = convertRelativeToAbsolute(
            listOf(start[0].asFloat, start[1].asFloat),
            screenWidth,
            screenHeight
        )
        val (endX, endY) = convertRelativeToAbsolute(
            listOf(end[0].asFloat, end[1].asFloat),
            screenWidth,
            screenHeight
        )
        
        Log.d("ActionExecutor", "Swipe: 从 ($startX, $startY) 到 ($endX, $endY)")
        service.swipe(startX, startY, endX, endY)
        delay(500)
        return ExecuteResult(success = true, message = "已滑动从 ($startX, $startY) 到 ($endX, $endY)")
    }
    
    private suspend fun back(): ExecuteResult {
        service.performBack()
        delay(500)
        return ExecuteResult(success = true)
    }
    
    private suspend fun home(): ExecuteResult {
        service.performHome()
        delay(500)
        return ExecuteResult(success = true)
    }
    
    private suspend fun longPress(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val element = actionObj.get("element")?.asJsonArray
        
        if (element == null || element.size() < 2) {
            return ExecuteResult(success = false, message = "LongPress 操作缺少 element 参数")
        }
        
        // 转换为绝对坐标
        val (x, y) = convertRelativeToAbsolute(
            listOf(element[0].asFloat, element[1].asFloat),
            screenWidth,
            screenHeight
        )
        
        Log.d("ActionExecutor", "LongPress: 坐标 ($x, $y)")
        service.longPress(x, y)
        delay(800)
        return ExecuteResult(success = true, message = "已长按坐标: ($x, $y)")
    }
    
    private suspend fun doubleTap(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val element = actionObj.get("element")?.asJsonArray
        
        if (element == null || element.size() < 2) {
            return ExecuteResult(success = false, message = "DoubleTap 操作缺少 element 参数")
        }
        
        // 转换为绝对坐标
        val (x, y) = convertRelativeToAbsolute(
            listOf(element[0].asFloat, element[1].asFloat),
            screenWidth,
            screenHeight
        )
        
        Log.d("ActionExecutor", "DoubleTap: 坐标 ($x, $y)")
        // 双击就是连续两次点击
        service.tap(x, y)
        delay(100)
        service.tap(x, y)
        delay(500)
        return ExecuteResult(success = true, message = "已双击坐标: ($x, $y)")
    }
    
    private suspend fun wait(actionObj: JsonObject): ExecuteResult {
        val duration = actionObj.get("duration")?.asInt ?: 1000
        delay(duration.toLong())
        return ExecuteResult(success = true)
    }
    
    private fun getPackageName(appName: String): String {
        // 应用名到包名映射
        val appPackageMap = mapOf(
            // Social & Messaging
            "微信" to "com.tencent.mm",
            "WeChat" to "com.tencent.mm",
            "wechat" to "com.tencent.mm",
            "QQ" to "com.tencent.mobileqq",
            "qq" to "com.tencent.mobileqq",
            "QQ音乐" to "com.tencent.qqmusic",
            "微博" to "com.sina.weibo",
            // E-commerce
            "淘宝" to "com.taobao.taobao",
            "淘宝闪购" to "com.taobao.taobao",
            "京东" to "com.jingdong.app.mall",
            "京东秒送" to "com.jingdong.app.mall",
            "拼多多" to "com.xunmeng.pinduoduo",
            "Temu" to "com.einnovation.temu",
            "temu" to "com.einnovation.temu",
            // Lifestyle & Social
            "小红书" to "com.xingin.xhs",
            "豆瓣" to "com.douban.frodo",
            "知乎" to "com.zhihu.android",
            // Maps & Navigation
            "高德地图" to "com.autonavi.minimap",
            "百度地图" to "com.baidu.BaiduMap",
            // Food & Services
            "美团" to "com.sankuai.meituan",
            "大众点评" to "com.dianping.v1",
            "饿了么" to "me.ele",
            "肯德基" to "com.yek.android.kfc.activitys",
            // Travel
            "携程" to "ctrip.android.view",
            "铁路12306" to "com.MobileTicket",
            "12306" to "com.MobileTicket",
            "去哪儿" to "com.Qunar",
            "去哪儿旅行" to "com.Qunar",
            "滴滴出行" to "com.sdu.did.psnger",
            // Video & Entertainment
            "bilibili" to "tv.danmaku.bili",
            "抖音" to "com.ss.android.ugc.aweme",
            "快手" to "com.smile.gifmaker",
            "腾讯视频" to "com.tencent.qqlive",
            "爱奇艺" to "com.qiyi.video",
            "优酷视频" to "com.youku.phone",
            "芒果TV" to "com.hunantv.imgo.activity",
            "红果短剧" to "com.phoenix.read",
            "VLC" to "org.videolan.vlc",
            // Music & Audio
            "网易云音乐" to "com.netease.cloudmusic",
            "QQ音乐" to "com.tencent.qqmusic",
            "汽水音乐" to "com.luna.music",
            "喜马拉雅" to "com.ximalaya.ting.android",
            "PiMusicPlayer" to "com.Project100Pi.themusicplayer",
            "pimusicplayer" to "com.Project100Pi.themusicplayer",
            "RetroMusic" to "code.name.monkey.retromusic",
            "retromusic" to "code.name.monkey.retromusic",
            // Reading
            "番茄小说" to "com.dragon.read",
            "番茄免费小说" to "com.dragon.read",
            "七猫免费小说" to "com.kmxs.reader",
            // Productivity
            "飞书" to "com.ss.android.lark",
            "QQ邮箱" to "com.tencent.androidqqmail",
            "GoogleDocs" to "com.google.android.apps.docs.editors.docs",
            "Google Docs" to "com.google.android.apps.docs.editors.docs",
            "GoogleSlides" to "com.google.android.apps.docs.editors.slides",
            "Google Slides" to "com.google.android.apps.docs.editors.slides",
            "GoogleTasks" to "com.google.android.apps.tasks",
            "Google Tasks" to "com.google.android.apps.tasks",
            "Joplin" to "net.cozic.joplin",
            "joplin" to "net.cozic.joplin",
            // AI & Tools
            "豆包" to "com.larus.nova",
            // Health & Fitness
            "keep" to "com.gotokeep.keep",
            "美柚" to "com.lingan.seeyou",
            // News & Information
            "腾讯新闻" to "com.tencent.news",
            "今日头条" to "com.ss.android.article.news",
            // Real Estate
            "贝壳找房" to "com.lianjia.beike",
            "安居客" to "com.anjuke.android.app",
            // Finance
            "同花顺" to "com.hexin.plat.android",
            "Bluecoins" to "com.rammigsoftware.bluecoins",
            "bluecoins" to "com.rammigsoftware.bluecoins",
            // Games
            "星穹铁道" to "com.miHoYo.hkrpg",
            "崩坏：星穹铁道" to "com.miHoYo.hkrpg",
            "恋与深空" to "com.papegames.lysk.cn",
            // System & Common apps (多写几种变体便于匹配)
            "AndroidSystemSettings" to "com.android.settings",
            "Android System Settings" to "com.android.settings",
            "Android  System Settings" to "com.android.settings",
            "Android-System-Settings" to "com.android.settings",
            "Settings" to "com.android.settings",
            "AudioRecorder" to "com.android.soundrecorder",
            "audiorecorder" to "com.android.soundrecorder",
            "Chrome" to "com.android.chrome",
            "chrome" to "com.android.chrome",
            "Google Chrome" to "com.android.chrome",
            "Clock" to "com.android.deskclock",
            "clock" to "com.android.deskclock",
            "Contacts" to "com.android.contacts",
            "contacts" to "com.android.contacts",
            "Duolingo" to "com.duolingo",
            "duolingo" to "com.duolingo",
            "Expedia" to "com.expedia.bookings",
            "expedia" to "com.expedia.bookings",
            "Files" to "com.android.fileexplorer",
            "files" to "com.android.fileexplorer",
            "File Manager" to "com.android.fileexplorer",
            "file manager" to "com.android.fileexplorer",
            "Gmail" to "com.google.android.gm",
            "gmail" to "com.google.android.gm",
            "GoogleMail" to "com.google.android.gm",
            "Google Mail" to "com.google.android.gm",
            "GoogleFiles" to "com.google.android.apps.nbu.files",
            "googlefiles" to "com.google.android.apps.nbu.files",
            "FilesbyGoogle" to "com.google.android.apps.nbu.files",
            "GoogleCalendar" to "com.google.android.calendar",
            "Google-Calendar" to "com.google.android.calendar",
            "Google Calendar" to "com.google.android.calendar",
            "google calendar" to "com.google.android.calendar",
            "GoogleChat" to "com.google.android.apps.dynamite",
            "Google Chat" to "com.google.android.apps.dynamite",
            "Google-Chat" to "com.google.android.apps.dynamite",
            "GoogleClock" to "com.google.android.deskclock",
            "Google Clock" to "com.google.android.deskclock",
            "Google-Clock" to "com.google.android.deskclock",
            "GoogleContacts" to "com.google.android.contacts",
            "Google Contacts" to "com.google.android.contacts",
            "google contacts" to "com.google.android.contacts",
            "Google Drive" to "com.google.android.apps.docs",
            "Google-Drive" to "com.google.android.apps.docs",
            "google drive" to "com.google.android.apps.docs",
            "GoogleDrive" to "com.google.android.apps.docs",
            "Googledrive" to "com.google.android.apps.docs",
            "googledrive" to "com.google.android.apps.docs",
            "GoogleFit" to "com.google.android.apps.fitness",
            "googlefit" to "com.google.android.apps.fitness",
            "GoogleKeep" to "com.google.android.keep",
            "googlekeep" to "com.google.android.keep",
            "GoogleMaps" to "com.google.android.apps.maps",
            "Google Maps" to "com.google.android.apps.maps",
            "googlemaps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "Google Play Books" to "com.google.android.apps.books",
            "Google-Play-Books" to "com.google.android.apps.books",
            "google play books" to "com.google.android.apps.books",
            "google-play-books" to "com.google.android.apps.books",
            "GooglePlayBooks" to "com.google.android.apps.books",
            "googleplaybooks" to "com.google.android.apps.books",
            "GooglePlayStore" to "com.android.vending",
            "Google Play Store" to "com.android.vending",
            "Google-Play-Store" to "com.android.vending",
            "GoogleSlides" to "com.google.android.apps.docs.editors.slides",
            "Google Slides" to "com.google.android.apps.docs.editors.slides",
            "Google-Slides" to "com.google.android.apps.docs.editors.slides",
            "GoogleTasks" to "com.google.android.apps.tasks",
            "Google Tasks" to "com.google.android.apps.tasks",
            "McDonald" to "com.mcdonalds.app",
            "mcdonald" to "com.mcdonalds.app",
            "Osmand" to "net.osmand",
            "osmand" to "net.osmand",
            "Quora" to "com.quora.android",
            "quora" to "com.quora.android",
            "Reddit" to "com.reddit.frontpage",
            "reddit" to "com.reddit.frontpage",
            "SimpleCalendarPro" to "com.scientificcalculatorplus.simplecalculator.basiccalculator.mathcalc",
            "SimpleSMSMessenger" to "com.simplemobiletools.smsmessenger",
            "Telegram" to "org.telegram.messenger",
            "Tiktok" to "com.zhiliaoapp.musically",
            "tiktok" to "com.zhiliaoapp.musically",
            "Twitter" to "com.twitter.android",
            "twitter" to "com.twitter.android",
            "X" to "com.twitter.android",
            "Whatsapp" to "com.whatsapp",
            "WhatsApp" to "com.whatsapp"
        )
        return appPackageMap[appName] ?: appName
    }
    
    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (root.isEditable) {
                return root
            }
        }
        
        val childCount = root.childCount
        if (childCount > 0) {
            // 先获取所有子节点的引用，避免在遍历时访问父节点导致警告
            val children = mutableListOf<AccessibilityNodeInfo>()
            for (i in 0 until childCount) {
                val child = root.getChild(i)
                if (child != null) {
                    children.add(child)
                }
            }
            
            // 然后递归查找
            for (child in children) {
                val editable = findEditableNode(child)
                if (editable != null) {
                    // 清理其他未使用的子节点
                    for (otherChild in children) {
                        if (otherChild != child) {
                            otherChild.recycle()
                        }
                    }
                    child.recycle()
                    return editable
                }
                child.recycle()
            }
        }
        
        return null
    }
}

