package com.elegia.pipcamera.ui.pipelineMenus

import android.util.Log
import androidx.compose.foundation.layout.*
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
 * Messages Tab - Third tab for text messages
 */
@Composable
fun MessagesTabComponent(
    textMessageEnabled: Boolean,
    oscHost: String,
    oscPort: Int,
    onTextMessageToggle: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Text Messages",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Configure text message streaming over OSC and send manual messages:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (textMessageEnabled)
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
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (textMessageEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Text Message Stream",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (textMessageEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ASCII text data transmission over OSC",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (textMessageEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = textMessageEnabled,
                    onCheckedChange = onTextMessageToggle
                )
            }
        }

        // Manual Message Sender Card
        ManualMessageSenderCard(
            oscHost = oscHost,
            oscPort = oscPort
        )
    }
}

/**
 * Manual Message Sender Card - Text edit and send functionality
 */
@Composable
private fun ManualMessageSenderCard(
    oscHost: String,
    oscPort: Int
) {
    var messageText by remember { mutableStateOf("") }
    var channelAddress by remember { mutableStateOf("/chan2/text") }
    var lastSentMessage by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Manual Message Sender",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            // Channel Address Field
            OutlinedTextField(
                value = channelAddress,
                onValueChange = { channelAddress = it },
                label = { Text("OSC Channel") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Message Text Field
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Message Text") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null
                    )
                },
                placeholder = { Text("Enter your message...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Quick Message Presets
            Text(
                text = "Quick Presets:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    "Hello World",
                    "Test Message",
                    "Status Update",
                    "Debug Info"
                )

                presets.forEach { preset ->
                    FilterChip(
                        onClick = { messageText = preset },
                        label = {
                            Text(
                                text = preset,
                                fontSize = 12.sp
                            )
                        },
                        selected = messageText == preset,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Send Button
            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        Log.i("MessagesTab", "Sending manual message: '$messageText' to $channelAddress via $oscHost:$oscPort")
                        sendOSCTextMessage(oscHost, oscPort, channelAddress, messageText)
                        lastSentMessage = messageText
                        messageText = "" // Clear after sending
                    }
                },
                enabled = messageText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null
                    )
                    Text(
                        text = "Send Message",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Last sent message display
            if (lastSentMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Last Sent:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "\"$lastSentMessage\" → $channelAddress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }

            // Connection info
            Text(
                text = "Messages sent to: $oscHost:$oscPort",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
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

            Log.i("MessagesTab", "OSC message sent successfully: $oscMessage")
        } catch (e: Exception) {
            Log.e("MessagesTab", "Failed to send OSC message: ${e.message}", e)
        }
    }
}