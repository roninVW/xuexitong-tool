package com.xxt.board.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect
import android.graphics.Shader
import kotlin.math.abs
import androidx.compose.ui.draw.clip
import com.xxt.board.ui.liquid.vendor.InteractiveHighlight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.xxt.board.ui.components.AppIcons
import com.xxt.board.ui.theme.LocalPaper
import kotlin.math.roundToInt

/** Plain options and one moving selection on the sole menu glass panel. */
@Composable
fun LiquidChoiceGroup(
    choices:List<GlassMenuChoice>,modifier:Modifier=Modifier,
    singleColumn:Boolean=false,onChoice:(String?)->Unit,
) {
    val p=LocalPaper.current
    val scope=rememberCoroutineScope()
    val lights=choices.map { choice -> remember(choice.key) { InteractiveHighlight(scope) } }
    var bounds by remember { mutableStateOf<List<Rect>>(emptyList()) }
    val selection=remember { Animatable(Rect.Zero,Rect.VectorConverter) }
    val selectedIndex=choices.indexOfFirst{it.selected}
    LaunchedEffect(selectedIndex,bounds) {
        val next=bounds.getOrNull(selectedIndex)?:return@LaunchedEffect
        if(selection.value.width==0f)selection.snapTo(next)
        else selection.animateTo(next,spring(0.8f,550f))
    }
    Layout(modifier=modifier.selectableGroup(),content={
        // A single painted selection moves over the shared menu glass; no nested lens.
        Box(Modifier.background(p.accent.copy(alpha=if(p.dark)0.20f else 0.13f),RoundedCornerShape(12.dp)))
        choices.forEachIndexed { index, choice ->
            val light=lights[index]
            val color by animateColorAsState(if(choice.selected)p.accent else p.ink,spring(0.9f,600f),label="choice.ink")
            val check by animateFloatAsState(if(choice.selected)1f else 0f,spring(0.9f,600f),label="choice.check")
            Row(Modifier.selectable(choice.selected,interactionSource=null,indication=null,role=Role.RadioButton,
                    onClick={onChoice(choice.key)})
                .clip(RoundedCornerShape(12.dp)).then(light.modifier).then(light.gestureModifier)
                .graphicsLayer {
                    val radius=(1f-abs(check.coerceIn(0f,1f)*2f-1f))*1.5f* density
                    renderEffect=if(radius>0.1f)RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP).asComposeRenderEffect()else null
                }
                .heightIn(min=44.dp).padding(horizontal=12.dp,vertical=8.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(AppIcons.Check,null,tint=color,modifier=Modifier.size(16.dp).graphicsLayer{alpha=check})
                Text(choice.label,color=color,style=MaterialTheme.typography.labelLarge,maxLines=1)
                Text(choice.count.toString(),color=color.copy(alpha=0.8f),style=MaterialTheme.typography.labelMedium,maxLines=1)
            }
        }
    }) { measurables,constraints ->
        val gap=8.dp.roundToPx()
        val foreground=measurables.drop(1).map {
            it.measure(Constraints(minWidth=if(singleColumn)constraints.maxWidth else 0,maxWidth=constraints.maxWidth))
        }
        var x=0;var y=0;var rowHeight=0
        val positions=foreground.map { child ->
            if(x>0 && (singleColumn || x+child.width>constraints.maxWidth)){x=0;y+=rowHeight+gap;rowHeight=0}
            Rect(x.toFloat(),y.toFloat(),(x+child.width).toFloat(),(y+child.height).toFloat())
                .also{x+=child.width+gap;rowHeight=maxOf(rowHeight,child.height)}
        }
        if(bounds!=positions)bounds=positions
        val selected=selection.value
        val selectionBackground=measurables[0].measure(Constraints.fixed(selected.width.roundToInt().coerceAtLeast(1),selected.height.roundToInt().coerceAtLeast(1)))
        layout(constraints.maxWidth,(y+rowHeight).coerceIn(constraints.minHeight,constraints.maxHeight)) {
            if(selectedIndex>=0)selectionBackground.place(selected.left.roundToInt(),selected.top.roundToInt())
            foreground.forEachIndexed{i,child->child.place(positions[i].left.roundToInt(),positions[i].top.roundToInt())}
        }
    }
}
