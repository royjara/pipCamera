package com.elegia.pipcamera.ui.pipelineMenus

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.elegia.pipcamera.audio.*
import kotlinx.coroutines.launch

/**
 * Audio source selection options
 */
enum class AudioSource(val displayName: String, val description: String) {
    SINE_WAVE_440HZ("440Hz Demo Tone", "Synthetic sine wave for testing"),
    MICROPHONE("Microphone Input", "Live audio from device microphone"),
    SINE_WAVE_CUSTOM("Custom Frequency", "Configurable sine wave generator")
}

/**
 * Audio Configuration Tab - Second tab for audio source and processing
 */
@Composable
fun AudioTabComponent(
    selectedAudioSource: AudioSource,
    microphoneGain: Float,
    enableNoiseReduction: Boolean,
    customFrequency: Float,
    isAudioStreaming: Boolean,
    oscHost: String,
    oscPort: Int,
    oscAddress: String,
    onAudioSourceChange: (AudioSource) -> Unit,
    onMicrophoneGainChange: (Float) -> Unit,
    onNoiseReductionToggle: (Boolean) -> Unit,
    onCustomFrequencyChange: (Float) -> Unit,
    onStreamingToggle: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Audio Configuration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Configure audio source and streaming controls:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Audio Source Selection
        AudioSourceConfigCard(
            selectedSource = selectedAudioSource,
            microphoneGain = microphoneGain,
            enableNoiseReduction = enableNoiseReduction,
            customFrequency = customFrequency,
            onSourceChange = onAudioSourceChange,
            onGainChange = onMicrophoneGainChange,
            onNoiseReductionToggle = onNoiseReductionToggle,
            onCustomFrequencyChange = onCustomFrequencyChange
        )

        // Streaming Control Card
        AudioStreamingControlCard(
            isStreaming = isAudioStreaming,
            selectedAudioSource = selectedAudioSource,
            oscHost = oscHost,
            oscPort = oscPort,
            oscAddress = oscAddress,
            customFrequency = customFrequency,
            microphoneGain = microphoneGain,
            enableNoiseReduction = enableNoiseReduction,
            onStreamingToggle = onStreamingToggle
        )
    }
}

/**
 * Audio Source Configuration Card with dropdown selection and context-sensitive controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSourceConfigCard(
    selectedSource: AudioSource,
    microphoneGain: Float,
    enableNoiseReduction: Boolean,
    customFrequency: Float,
    onSourceChange: (AudioSource) -> Unit,
    onGainChange: (Float) -> Unit,
    onNoiseReductionToggle: (Boolean) -> Unit,
    onCustomFrequencyChange: (Float) -> Unit
) {
    var sourceDropdownExpanded by remember { mutableStateOf(false) }

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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (selectedSource) {
                        AudioSource.SINE_WAVE_440HZ -> Icons.Default.Star
                        AudioSource.MICROPHONE -> Icons.Default.Phone
                        AudioSource.SINE_WAVE_CUSTOM -> Icons.Default.Settings
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Audio Source Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Source Selection Dropdown
            ExposedDropdownMenuBox(
                expanded = sourceDropdownExpanded,
                onExpandedChange = { sourceDropdownExpanded = !sourceDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSource.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Audio Source") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                ExposedDropdownMenu(
                    expanded = sourceDropdownExpanded,
                    onDismissRequest = { sourceDropdownExpanded = false }
                ) {
                    AudioSource.values().forEach { source ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = source.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = source.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onSourceChange(source)
                                sourceDropdownExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (source) {
                                        AudioSource.SINE_WAVE_440HZ -> Icons.Default.Star
                                        AudioSource.MICROPHONE -> Icons.Default.Phone
                                        AudioSource.SINE_WAVE_CUSTOM -> Icons.Default.Settings
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }

            // Source-specific controls
            when (selectedSource) {
                AudioSource.MICROPHONE -> {
                    MicrophoneControls(
                        gain = microphoneGain,
                        enableNoiseReduction = enableNoiseReduction,
                        onGainChange = onGainChange,
                        onNoiseReductionToggle = onNoiseReductionToggle
                    )
                }
                AudioSource.SINE_WAVE_440HZ -> {
                    SineWaveInfo(frequency = 440f, isCustom = false)
                }
                AudioSource.SINE_WAVE_CUSTOM -> {
                    CustomSineWaveControls(
                        frequency = customFrequency,
                        onFrequencyChange = onCustomFrequencyChange
                    )
                }
            }

            // Status indicator
            AudioSourceStatus(selectedSource = selectedSource)
        }
    }
}

/**
 * Audio Streaming Control Card - Toggle streaming on/off with status
 */
