package com.example.open_autoglm_android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.open_autoglm_android.data.InputMode
import com.example.open_autoglm_android.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AutoGLMAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: AutoGLMAccessibilityService? = null
        
        fun getInstance(): AutoGLMAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var preferencesRepository: PreferencesRepository
    var currentInputMode = InputMode.SET_TEXT
        private set
        
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentApp = MutableStateFlow<String?>(null)
    val currentApp: StateFlow<String?> = _currentApp.asStateFlow()
    
    val safeCurrentApp: String?
        get() {
            val fromEvent = _currentApp.value
            if (!fromEvent.isNullOrBlank()) return fromEvent
            val fromRoot = rootInActiveWindow?.packageName?.toString()
            return fromRoot
        }
    
    private val _latestScreenshot = MutableStateFlow<Bitmap?>(null)
    val latestScreenshot: StateFlow<Bitmap?> = _latestScreenshot.asStateFlow()
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        preferencesRepository = PreferencesRepository(this)
        
        serviceScope.launch {
            preferencesRepository.inputMode.collect { mode ->
                currentInputMode = mode
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        mainHandler.removeCallbacksAndMessages(null)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                _currentApp.value = it.packageName?.toString()
            }
        }
    }
    
    override fun onInterrupt() {}
    
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            FloatingWindowService.getInstance()?.setVisibility(false)
            mainExecutor.execute {
                takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        FloatingWindowService.getInstance()?.setVisibility(true)
                        val hardwareBuffer = result.hardwareBuffer
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                        hardwareBuffer?.close()
                        val bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        _latestScreenshot.value = bitmap
                        callback(bitmap)
                    }
                    override fun onFailure(errorCode: Int) {
                        FloatingWindowService.getInstance()?.setVisibility(true)
                        callback(null)
                    }
                })
            }
        } else callback(null)
    }
    
    suspend fun takeScreenshotSuspend(): Bitmap? = suspendCancellableCoroutine { continuation ->
        takeScreenshot { continuation.resume(it) }
    }
    
    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow
    
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }
    
    fun tap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    fun longPress(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 800))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }
    
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }
    
    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    
    fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.parent?.let { return performClick(it) }
        return false
    }
    
    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        Log.d("AutoGLMService", "准备输入文本, 模式: $currentInputMode")
        
        if (currentInputMode != InputMode.SET_TEXT) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val centerX = rect.centerX().toFloat()
            val centerY = rect.centerY().toFloat()
            Log.d("AutoGLMService", "执行物理点击激活: ($centerX, $centerY)")
            tap(centerX, centerY)
        }

        return when (currentInputMode) {
            InputMode.SET_TEXT -> {
                val arguments = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            InputMode.PASTE -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("auto_glm", text))
                mainHandler.postDelayed({
                    val success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    Log.d("AutoGLMService", "粘贴结果: $success")
                }, 400)
                true
            }
            InputMode.IME -> {
                if (MyInputMethodService.isEnabled()) {
                    var attempts = 0
                    val maxAttempts = 5
                    
                    fun tryTypeText() {
                        if (attempts >= maxAttempts) {
                            Log.e("AutoGLMService", "IME输入重试多次后依然失败")
                            return
                        }
                        
                        val success = MyInputMethodService.typeText(text)
                        Log.d("AutoGLMService", "IME输入尝试 ${attempts + 1}, 结果: $success")
                        
                        if (!success) {
                            attempts++
                            // 如果失败，可能是键盘还没准备好，500ms后重试
                            mainHandler.postDelayed({ tryTypeText() }, 500)
                        }
                    }
                    
                    // 第一次尝试放在 800ms 后，确保键盘动画基本完成
                    mainHandler.postDelayed({ tryTypeText() }, 800)
                    true
                } else {
                    val arguments = android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }
        }
    }
}
