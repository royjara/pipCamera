package com.elegia.pipcamera.audio

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elegia.pipcamera.preferences.OSCPreferences
import com.elegia.pipcamera.ui.pipelineMenus.AudioSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Data classes for grouped state management per tab
 */
data class OSCTabState(
    val host: String = "127.0.0.1",
    val port: Int = 8000,
    val address: String = "/audio/stream",
    val settingsApplied: Boolean = false
)

data class AudioTabState(
    val selectedAudioSource: AudioSource = AudioSource.SINE_WAVE_440HZ,
    val microphoneGain: Float = 1.0f,
    val enableNoiseReduction: Boolean = false,
    val customFrequency: Float = 440f
)

data class MessagesTabState(
    val textMessageEnabled: Boolean = false
)

data class ImagesTabState(
    val selectedAlgorithm: String = "J48",
    val dimensions: Int = 10,
    val learningRate: Float = 0.1f
)

data class CoreProcessingState(
    val nodeState: ProcessingNodeState = ProcessingNodeState.IDLE,
    val isStreamActive: Boolean = false,
    val parameterRefreshTrigger: Int = 0
)

data class UIState(
    val selectedTabIndex: Int = 0
)

/**
 * Manager for Audio Demo state following the CameraManager pattern
 * Handles all audio processing pipeline state and configuration persistence
 */
class AudioDemoManager {
    companion object {
        private const val TAG = "AudioDemoManager"
    }

    // Grouped state management
    private val _oscTabState = MutableStateFlow(OSCTabState())
    val oscTabState: StateFlow<OSCTabState> = _oscTabState

    private val _audioTabState = MutableStateFlow(AudioTabState())
    val audioTabState: StateFlow<AudioTabState> = _audioTabState

    private val _messagesTabState = MutableStateFlow(MessagesTabState())
    val messagesTabState: StateFlow<MessagesTabState> = _messagesTabState

    private val _imagesTabState = MutableStateFlow(ImagesTabState())
    val imagesTabState: StateFlow<ImagesTabState> = _imagesTabState

    private val _coreProcessingState = MutableStateFlow(CoreProcessingState())
    val coreProcessingState: StateFlow<CoreProcessingState> = _coreProcessingState

    private val _uiState = MutableStateFlow(UIState())
    val uiState: StateFlow<UIState> = _uiState

    // Internal state
    private var oscPreferences: OSCPreferences? = null
    private var sineProcessor: SineGeneratorProcessor? = null
    private var context: Context? = null

    fun initialize(context: Context) {
        Log.d(TAG, "initialize: Initializing AudioDemoManager")
        this.context = context
        this.oscPreferences = OSCPreferences.getInstance(context)
        this.sineProcessor = SineGeneratorProcessor()

        // Load cached OSC configuration
        loadOSCConfiguration()

        Log.d(TAG, "initialize: AudioDemoManager initialized successfully")
    }

    private fun loadOSCConfiguration() {
        oscPreferences?.let { prefs ->
            val (cachedHost, cachedPort, cachedAddress) = prefs.getOSCConfig()
            _oscTabState.value = _oscTabState.value.copy(
                host = cachedHost,
                port = cachedPort,
                address = cachedAddress
            )
            Log.d(TAG, "loadOSCConfiguration: Loaded cached OSC config - $cachedHost:$cachedPort$cachedAddress")
        }
    }

    // OSC Configuration methods
    fun updateOscHost(host: String) {
        _oscTabState.value = _oscTabState.value.copy(
            host = host,
            settingsApplied = false
        )
    }

    fun updateOscPort(port: Int) {
        _oscTabState.value = _oscTabState.value.copy(
            port = port,
            settingsApplied = false
        )
    }

    fun updateOscAddress(address: String) {
        _oscTabState.value = _oscTabState.value.copy(
            address = address,
            settingsApplied = false
        )
    }

    fun applyOscSettings() {
        val currentOsc = _oscTabState.value
        Log.d(TAG, "applyOscSettings: Applying OSC settings - ${currentOsc.host}:${currentOsc.port}${currentOsc.address}")

        // Save to preferences
        oscPreferences?.saveOSCConfig(currentOsc.host, currentOsc.port, currentOsc.address)

        // Update processor
        sineProcessor?.apply {
            updateParameter("oscHost", currentOsc.host)
            updateParameter("oscPort", currentOsc.port)
            updateParameter("oscAddress", currentOsc.address)
        }

        _coreProcessingState.value = _coreProcessingState.value.copy(
            parameterRefreshTrigger = _coreProcessingState.value.parameterRefreshTrigger + 1
        )

        _oscTabState.value = _oscTabState.value.copy(settingsApplied = true)

        Log.d(TAG, "applyOscSettings: OSC settings applied successfully")
    }

