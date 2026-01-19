package com.elegia.pipcamera.pipeline.examples

import android.content.Context
import android.util.Log
import com.elegia.pipcamera.pipeline.MediaPipeline
import com.elegia.pipcamera.pipeline.nodes.*

/**
 * Advanced audio processing pipeline example
 * Demonstrates: Microphone → ML Enhancement → Hardware Encoding → UDP Streaming
 */
class AdvancedAudioPipeline(private val context: Context) {

    companion object {
        private const val TAG = "AdvancedAudioPipeline"
    }

    private val pipeline = MediaPipeline(context)

    /**
     * Configure the complete audio processing pipeline
     */
    suspend fun configurePipeline(
        targetHost: String = "192.168.1.100",
        targetPort: Int = 8000,
        enableMLProcessing: Boolean = true,
        enableHardwareEncoding: Boolean = true
    ): Boolean {

        try {
            Log.i(TAG, "Configuring advanced audio pipeline")

            // 1. Microphone Input Node
            val microphoneNode = MicrophoneInputNode(
                nodeId = "microphone",
                sampleRate = 44100,
                channels = 1,  // Mono for better ML processing
                bufferSizeFrames = 1024
            )
            pipeline.addNode("microphone", microphoneNode)

            var lastNodeId = "microphone"

            // 2. Optional ML Processing Node
            if (enableMLProcessing) {
                val mlNode = WekaMLProcessorNode(
                    nodeId = "ml_processor",
                    modelType = WekaMLProcessorNode.MLModelType.AUDIO_ENHANCEMENT,
                    processingMode = WekaMLProcessorNode.ProcessingMode.REAL_TIME
                )
                pipeline.addNode("ml_processor", mlNode)
                pipeline.connect(lastNodeId, "ml_processor")
                lastNodeId = "ml_processor"
            }

            // 3. Optional Hardware Encoder Node
            if (enableHardwareEncoding) {
                val encoderNode = MediaCodecEncoderNode(
                    nodeId = "hardware_encoder",
                    codecType = MediaCodecEncoderNode.CODEC_AAC,
                    bitRate = 128000,
                    outputSampleRate = 44100
                )
                pipeline.addNode("hardware_encoder", encoderNode)
                pipeline.connect(lastNodeId, "hardware_encoder")
                lastNodeId = "hardware_encoder"
            }

            // 4. UDP Stream Output Node
            val udpNode = UdpStreamOutputNode(
                nodeId = "udp_stream",
                targetHost = targetHost,
                targetPort = targetPort,
                maxPacketSize = 1400,
                enableSequencing = true
            )
            pipeline.addNode("udp_stream", udpNode)
            pipeline.connect(lastNodeId, "udp_stream")

            // 5. Optional Raw Audio Output (for local monitoring)
            val rawUdpNode = UdpStreamOutputNode(
                nodeId = "raw_udp_stream",
                targetHost = targetHost,
                targetPort = targetPort + 1, // Use different port for raw audio
                maxPacketSize = 1400,
                enableSequencing = true
            )
            pipeline.addNode("raw_udp_stream", rawUdpNode)

            // Send both processed and raw audio
            if (enableMLProcessing) {
                pipeline.connect("ml_processor", "raw_udp_stream")
            } else {
                pipeline.connect("microphone", "raw_udp_stream")
            }

            Log.i(TAG, "Pipeline configured successfully")
            Log.i(TAG, "Configuration: ML=${enableMLProcessing}, HW_Encoding=${enableHardwareEncoding}")
            Log.i(TAG, "Target: ${targetHost}:${targetPort}")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure pipeline", e)
            return false
        }
    }

    /**
     * Start the audio processing pipeline
     */
    suspend fun start(): Boolean {
        return try {
            pipeline.start()
            Log.i(TAG, "Advanced audio pipeline started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start pipeline", e)
            false
        }
    }

    /**
     * Stop the pipeline and cleanup resources
     */
    suspend fun stop() {
        try {
            pipeline.stop()
            Log.i(TAG, "Advanced audio pipeline stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping pipeline", e)
        }
    }

