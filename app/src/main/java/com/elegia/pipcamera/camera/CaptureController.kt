package com.elegia.pipcamera.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCamera2Interop::class)
object CaptureController {
    private val _captureRequests = MutableSharedFlow<CaptureRequestOptions>()
    val captureRequests: SharedFlow<CaptureRequestOptions> = _captureRequests

    private val _currentMetering = MutableStateFlow<CameraMetering?>(null)
    val currentMetering: StateFlow<CameraMetering?> = _currentMetering

    private var currentSession: CameraCaptureSession? = null
    private var camera2Control: Camera2CameraControl? = null
    private var streamingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            currentSession = session
            // Setup initial capture request from preview template
            setupInitialCaptureRequest(session)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            currentSession = null
        }

        override fun onClosed(session: CameraCaptureSession) {
            currentSession = null
        }
    }

    private fun setupInitialCaptureRequest(session: CameraCaptureSession) {
        try {
            // Create a capture request based on preview template
            val captureRequestBuilder = session.device.createCaptureRequest(
                android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
            )

            // Set basic parameters for preview
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO
            )
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )

            // Note: Surface targets will be set by CameraX, we just setup the template parameters
        } catch (e: Exception) {
            // Handle error
        }
    }

    val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            // Update metering info from capture result
            scope.launch {
                _currentMetering.value = CameraMetering.from(result)
            }
        }
    }

    fun setCamera2Control(control: Camera2CameraControl?) {
        camera2Control = control
    }

    fun <T : Any> submitCaptureRequest(
        captureRequestKey: CaptureRequest.Key<T>,
        value: T
    ) {
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(captureRequestKey, value)
            .build()

        camera2Control?.addCaptureRequestOptions(options)

        // Emit the capture request for streaming
        scope.launch {
            _captureRequests.emit(options)
        }
    }

    fun startCaptureRequestStream() {
        streamingJob?.cancel()
        streamingJob = scope.launch {
            while (isActive) {
                // Example: Submit periodic capture requests
                // This could be used for continuous autofocus, exposure adjustments, etc.
                try {
                    val options = CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        .build()

                    camera2Control?.addCaptureRequestOptions(options)
                    _captureRequests.emit(options)

                    delay(1000) // Emit every second
                } catch (e: Exception) {
                    // Handle streaming errors
                }
            }
        }
    }

    fun stopCaptureRequestStream() {
        streamingJob?.cancel()
        currentSession = null
    }

    // Convenience method for common capture request parameters
    fun updateExposure(exposureCompensation: Int) {
        submitCaptureRequest(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation)
    }

    fun updateFocusMode(focusMode: Int) {
        submitCaptureRequest(CaptureRequest.CONTROL_AF_MODE, focusMode)
    }

    fun updateFlashMode(flashMode: Int) {
        submitCaptureRequest(CaptureRequest.CONTROL_AE_MODE, flashMode)
    }
}