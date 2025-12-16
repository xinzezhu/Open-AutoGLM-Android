package com.example.open_autoglm_android.asr

object WhisperAsrNative {

    init {
        try {
            System.loadLibrary("whisper_jni")
        } catch (e: Throwable) {
            // 忽略加载失败，在上层做降级处理
        }
    }

    external fun init(modelPath: String): Boolean

    external fun transcribe(
        pcm: ShortArray,
        sampleRate: Int,
        language: String?
    ): String
}


