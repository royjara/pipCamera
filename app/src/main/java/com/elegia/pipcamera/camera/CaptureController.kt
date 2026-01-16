package com.elegia.pipcamera.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalCamera2Interop::class)
object CaptureController {
    private val _captureRequests = MutableSharedFlow<CaptureRequestOptions>()
    val captureRequests: SharedFlow<CaptureRequestOptions> = _captureRequests

    private var currentSession: CameraCaptureSession? = null
    private var camera2Control: Camera2CameraControl? = null
    private var streamingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            currentSession = session
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            currentSession = null
        }

        override fun onClosed(session: CameraCaptureSession) {
            currentSession = null
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