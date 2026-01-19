package com.elegia.pipcamera.pipeline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Hardware-accelerated media processing pipeline framework
 * Supports real-time audio/video processing with ML transformations
 */
class MediaPipeline(private val context: Context) {
    companion object {
        private const val TAG = "MediaPipeline"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val nodes = ConcurrentHashMap<String, MediaNode>()
    private val connections = mutableMapOf<String, MutableSet<String>>()
    private var isRunning = false

    /**
     * Add a processing node to the pipeline
     */
    fun addNode(nodeId: String, node: MediaNode): MediaPipeline {
        nodes[nodeId] = node
        Log.i(TAG, "Added node: $nodeId (${node::class.simpleName})")
        return this
    }

    /**
     * Connect two nodes in the processing graph
     */
    fun connect(fromNodeId: String, toNodeId: String): MediaPipeline {
        connections.getOrPut(fromNodeId) { mutableSetOf() }.add(toNodeId)
        Log.i(TAG, "Connected: $fromNodeId -> $toNodeId")
        return this
    }

    /**
     * Start the media processing pipeline
     */
    suspend fun start(): Boolean {
        if (isRunning) return true

        Log.i(TAG, "Starting media pipeline with ${nodes.size} nodes")
        isRunning = true

        try {
            // Initialize all nodes
            nodes.values.forEach { node ->
                if (!node.initialize(context)) {
                    Log.e(TAG, "Failed to initialize node: ${node.nodeId}")
                    return false
                }
            }

            // Start processing
            scope.launch {
                startProcessing()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start pipeline", e)
            isRunning = false
            return false
        }
    }

    /**
     * Stop the pipeline and cleanup resources
     */
    suspend fun stop(): Boolean {
        if (!isRunning) return true

        Log.i(TAG, "Stopping media pipeline")
        isRunning = false

        try {
            scope.cancel()
            nodes.values.forEach { node ->
                node.cleanup()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during pipeline stop", e)
            return false
        }
    }

    private suspend fun startProcessing() {
        while (isRunning) {
            // Process each frame through the pipeline
            processFrame()
            delay(1) // Minimal delay for cooperative multitasking
        }
    }

    private suspend fun processFrame() {
        // Find input nodes and start processing
        val inputNodes = nodes.values.filterIsInstance<InputNode>()

        for (inputNode in inputNodes) {
            val data = inputNode.captureData()
            if (data != null) {
                propagateData(inputNode.nodeId, data)
            }
        }
    }

    private suspend fun propagateData(fromNodeId: String, data: MediaData) {
        val connectedNodes = connections[fromNodeId] ?: return

        for (toNodeId in connectedNodes) {
            val toNode = nodes[toNodeId]
            if (toNode != null) {
                val processedData = toNode.process(data)
                if (processedData != null) {
                    propagateData(toNodeId, processedData)
                }
            }
        }
    }
}

/**
 * Base class for all media processing nodes
 */
abstract class MediaNode(val nodeId: String) {
    abstract suspend fun initialize(context: Context): Boolean
    abstract suspend fun process(input: MediaData): MediaData?
    abstract suspend fun cleanup()
}

/**
 * Base class for input nodes (microphone, camera, etc.)
 */
abstract class InputNode(nodeId: String) : MediaNode(nodeId) {
    abstract suspend fun captureData(): MediaData?
}

/**
 * Base class for output nodes (UDP stream, file, etc.)
 */
abstract class OutputNode(nodeId: String) : MediaNode(nodeId) {
    override suspend fun process(input: MediaData): MediaData? {
        // Output nodes consume data but don't produce output
        outputData(input)
        return null
    }

    abstract suspend fun outputData(data: MediaData)
}

/**
 * Media data container with type information
 */
sealed class MediaData {
    data class AudioFrame(
        val buffer: ByteBuffer,
        val sampleRate: Int,
        val channels: Int,
        val timestamp: Long = System.nanoTime()
    ) : MediaData()

    data class VideoFrame(
        val buffer: ByteBuffer,
        val width: Int,
        val height: Int,
        val format: Int,
        val timestamp: Long = System.nanoTime()
    ) : MediaData()

    data class EncodedData(
        val buffer: ByteBuffer,
        val codecType: String,
        val timestamp: Long = System.nanoTime()
    ) : MediaData()
}