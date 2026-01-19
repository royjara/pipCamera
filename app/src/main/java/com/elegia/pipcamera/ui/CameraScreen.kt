package com.elegia.pipcamera.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.elegia.pipcamera.camera.rememberCameraManager
import com.elegia.pipcamera.camera.CaptureController
import android.os.Build
import androidx.annotation.RequiresApi

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

    var showAudioDemo by remember { mutableStateOf(false) }

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
                    "gl" -> {
                        if (enabled) {
                            cameraManager.enableGL()
                        } else {
                            cameraManager.disableGL()
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
            onRotateClockwise = {
                cameraManager.rotateFrameClockwise()
            },
            onRotateCounterclockwise = {
                cameraManager.rotateFrameCounterclockwise()
            },
            onAudioDemoClick = {
                showAudioDemo = true
            },
            isRecording = cameraManager.isRecording.collectAsState().value,
            isSnapshotEnabled = cameraManager.isSnapshotEnabled.collectAsState().value,
            isVideoEnabled = cameraManager.isVideoEnabled.collectAsState().value,
            isAnalysisEnabled = cameraManager.isAnalysisEnabled.collectAsState().value,
            isAudioEnabled = cameraManager.isAudioEnabled.collectAsState().value,
            isGLEnabled = cameraManager.isGLEnabled.collectAsState().value
        )

        // AGSL Shader overlay (when enabled) - smaller centered window
        if (cameraManager.isGLEnabled.collectAsState().value && !isPiPMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AGSLCameraOverlay(
                isEnabled = true
            )
        }

    }

    // Audio Demo Modal
    if (showAudioDemo) {
        AudioDemoModal(
            onDismiss = { showAudioDemo = false }
        )
    }
}