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
    if (!isReady() || data.empty()) {
        return;
    }

    // Limit data size to prevent excessive memory allocation
    if (data.size() > 4096) {
        LOGE("Audio data too large: %zu samples", data.size());
        return;
    }

    struct sockaddr_in dest_addr;
    memset(&dest_addr, 0, sizeof(dest_addr));
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(port_);

    // Use inet_pton for better address parsing
    if (inet_pton(AF_INET, host_.c_str(), &dest_addr.sin_addr) <= 0) {
        LOGE("Invalid host address: %s", host_.c_str());
        return;
    }

    // Send smaller chunks to reduce memory pressure and network load
    const size_t chunk_size = 128; // Reduced chunk size
    const size_t total_chunks = (data.size() + chunk_size - 1) / chunk_size;

    // Limit number of chunks to prevent network flooding
    if (total_chunks > 32) {
        LOGE("Too many chunks required: %zu", total_chunks);
        return;
    }

    for (size_t chunk = 0; chunk < total_chunks; ++chunk) {
        size_t start_idx = chunk * chunk_size;
        size_t end_idx = std::min(start_idx + chunk_size, data.size());

        // Create message for this chunk with pre-allocated size
        std::string message;
        message.reserve(900); // Conservative estimate for 128 samples
        message = address;

        // Add chunk info if multiple chunks
        if (total_chunks > 1) {
            message += "_" + std::to_string(chunk) + " ";
        } else {
            message += " ";
        }

        // Add samples with compact formatting - fixed buffer size
        char buffer[16];
        for (size_t i = start_idx; i < end_idx; ++i) {
            // Clamp values to prevent formatting issues
            float sample = std::max(-1.0f, std::min(1.0f, data[i]));
            int ret = snprintf(buffer, sizeof(buffer), "%.3f ", sample);
            if (ret > 0 && ret < static_cast<int>(sizeof(buffer))) {
                message += buffer;
            }
        }

        // Send with error checking
        ssize_t sent = sendto(socket_fd_, message.c_str(), message.length(), 0,
                             (struct sockaddr*)&dest_addr, sizeof(dest_addr));

        if (sent < 0) {
            LOGE("Failed to send OSC message chunk %zu: errno=%d", chunk, errno);
            break;
        }
    }

#ifdef DEBUG
    static int message_count = 0;
    if (++message_count % 100 == 0) { // Log every 100th message
        LOGI("Sent OSC message #%d: %zu samples in %zu chunks",
             message_count, data.size(), total_chunks);
    }
#endif
}