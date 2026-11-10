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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.liquid.dockinitial.DampedDragAnimation
import com.xxt.board.ui.liquid.vendor.InteractiveHighlight
import com.xxt.board.ui.theme.LocalPaper
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AndroidLiquidGlass LiquidBottomTabs adapted to the existing 66dp Dock geometry.
 * Page glass and tinted icons are sampled independently: blur never blurs the labels.
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
    val animation = remember(scope) {
        DampedDragAnimation(scope, selected.ordinal.toFloat(), 0f..3f, 0.001f,
            1f, 78f / 56f, {}, {}, { _, _ -> })
    }
    LaunchedEffect(selected) {
        if (animation.targetValue != selected.ordinal.toFloat()) {
            animation.animateToValue(selected.ordinal.toFloat())
        }
    }
    val light = remember(scope) { InteractiveHighlight(scope,animationSpec=DockGlassPressSpring) }
    val icons = rememberLayerBackdrop()
    BoxWithConstraints(modifier.fillMaxWidth().height(66.dp)) {
        val inset = with(density) { 4.dp.toPx() }
        val tabWidth = (constraints.maxWidth - 2f * inset) / AppTab.entries.size
        val indicatorHeight = with(density) { 56.dp.toPx() }
        fun indicatorX(): Float = inset +
            (if (rtl) 3f - animation.value else animation.value) * tabWidth
        fun xScale(): Float = animation.scaleX /
            (1f - (animation.velocity / 10f * 0.75f).coerceIn(-0.2f, 0.2f))
        fun yScale(): Float = animation.scaleY *
            (1f - (animation.velocity / 10f * 0.25f).coerceIn(-0.2f, 0.2f))
        val indicatorTransform: GraphicsLayerScope.() -> Unit = {
            scaleX = xScale()
            scaleY = yScale()
        }
        val indicatorOffset = Modifier.graphicsLayer {
            translationX = indicatorX()
            translationY = (66.dp.toPx() - indicatorHeight) / 2f
        }
        val indicatorSize = Modifier
            .width(with(density) { tabWidth.toDp() })
            .height(56.dp)

        Box(Modifier
            .drawBackdrop(
                backdrop = backdrop, shape = { RoundedCornerShape(50) },
                effects = { vibrancy(); blur(8.dp.toPx()); liquidLens(material,24.dp.toPx(),24.dp.toPx(),chromaticAberration=false) },
                layerBlock = {
                    val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, animation.pressProgress)
                    scaleX = scale; scaleY = scale
                },
                onDrawSurface = { drawRect(p.card.copy(alpha = material.surfaceAlpha(if(p.dark)0.45f else 0.40f))) },
            )
            .then(light.modifier)
            .fillMaxSize())

        // A hidden, noninteractive icon source. It never records the lens or itself.
        DockLabels(
            modifier = Modifier.clearAndSetSemantics {}.alpha(0f).layerBackdrop(icons),
            selected = selected, tinted = true, scale = { lerp(1f, 1.2f, animation.pressProgress) },
            onSelect = null,
        )
        DockLabels(
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val width = tabWidth * xScale()
                    val height = indicatorHeight * yScale()
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.Color.Black,
                        topLeft = Offset(indicatorX() + (tabWidth - width) / 2f, (size.height - height) / 2f),
                        size = Size(width, height), cornerRadius = CornerRadius(height / 2f),
                        blendMode = BlendMode.DstOut,
                    )
                },
            selected = selected, tinted = false, scale = { 1f },
            onSelect = { latestSelect(it) },
        )

        // Background stays blurred; the second lens separately refracts sharp Dock icons.
        Box(indicatorOffset
            .drawBackdrop(
                backdrop = backdrop, shape = { RoundedCornerShape(50) },
                effects = {
                    vibrancy(); blur(8.dp.toPx())
                    glassPressLens(material,light.pressProgress)
                },
                onDrawBackdrop = { draw ->
                    drawGlassPressBackdrop(light.pressProgress,
                        Offset(light.touchPosition.x-indicatorX(),light.touchPosition.y-(66.dp.toPx()-indicatorHeight)/2f),draw)
                },
                highlight = { Highlight.Default.copy(alpha = light.pressProgress) },
                shadow = { Shadow(alpha = light.pressProgress * 0.4f) },
                innerShadow = { InnerShadow(radius = 8.dp * light.pressProgress, alpha = light.pressProgress * 0.4f) },
                layerBlock = indicatorTransform,
                onDrawSurface = {
                    drawRect(p.card.copy(alpha = material.surfaceAlpha(if(p.dark)0.45f else 0.40f)))
                    drawRect(p.ink.copy(alpha = 0.10f * (1f - animation.pressProgress)))
                },
            ).then(indicatorSize))
        Box(indicatorOffset
            .drawBackdrop(
                backdrop = icons, shape = { RoundedCornerShape(50) },
                effects = {
                    glassPressLens(material,light.pressProgress)
                },
                onDrawBackdrop = { draw ->
                    drawGlassPressBackdrop(light.pressProgress,
                        Offset(light.touchPosition.x-indicatorX(),light.touchPosition.y-(66.dp.toPx()-indicatorHeight)/2f),draw)
                },
                highlight = null, shadow = null, layerBlock = indicatorTransform,
            ).then(indicatorSize))

        // Sole touch host: DOWN must belong to this Dock; scaled visuals never enlarge its hit area.
        Box(Modifier.fillMaxSize().then(light.gestureModifier).pointerInput(rtl, tabWidth) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startIndex = (if (rtl) 3f - (down.position.x - inset) / tabWidth
                    else (down.position.x - inset) / tabWidth).toInt().coerceIn(0, 3)
                var dragged = false
                var canceled = false
                var fraction = animation.value
                val startValue = fraction
                animation.press()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) { canceled = true; break }
                    val delta = change.position.x - down.position.x
                    if (!dragged && abs(delta) > viewConfiguration.touchSlop) dragged = true
                    if (dragged) {
                        fraction = (startValue + delta / tabWidth * if (rtl) -1f else 1f).coerceIn(0f, 3f)
                        animation.updateValue(fraction)
                    }
                    if (!change.pressed) break
                }
                val index = if (canceled) latestSelected.ordinal
                    else if (dragged) fraction.roundToInt().coerceIn(0, 3) else startIndex
                animation.animateToValue(index.toFloat())
                animation.release()
                if (!canceled) latestSelect(AppTab.entries[index])
            }
        })
    }
}

@Composable
private fun DockLabels(
    modifier: Modifier, selected: AppTab, tinted: Boolean, scale: () -> Float,
    onSelect: ((AppTab) -> Unit)?,
) {
    val p = LocalPaper.current
    Row(modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppTab.entries.forEach { tab ->
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
