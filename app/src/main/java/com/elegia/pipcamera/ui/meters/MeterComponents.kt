package com.elegia.pipcamera.ui.meters

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

// Meter Type Definitions
sealed class MeterType {
    object HorizontalBar : MeterType()
    object CircularRing : MeterType()
    object NeedleGauge : MeterType()
    object LedStrip : MeterType()
}

// Color scheme for different value zones
data class MeterColorScheme(
    val lowColor: Color = Color(0xFF00C853),      // Green - optimal
    val midColor: Color = Color(0xFFFFB300),      // Amber - caution
    val highColor: Color = Color(0xFFFF1744),     // Red - extreme
    val backgroundColor: Color = Color(0xFF1A1A1A), // Dark background
    val textColor: Color = Color(0xFFE0E0E0),     // Light text
    val accentColor: Color = Color(0xFF64FFDA)     // Cyan accent
)

// Data structure for meter values
data class MeterData(
    val value: Float,           // Normalized 0.0 - 1.0
    val rawValue: Float,        // Actual camera value
    val displayText: String,    // Formatted display string
    val minValue: Float,        // Minimum possible value
    val maxValue: Float,        // Maximum possible value
    val colorZone: ColorZone = ColorZone.OPTIMAL
)

enum class ColorZone { OPTIMAL, CAUTION, EXTREME }

// Main reusable meter component
@Composable
fun AnalogMeter(
    label: String,
    meterData: MeterData,
    meterType: MeterType,
    colorScheme: MeterColorScheme = MeterColorScheme(),
    modifier: Modifier = Modifier
) {
    // Animated value for smooth transitions
    val animatedValue by animateFloatAsState(
        targetValue = meterData.value,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "meter_value_$label"
    )

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

        // Meter visual based on type
        Box(
            modifier = Modifier.height(50.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (meterType) {
                is MeterType.HorizontalBar -> {
                    HorizontalBarMeter(
                        value = animatedValue,
                        meterData = meterData,
                        colorScheme = colorScheme
                    )
                }
                is MeterType.CircularRing -> {
                    CircularRingMeter(
                        value = animatedValue,
                        meterData = meterData,
                        colorScheme = colorScheme
                    )
                }
                is MeterType.NeedleGauge -> {
                    NeedleGaugeMeter(
                        value = animatedValue,
                        meterData = meterData,
                        colorScheme = colorScheme
                    )
                }
                is MeterType.LedStrip -> {
                    LedStripMeter(
                        value = animatedValue,
                        meterData = meterData,
                        colorScheme = colorScheme
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Value display
        Text(
            text = meterData.displayText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = getValueColor(meterData.colorZone, colorScheme)
        )
    }
}

// Helper function to get color based on zone
private fun getValueColor(zone: ColorZone, colorScheme: MeterColorScheme): Color {
    return when (zone) {
        ColorZone.OPTIMAL -> colorScheme.lowColor
        ColorZone.CAUTION -> colorScheme.midColor
        ColorZone.EXTREME -> colorScheme.highColor
    }
}

// Horizontal Bar Meter Implementation
@Composable
private fun HorizontalBarMeter(
    value: Float,
    meterData: MeterData,
    colorScheme: MeterColorScheme,
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

        // Tick marks
        for (i in 0..4) {
            val x = canvasWidth * (i / 4f)
            drawLine(
                color = colorScheme.textColor.copy(alpha = 0.3f),
                start = Offset(x, barY + barHeight + 4.dp.toPx()),
                end = Offset(x, barY + barHeight + 8.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

// Circular Ring Meter Implementation
@Composable
private fun CircularRingMeter(
    value: Float,
    meterData: MeterData,
    colorScheme: MeterColorScheme,
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
            style = Stroke(strokeWidth)
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
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

// Needle Gauge Meter Implementation
@Composable
private fun NeedleGaugeMeter(
    value: Float,
    meterData: MeterData,
    colorScheme: MeterColorScheme,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 8.dp.toPx()

        // Draw gauge arc background
        drawArc(
            color = colorScheme.backgroundColor.copy(alpha = 0.3f),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(4.dp.toPx())
        )

        // Draw tick marks
        for (i in 0..8) {
            val angle = 135f + (270f * i / 8f)
            val tickStart = Offset(
                center.x + (radius - 8.dp.toPx()) * cos(Math.toRadians(angle.toDouble())).toFloat(),
                center.y + (radius - 8.dp.toPx()) * sin(Math.toRadians(angle.toDouble())).toFloat()
            )
            val tickEnd = Offset(
                center.x + radius * cos(Math.toRadians(angle.toDouble())).toFloat(),
                center.y + radius * sin(Math.toRadians(angle.toDouble())).toFloat()
            )

            drawLine(
                color = colorScheme.textColor.copy(alpha = 0.4f),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 2.dp.toPx()
            )
        }

        // Draw needle
        val needleAngle = 135f + (270f * value.coerceIn(0f, 1f))
        val needleLength = radius - 12.dp.toPx()

        rotate(needleAngle, center) {
            // Needle line
            drawLine(
                color = getValueColor(meterData.colorZone, colorScheme),
                start = center,
                end = Offset(center.x, center.y - needleLength),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Center dot
        drawCircle(
            color = colorScheme.accentColor,
            radius = 4.dp.toPx(),
            center = center
        )
    }
}

// LED Strip Meter Implementation
@Composable
private fun LedStripMeter(
    value: Float,
    meterData: MeterData,
    colorScheme: MeterColorScheme,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val ledCount = 12
        val ledWidth = size.width / (ledCount + 1)
        val ledHeight = size.height * 0.4f
        val activeLeds = (ledCount * value.coerceIn(0f, 1f)).toInt()

        for (i in 0 until ledCount) {
            val x = ledWidth * (i + 0.5f) - ledWidth / 2f
            val y = (size.height - ledHeight) / 2f

            val ledColor = when {
                i >= activeLeds -> colorScheme.backgroundColor.copy(alpha = 0.2f)
                i < ledCount * 0.6f -> colorScheme.lowColor
                i < ledCount * 0.8f -> colorScheme.midColor
                else -> colorScheme.highColor
            }

            drawRoundRect(
                color = ledColor,
                topLeft = Offset(x, y),
                size = Size(ledWidth * 0.8f, ledHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}