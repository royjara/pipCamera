package com.elegia.pipcamera.camera

import android.hardware.camera2.CaptureResult

data class CameraMetering(
    val focusMode: Int? = null,
    val focusState: Int? = null,
    val exposureTime: Long? = null,
    val iso: Int? = null,
    val aperture: Float? = null,
    val focusDistance: Float? = null,
    val exposureCompensation: Int? = null,
    val whiteBalanceMode: Int? = null
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
                whiteBalanceMode = result.get(CaptureResult.CONTROL_AWB_MODE)
            )
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