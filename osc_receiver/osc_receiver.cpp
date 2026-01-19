#include "osc_receiver.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <iostream>
#include <sstream>
#include <cstring>
#include <iomanip>
#include <map>

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

void OSCReceiver::setTextCallback(TextCallback callback) {
    text_callback_ = callback;
}

void OSCReceiver::setAnalysisCallback(AnalysisCallback callback) {
    analysis_callback_ = callback;
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
    OSCParser::OSCMessage msg = OSCParser::parseMessage(data);

    if (msg.valid) {
        // Reduced verbosity - only show channel info
        static std::map<std::string, int> channel_counts;
        channel_counts[msg.address]++;

        if (channel_counts[msg.address] % 100 == 1) {  // Show every 100th message
            std::string typeStr = (msg.type == OSCParser::AUDIO) ? "audio" :
                                (msg.type == OSCParser::TEXT) ? "text" :
                                (msg.type == OSCParser::ANALYSIS) ? "analysis" : "unknown";
            std::cout << "[" << msg.address << "] " << typeStr << " (msg #" << channel_counts[msg.address] << ") ";
        }

        // Route to appropriate callback
        switch (msg.type) {
            case OSCParser::AUDIO:
                if (!msg.floatData.empty()) {
                    {
                        std::lock_guard<std::mutex> lock(data_mutex_);
                        latest_audio_ = msg.floatData;
                        audio_queue_.push(msg.floatData);
                        while (audio_queue_.size() > 10) { // Keep last 10 audio buffers
                            audio_queue_.pop();
                        }
                    }
                    if (audio_callback_) {
                        audio_callback_(msg.floatData);
                    }
                }
                break;
            case OSCParser::TEXT:
                if (text_callback_) {
                    text_callback_(msg.address, msg.textData);
                }
                break;
            case OSCParser::ANALYSIS:
                if (analysis_callback_) {
                    analysis_callback_(msg.address, msg.floatData);
                }
                break;
            default:
                break;
        }
    }
}

// OSC Parser implementation
OSCParser::OSCMessage OSCParser::parseMessage(const std::string& data) {
    OSCMessage msg;
    msg.valid = false;
    msg.type = UNKNOWN;

    std::istringstream iss(data);
    std::string token;

    // Get address
    if (iss >> token) {
        msg.address = token;
        msg.type = getMessageType(token);

        switch (msg.type) {
            case AUDIO:
                // Parse remaining tokens as float samples
                while (iss >> token) {
                    try {
                        float sample = std::stof(token);
                        msg.floatData.push_back(sample);
                    } catch (const std::exception&) {
                        continue;
                    }
                }
                msg.valid = !msg.floatData.empty();
                break;

            case TEXT:
                // Parse remaining text as single string
                {
                    std::string restOfLine;
                    std::getline(iss, restOfLine);
                    if (!restOfLine.empty() && restOfLine[0] == ' ') {
                        restOfLine = restOfLine.substr(1); // Remove leading space
                    }
                    msg.textData = restOfLine;
                    msg.valid = !msg.textData.empty();
                }
                break;

            case ANALYSIS:
                // Parse as float array (ML features)
                while (iss >> token) {
                    try {
                        float value = std::stof(token);
                        msg.floatData.push_back(value);
                    } catch (const std::exception&) {
                        continue;
                    }
                }
                msg.valid = !msg.floatData.empty();
                break;

            default:
                break;
        }
    }

    return msg;
}

OSCParser::MessageType OSCParser::getMessageType(const std::string& address) {
    // TouchDesigner-style channel routing
    if (address.find("/chan1/audio") == 0 ||
        address.find("/audio/") == 0 ||
        address.find("audio") != std::string::npos) {
        return AUDIO;
    } else if (address.find("/chan2/text") == 0 ||
               address.find("/text/") == 0 ||
               address.find("text") != std::string::npos) {
        return TEXT;
    } else if (address.find("/chan3/analysis") == 0 ||
               address.find("/analysis/") == 0 ||
               address.find("/features/") == 0 ||
               address.find("analysis") != std::string::npos ||
               address.find("features") != std::string::npos) {
        return ANALYSIS;
    }
    return UNKNOWN;
}