package com.elegia.pipcamera.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Microphone input processor with real-time OSC streaming
 * Uses the same AudioProcessor backend as SineGeneratorProcessor for proper OSC formatting
 */
class MicrophoneProcessor : NodeProcessor {
    companion object {
        private const val TAG = "MicrophoneProcessor"
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    // Use the same AudioProcessor backend as SineGeneratorProcessor
    private val audioProcessor = AudioProcessor()
    private var isInitialized = false
    private var isProcessingActive = false
    private var processingThread: Thread? = null

    // Synchronization for thread safety
    private val cleanupLock = Any()
    private var isCleaningUp = false

    // Audio configuration
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(512)

    // Processing parameters - store them until initialization
    private val parameters = mutableMapOf<String, Any>(
        "gain" to 1.0f,
        "enableNoiseReduction" to false,
        "streamEnabled" to false,
        "oscHost" to "127.0.0.1",
        "oscPort" to 8000,
        "oscAddress" to "/chan1/audio"
    )

    override suspend fun initialize(config: ProcessingNodeConfig): Boolean {
        Log.d(TAG, "Initializing microphone processor")

        try {
            // Initialize the native AudioProcessor first
            if (!audioProcessor.initialize(
                    sampleRate = config.sampleRate,
                    bufferSize = config.bufferSize,
                    inletCount = 1,  // 1 input for microphone
                    outletCount = 1   // 1 output for processed audio
                )) {
                Log.e(TAG, "Failed to initialize native audio processor")
                return false
            }

            // Configure OSC settings
            val host = parameters["oscHost"] as String
            val port = parameters["oscPort"] as Int
            val address = parameters["oscAddress"] as String

            Log.i(TAG, "Setting OSC config: $host:$port $address")

            audioProcessor.updateOSCDestination(host, port)
            audioProcessor.setOSCAddress(address)

            // Note: Permission check should be handled by the app before using this processor
            Log.d(TAG, "Initializing microphone - ensure RECORD_AUDIO permission is granted")

            // Initialize AudioRecord
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize * 2 // Double buffer for safety
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    return false
                }

                isInitialized = true
                Log.i(TAG, "Microphone processor initialization completed successfully")

                // Start processing thread
                startProcessing()

            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during AudioRecord initialization", e)
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Exception during AudioRecord initialization", e)
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
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
            Log.w(TAG, "Process called but not initialized")
            return
        }

        if (outputs.isEmpty()) {
            Log.w(TAG, "No output buffers provided")
            return
        }

        try {
            // The actual processing happens in the background thread
            // This method can be used for additional real-time processing if needed
            outputs[0]?.let { outputBuffer ->
                // For now, just clear the output buffer
                // The real processing happens in the background thread
                outputBuffer.clear()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during process", e)
            throw e
        }
    }

    private fun startProcessing() {
        if (isProcessingActive) {
            Log.w(TAG, "Processing already active")
            return
        }

        Log.i(TAG, "Starting microphone processing thread")
        isProcessingActive = true

        processingThread = Thread {
            val buffer = ShortArray(bufferSize)
            val outputBuffer = ByteBuffer.allocateDirect(bufferSize * 4) // 4 bytes per float
                .order(ByteOrder.nativeOrder())

            while (isProcessingActive && !Thread.currentThread().isInterrupted) {
                try {
                    // Check if cleanup is happening - if so, exit immediately
                    val shouldExit = synchronized(cleanupLock) {
                        if (isCleaningUp) {
                            Log.i(TAG, "Cleanup in progress, stopping processing thread")
                            true
                        } else false
                    }
                    if (shouldExit) break

                    if (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val samplesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                        if (samplesRead > 0) {
                            // Convert to float and apply gain
                            outputBuffer.clear()
                            val gain = parameters["gain"] as Float
                            val enableNoiseReduction = parameters["enableNoiseReduction"] as Boolean

                            for (i in 0 until samplesRead) {
                                var sample = buffer[i].toFloat() / Short.MAX_VALUE

                                // Apply gain
                                sample *= gain

                                // Apply basic noise reduction if enabled
                                if (enableNoiseReduction && kotlin.math.abs(sample) < 0.01f) {
                                    sample *= 0.1f // Reduce by 90%
                                }

                                // Prevent clipping
                                sample = sample.coerceIn(-1f, 1f)

                                outputBuffer.putFloat(sample)
                            }

                            outputBuffer.flip()

                            // Check again before native call - most critical section
                            val shouldSkip = synchronized(cleanupLock) {
                                if (isCleaningUp || !isInitialized) {
                                    Log.i(TAG, "Cleanup started, skipping audio processing")
                                    true
                                } else {
                                    // Process through AudioProcessor for proper OSC formatting
                                    val dummyOutputBuffer = ByteBuffer.allocateDirect(samplesRead * 4).order(ByteOrder.nativeOrder())
                                    audioProcessor.processAudio(
                                        inputBuffer = outputBuffer,
                                        outputBuffer = dummyOutputBuffer, // AudioProcessor requires non-null output
                                        frameCount = samplesRead
                                    )
                                    false
                                }
                            }
                            if (shouldSkip) break
                        }
                    }

                    // Sleep for a short time to prevent excessive CPU usage
                    Thread.sleep(10)

                } catch (e: InterruptedException) {
                    Log.i(TAG, "Processing thread interrupted")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in processing thread: ${e.message}", e)
                    break
                }
            }

            Log.i(TAG, "Microphone processing thread stopped")
        }

        processingThread?.start()
    }

    private fun stopProcessing() {
        isProcessingActive = false
        processingThread?.interrupt()
        processingThread?.join(1000) // Wait up to 1 second
        processingThread = null
    }

    fun startRecording() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot start recording - not initialized")
            return
        }

        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        Log.d(TAG, "Starting microphone recording")

