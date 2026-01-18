package com.elegia.pipcamera.ui

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.annotation.RequiresApi
import com.elegia.pipcamera.camera.FrameProcessor
import kotlinx.coroutines.flow.collectLatest

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AGSLCameraOverlay(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    if (isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            Card(
                modifier = Modifier
                    .size(200.dp)
                    .padding(start = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                AGSLShaderCanvas(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AGSLShaderCanvas(
    modifier: Modifier = Modifier
) {
    var time by remember { mutableFloatStateOf(0f) }
    var currentFrame by remember { mutableStateOf<Bitmap?>(null) }

    // Animated time for dynamic effects
    LaunchedEffect(Unit) {
        while (true) {
            time += 0.016f // ~60 FPS
            kotlinx.coroutines.delay(16)
        }
    }

    // Collect camera frames from FrameProcessor
    LaunchedEffect(Unit) {
        FrameProcessor.frameFlow.collectLatest { bitmap ->
            currentFrame = bitmap
        }
    }

    // Create AGSL shader for visual effects
    val shader = remember {
        RuntimeShader(CAMERA_EFFECT_SHADER)
    }

    Canvas(
        modifier = modifier
    ) {
        // Update shader uniforms
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)

        currentFrame?.let { frame ->
            // Draw camera frame with shader effects
            val imageBitmap = frame.asImageBitmap()

            // First draw the camera frame
            drawImage(
                image = imageBitmap,
                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low
            )

            // Then apply shader effects on top
            val shaderBrush = ShaderBrush(shader)
            drawRect(
                brush = shaderBrush,
                size = size,
                blendMode = BlendMode.Overlay // Blend with camera frame
            )
        } ?: run {
            // Fallback: show shader animation when no camera frames
            val shaderBrush = ShaderBrush(shader)
            drawRect(
                brush = shaderBrush,
                size = size
            )
        }
    }
}

// AGSL shader source for camera effects
private const val CAMERA_EFFECT_SHADER = """
    uniform float2 iResolution;
    uniform float iTime;

    // Simple animated gradient with ripple effect
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        float2 center = float2(0.5, 0.5);

        // Distance from center for ripple effect
        float dist = distance(uv, center);

        // Animated ripple
        float ripple = sin(dist * 10.0 - iTime * 3.0) * 0.1 + 0.9;

        // Color gradient based on position and time
        float3 color = float3(
            0.2 + 0.3 * sin(iTime + uv.x * 2.0),
            0.3 + 0.4 * sin(iTime * 1.1 + uv.y * 2.0),
            0.5 + 0.3 * sin(iTime * 0.8 + dist * 5.0)
        );

        // Apply ripple effect
        color *= ripple;

        // Add some sparkle
        float sparkle = sin(uv.x * 20.0 + iTime) * sin(uv.y * 20.0 + iTime * 1.3);
        color += sparkle * 0.1;

        return half4(color, 1.0);
    }
"""