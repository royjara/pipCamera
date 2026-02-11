package com.elegia.pipcamera

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

        val shouldStartInPip = intent.getBooleanExtra("launch_pip", false)
        if (shouldStartInPip) {
            // We use post to ensure the activity is laid out before entering PiP
            window.decorView.post {
                pipManager.enterPiPMode()
            }
        }

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