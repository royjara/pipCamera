package com.elegia.pipcamera.pipeline.nodes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.elegia.pipcamera.pipeline.InputNode
import com.elegia.pipcamera.pipeline.MediaData
import java.nio.ByteBuffer

/**
 * Real-time microphone audio capture node
 * Captures high-quality audio using AudioRecord API
 */
class MicrophoneInputNode(
    nodeId: String,
    private val sampleRate: Int = 44100,
    private val channels: Int = 1, // Mono for simplicity
    private val bufferSizeFrames: Int = 1024
) : InputNode(nodeId) {

    companion object {
        private const val TAG = "MicrophoneInput"
    }

    private var audioRecord: AudioRecord? = null
    private val bufferSizeBytes = bufferSizeFrames * channels * 2 // 16-bit samples
    private var isCapturing = false

    override suspend fun initialize(context: Context): Boolean {
        // Check audio permission
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Audio recording permission not granted")
            return false
        }

        val channelConfig = if (channels == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val actualBufferSize = maxOf(bufferSizeBytes, minBufferSize)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return false
            }

            audioRecord?.startRecording()
            isCapturing = true

            Log.i(TAG, "Microphone initialized: ${sampleRate}Hz, $channels ch, buffer: $actualBufferSize bytes")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize microphone", e)
            return false
        }
    }

    override suspend fun captureData(): MediaData? {
        val record = audioRecord
        if (record == null || !isCapturing) {
            return null
        }

        try {
            // Allocate buffer for audio data
            val buffer = ByteBuffer.allocateDirect(bufferSizeBytes)
            val audioBuffer = ByteArray(bufferSizeBytes)

            // Read audio data from microphone
            val bytesRead = record.read(audioBuffer, 0, bufferSizeBytes)

            if (bytesRead > 0) {
                buffer.put(audioBuffer, 0, bytesRead)
                buffer.flip()

                return MediaData.AudioFrame(
                    buffer = buffer,
                    sampleRate = sampleRate,
                    channels = channels,
                    timestamp = System.nanoTime()
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing audio data", e)
        }

        return null
    }

    override suspend fun process(input: MediaData): MediaData? {
        // Input nodes don't process external data
        return null
    }

    override suspend fun cleanup() {
        isCapturing = false
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
        Log.i(TAG, "Microphone cleanup completed")
    }

    /**
     * Get current audio recording state
     */
    fun isRecording(): Boolean {
        return audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
    }

    /**
     * Get audio format information
     */
    fun getAudioInfo(): String {
        return "${sampleRate}Hz, $channels ch, ${bufferSizeFrames} frames"
    }
}