package com.elegia.pipcamera.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.elegia.pipcamera.pipeline.examples.*
import com.elegia.pipcamera.pipeline.nodes.*
import kotlinx.coroutines.launch

/**
 * Advanced media processing pipeline demonstration UI
 * Showcases: Hardware acceleration, ML processing, Real-time streaming
 */
@Composable
fun MediaPipelineDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPipelineType by remember { mutableStateOf(PipelineType.ADVANCED) }
    var isRunning by remember { mutableStateOf(false) }
    var streamingHost by remember { mutableStateOf("192.168.1.100") }
    var streamingPort by remember { mutableStateOf(8000) }
    var enableMLProcessing by remember { mutableStateOf(true) }
    var enableHardwareEncoding by remember { mutableStateOf(true) }
    var enableSequencing by remember { mutableStateOf(true) }

    // Pipeline instances
    val advancedPipeline = remember { AdvancedAudioPipeline(context) }
    val simplePipeline = remember { SimpleAudioPipeline(context) }
    val researchPipeline = remember { MLAudioResearchPipeline(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Media Pipeline Framework",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Hardware-accelerated audio processing with ML transformation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Pipeline Type Selection
        PipelineTypeSelector(
            selectedType = selectedPipelineType,
            onTypeSelected = { selectedPipelineType = it },
            isRunning = isRunning
        )

        // Configuration Panel
        ConfigurationPanel(
            pipelineType = selectedPipelineType,
            streamingHost = streamingHost,
            streamingPort = streamingPort,
            enableMLProcessing = enableMLProcessing,
            enableHardwareEncoding = enableHardwareEncoding,
            enableSequencing = enableSequencing,
            onHostChange = { streamingHost = it },
            onPortChange = { streamingPort = it },
            onMLProcessingChange = { enableMLProcessing = it },
            onHardwareEncodingChange = { enableHardwareEncoding = it },
            onSequencingChange = { enableSequencing = it },
            isRunning = isRunning
        )

        // Control Panel
        ControlPanel(
            isRunning = isRunning,
            pipelineType = selectedPipelineType,
            onStart = {
                scope.launch {
                    val success = when (selectedPipelineType) {
                        PipelineType.ADVANCED -> {
                            advancedPipeline.configurePipeline(
                                targetHost = streamingHost,
                                targetPort = streamingPort,
                                enableMLProcessing = enableMLProcessing,
                                enableHardwareEncoding = enableHardwareEncoding
                            ) && advancedPipeline.start()
                        }
                        PipelineType.SIMPLE -> {
                            simplePipeline.configureBasicPipeline(streamingHost, streamingPort) &&
                            simplePipeline.start()
                        }
                        PipelineType.ML_RESEARCH -> {
                            researchPipeline.configureResearchPipeline() &&
                            researchPipeline.start()
                        }
                    }
                    if (success) {
                        isRunning = true
                    }
                }
            },
            onStop = {
                scope.launch {
                    when (selectedPipelineType) {
                        PipelineType.ADVANCED -> advancedPipeline.stop()
                        PipelineType.SIMPLE -> simplePipeline.stop()
                        PipelineType.ML_RESEARCH -> researchPipeline.stop()
                    }
                    isRunning = false
                }
            }
        )

        // Performance Monitor
        PerformanceMonitor(isRunning = isRunning)

        // Usage Instructions
        UsageInstructions(pipelineType = selectedPipelineType)
    }
}

enum class PipelineType(val displayName: String, val description: String) {
    ADVANCED("Advanced Pipeline", "Microphone → ML → Hardware Encoder → UDP Stream"),
    SIMPLE("Simple Pipeline", "Microphone → UDP Stream (Low Latency)"),
    ML_RESEARCH("ML Research", "Multi-path ML Processing & Analysis")
}

