package com.elegia.pipcamera.ui.pipelineMenus

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elegia.pipcamera.osc.OSCStateHolder

/**
 * OSC Configuration Tab - First tab for OSC settings
 */
@Composable
fun OSCTabComponent(
    oscHost: String,
    oscPort: Int,
    oscAddress: String,
    settingsApplied: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onAddressChange: (String) -> Unit,
    onApplySettings: () -> Unit
) {
    val context = LocalContext.current
    val oscStateHolder = remember { OSCStateHolder.getInstance(context) }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Configure Open Sound Control network destination:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Host Field
                OutlinedTextField(
                    value = oscHost,
                    onValueChange = onHostChange,
                    label = { Text("Host Address") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                    }
                )

                // Port Field
                OutlinedTextField(
                    value = oscPort.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { port ->
                            if (port in 1..65535) onPortChange(port)
                        }
                    },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                    }
                )

                               // Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Apply Button
                    Button(
                        onClick = onApplySettings,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settingsApplied)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (settingsApplied) Icons.Default.CheckCircle else Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (settingsApplied) "Applied" else "Apply",
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Test Connection Button
                    Button(
                        onClick = {
                            oscStateHolder.sendTestMessage()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Test",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

