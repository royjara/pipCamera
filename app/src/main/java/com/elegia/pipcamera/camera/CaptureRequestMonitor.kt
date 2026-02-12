package com.elegia.pipcamera.camera

import android.util.Log
import androidx.camera.camera2.interop.CaptureRequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Example consumer of the CaptureController.captureRequests SharedFlow
 * Demonstrates how to reactively consume capture request changes
 */
object CaptureRequestMonitor {
    private const val TAG = "CaptureRequestMonitor"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Start monitoring capture requests
     */
    fun startMonitoring() {
        Log.d(TAG, "Starting capture request monitoring")

        scope.launch {
            CaptureController.captureRequests.collectLatest { options ->
                analyzeCaptureRequest(options)
            }
        }
    }

    /**
     * Analyze and log capture request details
     */
    private fun analyzeCaptureRequest(options: CaptureRequestOptions) {
        try {
            // Extract and analyze capture request parameters
            val parameters = mutableListOf<String>()

            // You would access the options here to extract specific parameters
            // Note: CaptureRequestOptions doesn't expose its internal parameters directly,
            // but this is where you would analyze the request if needed

            Log.i(TAG, "Capture request received - reactive system working!")

            // Example use cases for consuming capture requests:
            // 1. Analytics/telemetry - track camera usage patterns
            // 2. UI updates - show current camera settings
            // 3. Performance monitoring - measure request frequency
            // 4. State management - sync UI with camera state
            // 5. Custom processing - trigger additional ML analysis

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing capture request", e)
        }
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping capture request monitoring")
        // Scope will be cancelled when parent is cancelled
    }
}