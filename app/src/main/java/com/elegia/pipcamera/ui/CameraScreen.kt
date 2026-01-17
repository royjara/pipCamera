package com.elegia.pipcamera.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.elegia.pipcamera.camera.rememberCameraManager
import com.elegia.pipcamera.camera.CaptureController

@Composable
fun CameraScreen(isPiPMode: Boolean = false) {
    PermissionHandler {
        CameraPreview(isPiPMode = isPiPMode)
    }
}

@Composable
private fun CameraPreview(isPiPMode: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = rememberCameraManager()

    val capabilities by cameraManager.capabilities.collectAsState()
    val metering by CaptureController.currentMetering.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = if (isPiPMode) {
                PreviewView.ScaleType.FIT_CENTER
            } else {
                PreviewView.ScaleType.FILL_CENTER
            }
        }
    }

    LaunchedEffect(previewView) {
        cameraManager.initializeCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView
        )
    }

    // Update preview scale type when PiP mode changes
    LaunchedEffect(isPiPMode) {
        previewView.scaleType = if (isPiPMode) {
            PreviewView.ScaleType.FIT_CENTER
        } else {
            PreviewView.ScaleType.FILL_CENTER
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isPiPMode) Modifier.clip(CircleShape) else Modifier)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Metering info overlay
        MeteringInfoOverlay(
            metering = metering,
            capabilities = capabilities,
            isPiPMode = isPiPMode
        )

        // Camera toolbar
        CameraToolbar(
            capabilities = capabilities,
            currentMetering = metering,
            isPiPMode = isPiPMode
        )

        // Surface toolbar
        SurfaceToolbar(
            isPiPMode = isPiPMode,
            onSurfaceToggle = { surfaceId, enabled ->
                when (surfaceId) {
                    "analysis" -> {
                        if (enabled) {
                            cameraManager.enableAnalysis()
                        } else {
                            cameraManager.disableAnalysis()
                        }
                    }
                    "snapshot" -> {
                        if (enabled) {
                            cameraManager.enableSnapshot()
                        } else {
                            cameraManager.disableSnapshot()
                        }
                    }
                    "video" -> {
                        if (enabled) {
                            cameraManager.enableVideo()
                        } else {
                            cameraManager.disableVideo()
                        }
                    }
                    "audio" -> {
                        if (enabled) {
                            cameraManager.enableAudio()
                        } else {
                            cameraManager.disableAudio()
                        }
                    }
                }
            },
            onSnapshotClick = {
                cameraManager.takeSnapshot()
            },
            onVideoToggle = {
                if (cameraManager.isRecording.value) {
                    cameraManager.stopVideoRecording()
                } else {
                    cameraManager.startVideoRecording()
                }
            },
            isRecording = cameraManager.isRecording.collectAsState().value,
            isSnapshotEnabled = cameraManager.isSnapshotEnabled.collectAsState().value,
            isVideoEnabled = cameraManager.isVideoEnabled.collectAsState().value,
            isAnalysisEnabled = cameraManager.isAnalysisEnabled.collectAsState().value,
            isAudioEnabled = cameraManager.isAudioEnabled.collectAsState().value
        )
    }
}