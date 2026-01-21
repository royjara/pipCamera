package com.elegia.pipcamera.audio

import androidx.compose.runtime.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Generic interface for audio processing nodes
 * Provides black box abstraction for plugin architecture
 */
interface NodeProcessor {
    /**
     * Process audio data
     * @param inputs Map of inlet index to audio buffer
     * @param outputs Map of outlet index to audio buffer
     * @param frameCount Number of frames to process
     */
    suspend fun process(
        inputs: Map<Int, ByteBuffer>,
        outputs: Map<Int, ByteBuffer>,
        frameCount: Int
    )

    /**
     * Initialize the processor with given configuration
     */
    suspend fun initialize(config: ProcessingNodeConfig): Boolean

    /**
     * Cleanup resources
     */
    suspend fun cleanup()

    /**
     * Get processor-specific parameters
     */
    fun getParameters(): Map<String, Any>

    /**
     * Update processor parameters
     */
    fun updateParameter(name: String, value: Any)
}

/**
 * Configuration for processing nodes
 */
data class ProcessingNodeConfig(
    val sampleRate: Int = 44100,
    val bufferSize: Int = 512,
    val inletCount: Int = 0,
    val outletCount: Int = 1,
    val parameters: Map<String, Any> = emptyMap()
)

/**
 * Generic Composable for audio processing nodes
 * Provides lifecycle management and reactive state
 */
