package com.elegia.pipcamera.ui.meters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elegia.pipcamera.camera.CameraMetering
import com.elegia.pipcamera.camera.CaptureController
import kotlinx.coroutines.launch
import android.hardware.camera2.CaptureRequest
import kotlin.math.*

// Interactive meter state management
enum class MeterMode { AUTO, MANUAL }

data class InteractiveMeterState(
    val mode: MeterMode = MeterMode.AUTO,
    val baselineValue: Float = 0f,    // Captured from last CaptureResult
    val manualDelta: Float = 0f,      // User's delta adjustment
    val displayValue: Float = 0f      // What meter shows
)

@Composable
fun InteractiveExposureMeter(
    metering: CameraMetering?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Meter state management
    var meterState by remember { mutableStateOf(InteractiveMeterState()) }

    // Auto-update baseline when new metering data arrives (in auto mode)
    LaunchedEffect(metering?.exposureTime) {
        if (meterState.mode == MeterMode.AUTO) {
            val exposureTime = metering?.exposureTime
            if (exposureTime != null) {
                val autoValue = createExposureNormalizedValue(exposureTime)
                meterState = meterState.copy(
                    baselineValue = exposureTime.toFloat(),
                    displayValue = autoValue
                )
            }
        }
    }

    // Ensure we return to auto mode when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            // Reset to auto mode on cleanup
            CaptureController.returnToAutoExposure()
        }
    }

    // Submit manual capture requests when in manual mode
    LaunchedEffect(meterState.mode, meterState.manualDelta) {
        if (meterState.mode == MeterMode.MANUAL) {
            val manualExposureNs = calculateManualExposureTime(
                meterState.baselineValue.toLong(),
                meterState.manualDelta
            )

            coroutineScope.launch {
                CaptureController.setManualExposure(manualExposureNs)
            }
        } else {
            // Return to auto mode when not in manual
            coroutineScope.launch {
                CaptureController.returnToAutoExposure()
            }
        }
    }

    val meterData = remember(meterState, metering) {
        if (meterState.mode == MeterMode.MANUAL) {
            // Show manual value
            val manualExposureNs = calculateManualExposureTime(
                meterState.baselineValue.toLong(),
                meterState.manualDelta
            )
            createExposureMeterData(manualExposureNs, isManual = true)
        } else {
            // Show auto value
            createExposureMeterData(metering?.exposureTime, isManual = false)
        }
    }

    InteractiveAnalogMeter(
        label = "Exposure",
        meterData = meterData,
        meterType = MeterType.HorizontalBar,
        colorScheme = if (meterState.mode == MeterMode.MANUAL)
            exposureManualColorScheme()
        else
            exposureAutoColorScheme(),
        isInteractive = true,
        onTouchStart = {
            // Capture baseline from current metering
            metering?.exposureTime?.let { currentExposure ->
                meterState = meterState.copy(
                    mode = MeterMode.MANUAL,
                    baselineValue = currentExposure.toFloat(),
                    manualDelta = 0f
                )
            }
        },
        onDrag = { delta ->
            // Update manual delta
            meterState = meterState.copy(
                manualDelta = meterState.manualDelta + delta * 2f // Scale factor for sensitivity
            )
        },
        onTouchEnd = {
            // Keep in manual mode - user can double-tap to go back to auto
        },
        onDoubleTap = {
            // Double-tap to return to auto mode
            meterState = meterState.copy(
                mode = MeterMode.AUTO,
                manualDelta = 0f
            )
        },
        modifier = modifier
    )
}

