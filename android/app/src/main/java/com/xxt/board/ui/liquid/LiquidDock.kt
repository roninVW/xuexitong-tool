package com.xxt.board.ui.liquid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.MainDockTabs
import com.xxt.board.ui.liquid.dockinitial.DampedDragAnimation
import com.xxt.board.ui.liquid.vendor.InteractiveHighlight
import com.xxt.board.ui.theme.LocalPaper
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AndroidLiquidGlass LiquidBottomTabs adapted to the existing 66dp Dock geometry.
 * The actual Dock background and tinted icons are sampled independently.
 * The visible icon row has a moving cutout; the lens supplies those pixels exactly once.
 */
@Composable
fun LiquidDock(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val p = LocalPaper.current
    val material=LocalLiquidMaterial.current
    val latestSelect by rememberUpdatedState(onSelect)
    val latestSelected by rememberUpdatedState(selected)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val tabs = MainDockTabs
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    val animation = remember(scope) {
        DampedDragAnimation(scope, selectedIndex.toFloat(), 0f..tabs.lastIndex.toFloat(), 0.001f,
            1f, DockLensPressedScale, {}, {}, { _, _ -> })
    }
    LaunchedEffect(selected) {
        if (animation.targetValue != selectedIndex.toFloat()) {
            animation.animateToValue(selectedIndex.toFloat())
        }
    }
    val light = remember(scope, animation) { InteractiveHighlight(scope, pressure = { animation.pressProgress }) }
    DisposableEffect(animation, light) {
        onDispose { animation.reset(); light.reset() }
    }
    val dockBackground = rememberLayerBackdrop()
    val dockGlow = rememberLayerBackdrop()
    val icons = rememberLayerBackdrop()
    val dockScene = rememberCombinedBackdrop(backdrop, dockBackground)
    val lensBackground = remember(backdrop, dockBackground, dockGlow, light) {
        DockLensBackdrop(backdrop, dockBackground, { light.pressProgress }, dockGlow)
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(66.dp)) {
        val dockHeight = with(density) { 66.dp.toPx() }
        val inset = with(density) { 4.dp.toPx() }
        val tabWidth = (constraints.maxWidth - 2f * inset) / tabs.size
        val indicatorHeight = with(density) { 56.dp.toPx() }

        fun indicatorBounds(): Rect = dockIndicatorBounds(
            dockHeight = dockHeight,
            inset = inset,
            tabWidth = tabWidth,
            indicatorHeight = indicatorHeight,
            value = animation.value,
            rtl = rtl,
            scale = animation.scale,
            scaleX = animation.scaleX, scaleY = animation.scaleY,
            offset = Offset(tabWidth * animation.lean * if (rtl) -1f else 1f, dockHeight * animation.crossOffset),
            tabCount = tabs.size,
        )

        // Real layout bounds, not a scaled graphics layer: shape, cutout and both
        // backdrop surfaces always read exactly the same Rect.
        fun Modifier.dockIndicatorPlacement(): Modifier = layout { measurable, constraints ->
            val bounds = indicatorBounds()
            val placeable = measurable.measure(
                Constraints.fixed(
                    bounds.width.roundToInt().coerceAtLeast(1),
                    bounds.height.roundToInt().coerceAtLeast(1),
                ),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(bounds.left.roundToInt(), bounds.top.roundToInt())
            }
        }

        // Record the real Dock background before rim refraction, highlights, labels
        // and the Lens. The existing background filter runs once and is reused by
        // both the visible Dock and Lens; the Lens adds no Gaussian blur.
        DockBackgroundLayer(backdrop, dockBackground, Modifier.fillMaxSize())
        DockEdgeGlowLayer(dockGlow, { light.pressProgress }, Modifier.fillMaxSize())
        Box(Modifier
            .drawBackdrop(
                backdrop = dockScene, shape = { RoundedCornerShape(50) },
                effects = { liquidLens(material,24.dp.toPx(),24.dp.toPx(),chromaticAberration=false) },
            )
            .then(light.modifier)
            .fillMaxSize())

        // A hidden, noninteractive icon source. It never records the lens or itself.
        DockLabels(
            modifier = Modifier.clearAndSetSemantics {}.alpha(0f).layerBackdrop(icons),
            selected = selected, tinted = true, scale = { 1f },
            onSelect = null,
        )
        DockLabels(
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val bounds = indicatorBounds()
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.Color.Black,
                        topLeft = Offset(bounds.left, bounds.top),
                        size = Size(bounds.width, bounds.height),
                        cornerRadius = CornerRadius(bounds.height / 2f),
                        blendMode = BlendMode.DstOut,
                    )
                },
            selected = selected, tinted = false, scale = { 1f },
            onSelect = { latestSelect(it) },
        )

        // Pure official refraction + RGB dispersion. The live page is retained even
        // outside the Dock. Only the real captured Dock background compresses to
        // 80%, including its footprint; the raw page fills the revealed margins.
        Box(Modifier.dockIndicatorPlacement()
            .drawBackdrop(
                backdrop = lensBackground, shape = { RoundedCornerShape(50) },
                effects = {
                    glassPressLens(material,light.pressProgress,
                        restHeight = 5.dp.toPx(), restAmount = 5.dp.toPx(),
                        chromaticAberration = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.12f + light.pressProgress * 0.78f) },
                shadow = { Shadow(alpha = light.pressProgress * 0.4f) },
                innerShadow = { InnerShadow(radius = 8.dp * light.pressProgress, alpha = light.pressProgress * 0.4f) },
                onDrawSurface = {
                    drawRect(p.ink.copy(alpha = 0.10f * (1f - animation.pressProgress)))
                },
            ))
        Box(Modifier.dockIndicatorPlacement()
            .drawBackdrop(
                backdrop = icons, shape = { RoundedCornerShape(50) },
                effects = {
                    glassPressLens(material,light.pressProgress,
                        restHeight = 5.dp.toPx(), restAmount = 5.dp.toPx(),
                        chromaticAberration = true)
                },
                // Icons/labels stay in their exact layout coordinates and scale.
                highlight = null, shadow = null,
            ))

        // Sole touch host: DOWN must belong to this Dock; scaled visuals never enlarge its hit area.
        Box(Modifier.fillMaxSize().then(light.gestureModifier).pointerInput(rtl, tabWidth) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slot = ((down.position.x - inset) / tabWidth).toInt().coerceIn(0, tabs.lastIndex)
                val startIndex = if (rtl) tabs.lastIndex - slot else slot
                var dragged = false
                var canceled = false
                var released = false
                var fraction = animation.value
                val startValue = fraction
                animation.press(down.uptimeMillis)
                try { while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) { canceled = true; break }
                    val delta = change.position.x - down.position.x
                    val crossDelta = change.position.y - down.position.y
                    if (!change.pressed) { released = true; break }
                    if (!dragged && Offset(delta, crossDelta).getDistance() > viewConfiguration.touchSlop) dragged = true
                    if (dragged) {
                        fraction = startValue + delta / tabWidth * if (rtl) -1f else 1f
                        animation.updateValue(fraction, change.uptimeMillis, crossDelta / dockHeight)
                    }
                    if (dragged) change.consume()
                } } finally {
                    // Cancellation and quick re-press must never leave the Lens enlarged.
                    if (!released) {
                        animation.animateToValue(tabs.indexOf(latestSelected).coerceAtLeast(0).toFloat())
                        animation.release()
                    }
                }
                val index = if (canceled) tabs.indexOf(latestSelected).coerceAtLeast(0)
                    else if (dragged) fraction.roundToInt().coerceIn(0, tabs.lastIndex) else startIndex
                animation.animateToValue(index.toFloat())
                animation.release()
                if (!canceled) latestSelect(tabs[index])
            }
        })
    }
}

