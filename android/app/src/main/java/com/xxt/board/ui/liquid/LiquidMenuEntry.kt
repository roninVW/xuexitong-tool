package com.xxt.board.ui.liquid

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/** Entry-only adaptation; ordinary glass buttons keep their existing feedback and layout. */
@Composable
fun LiquidMenuEntry(
    backdrop: Backdrop,
    state: LiquidBoardMenuState,
    menu: GlassMenu,
    selected: Boolean? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val drag = remember(scope, state, menu, density) {
        MenuDragFeedback(scope, with(density) { 9.dp.toPx() }) { !state.ownsEntry(menu) }
    }
    DisposableEffect(drag) { onDispose { drag.reset() } }
    // Logical anchors remain stable while the temporary visual layer follows the finger.
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val pressure = remember(drag) { { drag.pressProgress } }
    LiquidGlassButton(
        backdrop = backdrop,
        onClick = {
            val shift = drag.offset
            val warp = drag.deformation
            val scale = 1f + pressure() * 0.025f
            val width = anchor.width * warp.x * scale
            val height = anchor.height * warp.y * scale
            val center = anchor.center + shift
            state.open(menu, Rect(center.x-width/2f, center.y-height/2f,
                center.x+width/2f, center.y+height/2f))
        },
        selected = selected, compact = true, subdued = true,
        surfaceVisible = !state.ownsEntry(menu),
        feedbackPressure = pressure,
        feedbackLayer = {
            translationX = drag.offset.x
            translationY = drag.offset.y
            val scale = 1f + pressure() * 0.025f
            scaleX = scale * drag.deformation.x
            scaleY = scale * drag.deformation.y
        },
        modifier = modifier.onGloballyPositioned {
            anchor = it.boundsInRoot()
            if (menu == GlassMenu.TYPE) state.typeAnchor = anchor else state.filterAnchor = anchor
        }.then(drag.modifier),
        content = content,
    )
}
