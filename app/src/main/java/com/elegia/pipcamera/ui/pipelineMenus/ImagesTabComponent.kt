package com.elegia.pipcamera.ui.pipelineMenus

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elegia.pipcamera.camera.CameraManager
import com.elegia.pipcamera.camera.FrameProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Images Tab - Fourth tab with Weka ML processing and debug console
 */
@Composable
fun ImagesTabComponent(
    imageAnalysisEnabled: Boolean,
    selectedAlgorithm: String,
    dimensions: Int,
    learningRate: Float,
    onImageAnalysisToggle: (Boolean) -> Unit,
    onAlgorithmChange: (String) -> Unit,
    onDimensionsChange: (Int) -> Unit,
    onLearningRateChange: (Float) -> Unit,
    cameraManager: CameraManager? = null // Add camera manager to check frame availability
) {
    var wekaOutputConsole by remember { mutableStateOf("Weka output will appear here...") }
    var sharedFeatures by remember { mutableStateOf(listOf<Float>()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Image Processing & ML",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Configure camera image analysis with Weka machine learning:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Image Analysis Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (imageAnalysisEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (imageAnalysisEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Image Analysis Frames",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Live camera frame analysis with ML",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Switch(
                    checked = imageAnalysisEnabled,
                    onCheckedChange = onImageAnalysisToggle
                )
            }
        }

        if (imageAnalysisEnabled) {
            // Input Visualization with real camera frames when available
            if (cameraManager != null) {
                WekaInputVisualizationCard(
                    cameraManager = cameraManager,
                    onFeaturesExtracted = { features -> sharedFeatures = features }
                )
            } else {
                WekaInputVisualizationCard(
                    cameraManager = null,
                    onFeaturesExtracted = { features -> sharedFeatures = features }
                )
            }
            // Weka Output Debug Console
            WekaOutputConsole(
                outputText = wekaOutputConsole,
                onUpdateConsole = { wekaOutputConsole = it },
                inputFeatures = sharedFeatures
            )
            // Weka Configuration
            WekaConfigurationCard(
                selectedAlgorithm = selectedAlgorithm,
                dimensions = dimensions,
                learningRate = learningRate,
                onAlgorithmChange = onAlgorithmChange,
                onDimensionsChange = onDimensionsChange,
                onLearningRateChange = onLearningRateChange
            )


        }
    }
}

/**
 * Weka Input Visualization Card - Shows what camera data is being processed
 */
