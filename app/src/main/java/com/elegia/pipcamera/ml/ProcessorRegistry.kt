package com.elegia.pipcamera.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Registry for managing available feature processors
 */
object ProcessorRegistry {
    private val processors = mutableMapOf<String, FeatureProcessor>()
    private var currentProcessor: FeatureProcessor? = null

    init {
        // Register default processors
        registerProcessor(WeightedSumProcessor())
        registerProcessor(OpenCVProcessor())
        registerProcessor(TensorFlowLiteProcessor())
        registerProcessor(RandomProcessor())

        // Register example processors
        registerProcessor(NeuralNetworkSimulator())
        registerProcessor(StatisticalProcessor())
        registerProcessor(FrequencyDomainProcessor())

        // Set default processor
        setCurrentProcessor("Weighted Sum")
    }

    /**
     * Register a new processor
     */
    fun registerProcessor(processor: FeatureProcessor) {
        processors[processor.name] = processor

        // Initialize processor in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processor.initialize()
            } catch (e: Exception) {
                // Log initialization failure but continue
                println("Failed to initialize processor ${processor.name}: ${e.message}")
            }
        }
    }

    /**
     * Get all available processors
     */
    fun getAllProcessors(): List<FeatureProcessor> = processors.values.toList()

    /**
     * Get all processor names
     */
    fun getProcessorNames(): List<String> = processors.keys.toList()

    /**
     * Get available (initialized) processors
     */
    fun getAvailableProcessors(): List<FeatureProcessor> =
        processors.values.filter { it.isAvailable }

    /**
     * Get available processor names
     */
    fun getAvailableProcessorNames(): List<String> =
        processors.values.filter { it.isAvailable }.map { it.name }

    /**
     * Get processor by name
     */
    fun getProcessor(name: String): FeatureProcessor? = processors[name]

    /**
     * Set current active processor
     */
    fun setCurrentProcessor(name: String): Boolean {
        val processor = processors[name]
        return if (processor != null && processor.isAvailable) {
            currentProcessor = processor
            true
        } else {
            false
        }
    }

    /**
     * Get current active processor
     */
    fun getCurrentProcessor(): FeatureProcessor? = currentProcessor

    /**
     * Process features using current processor
     */
    suspend fun processFeatures(features: List<Float>): Float {
        return currentProcessor?.process(features) ?: 0f
    }

    /**
     * Cleanup all processors
     */
    fun cleanup() {
        processors.values.forEach { it.cleanup() }
        processors.clear()
        currentProcessor = null
    }
}