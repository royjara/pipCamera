# **PipCamera Architecture Organization Map**

## **1. Core Interface Hierarchy**

### **Level 1: Native Foundation (C++)**
```
AudioPipeline (Native C++)
├── OSC Message Formatting
├── Real-time Audio Processing
├── Network Transport (UDP)
└── Buffer Management
```

### **Level 2: JNI Bridge Layer (Kotlin)**
```
AudioProcessor (JNI Wrapper)
├── initialize() → Native pipeline setup
├── processAudio() → Real-time processing
├── updateOSCDestination() → Network config
├── setOSCAddress() → Channel routing
└── shutdown() → Resource cleanup
```

### **Level 3: Processing Abstractions (Kotlin)**
```
NodeProcessor Interface
├── process(inputs, outputs, frameCount)
├── initialize(config)
├── cleanup()
├── getParameters()
└── updateParameter(name, value)

Implementations:
├── SineGeneratorProcessor (440Hz/Custom frequency)
├── MicrophoneProcessor (Live audio capture)
└── [Future: FilterProcessor, AnalyzerProcessor, etc.]
```

## **2. Media Pipeline Architecture**

### **Pipeline Framework Hierarchy**
```
MediaPipeline (Orchestrator)
├── Node Management
│   ├── addNode(nodeId, node)
│   ├── connect(fromNodeId, toNodeId)
│   └── Graph execution
├── Lifecycle Control
│   ├── start() → Initialize all nodes
│   ├── stop() → Graceful shutdown
│   └── Health monitoring
└── Data Flow Management
    ├── MediaData routing
    ├── Buffer coordination
    └── Error propagation
```

### **Node Abstraction Hierarchy**
```
MediaNode (Abstract Base)
├── nodeId: String
├── initialize(context): Boolean
├── process(input): MediaData?
└── cleanup()

├── InputNode (Data Sources)
│   ├── captureData(): MediaData?
│   └── Implementations:
│       ├── MicrophoneInputNode
│       │   ├── AudioRecord management
│       │   ├── Real-time capture
│       │   └── Format conversion
│       └── [Future: CameraInputNode, FileInputNode]
│
├── OutputNode (Data Sinks)
│   ├── outputData(data): Unit
│   └── Implementations:
│       ├── UdpStreamOutputNode
│       │   ├── Packetization (1400 byte max)
│       │   ├── Sequencing & headers
│       │   ├── Error recovery
│       │   └── Statistics tracking
│       └── [Future: FileOutputNode, WebRTCOutputNode]
│
└── ProcessingNode (Transformations)
    ├── [Future: FilterNode, EncoderNode, AnalyzerNode]
    └── ML Integration:
        └── WekaMLProcessorNode
            ├── Feature extraction
            ├── Model inference
            └── Result packaging
```

### **MediaData Type System**
```
MediaData (Sealed Class)
├── AudioFrame
│   ├── buffer: ByteBuffer
│   ├── sampleRate: Int
│   ├── channels: Int
│   └── timestamp: Long
├── VideoFrame
│   ├── buffer: ByteBuffer
│   ├── width: Int
│   ├── height: Int
│   ├── format: Int
│   └── timestamp: Long
└── EncodedData
    ├── buffer: ByteBuffer
    ├── codecType: String
    └── timestamp: Long
```

## **3. Audio Processing System Components**

### **Compose-Level Audio Integration**
```
ProcessingNode (@Composable)
├── Lifecycle Management
│   ├── LaunchedEffect → processor.initialize()
│   ├── DisposableEffect → processor.cleanup()
│   └── State monitoring (IDLE, INITIALIZING, READY, ERROR)
├── Buffer Management
│   ├── inputBuffers: Map<Int, ByteBuffer>
│   ├── outputBuffers: Map<Int, ByteBuffer>
│   └── Direct allocation for JNI
└── Reactive State
    ├── nodeState: ProcessingNodeState
    ├── isProcessing: Boolean
    └── onStateChange callbacks
```

