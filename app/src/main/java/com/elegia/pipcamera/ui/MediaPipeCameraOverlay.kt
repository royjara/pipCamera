package com.elegia.pipcamera.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.RequiresApi
import com.elegia.pipcamera.camera.FrameProcessor
import com.elegia.pipcamera.mediapipe.MediaPipeHelper
import com.elegia.pipcamera.mediapipe.MediaPipeManager
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.collectLatest

data class MediaPipeOverlayData(
    val originalBitmap: Bitmap,
    val faceLandmarkerResult: FaceLandmarkerResult? = null,
    val inferenceTime: Long = 0
)

@Composable
fun MediaPipeCameraOverlay(
    modifier: Modifier = Modifier,
    showBefore: Boolean = true,
    showAfter: Boolean = true,
    mediaPipeHelper: MediaPipeHelper? = null
) {
    var currentFrame by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Get MediaPipe processing results from the manager
    val processingResults by (MediaPipeManager.getProcessingResults()?.collectAsState() ?: remember { mutableStateOf(null) })
    val processingState by (MediaPipeManager.getIsProcessing()?.collectAsState() ?: remember { mutableStateOf(false) })

    // Collect camera frames from FrameProcessor
    LaunchedEffect(Unit) {
        FrameProcessor.frameFlow.collectLatest { bitmap ->
            currentFrame = bitmap
        }
    }

    // Update local processing state
    LaunchedEffect(processingState) {
        isProcessing = processingState
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showBefore) {
            MediaPipePreviewCard(
                title = "Original Camera Feed",
                subtitle = "Before Processing",
                bitmap = currentFrame,
                showLandmarks = false,
                landmarks = null,
                modifier = Modifier.weight(1f)
            )
        }

        if (showAfter) {
            MediaPipePreviewCard(
                title = "MediaPipe Processed",
                subtitle = if (isProcessing) "Processing..." else "After Processing",
                bitmap = currentFrame, // Same frame, but with landmarks overlay
                showLandmarks = true,
                landmarks = processingResults?.faceLandmarkerResult,
                inferenceTime = processingResults?.inferenceTime ?: 0,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MediaPipePreviewCard(
    title: String,
    subtitle: String,
    bitmap: Bitmap?,
    showLandmarks: Boolean,
    landmarks: FaceLandmarkerResult?,
    inferenceTime: Long = 0,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showLandmarks && inferenceTime > 0) {
                    Text(
                        text = "${inferenceTime}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Camera Preview with Optional Landmarks
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        CameraCanvas(
                            bitmap = bitmap,
                            showLandmarks = showLandmarks,
                            landmarks = landmarks,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "No Camera Feed",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Stats Section
            if (showLandmarks) {
                Spacer(modifier = Modifier.height(8.dp))
                StatsRow(landmarks = landmarks, inferenceTime = inferenceTime)
            }
        }
    }
}

@Composable
private fun CameraCanvas(
    bitmap: Bitmap,
    showLandmarks: Boolean,
    landmarks: FaceLandmarkerResult?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val imageBitmap = bitmap.asImageBitmap()

        // Calculate scaling to fit the canvas while maintaining aspect ratio
        val canvasRatio = size.width / size.height
        val imageRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()

        val (drawWidth, drawHeight) = if (canvasRatio > imageRatio) {
            // Canvas is wider, fit to height
            val width = size.height * imageRatio
            Pair(width, size.height)
        } else {
            // Canvas is taller, fit to width
            val height = size.width / imageRatio
            Pair(size.width, height)
        }

        val offsetX = (size.width - drawWidth) / 2
        val offsetY = (size.height - drawHeight) / 2

        // Draw the camera frame
        drawImage(
            image = imageBitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt()),
            filterQuality = FilterQuality.Low
        )

        // Draw landmarks if available and requested
        if (showLandmarks && landmarks != null) {
            drawFaceLandmarks(
                landmarks = landmarks,
                imageWidth = imageBitmap.width,
                imageHeight = imageBitmap.height,
                canvasWidth = drawWidth,
                canvasHeight = drawHeight,
                offsetX = offsetX,
                offsetY = offsetY
            )
        }
    }
}

private fun DrawScope.drawFaceLandmarks(
    landmarks: FaceLandmarkerResult,
    imageWidth: Int,
    imageHeight: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float
) {
    if (landmarks.faceLandmarks().isEmpty()) return

    val scaleX = canvasWidth / imageWidth
    val scaleY = canvasHeight / imageHeight

    // Draw landmarks for each detected face
    landmarks.faceLandmarks().forEach { faceLandmarkList ->
        // Draw landmark points
        faceLandmarkList.forEach { landmark ->
            val x = offsetX + landmark.x() * canvasWidth
            val y = offsetY + landmark.y() * canvasHeight

            drawCircle(
                color = Color.Green,
                radius = 2.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }

        // Draw face outline (connecting specific landmark points)
        drawFaceOutline(faceLandmarkList, offsetX, offsetY, canvasWidth, canvasHeight)
    }
}

private fun DrawScope.drawFaceOutline(
    landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
    offsetX: Float,
    offsetY: Float,
    canvasWidth: Float,
    canvasHeight: Float
) {
    if (landmarks.size < 468) return // Face landmarks should have 468 points

    // Define face outline indices (these are MediaPipe face mesh indices)
    val faceOvalIndices = listOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
        397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
        172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
    )

    // Draw face oval
    for (i in 0 until faceOvalIndices.size) {
        val currentIndex = faceOvalIndices[i]
        val nextIndex = faceOvalIndices[(i + 1) % faceOvalIndices.size]

        if (currentIndex < landmarks.size && nextIndex < landmarks.size) {
            val start = landmarks[currentIndex]
            val end = landmarks[nextIndex]

            val startX = offsetX + start.x() * canvasWidth
            val startY = offsetY + start.y() * canvasHeight
            val endX = offsetX + end.x() * canvasWidth
            val endY = offsetY + end.y() * canvasHeight

            drawLine(
                color = Color.Green,
                start = androidx.compose.ui.geometry.Offset(startX, startY),
                end = androidx.compose.ui.geometry.Offset(endX, endY),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun StatsRow(
    landmarks: FaceLandmarkerResult?,
    inferenceTime: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Faces: ${landmarks?.faceLandmarks()?.size ?: 0}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Landmarks: ${landmarks?.faceLandmarks()?.firstOrNull()?.size ?: 0}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (inferenceTime > 0) {
            Text(
                text = "Inference: ${inferenceTime}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}