#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include "whisper.h"

#define LOG_TAG "WhisperJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static whisper_context *g_ctx = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_open_1autoglm_1android_asr_WhisperAsrNative_init(
        JNIEnv *env,
        jobject /* thiz */,
        jstring modelPath_) {
    const char *modelPath = env->GetStringUTFChars(modelPath_, nullptr);
    LOGI("Init whisper with model path: %s", modelPath);

    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // 默认关闭 GPU，加速相关可后续再调

    g_ctx = whisper_init_from_file_with_params(modelPath, cparams);
    env->ReleaseStringUTFChars(modelPath_, modelPath);

    if (!g_ctx) {
        LOGE("Failed to init whisper context");
        return JNI_FALSE;
    }

    LOGI("Whisper context initialized");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_open_1autoglm_1android_asr_WhisperAsrNative_transcribe(
        JNIEnv *env,
        jobject /* thiz */,
        jshortArray pcm_,
        jint sampleRate,
        jstring language_) {
    if (!g_ctx) {
        LOGE("Whisper context not initialized");
        std::string err = u8"本地语音模型未初始化";
        return env->NewStringUTF(err.c_str());
    }

    if (sampleRate != WHISPER_SAMPLE_RATE) {
        LOGE("Unexpected sample rate: %d, expected %d", sampleRate, WHISPER_SAMPLE_RATE);
    }

    const jsize nSamples = env->GetArrayLength(pcm_);
    std::vector<float> pcmf32;
    pcmf32.resize(nSamples);

    jshort *pcmData = env->GetShortArrayElements(pcm_, nullptr);
    for (jsize i = 0; i < nSamples; ++i) {
        pcmf32[i] = static_cast<float>(pcmData[i]) / 32768.0f;
    }
    env->ReleaseShortArrayElements(pcm_, pcmData, JNI_ABORT);

    const char *lang_cstr = nullptr;
    if (language_) {
        lang_cstr = env->GetStringUTFChars(language_, nullptr);
    }

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime   = false;
    wparams.print_progress   = false;
    wparams.print_timestamps = false;
    wparams.print_special    = false;
    wparams.translate        = false;
    wparams.no_context       = true;
    wparams.single_segment   = false;
    wparams.temperature      = 0.0f;
    wparams.n_threads        = 4;  // 简单写死，后面可以从 Java 传入或根据 CPU 核数调整

    if (lang_cstr && lang_cstr[0] != '\0') {
        wparams.language = lang_cstr;
    } else {
        wparams.language = nullptr; // 让 whisper 自动检测语言
    }

    LOGI("Running whisper_full on %d samples", nSamples);

    int ret = whisper_full(g_ctx, wparams, pcmf32.data(), pcmf32.size());

    if (lang_cstr) {
        env->ReleaseStringUTFChars(language_, lang_cstr);
    }

    if (ret != 0) {
        LOGE("whisper_full failed, code=%d", ret);
        std::string err = u8"本地语音识别失败";
        return env->NewStringUTF(err.c_str());
    }

    const int nSegments = whisper_full_n_segments(g_ctx);
    std::string result;
    result.reserve(128);

    for (int i = 0; i < nSegments; ++i) {
        const char *text = whisper_full_get_segment_text(g_ctx, i);
        if (text) {
            result += text;
        }
    }

    LOGI("Transcription done, segments=%d", nSegments);

    if (result.empty()) {
        result = u8"";
    }

    return env->NewStringUTF(result.c_str());
}

