package com.elegia.pipcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elegia.pipcamera.mediapipe.MediaPipeViewModel
import com.elegia.pipcamera.mediapipe.MediaPipeManager
import com.elegia.pipcamera.camera.CameraManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerModal(
    onDismiss: () -> Unit,
    cameraManager: CameraManager? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val mediaPipeViewModel: MediaPipeViewModel = viewModel()

    // Handle modal dismissal
    val handleDismiss = {
        // Disable MediaPipe processing and ImageAnalysis when modal closes
        cameraManager?.enableMediaPipeProcessing(false)
        cameraManager?.disableAnalysis()
        onDismiss()
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MediaPipe Analyzer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = handleDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Row
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Preview") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Settings") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        PreviewComponent(cameraManager = cameraManager)
                    }
                    1 -> {
                        SettingsComponent(
                            mediaPipeViewModel = mediaPipeViewModel,
                            cameraManager = cameraManager
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewComponent(
    cameraManager: CameraManager? = null
) {
    // Get MediaPipe processing results
    val processingResults by (MediaPipeManager.getProcessingResults()?.collectAsState() ?: remember { mutableStateOf(null) })
    val isProcessing by (MediaPipeManager.getIsProcessing()?.collectAsState() ?: remember { mutableStateOf(false) })

    // Get ImageAnalysis state from CameraManager
    val isAnalysisEnabled by (cameraManager?.isAnalysisEnabled?.collectAsState() ?: remember { mutableStateOf(false) })

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Analysis Control Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Camera Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isAnalysisEnabled && isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = when {
                                !isAnalysisEnabled -> "Analysis disabled"
                                isProcessing -> "Processing frames..."
                                else -> "Analysis enabled, waiting for frames"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Switch(
                    checked = isAnalysisEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            cameraManager?.enableAnalysis()
                            cameraManager?.enableMediaPipeProcessing(true)
                        } else {
                            cameraManager?.enableMediaPipeProcessing(false)
                            cameraManager?.disableAnalysis()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        // Camera Preview Overlay
        MediaPipeCameraOverlay(
            modifier = Modifier.weight(1f),
            showBefore = true,
            showAfter = true,
            mediaPipeHelper = null // We're using MediaPipeManager instead
        )
    }
}

@Composable
private fun SettingsComponent(
    mediaPipeViewModel: MediaPipeViewModel,
    cameraManager: CameraManager? = null
) {
    val currentDelegate by remember { derivedStateOf { mediaPipeViewModel.currentDelegate } }
    val currentMinFaceDetectionConfidence by remember { derivedStateOf { mediaPipeViewModel.currentMinFaceDetectionConfidence } }
    val currentMinFaceTrackingConfidence by remember { derivedStateOf { mediaPipeViewModel.currentMinFaceTrackingConfidence } }
    val currentMinFacePresenceConfidence by remember { derivedStateOf { mediaPipeViewModel.currentMinFacePresenceConfidence } }
    val currentMaxFaces by remember { derivedStateOf { mediaPipeViewModel.currentMaxFaces } }

    // Update MediaPipe settings whenever ViewModel state changes
    LaunchedEffect(
        currentDelegate,
        currentMinFaceDetectionConfidence,
        currentMinFaceTrackingConfidence,
        currentMinFacePresenceConfidence,
        currentMaxFaces
    ) {
        cameraManager?.updateMediaPipeSettings(
            delegate = currentDelegate,
            minFaceDetectionConfidence = currentMinFaceDetectionConfidence,
            minFaceTrackingConfidence = currentMinFaceTrackingConfidence,
            minFacePresenceConfidence = currentMinFacePresenceConfidence,
            maxFaces = currentMaxFaces
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MediaPipe Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Model Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Processing Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val modelOptions = listOf(
                    "Face Landmarker",
                    "Hand Landmarker",
                    "Pose Landmarker"
                )
                var selectedModelIndex by remember { mutableStateOf(0) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modelOptions.forEachIndexed { index, modelName ->
                        FilterChip(
                            onClick = { selectedModelIndex = index },
                            label = { Text(modelName) },
                            selected = selectedModelIndex == index,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Delegate Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Processing Delegate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        onClick = { mediaPipeViewModel.setDelegate(0) },
                        label = { Text("CPU") },
                        selected = currentDelegate == 0,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    FilterChip(
                        onClick = { mediaPipeViewModel.setDelegate(1) },
                        label = { Text("GPU") },
                        selected = currentDelegate == 1,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        // Confidence Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Detection Confidence",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Face Detection Confidence
                Column {
                    Text(
                        text = "Detection: ${String.format("%.2f", currentMinFaceDetectionConfidence)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = currentMinFaceDetectionConfidence,
                        onValueChange = { mediaPipeViewModel.setMinFaceDetectionConfidence(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Face Tracking Confidence
                Column {
                    Text(
                        text = "Tracking: ${String.format("%.2f", currentMinFaceTrackingConfidence)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = currentMinFaceTrackingConfidence,
                        onValueChange = { mediaPipeViewModel.setMinFaceTrackingConfidence(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Face Presence Confidence
                Column {
                    Text(
                        text = "Presence: ${String.format("%.2f", currentMinFacePresenceConfidence)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = currentMinFacePresenceConfidence,
                        onValueChange = { mediaPipeViewModel.setMinFacePresenceConfidence(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Max Faces
                Column {
                    Text(
                        text = "Max Faces: $currentMaxFaces",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = currentMaxFaces.toFloat(),
                        onValueChange = { mediaPipeViewModel.setMaxFaces(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}