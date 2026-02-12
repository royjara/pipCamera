package com.elegia.pipcamera.ui.meters

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elegia.pipcamera.camera.CameraMetering
import kotlin.math.*

// Camera-specific meter components with smart auto-configuration

@Composable
fun ExposureMeter(
    metering: CameraMetering?,
    modifier: Modifier = Modifier
) {
    val meterData = remember(metering) {
        createExposureMeterData(metering)
    }

    AnalogMeter(
        label = "Exposure",
        meterData = meterData,
        meterType = MeterType.HorizontalBar,
        colorScheme = exposureColorScheme(),
        modifier = modifier
    )
}

@Composable
fun IsoMeter(
    metering: CameraMetering?,
    modifier: Modifier = Modifier
) {
    val meterData = remember(metering) {
        createIsoMeterData(metering)
    }

    AnalogMeter(
        label = "ISO",
        meterData = meterData,
        meterType = MeterType.CircularRing,
        colorScheme = isoColorScheme(),
        modifier = modifier
    )
}

@Composable
fun ApertureMeter(
    metering: CameraMetering?,
    modifier: Modifier = Modifier
) {
    val meterData = remember(metering) {
        createApertureMeterData(metering)
    }

    AnalogMeter(
        label = "Aperture",
        meterData = meterData,
        meterType = MeterType.NeedleGauge,
        colorScheme = apertureColorScheme(),
        modifier = modifier
    )
}

// Data mapping functions for each camera parameter

private fun createExposureMeterData(metering: CameraMetering?): MeterData {
    val exposureTimeNs = metering?.exposureTime

    return if (exposureTimeNs != null && exposureTimeNs > 0) {
        // Convert nanoseconds to log scale for better visualization
        // Range: 1/4000s (250,000 ns) to 1s (1,000,000,000 ns)
        val logValue = log10(exposureTimeNs.toFloat())
        val logMin = log10(250_000f)        // ~1/4000s
        val logMax = log10(1_000_000_000f)  // 1s

        val normalizedValue = (logValue - logMin) / (logMax - logMin)

        val colorZone = when {
            exposureTimeNs < 1_000_000L -> ColorZone.OPTIMAL    // < 1ms (fast shutter)
            exposureTimeNs < 33_000_000L -> ColorZone.CAUTION   // < 33ms (~1/30s, motion blur risk)
            else -> ColorZone.EXTREME                            // > 33ms (camera shake risk)
        }

        MeterData(
            value = normalizedValue.coerceIn(0f, 1f),
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

private fun createIsoMeterData(metering: CameraMetering?): MeterData {
    val iso = metering?.iso

    return if (iso != null && iso > 0) {
        // ISO range: 100 to 6400 (log scale)
        val logIso = log10(iso.toFloat())
        val logMin = log10(100f)    // ISO 100
        val logMax = log10(6400f)   // ISO 6400

        val normalizedValue = (logIso - logMin) / (logMax - logMin)

        val colorZone = when {
            iso <= 400 -> ColorZone.OPTIMAL     // Low noise
            iso <= 1600 -> ColorZone.CAUTION    // Moderate noise
            else -> ColorZone.EXTREME            // High noise
        }

        MeterData(
            value = normalizedValue.coerceIn(0f, 1f),
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

private fun createApertureMeterData(metering: CameraMetering?): MeterData {
    val aperture = metering?.aperture

    return if (aperture != null && aperture > 0f) {
        // Aperture range: f/1.4 to f/16 (linear scale, inverted for depth of field)
        val minAperture = 1.4f    // f/1.4 (shallow DOF)
        val maxAperture = 16f     // f/16 (deep DOF)

        // Normalize and invert (lower f-number = higher on scale)
        val normalizedValue = 1f - ((aperture - minAperture) / (maxAperture - minAperture))

        val colorZone = when {
            aperture <= 2.8f -> ColorZone.EXTREME    // Very shallow DOF
            aperture <= 5.6f -> ColorZone.CAUTION    // Moderate DOF
            else -> ColorZone.OPTIMAL                 // Deep DOF
        }

        MeterData(
            value = normalizedValue.coerceIn(0f, 1f),
            rawValue = aperture,
            displayText = "f/$aperture",
            minValue = minAperture,
            maxValue = maxAperture,
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

// Camera-specific color schemes inspired by analog photography

private fun exposureColorScheme() = MeterColorScheme(
    lowColor = Color(0xFF4CAF50),        // Green - fast shutter, sharp
    midColor = Color(0xFFFF9800),        // Orange - caution zone
    highColor = Color(0xFFE91E63),       // Pink - slow shutter, blur risk
    backgroundColor = Color(0xFF0D1B2A),  // Deep blue-black
    textColor = Color(0xFFE0E0E0),
    accentColor = Color(0xFF64B5F6)      // Light blue
)

private fun isoColorScheme() = MeterColorScheme(
    lowColor = Color(0xFF2E7D32),        // Dark green - clean, low noise
    midColor = Color(0xFFEF6C00),        // Deep orange - moderate noise
    highColor = Color(0xFFC62828),       // Dark red - high noise
    backgroundColor = Color(0xFF1A1A1A),  // Charcoal
    textColor = Color(0xFFF5F5F5),
    accentColor = Color(0xFF81C784)      // Light green
)

private fun apertureColorScheme() = MeterColorScheme(
    lowColor = Color(0xFF6A1B9A),        // Purple - shallow DOF
    midColor = Color(0xFF1565C0),        // Blue - moderate DOF
    highColor = Color(0xFF2E7D32),       // Green - deep DOF
    backgroundColor = Color(0xFF0A0A0A),  // Almost black
    textColor = Color(0xFFECEFF1),
    accentColor = Color(0xFFBA68C8)      // Light purple
)

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
            // Show as fractional seconds for typical shutter speeds
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

// All-in-one camera meter dashboard
@Composable
fun CameraMeterDashboard(
    metering: CameraMetering?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExposureMeter(
            metering = metering,
            modifier = Modifier.weight(1f)
        )

        IsoMeter(
            metering = metering,
            modifier = Modifier.weight(1f)
        )

        ApertureMeter(
            metering = metering,
            modifier = Modifier.weight(1f)
        )
    }
}