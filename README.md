# PipCamera

Minimal CameraX + Compose application with Camera2 interop for capture request streaming.

## Architecture
- **CameraManager**: CameraX lifecycle and configuration
- **CaptureController**: Camera2 interop capture requests stream
- **CameraScreen**: Compose UI with CameraXViewfinder
- **PermissionHandler**: Camera permission management

## Key Features
- CameraX 1.5.1 with Compose-native CameraXViewfinder
- Camera2 interop for custom capture request streams
- MVVM pattern with Compose State
- Flow-based capture request streaming

## Prompt Instructions
- Use simplest abstraction possible
- Ensure good compose design
- Hook stream of capture requests into camera2 interop interface
- Minimal implementation, no unnecessary complexity