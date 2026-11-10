package com.xxt.board.ui.liquid

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.lens

/** Optical parameters are independent of theme colors and the original default material. */
data class LiquidMaterial(val transparency:Float=0.5f,val refraction:Float=1f) {
    fun surfaceAlpha(base:Float):Float=(base*(1f-transparency.coerceIn(0f,1f))/0.5f).coerceIn(0f,1f)
    val refractionScale:Float get()=refraction.takeIf{it.isFinite()}?.coerceIn(0f,2f)?:1f
}
val LocalLiquidMaterial=staticCompositionLocalOf{LiquidMaterial()}
internal fun BackdropEffectScope.liquidLens(material:LiquidMaterial,height:Float,amount:Float,
    depthEffect:Boolean=true,chromaticAberration:Boolean=true) {
    lens(height,amount*material.refractionScale,depthEffect=depthEffect,chromaticAberration=chromaticAberration)
}
