// Adapted from Kyant0/AndroidLiquidGlass catalog, Apache-2.0 (see NOTICE.md).
package com.xxt.board.ui.liquid.vendor

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.Job
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    val acceptDown: (size: Size, offset: Offset) -> Boolean = { _, _ -> true },
    val radiusDp: Float? = null,
    val ambientAlpha: Float = 0.08f,
    /** Physical pixels, independent from density; null preserves the original falloff. */
    val featherPx: Float? = null,
    val animationSpec: SpringSpec<Float>? = null,
    /** Optional shared material clock; pointer coordinates remain owned by this highlight. */
    val pressure: (() -> Float)? = null,
) {

    private val pressProgressAnimation = Animatable(0f, 0.001f)

    private var startPosition = Offset.Zero
    private var fingerPosition by mutableStateOf(Offset.Zero)
    private var pressJob:Job?=null
    private var held by mutableStateOf(false)
    private var inactive by mutableStateOf(false)
    val isPressed: Boolean get() = held
    val touchPosition: Offset get() = fingerPosition
    val pressProgress: Float get() = pressure?.invoke()?.coerceIn(0f,1f) ?: if(inactive) 0f else
        maxOf(if(held)0.2f else 0f,pressProgressAnimation.value).coerceIn(0f,1f)
    val offset: Offset get() = (fingerPosition - startPosition) * pressProgress
    fun localPosition(size: Size): Offset = position(size,fingerPosition)
    fun reset() {
        pressJob?.cancel()
        held=false
        inactive=true
        fingerPosition=startPosition
    }

    private val shader =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float feather;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = 1.0 - smoothstep(max(0.0, radius - feather), radius, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            drawContent()
            val progress = pressProgress
            if (progress > 0f) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                    drawRect(
                        Color.White.copy(ambientAlpha.coerceIn(0f, 1f) * progress),
                        blendMode = BlendMode.Plus
                    )
                    shader.apply {
                        val position = position(size, fingerPosition)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.15f * progress).toArgb())
                        val radius = radiusDp?.dp?.toPx()?.coerceAtLeast(1f) ?: (size.minDimension * 1.5f)
                        setFloatUniform("radius", radius)
                        setFloatUniform("feather", (featherPx ?: (radius * 0.5f)).coerceIn(0.001f, radius))
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(
                        ShaderBrush(shader),
                        blendMode = BlendMode.Plus
                    )
                } else {
                    if (radiusDp != null) {
                        val focus = position(size, fingerPosition)
                        val radius = radiusDp.dp.toPx().coerceAtLeast(1f)
                        val feather = (featherPx ?: (radius * 0.5f)).coerceIn(0.001f, radius)
                        val inner = 1f - feather / radius
                        val color = Color.White.copy(0.15f * progress)
                        drawRect(Brush.radialGradient(
                            0f to color, inner to color, 1f to Color.Transparent,
                            center = Offset(focus.x.coerceIn(0f, size.width), focus.y.coerceIn(0f, size.height)),
                            radius = radius), blendMode = BlendMode.Plus)
                    } else {
                        drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
                    }
                }
            }

        }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        awaitEachGesture {
            val down=awaitFirstDown(false,PointerEventPass.Initial)
            // Observe only the actual glass region; never steal a click or scroll gesture.
            if (!acceptDown(Size(size.width.toFloat(), size.height.toFloat()), down.position)) return@awaitEachGesture
            inactive=false
            held=true
            startPosition=down.position
            fingerPosition=down.position
            pressJob?.cancel()
            pressJob=if (pressure == null) animationScope.launch {
                pressProgressAnimation.snapTo(maxOf(pressProgressAnimation.value,0.2f))
                pressProgressAnimation.animateTo(1f,animationSpec ?: spring(0.82f,650f,0.001f))
            } else null
            try {
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val change=event.changes.firstOrNull{it.id==down.id}?:break
                    fingerPosition=change.position
                    if(!change.pressed)break
                }
            } finally {
                held=false
                pressJob?.cancel()
                pressJob=if (pressure == null) animationScope.launch {
                    pressProgressAnimation.animateTo(0f,animationSpec ?: spring(0.72f,420f,0.001f))
                } else null
            }
        }
    }
}
