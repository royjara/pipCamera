package com.elegia.pipcamera.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            onHostChange = { host ->
                oscHost = host
                sineProcessor.updateParameter("oscHost", host)
            },
            onPortChange = { port ->
                oscPort = port
                sineProcessor.updateParameter("oscPort", port)
            },
            onAddressChange = { address ->
                oscAddress = address
                sineProcessor.updateParameter("oscAddress", address)
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
        ProcessingInfo(sineProcessor)
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
    onAddressChange: (String) -> Unit
) {
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
        }
    }
}

@Composable
private fun ProcessingInfo(processor: SineGeneratorProcessor) {
    val parameters = processor.getParameters()

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
        Card(
            modifier = modifier
                .fillMaxSize()
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

                // Audio demo content
                AudioProcessingDemo(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}