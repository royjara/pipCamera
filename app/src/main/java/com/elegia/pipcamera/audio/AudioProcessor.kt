package com.elegia.pipcamera.audio

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native audio processor for high-performance audio pipeline
 * Interfaces with C++ audio processing backend
 */
class AudioProcessor {
    companion object {
        private const val TAG = "AudioProcessor"

        init {
            try {
                System.loadLibrary("audio_pipeline")
                Log.i(TAG, "Native audio pipeline library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native audio pipeline library", e)
            }
        }
    }

    private var isInitialized = false
    private var sampleRate = 44100
    private var bufferSize = 512
    private var inletCount = 0
    private var outletCount = 1

    /**
     * Initialize the native audio processing pipeline
     * @param sampleRate Audio sample rate (e.g., 44100, 48000)
     * @param bufferSize Buffer size in frames
     * @param inletCount Number of input channels
     * @param outletCount Number of output channels
     */
    fun initialize(
        sampleRate: Int = 44100,
        bufferSize: Int = 512,
        inletCount: Int = 0,
        outletCount: Int = 1
    ): Boolean {
        if (isInitialized) {
            Log.w(TAG, "Audio processor already initialized")
            return true
        }

        Log.i(TAG, "Initializing audio processor: sr=$sampleRate, buffer=$bufferSize, in=$inletCount, out=$outletCount")

        this.sampleRate = sampleRate
        this.bufferSize = bufferSize
        this.inletCount = inletCount
        this.outletCount = outletCount

        isInitialized = nativeInitialize(sampleRate, bufferSize, inletCount, outletCount)

        if (isInitialized) {
            Log.i(TAG, "Audio processor initialized successfully")
        } else {
            Log.e(TAG, "Failed to initialize audio processor")
        }

        return isInitialized
    }

    /**
     * Process audio through the native pipeline
     * @param inputBuffer Optional input audio buffer
     * @param outputBuffer Output audio buffer (will be filled with processed audio)
     * @param frameCount Number of frames to process
     */
    fun processAudio(
        inputBuffer: ByteBuffer? = null,
        outputBuffer: ByteBuffer,
        frameCount: Int = bufferSize
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Audio processor not initialized")
            return
        }

        nativeProcessAudio(inputBuffer, outputBuffer, frameCount)
    }

    /**
     * Update OSC destination for audio streaming
     * @param host Target host address
     * @param port Target port number
     */
    fun updateOSCDestination(host: String, port: Int) {
        if (!isInitialized) {
            Log.w(TAG, "Audio processor not initialized")
            return
        }

        Log.i(TAG, "Updating OSC destination: $host:$port")
        nativeUpdateOSCDestination(host, port)
    }

    /**
     * Create a direct ByteBuffer for efficient native access
     * @param sizeInFloats Buffer size in float elements
     */
    fun createAudioBuffer(sizeInFloats: Int = bufferSize): ByteBuffer {
        return ByteBuffer.allocateDirect(sizeInFloats * 4) // 4 bytes per float
            .order(ByteOrder.nativeOrder())
    }

    /**
     * Shutdown the audio processor and cleanup native resources
     */
    fun shutdown() {
        if (!isInitialized) {
            return
        }

        Log.i(TAG, "Shutting down audio processor")
        nativeShutdown()
        isInitialized = false
    }

    // Getters for configuration
    fun getSampleRate() = sampleRate
    fun getBufferSize() = bufferSize
    fun getInletCount() = inletCount
    fun getOutletCount() = outletCount
    fun isReady() = isInitialized

    // Native method declarations
    private external fun nativeInitialize(
        sampleRate: Int,
        bufferSize: Int,
        inletCount: Int,
        outletCount: Int
    ): Boolean

    private external fun nativeProcessAudio(
        inputBuffer: ByteBuffer?,
        outputBuffer: ByteBuffer?,
        frameCount: Int
    )

    private external fun nativeShutdown()

    private external fun nativeUpdateOSCDestination(host: String, port: Int)
}