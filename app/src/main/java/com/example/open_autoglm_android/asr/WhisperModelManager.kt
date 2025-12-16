package com.example.open_autoglm_android.asr

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object WhisperModelManager {

    private const val TAG = "WhisperModelManager"
    private const val ASSET_MODEL_PATH = "whisper/ggml-tiny-q5_1.bin"
    /**
     * 确保模型文件从 assets 解压到本地存储，并返回本地路径。
     * 如果失败，返回 null。
     */
    fun ensureLocalModel(context: Context): String? {
        val destDir = File(context.filesDir, "whisper")
        if (!destDir.exists() && !destDir.mkdirs()) {
            Log.e(TAG, "无法创建模型目录: ${destDir.absolutePath}")
            return null
        }

        val destFile = File(destDir, "model.gguf")
        if (destFile.exists() && destFile.length() > 0L) {
            return destFile.absolutePath
        }

        return try {
            context.assets.open(ASSET_MODEL_PATH).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            Log.i(TAG, "模型已从 assets 解压到: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "解压模型失败: ${e.message}", e)
            null
        }
    }
}


