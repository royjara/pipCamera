# OSC Audio Receiver

A standalone macOS application to receive and monitor OSC audio streams from the PipCamera Android app.

## Features

- **OSC Audio Reception**: Receives audio data over OSC protocol
- **Real-time Playback**: Plays received audio through system speakers
- **Live Monitoring**: Shows real-time status including message count and data rate
- **Configurable Options**: Adjustable port, volume, and silent mode
- **Minimal Dependencies**: Uses only PortAudio for cross-platform audio

## Quick Start

### Build and Run

```bash
# Make build script executable
chmod +x build.sh

# Build the receiver
./build.sh

# Run the receiver
cd build
./osc_audio_receiver
```

### Usage Examples

```bash
# Basic usage - listen on port 8000
./osc_audio_receiver

# Listen on custom port
./osc_audio_receiver -p 9000

# Set volume (0.0 to 1.0)
./osc_audio_receiver -v 0.8

# Silent mode (monitoring only, no audio output)
./osc_audio_receiver -s

# Show help
./osc_audio_receiver -h
```

## Testing with Android App

1. **Find your Mac's IP address:**
   ```bash
   ifconfig | grep "inet " | grep -v 127.0.0.1
   ```

2. **Start the receiver:**
   ```bash
   cd build
   ./osc_audio_receiver
   ```

3. **Configure Android app:**
   - Open PipCamera app
   - Tap Settings (FAB) to open audio demo
   - Set OSC Host to your Mac's IP address
   - Set OSC Port to 8000 (or your chosen port)
   - Set OSC Address to `/audio/stream`

4. **Start audio stream:**
   - Tap "Start Stream" in the Android app
   - You should see messages appear in the receiver console
   - Audio should play through your Mac's speakers

## Output Example

```
OSC Audio Receiver
==================
Port: 8000
Volume: 0.5
Audio output: enabled

Receiver started. Listening for OSC audio messages...
Press Ctrl+C to quit.

Time: 00:15 | Messages: 342 | Rate: 22.8 msg/s | Audio: ON (vol: 0.5)
Received audio: /audio/stream with 64 samples
Received audio: /audio/stream with 64 samples
```

## Dependencies

- **PortAudio**: Cross-platform audio I/O library
- **CMake**: Build system (version 3.20+)
- **C++17**: Standard library

Dependencies are automatically installed via Homebrew if not present.

## Architecture

- **OSCReceiver**: UDP socket-based OSC message reception and parsing
- **AudioOutput**: PortAudio-based real-time audio playback
- **Main Loop**: Status monitoring and signal handling

## Troubleshooting

### Build Issues

- **PortAudio not found**: Run `brew install portaudio`
- **CMake not found**: Run `brew install cmake`
- **Permission denied**: Run `chmod +x build.sh`

### Runtime Issues

- **No audio output**: Check volume settings and system audio preferences
- **Connection refused**: Ensure firewall allows incoming connections on the chosen port
- **No messages received**: Verify IP address and port configuration in Android app

### Network Issues

- **Firewall**: macOS may block incoming connections. Allow the receiver in System Preferences > Security & Privacy > Firewall
- **Network**: Ensure Mac and Android device are on the same Wi-Fi network
- **IP Address**: Use `ifconfig` to verify your Mac's correct IP address

## Integration

This receiver is designed to work with the PipCamera Android app's audio processing pipeline. It uses the same simplified OSC format for compatibility and testing.

For production use, consider integrating with full AOO (Audio over OSC) library for advanced features like:
- Audio compression
- Network redundancy
- Multi-channel support
- Synchronization