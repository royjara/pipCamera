package com.elegia.pipcamera.ui

import android.R.attr.onClick
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SurfaceToggle(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val isEnabled: Boolean,
    val canToggle: Boolean = true
)

@Composable
fun SurfaceToolbar(
    isPiPMode: Boolean = false,
    onSurfaceToggle: (String, Boolean) -> Unit = { _, _ -> },
    onSnapshotClick: () -> Unit = {},
    onVideoToggle: () -> Unit = {},
    isRecording: Boolean = false,
    isSnapshotEnabled: Boolean = false,
    isVideoEnabled: Boolean = false,
    isAudioEnabled: Boolean = true,
    // NEW: Visual feedback parameters
    snapshotFeedback: Boolean = false,
    recordingIndicator: Boolean = false
) {
    if (!isPiPMode) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .width(128.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Text(
                        text = "outputs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )




                    // Snapshot row
                    ModularRow {
                        FloatingActionButton(
                            onClick = { onSurfaceToggle("snapshot", !isSnapshotEnabled) },
                            modifier = Modifier.size(30.dp),
                            containerColor = if (isSnapshotEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSnapshotEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Snapshot Surface",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        FloatingActionButton(
                            onClick = onSnapshotClick,
                            modifier = Modifier.size(30.dp),
                            containerColor = if (isSnapshotEnabled && snapshotFeedback)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                else if (isSnapshotEnabled)
                                MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            contentColor = if (isSnapshotEnabled) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Take Photo",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "photo",
                            fontSize = 8.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Video row
                    ModularRow {
                        FloatingActionButton(
                            onClick = { onSurfaceToggle("video", !isVideoEnabled) },
                            modifier = Modifier.size(30.dp),
                            containerColor = if (isVideoEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isVideoEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video Surface",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = { if (isVideoEnabled) onSurfaceToggle("audio", !isAudioEnabled) },
                            modifier = Modifier.size(30.dp),
                            containerColor = if (!isVideoEnabled)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                else if (isAudioEnabled)
                                MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!isVideoEnabled)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else if (isAudioEnabled)
                                MaterialTheme.colorScheme.onTertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Audio Recording",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        FloatingActionButton(
                            onClick = { if (isVideoEnabled) onVideoToggle() },
                            modifier = Modifier.size(30.dp),
                            containerColor = if (recordingIndicator && isRecording)
                                MaterialTheme.colorScheme.error
                                else if (isVideoEnabled && isRecording)
                                MaterialTheme.colorScheme.error
                                else if (isVideoEnabled)
                                MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            contentColor = if (isVideoEnabled)
                                if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "video",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun ModularRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.height(48.dp), // Consistent height for all toolbar items
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
            content = content
        )
    }
}

