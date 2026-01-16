package com.elegia.pipcamera.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.elegia.pipcamera.camera.rememberCameraManager

@Composable
fun CameraScreen(isPiPMode: Boolean = false) {
    PermissionHandler {
        CameraPreview(isPiPMode = isPiPMode)
    }
}

@Composable
private fun CameraPreview(isPiPMode: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = rememberCameraManager()

    val capabilities by cameraManager.capabilities.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = if (isPiPMode) {
                PreviewView.ScaleType.FIT_CENTER
            } else {
                PreviewView.ScaleType.FILL_CENTER
            }
        }
    }

    LaunchedEffect(previewView) {
        cameraManager.initializeCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView
        )
    }

    // Update preview scale type when PiP mode changes
    LaunchedEffect(isPiPMode) {
        previewView.scaleType = if (isPiPMode) {
            PreviewView.ScaleType.FIT_CENTER
        } else {
            PreviewView.ScaleType.FILL_CENTER
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isPiPMode) Modifier.clip(CircleShape) else Modifier)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Camera controls overlay
        CameraControls(
            capabilities = capabilities,
            isPiPMode = isPiPMode
        )
    }
}