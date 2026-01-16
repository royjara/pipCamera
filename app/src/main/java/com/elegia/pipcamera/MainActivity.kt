package com.elegia.pipcamera

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.elegia.pipcamera.pip.PiPManager
import com.elegia.pipcamera.ui.CameraScreen
import com.elegia.pipcamera.ui.theme.PipCameraTheme

class MainActivity : ComponentActivity() {
    private lateinit var pipManager: PiPManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pipManager = PiPManager(this)

        setContent {
            val isPiPMode by pipManager.isPiPMode.collectAsState()

            PipCameraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraScreen(isPiPMode = isPiPMode)
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatically enter PiP when user presses home button
        pipManager.enterPiPMode()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipManager.updatePiPMode(isInPictureInPictureMode)
    }

}