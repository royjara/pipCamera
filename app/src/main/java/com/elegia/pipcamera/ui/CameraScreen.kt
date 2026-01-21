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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.elegia.pipcamera.camera.rememberCameraManager
import com.elegia.pipcamera.camera.CaptureController
import com.elegia.pipcamera.camera.tapToFocus
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.tooling.preview.Preview

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

//    val pipelineStateHolder = remember

    val capabilities by cameraManager.capabilities.collectAsState()
    val metering by CaptureController.currentMetering.collectAsState()
    val camera by cameraManager.camera.collectAsState()

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
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Use stateless tap to focus function
                        camera?.let { cameraInstance ->
                            tapToFocus(
                                camera = cameraInstance,
                                context = context,
                                displayId = 0, // Simplified for single display
                                x = offset.x,
                                y = offset.y,
                                width = size.width,
                                height = size.height
                            )
                        }
                    }
                }
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
            isPiPMode = isPiPMode,
            cameraManager = cameraManager
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
            isRecording = cameraManager.isRecording.collectAsState().value,
            isSnapshotEnabled = cameraManager.isSnapshotEnabled.collectAsState().value,
            isVideoEnabled = cameraManager.isVideoEnabled.collectAsState().value,
            isAudioEnabled = cameraManager.isAudioEnabled.collectAsState().value,
            // NEW: Visual feedback states
            snapshotFeedback = cameraManager.snapshotFeedback.collectAsState().value,
            recordingIndicator = cameraManager.recordingIndicator.collectAsState().value
        )
    }

    // Audio Demo Modal
    if (showAudioDemo) {
        AudioDemoModal(
            onDismiss = { showAudioDemo = false },
            cameraManager = cameraManager
        )
    }
}

@Preview
@Composable
fun MockPreview(){

}