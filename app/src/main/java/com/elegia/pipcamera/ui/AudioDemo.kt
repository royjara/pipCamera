package com.elegia.pipcamera.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elegia.pipcamera.audio.*

/**
 * Demo Composable showcasing the audio processing pipeline
 * Generates 440Hz sine wave and sends via OSC
 */
@Composable
fun AudioProcessingDemo(
    modifier: Modifier = Modifier
) {
    var nodeState by remember { mutableStateOf(ProcessingNodeState.IDLE) }
    var oscHost by remember { mutableStateOf("127.0.0.1") }
    var oscPort by remember { mutableStateOf(8000) }
    var oscAddress by remember { mutableStateOf("/audio/stream") }
    var isStreamActive by remember { mutableStateOf(false) }
    var parameterRefreshTrigger by remember { mutableStateOf(0) }

    // Create sine generator processor
    val sineProcessor = remember { SineGeneratorProcessor() }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Audio Processing Pipeline Demo",
            style = MaterialTheme.typography.headlineSmall
        )

        // Status indicator
        StatusIndicator(nodeState)

        // OSC Configuration
        OSCConfiguration(
            host = oscHost,
            port = oscPort,
            address = oscAddress,
            onHostChange = { oscHost = it },
            onPortChange = { oscPort = it },
            onAddressChange = { oscAddress = it },
            onApplyChanges = {
                sineProcessor.updateParameter("oscHost", oscHost)
                sineProcessor.updateParameter("oscPort", oscPort)
                sineProcessor.updateParameter("oscAddress", oscAddress)
                parameterRefreshTrigger++ // Trigger parameter UI refresh
            }
        )

        // Frequency Control
        FrequencyControl(
            processor = sineProcessor,
            refreshTrigger = parameterRefreshTrigger
        )

        // Stream Control Buttons
        StreamControlButtons(
            isStreamActive = isStreamActive,
            nodeState = nodeState,
            onStartClick = {
                isStreamActive = true
                sineProcessor.updateParameter("streamEnabled", true)
            },
            onStopClick = {
                isStreamActive = false
                sineProcessor.updateParameter("streamEnabled", false)
            }
        )

        // Audio Processing Node
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sine Wave Generator (440Hz)",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Outlets: 1 | Inlets: 0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // The actual processing node
                ProcessingNode(
                    inletCount = 0,
                    outletCount = 1,
                    processor = sineProcessor,
                    config = ProcessingNodeConfig(
                        sampleRate = 44100,
                        bufferSize = 512,
                        inletCount = 0,
                        outletCount = 1
                    ),
                    onStateChange = { state ->
                        nodeState = state
                    }
                )
            }
        }

        // Processing information
        ProcessingInfo(sineProcessor, parameterRefreshTrigger)

        // Instructions for testing
        TestingInstructions()
    }
}

@Composable
private fun StatusIndicator(state: ProcessingNodeState) {
    val (color, text) = when (state) {
        ProcessingNodeState.IDLE -> MaterialTheme.colorScheme.surface to "Idle"
        ProcessingNodeState.INITIALIZING -> MaterialTheme.colorScheme.primary to "Initializing..."
        ProcessingNodeState.READY -> MaterialTheme.colorScheme.tertiary to "Ready"
        ProcessingNodeState.PROCESSING -> MaterialTheme.colorScheme.secondary to "Processing"
        ProcessingNodeState.ERROR -> MaterialTheme.colorScheme.error to "Error"
        ProcessingNodeState.CLEANUP -> MaterialTheme.colorScheme.outline to "Cleanup"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Status: $text",
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun OSCConfiguration(
    host: String,
    port: Int,
    address: String,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onAddressChange: (String) -> Unit,
    onApplyChanges: () -> Unit
) {
    var pendingChanges by remember { mutableStateOf(false) }
    val originalHost = remember(pendingChanges) { host }
    val originalPort = remember(pendingChanges) { port }
    val originalAddress = remember(pendingChanges) { address }

    // Check if there are unsaved changes
    val hasChanges = host != originalHost || port != originalPort || address != originalAddress
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "OSC Configuration",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Host") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = port.toString(),
                onValueChange = { newPort ->
                    newPort.toIntOrNull()?.let(onPortChange)
                },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text("OSC Address/Topic") },
                placeholder = { Text("/audio/stream") },
                modifier = Modifier.fillMaxWidth()
            )

            // Apply button for OSC changes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onApplyChanges()
                        pendingChanges = !pendingChanges // Trigger remember refresh
                    },
                    enabled = hasChanges,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasChanges) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                ) {
                    Text(
                        text = if (hasChanges) "Apply OSC Configuration" else "Configuration Applied",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingInfo(processor: SineGeneratorProcessor, refreshTrigger: Int) {
    // Refresh parameters when trigger changes (clean approach)
    val parameters = remember(refreshTrigger) { processor.getParameters() }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Processor Parameters",
                style = MaterialTheme.typography.titleMedium
            )

            parameters.forEach { (name, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Modal dialog wrapper for the Audio Processing Demo
 * Only processes audio while the modal is open
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDemoModal(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val modalHeight = screenHeight * 0.7f

        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(modalHeight)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audio Processing Pipeline",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Audio demo content - scrollable
                AudioProcessingDemo(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun StreamControlButtons(
    isStreamActive: Boolean,
    nodeState: ProcessingNodeState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Stream Control",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    enabled = (nodeState == ProcessingNodeState.READY || nodeState == ProcessingNodeState.PROCESSING) && !isStreamActive,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Stream")
                }

                Button(
                    onClick = onStopClick,
                    enabled = isStreamActive,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Stream")
                }
            }

            // Stream status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stream Status:",
                    style = MaterialTheme.typography.bodyMedium
                )

                val (statusText, statusColor) = when {
                    nodeState == ProcessingNodeState.ERROR -> "Error" to MaterialTheme.colorScheme.error
                    nodeState == ProcessingNodeState.INITIALIZING -> "Initializing" to MaterialTheme.colorScheme.outline
                    nodeState == ProcessingNodeState.IDLE -> "Not Ready" to MaterialTheme.colorScheme.outline
                    isStreamActive -> "Active" to MaterialTheme.colorScheme.primary
                    else -> "Ready" to MaterialTheme.colorScheme.tertiary
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun TestingInstructions() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Testing Instructions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "1. Run the OSC receiver on your Mac: ./osc_audio_receiver",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "2. Set OSC Host to your Mac's IP address (check receiver output)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "3. Click 'Apply OSC Configuration' to save settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "4. Click 'Start Stream' to begin audio transmission",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FrequencyControl(
    processor: SineGeneratorProcessor,
    refreshTrigger: Int
) {
    // Get current frequency parameters
    val parameters = remember(refreshTrigger) { processor.getParameters() }
    val currentIndex = parameters["frequencyIndex"] as? Int ?: 5 // Default to A4
    val currentFreq = parameters["frequency"] as? Float ?: 440.0f

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Frequency Control",
                style = MaterialTheme.typography.titleMedium
            )

            // Current note display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Note:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${SineGeneratorProcessor.NOTE_NAMES.getOrNull(currentIndex) ?: "A4"} (${String.format("%.1f", currentFreq)} Hz)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Frequency slider
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { value ->
                        processor.updateParameter("frequencyIndex", value.toInt())
                    },
                    valueRange = 0f..(SineGeneratorProcessor.C_MAJOR_FREQUENCIES.size - 1).toFloat(),
                    steps = SineGeneratorProcessor.C_MAJOR_FREQUENCIES.size - 2 // steps between min and max
                )

                // Scale labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SineGeneratorProcessor.NOTE_NAMES.forEach { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}