@Composable
fun ProcessingNode(
    inletCount: Int,
    outletCount: Int,
    processor: NodeProcessor,
    config: ProcessingNodeConfig = ProcessingNodeConfig(
        inletCount = inletCount,
        outletCount = outletCount
    ),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onStateChange: (ProcessingNodeState) -> Unit = {}
) {
    // State management
    var nodeState by remember { mutableStateOf(ProcessingNodeState.IDLE) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Audio buffers
    val inputBuffers = remember {
        (0 until inletCount).associateWith {
            ByteBuffer.allocateDirect(config.bufferSize * 4)
        }
    }
    val outputBuffers = remember {
        (0 until outletCount).associateWith {
            ByteBuffer.allocateDirect(config.bufferSize * 4)
        }
    }

    // Initialize processor when composition enters
    LaunchedEffect(processor) {
        try {
            nodeState = ProcessingNodeState.INITIALIZING
            onStateChange(nodeState)

            val initialized = processor.initialize(config)

            nodeState = if (initialized) {
                ProcessingNodeState.READY
            } else {
                ProcessingNodeState.ERROR
            }
            onStateChange(nodeState)

            Log.i("ProcessingNode", "Node initialized: $nodeState")

        } catch (e: Exception) {
            Log.e("ProcessingNode", "Failed to initialize processor: ${e.message}", e)
            nodeState = ProcessingNodeState.ERROR
            onStateChange(nodeState)
        }
    }

    // Cleanup when composition exits
    DisposableEffect(lifecycleOwner) {
        onDispose {
            scope.launch {
                try {
                    nodeState = ProcessingNodeState.CLEANUP
                    onStateChange(nodeState)

                    processor.cleanup()

                    nodeState = ProcessingNodeState.IDLE
                    onStateChange(nodeState)

                    Log.i("ProcessingNode", "Node cleanup completed")
                } catch (e: Exception) {
                    Log.e("ProcessingNode", "Error during cleanup", e)
                }
            }
        }
    }

    // Monitor processing state (processor handles its own processing loop)
    LaunchedEffect(nodeState) {
        if (nodeState == ProcessingNodeState.READY) {
            Log.i("ProcessingNode", "Node ready - processor is handling audio processing")

            // Periodically update state to show processing activity
            while (nodeState == ProcessingNodeState.READY) {
                onStateChange(ProcessingNodeState.PROCESSING)
                kotlinx.coroutines.delay(100)
                onStateChange(ProcessingNodeState.READY)
                kotlinx.coroutines.delay(400)
            }
        }
    }

    // Stop processing when component is cleaned up
    DisposableEffect(lifecycleOwner) {
        onDispose {
            isProcessing = false
        }
    }
}

/**
 * State of a processing node
 */
enum class ProcessingNodeState {
    IDLE,
    INITIALIZING,
    READY,
    PROCESSING,
    ERROR,
    CLEANUP
}

/**
 * Simple sine wave generator node processor
 */
class SineGeneratorProcessor : NodeProcessor {
    private val audioProcessor = AudioProcessor()
    private var isInitialized = false
    private var processingThread: Thread? = null
    private var isProcessingActive = false
    private var isStreamEnabled = false

    // Synchronization for thread safety
    private val cleanupLock = Any()
    private var isCleaningUp = false

    companion object {
        // C Major scale frequencies (C4 to C5)
        val C_MAJOR_FREQUENCIES = listOf(
            261.63f, // C4
            293.66f, // D4
            329.63f, // E4
            349.23f, // F4
            392.00f, // G4
            440.00f, // A4 (440Hz standard)
            493.88f, // B4
            523.25f  // C5
        )

        val NOTE_NAMES = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")
    }

    // Mutable parameters that can be updated
    private val parameters = mutableMapOf<String, Any>(
        "frequency" to 440.0f, // Start at A4
        "frequencyIndex" to 5,  // Index for A4 in scale
        "amplitude" to 0.5f,
        "oscHost" to "127.0.0.1",
        "oscPort" to 8000,
        "oscAddress" to "/audio/stream"
    )

    override suspend fun initialize(config: ProcessingNodeConfig): Boolean {
        Log.i("SineGeneratorProcessor", "Starting initialization...")

        try {
            isInitialized = audioProcessor.initialize(
                config.sampleRate,
                config.bufferSize,
                config.inletCount,
                config.outletCount
            )

            if (!isInitialized) {
                Log.e("SineGeneratorProcessor", "AudioProcessor initialization failed")
                return false
            }

            // Set initial OSC configuration
            val host = parameters["oscHost"] as String
            val port = parameters["oscPort"] as Int
            val address = parameters["oscAddress"] as String

            Log.i("SineGeneratorProcessor", "Setting OSC config: $host:$port $address")

            audioProcessor.updateOSCDestination(host, port)
            audioProcessor.setOSCAddress(address)

            Log.i("SineGeneratorProcessor", "Initialization completed successfully")

            // Start processing thread
            startProcessing()

        } catch (e: Exception) {
            Log.e("SineGeneratorProcessor", "Exception during initialization", e)
            isInitialized = false
        }

        return isInitialized
    }

    override suspend fun process(
        inputs: Map<Int, ByteBuffer>,
        outputs: Map<Int, ByteBuffer>,
        frameCount: Int
    ) {
        if (!isInitialized) {
            Log.w("SineGeneratorProcessor", "Process called but not initialized")
            return
        }

        if (outputs.isEmpty()) {
            Log.w("SineGeneratorProcessor", "No output buffers provided")
            return
        }

        try {
            // Generate sine wave into first output buffer
            outputs[0]?.let { outputBuffer ->
                audioProcessor.processAudio(
                    inputBuffer = null,
                    outputBuffer = outputBuffer,
                    frameCount = frameCount
                )
            }
        } catch (e: Exception) {
            Log.e("SineGeneratorProcessor", "Exception during process", e)
            throw e // Re-throw to trigger ERROR state in ProcessingNode
        }
    }

    override suspend fun cleanup() {
        Log.d("SineGeneratorProcessor", "Cleaning up sine generator processor")

        synchronized(cleanupLock) {
            if (isCleaningUp) {
                Log.w("SineGeneratorProcessor", "Cleanup already in progress")
                return
            }
            isCleaningUp = true
        }

        try {
            // First stop processing
            stopProcessing()

            // Wait a bit for threads to actually stop
            Thread.sleep(50)

            // Finally clean up native resources
            synchronized(cleanupLock) {
                try {
                    if (isInitialized) {
                        audioProcessor.shutdown()
                        isInitialized = false
                        Log.i("SineGeneratorProcessor", "AudioProcessor shutdown completed")
                    }
                } catch (e: Exception) {
                    Log.e("SineGeneratorProcessor", "Error cleaning up audio processor", e)
                }
            }
        } finally {
            synchronized(cleanupLock) {
                isCleaningUp = false
            }
        }
    }

    private fun startProcessing() {
        if (isProcessingActive) return

        isProcessingActive = true
        processingThread = Thread {
            Log.i("SineGeneratorProcessor", "Processing thread started")

            // Create dummy buffers for processing
            val outputBuffer = java.nio.ByteBuffer.allocateDirect(512 * 4) // 512 floats

            while (isProcessingActive && isInitialized) {
                try {
                    // Check if cleanup is happening - if so, exit immediately
                    val shouldExit = synchronized(cleanupLock) {
                        if (isCleaningUp) {
                            Log.i("SineGeneratorProcessor", "Cleanup in progress, stopping processing thread")
                            true
                        } else false
                    }
                    if (shouldExit) break

                    // Only generate and send audio when streaming is enabled
                    if (isStreamEnabled) {
                        // Check again before native call - most critical section
                        val shouldSkip = synchronized(cleanupLock) {
                            if (isCleaningUp || !isInitialized) {
                                Log.i("SineGeneratorProcessor", "Cleanup started, skipping audio processing")
                                true
                            } else {
                                audioProcessor.processAudio(
                                    inputBuffer = null,
                                    outputBuffer = outputBuffer,
                                    frameCount = 512
                                )
                                false
                            }
                        }
                        if (shouldSkip) break
                    }

                    // Sleep for ~10ms (slightly faster to prevent buffer underruns)
                    Thread.sleep(10)

                } catch (e: InterruptedException) {
                    Log.i("SineGeneratorProcessor", "Processing thread interrupted")
                    break
                } catch (e: Exception) {
                    Log.e("SineGeneratorProcessor", "Error in processing thread: ${e.message}", e)
                    break
                }
            }

            Log.i("SineGeneratorProcessor", "Processing thread stopped")
        }

        processingThread?.start()
    }

    private fun stopProcessing() {
        isProcessingActive = false
        processingThread?.interrupt()
        processingThread?.join(1000) // Wait up to 1 second
        processingThread = null
    }

    override fun getParameters(): Map<String, Any> {
        return parameters.toMap() + mapOf(
            "sampleRate" to audioProcessor.getSampleRate(),
            "bufferSize" to audioProcessor.getBufferSize()
        )
    }

    override fun updateParameter(name: String, value: Any) {
        // Update internal parameter storage
        parameters[name] = value

        // Apply changes to native processor if initialized
        if (isInitialized) {
            when (name) {
                "oscHost" -> {
                    val port = parameters["oscPort"] as Int
                    audioProcessor.updateOSCDestination(value.toString(), port)
                }
                "oscPort" -> {
                    val host = parameters["oscHost"] as String
                    audioProcessor.updateOSCDestination(host, value as Int)
                }
                "oscAddress" -> {
                    audioProcessor.setOSCAddress(value.toString())
                }
                "streamEnabled" -> {
                    isStreamEnabled = value as Boolean
                    Log.i("SineGeneratorProcessor", "Stream enabled: $isStreamEnabled")
                }
                "frequencyIndex" -> {
                    val index = value as Int
                    if (index in 0 until C_MAJOR_FREQUENCIES.size) {
                        val frequency = C_MAJOR_FREQUENCIES[index]
                        parameters["frequency"] = frequency
                        parameters["frequencyIndex"] = index

                        // Update native frequency
                        audioProcessor.setFrequency(frequency)
                        Log.i("SineGeneratorProcessor", "Frequency changed to: $frequency Hz (${NOTE_NAMES[index]})")
                    }
                }
            }
        }
    }
}