package com.example.open_autoglm_android.service

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MyInputMethodService : InputMethodService() {

    private var statusTextView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val clearStatusRunnable = Runnable {
        statusTextView?.text = ""
    }

    companion object {
        private const val TAG = "AutoGLM-IME"
        private var instance: MyInputMethodService? = null

        fun typeText(text: String): Boolean {
            val service = instance
            if (service == null) {
                Log.e(TAG, "typeText 失败: MyInputMethodService 实例为空")
                return false
            }
            
            val ic = service.currentInputConnection
            if (ic == null) {
                Log.e(TAG, "typeText 失败: currentInputConnection 为空 (输入法未真正连接到输入框)")
                service.updateStatus("错误: 未建立输入连接", Color.RED)
                return false
            }
            
            Log.d(TAG, "正在通过 IME 输入文本: $text")
            service.updateStatus("正在输入: ${if (text.length > 10) text.take(10) + "..." else text}", Color.parseColor("#4CAF50"))
            
            return ic.commitText(text, 1)
        }
        
        fun isEnabled(): Boolean = instance != null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MyInputMethodService 已创建")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        handler.removeCallbacksAndMessages(null)
    }

    fun updateStatus(status: String, color: Int = Color.parseColor("#4CAF50")) {
        handler.post {
            statusTextView?.setTextColor(color)
            statusTextView?.text = status
            handler.removeCallbacks(clearStatusRunnable)
            handler.postDelayed(clearStatusRunnable, 3000)
        }
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 左侧状态显示
        statusTextView = TextView(this).apply {
            textSize = 10f
            setPadding(20, 10, 20, 10)
        }

        val statusParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setMargins(20, 0, 0, 0)
        }
        root.addView(statusTextView, statusParams)

        // 右侧按钮容器
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // 测试输入按钮
        val testButton = TextView(this).apply {
            text = "测试输入"
            setTextColor(Color.parseColor("#2196F3")) // 蓝色
            textSize = 12f
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.parseColor("#10000000"))
            
            setOnClickListener {
                typeText("键盘输入测试")
            }
        }
        buttonContainer.addView(testButton)

        // 切换输入法按钮
        val switchButton = TextView(this).apply {
            text = "切换输入法"
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.parseColor("#10000000"))
            
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = 10
        }
        buttonContainer.addView(switchButton, layoutParams)

        val containerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            setMargins(0, 0, 10, 10)
        }

        root.addView(buttonContainer, containerParams)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView: 连接已建立")
        updateStatus("输入法已就绪")
    }
    
    override fun onEvaluateFullscreenMode(): Boolean = false
    override fun onEvaluateInputViewShown(): Boolean = true
}
