package com.elegia.pipcamera.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.elegia.pipcamera.camera.FrameProcessor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MediaPipeProcessor(
    private val context: Context
) : MediaPipeHelper.MediaPipeListener {

    private val TAG = "MediaPipeProcessor"

    // State flows for UI consumption
    private val _processingResults = MutableStateFlow<ProcessingResult?>(null)
    val processingResults: StateFlow<ProcessingResult?> = _processingResults

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _processingStats = MutableStateFlow(ProcessingStats())
    val processingStats: StateFlow<ProcessingStats> = _processingStats

    // MediaPipe helper instance
    private var mediaPipeHelper: MediaPipeHelper? = null

    // Current settings
    private var currentDelegate = MediaPipeHelper.DELEGATE_CPU
    private var currentModel = "face_landmarker.task"
    private var isEnabled = false

    // Processing scope
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        setupMediaPipe()
        startFrameProcessing()
    }

    private fun setupMediaPipe() {
        try {
            mediaPipeHelper?.clearFaceLandmarker()

            mediaPipeHelper = MediaPipeHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                currentDelegate = currentDelegate,
                mediaPipeHelperListener = this
            )

            Log.d(TAG, "MediaPipe initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe", e)
        }
    }

    fun updateSettings(
        delegate: Int,
        minFaceDetectionConfidence: Float,
        minFaceTrackingConfidence: Float,
        minFacePresenceConfidence: Float,
        maxFaces: Int
    ) {
        if (currentDelegate != delegate) {
            currentDelegate = delegate
            // Reinitialize MediaPipe with new delegate
            setupMediaPipe()
        }

        mediaPipeHelper?.let { helper ->
            helper.minFaceDetectionConfidence = minFaceDetectionConfidence
            helper.minFaceTrackingConfidence = minFaceTrackingConfidence
            helper.minFacePresenceConfidence = minFacePresenceConfidence
            helper.maxNumFaces = maxFaces

            // Reinitialize with new settings
            helper.setupFaceLandmarker()
        }
    }

    private fun startFrameProcessing() {
        processingScope.launch {
            FrameProcessor.frameFlow.collectLatest { bitmap ->
                if (isEnabled) {
                    processFrame(bitmap)
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "MediaPipe processing ${if (enabled) "enabled" else "disabled"}")
    }

    private suspend fun processFrame(bitmap: Bitmap) {
        if (mediaPipeHelper?.isClose() == true) {
            Log.w(TAG, "MediaPipe helper is closed, skipping frame")
            return
        }

        _isProcessing.value = true

        try {
            // Convert bitmap to MPImage for MediaPipe processing
            val mpImage = BitmapImageBuilder(bitmap).build()

            // Use detectAsync instead of detectLiveStream since we have bitmap
            mediaPipeHelper?.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            _isProcessing.value = false
        }
    }

    // Remove the old ImageProxy-based processFrame method since it's no longer needed

    // MediaPipeHelper.MediaPipeListener implementation
    override fun onResults(resultBundle: MediaPipeHelper.ResultBundle) {
        _isProcessing.value = false

        val processingResult = ProcessingResult(
            faceLandmarkerResult = resultBundle.result,
            inferenceTime = resultBundle.inferenceTime,
            imageWidth = resultBundle.inputImageWidth,
            imageHeight = resultBundle.inputImageHeight
        )

        _processingResults.value = processingResult

        // Update processing stats
        val currentStats = _processingStats.value
        _processingStats.value = currentStats.copy(
            totalFramesProcessed = currentStats.totalFramesProcessed + 1,
            averageInferenceTime = updateRunningAverage(
                currentStats.averageInferenceTime,
                resultBundle.inferenceTime,
                currentStats.totalFramesProcessed + 1
            ),
            facesDetected = resultBundle.result.faceLandmarks().size,
            lastProcessingTime = System.currentTimeMillis()
        )

        Log.d(TAG, "Processing completed - ${resultBundle.inferenceTime}ms, ${resultBundle.result.faceLandmarks().size} faces")
    }

    override fun onError(error: String, errorCode: Int) {
        _isProcessing.value = false
        Log.e(TAG, "MediaPipe error: $error (code: $errorCode)")

        // Update error stats
        val currentStats = _processingStats.value
        _processingStats.value = currentStats.copy(
            errorCount = currentStats.errorCount + 1,
            lastError = error
        )
    }

    override fun onEmpty() {
        _isProcessing.value = false

        // No faces detected - this is normal, not an error
        val processingResult = ProcessingResult(
            faceLandmarkerResult = null,
            inferenceTime = 0,
            imageWidth = 0,
            imageHeight = 0
        )

        _processingResults.value = processingResult
    }

    private fun updateRunningAverage(currentAverage: Float, newValue: Long, count: Int): Float {
        return (currentAverage * (count - 1) + newValue) / count
    }

    fun cleanup() {
        isEnabled = false
        processingScope.cancel()
        mediaPipeHelper?.clearFaceLandmarker()
        mediaPipeHelper = null
        Log.d(TAG, "MediaPipeProcessor cleaned up")
    }

    // Data classes
    data class ProcessingResult(
        val faceLandmarkerResult: FaceLandmarkerResult?,
        val inferenceTime: Long,
        val imageWidth: Int,
        val imageHeight: Int
    )

    data class ProcessingStats(
        val totalFramesProcessed: Int = 0,
        val averageInferenceTime: Float = 0f,
        val facesDetected: Int = 0,
        val errorCount: Int = 0,
        val lastError: String = "",
        val lastProcessingTime: Long = 0L
    )
}