    // Stream control methods
    fun startStream() {
        val currentCore = _coreProcessingState.value
        if (currentCore.isStreamActive) {
            Log.w(TAG, "startStream: Stream already active")
            return
        }

        Log.d(TAG, "startStream: Starting audio stream")

        _coreProcessingState.value = currentCore.copy(isStreamActive = true)
        sineProcessor?.updateParameter("streamEnabled", true)

        Log.d(TAG, "startStream: Audio stream started successfully")
    }

    fun stopStream() {
        val currentCore = _coreProcessingState.value
        if (!currentCore.isStreamActive) {
            Log.w(TAG, "stopStream: Stream not active")
            return
        }

        Log.d(TAG, "stopStream: Stopping audio stream")

        sineProcessor?.updateParameter("streamEnabled", false)
        _coreProcessingState.value = currentCore.copy(isStreamActive = false)

        Log.d(TAG, "stopStream: Audio stream stopped successfully")
    }

    // Modal UI methods
    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTabIndex = index)
    }

    // Feature toggle methods

    fun toggleTextMessage() {
        val current = _messagesTabState.value
        _messagesTabState.value = current.copy(textMessageEnabled = !current.textMessageEnabled)
        Log.d(TAG, "toggleTextMessage: Text message ${if (!current.textMessageEnabled) "enabled" else "disabled"}")
    }

    // Audio source configuration methods
    fun updateAudioSource(source: AudioSource) {
        _audioTabState.value = _audioTabState.value.copy(selectedAudioSource = source)
        Log.d(TAG, "updateAudioSource: Audio source changed to $source")
    }

    fun updateMicrophoneGain(gain: Float) {
        _audioTabState.value = _audioTabState.value.copy(microphoneGain = gain)
        Log.d(TAG, "updateMicrophoneGain: Microphone gain set to $gain")
    }

    fun toggleNoiseReduction() {
        val current = _audioTabState.value
        _audioTabState.value = current.copy(enableNoiseReduction = !current.enableNoiseReduction)
        Log.d(TAG, "toggleNoiseReduction: Noise reduction ${if (!current.enableNoiseReduction) "enabled" else "disabled"}")
    }

    fun updateCustomFrequency(frequency: Float) {
        _audioTabState.value = _audioTabState.value.copy(customFrequency = frequency)
        Log.d(TAG, "updateCustomFrequency: Custom frequency set to $frequency Hz")
    }

    // ML configuration methods
    fun updateAlgorithm(algorithm: String) {
        _imagesTabState.value = _imagesTabState.value.copy(selectedAlgorithm = algorithm)
        Log.d(TAG, "updateAlgorithm: Algorithm changed to $algorithm")
    }

    fun updateDimensions(dims: Int) {
        _imagesTabState.value = _imagesTabState.value.copy(dimensions = dims)
        Log.d(TAG, "updateDimensions: Dimensions set to $dims")
    }

    fun updateLearningRate(rate: Float) {
        _imagesTabState.value = _imagesTabState.value.copy(learningRate = rate)
        Log.d(TAG, "updateLearningRate: Learning rate set to $rate")
    }

    // Node state management
    fun updateNodeState(state: ProcessingNodeState) {
        _coreProcessingState.value = _coreProcessingState.value.copy(nodeState = state)
        Log.d(TAG, "updateNodeState: Node state changed to $state")
    }

    // Processor access
    fun getSineProcessor(): SineGeneratorProcessor? = sineProcessor


    // Cleanup
    fun shutdown() {
        Log.d(TAG, "shutdown: Shutting down AudioDemoManager")

        if (_coreProcessingState.value.isStreamActive) {
            stopStream()
        }

        sineProcessor = null
        oscPreferences = null
        context = null

        // Reset all state
        _oscTabState.value = OSCTabState()
        _audioTabState.value = AudioTabState()
        _messagesTabState.value = MessagesTabState()
        _imagesTabState.value = ImagesTabState()
        _coreProcessingState.value = CoreProcessingState()
        _uiState.value = UIState()

        Log.d(TAG, "shutdown: AudioDemoManager shutdown complete")
    }
}

/**
 * Composable function to remember AudioDemoManager instance with proper lifecycle management
 * Follows the same pattern as rememberCameraManager()
 */
@Composable
fun rememberAudioDemoManager(): AudioDemoManager {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val audioDemoManager = remember { AudioDemoManager() }

    DisposableEffect(lifecycleOwner) {
        audioDemoManager.initialize(context)

        onDispose {
            audioDemoManager.shutdown()
        }
    }

    return audioDemoManager
}