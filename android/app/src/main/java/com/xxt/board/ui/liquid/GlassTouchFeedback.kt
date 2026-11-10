package com.xxt.board.ui.liquid

import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope

/** Dock's official feedback constants, shared with menu panels. */
val DockGlassPressSpring = spring<Float>(0.5f, 300f, 0.001f)

internal fun BackdropEffectScope.glassPressLens(
    material: LiquidMaterial, pressure: Float,
    restHeight: Float = 0f, restAmount: Float = 0f,
    depthEffect: Boolean = false, chromaticAberration: Boolean = false,
) {
    val press=pressure.coerceIn(0f,1f)
    // One official SDF lens pass: reusing its shader twice would overwrite uniforms.
    liquidLens(material,restHeight+10.dp.toPx()*press,restAmount+14.dp.toPx()*press,
        depthEffect=depthEffect,chromaticAberration=chromaticAberration)
}

/** Optical displacement affects only Backdrop's captured image, never menu/Dock layout. */
internal fun DrawScope.drawGlassPressBackdrop(pressure: Float, focus: Offset, drawBackdrop: DrawScope.()->Unit) {
    val press=pressure.coerceIn(0f,1f)
    val amount=6.dp.toPx()*press
    val dx=((size.width/2f-focus.x)/size.width.coerceAtLeast(1f)).coerceIn(-0.5f,0.5f)*amount
    val dy=((size.height/2f-focus.y)/size.height.coerceAtLeast(1f)).coerceIn(-0.5f,0.5f)*amount
    translate(dx,dy){drawBackdrop()}
}
