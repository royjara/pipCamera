package com.elegia.pipcamera.ui

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.elegia.pipcamera.camera.CameraCapabilities
import com.elegia.pipcamera.camera.CameraMetering
import com.elegia.pipcamera.camera.CaptureController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraControls(
    capabilities: CameraCapabilities?,
    currentMetering: CameraMetering?,
    isPiPMode: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }

    if (!isPiPMode && capabilities != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { showDialog = true },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Camera Settings"
                )
            }
        }

        if (showDialog) {
            CameraSettingsDialog(
                capabilities = capabilities,
                currentMetering = currentMetering,
                onDismiss = { showDialog = false }
            )
        }
    }
}

@Composable
private fun CameraSettingsDialog(
    capabilities: CameraCapabilities,
    currentMetering: CameraMetering?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Camera Settings",
                    style = MaterialTheme.typography.headlineSmall
                )

                // Focus Mode Control
                FocusModeControl(capabilities, currentMetering)

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusModeControl(
    capabilities: CameraCapabilities,
    currentMetering: CameraMetering?
) {
    // Use actual current focus mode from capture results, with fallback to first supported mode
    val actualFocusMode = currentMetering?.focusMode ?: capabilities.supportedFocusModes.firstOrNull() ?: CaptureRequest.CONTROL_AF_MODE_AUTO

    var selectedFocusMode by remember(actualFocusMode) {
        mutableIntStateOf(actualFocusMode)
    }
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Focus Mode",
            style = MaterialTheme.typography.titleMedium
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = capabilities.getFocusModeDisplayName(selectedFocusMode),
                onValueChange = { },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                capabilities.supportedFocusModes.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(text = capabilities.getFocusModeDisplayName(mode))
                        },
                        onClick = {
                            selectedFocusMode = mode
                            expanded = false
                            // Update camera setting
                            CaptureController.updateFocusMode(mode)
                        }
                    )
                }
            }
        }
    }
}