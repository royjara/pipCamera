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
            Log.e("ProcessingNode", "Failed to initialize processor", e)
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

    // Expose processing function for external audio pipeline
    LaunchedEffect(nodeState) {
        if (nodeState == ProcessingNodeState.READY) {
            // Node is ready for processing
            // External audio pipeline can call processor.process() with buffers
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

    override suspend fun initialize(config: ProcessingNodeConfig): Boolean {
        isInitialized = audioProcessor.initialize(
            config.sampleRate,
            config.bufferSize,
            config.inletCount,
            config.outletCount
        )
        return isInitialized
    }

    override suspend fun process(
        inputs: Map<Int, ByteBuffer>,
        outputs: Map<Int, ByteBuffer>,
        frameCount: Int
    ) {
        if (!isInitialized || outputs.isEmpty()) {
            return
        }

        // Generate sine wave into first output buffer
        outputs[0]?.let { outputBuffer ->
            audioProcessor.processAudio(
                inputBuffer = null,
                outputBuffer = outputBuffer,
                frameCount = frameCount
            )
        }
    }

    override suspend fun cleanup() {
        audioProcessor.shutdown()
        isInitialized = false
    }

    override fun getParameters(): Map<String, Any> {
        return mapOf(
            "sampleRate" to audioProcessor.getSampleRate(),
            "bufferSize" to audioProcessor.getBufferSize(),
            "frequency" to 440.0f,
            "amplitude" to 0.5f,
            "oscHost" to "127.0.0.1",
            "oscPort" to 8000,
            "oscAddress" to "/audio/stream"
        )
    }

    override fun updateParameter(name: String, value: Any) {
        when (name) {
            "oscHost" -> {
                val port = getParameters()["oscPort"] as? Int ?: 8000
                audioProcessor.updateOSCDestination(value.toString(), port)
            }
            "oscPort" -> {
                val host = getParameters()["oscHost"] as? String ?: "127.0.0.1"
                audioProcessor.updateOSCDestination(host, value as Int)
            }
            "oscAddress" -> {
                audioProcessor.setOSCAddress(value.toString())
            }
        }
    }
}