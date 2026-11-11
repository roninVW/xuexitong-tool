// Adapted from Kyant0/AndroidLiquidGlass catalog, Apache-2.0 (see NOTICE.md).
package com.xxt.board.ui.liquid.dockinitial

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.tanh

internal fun dockLensStretch(velocity: Float): Float =
    1f + 0.08f * (abs(velocity.takeIf { it.isFinite() } ?: 0f) / 6f).coerceIn(0f, 1f)

internal fun dockLensLean(velocity: Float): Float =
    0.04f * ((velocity.takeIf { it.isFinite() } ?: 0f) / 6f).coerceIn(-1f, 1f)

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val scaleAnimation = Animatable(initialScale, 0.001f)
    private val velocityAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset(0.001f, 0.001f))
    private val crossAnimation = Animatable(0f, 0.001f)
    private val velocityTracker = VelocityTracker()
    private var pointerValue by mutableFloatStateOf(initialValue)
    private var requestedValue by mutableFloatStateOf(initialValue)
    private var inactive by mutableStateOf(false)
    private var held by mutableStateOf(false)
    private var directVelocity by mutableStateOf(true)
    private var fingerVelocity by mutableStateOf(Offset.Zero)
    private var crossPointer by mutableFloatStateOf(0f)
    private var directCross by mutableStateOf(true)
    private var valueJob: Job? = null
    private var feedbackJob: Job? = null
    private var velocityJob: Job? = null
    private var crossJob: Job? = null

    // Pointer position is synchronous state. Only the released selection interpolates.
    val value: Float get() = pointerValue
    val targetValue: Float get() = requestedValue
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    // One clock drives enlargement, refraction, compression and edge lighting.
    val pressProgress: Float get() = ((scale - initialScale) / (pressedScale - initialScale)).coerceIn(0f, 1f)
    val scale: Float get() = if (inactive) initialScale else scaleAnimation.value
    private val motion: Offset get() = if (inactive) Offset.Zero else if (directVelocity) fingerVelocity else velocityAnimation.value
    private val directionalStretch: Float get() {
        val v = motion
        val direction = if (v.getDistanceSquared() < 0.000001f) 0f else
            (v.x * v.x - v.y * v.y) / v.getDistanceSquared()
        return 1f + (dockLensStretch(v.getDistance()) - 1f) * direction
    }
    val scaleX: Float get() = scale * directionalStretch
    val scaleY: Float get() = scale / directionalStretch
    val velocity: Float get() = motion.x
    val lean: Float get() = dockLensLean(velocity)
    val crossOffset: Float get() = if (inactive) 0f else if (directCross) crossPointer else crossAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down -> onDragStarted(down.position); press() },
            onDragEnd = { onDragStopped(); release() },
            onDragCancel = { onDragStopped(); release() },
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    fun press(eventTimeMillis: Long = SystemClock.uptimeMillis()) {
        val inheritedVelocity = motion
        inactive = false
        held = true
        velocityJob?.cancel()
        fingerVelocity = inheritedVelocity
        directVelocity = true
        velocityTracker.resetTracking()
        velocityTracker.addPosition(eventTimeMillis, Offset(pointerValue, 0f))
        valueJob?.cancel()
        crossJob?.cancel()
        crossPointer = crossOffset
        directCross = true
        feedbackJob?.cancel()
        feedbackJob = animationScope.launch {
            // 135% is the isotropic base; transient motion has its own area-preserving warp.
            scaleAnimation.animateTo(pressedScale, spring(0.72f, 400f, 0.001f))
        }
    }

    fun release() {
        if (!held) return
        val inheritedVelocity = motion
        held = false
        velocityJob?.cancel()
        fingerVelocity = inheritedVelocity
        directVelocity = true
        velocityJob = animationScope.launch {
            velocityAnimation.snapTo(inheritedVelocity)
            directVelocity = false
            velocityAnimation.animateTo(Offset.Zero, spring(0.55f, 240f, Offset(0.001f, 0.001f)))
        }
        val inheritedCross = crossOffset
        crossJob?.cancel()
        crossJob = animationScope.launch {
            crossAnimation.snapTo(inheritedCross)
            directCross = false
            crossAnimation.animateTo(0f, spring(0.6f, 260f, 0.001f),
                initialVelocity = inheritedVelocity.y * 0.03f)
        }
        feedbackJob?.cancel()
        feedbackJob = animationScope.launch {
            scaleAnimation.animateTo(initialScale, spring(0.55f, 260f, 0.001f))
        }
    }

    fun updateValue(value: Float, eventTimeMillis: Long = SystemClock.uptimeMillis(), crossAxis: Float = 0f) {
        valueJob?.cancel()
        pointerValue = value.coerceIn(valueRange)
        requestedValue = pointerValue
        if (held) {
            crossPointer = 0.09f * tanh(crossAxis * 1.6f)
            directCross = true
            velocityTracker.addPosition(eventTimeMillis, Offset(value, crossAxis))
            val measured = velocityTracker.calculateVelocity()
            fun finite(v: Float) = (v.takeIf { it.isFinite() } ?: 0f).coerceIn(-6f, 6f)
            fingerVelocity = Offset(finite(measured.x / (valueRange.endInclusive - valueRange.start)),
                finite(measured.y))
            directVelocity = true
            velocityJob?.cancel()
            velocityJob = animationScope.launch {
                delay(80)
                if (!held) return@launch
                velocityAnimation.snapTo(fingerVelocity)
                directVelocity = false
                velocityAnimation.animateTo(Offset.Zero, spring(1f, 300f, Offset(0.001f, 0.001f)))
            }
        }
    }

    fun animateToValue(value: Float) {
        valueJob?.cancel()
        requestedValue = value.coerceIn(valueRange)
        val start = pointerValue
        val target = requestedValue
        val initialVelocity = if (held) (velocity * (valueRange.endInclusive-valueRange.start)).coerceIn(-4f, 4f) else 0f
        valueJob = animationScope.launch {
            valueAnimation.snapTo(start)
            valueAnimation.animateTo(target, spring(0.62f, 340f, visibilityThreshold), initialVelocity) {
                pointerValue = this.value
            }
        }
    }

    fun reset() {
        valueJob?.cancel()
        feedbackJob?.cancel()
        velocityJob?.cancel()
        crossJob?.cancel()
        held = false
        inactive = true
    }
}
