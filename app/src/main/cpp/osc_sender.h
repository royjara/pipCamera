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
     * Update OSC destination
     * @param host Target host address
     * @param port Target port number
     */
    void updateDestination(const std::string& host, int port);

    /**
     * Check if OSC sender is connected and ready
     */
    bool isReady() const;

private:
    std::string host_;
    int port_;
    int socket_fd_;
    bool is_connected_;

    bool connect();
    void disconnect();
    void sendOSCMessage(const std::string& address, const std::vector<float>& data);
};