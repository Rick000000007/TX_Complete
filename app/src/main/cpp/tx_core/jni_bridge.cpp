#include <jni.h>
#include <android/log.h>
#include <memory>
#include <unordered_map>
#include <mutex>
#include "engine.h"

#define LOG_TAG "TX_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

static std::unordered_map<jlong, std::shared_ptr<tx::Engine>> engines;
static std::mutex engines_mutex;
static jlong next_id = 1;

JNIEXPORT jlong JNICALL
Java_com_tx_terminal_bridge_JNIInterface_nativeCreateEngine(
    JNIEnv* env, jobject thiz, jint rows, jint cols, jint scrollback) {
    
    tx::Engine::Config config;
    config.rows = rows;
    config.cols = cols;
    config.scrollbackLines = scrollback;
    
    auto engine = std::make_shared<tx::Engine>(config);
    
    std::lock_guard<std::mutex> lock(engines_mutex);
    jlong id = next_id++;
    engines[id] = engine;
    return id;
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_bridge_JNIInterface_nativeStartShell(
    JNIEnv* env, jobject thiz, jlong handle, jstring shellPath) {
    
    auto engine = [&]() -> std::shared_ptr<tx::Engine> {
        std::lock_guard<std::mutex> lock(engines_mutex);
        auto it = engines.find(handle);
        return (it != engines.end()) ? it->second : nullptr;
    }();
    
    if (!engine) return false;
    
    const char* shell = env->GetStringUTFChars(shellPath, nullptr);
    bool result = engine->Start(shell);
    env->ReleaseStringUTFChars(shellPath, shell);
    
    return result;
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_bridge_JNIInterface_nativeDestroyEngine(
    JNIEnv* env, jobject thiz, jlong handle) {
    
    std::lock_guard<std::mutex> lock(engines_mutex);
    engines.erase(handle);
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_bridge_JNIInterface_nativeResize(
    JNIEnv* env, jobject thiz, jlong handle, jint rows, jint cols) {
    
    auto engine = [&]() -> std::shared_ptr<tx::Engine> {
        std::lock_guard<std::mutex> lock(engines_mutex);
        auto it = engines.find(handle);
        return (it != engines.end()) ? it->second : nullptr;
    }();
    
    if (engine) engine->Resize(rows, cols);
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_bridge_JNIInterface_nativeWriteInput(
    JNIEnv* env, jobject thiz, jlong handle, jbyteArray data, jint offset, jint len) {
    
    auto engine = [&]() -> std::shared_ptr<tx::Engine> {
        std::lock_guard<std::mutex> lock(engines_mutex);
        auto it = engines.find(handle);
        return (it != engines.end()) ? it->second : nullptr;
    }();
    
    if (!engine) return;
    
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    engine->WriteInput(reinterpret_cast<const char*>(bytes) + offset, len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
}

// Optimized buffer access using DirectByteBuffer
JNIEXPORT jobject JNICALL
Java_com_tx_terminal_bridge_JNIInterface_nativeGetScreenBuffer(
    JNIEnv* env, jobject thiz, jlong handle) {
    
    // Returns a direct ByteBuffer for zero-copy access from Kotlin
    // This would map to screen buffer in shared memory
    return nullptr; // Placeholder for actual implementation
}

} // extern "C"