### **Audio Source Implementations**
```
NodeProcessor Concrete Types:

SineGeneratorProcessor
├── AudioProcessor backend (shared)
├── Background processing thread
├── Stream control (isStreamEnabled)
├── Frequency parameters (440Hz, custom)
├── OSC Integration
│   ├── updateOSCDestination(host, port)
│   ├── setOSCAddress(channel)
│   └── Native message formatting
└── Thread Safety
    ├── cleanupLock synchronization
    ├── isCleaningUp flag
    └── Proper shutdown sequence

MicrophoneProcessor
├── AudioRecord management
├── Real-time capture thread
├── Audio processing pipeline
│   ├── Gain control (0.1x - 5.0x)
│   ├── Noise reduction
│   └── Clipping prevention
├── OSC Integration (same as Sine)
└── Thread Safety (same pattern)
```

### **Parameter System**
```
Processor Parameters Map<String, Any>:
├── Audio Configuration
│   ├── "gain" → Float (0.1 - 5.0)
│   ├── "enableNoiseReduction" → Boolean
│   └── "streamEnabled" → Boolean
├── OSC Configuration
│   ├── "oscHost" → String ("127.0.0.1")
│   ├── "oscPort" → Int (8000)
│   └── "oscAddress" → String ("/chan1/audio")
└── Real-time Updates
    ├── updateParameter(name, value)
    ├── Synchronized application
    └── UI reactivity
```

## **4. Camera System Integration Points**

### **Camera Management Hierarchy**
```
CameraManager (Main Orchestrator)
├── CameraX Integration
│   ├── Preview surface
│   ├── ImageCapture surface
│   ├── VideoCapture surface
│   └── ImageAnalysis surface
├── Multi-Surface Coordination
│   ├── Surface state management (StateFlow)
│   │   ├── isSnapshotEnabled
│   │   ├── isVideoEnabled
│   │   ├── isAnalysisEnabled
│   │   ├── isAudioEnabled
│   │   └── isGLEnabled
│   ├── Surface lifecycle sync
│   └── Resource sharing optimization
├── Visual Feedback System
│   ├── snapshotFeedback: StateFlow<Boolean>
│   ├── recordingIndicator: StateFlow<Boolean>
│   └── Brief visual cues (no toasts)
└── AGSL Shader Integration
    ├── OpenGL effects overlay
    ├── Frame rotation controls
    └── Real-time video processing
```

### **Frame Processing Pipeline**
```
Camera Frame Flow:
├── PreviewView (Display)
├── ImageCapture → Snapshot files
├── VideoCapture → MP4 files + Audio
├── ImageAnalysis → FrameProcessor
│   ├── Frame analysis
│   ├── ML integration hooks
│   └── Real-time feedback
└── AGSL Shader Effects
    ├── Fragment shader processing
    ├── Rotation transformations
    └── Visual effects overlay
```

### **Audio-Video Coordination**
```
Synchronized A/V Recording:
├── CameraManager.enableAudio()
│   ├── Links to audio processing system
│   ├── Coordinates with video recording
│   └── Maintains sync timing
├── Recording State Management
│   ├── isRecording: StateFlow<Boolean>
│   ├── Audio processor stream control
│   └── Visual feedback coordination
└── OSC Integration Potential
    ├── Video frame metadata → OSC
    ├── Audio analysis → OSC
    ├── Camera parameters → OSC
    └── Real-time streaming hooks
```

## **5. OSC Communication Layers & Protocols**

### **OSC Protocol Stack**
```
OSC Communication Architecture:

Level 1: Native OSC Engine (C++)
├── OSC Message Formatting
│   ├── Address patterns ("/chan1/audio", "/chan2/video")
│   ├── Type tags (floats, ints, strings, blobs)
│   ├── Data serialization (network byte order)
│   └── Bundle support (synchronized messages)
├── UDP Transport Layer
│   ├── Socket management
│   ├── Packet fragmentation/reassembly
│   ├── Error handling & retry logic
│   └── Network interface selection
└── Buffer Management
    ├── Lock-free ring buffers
    ├── Zero-copy when possible
    └── Memory pool allocation

Level 2: JNI Bridge (Kotlin ↔ C++)
├── AudioProcessor.updateOSCDestination(host, port)
├── AudioProcessor.setOSCAddress(address)
├── AudioProcessor.processAudio() → Auto-OSC streaming
└── Native resource lifecycle management

Level 3: Application Integration (Kotlin)
├── Parameter-driven OSC control
├── UI → OSC configuration
├── Real-time streaming toggle
└── Connection management
```

