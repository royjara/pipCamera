package com.elegia.pipcamera.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
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
 * Organized into Input, Processor, and Sink tabs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDemoModal(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Input", "Processor", "Sink")

    // Configuration state
    var imageAnalysisEnabled by remember { mutableStateOf(false) }
    var sineWaveEnabled by remember { mutableStateOf(true) }
    var textMessageEnabled by remember { mutableStateOf(false) }
    var processingBypass by remember { mutableStateOf(false) }

    // Weka configuration state
    var selectedAlgorithm by remember { mutableStateOf("J48") }
    var dimensions by remember { mutableStateOf(10) }
    var learningRate by remember { mutableStateOf(0.1f) }

    // OSC configuration state
    var oscHost by remember { mutableStateOf("127.0.0.1") }
    var settingsApplied by remember { mutableStateOf(false) }
    var oscPort by remember { mutableStateOf(8000) }
    var oscAddress by remember { mutableStateOf("/audio/stream") }
    var encodingType by remember { mutableStateOf("Raw PCM") }

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

                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
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
                    when (selectedTabIndex) {
                        0 -> InputTab(
                            imageAnalysisEnabled = imageAnalysisEnabled,
                            sineWaveEnabled = sineWaveEnabled,
                            textMessageEnabled = textMessageEnabled,
                            onImageAnalysisToggle = { imageAnalysisEnabled = it },
                            onSineWaveToggle = { sineWaveEnabled = it },
                            onTextMessageToggle = { textMessageEnabled = it }
                        )
                        1 -> ProcessorTab(
                            selectedAlgorithm = selectedAlgorithm,
                            dimensions = dimensions,
                            learningRate = learningRate,
                            processingBypass = processingBypass,
                            onAlgorithmChange = { selectedAlgorithm = it },
                            onDimensionsChange = { dimensions = it },
                            onLearningRateChange = { learningRate = it },
                            onBypassToggle = { processingBypass = it }
                        )
                        2 -> SinkTab(
                            oscHost = oscHost,
                            oscPort = oscPort,
                            oscAddress = oscAddress,
                            encodingType = encodingType,
                            processingBypass = processingBypass,
                            settingsApplied = settingsApplied,
                            onHostChange = {
                                oscHost = it
                                settingsApplied = false  // Reset when settings change
                            },
                            onPortChange = {
                                oscPort = it
                                settingsApplied = false  // Reset when settings change
                            },
                            onAddressChange = { oscAddress = it },
                            onEncodingChange = { encodingType = it },
                            onApplySettings = {
                                // Apply OSC connection settings
                                // Store settings for next audio processing session
                                // In a real app, this would call audioProcessor.updateOSCDestination(oscHost, oscPort)
                                Log.i("AudioDemo", "Applying OSC settings: $oscHost:$oscPort address=$oscAddress")

                                // Set visual confirmation that settings have been applied
                                settingsApplied = true

                                // TODO: Connect to actual AudioProcessor instance
                                // The AudioProcessor is currently instantiated in ProcessingNode
                                // We would need to either:
                                // 1. Pass the AudioProcessor instance down to this UI
                                // 2. Use a shared preferences or ViewModel to store settings
                                // 3. Use an event bus to notify the audio processor
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputTab(
    imageAnalysisEnabled: Boolean,
    sineWaveEnabled: Boolean,
    textMessageEnabled: Boolean,
    onImageAnalysisToggle: (Boolean) -> Unit,
    onSineWaveToggle: (Boolean) -> Unit,
    onTextMessageToggle: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Input Sources",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Configure which input sources to include in the processing pipeline:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Image Analysis Row
        InputSourceRow(
            icon = Icons.Default.Search,
            title = "Image Analysis Frames",
            description = "Live camera frame analysis data",
            enabled = imageAnalysisEnabled,
            onToggle = onImageAnalysisToggle
        )

        // Sine Wave Row
        InputSourceRow(
            icon = Icons.Default.Star,
            title = "Sine Wave Generator",
            description = "440Hz synthetic audio signal",
            enabled = sineWaveEnabled,
            onToggle = onSineWaveToggle
        )

        // Text Message Row
        InputSourceRow(
            icon = Icons.Default.Email,
            title = "Text Message Stream",
            description = "ASCII text data transmission",
            enabled = textMessageEnabled,
            onToggle = onTextMessageToggle
        )
    }
}

@Composable
private fun InputSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcessorTab(
    selectedAlgorithm: String,
    dimensions: Int,
    learningRate: Float,
    processingBypass: Boolean,
    onAlgorithmChange: (String) -> Unit,
    onDimensionsChange: (Int) -> Unit,
    onLearningRateChange: (Float) -> Unit,
    onBypassToggle: (Boolean) -> Unit
) {
    val algorithms = listOf("J48", "RandomForest", "NaiveBayes", "SVM", "LinearRegression")
    var algorithmDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Weka ML Processor",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Bypass Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (processingBypass)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (processingBypass) Icons.Default.Close else Icons.Default.Check,
                    contentDescription = null,
                    tint = if (processingBypass)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Processing Bypass",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (processingBypass)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (processingBypass) "ML processing disabled" else "ML processing active",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (processingBypass)
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }

                Switch(
                    checked = processingBypass,
                    onCheckedChange = onBypassToggle
                )
            }
        }

        if (!processingBypass) {
            // Algorithm Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Algorithm",
                        style = MaterialTheme.typography.titleMedium
                    )

                    ExposedDropdownMenuBox(
                        expanded = algorithmDropdownExpanded,
                        onExpandedChange = { algorithmDropdownExpanded = !algorithmDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedAlgorithm,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algorithmDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            label = { Text("ML Algorithm") }
                        )

                        ExposedDropdownMenu(
                            expanded = algorithmDropdownExpanded,
                            onDismissRequest = { algorithmDropdownExpanded = false }
                        ) {
                            algorithms.forEach { algorithm ->
                                DropdownMenuItem(
                                    text = { Text(algorithm) },
                                    onClick = {
                                        onAlgorithmChange(algorithm)
                                        algorithmDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Dimensions Slider
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Feature Dimensions",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = dimensions.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = dimensions.toFloat(),
                        onValueChange = { onDimensionsChange(it.toInt()) },
                        valueRange = 1f..50f,
                        steps = 48
                    )
                }
            }

            // Learning Rate Slider
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Learning Rate",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = String.format("%.3f", learningRate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = learningRate,
                        onValueChange = onLearningRateChange,
                        valueRange = 0.001f..1.0f
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SinkTab(
    oscHost: String,
    oscPort: Int,
    oscAddress: String,
    encodingType: String,
    processingBypass: Boolean,
    settingsApplied: Boolean = false,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onAddressChange: (String) -> Unit,
    onEncodingChange: (String) -> Unit,
    onApplySettings: () -> Unit = {}
) {
    // State for each input type
    var audioScale by remember { mutableStateOf(1.0f) }
    var textMessage by remember { mutableStateOf("") }

    // Image analysis is only enabled when Weka processing is NOT bypassed
    val imageAnalysisEnabled = !processingBypass

    // Channel configurations
    var audioChannel by remember { mutableStateOf("/chan1/audio") }
    var textChannel by remember { mutableStateOf("/chan2/text") }
    var analysisChannel by remember { mutableStateOf("/chan3/analysis") }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "OSC Output Channels",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Configure TouchDesigner-style OSC channels for each input type:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Global OSC Configuration
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Global OSC Settings",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = oscHost,
                    onValueChange = onHostChange,
                    label = { Text("Host Address") },
                    leadingIcon = {
                        Icon(Icons.Default.Home, contentDescription = null)
                    },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = oscPort.toString(),
                    onValueChange = { newPort ->
                        newPort.toIntOrNull()?.let(onPortChange)
                    },
                    label = { Text("Port") },
                    leadingIcon = {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    },
                    placeholder = { Text("8000") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onApplySettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settingsApplied)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (settingsApplied) Icons.Default.Check else Icons.Default.Done,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (settingsApplied) "Settings Applied" else "Apply Changes")
                }

                Text(
                    text = if (settingsApplied)
                        "OSC settings have been applied successfully. New connections will use $oscHost:$oscPort."
                    else
                        "Click Apply Changes to update the OSC connection with new host/port settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settingsApplied)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Audio Channel Configuration
        AudioChannelConfig(
            channelAddress = audioChannel,
            scale = audioScale,
            oscHost = oscHost,
            oscPort = oscPort,
            onChannelChange = { audioChannel = it },
            onScaleChange = { audioScale = it }
        )

        // Text Channel Configuration
        TextChannelConfig(
            channelAddress = textChannel,
            message = textMessage,
            oscHost = oscHost,
            oscPort = oscPort,
            onChannelChange = { textChannel = it },
            onMessageChange = { textMessage = it }
        )

        // Image Analysis Channel Configuration
        ImageAnalysisChannelConfig(
            channelAddress = analysisChannel,
            enabled = imageAnalysisEnabled,
            onChannelChange = { analysisChannel = it }
        )

        // Connection Summary
        ConnectionSummary(
            oscHost = oscHost,
            oscPort = oscPort,
            audioChannel = audioChannel,
            textChannel = textChannel,
            analysisChannel = analysisChannel,
            imageAnalysisEnabled = imageAnalysisEnabled
        )
    }
}

@Composable
private fun AudioChannelConfig(
    channelAddress: String,
    scale: Float,
    oscHost: String,
    oscPort: Int,
    onChannelChange: (String) -> Unit,
    onScaleChange: (Float) -> Unit
) {
    var isStreaming by remember { mutableStateOf(false) }
    val sineProcessor = remember { SineGeneratorProcessor() }
    val lifecycleOwner = LocalLifecycleOwner.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Audio Stream (Raw PCM)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            OutlinedTextField(
                value = channelAddress,
                onValueChange = onChannelChange,
                label = { Text("Channel Address") },
                placeholder = { Text("/chan1/audio") },
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scale Factor",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = String.format("%.2f", scale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = scale,
                    onValueChange = onScaleChange,
                    valueRange = 0.1f..5.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Raw PCM format for minimal latency. Scale controls amplitude.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            // Start/Stop Streaming Button
            Button(
                onClick = {
                    isStreaming = !isStreaming
                    if (isStreaming) {
                        Log.i("AudioDemo", "Starting audio stream to $channelAddress via $oscHost:$oscPort")

                        // Configure the processor with current settings
                        sineProcessor.updateParameter("oscHost", oscHost)
                        sineProcessor.updateParameter("oscPort", oscPort)
                        sineProcessor.updateParameter("oscAddress", channelAddress)
                        sineProcessor.updateParameter("amplitude", scale)

                        // Initialize and start streaming
                        lifecycleOwner.lifecycleScope.launch {
                            val config = ProcessingNodeConfig(
                                sampleRate = 44100,
                                bufferSize = 512,
                                inletCount = 0,
                                outletCount = 1
                            )
                            if (sineProcessor.initialize(config)) {
                                sineProcessor.updateParameter("streamEnabled", true)
                                Log.i("AudioDemo", "Audio processor initialized and streaming started")
                            } else {
                                Log.e("AudioDemo", "Failed to initialize audio processor")
                                isStreaming = false
                            }
                        }
                    } else {
                        Log.i("AudioDemo", "Stopping audio stream")
                        sineProcessor.updateParameter("streamEnabled", false)
                        lifecycleOwner.lifecycleScope.launch {
                            sineProcessor.cleanup()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
            }
        }
    }
}

@Composable
private fun TextChannelConfig(
    channelAddress: String,
    message: String,
    oscHost: String,
    oscPort: Int,
    onChannelChange: (String) -> Unit,
    onMessageChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Text Messages (Manual)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            OutlinedTextField(
                value = channelAddress,
                onValueChange = onChannelChange,
                label = { Text("Channel Address") },
                placeholder = { Text("/chan2/text") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                label = { Text("Message to Send") },
                placeholder = { Text("Enter your message...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Button(
                onClick = {
                    if (message.isNotBlank()) {
                        Log.i("AudioDemo", "Sending text message: '$message' to $channelAddress via $oscHost:$oscPort")
                        sendOSCTextMessage(oscHost, oscPort, channelAddress, message)
                    }
                },
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Message")
            }

            Text(
                text = "Messages are sent manually when button is pressed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ImageAnalysisChannelConfig(
    channelAddress: String,
    enabled: Boolean,
    onChannelChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Image Analysis Features (Weka Required)",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = channelAddress,
                onValueChange = onChannelChange,
                label = { Text("Channel Address") },
                placeholder = { Text("/chan3/analysis") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Weka Processing Required",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Image analysis data can only be sent via OSC if Weka ML processing is enabled in the Processor tab. This ensures data is downscaled to a manageable feature vector.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionSummary(
    oscHost: String,
    oscPort: Int,
    audioChannel: String,
    textChannel: String,
    analysisChannel: String,
    imageAnalysisEnabled: Boolean
) {
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
                text = "Channel Summary",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Target: $oscHost:$oscPort",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Text("• Audio: $audioChannel (Raw PCM)", style = MaterialTheme.typography.bodySmall)
            Text("• Text: $textChannel (Manual trigger)", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "• Analysis: $analysisChannel ${if (imageAnalysisEnabled) "(Enabled)" else "(Weka Required)"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (imageAnalysisEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
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

/**
 * Simple OSC text message sender
 * Sends text messages in the format: "/channel/address message content"
 */
private fun sendOSCTextMessage(host: String, port: Int, channel: String, message: String) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            val address = InetAddress.getByName(host)

            // Create simple text-based OSC message (not full OSC protocol, but compatible with our receiver)
            val oscMessage = "$channel $message"
            val messageBytes = oscMessage.toByteArray()

            val packet = DatagramPacket(messageBytes, messageBytes.size, address, port)
            socket.send(packet)
            socket.close()

            Log.i("AudioDemo", "OSC message sent successfully: $oscMessage")
        } catch (e: Exception) {
            Log.e("AudioDemo", "Failed to send OSC message: ${e.message}", e)
        }
    }
}