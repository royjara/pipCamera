package com.elegia.pipcamera.ml

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import com.elegia.pipcamera.ml.FeatureProcessingFlow.currentProcessor

/**
 * Type alias for feature processing functions
 */
typealias ProcessorFunction = (List<Float>) -> Float

/**
 * Predefined processor functions
 */
object ProcessorFunctions {
    /**
     * Reset processor that bypasses processing (returns first feature or 0)
     */
    val resetProcessor: ProcessorFunction = { features ->
        features.firstOrNull() ?: 0f
    }

    /**
     * Identity processor that returns average of features
     */
    val averageProcessor: ProcessorFunction = { features ->
        if (features.isNotEmpty()) features.average().toFloat() else 0f
    }

    /**
     * Simple weighted sum processor
     */
    val weightedSumProcessor: ProcessorFunction = { features ->
        if (features.isNotEmpty()) {
            val weights = listOf(0.3f, 0.2f, 0.1f, 0.15f, 0.1f, 0.05f, 0.05f, 0.05f)
            features.take(8).mapIndexed { index, feature ->
                feature * weights.getOrElse(index) { 0.1f }
            }.sum().coerceIn(-1f, 1f)
        } else 0f
    }

    /**
     * Registry-based processor that uses ProcessorRegistry (non-suspend version)
     */
    val registryProcessor: ProcessorFunction = { features ->
        // Create a simple synchronous wrapper for the registry
        try {
            // Simple weighted sum as fallback since ProcessorRegistry.processFeatures is suspend
            if (features.isNotEmpty()) {
                val weights = listOf(0.3f, 0.2f, 0.1f, 0.15f, 0.1f, 0.05f, 0.05f, 0.05f)
                features.take(8).mapIndexed { index, feature ->
                    feature * weights.getOrElse(index) { 0.1f }
                }.sum().coerceIn(-1f, 1f)
            } else 0f
        } catch (e: Exception) {
            Log.e("ProcessorFunctions", "Registry processor error", e)
            0f
        }
    }
}

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

    // Current processor function and name
    private var currentProcessor: ProcessorFunction = ProcessorFunctions.weightedSumProcessor
    private var currentProcessorName = "Weighted Sum"

    /**
     * Data class for processed results
     */
    data class ProcessedResult(
        val originalFeatures: List<Float>,
        val processedValue: Float,
        val processorName: String,
        val timestamp: Long = System.currentTimeMillis()
    ){
        fun hasChanged(): Boolean{
            return originalFeatures != listOf(processedValue)
        }
    }

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
                    // Process features through the configurable processor
                    val processedValue = try {
                        currentProcessor(features)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing features with $currentProcessorName", e)
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
     * Set a custom processor function
     */
    fun setProcessor(processor: ProcessorFunction, name: String) {
        currentProcessor = processor
        currentProcessorName = name
        Log.d(TAG, "Custom processor updated to: $name")
    }

    /**
     * Set processor by registry name (backward compatibility)
     */
    fun setProcessorByName(processorName: String) {
        if (ProcessorRegistry.setCurrentProcessor(processorName)) {
            currentProcessor = ProcessorFunctions.registryProcessor
            currentProcessorName = processorName
            Log.d(TAG, "Registry processor updated to: $processorName")
        } else {
            Log.w(TAG, "Failed to set processor: $processorName")
        }
    }

    /**
     * Reset processor to bypass processing
     */
    fun resetProcessor() {
        currentProcessor = ProcessorFunctions.resetProcessor
        currentProcessorName = "Reset (Bypass)"
        Log.d(TAG, "Processor reset to bypass mode")
    }

    /**
     * Set to weighted sum processor
     */
    fun setWeightedSumProcessor() {
        currentProcessor = ProcessorFunctions.weightedSumProcessor
        currentProcessorName = "Weighted Sum"
        Log.d(TAG, "Processor set to weighted sum")
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