package com.elegia.pipcamera.pipeline.nodes

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import com.elegia.pipcamera.pipeline.MediaData
import com.elegia.pipcamera.pipeline.MediaNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Hardware-accelerated audio encoder using MediaCodec
 * Supports multiple codecs: AAC, Opus, AMR-WB
 */
class MediaCodecEncoderNode(
    nodeId: String,
    private val codecType: String = MediaFormat.MIMETYPE_AUDIO_AAC,
    private val bitRate: Int = 128000, // 128 kbps
    private val outputSampleRate: Int = 44100
) : MediaNode(nodeId) {

    companion object {
        private const val TAG = "MediaCodecEncoder"

        // Supported audio codecs
        const val CODEC_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
        const val CODEC_OPUS = "audio/opus"
        const val CODEC_AMR_WB = MediaFormat.MIMETYPE_AUDIO_AMR_WB
    }

    private var encoder: MediaCodec? = null
    private var inputFormat: MediaFormat? = null
    private var outputFormat: MediaFormat? = null
    private var isConfigured = false

    override suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create MediaCodec encoder
            encoder = MediaCodec.createEncoderByType(codecType)
            Log.i(TAG, "Created encoder for: $codecType")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaCodec encoder", e)
            return@withContext false
        }
    }

    override suspend fun process(input: MediaData): MediaData? = withContext(Dispatchers.IO) {
        if (input !is MediaData.AudioFrame) {
            Log.w(TAG, "Expected AudioFrame, got ${input::class.simpleName}")
            return@withContext null
        }

        try {
            // Configure encoder if not already done
            if (!isConfigured) {
                configureEncoder(input)
            }

            // Encode the audio frame
            return@withContext encodeFrame(input)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio frame", e)
            return@withContext null
        }
    }

    private fun configureEncoder(audioFrame: MediaData.AudioFrame) {
        val format = MediaFormat.createAudioFormat(codecType, outputSampleRate, audioFrame.channels)

        when (codecType) {
            CODEC_AAC -> {
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            CODEC_OPUS -> {
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                // Opus-specific configuration
            }
            CODEC_AMR_WB -> {
                format.setInteger(MediaFormat.KEY_BIT_RATE, 23850) // Fixed rate for AMR-WB
            }
        }

        encoder?.apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        inputFormat = format
        isConfigured = true
        Log.i(TAG, "Encoder configured: $codecType, ${audioFrame.sampleRate}Hz -> ${outputSampleRate}Hz")
    }

    private fun encodeFrame(audioFrame: MediaData.AudioFrame): MediaData? {
        val codec = encoder ?: return null

        try {
            // Get input buffer
            val inputBufferIndex = codec.dequeueInputBuffer(1000) // 1ms timeout
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.apply {
                    clear()
                    put(audioFrame.buffer.array(), 0, audioFrame.buffer.remaining())
                }

                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    audioFrame.buffer.remaining(),
                    audioFrame.timestamp / 1000, // Convert to microseconds
                    0
                )
            }

            // Get encoded output
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000)

            when {
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val encodedData = ByteBuffer.allocate(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        encodedData.put(outputBuffer)
                        encodedData.flip()

                        codec.releaseOutputBuffer(outputBufferIndex, false)

                        return MediaData.EncodedData(
                            buffer = encodedData,
                            codecType = codecType,
                            timestamp = bufferInfo.presentationTimeUs * 1000 // Convert to nanoseconds
                        )
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = codec.outputFormat
                    Log.i(TAG, "Encoder output format changed: $outputFormat")
                }

                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available yet
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Encoding error", e)
        }

        return null
    }

    override suspend fun cleanup() {
        encoder?.apply {
            if (isConfigured) {
                stop()
            }
            release()
        }
        encoder = null
        isConfigured = false
        Log.i(TAG, "MediaCodec encoder cleanup completed")
    }

    /**
     * Get current encoding statistics
     */
    fun getEncodingInfo(): Map<String, Any> {
        return mapOf(
            "codecType" to codecType,
            "bitRate" to bitRate,
            "sampleRate" to outputSampleRate,
            "isConfigured" to isConfigured,
            "outputFormat" to (outputFormat?.toString() ?: "not available")
        )
    }

    /**
     * Check if hardware acceleration is available for this codec
     */
    fun isHardwareAccelerated(): Boolean {
        return try {
            val codecInfo = MediaCodec.createEncoderByType(codecType).codecInfo
            !codecInfo.isSoftwareOnly
        } catch (e: Exception) {
            false
        }
    }
}