package com.example.open_autoglm_android.domain

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.open_autoglm_android.data.InputMode
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
import com.example.open_autoglm_android.service.FloatingWindowService
import com.example.open_autoglm_android.service.MyInputMethodService
import com.google.gson.JsonElement
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
            
            val jsonString = extractJsonFromText(actionJson)
            Log.d("ActionExecutor", "提取的 JSON: ${jsonString.take(200)}")
            
            if (jsonString.isEmpty() || jsonString == actionJson.trim()) {
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
                
                return ExecuteResult(
                    success = false,
                    message = "无法从响应中提取有效的 JSON 动作。响应内容: ${actionJson.take(200)}"
                )
            }
            
            val jsonElement = try {
                JsonParser.parseString(jsonString)
            } catch (e: Exception) {
                Log.w("ActionExecutor", "标准解析失败，尝试 lenient 模式", e)
                try {
                    val reader = JsonReader(StringReader(jsonString))
                    reader.isLenient = true
                    JsonParser.parseReader(reader)
                } catch (e2: Exception) {
                    Log.e("ActionExecutor", "Lenient 模式也失败", e2)
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
                bringAppToForeground()
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
    
    private fun extractJsonFromText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JsonParser.parseString(trimmed)
                return trimmed
            } catch (e: Exception) { }
        }
        
        val jsonCandidates = mutableListOf<String>()
        var startIndex = -1
        var braceCount = 0
        
        for (i in trimmed.indices) {
            when (trimmed[i]) {
                '{' -> {
                    if (startIndex == -1) startIndex = i
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && startIndex != -1) {
                        val candidate = trimmed.substring(startIndex, i + 1)
                        try {
                            JsonParser.parseString(candidate)
                            jsonCandidates.add(candidate)
                        } catch (e: Exception) { }
                        startIndex = -1
                    }
                }
            }
        }
        
        if (jsonCandidates.isNotEmpty()) return jsonCandidates.first()
        
        val fixedJson = tryFixMalformedJson(trimmed)
        if (fixedJson.isNotEmpty()) {
            try {
                JsonParser.parseString(fixedJson)
                return fixedJson
            } catch (e: Exception) { }
        }
        
        return trimmed
    }
    
    private fun tryFixMalformedJson(text: String): String {
        val functionCallPattern = Regex("""(do|finish)\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
        val functionMatch = functionCallPattern.find(text)
        
        if (functionMatch != null) {
            val functionName = functionMatch.groupValues[1].lowercase()
            val paramsStr = functionMatch.groupValues[2]
            
            if (functionName == "finish") {
                val messagePattern = Regex("""message\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val messageMatch = messagePattern.find(paramsStr)
                val message = messageMatch?.groupValues?.get(1) ?: paramsStr.trim().trim('"', '\'')
                return """{"_metadata": "finish", "message": "$message"}"""
            } else if (functionName == "do") {
                val action = mutableMapOf<String, Any>("_metadata" to "do")
                val paramPattern = Regex("""(\w+)\s*=\s*("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\[[^\]]+\]|\d+\.?\d*|true|false)""", RegexOption.IGNORE_CASE)
                val paramMatches = paramPattern.findAll(paramsStr)
                
                for (match in paramMatches) {
                    val key = match.groupValues[1]
                    val valueStr = match.groupValues[2].trim()
                    val value: Any = when {
                        valueStr.startsWith("[") -> {
                            val arrayValues = valueStr.substring(1, valueStr.length - 1).split(",").map { it.trim() }
                            "[" + arrayValues.joinToString(",") + "]"
                        }
                        valueStr.startsWith("\"") || valueStr.startsWith("'") -> {
                            valueStr.trim('"', '\'').replace("\\\"", "\"").replace("\\'", "'")
                        }
                        valueStr == "true" -> true
                        valueStr == "false" -> false
                        valueStr.contains(".") -> valueStr.toDoubleOrNull() ?: valueStr
                        else -> valueStr.toIntOrNull() ?: valueStr
                    }
                    action[key] = value
                }
                
                val jsonBuilder = StringBuilder("{")
                jsonBuilder.append("\"_metadata\": \"do\"")
                for ((key, value) in action) {
                    if (key == "_metadata") continue
                    jsonBuilder.append(", \"$key\": ")
                    when (value) {
                        is String -> {
                            if (value.startsWith("[")) jsonBuilder.append(value)
                            else jsonBuilder.append("\"${value.replace("\"", "\\\"")}\"")
                        }
                        is Number, is Boolean -> jsonBuilder.append(value)
                        else -> {
                            val vStr = value.toString()
                            if (vStr.startsWith("[")) jsonBuilder.append(vStr)
                            else jsonBuilder.append("\"${vStr.replace("\"", "\\\"")}\"")
                        }
                    }
                }
                jsonBuilder.append("}")
                return jsonBuilder.toString()
            }
        }
        
        val pattern1 = Regex("""do\s*\(\s*action\s*=\s*["']([^"']+)["']\s*,\s*app\s*=\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(text)
        if (match1 != null) {
            return """{"_metadata": "do", "action": "${match1.groupValues[1]}", "app": "${match1.groupValues[2]}"}"""
        }
        
        val launchPattern = Regex("""(?:打开|启动|运行|launch)\s*([^\s，,。.]+)""", RegexOption.IGNORE_CASE)
        val launchMatch = launchPattern.find(text)
        if (launchMatch != null) {
            return """{"_metadata": "do", "action": "Launch", "app": "${launchMatch.groupValues[1].trim()}"}"""
        }
        
        return ""
    }
    
    private fun convertRelativeToAbsolute(element: List<Float>, screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        val x = (element[0] / 1000f) * screenWidth
        val y = (element[1] / 1000f) * screenHeight
        return Pair(x, y)
    }
    
    private suspend fun executeAction(action: String, actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        FloatingWindowService.getInstance()?.setVisibility(false)
        delay(100)
        val result = try {
            when (action.lowercase()) {
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
        } finally {
            FloatingWindowService.getInstance()?.setVisibility(true)
        }
        return result
    }
    
    private suspend fun launchApp(actionObj: JsonObject): ExecuteResult {
        val appName = actionObj.get("app")?.asString ?: return ExecuteResult(success = false, message = "Launch 操作缺少 app 参数")
        val packageName = getPackageName(appName)
        if (packageName == appName && !isPackageInstalled(packageName)) {
            return ExecuteResult(success = false, message = "找不到应用: $appName，且未安装此包名")
        }
        return try {
            val pm = service.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
                ?: return ExecuteResult(success = false, message = "找不到应用: $appName (包名: $packageName)")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            delay(2000)
            ExecuteResult(success = true)
        } catch (e: Exception) {
            ExecuteResult(success = false, message = "启动应用失败: ${e.message}")
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            service.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private suspend fun tap(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val element = actionObj.get("element")
        if (element?.isJsonArray == true) {
            val array = element.asJsonArray
            if (array.size() >= 2) {
                val (absoluteX, absoluteY) = convertRelativeToAbsolute(listOf(array[0].asFloat, array[1].asFloat), screenWidth, screenHeight)
                service.tap(absoluteX, absoluteY)
                delay(500)
                return ExecuteResult(success = true, message = "已点击坐标: ($absoluteX, $absoluteY)")
            }
            return ExecuteResult(success = false, message = "坐标格式错误")
        } else {
            val text = actionObj.get("text")?.asString
            if (text != null) {
                val node = service.findNodeByText(text)
                if (node != null) {
                    val success = service.performClick(node)
                    node.recycle()
                    delay(500)
                    return ExecuteResult(success = success)
                }
                return ExecuteResult(success = false, message = "找不到元素: $text")
            }
            return ExecuteResult(success = false, message = "Tap 操作缺少 element 或 text 参数")
        }
    }
    
    private suspend fun type(actionObj: JsonObject): ExecuteResult {
        val text = actionObj.get("text")?.asString ?: return ExecuteResult(success = false, message = "Type 操作缺少 text 参数")
        
        // 如果是 IME 模式且已启用，直接尝试输入，不需要找输入框
        if (service.currentInputMode == InputMode.IME && MyInputMethodService.isEnabled()) {
            val success = MyInputMethodService.typeText(text)
            if (success) {
                delay(500)
                return ExecuteResult(success = true)
            }
            Log.w("ActionExecutor", "IME 直接输入失败，尝试寻找输入框")
        }

        val root = service.getRootNode() ?: return ExecuteResult(success = false, message = "无法获取根节点")
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
        if (start == null || end == null || start.size() < 2 || end.size() < 2) return ExecuteResult(success = false, message = "Swipe 操作缺少 start 或 end 参数")
        val (startX, startY) = convertRelativeToAbsolute(listOf(start[0].asFloat, start[1].asFloat), screenWidth, screenHeight)
        val (endX, endY) = convertRelativeToAbsolute(listOf(end[0].asFloat, end[1].asFloat), screenWidth, screenHeight)
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
        if (element == null || element.size() < 2) return ExecuteResult(success = false, message = "LongPress 操作缺少 element 参数")
        val (x, y) = convertRelativeToAbsolute(listOf(element[0].asFloat, element[1].asFloat), screenWidth, screenHeight)
        service.longPress(x, y)
        delay(800)
        return ExecuteResult(success = true, message = "已长按坐标: ($x, $y)")
    }
    
    private suspend fun doubleTap(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val element = actionObj.get("element")?.asJsonArray
        if (element == null || element.size() < 2) return ExecuteResult(success = false, message = "DoubleTap 操作缺少 element 参数")
        val (x, y) = convertRelativeToAbsolute(listOf(element[0].asFloat, element[1].asFloat), screenWidth, screenHeight)
        service.tap(x, y)
        delay(100)
        service.tap(x, y)
        delay(500)
        return ExecuteResult(success = true, message = "已双击坐标: ($x, $y)")
    }
    
    private suspend fun wait(actionObj: JsonObject): ExecuteResult {
        val durationMs = parseDurationMillis(actionObj.get("duration"))
        delay(durationMs)
        return ExecuteResult(success = true, message = "已等待 ${durationMs}ms")
    }

    private fun parseDurationMillis(durationElement: JsonElement?): Long {
        if (durationElement == null) return 1000L
        if (durationElement.isJsonPrimitive) {
            val prim = durationElement.asJsonPrimitive
            if (prim.isNumber) return prim.asLong.coerceAtLeast(0L)
            if (prim.isString) {
                val raw = prim.asString.trim()
                val regex = Regex("""(?i)(\d+(?:\.\d+)?)\s*(ms|millisecond|milliseconds|s|sec|secs|second|seconds)?""")
                val match = regex.find(raw)
                if (match != null) {
                    val value = match.groupValues[1].toDoubleOrNull() ?: 1.0
                    val unit = match.groupValues.getOrNull(2)?.lowercase()
                    val millis = when (unit) {
                        "ms", "millisecond", "milliseconds" -> value
                        "s", "sec", "secs", "second", "seconds" -> value * 1000
                        else -> value * 1000
                    }
                    return millis.toLong().coerceAtLeast(0L)
                }
            }
        }
        return 1000L
    }
    
    fun bringAppToForeground() {
        try {
            val packageName = service.packageName
            val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                service.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("ActionExecutor", "返回应用失败", e)
        }
    }

    private fun getPackageName(appName: String): String {
        val appPackageMap = mapOf(
            "支付宝" to "com.eg.android.AlipayGphone",
            "微信" to "com.tencent.mm",
            "WeChat" to "com.tencent.mm",
            "wechat" to "com.tencent.mm",
            "QQ" to "com.tencent.mobileqq",
            "qq" to "com.tencent.mobileqq",
            "微博" to "com.sina.weibo",
            "淘宝" to "com.taobao.taobao",
            "京东" to "com.jingdong.app.mall",
            "拼多多" to "com.xunmeng.pinduoduo",
            "小红书" to "com.xingin.xhs",
            "知乎" to "com.zhihu.android",
            "高德地图" to "com.autonavi.minimap",
            "百度地图" to "com.baidu.BaiduMap",
            "美团" to "com.sankuai.meituan",
            "bilibili" to "tv.danmaku.bili",
            "抖音" to "com.ss.android.ugc.aweme",
            "网易云音乐" to "com.netease.cloudmusic",
            "Settings" to "com.android.settings",
            "Chrome" to "com.android.chrome",
            "YouTube" to "com.google.android.youtube"
        )
        val mappedPackage = appPackageMap[appName]
        if (mappedPackage != null) return mappedPackage
        try {
            val pm = service.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString()
                if (label.equals(appName, ignoreCase = true) || label.contains(appName, ignoreCase = true)) {
                    return info.activityInfo.packageName
                }
            }
        } catch (e: Exception) { }
        return appName
    }
    
    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && root.isEditable) return root
        val childCount = root.childCount
        if (childCount > 0) {
            val children = mutableListOf<AccessibilityNodeInfo>()
            for (i in 0 until childCount) {
                root.getChild(i)?.let { children.add(it) }
            }
            for (child in children) {
                val editable = findEditableNode(child)
                if (editable != null) {
                    for (otherChild in children) if (otherChild != child) otherChild.recycle()
                    child.recycle()
                    return editable
                }
                child.recycle()
            }
        }
        return null
    }
}
