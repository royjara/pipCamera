package com.elegia.pipcamera.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elegia.pipcamera.audio.*
import com.elegia.pipcamera.camera.CameraManager
import com.elegia.pipcamera.ui.pipelineMenus.*

/**
 * Unified Audio Demo - combines processing demo and modal functionality
 * Generates sine wave and sends via OSC with comprehensive configuration
 */
@Composable
fun AudioDemo(
    modifier: Modifier = Modifier,
    isModal: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    cameraManager: CameraManager? = null
) {
    val audioDemoManager = rememberAudioDemoManager()

    // Collect grouped state from manager
    val oscTabState by audioDemoManager.oscTabState.collectAsState()
    val audioTabState by audioDemoManager.audioTabState.collectAsState()
    val messagesTabState by audioDemoManager.messagesTabState.collectAsState()
    val imagesTabState by audioDemoManager.imagesTabState.collectAsState()
    val coreProcessingState by audioDemoManager.coreProcessingState.collectAsState()
    val uiState by audioDemoManager.uiState.collectAsState()

    val sineProcessor = audioDemoManager.getSineProcessor()

    if (isModal && onDismiss != null) {
        AudioDemoModalContent(
            audioDemoManager = audioDemoManager,
            onDismiss = onDismiss,
            cameraManager = cameraManager
        )
    } else {
        AudioDemoStandaloneContent(
            modifier = modifier,
            audioDemoManager = audioDemoManager,
            oscTabState = oscTabState,
            coreProcessingState = coreProcessingState
        )
    }
}

@Composable
private fun AudioDemoStandaloneContent(
    modifier: Modifier,
    audioDemoManager: AudioDemoManager,
    oscTabState: OSCTabState,
    coreProcessingState: CoreProcessingState
) {
    val sineProcessor = audioDemoManager.getSineProcessor()
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Audio Processing Pipeline",
            style = MaterialTheme.typography.headlineSmall
        )

        // Status indicator
        StatusIndicator(coreProcessingState.nodeState)

        // OSC Configuration
        OSCConfiguration(
            host = oscTabState.host,
            port = oscTabState.port,
            address = oscTabState.address,
            onHostChange = audioDemoManager::updateOscHost,
            onPortChange = audioDemoManager::updateOscPort,
            onAddressChange = audioDemoManager::updateOscAddress,
            onApplyChanges = audioDemoManager::applyOscSettings
        )

        // Frequency Control
        sineProcessor?.let { processor ->
            FrequencyControl(
                processor = processor,
                refreshTrigger = coreProcessingState.parameterRefreshTrigger
            )
        }

        // Stream Control Buttons
        StreamControlButtons(
            isStreamActive = coreProcessingState.isStreamActive,
            nodeState = coreProcessingState.nodeState,
            onStartClick = audioDemoManager::startStream,
            onStopClick = audioDemoManager::stopStream
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
                    text = "Sine Wave Generator",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Outlets: 1 | Inlets: 0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // The actual processing node
                sineProcessor?.let { processor ->
                    ProcessingNode(
                        inletCount = 0,
                        outletCount = 1,
                        processor = processor,
                        config = ProcessingNodeConfig(
                            sampleRate = 44100,
                            bufferSize = 512,
                            inletCount = 0,
                            outletCount = 1
                        ),
                        onStateChange = audioDemoManager::updateNodeState
                    )
                }
            }
        }

        // Processing information
        sineProcessor?.let { processor ->
            ProcessingInfo(processor, coreProcessingState.parameterRefreshTrigger)
        }

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

// Legacy wrapper for backwards compatibility
@Composable
fun AudioProcessingDemo(
    modifier: Modifier = Modifier
) {
    AudioDemo(modifier = modifier, isModal = false)
}

// Legacy wrapper for backwards compatibility
@Composable
fun AudioDemoModal(
    onDismiss: () -> Unit,
    cameraManager: CameraManager? = null,
    modifier: Modifier = Modifier
) {
    AudioDemo(
        modifier = modifier,
        isModal = true,
        onDismiss = onDismiss,
        cameraManager = cameraManager
    )
}

