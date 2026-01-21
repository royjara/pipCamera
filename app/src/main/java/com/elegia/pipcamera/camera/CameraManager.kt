package com.elegia.pipcamera.camera

import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import android.hardware.camera2.CaptureRequest
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.ImageCaptureException
import androidx.core.util.Consumer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalCamera2Interop::class)
class CameraManager {
    companion object {
        private const val TAG = "CLAUDE_CameraManager"
    }
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _isPiPMode = MutableStateFlow(false)
    val isPiPMode: StateFlow<Boolean> = _isPiPMode

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    val capabilities: StateFlow<CameraCapabilities?> = _capabilities

    private val _camera = MutableStateFlow<Camera?>(null)
    val camera: StateFlow<Camera?> = _camera

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var camera2Control: Camera2CameraControl? = null
    private var camera2Info: Camera2CameraInfo? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraSelector: CameraSelector? = null
    private var context: Context? = null

    private val _isAnalysisEnabled = MutableStateFlow(false)
    val isAnalysisEnabled: StateFlow<Boolean> = _isAnalysisEnabled

    private val _isSnapshotEnabled = MutableStateFlow(false)
    val isSnapshotEnabled: StateFlow<Boolean> = _isSnapshotEnabled

    private val _isVideoEnabled = MutableStateFlow(false)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isAudioEnabled = MutableStateFlow(true)
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled

    // Visual feedback states
    private val _snapshotFeedback = MutableStateFlow(false)
    val snapshotFeedback: StateFlow<Boolean> = _snapshotFeedback

    private val _recordingIndicator = MutableStateFlow(false)
    val recordingIndicator: StateFlow<Boolean> = _recordingIndicator

    private val _isGLEnabled = MutableStateFlow(false)
    val isGLEnabled: StateFlow<Boolean> = _isGLEnabled

    // Frame rotation for ImageAnalysis display
    private val _frameRotation = MutableStateFlow(0) // 0, 90, 180, 270 degrees
    val frameRotation: StateFlow<Int> = _frameRotation

    // Audio processing pipeline
    private val _isAudioProcessingEnabled = MutableStateFlow(false)
    val isAudioProcessingEnabled: StateFlow<Boolean> = _isAudioProcessingEnabled

    private var surfaceInstanceIndex = 0
    private fun getNextSurfaceIndex() = ++surfaceInstanceIndex

    fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {}
    ) {
        Log.d(TAG, "initializeCamera: Starting camera initialization")
        this.lifecycleOwner = lifecycleOwner
        this.context = previewView.context
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            Log.d(TAG, "initializeCamera: CameraProvider obtained")

            val previewIndex = getNextSurfaceIndex()
            Log.d(TAG, "Surface_Preview: Creating surface - camid=back, usecase=Preview, index=$previewIndex")

            preview = Preview.Builder()
                .also { builder ->
                    Camera2Interop.Extender(builder)
                        .setSessionStateCallback(CaptureController.sessionStateCallback)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .setSessionCaptureCallback(CaptureController.captureCallback)
                }
                .build()
                .also { preview ->
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    Log.d(TAG, "Surface_Preview: Surface provider set - index=$previewIndex")
                }

            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Pre-allocate surfaces for performance
            initializeSurfaces()

            try {
                cameraProvider?.unbindAll()
                val camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector!!,
                    preview!!
                )

                // Get Camera2 control and info for interop
                camera?.let {
                    camera2Control = Camera2CameraControl.from(it.cameraControl)
                    camera2Info = Camera2CameraInfo.from(it.cameraInfo)
                    CaptureController.setCamera2Control(camera2Control)

                    // Query camera capabilities
                    _capabilities.value = CameraCapabilities.from(camera2Info)
                }

                _isReady.value = true
                onReady()

                // Start capture request streaming
                CaptureController.startCaptureRequestStream()
            } catch (exc: Exception) {
                // Handle camera binding failure
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    private fun initializeSurfaces() {
        Log.d(TAG, "initializeSurfaces: Pre-allocating surfaces for performance")
        context?.let { ctx ->
            // Initialize ImageAnalysis for analysis
            val imageAnalysisIndex = getNextSurfaceIndex()
            Log.d(TAG, "Surface_ImageAnalysis: Pre-allocating surface - camid=back, usecase=ImageAnalysis, index=$imageAnalysisIndex")

            imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Drop frames if processing is slow - optimal for real-time processing
                .also { builder ->
                    Camera2Interop.Extender(builder)
                        .setSessionStateCallback(CaptureController.sessionStateCallback)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .setSessionCaptureCallback(CaptureController.captureCallback)
                }
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        processImageAnalysis(imageProxy)
                        imageProxy.close()
                    }
                }
            Log.d(TAG, "Surface_ImageAnalysis: Surface pre-allocated - index=$imageAnalysisIndex")

            // Initialize ImageCapture for snapshots
            val imageCaptureIndex = getNextSurfaceIndex()
            Log.d(TAG, "Surface_ImageCapture: Pre-allocating surface - camid=back, usecase=ImageCapture, index=$imageCaptureIndex")

            imageCapture = ImageCapture.Builder()
                .also { builder ->
                    Camera2Interop.Extender(builder)
                        .setSessionStateCallback(CaptureController.sessionStateCallback)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .setSessionCaptureCallback(CaptureController.captureCallback)
                }
                .build()
            Log.d(TAG, "Surface_ImageCapture: Surface pre-allocated - index=$imageCaptureIndex")

            // Initialize VideoCapture for video recording
            val videoCaptureIndex = getNextSurfaceIndex()
            Log.d(TAG, "Surface_VideoCapture: Pre-allocating VideoCapture - camid=back, usecase=VideoRecord, index=$videoCaptureIndex")

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD))
                .build()

            videoCapture = VideoCapture.Builder(recorder)
                .also { builder ->
                    Camera2Interop.Extender(builder)
                        .setSessionStateCallback(CaptureController.sessionStateCallback)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .setSessionCaptureCallback(CaptureController.captureCallback)
                }
                .build()
            Log.d(TAG, "Surface_VideoCapture: VideoCapture pre-allocated - index=$videoCaptureIndex")

            // GL rendering will use ImageAnalysis instead of separate Preview
            Log.d(TAG, "Surface_GL: GL rendering will be handled through ImageAnalysis pipeline")
        }
    }



    fun enableAnalysis() {
        if (_isAnalysisEnabled.value) {
            Log.w(TAG, "enableAnalysis: Already enabled")
            return
        }

        Log.d(TAG, "enableAnalysis: Enabling pre-allocated analysis surface")
        _isAnalysisEnabled.value = true
        rebindCamera()
        Log.d(TAG, "enableAnalysis: Analysis enabled successfully")
    }

    fun disableAnalysis() {
        if (!_isAnalysisEnabled.value) {
            Log.w(TAG, "disableAnalysis: Already disabled")
            return
        }

        Log.d(TAG, "disableAnalysis: Disabling analysis surface")
        _isAnalysisEnabled.value = false
        rebindCamera()
        Log.d(TAG, "disableAnalysis: Analysis disabled successfully")
    }

    fun enableSnapshot() {
        if (_isSnapshotEnabled.value) {
            Log.w(TAG, "enableSnapshot: Already enabled")
            return
        }

        Log.d(TAG, "enableSnapshot: Enabling pre-allocated snapshot surface")
        _isSnapshotEnabled.value = true
        rebindCamera()
        Log.d(TAG, "enableSnapshot: Snapshot enabled successfully")
    }

    fun disableSnapshot() {
        if (!_isSnapshotEnabled.value) {
            Log.w(TAG, "disableSnapshot: Already disabled")
            return
        }

        Log.d(TAG, "disableSnapshot: Disabling snapshot surface")
        _isSnapshotEnabled.value = false
        rebindCamera()
        Log.d(TAG, "disableSnapshot: Snapshot disabled successfully")
    }

    fun enableVideo() {
        if (_isVideoEnabled.value) {
            Log.w(TAG, "enableVideo: Already enabled")
            return
        }

        Log.d(TAG, "enableVideo: Enabling video surface")

        // Just enable video surface - MediaRecorder will be prepared when recording starts
        _isVideoEnabled.value = true
        rebindCamera()
        Log.d(TAG, "enableVideo: Video enabled successfully")
    }

    fun disableVideo() {
        if (!_isVideoEnabled.value) {
            Log.w(TAG, "disableVideo: Already disabled")
            return
        }

        Log.d(TAG, "disableVideo: Disabling video surface")

        // Stop recording if currently recording
        if (_isRecording.value) {
            stopVideoRecording()
        }

        _isVideoEnabled.value = false
        rebindCamera()
        Log.d(TAG, "disableVideo: Video disabled successfully")
    }

    fun enableAudio() {
        Log.d(TAG, "enableAudio: Audio enabled")
        _isAudioEnabled.value = true
    }

    fun disableAudio() {
        Log.d(TAG, "disableAudio: Audio disabled")
        _isAudioEnabled.value = false
    }

    fun enableGL() {
        if (_isGLEnabled.value) {
            Log.w(TAG, "enableGL: Already enabled")
            return
        }

        Log.d(TAG, "enableGL: Enabling GL surface")
        _isGLEnabled.value = true
        rebindCamera()
        Log.d(TAG, "enableGL: GL surface enabled successfully")
    }

    fun disableGL() {
        if (!_isGLEnabled.value) {
            Log.w(TAG, "disableGL: Already disabled")
            return
        }

        Log.d(TAG, "disableGL: Disabling GL surface")
        _isGLEnabled.value = false
        rebindCamera()
        Log.d(TAG, "disableGL: GL surface disabled successfully")
    }

    fun rotateFrameClockwise() {
        val currentRotation = _frameRotation.value
        val newRotation = (currentRotation + 90) % 360
        _frameRotation.value = newRotation
        FrameProcessor.setRotation(newRotation)
        Log.d(TAG, "rotateFrameClockwise: Frame rotation set to ${newRotation}°")
    }

    fun rotateFrameCounterclockwise() {
        val currentRotation = _frameRotation.value
        val newRotation = (currentRotation - 90 + 360) % 360
        _frameRotation.value = newRotation
        FrameProcessor.setRotation(newRotation)
        Log.d(TAG, "rotateFrameCounterclockwise: Frame rotation set to ${newRotation}°")
    }

    // Capture Request Control Methods
    @OptIn(ExperimentalCamera2Interop::class)
    fun updateAWBMode(mode: Int) {
        try {
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, mode)
                .build()
            camera2Control?.addCaptureRequestOptions(captureRequestOptions)
            Log.d(TAG, "updateAWBMode: Set AWB mode to $mode")
        } catch (e: Exception) {
            Log.e(TAG, "updateAWBMode: Failed to update AWB mode", e)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun updateAFMode(mode: Int) {
        try {
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, mode)
                .build()
            camera2Control?.addCaptureRequestOptions(captureRequestOptions)
            Log.d(TAG, "updateAFMode: Set AF mode to $mode")
        } catch (e: Exception) {
            Log.e(TAG, "updateAFMode: Failed to update AF mode", e)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun updateAEMode(mode: Int) {
        try {
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, mode)
                .build()
            camera2Control?.addCaptureRequestOptions(captureRequestOptions)
            Log.d(TAG, "updateAEMode: Set AE mode to $mode")
        } catch (e: Exception) {
            Log.e(TAG, "updateAEMode: Failed to update AE mode", e)
        }
    }


    fun enableAudioProcessing() {
        if (_isAudioProcessingEnabled.value) {
            Log.w(TAG, "enableAudioProcessing: Already enabled")
            return
        }

        Log.d(TAG, "enableAudioProcessing: Enabling audio processing pipeline")
        _isAudioProcessingEnabled.value = true
        Log.d(TAG, "enableAudioProcessing: Audio processing enabled successfully")
    }

    fun disableAudioProcessing() {
        if (!_isAudioProcessingEnabled.value) {
            Log.w(TAG, "disableAudioProcessing: Already disabled")
            return
        }

        Log.d(TAG, "disableAudioProcessing: Disabling audio processing pipeline")
        _isAudioProcessingEnabled.value = false
        Log.d(TAG, "disableAudioProcessing: Audio processing disabled successfully")
    }

    fun takeSnapshot() {
        if (!_isSnapshotEnabled.value || imageCapture == null || context == null) return

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            File(context!!.getExternalFilesDir(null), "snapshot_${System.currentTimeMillis()}.jpg")
        ).build()

        imageCapture!!.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context!!),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Trigger visual feedback instead of toast
                    _snapshotFeedback.value = true
                    // Reset feedback after delay
                    GlobalScope.launch {
                        delay(200) // Brief color change
                        _snapshotFeedback.value = false
                    }
                    Log.i(TAG, "Snapshot captured successfully: ${output.savedUri}")
                }

                override fun onError(exception: ImageCaptureException) {
                    // Keep error toasts as requested
                    Toast.makeText(context, "Snapshot capture failed", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Snapshot capture failed", exception)
                }
            }
        )
    }

    fun startVideoRecording() {
        if (!_isVideoEnabled.value || _isRecording.value || context == null || videoCapture == null) {
            Log.w(TAG, "startVideoRecording: Cannot start - videoEnabled=${_isVideoEnabled.value}, isRecording=${_isRecording.value}, context=${context != null}, videoCapture=${videoCapture != null}")
            return
        }

        Log.d(TAG, "startVideoRecording: Starting video recording with CameraX VideoCapture")

        try {
            val videoFile = File(context!!.getExternalFilesDir(null), "video_${System.currentTimeMillis()}.mp4")
            Log.d(TAG, "startVideoRecording: Target file = ${videoFile.absolutePath}")

            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            val pendingRecording = videoCapture!!.output
                .prepareRecording(context!!, outputOptions)
                .apply {
                    if (_isAudioEnabled.value && ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        withAudioEnabled()
                        Log.d(TAG, "startVideoRecording: Audio enabled for recording")
                    } else {
                        Log.d(TAG, "startVideoRecording: Audio disabled for recording (permission not granted or disabled)")
                    }
                }

            Log.d(TAG, "startVideoRecording: Starting recording with CameraX VideoCapture")
            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context!!)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "VideoRecordEvent: Recording started")
                        _isRecording.value = true
                        _recordingIndicator.value = true
                        Log.i(TAG, "Video recording started: ${videoFile.name}")
                    }
                    is VideoRecordEvent.Finalize -> {
                        Log.d(TAG, "VideoRecordEvent: Recording finalized, error=${recordEvent.error}")
                        _isRecording.value = false
                        _recordingIndicator.value = false
                        activeRecording = null

                        if (recordEvent.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                            Log.e(TAG, "VideoRecordEvent: Recording failed with error ${recordEvent.error}")
                            // Keep error toasts as requested
                            Toast.makeText(context, "Video recording failed", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d(TAG, "VideoRecordEvent: Recording saved to ${recordEvent.outputResults.outputUri}")
                            // Remove success toast - visual feedback will be provided by red circle disappearing
                        }
                    }
                    is VideoRecordEvent.Status -> {
                        Log.d(TAG, "VideoRecordEvent: Status update - recordedDuration=${recordEvent.recordingStats.recordedDurationNanos / 1_000_000}ms")
                    }
                    is VideoRecordEvent.Pause -> {
                        Log.d(TAG, "VideoRecordEvent: Recording paused")
                    }
                    is VideoRecordEvent.Resume -> {
                        Log.d(TAG, "VideoRecordEvent: Recording resumed")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "startVideoRecording: Failed to start recording", e)
            _isRecording.value = false
            _recordingIndicator.value = false
            activeRecording = null
            Toast.makeText(context, "Failed to start video recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopVideoRecording() {
        if (!_isRecording.value || activeRecording == null) {
            Log.w(TAG, "stopVideoRecording: Not recording or no active recording")
            return
        }

        Log.d(TAG, "stopVideoRecording: Stopping video recording with CameraX VideoCapture")

        try {
            activeRecording?.stop()
            Log.d(TAG, "stopVideoRecording: Stop requested, waiting for finalize event")
        } catch (e: Exception) {
            Log.e(TAG, "stopVideoRecording: Error stopping recording", e)
            _isRecording.value = false
            _recordingIndicator.value = false
            activeRecording = null
            Toast.makeText(context, "Error stopping video recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rebindCamera() {
        Log.d(TAG, "rebindCamera: Starting camera rebinding")
        lifecycleOwner?.let { lifecycle ->
            cameraSelector?.let { selector ->
                try {
                    Log.d(TAG, "rebindCamera: Unbinding all existing use cases")
                    cameraProvider?.unbindAll()

                    // Build use case list based on enabled surfaces - ALWAYS use consistent order
                    val useCases = mutableListOf<UseCase>().apply {
                        // Order matters for surface indexing - always add in same order:
                        // 1. Preview (always enabled)
                        preview?.let {
                            add(it)
                            Log.d(TAG, "rebindCamera: Adding Preview surface to binding (index 0)")
                        }

                        // 2. ImageAnalysis (if enabled)
                        if (_isAnalysisEnabled.value && imageAnalysis != null) {
                            add(imageAnalysis!!)
                            Log.d(TAG, "rebindCamera: Adding ImageAnalysis surface to binding (index 1)")
                        }

                        // 3. ImageCapture (if enabled)
                        if (_isSnapshotEnabled.value && imageCapture != null) {
                            add(imageCapture!!)
                            Log.d(TAG, "rebindCamera: Adding ImageCapture surface to binding (index 2)")
                        }

                        // 4. VideoCapture (if enabled)
                        if (_isVideoEnabled.value && videoCapture != null) {
                            add(videoCapture!!)
                            Log.d(TAG, "rebindCamera: Adding VideoCapture surface to binding (index 3)")
                        }

                        // Note: GL rendering will use existing surfaces, no separate GL Preview needed
                    }

                    Log.d(TAG, "rebindCamera: Total use cases to bind: ${useCases.size}")

                    Log.d(TAG, "rebindCamera: Binding ${useCases.size} use cases to lifecycle")
                    val cameraInstance = cameraProvider?.bindToLifecycle(
                        lifecycle,
                        selector,
                        *useCases.toTypedArray()
                    )

                    _camera.value = cameraInstance

                    cameraInstance?.let {
                        Log.d(TAG, "rebindCamera: Camera bound successfully, setting up interop controls")
                        camera2Control = Camera2CameraControl.from(it.cameraControl)
                        camera2Info = Camera2CameraInfo.from(it.cameraInfo)
                        CaptureController.setCamera2Control(camera2Control)
                        _capabilities.value = CameraCapabilities.from(camera2Info)
                        Log.d(TAG, "rebindCamera: Camera2Interop controls configured")
                    }
                } catch (exc: Exception) {
                    Log.e(TAG, "rebindCamera: Failed to rebind camera", exc)
                }
            }
        }
        Log.d(TAG, "rebindCamera: Camera rebinding completed")
    }

    private fun processImageAnalysis(imageProxy: ImageProxy) {
        // Send frames to AGSL shader through optimized channel
        kotlinx.coroutines.runBlocking {
            FrameProcessor.processFrame(imageProxy)
        }
    }

    fun updatePiPMode(isPiP: Boolean) {
        _isPiPMode.value = isPiP
    }

    fun shutdown() {
        Log.d(TAG, "shutdown: Shutting down camera manager")

        // Stop any ongoing recording
        if (_isRecording.value) {
            stopVideoRecording()
        }

        CaptureController.stopCaptureRequestStream()
        FrameProcessor.cleanup()
        cameraProvider?.unbindAll()
        _camera.value = null
        _isReady.value = false
        _isAnalysisEnabled.value = false
        _isSnapshotEnabled.value = false
        _isVideoEnabled.value = false
        _isRecording.value = false
        _isGLEnabled.value = false
        _isAudioProcessingEnabled.value = false
        Log.d(TAG, "shutdown: Camera manager shutdown complete")
    }
}

@Composable
fun rememberCameraManager(): CameraManager {
    val cameraManager = remember { CameraManager() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraManager.shutdown()
        }
    }

    return cameraManager
}

/**
 * Stateless tap-to-focus function that doesn't depend on CameraManager state
 */
fun tapToFocus(camera: Camera, context: Context, displayId: Int, x: Float, y: Float, width: Int, height: Int) {
    try {
        // Create a factory that maps UI coordinates to Sensor coordinates
        val display = (context as? android.app.Activity)?.display
            ?: (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .getDisplay(displayId)

        val factory = DisplayOrientedMeteringPointFactory(
            display, // Use the display to handle rotation
            camera.cameraInfo,
            width.toFloat(),
            height.toFloat()
        )

        // Create the point and action
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, TimeUnit.SECONDS) // AF stays locked for 3s
            .build()

        // Start the focus using regular CameraControl
        camera.cameraControl.startFocusAndMetering(action)
        Log.d("CLAUDE_TapToFocus", "Focus started at coordinates ($x, $y)")
    } catch (e: Exception) {
        Log.e("CLAUDE_TapToFocus", "Failed to start focus and metering", e)
    }
}