    /**
     * Get real-time pipeline statistics
     */
    fun getPipelineStats(): Map<String, Any> {
        // In a real implementation, we would collect stats from all nodes
        return mapOf(
            "pipelineStatus" to "running",
            "processingNodes" to 4,
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Update streaming destination dynamically
     */
    fun updateStreamingDestination(host: String, port: Int) {
        // In a real implementation, we would access nodes and update their configuration
        Log.i(TAG, "Updated streaming destination: $host:$port")
    }
}

/**
 * Simple audio pipeline for basic use cases
 */
class SimpleAudioPipeline(private val context: Context) {

    private val pipeline = MediaPipeline(context)

    /**
     * Configure a basic microphone → UDP stream pipeline
     */
    suspend fun configureBasicPipeline(targetHost: String, targetPort: Int): Boolean {
        try {
            // Microphone input
            val microphoneNode = MicrophoneInputNode(
                nodeId = "microphone",
                sampleRate = 44100,
                channels = 2, // Stereo for better quality
                bufferSizeFrames = 512
            )
            pipeline.addNode("microphone", microphoneNode)

            // Direct UDP streaming (no encoding for minimal latency)
            val udpNode = UdpStreamOutputNode(
                nodeId = "udp_stream",
                targetHost = targetHost,
                targetPort = targetPort,
                maxPacketSize = 1400
            )
            pipeline.addNode("udp_stream", udpNode)

            // Connect microphone directly to UDP stream
            pipeline.connect("microphone", "udp_stream")

            return true

        } catch (e: Exception) {
            Log.e("SimpleAudioPipeline", "Configuration failed", e)
            return false
        }
    }

    suspend fun start(): Boolean = pipeline.start()
    suspend fun stop(): Boolean = pipeline.stop()
}

/**
 * ML-focused pipeline for audio research and experimentation
 */
class MLAudioResearchPipeline(private val context: Context) {

    private val pipeline = MediaPipeline(context)

    /**
     * Configure pipeline for ML audio research
     */
    suspend fun configureResearchPipeline(): Boolean {
        try {
            // High-quality microphone input
            val microphoneNode = MicrophoneInputNode(
                nodeId = "microphone",
                sampleRate = 48000, // Higher sample rate for research
                channels = 1,
                bufferSizeFrames = 2048 // Larger buffer for better ML processing
            )
            pipeline.addNode("microphone", microphoneNode)

            // Multiple ML processors in parallel
            val enhancementNode = WekaMLProcessorNode(
                nodeId = "enhancement",
                modelType = WekaMLProcessorNode.MLModelType.AUDIO_ENHANCEMENT,
                processingMode = WekaMLProcessorNode.ProcessingMode.HIGH_QUALITY
            )
            pipeline.addNode("enhancement", enhancementNode)

            val analysisNode = WekaMLProcessorNode(
                nodeId = "frequency_analysis",
                modelType = WekaMLProcessorNode.MLModelType.FREQUENCY_ANALYSIS,
                processingMode = WekaMLProcessorNode.ProcessingMode.HIGH_QUALITY
            )
            pipeline.addNode("frequency_analysis", analysisNode)

            val emotionNode = WekaMLProcessorNode(
                nodeId = "emotion_detection",
                modelType = WekaMLProcessorNode.MLModelType.EMOTION_DETECTION,
                processingMode = WekaMLProcessorNode.ProcessingMode.HIGH_QUALITY
            )
            pipeline.addNode("emotion_detection", emotionNode)

            // Connect microphone to all ML processors
            pipeline.connect("microphone", "enhancement")
            pipeline.connect("microphone", "frequency_analysis")
            pipeline.connect("microphone", "emotion_detection")

            // Stream results on different ports
            val enhancedStreamNode = UdpStreamOutputNode("enhanced_stream", "192.168.1.100", 8001)
            val analysisStreamNode = UdpStreamOutputNode("analysis_stream", "192.168.1.100", 8002)
            val emotionStreamNode = UdpStreamOutputNode("emotion_stream", "192.168.1.100", 8003)

            pipeline.addNode("enhanced_stream", enhancedStreamNode)
            pipeline.addNode("analysis_stream", analysisStreamNode)
            pipeline.addNode("emotion_stream", emotionStreamNode)

            pipeline.connect("enhancement", "enhanced_stream")
            pipeline.connect("frequency_analysis", "analysis_stream")
            pipeline.connect("emotion_detection", "emotion_stream")

            return true

        } catch (e: Exception) {
            Log.e("MLAudioResearchPipeline", "Configuration failed", e)
            return false
        }
    }

    suspend fun start(): Boolean = pipeline.start()
    suspend fun stop(): Boolean = pipeline.stop()
}