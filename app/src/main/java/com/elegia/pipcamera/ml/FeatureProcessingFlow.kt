package com.elegia.pipcamera.ml

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Manages the flow of features from input through processing to output
 * Implements a pub-sub pattern for ML feature processing pipeline
 */
object FeatureProcessingFlow {
    private const val TAG = "FeatureProcessingFlow"

    // Input features stream
    private val _inputFeatures = MutableSharedFlow<List<Float>>(
        replay = 1,
        extraBufferCapacity = 10
    )

    // Processed output stream
    private val _processedOutput = MutableSharedFlow<ProcessedResult>(
        replay = 1,
        extraBufferCapacity = 10
    )

    // Public flows for subscription
    val inputFeatures: SharedFlow<List<Float>> = _inputFeatures.asSharedFlow()
    val processedOutput: SharedFlow<ProcessedResult> = _processedOutput.asSharedFlow()

    // Current processor name
    private var currentProcessorName = "Weighted Sum"

    /**
     * Data class for processed results
     */
    data class ProcessedResult(
        val originalFeatures: List<Float>,
        val processedValue: Float,
        val processorName: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Initialize the processing pipeline
     */
    fun initialize(scope: CoroutineScope) {
        // Set up the processing pipeline
        scope.launch {
            inputFeatures
                .onEach { features ->
                    Log.d(TAG, "Processing features: $features with processor: $currentProcessorName")
                }
                .map { features ->
                    // Process features through the selected processor
                    val processedValue = try {
                        ProcessorRegistry.processFeatures(features)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing features", e)
                        0f
                    }

                    ProcessedResult(
                        originalFeatures = features,
                        processedValue = processedValue,
                        processorName = currentProcessorName
                    )
                }
                .collect { result ->
                    // Emit the processed result
                    _processedOutput.emit(result)
                    Log.d(TAG, "Emitted processed result: ${result.processedValue} from ${result.processorName}")
                }
        }
    }

    /**
     * Publish input features to the stream
     */
    suspend fun publishFeatures(features: List<Float>) {
        if (features.isNotEmpty()) {
            _inputFeatures.emit(features)
        }
    }

    /**
     * Update the current processor being used
     */
    fun setProcessor(processorName: String) {
        if (ProcessorRegistry.setCurrentProcessor(processorName)) {
            currentProcessorName = processorName
            Log.d(TAG, "Processor updated to: $processorName")
        } else {
            Log.w(TAG, "Failed to set processor: $processorName")
        }
    }

    /**
     * Get current processor name
     */
    fun getCurrentProcessor(): String = currentProcessorName

    /**
     * Get processing statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "currentProcessor" to currentProcessorName,
            "isInitialized" to true
        )
    }
}