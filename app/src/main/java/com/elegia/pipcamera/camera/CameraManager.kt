package com.elegia.pipcamera.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
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
import android.hardware.camera2.CameraManager as AndroidCameraManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(ExperimentalCamera2Interop::class)
class CameraManager {
    companion object {
        private const val TAG = "CLAUDE_CameraManager"
    }

    // Background coroutine scope for image processing to avoid blocking main thread
    private val imageProcessingScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )
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

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError

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

    // Camera selection state
    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera

    // Available camera IDs and current selection
    private var availableCameraIds: List<String> = emptyList()
    private val _currentCameraIndex = MutableStateFlow(0)
    val currentCameraIndex: StateFlow<Int> = _currentCameraIndex
    private val _currentCameraId = MutableStateFlow("0")
    val currentCameraId: StateFlow<String> = _currentCameraId


    private var surfaceInstanceIndex = 0
    private fun getNextSurfaceIndex() = ++surfaceInstanceIndex

    private fun queryAndTestCameraIds(context: Context): Boolean {
        return try {
            val androidCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
            val candidateCameraIds = androidCameraManager.cameraIdList

            Log.d(TAG, "queryAndTestCameraIds: Found ${candidateCameraIds.size} camera IDs: ${candidateCameraIds.contentToString()}")

            if (candidateCameraIds.isEmpty()) {
                Log.e(TAG, "queryAndTestCameraIds: No camera IDs found")
                return false
            }

            // Test each camera and collect working ones
            val workingCameraIds = mutableListOf<String>()

            for ((index, cameraId) in candidateCameraIds.withIndex()) {
                try {
                    Log.d(TAG, "queryAndTestCameraIds: Testing camera ID: $cameraId (index $index)")
                    val characteristics = androidCameraManager.getCameraCharacteristics(cameraId)

                    // Get basic info to ensure camera is accessible
                    val lensFacing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                    val hardwareLevel = characteristics.get(android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

                    Log.d(TAG, "queryAndTestCameraIds: Camera $cameraId - lensFacing=$lensFacing, hardwareLevel=$hardwareLevel")

                    // Camera is working, add to list
                    workingCameraIds.add(cameraId)
                    Log.i(TAG, "queryAndTestCameraIds: Successfully validated camera $cameraId")

                } catch (e: Exception) {
                    Log.w(TAG, "queryAndTestCameraIds: Failed to access camera $cameraId: ${e.message}")
                    continue
                }
            }

            if (workingCameraIds.isEmpty()) {
                Log.e(TAG, "queryAndTestCameraIds: All ${candidateCameraIds.size} cameras failed validation")
                return false
            }

            // Store working camera IDs and initialize to first camera
            availableCameraIds = workingCameraIds
            _currentCameraIndex.value = 0
            _currentCameraId.value = workingCameraIds[0]

            Log.i(TAG, "queryAndTestCameraIds: Found ${workingCameraIds.size} working cameras: $workingCameraIds")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "queryAndTestCameraIds: Error during camera query", e)
            false
        }
    }

    fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {}
    ) {
        Log.d(TAG, "initializeCamera: Starting camera initialization")
        this.lifecycleOwner = lifecycleOwner
        this.context = previewView.context

        // First, query available camera IDs and try to initialize with a working camera
        if (!queryAndTestCameraIds(previewView.context)) {
            Log.e(TAG, "initializeCamera: No working cameras found")
            _cameraError.value = "No working cameras available"
            _isReady.value = false
            return
        }

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
                        // Optimized capture request options
                        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(30, 30)) // Lock to 30fps for consistency
                        .setCaptureRequestOption(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW)
                        .setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                        .setSessionCaptureCallback(CaptureController.captureCallback)
                }
                .build()
                .also { preview ->
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    Log.d(TAG, "Surface_Preview: Surface provider set - index=$previewIndex")
                }

            cameraSelector = if (_isFrontCamera.value) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Pre-allocate surfaces for performance
            initializeSurfaces()

            // All surfaces are pre-allocated and cached in memory, but not bound
            // Surface FABs control whether surfaces are bound to session
            // Shutter buttons use already-cached surfaces instantly
            _isAnalysisEnabled.value = false  // Enable when ML processing needed
            _isSnapshotEnabled.value = false  // Enable when user toggles snapshot FAB
            _isVideoEnabled.value = false     // Enable when user toggles video FAB

            try {
                cameraProvider?.unbindAll()

                // Validate preconditions before binding
                require(lifecycleOwner != null) { "LifecycleOwner is null" }
                require(cameraSelector != null) { "CameraSelector is null" }
                require(preview != null) { "Preview is null" }

                Log.d(TAG, "Binding camera with initial preview surface only")
                val camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector!!,
                    preview!!  // Start with just preview
                )

                // Get Camera2 control and info for interop
                camera?.let {
                    camera2Control = Camera2CameraControl.from(it.cameraControl)
                    camera2Info = Camera2CameraInfo.from(it.cameraInfo)
                    CaptureController.setCamera2Control(camera2Control)

                    // Query camera capabilities and hardware level
                    _capabilities.value = CameraCapabilities.from(camera2Info)

                    // Log hardware capabilities and apply optimizations based on hardware level
                    val hardwareLevel: Int? = camera2Info?.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    val isLegacy = hardwareLevel == android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
                    val isFull = hardwareLevel == android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                    val isLevel3 = hardwareLevel == android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3

                    // Check for concurrent camera support (API 28+)
                    val capabilities: IntArray? = camera2Info?.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    val supportsConcurrent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        capabilities?.contains(
                            android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                        ) == true
                    } else false

                    // Check manual sensor capabilities
                    val supportsManualSensor = capabilities?.contains(
                        android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
                    ) == true

                    // Check RAW capabilities
                    val supportsRaw = capabilities?.contains(
                        android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                    ) == true

                    Log.d(TAG, "Camera hardware: level=$hardwareLevel, legacy=$isLegacy, full=$isFull, level3=$isLevel3")
                    Log.d(TAG, "Camera capabilities: concurrent=$supportsConcurrent, manual=$supportsManualSensor, raw=$supportsRaw")

                    // Apply hardware-specific optimizations
                    applyHardwareOptimizations(hardwareLevel, supportsManualSensor)
                }

                _isReady.value = true
                onReady()

                // Start capture request streaming
                CaptureController.startCaptureRequestStream()
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to bind camera", exc)
                _cameraError.value = "Camera binding failed: ${exc.message}"
                _isReady.value = false
                context?.let { ctx ->
                    Toast.makeText(ctx, "Camera initialization failed: ${exc.message}", Toast.LENGTH_LONG).show()
                }
                // Reset state on failure
                _camera.value = null
                camera2Control = null
                camera2Info = null
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    private fun applyHardwareOptimizations(hardwareLevel: Int?, supportsManualSensor: Boolean) {
        Log.d(TAG, "applyHardwareOptimizations: Applying optimizations based on hardware capabilities")

        try {
            val optimizations = CaptureRequestOptions.Builder()

            // Apply optimizations based on hardware level
            when (hardwareLevel) {
                android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> {
                    Log.d(TAG, "Legacy hardware detected - applying conservative settings")
                    // For legacy devices, use simpler processing
                    optimizations.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                    optimizations.setCaptureRequestOption(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
                }

                android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> {
                    Log.d(TAG, "Full/Level3 hardware detected - enabling advanced features")
                    // For full/level3 devices, enable advanced features
                    optimizations.setCaptureRequestOption(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                    optimizations.setCaptureRequestOption(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)

                    if (supportsManualSensor) {
                        Log.d(TAG, "Manual sensor control available - enabling advanced exposure control")
                        // Enable advanced exposure control for manual sensor devices
                        optimizations.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                    }
                }

                else -> {
                    Log.d(TAG, "Limited hardware detected - using balanced settings")
                    // For limited devices, use balanced settings
                    optimizations.setCaptureRequestOption(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                }
            }

            // Apply thermal throttling prevention
            optimizations.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(30, 30))

            // Optimize for low power consumption in continuous analysis
            optimizations.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)

            // Apply the optimizations
            camera2Control?.addCaptureRequestOptions(optimizations.build())
            Log.d(TAG, "applyHardwareOptimizations: Hardware optimizations applied successfully")

        } catch (e: Exception) {
            Log.e(TAG, "applyHardwareOptimizations: Failed to apply hardware optimizations", e)
        }
    }

    private fun initializeSurfaces() {
        Log.d(TAG, "initializeSurfaces: Pre-allocating optimized surfaces for performance")
        context?.let { ctx ->
            // Initialize ImageAnalysis for analysis
            val imageAnalysisIndex = getNextSurfaceIndex()
            Log.d(TAG, "Surface_ImageAnalysis: Pre-allocating surface - camid=back, usecase=ImageAnalysis, index=$imageAnalysisIndex")

            imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Drop frames if processing is slow - optimal for real-time processing
                .setTargetResolution(Size(1280, 720)) // Optimize resolution for ML processing
                .setTargetRotation(Surface.ROTATION_0) // Explicit rotation handling
                .also { builder ->
                    Camera2Interop.Extender(builder)
                        .setSessionStateCallback(CaptureController.sessionStateCallback)
                        // Optimized for analysis performance
                        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)
                        .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF) // Faster processing
                        .setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF) // Reduce processing overhead
                        .setSessionCaptureCallback(CaptureController.captureCallback)
                }
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        // Launch processing in background scope to avoid blocking main thread
                        imageProcessingScope.launch {
                            processImageAnalysis(imageProxy)
                        }
                    }
                }
            Log.d(TAG, "Surface_ImageAnalysis: Surface pre-allocated - index=$imageAnalysisIndex")

            // Initialize ImageCapture for snapshots
            val imageCaptureIndex = getNextSurfaceIndex()
            Log.d(TAG, "Surface_ImageCapture: Pre-allocating surface - camid=back, usecase=ImageCapture, index=$imageCaptureIndex")

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) // Optimize for speed over quality in ML context
                .setTargetRotation(Surface.ROTATION_0) // Explicit rotation handling
                .also { builder ->
                    Camera2Interop.Extender(builder)
                        .setSessionStateCallback(CaptureController.sessionStateCallback)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .setCaptureRequestOption(CaptureRequest.JPEG_QUALITY, 85) // Balance quality vs speed
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
                .setTargetRotation(Surface.ROTATION_0) // Explicit rotation handling
                .also { builder ->
                    Camera2Interop.Extender(builder)
                        .setSessionStateCallback(CaptureController.sessionStateCallback)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
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

        Log.d(TAG, "enableAnalysis: Enabling analysis processing")
        _isAnalysisEnabled.value = true
        rebindCameraWithActiveSurfaces()
        Log.d(TAG, "enableAnalysis: Analysis enabled successfully")
    }

    fun disableAnalysis() {
        if (!_isAnalysisEnabled.value) {
            Log.w(TAG, "disableAnalysis: Already disabled")
            return
        }

        Log.d(TAG, "disableAnalysis: Disabling analysis processing")
        _isAnalysisEnabled.value = false
        rebindCameraWithActiveSurfaces()
        Log.d(TAG, "disableAnalysis: Analysis disabled successfully")
    }

    fun enableSnapshot() {
        if (_isSnapshotEnabled.value) {
            Log.w(TAG, "enableSnapshot: Snapshot surface already bound")
            return
        }

        Log.d(TAG, "enableSnapshot: Binding cached snapshot surface to camera session")
        _isSnapshotEnabled.value = true
        rebindCameraWithActiveSurfaces()
        Log.d(TAG, "enableSnapshot: Snapshot surface bound - shutter ready")
    }

    fun disableSnapshot() {
        if (!_isSnapshotEnabled.value) {
            Log.w(TAG, "disableSnapshot: Snapshot surface not bound")
            return
        }

        Log.d(TAG, "disableSnapshot: Unbinding snapshot surface from camera session")
        _isSnapshotEnabled.value = false
        rebindCameraWithActiveSurfaces()
        Log.d(TAG, "disableSnapshot: Snapshot surface unbound - surface cached in memory")
    }

    fun enableVideo() {
        if (_isVideoEnabled.value) {
            Log.w(TAG, "enableVideo: Video surface already bound")
            return
        }

        Log.d(TAG, "enableVideo: Binding cached video surface to camera session")
        _isVideoEnabled.value = true
        rebindCameraWithActiveSurfaces()
        Log.d(TAG, "enableVideo: Video surface bound - recording ready")
    }

    fun disableVideo() {
        if (!_isVideoEnabled.value) {
            Log.w(TAG, "disableVideo: Video surface not bound")
            return
        }

        Log.d(TAG, "disableVideo: Unbinding video surface from camera session")

        // Stop recording if currently recording
        if (_isRecording.value) {
            stopVideoRecording()
        }

        _isVideoEnabled.value = false
        rebindCameraWithActiveSurfaces()
        Log.d(TAG, "disableVideo: Video surface unbound - surface cached in memory")
    }


    fun enableGL() {
        if (_isGLEnabled.value) {
            Log.w(TAG, "enableGL: Already enabled")
            return
        }

        Log.d(TAG, "enableGL: Enabling GL processing")
        _isGLEnabled.value = true
        Log.d(TAG, "enableGL: GL processing enabled successfully")
    }

    fun disableGL() {
        if (!_isGLEnabled.value) {
            Log.w(TAG, "disableGL: Already disabled")
            return
        }

        Log.d(TAG, "disableGL: Disabling GL processing")
        _isGLEnabled.value = false
        Log.d(TAG, "disableGL: GL processing disabled successfully")
    }

    /**
     * Cycle through available camera IDs
     */
    fun toggleCamera() {
        if (availableCameraIds.isEmpty()) {
            Log.w(TAG, "toggleCamera: No available camera IDs")
            return
        }

        // Cycle to next camera ID
        val nextIndex = (_currentCameraIndex.value + 1) % availableCameraIds.size
        val nextCameraId = availableCameraIds[nextIndex]

        Log.d(TAG, "toggleCamera: Cycling from camera ${_currentCameraId.value} (index ${_currentCameraIndex.value}) to camera $nextCameraId (index $nextIndex)")

        _currentCameraIndex.value = nextIndex
        _currentCameraId.value = nextCameraId

        // Create camera selector for the specific camera ID
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(getCameraLensFacing(nextCameraId))
            .build()

        // Update front camera state for UI
        _isFrontCamera.value = getCameraLensFacing(nextCameraId) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT

        // Rebind camera with new selector
        rebindCameraWithActiveSurfaces()
    }

    private fun getCameraLensFacing(cameraId: String): Int {
        return try {
            context?.let { ctx ->
                val androidCameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
                val characteristics = androidCameraManager.getCameraCharacteristics(cameraId)
                characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                    ?: android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
        } catch (e: Exception) {
            Log.w(TAG, "getCameraLensFacing: Failed to get lens facing for camera $cameraId", e)
            android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
        }
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



    fun takeSnapshot() {
        // Check if surface is allocated and bound (snapshot FAB was pressed)
        if (!_isSnapshotEnabled.value) {
            Log.w(TAG, "takeSnapshot: Snapshot surface not bound - press snapshot FAB first")
            return
        }
        if (imageCapture == null || context == null) {
            Log.e(TAG, "takeSnapshot: Camera not initialized")
            return
        }

        Log.d(TAG, "takeSnapshot: Using cached snapshot surface")

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
        // Check if surface is allocated and bound (video FAB was pressed)
        if (!_isVideoEnabled.value) {
            Log.w(TAG, "startVideoRecording: Video surface not bound - press video FAB first")
            return
        }
        if (_isRecording.value) {
            Log.w(TAG, "startVideoRecording: Already recording")
            return
        }
        if (context == null || videoCapture == null) {
            Log.e(TAG, "startVideoRecording: Camera not initialized")
            return
        }

        Log.d(TAG, "startVideoRecording: Using cached video surface")

        Log.d(TAG, "startVideoRecording: Starting video recording with CameraX VideoCapture")

        try {
            val videoFile = File(context!!.getExternalFilesDir(null), "video_${System.currentTimeMillis()}.mp4")
            Log.d(TAG, "startVideoRecording: Target file = ${videoFile.absolutePath}")

            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            val pendingRecording = videoCapture!!.output
                .prepareRecording(context!!, outputOptions)

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

        // Validate preconditions before rebinding
        if (lifecycleOwner == null) {
            Log.e(TAG, "rebindCamera: LifecycleOwner is null, cannot rebind")
            return
        }
        if (cameraSelector == null) {
            Log.e(TAG, "rebindCamera: CameraSelector is null, cannot rebind")
            return
        }
        if (cameraProvider == null) {
            Log.e(TAG, "rebindCamera: CameraProvider is null, cannot rebind")
            return
        }

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

                    // Reset camera state on binding failure
                    _camera.value = null
                    camera2Control = null
                    camera2Info = null
                    _capabilities.value = null

                    // Notify user of camera failure
                    context?.let { ctx ->
                        Toast.makeText(ctx, "Camera rebinding failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Log.d(TAG, "rebindCamera: Camera rebinding completed")
    }

    /**
     * Smart camera rebinding - only bind surfaces that are cached and enabled
     *
     * Architecture:
     * - All surfaces are pre-allocated and cached in memory during initialization
     * - Surface FABs control whether cached surfaces are bound to camera session
     * - Shutter buttons use already-bound surfaces instantly (no rebinding)
     * - This reduces hardware strain and eliminates capture delays
     */
    private fun rebindCameraWithActiveSurfaces() {
        Log.d(TAG, "rebindCameraWithActiveSurfaces: Binding cached surfaces to camera session")

        // Validate preconditions before rebinding
        if (lifecycleOwner == null) {
            Log.e(TAG, "rebindCameraWithActiveSurfaces: LifecycleOwner is null, cannot rebind")
            return
        }
        if (cameraSelector == null) {
            Log.e(TAG, "rebindCameraWithActiveSurfaces: CameraSelector is null, cannot rebind")
            return
        }
        if (cameraProvider == null) {
            Log.e(TAG, "rebindCameraWithActiveSurfaces: CameraProvider is null, cannot rebind")
            return
        }

        lifecycleOwner?.let { lifecycle ->
            cameraSelector?.let { selector ->
                try {
                    Log.d(TAG, "rebindCameraWithActiveSurfaces: Unbinding all existing use cases")
                    cameraProvider?.unbindAll()

                    // Build use case list with stable surface combinations
                    val useCases = mutableListOf<UseCase>().apply {
                        // 1. Preview (always bound for camera feed)
                        preview?.let {
                            add(it)
                            Log.d(TAG, "rebindCameraWithActiveSurfaces: Binding Preview surface")
                        }

                        // 2. ImageAnalysis (bind only if analysis FAB enabled)
                        if (_isAnalysisEnabled.value && imageAnalysis != null) {
                            add(imageAnalysis!!)
                            Log.d(TAG, "rebindCameraWithActiveSurfaces: Binding ImageAnalysis surface for ML processing")
                        }

                        // 3. ImageCapture (bind only if snapshot FAB enabled)
                        if (_isSnapshotEnabled.value && imageCapture != null) {
                            add(imageCapture!!)
                            Log.d(TAG, "rebindCameraWithActiveSurfaces: Binding cached ImageCapture surface - shutter ready")
                        }

                        // 4. VideoCapture (bind if video enabled OR snapshot needs companion for stability)
                        val needsVideoForStability = _isSnapshotEnabled.value && !_isAnalysisEnabled.value
                        if ((_isVideoEnabled.value || needsVideoForStability) && videoCapture != null) {
                            add(videoCapture!!)
                            if (_isVideoEnabled.value) {
                                Log.d(TAG, "rebindCameraWithActiveSurfaces: Binding cached VideoCapture surface - recording ready")
                            } else {
                                Log.d(TAG, "rebindCameraWithActiveSurfaces: Binding VideoCapture as companion for ImageCapture stability (not for recording)")
                            }
                        }
                    }

                    Log.d(TAG, "rebindCameraWithActiveSurfaces: Binding ${useCases.size} active use cases")

                    val cameraInstance = cameraProvider?.bindToLifecycle(
                        lifecycle,
                        selector,
                        *useCases.toTypedArray()
                    )

                    _camera.value = cameraInstance

                    cameraInstance?.let {
                        Log.d(TAG, "rebindCameraWithActiveSurfaces: Camera session configured with cached surfaces")
                        camera2Control = Camera2CameraControl.from(it.cameraControl)
                        camera2Info = Camera2CameraInfo.from(it.cameraInfo)
                        CaptureController.setCamera2Control(camera2Control)
                        _capabilities.value = CameraCapabilities.from(camera2Info)
                        Log.d(TAG, "rebindCameraWithActiveSurfaces: Camera2Interop controls configured - surfaces ready for instant use")
                    }
                } catch (exc: Exception) {
                    Log.e(TAG, "rebindCameraWithActiveSurfaces: Failed to rebind camera", exc)

                    // Reset camera state on binding failure
                    _camera.value = null
                    camera2Control = null
                    camera2Info = null
                    _capabilities.value = null

                    // Notify user of camera failure
                    context?.let { ctx ->
                        Toast.makeText(ctx, "Camera rebinding failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Log.d(TAG, "rebindCameraWithActiveSurfaces: Camera session updated with bound surfaces - shutter/record buttons ready")
    }

    @OptIn(ExperimentalGetImage::class)
    private suspend fun processImageAnalysis(imageProxy: ImageProxy) {
        try {
            // Check if analysis is enabled
            if (!_isAnalysisEnabled.value) {
                // Skip processing if analysis is disabled
                return
            }

            // Validate image proxy before processing
            if (imageProxy.image == null) {
                Log.w(TAG, "processImageAnalysis: Received null image, skipping frame")
                return
            }

            // Send frames to AGSL shader through optimized channel (already on background thread)
            FrameProcessor.processFrame(imageProxy)
        } catch (e: Exception) {
            Log.e(TAG, "processImageAnalysis: Error processing frame", e)
            // Don't propagate exception - just log and continue with next frame
        }
    }

    fun updatePiPMode(isPiP: Boolean) {
        _isPiPMode.value = isPiP
    }

    fun shutdown() {
        Log.d(TAG, "shutdown: Shutting down camera manager")

        try {
            // Stop any ongoing recording gracefully
            if (_isRecording.value) {
                Log.d(TAG, "shutdown: Stopping active recording")
                stopVideoRecording()
            }

            // Stop capture request streaming
            Log.d(TAG, "shutdown: Stopping capture request stream")
            CaptureController.stopCaptureRequestStream()

            // Clean up frame processing
            Log.d(TAG, "shutdown: Cleaning up frame processor")
            FrameProcessor.cleanup()

            // Cancel image processing coroutines
            Log.d(TAG, "shutdown: Cancelling image processing coroutines")
            imageProcessingScope.cancel()

            // Unbind all camera use cases
            Log.d(TAG, "shutdown: Unbinding all camera use cases")
            cameraProvider?.unbindAll()

            // Clear all references and reset states atomically
            Log.d(TAG, "shutdown: Clearing all references and resetting state")
            _camera.value = null
            camera2Control = null
            camera2Info = null
            cameraProvider = null
            preview = null
            imageAnalysis = null
            imageCapture = null
            videoCapture = null
            activeRecording = null
            lifecycleOwner = null
            cameraSelector = null
            context = null

            // Reset all state flags
            _isReady.value = false
            _isAnalysisEnabled.value = false
            _isSnapshotEnabled.value = false
            _isVideoEnabled.value = false
            _isRecording.value = false
            _isGLEnabled.value = false
            _isPiPMode.value = false
            _capabilities.value = null

            // Reset visual feedback states
            _snapshotFeedback.value = false
            _recordingIndicator.value = false
            _frameRotation.value = 0
            _cameraError.value = null

            // Reset camera ID state
            availableCameraIds = emptyList()
            _currentCameraIndex.value = 0
            _currentCameraId.value = "0"

            Log.d(TAG, "shutdown: Camera manager shutdown complete")

        } catch (e: Exception) {
            Log.e(TAG, "shutdown: Error during cleanup", e)
            // Even if cleanup fails, ensure critical states are reset
            _isReady.value = false
            _isRecording.value = false
            _camera.value = null
        }
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