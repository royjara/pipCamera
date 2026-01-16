package com.elegia.pipcamera.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PiPManager(private val activity: Activity) {
    private val _isPiPMode = MutableStateFlow(false)
    val isPiPMode: StateFlow<Boolean> = _isPiPMode

    fun updatePiPMode(isInPiP: Boolean) {
        _isPiPMode.value = isInPiP
    }

    fun enterPiPMode(): Boolean {
        if (!isPiPSupported()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = createPiPParams()
            try {
                activity.enterPictureInPictureMode(params)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPiPParams(): PictureInPictureParams {
        // Camera aspect ratio is typically 4:3 or 16:9
        val aspectRatio = Rational(16, 9)

        return PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .build()
    }

    private fun isPiPSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
}