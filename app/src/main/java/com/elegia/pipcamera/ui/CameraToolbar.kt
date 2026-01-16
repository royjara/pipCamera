package com.elegia.pipcamera.ui

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.elegia.pipcamera.camera.CameraCapabilities
import com.elegia.pipcamera.camera.CameraMetering
import com.elegia.pipcamera.camera.CaptureController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraToolbar(
    capabilities: CameraCapabilities?,
    currentMetering: CameraMetering?,
    isPiPMode: Boolean = false
) {
    var showDebugScreen by remember { mutableStateOf(false) }
    var showMenuPopup by remember { mutableStateOf(false) }

    if (!isPiPMode && capabilities != null) {
        // Bottom toolbar - always visible
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Bottom toolbar with 3 buttons
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left button - Menu
                    Box {
                        FloatingActionButton(
                            onClick = { showMenuPopup = !showMenuPopup },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Menu popup
                        if (showMenuPopup) {
                            CaptureRequestMenuPopup(
                                capabilities = capabilities,
                                currentMetering = currentMetering,
                                onDismiss = { showMenuPopup = false }
                            )
                        }
                    }

                    // Middle button - Focus
                    FloatingActionButton(
                        onClick = { /* TODO: Focus controls */ },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Focus",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Right button - Debug toggle
                    FloatingActionButton(
                        onClick = { showDebugScreen = !showDebugScreen },
                        modifier = Modifier.size(48.dp),
                        containerColor = if (showDebugScreen)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Debug",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Debug screen overlay
        if (showDebugScreen) {
            DebugScreenOverlay(
                currentMetering = currentMetering,
                onDismiss = { showDebugScreen = false }
            )
        }
    }
}

@Composable
private fun DebugScreenOverlay(
    currentMetering: CameraMetering?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .alpha(0.8f)
                .background(
                    Color.Black.copy(alpha = 0.8f),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "DEBUG - Capture Results",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )

                currentMetering?.let { metering ->
                    DebugItem("Focus Mode", metering.focusMode?.toString() ?: "N/A")
                    DebugItem("Focus State", metering.getFocusStateDisplayName())
                    DebugItem("Exposure Time", metering.getExposureTimeDisplayValue())
                    DebugItem("ISO", metering.getISODisplayValue())
                    DebugItem("Aperture", metering.getApertureDisplayValue())
                    DebugItem("Focus Distance",
                        metering.focusDistance?.let { "%.2f".format(it) } ?: "N/A"
                    )
                    DebugItem("Exposure Comp",
                        metering.exposureCompensation?.toString() ?: "N/A"
                    )
                    DebugItem("White Balance",
                        metering.whiteBalanceMode?.toString() ?: "N/A"
                    )
                } ?: run {
                    Text(
                        text = "No capture result data available",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Close Debug")
                }
            }
        }
    }
}

@Composable
private fun DebugItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = Color.Green,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CaptureRequestMenuPopup(
    capabilities: CameraCapabilities,
    currentMetering: CameraMetering?,
    onDismiss: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .width(280.dp)
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Available Camera Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Current capture result values
                Text(
                    text = "Current Values:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                CaptureKeyValueRow(
                    key = "CONTROL_AF_MODE",
                    value = currentMetering?.focusMode?.toString() ?: "N/A"
                )

                CaptureKeyValueRow(
                    key = "SENSOR_EXPOSURE_TIME",
                    value = currentMetering?.exposureTime?.toString() ?: "N/A"
                )

                CaptureKeyValueRow(
                    key = "SENSOR_SENSITIVITY",
                    value = currentMetering?.iso?.toString() ?: "N/A"
                )

                CaptureKeyValueRow(
                    key = "LENS_APERTURE",
                    value = currentMetering?.aperture?.let { "%.2f".format(it) } ?: "N/A"
                )

                // Available options from capabilities
                Divider()

                Text(
                    text = "Available Options:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                CaptureKeyValueRow(
                    key = "ISO Range",
                    value = capabilities.supportedISO?.let { "${it.first}-${it.last}" } ?: "N/A"
                )

                CaptureKeyValueRow(
                    key = "Focus Modes",
                    value = "${capabilities.supportedFocusModes.size} available"
                )

                CaptureKeyValueRow(
                    key = "Available Apertures",
                    value = capabilities.availableApertures?.let { "${it.size} f-stops" } ?: "N/A"
                )

                CaptureKeyValueRow(
                    key = "Exposure Time Range",
                    value = capabilities.exposureTimeRange?.let {
                        "${it.lower}ns - ${it.upper}ns"
                    } ?: "N/A"
                )

                CaptureKeyValueRow(
                    key = "Scene Modes",
                    value = capabilities.availableSceneModes?.let { "${it.size} modes" } ?: "N/A"
                )
            }
        }
    }
}

@Composable
private fun CaptureKeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}