#pragma once

#include <string>
#include <vector>
#include <functional>
#include <atomic>
#include <thread>
#include <mutex>
#include <queue>

/**
 * Simple OSC receiver for audio data
 * Receives OSC messages and extracts audio samples
 */
class OSCReceiver {
public:
    using AudioCallback = std::function<void(const std::vector<float>&)>;

    OSCReceiver(int port = 8000);
    ~OSCReceiver();

    /**
     * Start receiving OSC messages
     */
    bool start();

    /**
     * Stop receiving OSC messages
     */
    void stop();

    /**
     * Set callback for received audio data
     */
    void setAudioCallback(AudioCallback callback);

    /**
     * Get latest received audio data
     */
    std::vector<float> getLatestAudioData();

    /**
     * Check if receiver is running
     */
    bool isRunning() const { return running_; }

    /**
     * Get number of messages received
     */
    uint64_t getMessageCount() const { return message_count_; }

private:
    void receiveLoop();
    void parseOSCMessage(const std::string& data);

    int port_;
    int socket_fd_;
    std::atomic<bool> running_;
    std::thread receive_thread_;

    AudioCallback audio_callback_;
    std::mutex data_mutex_;
    std::queue<std::vector<float>> audio_queue_;
    std::vector<float> latest_audio_;

    std::atomic<uint64_t> message_count_;
};

/**
 * Simple OSC message parser for audio data
 */
class OSCParser {
public:
    struct AudioMessage {
        std::string address;
        std::vector<float> samples;
        bool valid;
    };

    static AudioMessage parseAudioMessage(const std::string& data);

private:
    static bool parseFloatArray(const std::string& data, std::vector<float>& samples);
};