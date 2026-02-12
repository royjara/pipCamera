package com.elegia.pipcamera.camera

import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Simplified reactive capture request processor that responds to basic camera data
 * Analyzes camera frames directly without ML dependencies
 */
object ReactiveCaptureProcessor {
    private const val TAG = "ReactiveCaptureProcessor"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Configuration state
    private val _enabledFeatures = MutableStateFlow(CaptureFeatures())
    val enabledFeatures: StateFlow<CaptureFeatures> = _enabledFeatures

    // Processing state
    private val _frameAnalysisFlow = MutableSharedFlow<FrameAnalysis>()
    private val _currentScene = MutableStateFlow(SceneAnalysis())
    private var lastFrameTime = 0L
    private var lastAverageLuminance = 0f

    data class CaptureFeatures(
        val autoExposureAdjustment: Boolean = true,
        val autoFocusAdjustment: Boolean = true,
        val autoWhiteBalance: Boolean = true,
        val sceneAdaptiveSettings: Boolean = true
    )

    data class FrameAnalysis(
        val averageLuminance: Float,
        val timestamp: Long
    )

    data class SceneAnalysis(
        val brightness: Float = 0f,       // 0 to 1 (dark to bright)
        val motionDetected: Boolean = false,
        val lastUpdateTime: Long = System.currentTimeMillis()
    )

    /**
     * Initialize reactive capture processing
     */
    fun initialize() {
        Log.d(TAG, "Initializing reactive capture processor")

        // React to frame analysis results
        scope.launch {
            _frameAnalysisFlow
                .collect { analysis ->
                    processFrameAnalysis(analysis)
                }
        }

        // Generate capture requests based on scene analysis changes
        scope.launch {
            _currentScene
                .distinctUntilChanged { old, new ->
                    // Only emit if significant change detected
                    abs(old.brightness - new.brightness) > 0.1f ||
                    old.motionDetected != new.motionDetected
                }
                .collect { scene ->
                    generateCaptureRequest(scene)
                }
        }

        Log.d(TAG, "Reactive capture processor initialized")
    }

    /**
     * Process frame analysis to update scene analysis and trigger capture requests
     */
    private fun processFrameAnalysis(analysis: FrameAnalysis) {
        // Detect motion by comparing luminance changes over time
        val motionDetected = if (lastFrameTime > 0) {
            val timeDelta = analysis.timestamp - lastFrameTime
            val luminanceDelta = abs(analysis.averageLuminance - lastAverageLuminance)

            // Motion detected if significant luminance change in short time
            timeDelta < 500 && luminanceDelta > 0.1f
        } else {
            false
        }

        val newScene = SceneAnalysis(
            brightness = analysis.averageLuminance,
            motionDetected = motionDetected,
            lastUpdateTime = analysis.timestamp
        )

        // Only update if scene has changed significantly
        val currentScene = _currentScene.value
        if (abs(newScene.brightness - currentScene.brightness) > 0.05f ||
            newScene.motionDetected != currentScene.motionDetected) {

            _currentScene.value = newScene
            Log.d(TAG, "Scene updated: brightness=${String.format("%.2f", newScene.brightness)}, " +
                      "motion=$motionDetected")
        }

        lastFrameTime = analysis.timestamp
        lastAverageLuminance = analysis.averageLuminance
    }

    /**
     * Analyze camera frame for basic scene properties
     * Call this from ImageAnalysis.Analyzer
     */
    fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            val luminance = calculateAverageLuminance(imageProxy)
            val analysis = FrameAnalysis(
                averageLuminance = luminance,
                timestamp = System.currentTimeMillis()
            )

            scope.launch {
                _frameAnalysisFlow.emit(analysis)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing frame", e)
        }
    }

    /**
     * Calculate average luminance from image data
     */
    private fun calculateAverageLuminance(imageProxy: ImageProxy): Float {
        val yBuffer = imageProxy.planes[0].buffer // Y plane for luminance
        val ySize = yBuffer.remaining()
        var sum = 0L

        // Sample every 10th pixel for performance
        val stepSize = max(1, ySize / 1000)
        var sampleCount = 0

        for (i in 0 until ySize step stepSize) {
            sum += (yBuffer.get(i).toInt() and 0xFF)
            sampleCount++
        }

        return if (sampleCount > 0) {
            (sum.toFloat() / sampleCount) / 255f // Normalize to 0-1
        } else {
            0.5f // Default middle brightness
        }
    }

    /**
     * Generate capture request based on scene analysis
     */
    private fun generateCaptureRequest(scene: SceneAnalysis) {
        val features = _enabledFeatures.value
        if (!features.autoExposureAdjustment && !features.autoFocusAdjustment && !features.autoWhiteBalance) {
            return // No features enabled
        }

        try {
            val optionsBuilder = CaptureRequestOptions.Builder()

            // Adaptive exposure compensation based on brightness
            if (features.autoExposureAdjustment) {
                val exposureCompensation = when {
                    scene.brightness < 0.2f -> 2  // Very dark, increase exposure
                    scene.brightness < 0.4f -> 1  // Dark, slight increase
                    scene.brightness > 0.8f -> -2  // Very bright, decrease exposure
                    scene.brightness > 0.6f -> -1  // Bright, slight decrease
                    else -> 0  // Normal lighting
                }

                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    exposureCompensation
                )
            }

            // Adaptive focus mode based on motion detection
            if (features.autoFocusAdjustment) {
                val focusMode = if (scene.motionDetected) {
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO  // Better for motion
                } else {
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE  // Better for stills
                }

                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    focusMode
                )
            }

            // Auto white balance (simplified)
            if (features.autoWhiteBalance) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO
                )
            }

            val options = optionsBuilder.build()

            // Submit to camera control
            CaptureController.submitReactiveCaptureRequest(options)

            Log.d(TAG, "Generated reactive capture request for scene: " +
                      "brightness=${String.format("%.2f", scene.brightness)}, " +
                      "motion=${scene.motionDetected}")

        } catch (e: Exception) {
            Log.e(TAG, "Error generating capture request", e)
        }
    }

    /**
     * Enable/disable specific capture features
     */
    fun updateFeatures(features: CaptureFeatures) {
        _enabledFeatures.value = features
        Log.d(TAG, "Capture features updated: $features")
    }

    /**
     * Manually trigger scene re-analysis (useful for testing)
     */
    fun triggerSceneUpdate() {
        val currentScene = _currentScene.value
        val updatedScene = currentScene.copy(lastUpdateTime = System.currentTimeMillis())
        _currentScene.value = updatedScene
        Log.d(TAG, "Manual scene update triggered")
    }

    /**
     * Get current scene analysis for debugging
     */
    fun getCurrentScene(): SceneAnalysis = _currentScene.value

    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "Reactive capture processor cleaned up")
    }
}