package com.xxt.board.ui.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import kotlin.math.tanh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.xxt.board.ui.liquid.vendor.InteractiveHighlight
import com.xxt.board.ui.theme.LocalPaper

/** Null outside Liquid Glass. Existing page tokens and controls do not read these locals. */
val LocalLiquidWallpaper = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Official LiquidButton recipe: vibrancy -> blur -> lens; press transforms in layerBlock.
 * The backdrop is inverse-transformed by Backdrop, while foreground text stays sharp.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    buttonShape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues? = null,
    surfaceVisible: Boolean = true,
    subdued: Boolean = false,
    feedbackPressure: (() -> Float)? = null,
    feedbackLayer: (GraphicsLayerScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalPaper.current
    val material=LocalLiquidMaterial.current
    // Backdrop lens requires a CornerBasedShape. M3 segmented buttons can supply a wrapper Shape.
    val lensShape = buttonShape as? CornerBasedShape ?: RoundedCornerShape(50)
    val scope = rememberCoroutineScope()
    val light = remember(scope, feedbackPressure) { InteractiveHighlight(scope, pressure = feedbackPressure) }
    val selection by animateFloatAsState(
        if (selected == true) 1f else 0f, spring(0.8f, 600f), label = "glass.selection",
    )
    Row(
        modifier
            .then(if (surfaceVisible) Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { lensShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    liquidLens(material,lerp(if(subdued)6f else 12f,if(subdued)9f else 16f,light.pressProgress).dp.toPx(), lerp(if(subdued)7f else 24f,if(subdued)13f else 36f,light.pressProgress).dp.toPx(), depthEffect = true, chromaticAberration = true)
                },
                highlight = { Highlight.Default.copy(alpha = if(subdued)lerp(0.18f,0.30f,light.pressProgress) else if (p.dark) 0.7f else 0.9f) },
                shadow = { Shadow(radius = if(subdued)2.dp else 3.dp, alpha = if(subdued)0.04f else 0.12f) },
                layerBlock = {
                    val scale = lerp(1f, 1f + (if(subdued)2.dp else 4.dp).toPx() / size.height, light.pressProgress)
                    val maxOffset = size.minDimension.coerceAtLeast(1f)
                    translationX = maxOffset * tanh(0.05f * light.offset.x / maxOffset)
                    translationY = maxOffset * tanh(0.05f * light.offset.y / maxOffset)
                    scaleX = scale
                    scaleY = scale
                    feedbackLayer?.invoke(this)
                },
                onDrawSurface = {
                    drawRect(p.card.copy(alpha =material.surfaceAlpha( if(subdued) {if(p.dark)0.34f else 0.30f} else if (p.dark) 0.54f else 0.50f)))
                    if (selection > 0f) {
                        drawRect(p.accent, alpha = selection * 0.35f, blendMode = BlendMode.Hue)
                        drawRect(p.accent, alpha = selection * if (p.dark) 0.30f else 0.22f)
                    }
                },
            ) else Modifier)
            .then(if (selected == null) {
                Modifier.combinedClickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            } else {
                Modifier.selectable(selected, enabled = enabled, interactionSource = null, indication = null, role = Role.RadioButton, onClick = onClick)
            })
            .clip(lensShape)
            .then(if (enabled) light.modifier.then(light.gestureModifier) else Modifier)
            .alpha(if (!surfaceVisible) 0f else if (enabled) 1f else 0.38f)
            .heightIn(min = if (compact) 40.dp else 44.dp)
            .then(if (contentPadding != null) Modifier.padding(contentPadding) else Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 6.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = { CompositionLocalProvider(LocalContentColor provides p.ink) { content() } },
    )
}
