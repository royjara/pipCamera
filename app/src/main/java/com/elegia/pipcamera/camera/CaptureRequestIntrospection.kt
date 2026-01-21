package com.elegia.pipcamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.hardware.camera2.CaptureRequest
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Data class to represent a capture request key with its available options
 */
data class CaptureRequestOption(
    val key: String,
    val displayName: String,
    val availableValues: List<Pair<String, Int>>, // Display name to actual value mapping
    val currentValue: Int?,
    val updateFunction: (CameraManager, Int) -> Unit // Function to call when value is selected
)

/**
 * Utility class for introspecting camera capture request capabilities
 */
class CaptureRequestIntrospection(private val context: Context) {

    fun getAWBModeOptions(cameraId: String = "0"): CaptureRequestOption {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        val availableModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()

        val modeMap = mapOf(
            CaptureRequest.CONTROL_AWB_MODE_OFF to "OFF",
            CaptureRequest.CONTROL_AWB_MODE_AUTO to "AUTO",
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT to "INCANDESCENT",
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT to "FLUORESCENT",
            CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT to "WARM_FLUORESCENT",
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT to "DAYLIGHT",
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "CLOUDY_DAYLIGHT",
            CaptureRequest.CONTROL_AWB_MODE_TWILIGHT to "TWILIGHT",
            CaptureRequest.CONTROL_AWB_MODE_SHADE to "SHADE"
        )

        val availableOptions = availableModes.toList().mapNotNull { mode ->
            modeMap[mode]?.let { displayName ->
                Pair(displayName, mode)
            }
        }

        return CaptureRequestOption(
            key = "android.control.awbMode",
            displayName = "Auto White Balance Mode",
            availableValues = availableOptions,
            currentValue = null, // Will be set by caller if needed
            updateFunction = { cameraManager, value -> cameraManager.updateAWBMode(value) }
        )
    }

    fun getAFModeOptions(cameraId: String = "0"): CaptureRequestOption {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        val availableModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()

        val modeMap = mapOf(
            CaptureRequest.CONTROL_AF_MODE_OFF to "OFF",
            CaptureRequest.CONTROL_AF_MODE_AUTO to "AUTO",
            CaptureRequest.CONTROL_AF_MODE_MACRO to "MACRO",
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO to "CONTINUOUS_VIDEO",
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE to "CONTINUOUS_PICTURE",
            CaptureRequest.CONTROL_AF_MODE_EDOF to "EDOF"
        )

        val availableOptions = availableModes.toList().mapNotNull { mode ->
            modeMap[mode]?.let { displayName ->
                Pair(displayName, mode)
            }
        }

        return CaptureRequestOption(
            key = "android.control.afMode",
            displayName = "Auto Focus Mode",
            availableValues = availableOptions,
            currentValue = null,
            updateFunction = { cameraManager, value -> cameraManager.updateAFMode(value) }
        )
    }

    fun getAEModeOptions(cameraId: String = "0"): CaptureRequestOption {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        val availableModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()

        val modeMap = mapOf(
            CaptureRequest.CONTROL_AE_MODE_OFF to "OFF",
            CaptureRequest.CONTROL_AE_MODE_ON to "ON",
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH to "ON_AUTO_FLASH",
            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH to "ON_ALWAYS_FLASH",
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE to "ON_AUTO_FLASH_REDEYE",
            CaptureRequest.CONTROL_AE_MODE_ON_EXTERNAL_FLASH to "ON_EXTERNAL_FLASH"
        )

        val availableOptions = availableModes.toList().mapNotNull { mode ->
            modeMap[mode]?.let { displayName ->
                Pair(displayName, mode)
            }
        }

        return CaptureRequestOption(
            key = "android.control.aeMode",
            displayName = "Auto Exposure Mode",
            availableValues = availableOptions,
            currentValue = null,
            updateFunction = { cameraManager, value -> cameraManager.updateAEMode(value) }
        )
    }

    /**
     * Get all available capture request options for the device
     */
    fun getAllCaptureRequestOptions(cameraId: String = "0"): List<CaptureRequestOption> {
        return listOf(
            getAWBModeOptions(cameraId),
            getAFModeOptions(cameraId),
            getAEModeOptions(cameraId)
        ).filter { it.availableValues.isNotEmpty() }
    }
}

/**
 * Composable to remember CaptureRequestIntrospection instance
 */
@Composable
fun rememberCaptureRequestIntrospection(): CaptureRequestIntrospection {
    val context = LocalContext.current
    return remember { CaptureRequestIntrospection(context) }
}