### **OSC Message Types & Routing**
```
Current OSC Message Flows:

Audio Messages:
├── Address: "/chan1/audio" (configurable)
├── Data: Float32 audio samples (32-bit float array)
├── Rate: Real-time streaming (44.1kHz sample rate)
├── Transport: UDP packets to configured host:port
└── Format: Proper OSC blob messages

Text/Control Messages:
├── Address: "/channel/control"
├── Data: String messages
├── Use Case: Status updates, commands
├── Implementation: Simple UDP text (AudioDemo.kt:2067-2079)
└── Future: Full OSC control message support

Future Message Types:
├── Video Frames: "/chan1/video"
├── Camera Parameters: "/camera/settings"
├── Analysis Results: "/analysis/features"
├── ML Inference: "/ml/predictions"
└── System Status: "/system/health"
```

### **Device-Level vs Process-Level Communication**
```
Inter-Device Communication:
├── WiFi/Ethernet UDP transport
├── Configurable host:port destinations
├── Standard OSC protocol compliance
├── Audio streaming (current)
├── Future: Video, control, telemetry
└── Network discovery potential

Intra-Device Communication:
├── Loopback interface (127.0.0.1)
├── Different ports per service
├── Component coordination
├── Local processing chains
├── Debug/monitoring interfaces
└── Development testing support

Protocol Unification:
├── Same OSC message format for both
├── Routing based on destination config
├── Transparent local vs remote
├── Scalable architecture
└── Plugin system potential
```

## **6. UI Integration & State Management**

### **Compose UI Architecture**
```
UI State Management Hierarchy:

CameraScreen (Main Orchestrator)
├── PermissionHandler → Camera & Audio permissions
├── CameraPreview
│   ├── AndroidView(PreviewView) → Camera display
│   ├── MeteringInfoOverlay → Camera info display
│   ├── CameraToolbar → Camera controls
│   ├── SurfaceToolbar → Multi-surface controls
│   └── AGSLCameraOverlay → Shader effects
├── AudioDemoModal → Audio processing UI
└── State Coordination
    ├── remember cameraManager
    ├── collectAsState() reactive updates
    └── LaunchedEffect lifecycle hooks

State Flow Architecture:
├── CameraManager StateFlows
│   ├── isSnapshotEnabled
│   ├── isVideoEnabled
│   ├── isAnalysisEnabled
│   ├── isAudioEnabled
│   ├── isGLEnabled
│   ├── isRecording
│   ├── snapshotFeedback
│   └── recordingIndicator
└── ProcessingNode States
    ├── nodeState (IDLE/INITIALIZING/READY/ERROR)
    ├── isProcessing
    └── parameter values
```

### **Audio UI Component Hierarchy**
```
AudioDemoModal
├── AudioSourceConfigCard
│   ├── Source Selection Dropdown
│   │   ├── SINE_WAVE_440HZ → "440Hz Demo Tone"
│   │   ├── MICROPHONE → "Microphone Input"
│   │   └── SINE_WAVE_CUSTOM → "Custom Frequency"
│   ├── Context-Sensitive Controls
│   │   ├── MicrophoneControls (gain slider, noise reduction)
│   │   ├── CustomSineWaveControls (frequency slider, presets)
│   │   └── SineWaveDisplay (frequency info)
│   └── Stream Toggle Button
├── OSC Configuration (SinkTab)
│   ├── Host/Port settings
│   ├── Channel address config
│   ├── Connection testing
│   └── Stream status display
└── Real-time Parameter Updates
    ├── processor.updateParameter() calls
    ├── Immediate effect application
    ├── UI state synchronization
    └── Visual feedback integration
```

