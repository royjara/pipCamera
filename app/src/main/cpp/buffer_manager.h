#pragma once

#include <memory>
#include <vector>
#include <mutex>

/**
 * Buffer manager for efficient audio memory allocation
 * Abstracts buffer management for future optimization
 */
class BufferManager {
public:
    BufferManager(int buffer_size, int inlet_count, int outlet_count);
    ~BufferManager() = default;

    /**
     * Get a reusable audio buffer
     * @return Shared pointer to audio buffer
     */
    std::shared_ptr<float> getAudioBuffer();

    /**
     * Get buffer for specific inlet
     * @param inlet_index Index of the inlet
     * @return Pointer to inlet buffer
     */
    float* getInletBuffer(int inlet_index);

    /**
     * Get buffer for specific outlet
     * @param outlet_index Index of the outlet
     * @return Pointer to outlet buffer
     */
    float* getOutletBuffer(int outlet_index);

    /**
     * Get the configured buffer size
     */
    int getBufferSize() const { return buffer_size_; }

    /**
     * Get inlet count
     */
    int getInletCount() const { return inlet_count_; }

    /**
     * Get outlet count
     */
    int getOutletCount() const { return outlet_count_; }

    /**
     * Clear all buffers (set to zero)
     */
    void clearBuffers();

private:
    int buffer_size_;
    int inlet_count_;
    int outlet_count_;

    std::shared_ptr<float> main_audio_buffer_;
    std::vector<std::unique_ptr<float[]>> inlet_buffers_;
    std::vector<std::unique_ptr<float[]>> outlet_buffers_;

    std::mutex buffer_mutex_;

    void allocateBuffers();
};