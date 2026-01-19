#include <iostream>
#include <string>
#include <signal.h>
#include <unistd.h>
#include <chrono>
#include <iomanip>
#include <ifaddrs.h>
#include <arpa/inet.h>

#include "osc_receiver.h"
#include "audio_output.h"

// Global variables for signal handling
static bool g_running = true;
static OSCReceiver* g_receiver = nullptr;
static AudioOutput* g_audio_output = nullptr;

void signalHandler(int signal) {
    std::cout << "\nReceived signal " << signal << ", shutting down..." << std::endl;
    g_running = false;

    // Stop the receiver to unblock the receive thread
    if (g_receiver) {
        g_receiver->stop();
    }
}

void printLocalIPs() {
    struct ifaddrs *ifaddrs_ptr = nullptr;

    if (getifaddrs(&ifaddrs_ptr) == 0) {
        std::cout << "Local IP addresses:" << std::endl;

        for (struct ifaddrs *ifa = ifaddrs_ptr; ifa != nullptr; ifa = ifa->ifa_next) {
            if (ifa->ifa_addr && ifa->ifa_addr->sa_family == AF_INET) {
                struct sockaddr_in *addr_in = (struct sockaddr_in *)ifa->ifa_addr;
                char ip_str[INET_ADDRSTRLEN];

                if (inet_ntop(AF_INET, &addr_in->sin_addr, ip_str, INET_ADDRSTRLEN)) {
                    if (strcmp(ip_str, "127.0.0.1") != 0) {  // Skip localhost
                        std::cout << "  " << ifa->ifa_name << ": " << ip_str << std::endl;
                    }
                }
            }
        }

        freeifaddrs(ifaddrs_ptr);
    } else {
        std::cout << "Could not get local IP addresses" << std::endl;
    }
}

void printUsage(const char* program_name) {
    std::cout << "Usage: " << program_name << " [options]" << std::endl;
    std::cout << "Options:" << std::endl;
    std::cout << "  -p <port>     OSC port to listen on (default: 8000)" << std::endl;
    std::cout << "  -v <volume>   Output volume 0.0-1.0 (default: 0.5)" << std::endl;
    std::cout << "  -s            Silent mode (no audio output)" << std::endl;
    std::cout << "  -h            Show this help message" << std::endl;
    std::cout << std::endl;
    std::cout << "This receiver will listen for OSC audio messages and optionally play them back." << std::endl;
    std::cout << "Press Ctrl+C to quit." << std::endl;
}

void printStatus(const OSCReceiver& receiver, const AudioOutput* audio_output) {
    static auto start_time = std::chrono::steady_clock::now();
    static uint64_t last_message_count = 0;
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(now - start_time);

    uint64_t message_count = receiver.getMessageCount();
    double messages_per_second = static_cast<double>(message_count) / (elapsed.count() > 0 ? elapsed.count() : 1);

    // Only update status line if there are new messages or every 5 seconds
    if (message_count != last_message_count || elapsed.count() % 5 == 0) {
        std::cout << "\r" << std::string(80, ' ') << "\r"; // Clear line
        std::cout << "Time: " << std::setfill('0') << std::setw(2) << elapsed.count() / 60 << ":"
                  << std::setw(2) << elapsed.count() % 60
                  << " | Total: " << message_count
                  << " | Rate: " << std::fixed << std::setprecision(1) << messages_per_second << " msg/s";

        if (audio_output && audio_output->isRunning()) {
            std::cout << " | Audio: ON";
        } else {
            std::cout << " | Audio: OFF";
        }

        std::cout << " | Channels: Audio/Text/Analysis" << std::flush;
        last_message_count = message_count;
    }
}

int main(int argc, char* argv[]) {
    int port = 8000;
    float volume = 0.5f;
    bool silent_mode = false;

    // Parse command line arguments
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];

        if (arg == "-h") {
            printUsage(argv[0]);
            return 0;
        } else if (arg == "-p" && i + 1 < argc) {
            port = std::atoi(argv[++i]);
        } else if (arg == "-v" && i + 1 < argc) {
            volume = std::atof(argv[++i]);
            volume = std::clamp(volume, 0.0f, 1.0f);
        } else if (arg == "-s") {
            silent_mode = true;
        } else {
            std::cerr << "Unknown argument: " << arg << std::endl;
            printUsage(argv[0]);
            return 1;
        }
    }

    // Set up signal handling
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);

    std::cout << "OSC Multi-Channel Receiver" << std::endl;
    std::cout << "===========================" << std::endl;

    printLocalIPs();
    std::cout << std::endl;

    std::cout << "Port: " << port << std::endl;
    std::cout << "Volume: " << volume << std::endl;
    std::cout << "Audio output: " << (silent_mode ? "disabled" : "enabled") << std::endl;
    std::cout << "Supported channels:" << std::endl;
    std::cout << "  • Audio: /chan1/audio or /audio/*" << std::endl;
    std::cout << "  • Text:  /chan2/text or /text/*" << std::endl;
    std::cout << "  • Analysis: /chan3/analysis or /analysis/*" << std::endl;
    std::cout << std::endl;

    // Create OSC receiver
    OSCReceiver receiver(port);
    g_receiver = &receiver;

    // Create audio output (if not in silent mode)
    AudioOutput* audio_output = nullptr;
    if (!silent_mode) {
        audio_output = new AudioOutput();
        g_audio_output = audio_output;

        if (!audio_output->initialize()) {
            std::cerr << "Failed to initialize audio output" << std::endl;
            delete audio_output;
            return 1;
        }

        audio_output->setVolume(volume);

        if (!audio_output->start()) {
            std::cerr << "Failed to start audio output" << std::endl;
            delete audio_output;
            return 1;
        }

        // Set up audio callback to forward received audio to output
        receiver.setAudioCallback([audio_output](const std::vector<float>& samples) {
            audio_output->addAudioData(samples);
        });
    }

    // Set up text message callback
    receiver.setTextCallback([](const std::string& channel, const std::string& message) {
        std::cout << std::endl << "[TEXT " << channel << "] " << message << std::endl;
    });

    // Set up analysis data callback
    receiver.setAnalysisCallback([](const std::string& channel, const std::vector<float>& features) {
        std::cout << std::endl << "[ANALYSIS " << channel << "] " << features.size() << " features: ";
        for (size_t i = 0; i < std::min(features.size(), size_t(5)); ++i) {
            std::cout << std::fixed << std::setprecision(3) << features[i];
            if (i < std::min(features.size(), size_t(5)) - 1) std::cout << ", ";
        }
        if (features.size() > 5) std::cout << "...";
        std::cout << std::endl;
    });

    // Start OSC receiver
    if (!receiver.start()) {
        std::cerr << "Failed to start OSC receiver" << std::endl;
        if (audio_output) {
            delete audio_output;
        }
        return 1;
    }

    std::cout << "Receiver started. Listening for multi-channel OSC messages..." << std::endl;
    std::cout << "Press Ctrl+C to quit." << std::endl;
    std::cout << std::endl;

    // Main loop
    auto last_status_time = std::chrono::steady_clock::now();
    while (g_running) {
        sleep(1);

        // Print status every second
        auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - last_status_time).count() >= 1000) {
            printStatus(receiver, audio_output);
            last_status_time = now;
        }
    }

    std::cout << std::endl << "Shutting down..." << std::endl;

    // Cleanup
    if (g_running) {
        receiver.stop();
    }
    if (audio_output) {
        audio_output->stop();
        delete audio_output;
    }

    std::cout << "Shutdown complete." << std::endl;
    return 0;
}