@Composable
private fun PipelineTypeSelector(
    selectedType: PipelineType,
    onTypeSelected: (PipelineType) -> Unit,
    isRunning: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pipeline Configuration",
                style = MaterialTheme.typography.titleMedium
            )

            PipelineType.values().forEach { pipelineType ->
                FilterChip(
                    onClick = { if (!isRunning) onTypeSelected(pipelineType) },
                    label = { Text(pipelineType.displayName) },
                    selected = selectedType == pipelineType,
                    enabled = !isRunning,
                    leadingIcon = {
                        when (pipelineType) {
                            PipelineType.ADVANCED -> Icon(Icons.Default.Build, contentDescription = null)
                            PipelineType.SIMPLE -> Icon(Icons.Default.Star, contentDescription = null)
                            PipelineType.ML_RESEARCH -> Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = selectedType.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfigurationPanel(
    pipelineType: PipelineType,
    streamingHost: String,
    streamingPort: Int,
    enableMLProcessing: Boolean,
    enableHardwareEncoding: Boolean,
    enableSequencing: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onMLProcessingChange: (Boolean) -> Unit,
    onHardwareEncodingChange: (Boolean) -> Unit,
    onSequencingChange: (Boolean) -> Unit,
    isRunning: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium
            )

            // Streaming Configuration
            OutlinedTextField(
                value = streamingHost,
                onValueChange = onHostChange,
                label = { Text("Stream Host") },
                enabled = !isRunning,
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = streamingPort.toString(),
                onValueChange = { it.toIntOrNull()?.let(onPortChange) },
                label = { Text("Stream Port") },
                enabled = !isRunning,
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            // Feature Toggles
            if (pipelineType == PipelineType.ADVANCED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ML Processing")
                    Switch(
                        checked = enableMLProcessing,
                        onCheckedChange = onMLProcessingChange,
                        enabled = !isRunning
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hardware Encoding")
                    Switch(
                        checked = enableHardwareEncoding,
                        onCheckedChange = onHardwareEncodingChange,
                        enabled = !isRunning
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Packet Sequencing")
                Switch(
                    checked = enableSequencing,
                    onCheckedChange = onSequencingChange,
                    enabled = !isRunning
                )
            }
        }
    }
}

@Composable
private fun ControlPanel(
    isRunning: Boolean,
    pipelineType: PipelineType,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pipeline Control",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Pipeline")
                }

                Button(
                    onClick = onStop,
                    enabled = isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Pipeline")
                }
            }

            // Status Indicator
            val statusColor = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            val statusText = if (isRunning) "Running" else "Stopped"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Check else Icons.Default.Clear,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Status: $statusText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun PerformanceMonitor(isRunning: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Performance Monitor",
                style = MaterialTheme.typography.titleMedium
            )

            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Real-time audio processing active...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Pipeline not running",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UsageInstructions(pipelineType: PipelineType) {
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
                text = "Usage Instructions",
                style = MaterialTheme.typography.titleMedium
            )

            when (pipelineType) {
                PipelineType.ADVANCED -> {
                    Text("• Configure streaming destination", style = MaterialTheme.typography.bodySmall)
                    Text("• Enable ML processing for audio enhancement", style = MaterialTheme.typography.bodySmall)
                    Text("• Hardware encoding reduces bandwidth usage", style = MaterialTheme.typography.bodySmall)
                    Text("• Start pipeline and speak into microphone", style = MaterialTheme.typography.bodySmall)
                }
                PipelineType.SIMPLE -> {
                    Text("• Minimal latency raw audio streaming", style = MaterialTheme.typography.bodySmall)
                    Text("• No processing overhead", style = MaterialTheme.typography.bodySmall)
                    Text("• Best for real-time communication", style = MaterialTheme.typography.bodySmall)
                }
                PipelineType.ML_RESEARCH -> {
                    Text("• Multi-output ML analysis on ports 8001-8003", style = MaterialTheme.typography.bodySmall)
                    Text("• Audio enhancement, frequency analysis, emotion detection", style = MaterialTheme.typography.bodySmall)
                    Text("• Higher quality processing with increased latency", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}