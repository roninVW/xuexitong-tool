// Adapted from Kyant0/AndroidLiquidGlass catalog, Apache-2.0 (see NOTICE.md).
package com.xxt.board.ui.liquid.vendor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import android.os.SystemClock

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

    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec =
        spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation =
        Animatable(0f, visibilityThreshold * 10f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    // Value and transient feedback have separate owners; release never waits for a value.
    private var feedbackJob: Job? = null
    private var valueJob: Job? = null
    private var velocityJob: Job? = null
    private var held by mutableStateOf(false)
    private var direct by mutableStateOf(false)
    private var fingerValue by mutableFloatStateOf(initialValue)
    private var destination by mutableFloatStateOf(initialValue)
    private var fingerVelocity by mutableFloatStateOf(0f)

    private val velocityTracker = VelocityTracker()

    val value: Float get() = if(direct)fingerValue else valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = destination
    val isPressed: Boolean get() = held
    val pressProgress: Float get() = pressProgressAnimation.value.coerceIn(0f, 1f)
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = if(held)fingerVelocity else velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        valueJob?.cancel()
        fingerValue=value
        destination=fingerValue
        direct=true
        held=true
        fingerVelocity=0f
        velocityTracker.resetTracking()
        animateFeedback(1f, pressedScale)
    }

    fun release() {
        if(!held) return
        held=false
        animateFeedback(0f, initialScale)
        velocityJob?.cancel()
        val releaseVelocity=fingerVelocity
        velocityJob=animationScope.launch {
            velocityAnimation.snapTo(releaseVelocity)
            velocityAnimation.animateTo(0f, velocityAnimationSpec)
        }
        settleTo(destination)
    }

    private fun animateFeedback(progress: Float, scale: Float) {
        feedbackJob?.cancel()
        feedbackJob=animationScope.launch {
            launch { pressProgressAnimation.animateTo(progress, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(scale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(scale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        if(!held) {animateToValue(value);return}
        // Synchronous pointer position: no coroutine, spring or interpolation in the drag path.
        fingerValue=value.coerceIn(valueRange)
        destination=fingerValue
        velocityTracker.addPosition(SystemClock.uptimeMillis(),Offset(fingerValue,0f))
        fingerVelocity=velocityTracker.calculateVelocity().x/(valueRange.endInclusive-valueRange.start)
    }

    fun animateToValue(value: Float) {
        destination=value.coerceIn(valueRange)
        if(!held)settleTo(destination)
    }

    private fun settleTo(target:Float) {
        valueJob?.cancel()
        val start=value
        valueJob=animationScope.launch {
            valueAnimation.snapTo(start)
            if(held)return@launch
            direct=false
            valueAnimation.animateTo(target,valueAnimationSpec)
        }
    }
}
