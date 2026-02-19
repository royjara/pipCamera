package com.elegia.pipcamera.ui

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.elegia.pipcamera.camera.CameraCapabilities
import com.elegia.pipcamera.camera.CameraManager
import com.elegia.pipcamera.camera.CameraMetering
import com.elegia.pipcamera.camera.CaptureController
import com.elegia.pipcamera.camera.CaptureController.currentMetering
import com.elegia.pipcamera.camera.CaptureRequestIntrospection
import com.elegia.pipcamera.camera.rememberCaptureRequestIntrospection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraToolbar(
    capabilities: CameraCapabilities?,
    currentMetering: CameraMetering?,
    isPiPMode: Boolean = false,
    cameraManager: CameraManager? = null,
    showDebugPanel: Boolean = false,
    onDebugToggle: () -> Unit = {}
) {
    var showAnalyzerPopup by remember { mutableStateOf(false) }
    var showOSCConfig by remember { mutableStateOf(false) }

    if (!isPiPMode && capabilities != null) {
        // Bottom toolbar - always visible
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Bottom toolbar with 4 buttons
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left button - Menu
//                    Box(modifier = Modifier.weight(1f)) {
                    FloatingActionButton(
                        onClick = { showAnalyzerPopup = !showAnalyzerPopup },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // OSC Config button
                    FloatingActionButton(
                        onClick = { showOSCConfig = !showOSCConfig },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        containerColor = if (showOSCConfig)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "OSC Config",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Camera toggle button
                    FloatingActionButton(
                        onClick = {
                            cameraManager?.toggleCamera()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        containerColor = if (cameraManager?.isFrontCamera?.collectAsState()?.value == true)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (cameraManager?.isFrontCamera?.collectAsState()?.value == true)
                                    Icons.Default.Face
                                else
                                    Icons.Default.CameraRear,
                                contentDescription = "Toggle Camera",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = cameraManager?.currentCameraId?.collectAsState()?.value
                                    ?: "0",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Right button - Debug toggle
                    FloatingActionButton(
                        onClick = onDebugToggle,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        containerColor = if (showDebugPanel)
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

        if (showAnalyzerPopup) {
            AnalyzerModal(
                onDismiss = { showAnalyzerPopup = false },
                cameraManager = cameraManager
            )
        }

        // OSC Config Modal
        if (showOSCConfig) {
            OSCConfigModal(
                onDismiss = { showOSCConfig = false },
                cameraManager = cameraManager
            )
        }
    }
}


@Composable
private fun CaptureRequestMenuPopup(
    capabilities: CameraCapabilities,
    currentMetering: CameraMetering?,
    cameraManager: CameraManager?,
    onDismiss: () -> Unit
) {
    val introspection = rememberCaptureRequestIntrospection()
    val captureOptions = remember {
        val options = introspection.getAllCaptureRequestOptions()
        android.util.Log.d("CaptureRequestMenuPopup", "Loaded ${options.size} capture options")
        options.forEach { option ->
            android.util.Log.d(
                "CaptureRequestMenuPopup",
                "${option.displayName}: ${option.availableValues.size} values"
            )
        }
        options
    }

    // Update capture options with current metering values
    val updatedOptions = remember(currentMetering) {
        // Debug: Show what keys are actually in the capture result
        currentMetering?.let { metering ->
            android.util.Log.d(
                "CaptureRequestMenuPopup",
                "Available keys containing 'CONTROL': ${metering.debugKeys()}"
            )
            android.util.Log.d(
                "CaptureRequestMenuPopup",
                "focusMode = ${metering.focusMode}, whiteBalanceMode = ${metering.whiteBalanceMode}, aeMode = ${metering.aeMode}"
            )
        }

        captureOptions.map { option ->
            val currentValue = when (option.key) {
                "android.control.awbMode" -> currentMetering?.whiteBalanceMode
                "android.control.afMode" -> currentMetering?.focusMode
                "android.control.aeMode" -> currentMetering?.aeMode
                else -> null
            }
            option.copy(currentValue = currentValue)
        }
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .width(320.dp)
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Camera Control Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (updatedOptions.isEmpty()) {
                    item {
                        Text(
                            text = "No camera control options available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(updatedOptions.size) { index ->
                        val option = updatedOptions[index]
                        CaptureRequestDropdown(
                            option = option,
                            onValueSelected = { selectedValue ->
                                cameraManager?.let { manager ->
                                    option.updateFunction(manager, selectedValue)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureRequestDropdown(
    option: com.elegia.pipcamera.camera.CaptureRequestOption,
    onValueSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Persist UI state using option key as stable identifier
    var selectedValue by remember(option.key) {
        mutableStateOf(option.currentValue ?: option.availableValues.firstOrNull()?.second)
    }

    // Sync UI with actual camera state when it changes
    LaunchedEffect(option.currentValue) {
        option.currentValue?.let { actualValue ->
            if (actualValue != selectedValue) {
                selectedValue = actualValue
            }
        }
    }

    // Debug logging
    LaunchedEffect(option) {
        android.util.Log.d(
            "CaptureRequestDropdown",
            "${option.displayName}: ${option.availableValues.size} values, current=$selectedValue"
        )
    }

    Column {
        Text(
            text = option.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = option.availableValues.find { it.second == selectedValue }?.first
                    ?: "Unknown",
                onValueChange = { },
                readOnly = true,
                label = { Text(option.key) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                option.availableValues.forEach { (displayName, value) ->
                    DropdownMenuItem(
                        text = { Text(displayName) },
                        onClick = {
                            selectedValue = value
                            expanded = false
                            onValueSelected(value)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}


