#!/bin/bash

# OSC Audio Receiver Build Script
# Builds the standalone macOS OSC audio receiver

set -e

echo "Building OSC Audio Receiver for macOS..."

# Check for PortAudio
if ! pkg-config --exists portaudio-2.0; then
    echo "PortAudio not found. Installing via Homebrew..."
    if ! command -v brew &> /dev/null; then
        echo "Homebrew not found. Please install Homebrew first:"
        echo "  /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        exit 1
    fi
    brew install portaudio
fi

# Create build directory
mkdir -p build
cd build

# Configure with CMake
echo "Configuring build with CMake..."
cmake .. -DCMAKE_BUILD_TYPE=Release

# Build
echo "Building..."
make -j$(sysctl -n hw.ncpu)

echo "Build complete!"
echo ""
echo "Usage:"
echo "  ./osc_audio_receiver              # Listen on port 8000"
echo "  ./osc_audio_receiver -p 9000      # Listen on port 9000"
echo "  ./osc_audio_receiver -v 0.8       # Set volume to 0.8"
echo "  ./osc_audio_receiver -s           # Silent mode (no audio output)"
echo "  ./osc_audio_receiver -h           # Show help"
echo ""
echo "To test with Android app:"
echo "1. Find your Mac's IP address: ifconfig | grep 'inet '"
echo "2. In the Android audio demo, set OSC host to your Mac's IP"
echo "3. Run: ./osc_audio_receiver"
echo "4. Start the audio stream in the Android app"