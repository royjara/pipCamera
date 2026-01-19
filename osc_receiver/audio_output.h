#pragma once

#include <vector>
#include <atomic>
#include <mutex>
#include <queue>
#include <portaudio.h>

/**
 * Audio output using PortAudio
 * Plays received audio samples through the default audio device
 */
class AudioOutput {
public:
    AudioOutput(int sample_rate = 44100, int buffer_size = 512);
    ~AudioOutput();

    /**
     * Initialize audio output
     */
    bool initialize();

    /**
     * Start audio output
     */
    bool start();

    /**
     * Stop audio output
     */
    void stop();

    /**
     * Add audio data to output queue
     */
    void addAudioData(const std::vector<float>& samples);

    /**
     * Check if audio output is running
     */
    bool isRunning() const { return running_; }

    /**
     * Get output volume (0.0 - 1.0)
     */
    float getVolume() const { return volume_; }

    /**
     * Set output volume (0.0 - 1.0)
     */
    void setVolume(float volume);

private:
    static int audioCallback(const void* input_buffer,
                           void* output_buffer,
                           unsigned long frame_count,
                           const PaStreamCallbackTimeInfo* time_info,
                           PaStreamCallbackFlags status_flags,
                           void* user_data);

    int processAudio(float* output, unsigned long frame_count);

    int sample_rate_;
    int buffer_size_;
    std::atomic<bool> running_;
    std::atomic<bool> initialized_;
    std::atomic<float> volume_;

    PaStream* stream_;
    std::mutex audio_mutex_;
    std::queue<std::vector<float>> audio_queue_;
    std::vector<float> current_buffer_;
    size_t buffer_position_;
};