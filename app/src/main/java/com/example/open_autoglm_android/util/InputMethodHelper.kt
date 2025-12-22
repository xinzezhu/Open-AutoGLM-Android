package com.example.open_autoglm_android.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import com.example.open_autoglm_android.service.MyInputMethodService

object InputMethodHelper {
    private const val TAG = "InputMethodHelper"

    /**
     * 获取本应用输入法在系统中的正式 ID
     * 优先从系统安装列表中获取，确保 ID 格式与系统一致（解决全路径/缩写路径不匹配问题）
     */
    fun getInputMethodId(context: Context): String {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val myComponentName = ComponentName(context, MyInputMethodService::class.java)
        
        // 遍历所有已安装的输入法
        val imis = imm.inputMethodList
        val myImi = imis.find { it.component == myComponentName }
        
        val id = myImi?.id ?: myComponentName.flattenToString()
        Log.v(TAG, "获取输入法ID: $id")
        return id
    }

    /**
     * 检查本应用输入法是否已在系统中启用
     */
    fun isInputMethodEnabled(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        val myId = getInputMethodId(context)
        
        // 使用 id 匹配，或者更保险地使用 component 匹配
        val isEnabled = enabledMethods.any { it.id == myId }
        
        Log.d(TAG, "检查输入法是否启用: $myId -> $isEnabled")
        return isEnabled
    }

    /**
     * 检查当前是否正在使用本应用输入法
     */
    fun isInputMethodSelected(context: Context): Boolean {
        val currentMethodId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        val myId = getInputMethodId(context)
        
        // 为了防止系统返回缩写而 myId 是全写，或者反过来，我们先统一转为 ComponentName 再比较
        val currentComponent = ComponentName.unflattenFromString(currentMethodId ?: "")
        val myComponent = ComponentName(context, MyInputMethodService::class.java)
        
        val isSelected = currentComponent == myComponent
        Log.d(TAG, "检查当前输入法: 当前=$currentMethodId, 目标=$myId -> $isSelected")
        return isSelected
    }

    /**
     * 尝试通过 WRITE_SECURE_SETTINGS 自动切换到本应用输入法
     */
    fun switchToMyInputMethod(context: Context): Boolean {
        Log.d(TAG, "开始尝试切换到本应用输入法...")
        
        if (!AuthHelper.hasWriteSecureSettingsPermission(context)) {
            Log.e(TAG, "切换失败: 缺少 WRITE_SECURE_SETTINGS 权限")
            return false
        }
        
        if (!isInputMethodEnabled(context)) {
            Log.e(TAG, "切换失败: 输入法未启用，请先在系统设置中开启")
            return false
        }

        return try {
            val myId = getInputMethodId(context)
            val success = Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                myId
            )
            Log.i(TAG, "切换指令执行结果: $success, ID: $myId")
            
            // 某些设备可能还需要设置这个值才生效
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
                "-1" // -1 表示默认子类型
            )
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "切换异常", e)
            false
        }
    }
}
