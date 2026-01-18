#include "buffer_manager.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "BufferManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

BufferManager::BufferManager(int buffer_size, int inlet_count, int outlet_count)
    : buffer_size_(buffer_size)
    , inlet_count_(inlet_count)
    , outlet_count_(outlet_count) {

    LOGI("Creating buffer manager: size=%d, inlets=%d, outlets=%d",
         buffer_size_, inlet_count_, outlet_count_);

    allocateBuffers();
}

std::shared_ptr<float> BufferManager::getAudioBuffer() {
    std::lock_guard<std::mutex> lock(buffer_mutex_);
    return main_audio_buffer_;
}

float* BufferManager::getInletBuffer(int inlet_index) {
    if (inlet_index < 0 || inlet_index >= inlet_count_) {
        LOGE("Invalid inlet index: %d", inlet_index);
        return nullptr;
    }
    return inlet_buffers_[inlet_index].get();
}

float* BufferManager::getOutletBuffer(int outlet_index) {
    if (outlet_index < 0 || outlet_index >= outlet_count_) {
        LOGE("Invalid outlet index: %d", outlet_index);
        return nullptr;
    }
    return outlet_buffers_[outlet_index].get();
}

void BufferManager::clearBuffers() {
    std::lock_guard<std::mutex> lock(buffer_mutex_);

    // Clear main audio buffer
    if (main_audio_buffer_) {
        std::memset(main_audio_buffer_.get(), 0, buffer_size_ * sizeof(float));
    }

    // Clear inlet buffers
    for (auto& buffer : inlet_buffers_) {
        if (buffer) {
            std::memset(buffer.get(), 0, buffer_size_ * sizeof(float));
        }
    }

    // Clear outlet buffers
    for (auto& buffer : outlet_buffers_) {
        if (buffer) {
            std::memset(buffer.get(), 0, buffer_size_ * sizeof(float));
        }
    }
}

void BufferManager::allocateBuffers() {
    try {
        // Allocate main audio buffer with custom deleter for alignment
        main_audio_buffer_.reset(
            new float[buffer_size_](),
            [](float* p) { delete[] p; }
        );

        // Allocate inlet buffers
        inlet_buffers_.reserve(inlet_count_);
        for (int i = 0; i < inlet_count_; ++i) {
            inlet_buffers_.emplace_back(std::make_unique<float[]>(buffer_size_));
        }

        // Allocate outlet buffers
        outlet_buffers_.reserve(outlet_count_);
        for (int i = 0; i < outlet_count_; ++i) {
            outlet_buffers_.emplace_back(std::make_unique<float[]>(buffer_size_));
        }

        // Clear all buffers to start with silence
        clearBuffers();

        LOGI("Buffer allocation completed successfully");

    } catch (const std::exception& e) {
        LOGE("Buffer allocation failed: %s", e.what());
        throw;
    }
}