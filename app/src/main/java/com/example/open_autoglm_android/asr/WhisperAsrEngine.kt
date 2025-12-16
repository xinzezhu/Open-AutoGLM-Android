package com.example.open_autoglm_android.asr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WhisperAsrEngine {

    private const val TAG = "WhisperAsrEngine"

    @Volatile
    private var initialized = false

    /**
     * 初始化引擎：确保模型在本地并调用 native init。
     * 返回是否初始化成功。
     */
    suspend fun initIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (initialized) return@withContext true

        val modelPath = WhisperModelManager.ensureLocalModel(context)
        if (modelPath.isNullOrBlank()) {
            Log.e(TAG, "模型路径为空，无法初始化")
            return@withContext false
        }

        return@withContext try {
            val ok = WhisperAsrNative.init(modelPath)
            initialized = ok
            Log.i(TAG, "Whisper native init result: $ok, path=$modelPath")
            ok
        } catch (e: Throwable) {
            Log.e(TAG, "初始化 native 失败: ${e.message}", e)
            false
        }
    }

    /**
     * 使用给定 PCM 数据进行识别。
     * 目前 native 仍是占位实现，后续可直接替换为真实 whisper 推理。
     */
    suspend fun transcribe(
        context: Context,
        pcm: ShortArray,
        sampleRate: Int,
        language: String?
    ): String = withContext(Dispatchers.Default) {
        if (!initIfNeeded(context)) {
            return@withContext "本地语音模型未就绪"
        }
        return@withContext try {
            WhisperAsrNative.transcribe(pcm, sampleRate, language)
        } catch (e: Throwable) {
            Log.e(TAG, "调用 native 识别失败: ${e.message}", e)
            "本地语音识别出错: ${e.message}"
        }
    }
}


