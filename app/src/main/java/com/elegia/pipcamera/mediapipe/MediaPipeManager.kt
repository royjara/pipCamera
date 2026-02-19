package com.elegia.pipcamera.mediapipe

import android.content.Context
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.StateFlow

object MediaPipeManager {
    private var mediaPipeProcessor: MediaPipeProcessor? = null
    private var isEnabled: Boolean = false

    fun initialize(context: Context) {
        if (mediaPipeProcessor == null) {
            mediaPipeProcessor = MediaPipeProcessor(context)
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        mediaPipeProcessor?.setEnabled(enabled)
    }

    fun isEnabled(): Boolean = isEnabled

    fun updateSettings(
        delegate: Int,
        minFaceDetectionConfidence: Float,
        minFaceTrackingConfidence: Float,
        minFacePresenceConfidence: Float,
        maxFaces: Int
    ) {
        mediaPipeProcessor?.updateSettings(
            delegate = delegate,
            minFaceDetectionConfidence = minFaceDetectionConfidence,
            minFaceTrackingConfidence = minFaceTrackingConfidence,
            minFacePresenceConfidence = minFacePresenceConfidence,
            maxFaces = maxFaces
        )
    }

    // Expose state flows for UI consumption
    fun getProcessingResults(): StateFlow<MediaPipeProcessor.ProcessingResult?>? {
        return mediaPipeProcessor?.processingResults
    }

    fun getIsProcessing(): StateFlow<Boolean>? {
        return mediaPipeProcessor?.isProcessing
    }

    fun getProcessingStats(): StateFlow<MediaPipeProcessor.ProcessingStats>? {
        return mediaPipeProcessor?.processingStats
    }

    fun cleanup() {
        mediaPipeProcessor?.cleanup()
        mediaPipeProcessor = null
        isEnabled = false
    }
}