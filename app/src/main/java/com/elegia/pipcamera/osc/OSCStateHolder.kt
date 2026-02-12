package com.elegia.pipcamera.osc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.util.Log
import androidx.compose.runtime.*
import com.elegia.pipcamera.camera.CaptureController
import com.elegia.pipcamera.preferences.OSCPreferences
import com.elegia.pipcamera.ui.pipelineMenus.OSCTabComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class OSCStateHolder private constructor(private val context: Context) {

    // Registry mapping simplified OSC addresses to CaptureRequest keys
    private val keyRegistry: Map<String, CaptureRequest.Key<out Any>> = mapOf(
        "aeMode" to CaptureRequest.CONTROL_AE_MODE,
        "awbMode" to CaptureRequest.CONTROL_AWB_MODE,
        "afMode" to CaptureRequest.CONTROL_AF_MODE,
        "iso" to CaptureRequest.SENSOR_SENSITIVITY,
        "exposure" to CaptureRequest.SENSOR_EXPOSURE_TIME,
        "compensation" to CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
        "focus" to CaptureRequest.LENS_FOCUS_DISTANCE,
        "zoom" to CaptureRequest.CONTROL_ZOOM_RATIO,
        "sceneMode" to CaptureRequest.CONTROL_SCENE_MODE,
        "effectMode" to CaptureRequest.CONTROL_EFFECT_MODE
    )

    companion object {
        @Volatile
        private var INSTANCE: OSCStateHolder? = null

        fun getInstance(context: Context): OSCStateHolder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OSCStateHolder(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val oscPreferences = OSCPreferences.getInstance(context)

    var oscHost by mutableStateOf(oscPreferences.oscHost)
        private set

    var oscPort by mutableStateOf(oscPreferences.oscPort)
        private set

    var oscAddress by mutableStateOf(oscPreferences.oscAddress)
        private set

    var settingsApplied by mutableStateOf(false)
        private set

    // OSC Listener state
    var isListening by mutableStateOf(false)
        private set

    var listenPort by mutableStateOf(9000)
        private set

    private val _lastReceivedMessage = MutableStateFlow<String>("No messages received yet...")
    val lastReceivedMessage: StateFlow<String> = _lastReceivedMessage

    private var listenerSocket: DatagramSocket? = null
    private var listenerJob: Job? = null

    fun updateHost(host: String) {
        oscHost = host
        settingsApplied = false
    }

    fun updatePort(port: Int) {
        oscPort = port
        settingsApplied = false
    }

    fun updateAddress(address: String) {
        oscAddress = address
        settingsApplied = false
    }

    fun applySettings() {
        oscPreferences.saveOSCConfig(oscHost, oscPort, oscAddress)
        settingsApplied = true
        Log.i("OSCStateHolder", "OSC settings applied: $oscHost:$oscPort$oscAddress")
    }

    fun sendTestMessage() {
        sendOSCMessage("/test", "connection_test")
    }

    fun sendCameraConfigurationSpace() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val modulationSchema = getModulationSchema()

                // Build modulation schema message - tunable keys with their limits
                val configData = buildString {
                    modulationSchema.forEach { (key, limit) ->
                        append("$key=$limit\n")
                    }
                }

                sendOSCMessage("/camera/config", configData)
                Log.i("OSCStateHolder", "Camera modulation schema sent: ${modulationSchema.size} tunable parameters")

            } catch (e: Exception) {
                Log.e("OSCStateHolder", "Failed to send camera configuration: ${e.message}", e)
            }
        }
    }

    private fun getCameraCharacteristicsMap(cameraId: String = "0"): Map<String, String> {
        val configMap = mutableMapOf<String, String>()

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            // Get EVERY key available on this specific hardware
            val allKeys = characteristics.keys

            for (key in allKeys) {
                val value = characteristics.get(key)
                if (value != null) {
                    // Use a clean string representation
                    val stringValue = when (value) {
                        is IntArray -> value.contentToString()
                        is FloatArray -> value.contentToString()
                        is Array<*> -> value.contentDeepToString()
                        else -> value.toString()
                    }
                    configMap[key.name] = stringValue
                }
            }

        } catch (e: Exception) {
            Log.e("OSCStateHolder", "Failed to query camera characteristics: ${e.message}", e)
            configMap["error"] = "Failed to query camera: ${e.message}"
        }

        return configMap
    }

    private fun getModulationSchema(cameraId: String = "0"): Map<String, Any> {
        val schema = mutableMapOf<String, Any>()

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
            val char = cameraManager.getCameraCharacteristics(cameraId)

            // 1. Get the list of every "knob" we can turn
            val requestKeys = char.availableCaptureRequestKeys

            for (key in requestKeys) {
                val keyName = key.name

                // 2. Dynamically find the "Limit" for this key
                // We map the RequestKey to its corresponding Info/Range Characteristic
                val limit = when {
                    keyName.contains("exposure", ignoreCase = true) ->
                        char.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

                    keyName.contains("sensitivity", ignoreCase = true) ->
                        char.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

                    keyName.contains("awb.mode", ignoreCase = true) ->
                        char.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)

                    keyName.contains("af.mode", ignoreCase = true) ->
                        char.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)

                    keyName.contains("ae.mode", ignoreCase = true) ->
                        char.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)

                    keyName.contains("zoom", ignoreCase = true) ->
                        char.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)

                    keyName.contains("scene", ignoreCase = true) ->
                        char.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)

                    keyName.contains("effect", ignoreCase = true) ->
                        char.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)

                    keyName.contains("antibanding", ignoreCase = true) ->
                        char.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)

                    keyName.contains("compensation", ignoreCase = true) ->
                        char.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)

                    keyName.contains("focus.distance", ignoreCase = true) ->
                        char.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)

                    keyName.contains("aperture", ignoreCase = true) ->
                        char.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)

                    keyName.contains("focal", ignoreCase = true) ->
                        char.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

                    else -> null  // Skip parameters we can't map to specific limits
                }

                // Only include parameters where we found a useful limit
                if (limit != null) {
                    // Format the limit value properly
                    val limitString = when (limit) {
                        is IntArray -> limit.contentToString()
                        is FloatArray -> limit.contentToString()
                        is Array<*> -> limit.contentDeepToString()
                        else -> limit.toString()
                    }

                    schema[keyName] = limitString
                }
            }

        } catch (e: Exception) {
            Log.e("OSCStateHolder", "Failed to map modulation schema: ${e.message}", e)
            schema["error"] = "Failed to query modulation schema: ${e.message}"
        }

        return schema
    }

    private fun processCameraControlMessage(message: String) {
        try {
            // Parse format: "timestamp: /oscaddress value"
            val colonIndex = message.indexOf(": ")
            if (colonIndex == -1) return

            val content = message.substring(colonIndex + 2).trim()
            val spaceIndex = content.indexOf(' ')
            if (spaceIndex == -1) return

            val oscAddress = content.substring(0, spaceIndex).trim()
            val valueString = content.substring(spaceIndex + 1).trim()

            // Extract request key from OSC address (remove leading /)
            val requestKey = if (oscAddress.startsWith("/")) {
                oscAddress.substring(1)
            } else {
                oscAddress
            }

            Log.d("OSCStateHolder", "Processing camera control: $requestKey = $valueString")

            // Convert OSC address to camera capture request and apply the value
            applyCameraControl(requestKey, valueString)

        } catch (e: Exception) {
            Log.e("OSCStateHolder", "Failed to process camera control message: $message", e)
        }
    }

    private fun applyCameraControl(requestKey: String, valueString: String) {
        try {
            // Look up the capture request key using the registry
            val captureKey = keyRegistry[requestKey]
            if (captureKey == null) {
                Log.d("OSCStateHolder", "Unmapped camera control parameter: $requestKey")
                return
            }

            // Convert value based on key type and submit to capture controller
            when (captureKey) {
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.SENSOR_SENSITIVITY,
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                CaptureRequest.CONTROL_SCENE_MODE,
                CaptureRequest.CONTROL_EFFECT_MODE -> {
                    val value = valueString.toIntOrNull()
                    if (value != null) {
                        @Suppress("UNCHECKED_CAST")
                        CaptureController.submitCaptureRequest(captureKey as CaptureRequest.Key<Int>, value)
                        Log.i("OSCStateHolder", "Applied $requestKey: $value")
                    } else {
                        Log.w("OSCStateHolder", "Invalid integer value for $requestKey: $valueString")
                    }
                }

                CaptureRequest.SENSOR_EXPOSURE_TIME -> {
                    val value = valueString.toLongOrNull()
                    if (value != null) {
                        @Suppress("UNCHECKED_CAST")
                        CaptureController.submitCaptureRequest(captureKey as CaptureRequest.Key<Long>, value)
                        Log.i("OSCStateHolder", "Applied $requestKey: $value ns")
                    } else {
                        Log.w("OSCStateHolder", "Invalid long value for $requestKey: $valueString")
                    }
                }

                CaptureRequest.LENS_FOCUS_DISTANCE,
                CaptureRequest.CONTROL_ZOOM_RATIO -> {
                    val value = valueString.toFloatOrNull()
                    if (value != null) {
                        @Suppress("UNCHECKED_CAST")
                        CaptureController.submitCaptureRequest(captureKey as CaptureRequest.Key<Float>, value)
                        Log.i("OSCStateHolder", "Applied $requestKey: $value")
                    } else {
                        Log.w("OSCStateHolder", "Invalid float value for $requestKey: $valueString")
                    }
                }

                else -> {
                    Log.d("OSCStateHolder", "Unsupported key type for $requestKey")
                }
            }

        } catch (e: Exception) {
            Log.e("OSCStateHolder", "Failed to apply camera control: $requestKey = $valueString", e)
        }
    }

    fun sendOSCMessage(channel: String, message: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(oscHost)

                val oscPacket = createOSCMessage(channel, message)

                val packet = DatagramPacket(oscPacket, oscPacket.size, address, oscPort)
                socket.send(packet)
                socket.close()

                Log.i("OSCStateHolder", "OSC message sent: $channel -> $message (${oscPacket.size} bytes)")
            } catch (e: Exception) {
                Log.e("OSCStateHolder", "Failed to send OSC message: ${e.message}", e)
            }
        }
    }

    private fun createOSCMessage(address: String, stringArg: String): ByteArray {
        // OSC Message format:
        // 1. Address pattern (null-terminated, padded to 4-byte boundary)
        // 2. Type tag string (null-terminated, padded to 4-byte boundary)
        // 3. Arguments (each padded to 4-byte boundary)

        val addressBytes = padToFourBytes((address + '\u0000').toByteArray(Charsets.UTF_8))
        val typeTagBytes = padToFourBytes(",s\u0000".toByteArray(Charsets.UTF_8)) // ,s = string argument
        val argBytes = padToFourBytes((stringArg + '\u0000').toByteArray(Charsets.UTF_8))

        return addressBytes + typeTagBytes + argBytes
    }

    private fun padToFourBytes(bytes: ByteArray): ByteArray {
        val remainder = bytes.size % 4
        if (remainder == 0) return bytes

        val paddingSize = 4 - remainder
        return bytes + ByteArray(paddingSize) // pad with zeros
    }

    fun updateListenPort(port: Int) {
        if (port in 1..65535) {
            listenPort = port
        }
    }

    fun startListening() {
        if (isListening) return

        try {
            listenerSocket = DatagramSocket(listenPort)
            listenerSocket?.soTimeout = 1000 // 1 second timeout for non-blocking operation
            isListening = true

            listenerJob = GlobalScope.launch(Dispatchers.IO) {
                _lastReceivedMessage.value = "Listening on port $listenPort..."
                Log.d("OSCListener", "Started listening on port $listenPort")

                val buffer = ByteArray(1024)
                while (isListening) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        listenerSocket?.receive(packet)

                        val receivedData = packet.data.copyOf(packet.length)
                        val message = parseOSCMessage(receivedData)

                        _lastReceivedMessage.value = "${System.currentTimeMillis()}: $message"
                        Log.d("OSCListener", "Received: $message")

                        // Process camera control messages
                        processCameraControlMessage("${System.currentTimeMillis()}: $message")

                    } catch (e: SocketTimeoutException) {
                        // Normal timeout, continue listening
                        continue
                    } catch (e: Exception) {
                        if (isListening) {
                            Log.e("OSCListener", "Error receiving OSC message", e)
                            _lastReceivedMessage.value = "Error: ${e.message}"
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OSCListener", "Failed to start listening", e)
            _lastReceivedMessage.value = "Failed to start: ${e.message}"
            isListening = false
        }
    }

    fun stopListening() {
        isListening = false
        listenerJob?.cancel()
        listenerJob = null

        try {
            listenerSocket?.close()
            listenerSocket = null
            _lastReceivedMessage.value = "Stopped listening"
            Log.d("OSCListener", "Stopped listening")
        } catch (e: Exception) {
            Log.e("OSCListener", "Error stopping listener", e)
        }
    }

    private fun findNullTerminator(data: ByteArray, startOffset: Int): Int {
        for (i in startOffset until data.size) {
            if (data[i].toInt() == 0) return i
        }
        return -1
    }

    private fun parseOSCMessage(data: ByteArray): String {
        try {
            // Simple OSC message parser
            var offset = 0

            // Parse address pattern
            val addressEnd = findNullTerminator(data, offset)
            if (addressEnd == -1) return "Invalid OSC message (no null terminator)"

            val address = String(data, offset, addressEnd - offset, Charsets.UTF_8)
            offset = ((addressEnd + 4) / 4) * 4 // Skip to 4-byte boundary

            if (offset >= data.size) return "$address (no type tags)"

            // Parse type tag string
            val typeTagEnd = findNullTerminator(data, offset)
            if (typeTagEnd == -1) return "$address (invalid type tags)"

            val typeTags = String(data, offset, typeTagEnd - offset, Charsets.UTF_8)
            offset = ((typeTagEnd + 4) / 4) * 4 // Skip to 4-byte boundary

            // Parse arguments based on type tags
            val args = mutableListOf<String>()
            if (typeTags.startsWith(",")) {
                for (i in 1 until typeTags.length) {
                    when (typeTags[i]) {
                        's' -> {
                            // String argument
                            if (offset >= data.size) break
                            val stringEnd = findNullTerminator(data, offset)
                            if (stringEnd == -1) break

                            val stringArg = String(data, offset, stringEnd - offset, Charsets.UTF_8)
                            args.add("\"$stringArg\"")
                            offset = ((stringEnd + 4) / 4) * 4 // Skip to 4-byte boundary
                        }
                        'i' -> {
                            // Integer argument (32-bit big-endian)
                            if (offset + 4 > data.size) break
                            val intArg = ((data[offset].toInt() and 0xFF) shl 24) or
                                       ((data[offset + 1].toInt() and 0xFF) shl 16) or
                                       ((data[offset + 2].toInt() and 0xFF) shl 8) or
                                       (data[offset + 3].toInt() and 0xFF)
                            args.add(intArg.toString())
                            offset += 4
                        }
                        'f' -> {
                            // Float argument (32-bit big-endian IEEE 754)
                            if (offset + 4 > data.size) break
                            val intBits = ((data[offset].toInt() and 0xFF) shl 24) or
                                        ((data[offset + 1].toInt() and 0xFF) shl 16) or
                                        ((data[offset + 2].toInt() and 0xFF) shl 8) or
                                        (data[offset + 3].toInt() and 0xFF)
                            val floatArg = Float.fromBits(intBits)
                            args.add(floatArg.toString())
                            offset += 4
                        }
                        // Add more type handlers as needed
                    }
                }
            }

            return if (args.isNotEmpty()) {
                "$address ${args.joinToString(" ")}"
            } else {
                address
            }

        } catch (e: Exception) {
            Log.e("OSCParser", "Error parsing OSC message", e)
            return "Parse error: ${e.message}"
        }
    }

    @Composable
    fun OSCConfigurationUI() {
        OSCTabComponent(
            oscHost = oscHost,
            oscPort = oscPort,
            oscAddress = oscAddress,
            settingsApplied = settingsApplied,
            onHostChange = ::updateHost,
            onPortChange = ::updatePort,
            onAddressChange = ::updateAddress,
            onApplySettings = ::applySettings
        )
    }
}