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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

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
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "OSC Configuration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

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

                // Address Field
                OutlinedTextField(
                    value = oscAddress,
                    onValueChange = onAddressChange,
                    label = { Text("OSC Address") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
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
                            Log.i("OSCTab", "Testing OSC connection to $oscHost:$oscPort")
                            sendOSCTestMessage(oscHost, oscPort, "/test", "connection_test")
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

/**
 * Simple OSC test message sender
 * Sends test messages in the format: "/channel/address message content"
 */
private fun sendOSCTestMessage(host: String, port: Int, channel: String, message: String) {
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

            Log.i("OSCTab", "OSC test message sent successfully: $oscMessage")
        } catch (e: Exception) {
            Log.e("OSCTab", "Failed to send OSC test message: ${e.message}", e)
        }
    }
}