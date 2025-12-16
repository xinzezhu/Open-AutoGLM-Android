cmake_minimum_required(VERSION 3.22.1)

project(whisper_jni)

# 设置一个简单的版本号，供 whisper 源码的 CMake 使用
set(PROJECT_VERSION 1.0.0)
set(SOVERSION 1)

# 引入 ggml 和 whisper 源码（相对路径基于 app/src/main/cpp/）
# 当前路径是：D:/project/Open-AutoGLM-Android/app/src/main/cpp
# 目标路径是：D:/project/whisper.cpp
# 因此需要先回到 D:/project，然后进入 whisper.cpp：../../../../../whisper.cpp
add_subdirectory("../../../../../whisper.cpp/ggml" "ggml_build")
add_subdirectory("../../../../../whisper.cpp/src" "whisper_src")

# 设置 WHISPER_VERSION 宏，避免上游 CMake 中的版本探测逻辑缺失导致编译错误
target_compile_definitions(
    whisper
    PRIVATE
    WHISPER_VERSION="local-android"
)

add_library(
    whisper_jni
    SHARED
    WhisperJni.cpp
)

target_include_directories(
    whisper_jni
    PRIVATE
    "../../../../whisper.cpp/include"
    "../../../../whisper.cpp/ggml/include"
)

find_library(
    log-lib
    log
)

target_link_libraries(
    whisper_jni
    whisper
    ${log-lib}
)

