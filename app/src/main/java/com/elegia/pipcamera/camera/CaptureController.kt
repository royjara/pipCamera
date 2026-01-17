package com.elegia.pipcamera.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCamera2Interop::class)
object CaptureController {
    private const val TAG = "CLAUDE_CaptureController"
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
            Log.d(TAG, "Surface_Session: onConfigured - session=${session.hashCode()}")
            currentSession = session
            // Setup initial capture request from preview template
            setupInitialCaptureRequest(session)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Surface_Session: onConfigureFailed - session=${session.hashCode()}")
            currentSession = null
        }

        override fun onClosed(session: CameraCaptureSession) {
            Log.d(TAG, "Surface_Session: onClosed - session=${session.hashCode()}")
            currentSession = null
        }

        override fun onReady(session: CameraCaptureSession) {
            Log.d(TAG, "Surface_Session: onReady - session=${session.hashCode()}")
            super.onReady(session)
        }

        override fun onActive(session: CameraCaptureSession) {
            Log.d(TAG, "Surface_Session: onActive - session=${session.hashCode()}")
            super.onActive(session)
        }

        override fun onSurfacePrepared(session: CameraCaptureSession, surface: android.view.Surface) {
            Log.d(TAG, "Surface_Session: onSurfacePrepared - session=${session.hashCode()}, surface=${surface.hashCode()}")
            super.onSurfacePrepared(session, surface)
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
        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            Log.v(TAG, "CaptureRequest: onCaptureStarted - frame=$frameNumber, timestamp=$timestamp")
            super.onCaptureStarted(session, request, timestamp, frameNumber)
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            Log.v(TAG, "CaptureRequest: onCaptureProgressed - partial result available")
            super.onCaptureProgressed(session, request, partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            Log.v(TAG, "CaptureRequest: onCaptureCompleted - result available")
            super.onCaptureCompleted(session, request, result)
            // Update metering info from capture result
            scope.launch {
                _currentMetering.value = CameraMetering.from(result)
            }
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: android.hardware.camera2.CaptureFailure
        ) {
            Log.w(TAG, "CaptureRequest: onCaptureFailed - reason=${failure.reason}, frame=${failure.frameNumber}")
            super.onCaptureFailed(session, request, failure)
        }

        override fun onCaptureSequenceCompleted(
            session: CameraCaptureSession,
            sequenceId: Int,
            frameNumber: Long
        ) {
            Log.d(TAG, "CaptureRequest: onCaptureSequenceCompleted - sequence=$sequenceId, frame=$frameNumber")
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber)
        }

        override fun onCaptureSequenceAborted(
            session: CameraCaptureSession,
            sequenceId: Int
        ) {
            Log.w(TAG, "CaptureRequest: onCaptureSequenceAborted - sequence=$sequenceId")
            super.onCaptureSequenceAborted(session, sequenceId)
        }

        override fun onCaptureBufferLost(
            session: CameraCaptureSession,
            request: CaptureRequest,
            target: android.view.Surface,
            frameNumber: Long
        ) {
            Log.w(TAG, "CaptureRequest: onCaptureBufferLost - frame=$frameNumber, surface=${target.hashCode()}")
            super.onCaptureBufferLost(session, request, target, frameNumber)
        }
    }

    fun setCamera2Control(control: Camera2CameraControl?) {
        Log.d(TAG, "setCamera2Control: Setting Camera2 control - control=${control?.hashCode()}")
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
        Log.d(TAG, "startCaptureRequestStream: Starting capture request streaming")
        streamingJob?.cancel()
        streamingJob = scope.launch {
            var requestCount = 0
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

                    requestCount++
                    Log.v(TAG, "CaptureRequestStream: Emitted request #$requestCount")

                    delay(1000) // Emit every second
                } catch (e: Exception) {
                    Log.e(TAG, "CaptureRequestStream: Error in streaming", e)
                }
            }
            Log.d(TAG, "CaptureRequestStream: Streaming stopped - total requests: $requestCount")
        }
    }

    fun stopCaptureRequestStream() {
        Log.d(TAG, "stopCaptureRequestStream: Stopping capture request streaming")
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