package com.elegia.pipcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
    onRotateClockwise: () -> Unit = {},
    onRotateCounterclockwise: () -> Unit = {},
    onAudioDemoClick: () -> Unit = {},
    isRecording: Boolean = false,
    isSnapshotEnabled: Boolean = false,
    isVideoEnabled: Boolean = false,
    isAnalysisEnabled: Boolean = false,
    isAudioEnabled: Boolean = true,
    isGLEnabled: Boolean = false
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
                        text = "SurfaceMgmt",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Analysis row
                    SurfaceButtonTriple(
                        leftIcon = Icons.Default.Search,
                        leftEnabled = isAnalysisEnabled,
                        leftCanToggle = true,
                        leftDescription = "Analysis Surface",
                        middleIcon = null,
                        middleEnabled = false,
                        middleCanToggle = false,
                        middleDescription = "",
                        rightIcon = null,
                        rightEnabled = false,
                        rightDescription = "",
                        onLeftClick = { onSurfaceToggle("analysis", !isAnalysisEnabled) },
                        onMiddleClick = {},
                        onRightClick = {}
                    )

                    // Snapshot row
                    SurfaceButtonTriple(
                        leftIcon = Icons.Default.Add,
                        leftEnabled = isSnapshotEnabled,
                        leftCanToggle = true,
                        leftDescription = "Snapshot Surface",
                        middleIcon = null,
                        middleEnabled = false,
                        middleCanToggle = false,
                        middleDescription = "",
                        rightIcon = Icons.Default.Add,
                        rightEnabled = isSnapshotEnabled,
                        rightDescription = "Take Photo",
                        onLeftClick = { onSurfaceToggle("snapshot", !isSnapshotEnabled) },
                        onMiddleClick = {},
                        onRightClick = onSnapshotClick
                    )

                    // Video row (with audio toggle in middle)
                    SurfaceButtonTriple(
                        leftIcon = Icons.Default.PlayArrow,
                        leftEnabled = isVideoEnabled,
                        leftCanToggle = true,
                        leftDescription = "Video Surface",
                        middleIcon = Icons.Default.Phone,
                        middleEnabled = isAudioEnabled,
                        middleCanToggle = isVideoEnabled, // Only enabled when video surface is enabled
                        middleDescription = "Audio Recording",
                        rightIcon = if (isRecording) Icons.Default.Close else Icons.Default.PlayArrow,
                        rightEnabled = isVideoEnabled,
                        rightDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        onLeftClick = { onSurfaceToggle("video", !isVideoEnabled) },
                        onMiddleClick = { onSurfaceToggle("audio", !isAudioEnabled) },
                        onRightClick = onVideoToggle
                    )

                    // AGSL Shader row
                    SurfaceButtonTriple(
                        leftIcon = Icons.Default.Face,
                        leftEnabled = isGLEnabled,
                        leftCanToggle = true,
                        leftDescription = "AGSL Shader Effects",
                        middleIcon = null,
                        middleEnabled = false,
                        middleCanToggle = false,
                        middleDescription = "",
                        rightIcon = null,
                        rightEnabled = false,
                        rightDescription = "",
                        onLeftClick = { onSurfaceToggle("gl", !isGLEnabled) },
                        onMiddleClick = {},
                        onRightClick = {}
                    )

                    // Rotation controls row (enabled only when GL/AGSL is active)
                    SurfaceButtonTriple(
                        leftIcon = Icons.Default.Refresh,
                        leftEnabled = isGLEnabled,
                        leftCanToggle = isGLEnabled,
                        leftDescription = "Rotate Counter-clockwise",
                        middleIcon = null,
                        middleEnabled = false,
                        middleCanToggle = false,
                        middleDescription = "",
                        rightIcon = Icons.Default.Refresh,
                        rightEnabled = isGLEnabled,
                        rightDescription = "Rotate Clockwise",
                        onLeftClick = onRotateCounterclockwise,
                        onMiddleClick = {},
                        onRightClick = onRotateClockwise
                    )

                    // Audio Demo row - single full-width button
                    FloatingActionButton(
                        onClick = onAudioDemoClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Audio Processing Demo",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceButtonTriple(
    leftIcon: ImageVector,
    leftEnabled: Boolean,
    leftCanToggle: Boolean,
    leftDescription: String,
    middleIcon: ImageVector?,
    middleEnabled: Boolean,
    middleCanToggle: Boolean,
    middleDescription: String,
    rightIcon: ImageVector?,
    rightEnabled: Boolean,
    rightDescription: String,
    onLeftClick: () -> Unit,
    onMiddleClick: () -> Unit,
    onRightClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left button - Surface toggle
        val leftBackgroundColor = when {
            !leftCanToggle && leftEnabled -> MaterialTheme.colorScheme.primary
            leftEnabled -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        val leftContentColor = when {
            leftEnabled -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        FloatingActionButton(
            onClick = {
                if (leftCanToggle) {
                    onLeftClick()
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            containerColor = leftBackgroundColor,
            contentColor = leftContentColor
        ) {
            Icon(
                imageVector = leftIcon,
                contentDescription = leftDescription,
                modifier = Modifier.size(18.dp)
            )
        }

        // Middle button - Additional toggle (if available)
        if (middleIcon != null) {
            val middleBackgroundColor = when {
                !middleCanToggle -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                middleEnabled -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            val middleContentColor = when {
                !middleCanToggle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                middleEnabled -> MaterialTheme.colorScheme.onTertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            FloatingActionButton(
                onClick = {
                    if (middleCanToggle) {
                        onMiddleClick()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                containerColor = middleBackgroundColor,
                contentColor = middleContentColor
            ) {
                Icon(
                    imageVector = middleIcon,
                    contentDescription = middleDescription,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Right button - Action trigger (if available)
        if (rightIcon != null) {
            val rightBackgroundColor = when {
                rightEnabled -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }

            val rightContentColor = when {
                rightEnabled -> MaterialTheme.colorScheme.onSecondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }

            FloatingActionButton(
                onClick = {
                    if (rightEnabled) {
                        onRightClick()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                containerColor = rightBackgroundColor,
                contentColor = rightContentColor
            ) {
                Icon(
                    imageVector = rightIcon,
                    contentDescription = rightDescription,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}