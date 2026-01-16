package com.elegia.pipcamera.camera

import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCamera2Interop::class)
class CameraManager {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var camera2Control: Camera2CameraControl? = null

    fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder()
                .also { builder ->
                    Camera2Interop.Extender(builder)
                        .setSessionStateCallback(CaptureController.sessionStateCallback)
                }
                .build()
                .also { preview ->
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                val camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )

                // Get Camera2 control for interop
                camera?.let {
                    camera2Control = Camera2CameraControl.from(it.cameraControl)
                    CaptureController.setCamera2Control(camera2Control)
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

    fun shutdown() {
        CaptureController.stopCaptureRequestStream()
        cameraProvider?.unbindAll()
        _isReady.value = false
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