### **Control Flow & Parameter Propagation**
```
UI → Processor Communication:

Parameter Update Flow:
├── UI Component (Slider, Toggle, Dropdown)
├── State Update (remember/mutableStateOf)
├── LaunchedEffect(dependency) trigger
├── processor.updateParameter(name, value)
├── Synchronized application (with cleanup checks)
├── Native backend update (AudioProcessor JNI)
└── Real-time effect (audio stream changes)

Visual Feedback Flow:
├── Action Trigger (snapshot, video toggle)
├── CameraManager operation
├── StateFlow emission (snapshotFeedback, recordingIndicator)
├── UI collectAsState() update
├── Compose recomposition
├── Visual indication (color flash, indicator circle)
└── Auto-reset after brief duration

Stream Control Coordination:
├── Audio Source Selection → InputTab
├── Stream Toggle → Respects selected source
├── No redundant controls → Single source of truth
├── Parameter updates only → No manual cleanup
└── ProcessingNode manages lifecycle
```

## **7. Extension Points & Future Architecture**

### **OSC-Unified Extension Points**
```
All Components → OSC Interface:

Current OSC-Enabled:
├── Audio Processing
│   ├── Real-time audio streams
│   ├── Parameter control
│   └── Status reporting

Future OSC Extensions:
├── Camera System
│   ├── Frame metadata → "/camera/frame"
│   ├── Recording status → "/camera/recording"
│   ├── Settings control → "/camera/settings"
│   └── Visual analysis → "/camera/analysis"
├── MediaPipeline Integration
│   ├── Node status → "/pipeline/nodes"
│   ├── Data flow metrics → "/pipeline/metrics"
│   ├── ML results → "/pipeline/ml"
│   └── Error reporting → "/pipeline/errors"
├── System Telemetry
│   ├── Performance → "/system/performance"
│   ├── Resource usage → "/system/resources"
│   ├── Network status → "/system/network"
│   └── Health checks → "/system/health"
└── Inter-App Communication
    ├── App coordination → "/apps/coordination"
    ├── Shared resources → "/apps/resources"
    ├── Event distribution → "/apps/events"
    └── Service discovery → "/apps/discovery"
```

### **Abstraction Level Summary**
```
Architecture Levels (Low → High):

Level 0: Native C++
├── OSC Protocol Engine
├── Audio Processing DSP
├── Network Transport
└── Memory Management

Level 1: JNI Bridge
├── AudioProcessor (Kotlin ↔ C++)
├── Resource lifecycle management
├── Thread safety coordination
└── Native exception handling

Level 2: Processor Abstractions
├── NodeProcessor interface
├── ProcessingNode Composable
├── Parameter system
└── Lifecycle automation

Level 3: Media Pipeline
├── MediaNode hierarchy
├── Graph-based processing
├── MediaData type system
└── Pipeline orchestration

Level 4: Application Integration
├── CameraManager coordination
├── UI state management
├── Permission handling
└── User experience features

Level 5: OSC Unification
├── All components → OSC interface
├── Local & remote communication
├── Extensible message routing
└── Plugin architecture potential
```

---

## **Summary: OSC-Centered Architecture**

The PipCamera architecture is built around **OSC (Open Sound Control) as the universal communication protocol**, providing unified interfaces across all abstraction levels:

**Core Principle**: *Everything that can generate, process, or consume data can communicate through OSC messages, whether locally within the device or remotely between devices.*

**Key Strengths**:
- **Unified Protocol**: Audio, video, control, telemetry all use OSC
- **Scalable Design**: Easy to add new OSC-enabled components
- **Local & Remote**: Same interface for intra-device and inter-device communication
- **Real-time Capable**: Native C++ backend for low-latency processing
- **Plugin Architecture**: NodeProcessor interface allows easy extension
- **Reactive UI**: StateFlow-based UI automatically reflects system state

**Extension Strategy**: Any new feature (ML analysis, video streaming, sensor data, etc.) can be added by implementing the appropriate interface (NodeProcessor, MediaNode, etc.) and automatically gains OSC communication capabilities through the existing infrastructure.