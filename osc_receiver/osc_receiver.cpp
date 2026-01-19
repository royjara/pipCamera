#include "osc_receiver.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <iostream>
#include <sstream>
#include <cstring>
#include <iomanip>

OSCReceiver::OSCReceiver(int port)
    : port_(port)
    , socket_fd_(-1)
    , running_(false)
    , message_count_(0) {
}

OSCReceiver::~OSCReceiver() {
    stop();
}

bool OSCReceiver::start() {
    if (running_) {
        return true;
    }

    // Create UDP socket
    socket_fd_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (socket_fd_ < 0) {
        std::cerr << "Failed to create socket" << std::endl;
        return false;
    }

    // Set socket options
    int opt = 1;
    if (setsockopt(socket_fd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        std::cerr << "Failed to set socket options" << std::endl;
        close(socket_fd_);
        return false;
    }

    // Bind to port
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port_);

    if (bind(socket_fd_, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        std::cerr << "Failed to bind to port " << port_ << std::endl;
        close(socket_fd_);
        return false;
    }

    running_ = true;
    receive_thread_ = std::thread(&OSCReceiver::receiveLoop, this);

    std::cout << "OSC Receiver started on port " << port_ << std::endl;
    return true;
}

void OSCReceiver::stop() {
    if (!running_) {
        return;
    }

    running_ = false;

    // Close socket first to unblock receive thread
    if (socket_fd_ >= 0) {
        close(socket_fd_);
        socket_fd_ = -1;
    }

    // Wait for thread to finish
    if (receive_thread_.joinable()) {
        receive_thread_.join();
    }

    std::cout << "OSC Receiver stopped" << std::endl;
}

void OSCReceiver::setAudioCallback(AudioCallback callback) {
    audio_callback_ = callback;
}

std::vector<float> OSCReceiver::getLatestAudioData() {
    std::lock_guard<std::mutex> lock(data_mutex_);
    return latest_audio_;
}

void OSCReceiver::receiveLoop() {
    char buffer[4096];
    struct sockaddr_in sender_addr;
    socklen_t sender_len = sizeof(sender_addr);

    while (running_) {
        ssize_t bytes_received = recvfrom(socket_fd_, buffer, sizeof(buffer) - 1, 0,
                                        (struct sockaddr*)&sender_addr, &sender_len);

        if (bytes_received > 0) {
            buffer[bytes_received] = '\0';
            std::string data(buffer, bytes_received);
            parseOSCMessage(data);
            message_count_++;
        } else if (bytes_received < 0) {
            // Socket error or closed - exit gracefully
            if (running_) {
                std::cerr << "Socket error in receive loop, stopping..." << std::endl;
                running_ = false;
            }
            break;
        }
    }
}

void OSCReceiver::parseOSCMessage(const std::string& data) {
    // Simple parser for our audio messages
    auto audio_msg = OSCParser::parseAudioMessage(data);

    if (audio_msg.valid && !audio_msg.samples.empty()) {
        {
            std::lock_guard<std::mutex> lock(data_mutex_);
            latest_audio_ = audio_msg.samples;

            // Keep a queue of recent audio data
            audio_queue_.push(audio_msg.samples);
            while (audio_queue_.size() > 10) { // Keep last 10 audio buffers
                audio_queue_.pop();
            }
        }

        // Call callback if set
        if (audio_callback_) {
            audio_callback_(audio_msg.samples);
        }

        std::cout << "Received audio: " << audio_msg.address
                  << " with " << audio_msg.samples.size() << " samples";
        if (!audio_msg.samples.empty()) {
            std::cout << " (first: " << std::fixed << std::setprecision(3)
                     << audio_msg.samples[0] << ")";
        }
        std::cout << std::endl;
    }
}

// OSC Parser implementation
OSCParser::AudioMessage OSCParser::parseAudioMessage(const std::string& data) {
    AudioMessage msg;
    msg.valid = false;

    // Simple text-based parsing for our prototype OSC format
    // Format: "/audio/stream sample1 sample2 sample3 ..."

    std::istringstream iss(data);
    std::string token;

    // Get address
    if (iss >> token) {
        msg.address = token;

        // Parse remaining tokens as float samples
        while (iss >> token) {
            try {
                float sample = std::stof(token);
                msg.samples.push_back(sample);
            } catch (const std::exception&) {
                // Skip invalid tokens
                continue;
            }
        }

        if (!msg.samples.empty()) {
            msg.valid = true;
        }
    }

    return msg;
}