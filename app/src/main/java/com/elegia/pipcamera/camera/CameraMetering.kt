package com.elegia.pipcamera.camera

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CaptureResult.Key
import java.lang.reflect.Field

data class CameraMetering(
    val focusMode: Int? = null,
    val focusState: Int? = null,
    val exposureTime: Long? = null,
    val iso: Int? = null,
    val aperture: Float? = null,
    val focusDistance: Float? = null,
    val exposureCompensation: Int? = null,
    val whiteBalanceMode: Int? = null,
    val allCaptureKeys: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(result: CaptureResult): CameraMetering {
            return CameraMetering(
                focusMode = result.get(CaptureResult.CONTROL_AF_MODE),
                focusState = result.get(CaptureResult.CONTROL_AF_STATE),
                exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
                aperture = result.get(CaptureResult.LENS_APERTURE),
                focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                exposureCompensation = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION),
                whiteBalanceMode = result.get(CaptureResult.CONTROL_AWB_MODE),
                allCaptureKeys = getAllCaptureKeys(result)
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun getAllCaptureKeys(result: CaptureResult): Map<String, String> {
            val captureKeys = mutableMapOf<String, String>()

            try {
                // Get all public static fields of CaptureResult that are Key<*> types
                val fields = CaptureResult::class.java.fields

                for (field in fields) {
                    try {
                        // Check if field is a Key type
                        if (field.type == Key::class.java) {
                            val key = field.get(null) as? Key<Any>
                            key?.let {
                                val value = result.get(it)
                                if (value != null) {
                                    captureKeys[field.name] = formatCaptureValue(value)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip this field if we can't access it
                        continue
                    }
                }

                // Also try to get vendor-specific keys by iterating through result keys
                try {
                    val keysMethod = result::class.java.getMethod("getKeys")
                    val keys = keysMethod.invoke(result) as? List<Key<*>>

                    keys?.forEach { key ->
                        try {
                            val value = result.get(key as Key<Any>)
                            if (value != null) {
                                val keyName = key.name ?: key.toString()
                                captureKeys[keyName] = formatCaptureValue(value)
                            }
                        } catch (e: Exception) {
                            // Skip this key if we can't get its value
                        }
                    }
                } catch (e: Exception) {
                    // getKeys() method not available or failed
                }

            } catch (e: Exception) {
                // Reflection failed, return what we have
            }

            return captureKeys.toSortedMap()
        }

        private fun formatCaptureValue(value: Any): String {
            return when (value) {
                is Array<*> -> value.contentToString()
                is IntArray -> value.contentToString()
                is FloatArray -> value.contentToString()
                is LongArray -> value.contentToString()
                is ByteArray -> value.contentToString()
                else -> value.toString()
            }
        }
    }

    fun getFocusStateDisplayName(): String {
        return when (focusState) {
            CaptureResult.CONTROL_AF_STATE_INACTIVE -> "Inactive"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> "Scanning"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "Focused"
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> "Active Scan"
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> "Locked"
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "Not Focused"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> "Unfocused"
            else -> "Unknown"
        }
    }

    fun getExposureTimeDisplayValue(): String {
        return exposureTime?.let {
            if (it > 1_000_000) {
                "1/${(1_000_000_000L / it).toInt()}s"
            } else {
                "${it / 1_000_000f}ms"
            }
        } ?: "N/A"
    }

    fun getISODisplayValue(): String {
        return iso?.toString() ?: "N/A"
    }

    fun getApertureDisplayValue(): String {
        return aperture?.let { "f/$it" } ?: "N/A"
    }
}