@Composable
fun InteractiveIsoMeter(
    metering: CameraMetering?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var meterState by remember { mutableStateOf(InteractiveMeterState()) }

    // Auto-update baseline when new metering data arrives (in auto mode)
    LaunchedEffect(metering?.iso) {
        if (meterState.mode == MeterMode.AUTO) {
            val iso = metering?.iso
            if (iso != null) {
                val autoValue = createIsoNormalizedValue(iso)
                meterState = meterState.copy(
                    baselineValue = iso.toFloat(),
                    displayValue = autoValue
                )
            }
        }
    }

    // Ensure we return to auto mode when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            // Reset to auto mode on cleanup
            CaptureController.returnToAutoExposure()
        }
    }

    // Submit manual capture requests when in manual mode
    LaunchedEffect(meterState.mode, meterState.manualDelta) {
        if (meterState.mode == MeterMode.MANUAL) {
            val manualIso = calculateManualIso(
                meterState.baselineValue.toInt(),
                meterState.manualDelta
            )

            coroutineScope.launch {
                CaptureController.setManualISO(manualIso)
            }
        } else {
            // Return to auto mode when not in manual
            coroutineScope.launch {
                CaptureController.returnToAutoExposure()
            }
        }
    }

    val meterData = remember(meterState, metering) {
        if (meterState.mode == MeterMode.MANUAL) {
            val manualIso = calculateManualIso(
                meterState.baselineValue.toInt(),
                meterState.manualDelta
            )
            createIsoMeterData(manualIso, isManual = true)
        } else {
            createIsoMeterData(metering?.iso, isManual = false)
        }
    }

    InteractiveAnalogMeter(
        label = "ISO",
        meterData = meterData,
        meterType = MeterType.CircularRing,
        colorScheme = if (meterState.mode == MeterMode.MANUAL)
            isoManualColorScheme()
        else
            isoAutoColorScheme(),
        isInteractive = true,
        onTouchStart = {
            metering?.iso?.let { currentIso ->
                meterState = meterState.copy(
                    mode = MeterMode.MANUAL,
                    baselineValue = currentIso.toFloat(),
                    manualDelta = 0f
                )
            }
        },
        onDrag = { delta ->
            meterState = meterState.copy(
                manualDelta = meterState.manualDelta + delta * 1000f // Scale for ISO sensitivity
            )
        },
        onTouchEnd = {
            // Keep in manual mode - user can double-tap to go back to auto
        },
        onDoubleTap = {
            // Double-tap to return to auto mode
            meterState = meterState.copy(
                mode = MeterMode.AUTO,
                manualDelta = 0f
            )
        },
        modifier = modifier
    )
}