@Composable
private fun WekaInputVisualizationCard(
    cameraManager: CameraManager?,
    onFeaturesExtracted: (List<Float>) -> Unit
) {
    var simulateProcessing by remember { mutableStateOf(false) }
    var frameCounter by remember { mutableStateOf(0) }
    var currentFeatures by remember { mutableStateOf(listOf<Float>()) }

    // Check if camera analysis is enabled and frames are available
    val isAnalysisEnabled by (cameraManager?.isAnalysisEnabled ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)
    var currentCameraFrame by remember { mutableStateOf<Bitmap?>(null) }
    var cameraFrameCounter by remember { mutableStateOf(0) }

    // Collect real camera frames when available
    LaunchedEffect(isAnalysisEnabled) {
        if (isAnalysisEnabled) {
            FrameProcessor.frameFlow.collectLatest { bitmap ->
                // Resize to 100x100 for memory efficiency
                currentCameraFrame = resizeBitmap(bitmap, 100, 100)
                cameraFrameCounter++

                // Extract mock features from the frame
                currentFeatures = extractMockFeaturesFromBitmap(currentCameraFrame!!)
                onFeaturesExtracted(currentFeatures)
            }
        } else {
            currentCameraFrame = null
            cameraFrameCounter = 0
        }
    }

    // Simulate frame processing
    LaunchedEffect(simulateProcessing) {
        if (simulateProcessing) {
            while (simulateProcessing) {
                frameCounter++
                // Simulate random feature extraction from camera frames
                currentFeatures = List(8) {
                    (Math.random() * 2 - 1).toFloat() // Random values between -1 and 1
                }
                onFeaturesExtracted(currentFeatures)
                delay(100) // 10 FPS simulation
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (currentCameraFrame != null) Icons.Default.Star else Icons.Default.Search,
                        contentDescription = null,
                        tint = if (currentCameraFrame != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Camera Input Visualization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                currentCameraFrame != null -> "Live camera: frame #$cameraFrameCounter (100x100px)"
                                simulateProcessing -> "Simulation: frame #$frameCounter"
                                isAnalysisEnabled -> "Camera analysis enabled, waiting for frames..."
                                else -> "Camera analysis disabled"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (currentCameraFrame != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Processing toggle (only show when no real camera frames)
                if (currentCameraFrame == null) {
                    Switch(
                        checked = simulateProcessing,
                        onCheckedChange = { simulateProcessing = it },
                        enabled = isAnalysisEnabled == false // Disable when camera is active
                    )
                }
            }

            // Camera Frame Visualization (Real or Simulated)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        currentCameraFrame != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        simulateProcessing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // Show real camera frames when available
                        currentCameraFrame != null -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Live camera frame display (100x100)
                                Card(
                                    modifier = Modifier.size(80.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Canvas(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        currentCameraFrame?.let { bitmap ->
                                            drawImage(
                                                image = bitmap.asImageBitmap(),
                                                dstSize = androidx.compose.ui.unit.IntSize(
                                                    size.width.toInt(),
                                                    size.height.toInt()
                                                ),
                                                filterQuality = FilterQuality.Medium
                                            )
                                        }
                                    }
                                }

                                Column {
                                    Text(
                                        text = "Live Camera Input",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Size: 100x100 pixels",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Source: ImageAnalysis",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Show simulation when camera not available but simulation enabled
                        simulateProcessing -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Simulated camera frame visualization
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    repeat(6) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary.copy(
                                                        alpha = (Math.random() * 0.8 + 0.2).toFloat()
                                                    ),
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }
                                Text(
                                    text = "Simulated Camera Frames",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Show disabled state
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAnalysisEnabled) Icons.Default.Refresh else Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (isAnalysisEnabled)
                                        "Waiting for Camera Frames..."
                                    else
                                        "Camera Analysis Disabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isAnalysisEnabled)
                                        "Enable camera analysis in Surface toolbar"
                                    else
                                        "Toggle simulation or enable camera analysis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Feature extraction display (for both real and simulated)
            if ((currentCameraFrame != null || simulateProcessing) && currentFeatures.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Extracted Features (First 8):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        // Feature values as horizontal bars
                        currentFeatures.forEachIndexed { index, value ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "F$index",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth((kotlin.math.abs(value) * 0.5f + 0.5f).coerceIn(0f, 1f))
                                            .background(
                                                color = if (value >= 0)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.error,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.3f", value),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(48.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Processing info
            Text(
                text = if (currentCameraFrame != null) {
                    "Live camera frames (100x100px) are being processed for ML feature extraction. Real frame data is used for Weka analysis."
                } else {
                    "Enable camera analysis from the Surface toolbar to show live frames. Currently showing simulation mode."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Weka Configuration Card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WekaConfigurationCard(
    selectedAlgorithm: String,
    dimensions: Int,
    learningRate: Float,
    onAlgorithmChange: (String) -> Unit,
    onDimensionsChange: (Int) -> Unit,
    onLearningRateChange: (Float) -> Unit
) {
    val algorithms = listOf("J48", "RandomForest", "NaiveBayes", "SVM", "KMeans")
    var algorithmExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Weka ML Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Algorithm Dropdown
            ExposedDropdownMenuBox(
                expanded = algorithmExpanded,
                onExpandedChange = { algorithmExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedAlgorithm,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("ML Algorithm") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algorithmExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = algorithmExpanded,
                    onDismissRequest = { algorithmExpanded = false }
                ) {
                    algorithms.forEach { algorithm ->
                        DropdownMenuItem(
                            text = { Text(algorithm) },
                            onClick = {
                                onAlgorithmChange(algorithm)
                                algorithmExpanded = false
                            }
                        )
                    }
                }
            }

            // Dimensions Slider
            Column {
                Text(
                    text = "Feature Dimensions: $dimensions",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = dimensions.toFloat(),
                    onValueChange = { onDimensionsChange(it.toInt()) },
                    valueRange = 5f..50f,
                    steps = 44
                )
            }

            // Learning Rate Slider
            Column {
                Text(
                    text = "Learning Rate: %.3f".format(learningRate),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = learningRate,
                    onValueChange = onLearningRateChange,
                    valueRange = 0.001f..1.0f
                )
            }
        }
    }
}

/**
 * Weka Output Console with single scalar bar visualization
 */
@Composable
private fun WekaOutputConsole(
    outputText: String,
    onUpdateConsole: (String) -> Unit,
    inputFeatures: List<Float>
) {
    var scalarOutput by remember { mutableStateOf(0.0f) }
    var isProcessing by remember { mutableStateOf(false) }

    // Calculate scalar output from input features
    LaunchedEffect(inputFeatures, isProcessing) {
        if (isProcessing && inputFeatures.isNotEmpty()) {
            while (isProcessing) {
                // Use actual features to calculate scalar output
                // Simple weighted sum of features as a mock ML model
                val weights = listOf(0.3f, 0.2f, 0.1f, 0.15f, 0.1f, 0.05f, 0.05f, 0.05f)
                scalarOutput = inputFeatures.take(8).mapIndexed { index, feature ->
                    feature * weights.getOrElse(index) { 0.1f }
                }.sum().coerceIn(-1f, 1f)
                delay(150) // Update every 150ms
            }
        } else if (isProcessing) {
            while (isProcessing) {
                // Fallback to random when no features available
                scalarOutput = (Math.random() * 2 - 1).toFloat()
                delay(150)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Weka Output Visualization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Single Scalar Bar Visualization
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Model Output (Scalar):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    // Single scalar output bar (similar to forEach structure from input visualization)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Out",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(30.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((kotlin.math.abs(scalarOutput) * 0.5f + 0.5f).coerceIn(0f, 1f))
                                    .background(
                                        color = if (scalarOutput >= 0)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%.3f", scalarOutput),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(60.dp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isProcessing = !isProcessing
                        if (isProcessing) {
                            onUpdateConsole("Model processing started...")
                        } else {
                            onUpdateConsole("Model processing stopped.")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProcessing)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isProcessing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isProcessing) "Stop" else "Start",
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        scalarOutput = 0.0f
                        onUpdateConsole("Model output reset to 0.000")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 12.sp)
                }
            }

            Text(
                text = "Simple scalar model output: reduces input features to single value in range [-1, 1]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Resize bitmap to specified dimensions for memory efficiency
 */
private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
    return Bitmap.createScaledBitmap(bitmap, width, height, false)
}

/**
 * Extract mock features from a real camera bitmap
 * This simulates what a real ML feature extractor would do
 */
private fun extractMockFeaturesFromBitmap(bitmap: Bitmap): List<Float> {
    val features = mutableListOf<Float>()

    try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Extract basic image statistics as "features"
        var redSum = 0f
        var greenSum = 0f
        var blueSum = 0f
        var brightnessSum = 0f

        pixels.forEach { pixel ->
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            redSum += red
            greenSum += green
            blueSum += blue
            brightnessSum += (red + green + blue) / 3f
        }

        val pixelCount = pixels.size.toFloat()

        // Normalize to -1 to 1 range
        features.add((redSum / pixelCount / 255f) * 2 - 1)
        features.add((greenSum / pixelCount / 255f) * 2 - 1)
        features.add((blueSum / pixelCount / 255f) * 2 - 1)
        features.add((brightnessSum / pixelCount / 255f) * 2 - 1)

        // Add some spatial features (corners vs center)
        val cornerPixels = listOf(
            pixels[0], pixels[width - 1],
            pixels[(height - 1) * width], pixels[width * height - 1]
        )
        val centerPixel = pixels[(height / 2) * width + (width / 2)]

        val cornerBrightness = cornerPixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (r + g + b) / 3f
        }.average().toFloat()

        val centerBrightness = run {
            val r = (centerPixel shr 16) and 0xFF
            val g = (centerPixel shr 8) and 0xFF
            val b = centerPixel and 0xFF
            (r + g + b) / 3f
        }

        features.add((cornerBrightness / 255f) * 2 - 1)
        features.add((centerBrightness / 255f) * 2 - 1)

        // Add some gradient-like features (difference between adjacent regions)
        val leftHalf = pixels.sliceArray(0 until pixels.size / 2)
        val rightHalf = pixels.sliceArray(pixels.size / 2 until pixels.size)

        val leftAvg = leftHalf.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (r + g + b) / 3f
        }.average().toFloat()

        val rightAvg = rightHalf.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (r + g + b) / 3f
        }.average().toFloat()

        features.add((leftAvg - rightAvg) / 255f) // Horizontal gradient
        features.add((Math.random() * 2.0 - 1.0).toFloat()) // Random feature for variety

    } catch (e: Exception) {
        // Fallback to random features if bitmap processing fails
        repeat(8) {
            features.add((Math.random() * 2.0 - 1.0).toFloat())
        }
    }

    return features.take(8) // Return first 8 features
}