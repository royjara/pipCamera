#include "audio_output.h"
#include <iostream>
#include <algorithm>
#include <cstring>

AudioOutput::AudioOutput(int sample_rate, int buffer_size)
    : sample_rate_(sample_rate)
    , buffer_size_(buffer_size)
    , running_(false)
    , initialized_(false)
    , volume_(0.5f)
    , stream_(nullptr)
    , buffer_position_(0) {
}

AudioOutput::~AudioOutput() {
    stop();

    if (initialized_) {
        Pa_Terminate();
    }
}

bool AudioOutput::initialize() {
    if (initialized_) {
        return true;
    }

    // Initialize PortAudio
    PaError err = Pa_Initialize();
    if (err != paNoError) {
        std::cerr << "PortAudio error: " << Pa_GetErrorText(err) << std::endl;
        return false;
    }

    initialized_ = true;
    std::cout << "PortAudio initialized successfully" << std::endl;
    return true;
}

bool AudioOutput::start() {
    if (!initialized_ || running_) {
        return false;
    }

    // Set up output parameters
    PaStreamParameters output_params;
    output_params.device = Pa_GetDefaultOutputDevice();
    if (output_params.device == paNoDevice) {
        std::cerr << "No default output device found" << std::endl;
        return false;
    }

    output_params.channelCount = 1; // Mono output
    output_params.sampleFormat = paFloat32;
    output_params.suggestedLatency = Pa_GetDeviceInfo(output_params.device)->defaultLowOutputLatency;
    output_params.hostApiSpecificStreamInfo = nullptr;

    // Open audio stream
    PaError err = Pa_OpenStream(&stream_,
                               nullptr, // No input
                               &output_params,
                               sample_rate_,
                               buffer_size_,
                               paClipOff,
                               audioCallback,
                               this);

    if (err != paNoError) {
        std::cerr << "Failed to open audio stream: " << Pa_GetErrorText(err) << std::endl;
        return false;
    }

    // Start the stream
    err = Pa_StartStream(stream_);
    if (err != paNoError) {
        std::cerr << "Failed to start audio stream: " << Pa_GetErrorText(err) << std::endl;
        Pa_CloseStream(stream_);
        stream_ = nullptr;
        return false;
    }

    running_ = true;
    std::cout << "Audio output started (Sample rate: " << sample_rate_
              << ", Buffer size: " << buffer_size_ << ")" << std::endl;
    return true;
}

void AudioOutput::stop() {
    if (!running_ || !stream_) {
        return;
    }

    running_ = false;

    PaError err = Pa_StopStream(stream_);
    if (err != paNoError) {
        std::cerr << "Error stopping stream: " << Pa_GetErrorText(err) << std::endl;
    }

    err = Pa_CloseStream(stream_);
    if (err != paNoError) {
        std::cerr << "Error closing stream: " << Pa_GetErrorText(err) << std::endl;
    }

    stream_ = nullptr;
    std::cout << "Audio output stopped" << std::endl;
}

void AudioOutput::addAudioData(const std::vector<float>& samples) {
    if (samples.empty()) {
        return;
    }

    std::lock_guard<std::mutex> lock(audio_mutex_);

    // Add samples to queue
    audio_queue_.push(samples);

    // Keep queue size reasonable
    while (audio_queue_.size() > 20) {
        audio_queue_.pop();
    }
}

void AudioOutput::setVolume(float volume) {
    volume_ = std::clamp(volume, 0.0f, 1.0f);
}

int AudioOutput::audioCallback(const void* input_buffer,
                              void* output_buffer,
                              unsigned long frame_count,
                              const PaStreamCallbackTimeInfo* time_info,
                              PaStreamCallbackFlags status_flags,
                              void* user_data) {
    AudioOutput* audio_output = static_cast<AudioOutput*>(user_data);
    float* output = static_cast<float*>(output_buffer);

    return audio_output->processAudio(output, frame_count);
}

int AudioOutput::processAudio(float* output, unsigned long frame_count) {
    std::lock_guard<std::mutex> lock(audio_mutex_);

    // Clear output buffer
    std::memset(output, 0, frame_count * sizeof(float));

    float vol = volume_.load();
    size_t frames_filled = 0;

    while (frames_filled < frame_count) {
        // If we need a new buffer, get one from the queue
        if (buffer_position_ >= current_buffer_.size()) {
            if (!audio_queue_.empty()) {
                current_buffer_ = audio_queue_.front();
                audio_queue_.pop();
                buffer_position_ = 0;
            } else {
                // No more audio data, fill rest with silence
                break;
            }
        }

        // Copy samples from current buffer to output
        size_t samples_available = current_buffer_.size() - buffer_position_;
        size_t samples_needed = frame_count - frames_filled;
        size_t samples_to_copy = std::min(samples_available, samples_needed);

        for (size_t i = 0; i < samples_to_copy; ++i) {
            output[frames_filled + i] = current_buffer_[buffer_position_ + i] * vol;
        }

        buffer_position_ += samples_to_copy;
        frames_filled += samples_to_copy;
    }

    return paContinue;
}