/** Official angular edge light, captured separately and used only inside the Lens. */
@Composable
internal fun DockEdgeGlowLayer(capture: LayerBackdrop, pressure: () -> Float, modifier: Modifier) {
    val empty = rememberCanvasBackdrop { }
    Box(modifier.clearAndSetSemantics {}.alpha(0f)
        .layerBackdrop(capture)
        .drawBackdrop(
            backdrop = empty, shape = { RoundedCornerShape(50) }, effects = {},
            highlight = { Highlight(
                width = 1.25.dp, blurRadius = 0.6.dp,
                alpha = pressure().coerceIn(0f, 1f) * 0.9f,
                style = HighlightStyle.Default(intensity = 0.7f, falloff = 0.8f),
            ) }, shadow = null,
        ))
}

/** Shared real background capture, with no foreground, rim, touch light or Lens. */
@Composable
internal fun DockBackgroundLayer(backdrop: Backdrop, capture: LayerBackdrop, modifier: Modifier) {
    val p = LocalPaper.current
    val material = LocalLiquidMaterial.current
    Box(modifier.clearAndSetSemantics {}.alpha(0f)
        .layerBackdrop(capture)
        .drawPlainBackdrop(
            backdrop = backdrop, shape = { RoundedCornerShape(50) },
            effects = { vibrancy(); blur(8.dp.toPx()) },
            onDrawSurface = {
                drawRect(p.card.copy(alpha = material.surfaceAlpha(if(p.dark)0.45f else 0.40f)))
            },
        ))
}

/** Single source of truth for the moving indicator's on-screen capsule. */
internal fun dockIndicatorBounds(
    dockHeight: Float,
    inset: Float,
    tabWidth: Float,
    indicatorHeight: Float,
    value: Float,
    rtl: Boolean,
    scale: Float,
    scaleX: Float = scale, scaleY: Float = scale, offset: Offset = Offset.Zero,
    tabCount: Int = 4,
): Rect {
    val width = (tabWidth * scaleX.coerceIn(0.1f, 4f)).coerceAtLeast(1f)
    val height = (indicatorHeight * scaleY.coerceIn(0.1f, 4f)).coerceAtLeast(1f)
    val anchorX = inset + (if (rtl) tabCount - 1f - value else value) * tabWidth
    val left = anchorX + (tabWidth - width) / 2f + offset.x
    val top = (dockHeight - height) / 2f + offset.y
    return Rect(left, top, left + width, top + height)
}

@Composable
private fun DockLabels(
    modifier: Modifier, selected: AppTab, tinted: Boolean, scale: () -> Float,
    onSelect: ((AppTab) -> Unit)?,
) {
    val p = LocalPaper.current
    Row(modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MainDockTabs.forEach { tab ->
            Column(
                modifier = Modifier.weight(1f).height(56.dp)
                    .then(if (onSelect != null) Modifier.semantics(mergeDescendants = true) {
                        role = Role.Tab; this.selected = selected == tab
                        onClick(tab.label) { onSelect(tab); true }
                    } else Modifier)
                    .graphicsLayer { scaleX = scale(); scaleY = scale() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                val color = if (tinted) p.accent else p.ink2
                Icon(tab.icon, null, tint = color, modifier = Modifier.size(23.dp))
                Text(tab.label, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
