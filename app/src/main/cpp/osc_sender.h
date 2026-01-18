#pragma once

#include <string>
#include <vector>

/**
 * Simple OSC sender for audio data transmission
 * Future integration point for full AOO library
 */
class OSCSender {
public:
    OSCSender(const std::string& host, int port);
    ~OSCSender();

    /**
     * Send audio data via OSC
     * @param audio_data Pointer to audio samples
     * @param frame_count Number of audio frames
     */
    void sendAudio(const float* audio_data, int frame_count);

    /**
     * Send audio data to specific OSC address
     * @param address OSC address/topic
     * @param audio_data Pointer to audio samples
     * @param frame_count Number of audio frames
     */
    void sendAudio(const std::string& address, const float* audio_data, int frame_count);

    /**
     * Update OSC destination
     * @param host Target host address
     * @param port Target port number
     */
    void updateDestination(const std::string& host, int port);

    /**
     * Set default OSC address for audio streams
     * @param address Default OSC address (e.g., "/audio/stream")
     */
    void setDefaultAddress(const std::string& address);

    /**
     * Check if OSC sender is connected and ready
     */
    bool isReady() const;

private:
    std::string host_;
    int port_;
    int socket_fd_;
    bool is_connected_;
    std::string default_address_;

    bool connect();
    void disconnect();
    void sendOSCMessage(const std::string& address, const std::vector<float>& data);
};