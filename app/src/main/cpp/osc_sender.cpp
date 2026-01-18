#include "osc_sender.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>

#define LOG_TAG "OSCSender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

OSCSender::OSCSender(const std::string& host, int port)
    : host_(host)
    , port_(port)
    , socket_fd_(-1)
    , is_connected_(false) {
    connect();
}

OSCSender::~OSCSender() {
    disconnect();
}

void OSCSender::sendAudio(const float* audio_data, int frame_count) {
    if (!isReady() || !audio_data || frame_count <= 0) {
        return;
    }

    // Convert audio data to vector for OSC message
    std::vector<float> data(audio_data, audio_data + frame_count);

    // Send as OSC message with audio data
    sendOSCMessage("/audio/stream", data);
}

void OSCSender::updateDestination(const std::string& host, int port) {
    disconnect();
    host_ = host;
    port_ = port;
    connect();
}

bool OSCSender::isReady() const {
    return is_connected_ && socket_fd_ >= 0;
}

bool OSCSender::connect() {
    disconnect(); // Ensure clean state

    socket_fd_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (socket_fd_ < 0) {
        LOGE("Failed to create UDP socket");
        return false;
    }

    // For UDP, we don't need to explicitly connect
    // We'll send to the destination each time
    is_connected_ = true;

    LOGI("OSC sender ready for %s:%d", host_.c_str(), port_);
    return true;
}

void OSCSender::disconnect() {
    if (socket_fd_ >= 0) {
        close(socket_fd_);
        socket_fd_ = -1;
    }
    is_connected_ = false;
}

void OSCSender::sendOSCMessage(const std::string& address, const std::vector<float>& data) {
    if (!isReady()) {
        return;
    }

    // Simple OSC message format for prototype
    // Real implementation would use proper OSC encoding

    struct sockaddr_in dest_addr;
    memset(&dest_addr, 0, sizeof(dest_addr));
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(port_);
    dest_addr.sin_addr.s_addr = inet_addr(host_.c_str());

    // Create a simple message header
    std::string message = address + " ";

    // Append audio data (simplified format)
    // In a real implementation, this would use proper OSC binary format
    for (size_t i = 0; i < data.size() && i < 64; ++i) { // Limit message size
        message += std::to_string(data[i]) + " ";
    }

    ssize_t sent = sendto(socket_fd_, message.c_str(), message.length(), 0,
                         (struct sockaddr*)&dest_addr, sizeof(dest_addr));

    if (sent < 0) {
        LOGE("Failed to send OSC message");
    }
}