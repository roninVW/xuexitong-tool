package com.xxt.board.ui.liquid

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** Each real child travels from the entry center; the final layout is never simply revealed in place. */
@Composable
fun MorphMenuColumn(progress:Float,sourceCenter:Offset,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    val interactive=progress>0.82f
    Layout(modifier=modifier.pointerInput(interactive) {
        if(!interactive)awaitEachGesture {
            val down=awaitFirstDown(false,PointerEventPass.Initial)
            down.consume()
            do {
                val event=awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach{it.consume()}
            } while(event.changes.any{it.pressed})
        }
    },content=content) { measurables,constraints ->
        val gap=10.dp.roundToPx()
        val children=measurables.map{it.measure(Constraints(maxWidth=constraints.maxWidth))}
        val height=children.sumOf{it.height}+gap*(children.size-1).coerceAtLeast(0)
        val t=progress.coerceIn(0f,1f)
        val scale=lerp(0.38f,1f,t)
        val alphaT=((t-0.04f)/0.74f).coerceIn(0f,1f)
        val visibility=alphaT*alphaT*(3f-2f*alphaT)
        val blur=(1f-visibility)*6.dp.toPx()
        val effect=if(blur>0.1f)RenderEffect.createBlurEffect(blur,blur,Shader.TileMode.CLAMP).asComposeRenderEffect()else null
        layout(constraints.maxWidth,height.coerceAtLeast(constraints.minHeight)) {
            var y=0
            children.forEach { child ->
                val startX=sourceCenter.x-child.width*scale/2
                val startY=sourceCenter.y-child.height*scale/2
                child.placeWithLayer(lerp(startX,0f,t).roundToInt(),lerp(startY,y.toFloat(),t).roundToInt()) {
                    transformOrigin=TransformOrigin(0f,0f)
                    scaleX=scale;scaleY=scale
                    alpha=visibility
                    renderEffect=effect
                }
                y+=child.height+gap
            }
        }
    }
}
