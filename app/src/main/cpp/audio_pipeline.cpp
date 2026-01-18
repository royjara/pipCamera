#include <jni.h>
#include <android/log.h>
#include <memory>
#include <cmath>
#include <cstring>
#include "sine_generator.h"
#include "osc_sender.h"
#include "buffer_manager.h"

#define LOG_TAG "AudioPipeline"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global instances for the prototype
std::unique_ptr<SineGenerator> g_sine_generator;
std::unique_ptr<OSCSender> g_osc_sender;
std::unique_ptr<BufferManager> g_buffer_manager;

extern "C" {

/**
 * Initialize the audio processing pipeline
 * @param env JNI environment
 * @param thiz Java object
 * @param sample_rate Audio sample rate (e.g., 44100)
 * @param buffer_size Buffer size in frames
 * @param inlet_count Number of input channels
 * @param outlet_count Number of output channels
 */
JNIEXPORT jboolean JNICALL
Java_com_elegia_pipcamera_audio_AudioProcessor_nativeInitialize(
    JNIEnv *env,
    jobject thiz,
    jint sample_rate,
    jint buffer_size,
    jint inlet_count,
    jint outlet_count
) {
    LOGI("Initializing audio pipeline: sr=%d, buffer=%d, inlets=%d, outlets=%d",
         sample_rate, buffer_size, inlet_count, outlet_count);

    try {
        // Initialize buffer manager for efficient memory allocation
        g_buffer_manager = std::make_unique<BufferManager>(buffer_size, inlet_count, outlet_count);

        // Initialize 440Hz sine wave generator
        g_sine_generator = std::make_unique<SineGenerator>(sample_rate, 440.0f);

        // Initialize OSC sender for audio output
        g_osc_sender = std::make_unique<OSCSender>("127.0.0.1", 8000);

        LOGI("Audio pipeline initialized successfully");
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("Failed to initialize audio pipeline: %s", e.what());
        return JNI_FALSE;
    }
}

/**
 * Process audio data through the pipeline
 * @param env JNI environment
 * @param thiz Java object
 * @param input_buffer Input audio data (ByteBuffer)
 * @param output_buffer Output audio data (ByteBuffer)
 * @param frame_count Number of frames to process
 */
JNIEXPORT void JNICALL
Java_com_elegia_pipcamera_audio_AudioProcessor_nativeProcessAudio(
    JNIEnv *env,
    jobject thiz,
    jobject input_buffer,
    jobject output_buffer,
    jint frame_count
) {
    if (!g_sine_generator || !g_osc_sender || !g_buffer_manager) {
        LOGE("Audio pipeline not initialized");
        return;
    }

    // Get direct buffer pointers for efficient data access
    float* input_data = nullptr;
    float* output_data = nullptr;

    if (input_buffer) {
        input_data = static_cast<float*>(env->GetDirectBufferAddress(input_buffer));
    }

    if (output_buffer) {
        output_data = static_cast<float*>(env->GetDirectBufferAddress(output_buffer));
    }

    // Generate sine wave audio (mock microphone)
    auto audio_buffer = g_buffer_manager->getAudioBuffer();
    g_sine_generator->generate(audio_buffer.get(), frame_count);

    // Send audio data via OSC
    g_osc_sender->sendAudio(audio_buffer.get(), frame_count);

    // Copy to output buffer if provided
    if (output_data && audio_buffer) {
        std::memcpy(output_data, audio_buffer.get(), frame_count * sizeof(float));
    }
}

/**
 * Cleanup the audio processing pipeline
 */
JNIEXPORT void JNICALL
Java_com_elegia_pipcamera_audio_AudioProcessor_nativeShutdown(
    JNIEnv *env,
    jobject thiz
) {
    LOGI("Shutting down audio pipeline");

    g_sine_generator.reset();
    g_osc_sender.reset();
    g_buffer_manager.reset();

    LOGI("Audio pipeline shutdown complete");
}

/**
 * Update OSC destination for real-time routing
 */
JNIEXPORT void JNICALL
Java_com_elegia_pipcamera_audio_AudioProcessor_nativeUpdateOSCDestination(
    JNIEnv *env,
    jobject thiz,
    jstring host,
    jint port
) {
    if (!g_osc_sender) {
        LOGE("OSC sender not initialized");
        return;
    }

    const char* host_str = env->GetStringUTFChars(host, nullptr);
    g_osc_sender->updateDestination(host_str, port);
    env->ReleaseStringUTFChars(host, host_str);

    LOGI("OSC destination updated: %s:%d", host_str, port);
}

/**
 * Set OSC address/topic for audio streams
 */
JNIEXPORT void JNICALL
Java_com_elegia_pipcamera_audio_AudioProcessor_nativeSetOSCAddress(
    JNIEnv *env,
    jobject thiz,
    jstring address
) {
    if (!g_osc_sender) {
        LOGE("OSC sender not initialized");
        return;
    }

    const char* address_str = env->GetStringUTFChars(address, nullptr);
    g_osc_sender->setDefaultAddress(address_str);
    env->ReleaseStringUTFChars(address, address_str);

    LOGI("OSC address set: %s", address_str);
}

} // extern "C"