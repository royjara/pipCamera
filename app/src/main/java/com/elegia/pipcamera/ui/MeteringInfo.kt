package com.elegia.pipcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elegia.pipcamera.camera.CameraCapabilities
import com.elegia.pipcamera.camera.CameraMetering

@Composable
fun MeteringInfoOverlay(
    metering: CameraMetering?,
    capabilities: CameraCapabilities?,
    isPiPMode: Boolean = false
) {
    // State for toggle visibility
    var isExpanded by remember { mutableStateOf(true) }
    if (!isPiPMode && metering != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = if (isExpanded) 0.7f else 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        isExpanded = !isExpanded
                    }
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Header with toggle icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CAMERA METERING",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Metering details (only show when expanded)
                    if (isExpanded) {
                        // Focus Info
                     Text("TODO: add 3A tuning knobs here!", color = Color.Red)
                    } else {
                        // Collapsed state - show minimal info
                        Text(
                            text = "Tap to expand camera details",
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 6.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Focus:",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${
                                    getCurrentFocusModeName(
                                        metering.focusMode,
                                        capabilities
                                    )
                                } (${metering.getFocusStateDisplayName()})",
                                color = Color.Green,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        Row {
                            // Exposure Info
                            Box(
                                modifier = Modifier.weight(1f),
//                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row{
                                    Text(
                                        text = "Exposure:",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = metering.getExposureTimeDisplayValue(),
                                        color = Color.Yellow,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // ISO Info
                            Box(
                                modifier = Modifier.weight(1f),
                            ) {
                                Row{
                                    Text(
                                        text = "ISO:",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = metering.getISODisplayValue(),
                                        color = Color.Cyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }}

                            // Aperture Info
                            Box(
                                modifier = Modifier.weight(1f),
                            ) {
                                Row {
                                    Text(
                                        text = "Aperture:",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = metering.getApertureDisplayValue(),
                                        color = Color.Magenta,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }

                            }

                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getCurrentFocusModeName(
    currentMode: Int?,
    capabilities: CameraCapabilities?
): String {
    return if (currentMode != null && capabilities != null) {
        capabilities.getFocusModeDisplayName(currentMode)
    } else {
        "Unknown"
    }
}