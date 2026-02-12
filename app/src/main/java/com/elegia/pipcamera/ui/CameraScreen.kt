package com.elegia.pipcamera.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.sp
import com.elegia.pipcamera.camera.CameraMetering
import com.elegia.pipcamera.camera.rememberCameraManager
import com.elegia.pipcamera.camera.CaptureController
import com.elegia.pipcamera.camera.tapToFocus
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
    var showDebugPanel by remember { mutableStateOf(false) }

//    val pipelineStateHolder = remember

    val capabilities by cameraManager.capabilities.collectAsState()
    val metering by CaptureController.currentMetering.collectAsState()
    val camera by cameraManager.camera.collectAsState()
    val cameraError by cameraManager.cameraError.collectAsState()


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

    // Show error screen if camera initialization failed
    if (cameraError != null) {
        CameraErrorScreen(error = cameraError!!, isPiPMode = isPiPMode)
        return
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

        // Pinned debug display above toolbar
        if (showDebugPanel) {
            PinnedDebugDisplay(
                metering = metering,
                isPiPMode = isPiPMode
            )
        }

        // Camera toolbar
        CameraToolbar(
            capabilities = capabilities,
            currentMetering = metering,
            isPiPMode = isPiPMode,
            cameraManager = cameraManager,
            showDebugPanel = showDebugPanel,
            onDebugToggle = { showDebugPanel = !showDebugPanel }
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
            // NEW: Visual feedback states
            snapshotFeedback = cameraManager.snapshotFeedback.collectAsState().value,
            recordingIndicator = cameraManager.recordingIndicator.collectAsState().value
        )
    }
}

@Composable
private fun CameraErrorScreen(error: String, isPiPMode: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .then(if (isPiPMode) Modifier.clip(CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Camera Error",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Camera Error",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Preview
@Composable
fun CameraErrorPreview() {
    CameraErrorScreen(error = "No working cameras available")
}

@Composable
private fun PinnedDebugDisplay(
    metering: CameraMetering?,
    isPiPMode: Boolean = false
) {
    var filterText by remember { mutableStateOf("EXP|SENS") }

    if (!isPiPMode) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomStart
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
                    .heightIn(max = 200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DEBUG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
                            placeholder = { Text("Filter", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(2f),
                            textStyle = MaterialTheme.typography.labelSmall,
                            singleLine = true
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        metering?.let { meteringData ->
                            val filteredKeys = meteringData.getFilteredKeys(filterText)

                            filteredKeys.forEach { (keyName, value) ->
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = keyName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(2f),
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            if (filteredKeys.isEmpty() && meteringData.allCaptureKeys.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "No keys match \"$filterText\"",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } ?: item {
                            Text(
                                text = "No capture result data",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun MockPreview(){

}