package com.example.open_autoglm_android.domain

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

/**
 * 负责应用名称到包名的映射管理
 */
object AppRegistry {
    private const val TAG = "AppRegistry"
    private const val CONFIG_FILE_NAME = "app_mapping.json"
    private const val CONFIG_DIR_NAME = "OpenAutoGLM"
    
    // 应用名称 -> 包名 的映射
    @Volatile
    private var appPackageMap: Map<String, String> = emptyMap()

    /**
     * 初始化 AppRegistry
     * 策略：
     * 1. 优先尝试公共 Documents 目录 (无需特殊权限即可访问应用自己创建的文件，且便于用户查找)
     * 2. 如果失败，回退到使用 Assets
     */
    fun initialize(context: Context) {
        if (loadFromPublicDir(context, Environment.DIRECTORY_DOCUMENTS)) {
            return
        }
        
        loadFromAssets(context)
    }

    /**
     * 尝试从公共目录加载 (如 Documents/OpenAutoGLM/)
     */
    private fun loadFromPublicDir(context: Context, directoryType: String): Boolean {
        return try {
            val publicDir = Environment.getExternalStoragePublicDirectory(directoryType)
            val configDir = File(publicDir, CONFIG_DIR_NAME)
            
            loadConfigFile(context, configDir, "Public Storage ($directoryType)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load from public directory: $directoryType", e)
            false
        }
    }

    /**
     * 通用的配置文件加载逻辑
     */
    private fun loadConfigFile(context: Context, dir: File, sourceName: String): Boolean {
        try {
            // 尝试创建目录
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.w(TAG, "[$sourceName] Failed to create directory: ${dir.absolutePath}")
                    // 如果目录创建失败（可能是权限问题），直接返回 false
                    return false
                }
            }

            val configFile = File(dir, CONFIG_FILE_NAME)
            
            // 如果文件不存在，尝试从 assets 复制
            if (!configFile.exists()) {
                Log.i(TAG, "[$sourceName] Config file not found, copying from assets...")
                if (!copyAssetsToLocalStorage(context, CONFIG_FILE_NAME, configFile)) {
                    Log.w(TAG, "[$sourceName] Failed to copy default config")
                    return false
                }
            }
            
            // 尝试读取并解析
            if (configFile.exists() && configFile.canRead()) {
                val jsonString = configFile.readText()
                val type = object : TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String>? = Gson().fromJson(jsonString, type)
                
                if (!map.isNullOrEmpty()) {
                    appPackageMap = map
                    Log.i(TAG, "Loaded ${appPackageMap.size} app mappings from $sourceName: ${configFile.absolutePath}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$sourceName] Error handling config file", e)
        }
        return false
    }

    private fun loadFromAssets(context: Context) {
        try {
            Log.i(TAG, "Loading config from assets (Fallback)...")
            context.assets.open(CONFIG_FILE_NAME).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val map: Map<String, String>? = Gson().fromJson(reader, type)
                    appPackageMap = map ?: emptyMap()
                    Log.i(TAG, "Loaded ${appPackageMap.size} app mappings from assets")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config from assets", e)
            if (appPackageMap == null) {
                appPackageMap = emptyMap()
            }
        }
    }

    private fun copyAssetsToLocalStorage(context: Context, assetName: String, destinationFile: File): Boolean {
        return try {
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset: $assetName", e)
            false
        }
    }

    fun getPackageName(appName: String): String {
        return appPackageMap[appName.trim()] ?: appName
    }
}