        try {
            audioRecord?.startRecording()
            isRecording = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            isRecording = false
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            return
        }

        Log.d(TAG, "Stopping microphone recording")
        isRecording = false

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
    }

    override suspend fun cleanup() {
        Log.d(TAG, "Cleaning up microphone processor")

        synchronized(cleanupLock) {
            if (isCleaningUp) {
                Log.w(TAG, "Cleanup already in progress")
                return
            }
            isCleaningUp = true
        }

        try {
            // First stop recording and processing
            stopRecording()
            stopProcessing()

            // Wait a bit for threads to actually stop
            Thread.sleep(50)

            // Clean up AudioRecord
            try {
                audioRecord?.release()
                audioRecord = null
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord", e)
            }

            // Finally clean up native resources
            synchronized(cleanupLock) {
                try {
                    if (isInitialized) {
                        audioProcessor.shutdown()
                        isInitialized = false
                        Log.i(TAG, "AudioProcessor shutdown completed")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up audio processor", e)
                }
            }
        } finally {
            synchronized(cleanupLock) {
                isCleaningUp = false
            }
        }
    }

    override fun getParameters(): Map<String, Any> {
        return parameters.toMap() + mapOf(
            "sampleRate" to sampleRate,
            "bufferSize" to bufferSize,
            "isRecording" to isRecording,
            "isInitialized" to isInitialized
        )
    }

    override fun updateParameter(name: String, value: Any) {
        // Update internal parameter storage
        parameters[name] = value

        // Apply changes immediately if initialized and not cleaning up
        synchronized(cleanupLock) {
            if (isInitialized && !isCleaningUp) {
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
                        val enabled = when (value) {
                            is Boolean -> value
                            is String -> value.toBoolean()
                            else -> false
                        }
                        if (enabled) {
                            startRecording()
                        } else {
                            stopRecording()
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Updated parameter $name = $value")
    }

    // Public methods for UI access
    fun getCurrentAudioLevel(): Float {
        // This could be implemented to return current RMS level
        return 0.0f
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}