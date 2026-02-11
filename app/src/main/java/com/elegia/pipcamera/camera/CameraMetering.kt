package com.elegia.pipcamera.camera

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CaptureResult.Key

data class CameraMetering(
    val allCaptureKeys: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(result: CaptureResult): CameraMetering {
            return CameraMetering(
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

    // Derived properties - extract from allCaptureKeys using correct field names
    val focusMode: Int?
        get() = allCaptureKeys["CONTROL_AF_MODE"]?.toIntOrNull()

    val focusState: Int?
        get() = allCaptureKeys["CONTROL_AF_STATE"]?.toIntOrNull()

    val exposureTime: Long?
        get() = allCaptureKeys["SENSOR_EXPOSURE_TIME"]?.toLongOrNull()

    val iso: Int?
        get() = allCaptureKeys["SENSOR_SENSITIVITY"]?.toIntOrNull()

    val aperture: Float?
        get() = allCaptureKeys["LENS_APERTURE"]?.toFloatOrNull()

    val focusDistance: Float?
        get() = allCaptureKeys["LENS_FOCUS_DISTANCE"]?.toFloatOrNull()

    val exposureCompensation: Int?
        get() = allCaptureKeys["CONTROL_AE_EXPOSURE_COMPENSATION"]?.toIntOrNull()

    val whiteBalanceMode: Int?
        get() = allCaptureKeys["CONTROL_AWB_MODE"]?.toIntOrNull()

    val aeMode: Int?
        get() = allCaptureKeys["CONTROL_AE_MODE"]?.toIntOrNull()

    // Helper function to debug what keys are actually available
    fun debugKeys(): String {
        return allCaptureKeys.keys.filter {
            it.contains("CONTROL_", ignoreCase = true) ||
            it.contains("AWB", ignoreCase = true) ||
            it.contains("AF", ignoreCase = true) ||
            it.contains("AE", ignoreCase = true)
        }.sorted().joinToString(", ")
    }

    // Filter capture keys by search term
    fun getFilteredKeys(filter: String): Map<String, String> {
        return if (filter.isBlank()) {
            allCaptureKeys
        } else {
            allCaptureKeys.filterKeys { key ->
                key.contains(filter, ignoreCase = true) ||
                allCaptureKeys[key]?.contains(filter, ignoreCase = true) == true
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