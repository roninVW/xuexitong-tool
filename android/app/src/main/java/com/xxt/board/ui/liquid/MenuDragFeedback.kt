package com.xxt.board.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.tanh

/** Shared entry/panel observer. Clicks and scrolling keep their existing gesture owners. */
internal class MenuDragFeedback(
    private val scope: CoroutineScope,
    private val maxOffsetPx: Float,
    private val acceptDown: () -> Boolean = { true },
) {
    private val bounce = Animatable(Offset.Zero, Offset.VectorConverter, Offset(0.05f, 0.05f))
    private val pressure = Animatable(0f, 0.001f)
    private val tracker = VelocityTracker()
    private var directOffset by mutableStateOf(Offset.Zero)
    private var direct by mutableStateOf(true)
    private var inactive by mutableStateOf(false)
    private var held = false
    private var start = Offset.Zero
    private var inherited = Offset.Zero
    private var displacement = Offset.Zero
    private var coordinates: LayoutCoordinates? = null
    private var returnJob: Job? = null
    private var pressureJob: Job? = null
    val offset: Offset get() = if (inactive) Offset.Zero else if (direct) directOffset else bounce.value
    val pressProgress: Float get() = if (inactive) 0f else pressure.value.coerceIn(0f, 1f)
    // Small directional warp follows the same displacement spring, including its overshoot.
    val deformation: Offset get() {
        val x = offset.x / maxOffsetPx
        val y = offset.y / maxOffsetPx
        return Offset(1f + abs(x) * 0.045f - abs(y) * 0.022f,
            1f + abs(y) * 0.045f - abs(x) * 0.022f)
    }

    fun begin(point: Offset, time: Long) {
        val current = offset
        returnJob?.cancel()
        inactive = false
        held = true
        inherited = current
        directOffset = current
        direct = true
        start = point
        displacement = Offset.Zero
        tracker.resetTracking()
        tracker.addPosition(time, point)
        pressureJob?.cancel()
        pressureJob = scope.launch { pressure.animateTo(1f, DockGlassPressSpring) }
    }

    fun move(point: Offset, time: Long) {
        if (!held) return
        tracker.addPosition(time, point)
        displacement = point - start
        fun damp(value: Float) = maxOffsetPx * tanh(value * 0.18f / maxOffsetPx)
        directOffset = Offset(
            (inherited.x + damp(displacement.x)).coerceIn(-maxOffsetPx, maxOffsetPx),
            (inherited.y + damp(displacement.y)).coerceIn(-maxOffsetPx, maxOffsetPx),
        )
    }

    fun finish(cancelled: Boolean = false) {
        if (!held) return
        held = false
        val current = offset
        val measured = tracker.calculateVelocity()
        fun velocity(value: Float, delta: Float, inheritedValue: Float): Float {
            if (cancelled || !value.isFinite()) return 0f
            val compressed = tanh(delta * 0.18f / maxOffsetPx)
            if (abs(inheritedValue + maxOffsetPx * compressed) >= maxOffsetPx) return 0f
            return (value * 0.18f * (1f - compressed * compressed))
                .coerceIn(-maxOffsetPx * 7f, maxOffsetPx * 7f)
        }
        val initialVelocity = Offset(
            velocity(measured.x, displacement.x, inherited.x),
            velocity(measured.y, displacement.y, inherited.y),
        )
        returnJob?.cancel()
        pressureJob?.cancel()
        pressureJob = scope.launch { pressure.animateTo(0f, DockGlassPressSpring) }
        returnJob = scope.launch {
            bounce.snapTo(current)
            direct = false
            bounce.animateTo(Offset.Zero, spring(0.6f, 240f, Offset(0.05f, 0.05f)), initialVelocity)
        }
    }

    fun reset() {
        returnJob?.cancel()
        pressureJob?.cancel()
        held = false
        inactive = true
        coordinates = null
    }

    private fun rootPoint(point: Offset): Offset =
        coordinates?.takeIf { it.isAttached }?.localToRoot(point) ?: point

    val modifier: Modifier = Modifier.onGloballyPositioned { coordinates = it }.pointerInput(this) {
        awaitEachGesture {
            val down = awaitFirstDown(false, PointerEventPass.Initial)
            if (!acceptDown()) return@awaitEachGesture
            begin(rootPoint(down.position), down.uptimeMillis)
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) { released = true; break }
                    move(rootPoint(change.position), change.uptimeMillis)
                }
            } finally { finish(cancelled = !released) }
        }
    }
}
