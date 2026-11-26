package ru.chronicnotebook.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.chronicnotebook.ui.theme.LocalVisualEffects

private val glassRadius = 28.dp
internal val JournalCardShape = RoundedCornerShape(glassRadius)

@Composable
internal fun JournalGlassCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val effectsOn = LocalVisualEffects.current
    val density = LocalDensity.current
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val effect = remember(bounds, density, effectsOn) {
        if (effectsOn && Build.VERSION.SDK_INT >= 33 && bounds.width > 0 && bounds.height > 0) {
            with(density) { lensEffect(bounds, glassRadius.toPx(), 22.dp.toPx(), 12.dp.toPx()) }
        } else null
    }

    Box(modifier.clip(JournalCardShape)) {
        Canvas(
            Modifier.matchParentSize().onSizeChanged { bounds = it }.graphicsLayer {
                renderEffect = effect
            },
        ) {
            drawRect(colors.surfaceContainerHighest)
            if (effectsOn) {
                drawRect(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHighest)))
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = .21f), Color.Transparent),
                        center = Offset(size.width * .92f, size.height * .10f),
                        radius = size.width * .72f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.secondary.copy(alpha = .16f), Color.Transparent),
                        center = Offset(size.width * .16f, size.height * .96f),
                        radius = size.width * .50f,
                    ),
                )
            }
        }
        content()
    }
}

@RequiresApi(33)
private fun lensEffect(bounds: IntSize, radius: Float, height: Float, amount: Float) =
    RenderEffect.createRuntimeShaderEffect(
        RuntimeShader(PanelRefraction).apply {
            setFloatUniform("size", bounds.width.toFloat(), bounds.height.toFloat())
            setFloatUniform("radius", radius.coerceAtMost(minOf(bounds.width, bounds.height) / 2f))
            setFloatUniform("refractionHeight", height)
            setFloatUniform("refractionAmount", amount)
        },
        "content",
    ).asComposeRenderEffect()

/*
 * The rounded-rectangle distance, gradient, and lens mapping below are adapted from
 * AndroidLiquidGlass / Backdrop, Shaders.kt (commit eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5).
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * Changes: uniform corner radius, local panel coordinates, no dispersion, clamped sampling.
 * Full license: app/src/main/assets/licenses/AndroidLiquidGlass.txt
 */
private const val PanelRefraction = """
    uniform shader content;
    uniform float2 size;
    uniform float radius;
    uniform float refractionHeight;
    uniform float refractionAmount;

    float sdRoundedRect(float2 coord, float2 halfSize, float cornerRadius) {
        float2 corner = abs(coord) - (halfSize - float2(cornerRadius));
        return length(max(corner, 0.0)) - cornerRadius + min(max(corner.x, corner.y), 0.0);
    }

    float2 gradSdRoundedRect(float2 coord, float2 halfSize, float cornerRadius) {
        float2 corner = abs(coord) - (halfSize - float2(cornerRadius));
        if (max(corner.x, corner.y) > 0.0) {
            return sign(coord) * normalize(max(corner, 0.0));
        }
        float gradX = step(corner.y, corner.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }

    half4 main(float2 coord) {
        float2 halfSize = size * 0.5;
        float2 centered = coord - halfSize;
        float distance = sdRoundedRect(centered, halfSize, radius);
        if (-distance >= refractionHeight) return content.eval(coord);
        float x = clamp(1.0 + distance / refractionHeight, 0.0, 1.0);
        float displacement = (1.0 - sqrt(1.0 - x * x)) * refractionAmount;
        float edgeRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 gradient = gradSdRoundedRect(centered, halfSize, edgeRadius);
        return content.eval(clamp(coord + displacement * gradient, float2(0.5), size - 0.5));
    }
"""