// Enhanced analog meter with touch interaction
@Composable
private fun InteractiveAnalogMeter(
    label: String,
    meterData: MeterData,
    meterType: MeterType,
    colorScheme: MeterColorScheme = MeterColorScheme(),
    isInteractive: Boolean = false,
    onTouchStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onTouchEnd: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                colorScheme.backgroundColor,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Meter label
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = colorScheme.textColor.copy(alpha = 0.8f),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Interactive meter visual
        Box(
            modifier = Modifier
                .height(50.dp)
                .fillMaxWidth()
                .then(
                    if (isInteractive) {
                        Modifier
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        onTouchStart()
                                    },
                                    onDrag = { change, dragAmount ->
                                        // Convert drag to delta (negative because drag down = increase value)
                                        val delta = -dragAmount.y / size.height
                                        onDrag(delta)
                                    },
                                    onDragEnd = {
                                        onTouchEnd()
                                    }
                                )
                            }
                            .pointerInput("doubleTap") {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onDoubleTap()
                                    }
                                )
                            }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Draw the meter
            when (meterType) {
                is MeterType.HorizontalBar -> {
                    InteractiveHorizontalBarMeter(
                        value = meterData.value,
                        meterData = meterData,
                        colorScheme = colorScheme,
                        showTouchPoint = isInteractive
                    )
                }
                is MeterType.CircularRing -> {
                    InteractiveCircularRingMeter(
                        value = meterData.value,
                        meterData = meterData,
                        colorScheme = colorScheme,
                        showTouchPoint = isInteractive
                    )
                }
                else -> {
                    // Fallback to regular meter
                    Text("Unsupported", color = colorScheme.textColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Value display
        Text(
            text = meterData.displayText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = getValueColorForZone(meterData.colorZone, colorScheme)
        )
    }
}

// Interactive horizontal bar with touchable circle
@Composable
private fun InteractiveHorizontalBarMeter(
    value: Float,
    meterData: MeterData,
    colorScheme: MeterColorScheme,
    showTouchPoint: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barHeight = canvasHeight * 0.3f
        val barY = (canvasHeight - barHeight) / 2f

        // Background bar
        drawRoundRect(
            color = colorScheme.backgroundColor.copy(alpha = 0.3f),
            topLeft = Offset(0f, barY),
            size = Size(canvasWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
        )

        // Progress bar with gradient
        val progressWidth = canvasWidth * value.coerceIn(0f, 1f)

        if (progressWidth > 0f) {
            val gradient = Brush.horizontalGradient(
                colors = listOf(
                    colorScheme.lowColor,
                    colorScheme.midColor,
                    colorScheme.highColor
                ),
                startX = 0f,
                endX = canvasWidth
            )

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(0f, barY),
                size = Size(progressWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
            )
        }

        // Touchable circle at current value position
        if (showTouchPoint && progressWidth > 0f) {
            val circleX = progressWidth
            val circleY = barY + barHeight / 2f
            val circleRadius = barHeight * 0.6f

            // Circle background
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = circleRadius,
                center = Offset(circleX, circleY)
            )

            // Circle border
            drawCircle(
                color = colorScheme.accentColor,
                radius = circleRadius,
                center = Offset(circleX, circleY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }
}

// Interactive circular ring with touchable circle
@Composable
private fun InteractiveCircularRingMeter(
    value: Float,
    meterData: MeterData,
    colorScheme: MeterColorScheme,
    showTouchPoint: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 10.dp.toPx()
        val strokeWidth = 8.dp.toPx()

        // Background ring
        drawCircle(
            color = colorScheme.backgroundColor.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
        )

        // Progress arc
        val sweepAngle = 360f * value.coerceIn(0f, 1f)
        val gradient = Brush.sweepGradient(
            colors = listOf(
                colorScheme.lowColor,
                colorScheme.midColor,
                colorScheme.highColor,
                colorScheme.lowColor
            ),
            center = center
        )

        if (sweepAngle > 0f) {
            drawArc(
                brush = gradient,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Touchable circle at current value position on the ring
        if (showTouchPoint && sweepAngle > 0f) {
            val angle = Math.toRadians((-90f + sweepAngle).toDouble())
            val circleX = center.x + radius * cos(angle).toFloat()
            val circleY = center.y + radius * sin(angle).toFloat()
            val circleRadius = strokeWidth * 0.8f

            // Circle background
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = circleRadius,
                center = Offset(circleX, circleY)
            )

            // Circle border
            drawCircle(
                color = colorScheme.accentColor,
                radius = circleRadius,
                center = Offset(circleX, circleY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }
}

// Calculation helpers
private fun calculateManualExposureTime(baselineNs: Long, delta: Float): Long {
    // Convert delta to log scale adjustment
    val logAdjustment = delta * 3f // 3 stops range
    val multiplier = 2.0.pow(logAdjustment.toDouble())
    val newExposureNs = (baselineNs * multiplier).toLong()

    // Clamp to reasonable bounds
    return newExposureNs.coerceIn(250_000L, 1_000_000_000L) // 1/4000s to 1s
}

private fun calculateManualIso(baselineIso: Int, delta: Float): Int {
    // Convert delta to log scale adjustment
    val logAdjustment = delta / 1000f * 3f // 3 stops range
    val multiplier = 2.0.pow(logAdjustment.toDouble())
    val newIso = (baselineIso * multiplier).toInt()

    // Clamp to reasonable bounds
    return newIso.coerceIn(100, 6400)
}

// Enhanced color schemes for manual mode
private fun exposureManualColorScheme() = MeterColorScheme(
    lowColor = Color(0xFF66BB6A),        // Brighter green - manual control
    midColor = Color(0xFFFFB74D),        // Brighter orange
    highColor = Color(0xFFEF5350),       // Brighter red
    backgroundColor = Color(0xFF0D1B2A),
    textColor = Color(0xFFE0E0E0),
    accentColor = Color(0xFF42A5F5)      // Bright blue for manual
)

private fun isoManualColorScheme() = MeterColorScheme(
    lowColor = Color(0xFF4CAF50),        // Brighter green
    midColor = Color(0xFFFF9800),        // Brighter orange
    highColor = Color(0xFFE53935),       // Brighter red
    backgroundColor = Color(0xFF1A1A1A),
    textColor = Color(0xFFF5F5F5),
    accentColor = Color(0xFF26C6DA)      // Bright cyan for manual
)

// Helper functions for meter data creation
private fun createExposureNormalizedValue(exposureTimeNs: Long?): Float {
    return if (exposureTimeNs != null && exposureTimeNs > 0) {
        val logValue = log10(exposureTimeNs.toFloat())
        val logMin = log10(250_000f)
        val logMax = log10(1_000_000_000f)
        ((logValue - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
    } else 0f
}

private fun createIsoNormalizedValue(iso: Int?): Float {
    return if (iso != null && iso > 0) {
        val logIso = log10(iso.toFloat())
        val logMin = log10(100f)
        val logMax = log10(6400f)
        ((logIso - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
    } else 0f
}

// All-in-one interactive dashboard
@Composable
fun InteractiveCameraMeterDashboard(
    metering: CameraMetering?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InteractiveExposureMeter(
            metering = metering,
            modifier = Modifier.weight(1f)
        )

        InteractiveIsoMeter(
            metering = metering,
            modifier = Modifier.weight(1f)
        )

        // Aperture meter (read-only for now)
        ApertureMeter(
            metering = metering,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun createExposureMeterData(exposureTimeNs: Long?, isManual: Boolean = false): MeterData {
    return if (exposureTimeNs != null && exposureTimeNs > 0) {
        val normalizedValue = createExposureNormalizedValue(exposureTimeNs)
        val colorZone = when {
            exposureTimeNs < 1_000_000L -> ColorZone.OPTIMAL
            exposureTimeNs < 33_000_000L -> ColorZone.CAUTION
            else -> ColorZone.EXTREME
        }
        
        MeterData(
            value = normalizedValue,
            rawValue = exposureTimeNs.toFloat(),
            displayText = formatExposureTime(exposureTimeNs),
            minValue = 250_000f,
            maxValue = 1_000_000_000f,
            colorZone = colorZone
        )
    } else {
        MeterData(
            value = 0f,
            rawValue = 0f,
            displayText = "N/A",
            minValue = 0f,
            maxValue = 1f,
            colorZone = ColorZone.OPTIMAL
        )
    }
}

private fun createIsoMeterData(iso: Int?, isManual: Boolean = false): MeterData {
    return if (iso != null && iso > 0) {
        val normalizedValue = createIsoNormalizedValue(iso)
        val colorZone = when {
            iso <= 400 -> ColorZone.OPTIMAL
            iso <= 1600 -> ColorZone.CAUTION
            else -> ColorZone.EXTREME
        }
        
        MeterData(
            value = normalizedValue,
            rawValue = iso.toFloat(),
            displayText = "ISO $iso",
            minValue = 100f,
            maxValue = 6400f,
            colorZone = colorZone
        )
    } else {
        MeterData(
            value = 0f,
            rawValue = 0f,
            displayText = "N/A",
            minValue = 0f,
            maxValue = 1f,
            colorZone = ColorZone.OPTIMAL
        )
    }
}

// Helper function to format exposure time for display
private fun formatExposureTime(nanoseconds: Long): String {
    return when {
        nanoseconds >= 1_000_000_000L -> {
            val seconds = nanoseconds / 1_000_000_000f
            "${seconds.format(1)}s"
        }
        nanoseconds >= 1_000_000L -> {
            val ms = nanoseconds / 1_000_000f
            "${ms.format(1)}ms"
        }
        nanoseconds >= 10_000L -> {
            val fraction = (1_000_000_000L / nanoseconds).toInt()
            "1/${fraction}s"
        }
        else -> {
            "${nanoseconds}ns"
        }
    }
}

// Extension function for number formatting
private fun Float.format(digits: Int): String = "%.${digits}f".format(this)


// Auto color schemes
private fun exposureAutoColorScheme() = MeterColorScheme(
    lowColor = Color(0xFF4CAF50),        // Green - fast shutter, sharp
    midColor = Color(0xFFFF9800),        // Orange - caution zone
    highColor = Color(0xFFE91E63),       // Pink - slow shutter, blur risk
    backgroundColor = Color(0xFF0D1B2A),  // Deep blue-black
    textColor = Color(0xFFE0E0E0),
    accentColor = Color(0xFF64B5F6)      // Light blue
)

private fun isoAutoColorScheme() = MeterColorScheme(
    lowColor = Color(0xFF2E7D32),        // Dark green - clean, low noise
    midColor = Color(0xFFEF6C00),        // Deep orange - moderate noise
    highColor = Color(0xFFC62828),       // Dark red - high noise
    backgroundColor = Color(0xFF1A1A1A),  // Charcoal
    textColor = Color(0xFFF5F5F5),
    accentColor = Color(0xFF81C784)      // Light green
)

// Helper function to get color based on zone
private fun getValueColorForZone(zone: ColorZone, colorScheme: MeterColorScheme): Color {
    return when (zone) {
        ColorZone.OPTIMAL -> colorScheme.lowColor
        ColorZone.CAUTION -> colorScheme.midColor
        ColorZone.EXTREME -> colorScheme.highColor
    }
}