/**
 * Modal content for the unified Audio Demo
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioDemoModalContent(
    audioDemoManager: AudioDemoManager,
    onDismiss: () -> Unit,
    cameraManager: CameraManager?
) {
    // Collect grouped state from manager
    val oscTabState by audioDemoManager.oscTabState.collectAsState()
    val audioTabState by audioDemoManager.audioTabState.collectAsState()
    val messagesTabState by audioDemoManager.messagesTabState.collectAsState()
    val imagesTabState by audioDemoManager.imagesTabState.collectAsState()
    val coreProcessingState by audioDemoManager.coreProcessingState.collectAsState()
    val uiState by audioDemoManager.uiState.collectAsState()

    val sineProcessor = audioDemoManager.getSineProcessor()
    val tabs = listOf("OSC", "Audio", "Messages", "Images")

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
            modifier = Modifier
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

                // Tab Row
                TabRow(
                    selectedTabIndex = uiState.selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = uiState.selectedTabIndex == index,
                            onClick = { audioDemoManager.selectTab(index) },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (uiState.selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Tab Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (uiState.selectedTabIndex) {
                        0 -> {
                            // OSC Tab with integrated controls
                            OSCTabComponent(
                                oscHost = oscTabState.host,
                                oscPort = oscTabState.port,
                                oscAddress = oscTabState.address,
                                settingsApplied = oscTabState.settingsApplied,
                                onHostChange = audioDemoManager::updateOscHost,
                                onPortChange = audioDemoManager::updateOscPort,
                                onAddressChange = audioDemoManager::updateOscAddress,
                                onApplySettings = audioDemoManager::applyOscSettings
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Status and Stream Controls
                            StatusIndicator(coreProcessingState.nodeState)

                            Spacer(modifier = Modifier.height(16.dp))

                            StreamControlButtons(
                                isStreamActive = coreProcessingState.isStreamActive,
                                nodeState = coreProcessingState.nodeState,
                                onStartClick = audioDemoManager::startStream,
                                onStopClick = audioDemoManager::stopStream
                            )
                        }
                        1 -> {
                            // Audio Tab with frequency control
                            AudioTabComponent(
                                selectedAudioSource = audioTabState.selectedAudioSource,
                                microphoneGain = audioTabState.microphoneGain,
                                enableNoiseReduction = audioTabState.enableNoiseReduction,
                                customFrequency = audioTabState.customFrequency,
                                isAudioStreaming = coreProcessingState.isStreamActive,
                                oscHost = oscTabState.host,
                                oscPort = oscTabState.port,
                                oscAddress = oscTabState.address,
                                onAudioSourceChange = audioDemoManager::updateAudioSource,
                                onMicrophoneGainChange = audioDemoManager::updateMicrophoneGain,
                                onNoiseReductionToggle = { audioDemoManager.toggleNoiseReduction() },
                                onCustomFrequencyChange = audioDemoManager::updateCustomFrequency,
                                onStreamingToggle = { isActive -> if (isActive) audioDemoManager.startStream() else audioDemoManager.stopStream() }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Frequency Control
                            sineProcessor?.let { processor ->
                                FrequencyControl(
                                    processor = processor,
                                    refreshTrigger = coreProcessingState.parameterRefreshTrigger
                                )
                            }
                        }
                        2 -> MessagesTabComponent(
                            textMessageEnabled = messagesTabState.textMessageEnabled,
                            oscHost = oscTabState.host,
                            oscPort = oscTabState.port,
                            onTextMessageToggle = { audioDemoManager.toggleTextMessage() }
                        )
                        3 -> ImagesTabComponent(
                            imageAnalysisEnabled = cameraManager?.isAnalysisEnabled?.collectAsState()?.value ?: false,
                            onImageAnalysisToggle = { enabled ->
                                if (enabled) {
                                    cameraManager?.enableAnalysis()
                                } else {
                                    cameraManager?.disableAnalysis()
                                }
                            },
                            cameraManager = cameraManager
                        )
                    }
                }
            }
        }
    }
}

// Removed unused InputTab - functionality moved to individual tab components

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

