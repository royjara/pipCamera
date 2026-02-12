package com.elegia.pipcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elegia.pipcamera.camera.CameraCapabilities
import com.elegia.pipcamera.camera.CameraMetering
import com.elegia.pipcamera.ui.meters.InteractiveCameraMeterDashboard

@Composable
fun MeteringInfoOverlay(
    metering: CameraMetering?,
    capabilities: CameraCapabilities?,
    isPiPMode: Boolean = false
) {
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
                        Color.Black.copy(alpha = 0.8f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                // Interactive analog meter dashboard
                InteractiveCameraMeterDashboard(
                    metering = metering,
                    modifier = Modifier.fillMaxWidth()
                )
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