@Composable
private fun AudioStreamingControlCard(
    isStreaming: Boolean,
    selectedAudioSource: AudioSource,
    oscHost: String,
    oscPort: Int,
    oscAddress: String,
    customFrequency: Float,
    microphoneGain: Float,
    enableNoiseReduction: Boolean,
    onStreamingToggle: (Boolean) -> Unit
) {
    val sineProcessor = remember { SineGeneratorProcessor() }
    val microphoneProcessor = remember { MicrophoneProcessor() }
    val lifecycleOwner = LocalLifecycleOwner.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isStreaming)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.PlayArrow else Icons.Default.Settings,
                    contentDescription = null,
                    tint = if (isStreaming)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio Streaming Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isStreaming) "Currently streaming to $oscHost:$oscPort" else "Ready to stream",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Information
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isStreaming)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Current Configuration:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "• Source: ${selectedAudioSource.displayName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Destination: $oscHost:$oscPort",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Channel: $oscAddress",
                        style = MaterialTheme.typography.bodySmall
                    )
                    when (selectedAudioSource) {
                        AudioSource.SINE_WAVE_440HZ -> {
                            Text(
                                text = "• Frequency: 440Hz",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        AudioSource.SINE_WAVE_CUSTOM -> {
                            Text(
                                text = "• Frequency: ${customFrequency.toInt()}Hz",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        AudioSource.MICROPHONE -> {
                            Text(
                                text = "• Gain: ${String.format("%.1f", microphoneGain)}x, Noise Reduction: ${if (enableNoiseReduction) "On" else "Off"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Toggle Button
            Button(
                onClick = {
                    val newStreamingState = !isStreaming
                    onStreamingToggle(newStreamingState)

                    if (newStreamingState) {
                        // Start streaming
                        when (selectedAudioSource) {
                            AudioSource.SINE_WAVE_440HZ, AudioSource.SINE_WAVE_CUSTOM -> {
                                val frequency = if (selectedAudioSource == AudioSource.SINE_WAVE_440HZ) 440f else customFrequency
                                Log.i("AudioTab", "Starting ${frequency}Hz sine wave stream to $oscAddress via $oscHost:$oscPort")
                                sineProcessor.updateParameter("oscHost", oscHost)
                                sineProcessor.updateParameter("oscPort", oscPort)
                                sineProcessor.updateParameter("oscAddress", oscAddress)
                                sineProcessor.updateParameter("frequency", frequency)

                                lifecycleOwner.lifecycleScope.launch {
                                    val config = ProcessingNodeConfig(
                                        sampleRate = 44100,
                                        bufferSize = 512,
                                        inletCount = 0,
                                        outletCount = 1
                                    )
                                    if (sineProcessor.initialize(config)) {
                                        sineProcessor.updateParameter("streamEnabled", true)
                                        Log.i("AudioTab", "Sine processor initialized and streaming started")
                                    } else {
                                        Log.e("AudioTab", "Failed to initialize sine processor")
                                        onStreamingToggle(false)
                                    }
                                }
                            }
                            AudioSource.MICROPHONE -> {
                                Log.i("AudioTab", "Starting microphone stream to $oscAddress via $oscHost:$oscPort")
                                microphoneProcessor.updateParameter("oscHost", oscHost)
                                microphoneProcessor.updateParameter("oscPort", oscPort)
                                microphoneProcessor.updateParameter("oscAddress", oscAddress)
                                microphoneProcessor.updateParameter("gain", microphoneGain)
                                microphoneProcessor.updateParameter("noiseReduction", enableNoiseReduction)

                                lifecycleOwner.lifecycleScope.launch {
                                    val config = ProcessingNodeConfig(
                                        sampleRate = 44100,
                                        bufferSize = 512,
                                        inletCount = 0,
                                        outletCount = 1
                                    )
                                    if (microphoneProcessor.initialize(config)) {
                                        microphoneProcessor.updateParameter("streamEnabled", true)
                                        Log.i("AudioTab", "Microphone processor initialized and streaming started")
                                    } else {
                                        Log.e("AudioTab", "Failed to initialize microphone processor")
                                        onStreamingToggle(false)
                                    }
                                }
                            }
                        }
                    } else {
                        // Stop streaming
                        Log.i("AudioTab", "Stopping audio stream")
                        when (selectedAudioSource) {
                            AudioSource.SINE_WAVE_440HZ, AudioSource.SINE_WAVE_CUSTOM -> {
                                sineProcessor.updateParameter("streamEnabled", false)
                            }
                            AudioSource.MICROPHONE -> {
                                microphoneProcessor.updateParameter("streamEnabled", false)
                            }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Text(
                        text = if (isStreaming) "Stop Streaming" else "Start Streaming",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Include the existing helper components from AudioDemo.kt
@Composable
private fun MicrophoneControls(
    gain: Float,
    enableNoiseReduction: Boolean,
    onGainChange: (Float) -> Unit,
    onNoiseReductionToggle: (Boolean) -> Unit
) {
    // Same implementation as in AudioDemo.kt
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Microphone gain controls
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Input Gain",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${String.format("%.1f", gain)}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = gain,
                    onValueChange = onGainChange,
                    valueRange = 0.1f..5.0f,
                    steps = 48,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Noise reduction toggle
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (enableNoiseReduction)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Noise Reduction",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                Switch(
                    checked = enableNoiseReduction,
                    onCheckedChange = onNoiseReductionToggle
                )
            }
        }
    }
}

@Composable
private fun CustomSineWaveControls(
    frequency: Float,
    onFrequencyChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Custom Frequency Generator",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Frequency",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${frequency.toInt()}Hz",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = frequency,
                    onValueChange = onFrequencyChange,
                    valueRange = 20f..20000f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SineWaveInfo(frequency: Float, isCustom: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCustom) "Custom Sine Wave" else "440Hz Demo Tone",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "${frequency}Hz pure sine wave",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Perfect for testing audio pipeline connectivity and latency",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AudioSourceStatus(selectedSource: AudioSource) {
    val (statusColor, statusIcon, statusText) = when (selectedSource) {
        AudioSource.SINE_WAVE_440HZ -> Triple(
            MaterialTheme.colorScheme.primary,
            Icons.Default.Check,
            "Ready for testing"
        )
        AudioSource.MICROPHONE -> Triple(
            MaterialTheme.colorScheme.secondary,
            Icons.Default.Phone,
            "Live audio input"
        )
        AudioSource.SINE_WAVE_CUSTOM -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Settings,
            "Custom frequency"
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}