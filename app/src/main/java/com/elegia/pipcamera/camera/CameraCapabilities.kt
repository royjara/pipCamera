package com.elegia.pipcamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

@OptIn(ExperimentalCamera2Interop::class)
data class CameraCapabilities(
    val supportedISO: IntRange?,
    val supportedExposureCompensation: IntRange?,
    val supportedFocusModes: List<Int>,
    val supportedFlashModes: List<Int>,
    val supportedWhiteBalance: List<Int>,
    val availableApertures: FloatArray?,
    val availableFocalLengths: FloatArray?,
    val exposureTimeRange: android.util.Range<Long>?,
    val availableSceneModes: IntArray?,
    val availableEffects: IntArray?,
    val availableAntiBandingModes: IntArray?,
    val availableColorCorrectionModes: IntArray?
) {
    companion object {
        fun from(camera2CameraInfo: Camera2CameraInfo?): CameraCapabilities {
            if (camera2CameraInfo == null) {
                return CameraCapabilities(
                    supportedISO = null,
                    supportedExposureCompensation = null,
                    supportedFocusModes = emptyList(),
                    supportedFlashModes = emptyList(),
                    supportedWhiteBalance = emptyList(),
                    availableApertures = null,
                    availableFocalLengths = null,
                    exposureTimeRange = null,
                    availableSceneModes = null,
                    availableEffects = null,
                    availableAntiBandingModes = null,
                    availableColorCorrectionModes = null
                )
            }

            val isoRange = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
            )?.let { range ->
                IntRange(range.lower, range.upper)
            }

            val exposureRange = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
            )?.let { range ->
                IntRange(range.lower, range.upper)
            }

            val focusModes = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
            )?.toList() ?: emptyList()

            val flashModes = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES
            )?.toList() ?: emptyList()

            val whiteBalanceModes = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
            )?.toList() ?: emptyList()

            // Query additional camera characteristics
            val apertures = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES
            )

            val focalLengths = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            )

            val exposureTimeRange = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
            )

            val sceneModes = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES
            )

            val effects = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS
            )

            val antiBandingModes = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES
            )

            val colorCorrectionModes = camera2CameraInfo.getCameraCharacteristic(
                CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES
            )

            return CameraCapabilities(
                supportedISO = isoRange,
                supportedExposureCompensation = exposureRange,
                supportedFocusModes = focusModes,
                supportedFlashModes = flashModes,
                supportedWhiteBalance = whiteBalanceModes,
                availableApertures = apertures,
                availableFocalLengths = focalLengths,
                exposureTimeRange = exposureTimeRange,
                availableSceneModes = sceneModes,
                availableEffects = effects,
                availableAntiBandingModes = antiBandingModes,
                availableColorCorrectionModes = colorCorrectionModes
            )
        }
    }

    fun getFocusModeDisplayName(mode: Int): String {
        return when (mode) {
            CaptureRequest.CONTROL_AF_MODE_OFF -> "Manual Focus"
            CaptureRequest.CONTROL_AF_MODE_AUTO -> "Auto Focus"
            CaptureRequest.CONTROL_AF_MODE_MACRO -> "Macro Focus"
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "Continuous Video"
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "Continuous Picture"
            CaptureRequest.CONTROL_AF_MODE_EDOF -> "Extended DOF"
            else -> "Unknown ($mode)"
        }
    }

    fun getFlashModeDisplayName(mode: Int): String {
        return when (mode) {
            CaptureRequest.CONTROL_AE_MODE_OFF -> "Off"
            CaptureRequest.CONTROL_AE_MODE_ON -> "On"
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH -> "Auto Flash"
            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "Always Flash"
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "Auto Flash Red-eye"
            else -> "Unknown ($mode)"
        }
    }

    fun getWhiteBalanceDisplayName(mode: Int): String {
        return when (mode) {
            CaptureRequest.CONTROL_AWB_MODE_OFF -> "Off"
            CaptureRequest.CONTROL_AWB_MODE_AUTO -> "Auto"
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "Incandescent"
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "Fluorescent"
            CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "Warm Fluorescent"
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "Daylight"
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "Cloudy"
            CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "Twilight"
            CaptureRequest.CONTROL_AWB_MODE_SHADE -> "Shade"
            else -> "Unknown ($mode)"
        }
    }
}