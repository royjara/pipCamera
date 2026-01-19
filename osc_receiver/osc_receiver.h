#pragma once

#include <string>
#include <vector>
#include <functional>
#include <atomic>
#include <thread>
#include <mutex>
#include <queue>

/**
 * Multi-channel OSC receiver for audio, text, and analysis data
 * Receives OSC messages on different channels like TouchDesigner
 */
class OSCReceiver {
public:
    using AudioCallback = std::function<void(const std::vector<float>&)>;
    using TextCallback = std::function<void(const std::string&, const std::string&)>;  // (channel, message)
    using AnalysisCallback = std::function<void(const std::string&, const std::vector<float>&)>;  // (channel, features)

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
     * Set callback for received text messages
     */
    void setTextCallback(TextCallback callback);

    /**
     * Set callback for received analysis data
     */
    void setAnalysisCallback(AnalysisCallback callback);

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
    TextCallback text_callback_;
    AnalysisCallback analysis_callback_;
    std::mutex data_mutex_;
    std::queue<std::vector<float>> audio_queue_;
    std::vector<float> latest_audio_;

    std::atomic<uint64_t> message_count_;
};

/**
 * Multi-type OSC message parser for TouchDesigner-style channels
 */
class OSCParser {
public:
    enum MessageType {
        AUDIO,
        TEXT,
        ANALYSIS,
        UNKNOWN
    };

    struct OSCMessage {
        std::string address;
        MessageType type;
        std::vector<float> floatData;
        std::string textData;
        bool valid;
    };

    static OSCMessage parseMessage(const std::string& data);
    static MessageType getMessageType(const std::string& address);

private:
    static bool parseFloatArray(const std::string& data, std::vector<float>& samples);
    static bool parseTextMessage(const std::string& data, std::string& text);
};