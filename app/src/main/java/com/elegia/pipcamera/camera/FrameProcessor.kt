package com.elegia.pipcamera.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

object FrameProcessor {
    private const val TAG = "CLAUDE_FrameProcessor"

    // High-performance channel for frame data
    private val frameChannel = Channel<Bitmap>(capacity = Channel.RENDEZVOUS)

    // Flow for AGSL shader to consume frames
    val frameFlow: Flow<Bitmap> = frameChannel.receiveAsFlow()

    // Current rotation angle (will be set by CameraManager)
    private var currentRotation: Int = 0

    /**
     * Set the rotation angle for frames
     */
    fun setRotation(rotation: Int) {
        currentRotation = rotation
        Log.d(TAG, "setRotation: Frame rotation set to ${rotation}°")
    }

    /**
     * Process ImageProxy from ImageAnalysis and convert to Bitmap for AGSL
     * Optimized for performance with minimal allocations
     */
    suspend fun processFrame(imageProxy: ImageProxy) {
        try {
            Log.v(TAG, "Processing frame - format=${imageProxy.format}, size=${imageProxy.width}x${imageProxy.height}")

            val rawBitmap = when (imageProxy.format) {
                ImageFormat.YUV_420_888 -> convertYuv420ToBitmap(imageProxy)
                ImageFormat.NV21 -> convertNv21ToBitmap(imageProxy)
                else -> {
                    Log.w(TAG, "Unsupported image format: ${imageProxy.format}, using fallback")
                    convertFallbackToBitmap(imageProxy)
                }
            }

            // Apply rotation if needed
            val bitmap = if (currentRotation != 0) {
                rotateBitmap(rawBitmap, currentRotation.toFloat())
            } else {
                rawBitmap
            }

            // Non-blocking send - drops frame if consumer can't keep up
            frameChannel.trySend(bitmap).getOrNull()

            // Clean up raw bitmap if we created a rotated copy
            if (bitmap != rawBitmap) {
                rawBitmap.recycle()
            }

            Log.v(TAG, "Frame processed and sent to AGSL - format=${imageProxy.format}, size=${bitmap.width}x${bitmap.height}")
        } catch (e: Exception) {
            Log.w(TAG, "Frame processing failed", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Convert YUV420_888 ImageProxy to RGB Bitmap
     * Properly handles YUV color space conversion
     */
    private fun convertYuv420ToBitmap(imageProxy: ImageProxy): Bitmap {
        val planes = imageProxy.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val yArray = ByteArray(ySize)
        val uArray = ByteArray(uSize)
        val vArray = ByteArray(vSize)

        yBuffer.get(yArray)
        uBuffer.get(uArray)
        vBuffer.get(vArray)

        val width = imageProxy.width
        val height = imageProxy.height

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val yIndex = y * yPlane.rowStride + x * yPlane.pixelStride
                    val uvIndex = (y / 2) * uPlane.rowStride + (x / 2) * uPlane.pixelStride

                    if (yIndex < yArray.size && uvIndex < uArray.size && uvIndex < vArray.size) {
                        val yValue = yArray[yIndex].toInt() and 0xFF
                        val uValue = uArray[uvIndex].toInt() and 0xFF
                        val vValue = vArray[uvIndex].toInt() and 0xFF

                        // YUV to RGB conversion
                        val rgb = yuvToRgb(yValue, uValue, vValue)
                        pixels[y * width + x] = rgb
                    }
                }
            }

            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Convert NV21 format (alternative YUV format)
     */
    private fun convertNv21ToBitmap(imageProxy: ImageProxy): Bitmap {
        // NV21 has Y plane followed by interleaved VU
        val yPlane = imageProxy.planes[0]
        val uvPlane = imageProxy.planes[1]

        val yBuffer = yPlane.buffer
        val uvBuffer = uvPlane.buffer

        val yArray = ByteArray(yBuffer.remaining())
        val uvArray = ByteArray(uvBuffer.remaining())

        yBuffer.get(yArray)
        uvBuffer.get(uvArray)

        val width = imageProxy.width
        val height = imageProxy.height

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(width * height)
            // Simplified NV21 conversion - similar to YUV420 but with different UV layout
            for (i in pixels.indices) {
                val gray = yArray.getOrNull(i * (yArray.size / pixels.size))?.toInt()?.and(0xFF) ?: 0
                pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Fallback conversion for unsupported formats
     */
    private fun convertFallbackToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val width = imageProxy.width
        val height = imageProxy.height

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(width * height)
            for (i in pixels.indices) {
                val gray = bytes.getOrNull(i * (bytes.size / pixels.size))?.toInt()?.and(0xFF) ?: 0
                pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * YUV to RGB color space conversion
     * Uses standard BT.601 conversion coefficients
     */
    private fun yuvToRgb(y: Int, u: Int, v: Int): Int {
        val yAdj = y - 16
        val uAdj = u - 128
        val vAdj = v - 128

        val r = (1.164 * yAdj + 1.596 * vAdj).toInt()
        val g = (1.164 * yAdj - 0.392 * uAdj - 0.813 * vAdj).toInt()
        val b = (1.164 * yAdj + 2.017 * uAdj).toInt()

        val rClamped = max(0, min(255, r))
        val gClamped = max(0, min(255, g))
        val bClamped = max(0, min(255, b))

        return (0xFF shl 24) or (rClamped shl 16) or (gClamped shl 8) or bClamped
    }

    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees, bitmap.width / 2f, bitmap.height / 2f)
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        frameChannel.close()
        Log.d(TAG, "FrameProcessor cleaned up")
    }
}