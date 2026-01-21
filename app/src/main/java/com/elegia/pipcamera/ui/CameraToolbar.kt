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
    cameraManager: CameraManager? = null
) {
    var showDebugScreen by remember { mutableStateOf(false) }
    var showMenuPopup by remember { mutableStateOf(false) }
    var showPipelineMenu by remember {mutableStateOf(false)}

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left button - Menu
                    Box(modifier = Modifier.weight(1f)) {
                        FloatingActionButton(
                            onClick = { showMenuPopup = !showMenuPopup },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
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
                                cameraManager = cameraManager,
                                onDismiss = { showMenuPopup = false }
                            )
                        }
                    }

                    // Middle button - Settings/Controls
                    Box(modifier = Modifier.weight(1f)) {
                        FloatingActionButton(
                            onClick = { showPipelineMenu = ! showPipelineMenu },
                            modifier = Modifier.fillMaxWidth()
//                                .weight(1f)
                                .height(48.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(20.dp)
                            )
                            if( showPipelineMenu) {
                                AudioDemoModal(
                                    onDismiss = {showPipelineMenu = false},
                                    cameraManager = cameraManager
                                )
                            }
                        }
                    }

                    // Right button - Debug toggle
                    FloatingActionButton(
                        onClick = { showDebugScreen = !showDebugScreen },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
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

        // Debug screen popup - same styling as menu popup
        if (showDebugScreen) {
            DebugPopup(
                currentMetering = currentMetering,
                onDismiss = { showDebugScreen = false }
            )
        }
    }
}

@Composable
private fun DebugPopup(
    currentMetering: CameraMetering?,
    onDismiss: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
//                .width(320.dp)

                .heightIn(max = 500.dp)
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Text(
                    text = "DEBUG - Capture Results",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    currentMetering?.let { metering ->
                        // Show all dynamic capture keys from reflection
                        metering.allCaptureKeys.forEach { (keyName, value) ->
                            item {
                                CaptureKeyValueRow(keyName, value)
                            }
                        }

                        // If no keys found, show fallback message
                        if (metering.allCaptureKeys.isEmpty()) {
                            item {
                                Text(
                                    text = "No capture result keys available",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } ?: item {
                        Text(
                            text = "No capture result data available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
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
    val captureOptions = remember { introspection.getAllCaptureRequestOptions() }

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

                items(captureOptions.size) { index ->
                    val option = captureOptions[index]
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
            modifier = Modifier.weight(4f)
        )
        Box(modifier = Modifier.weight(3f)){
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

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
    var selectedValue by remember {
        mutableStateOf(option.currentValue ?: option.availableValues.firstOrNull()?.second)
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
                value = option.availableValues.find { it.second == selectedValue }?.first ?: "Unknown",
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


@Preview
@Composable
fun DebugPopup(
//    onDismiss: () -> Unit
) {
    Popup(
        onDismissRequest = { },
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 500.dp)
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Text(
                    text = "DEBUG - Capture Results",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    currentMetering?.let { metering ->
                        // Show all dynamic capture keys from reflection

                        item {
                            Text(
                                text = "No capture result keys available",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } ?: item {
                        Text(
                            text = "No capture result data available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}