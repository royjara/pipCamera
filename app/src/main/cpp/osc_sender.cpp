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
    , is_connected_(false)
    , default_address_("/audio/stream") {
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

    // Send as OSC message with default address
    sendOSCMessage(default_address_, data);
}

void OSCSender::sendAudio(const std::string& address, const float* audio_data, int frame_count) {
    if (!isReady() || !audio_data || frame_count <= 0) {
        return;
    }

    // Convert audio data to vector for OSC message
    std::vector<float> data(audio_data, audio_data + frame_count);

    // Send as OSC message with custom address
    sendOSCMessage(address, data);
}

void OSCSender::updateDestination(const std::string& host, int port) {
    disconnect();
    host_ = host;
    port_ = port;
    connect();
}

void OSCSender::setDefaultAddress(const std::string& address) {
    default_address_ = address;
    LOGI("Default OSC address set to: %s", address.c_str());
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

    struct sockaddr_in dest_addr;
    memset(&dest_addr, 0, sizeof(dest_addr));
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(port_);
    dest_addr.sin_addr.s_addr = inet_addr(host_.c_str());

    // Split large buffers into chunks that fit in UDP packets
    const size_t chunk_size = 256; // Send 256 samples per message
    const size_t total_chunks = (data.size() + chunk_size - 1) / chunk_size;

    for (size_t chunk = 0; chunk < total_chunks; ++chunk) {
        size_t start_idx = chunk * chunk_size;
        size_t end_idx = std::min(start_idx + chunk_size, data.size());
        size_t samples_in_chunk = end_idx - start_idx;

        // Create message for this chunk
        std::string message;
        message.reserve(1800); // ~256 * 7 chars per sample
        message = address;

        // Add chunk info if multiple chunks
        if (total_chunks > 1) {
            message += "_" + std::to_string(chunk) + " ";
        } else {
            message += " ";
        }

        // Add samples with compact formatting
        char buffer[8];
        for (size_t i = start_idx; i < end_idx; ++i) {
            snprintf(buffer, sizeof(buffer), "%.3f ", data[i]);
            message += buffer;
        }

        ssize_t sent = sendto(socket_fd_, message.c_str(), message.length(), 0,
                             (struct sockaddr*)&dest_addr, sizeof(dest_addr));

        if (sent < 0) {
            LOGE("Failed to send OSC message chunk %zu", chunk);
            break;
        }
    }

#ifdef DEBUG
    static int message_count = 0;
    if (++message_count % 50 == 0) { // Log every 50th message
        LOGI("Sent OSC message #%d: %zu samples in %zu chunks",
             message_count, data.size(), total_chunks);
    }
#endif
}