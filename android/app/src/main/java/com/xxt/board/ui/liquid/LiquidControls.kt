package com.xxt.board.ui.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.*
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.xxt.board.ui.theme.LocalPaper
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun sliderValueAt(fraction:Float,range:ClosedFloatingPointRange<Float>,steps:Int):Float {
    val t=fraction.coerceIn(0f,1f)
    val snapped=if(steps>0)(t*(steps+1)).roundToInt().toFloat()/(steps+1) else t
    return lerp(range.start,range.endInclusive,snapped)
}

/** Sole 48dp gesture host: held press, thumb drag and track positioning share one state machine. */
@Composable
fun MaterialSlider(value:Float,onValueChange:(Float)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    valueRange:ClosedFloatingPointRange<Float> = 0f..1f,steps:Int=0,onValueChangeFinished:(()->Unit)?=null) {
    val backdrop=LocalLiquidBackdrop.current
    if(backdrop==null) {Slider(value,onValueChange,modifier,enabled,valueRange,steps,onValueChangeFinished);return}
    val p=LocalPaper.current
    val material=LocalLiquidMaterial.current
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    var held by remember {mutableStateOf(false)}
    var display by remember {mutableFloatStateOf(value)}
    LaunchedEffect(value) {if(!held)display=value}
    val animatedPressure by animateFloatAsState(if(held)1f else 0f,spring(0.72f,500f),label="glass.slider.pressure")
    val pressure=animatedPressure.coerceIn(0f,1f)
    DisposableEffect(enabled){onDispose{held=false}}
    val track=rememberLayerBackdrop()
    val combined=rememberCombinedBackdrop(backdrop,track)
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min=48.dp)
        .semantics {
            if(!enabled)disabled()
            progressBarRangeInfo=ProgressBarRangeInfo(value,valueRange,steps)
            setProgress { target ->
                if(!enabled)false else {
                    val snapped=sliderValueAt((target-valueRange.start)/(valueRange.endInclusive-valueRange.start),valueRange,steps)
                    display=snapped;change(snapped);finish?.invoke();true
                }
            }
        },contentAlignment=Alignment.CenterStart) {
        val inset=20.dp
        val widthPx=constraints.maxWidth.toFloat()
        val insetPx=with(androidx.compose.ui.platform.LocalDensity.current){inset.toPx()}
        val travel=(widthPx-2*insetPx).coerceAtLeast(1f)
        fun fraction(v:Float)=((v-valueRange.start)/(valueRange.endInclusive-valueRange.start)).coerceIn(0f,1f)
        val visual=if(rtl)1f-fraction(display)else fraction(display)
        Canvas(Modifier.fillMaxWidth().padding(horizontal=inset).height(6.dp).layerBackdrop(track)) {
            val y=size.height/2
            val start=if(rtl)size.width else 0f
            val end=if(rtl)0f else size.width
            val alpha=if(enabled)1f else 0.35f
            drawLine(p.ink2.copy(alpha=0.20f*alpha),Offset(0f,y),Offset(size.width,y),size.height,StrokeCap.Round)
            drawLine(p.accent.copy(alpha=alpha),Offset(start,y),Offset(lerp(start,end,fraction(display)),y),size.height,StrokeCap.Round)
        }
        Box(Modifier.graphicsLayer {translationX=insetPx+visual*travel-20.dp.toPx()}
            .drawBackdrop(combined,shape={RoundedCornerShape(50)},
                effects={blur(lerp(6f,1.5f,pressure).dp.toPx());liquidLens(material,lerp(4f,10f,pressure).dp.toPx(),lerp(5f,18f,pressure).dp.toPx(), depthEffect = true, chromaticAberration = true)},
                highlight={if(pressure>0.001f)Highlight.Ambient.copy(alpha=pressure*0.8f)else null},
                shadow={Shadow(radius=3.dp,alpha=0.08f)},
                innerShadow={InnerShadow(radius=4.dp*pressure,alpha=0.25f*pressure)},
                layerBlock={scaleX=lerp(1f,1.45f,animatedPressure);scaleY=lerp(1f,1.45f,animatedPressure)},
                onDrawSurface={drawRect(Color.White.copy(alpha=material.surfaceAlpha(lerp(0.38f,0.03f,pressure))*(if(enabled)1f else 0.35f)))})
            .size(40.dp,24.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).pointerInput(enabled,rtl,valueRange,steps,widthPx) {
            if(!enabled)return@pointerInput
            awaitEachGesture {
                val down=awaitFirstDown(false)
                held=true
                val initial=display
                val initialVisual=if(rtl)1f-fraction(initial)else fraction(initial)
                val thumbCenter=insetPx+initialVisual*travel
                val thumbHit=abs(down.position.x-thumbCenter)<=24.dp.toPx()
                val grab=if(thumbHit)down.position.x-thumbCenter else 0f
                var dragging=false
                var edited=false
                fun updateAt(x:Float) {
                    val visualFraction=((x-grab-insetPx)/travel).coerceIn(0f,1f)
                    val next=sliderValueAt(if(rtl)1f-visualFraction else visualFraction,valueRange,steps)
                    if(display!=next){display=next;edited=true;change(next)}
                }
                try {
                    while(true) {
                        val event=awaitPointerEvent()
                        val pointer=event.changes.firstOrNull{it.id==down.id}
                        if(pointer==null)break
                        val dx=pointer.position.x-down.position.x
                        val dy=pointer.position.y-down.position.y
                        if(!dragging && (pointer.isConsumed || (abs(dy)>viewConfiguration.touchSlop && abs(dy)>abs(dx)))) {break}
                        if(!dragging && abs(dx)>viewConfiguration.touchSlop)dragging=true
                        if(dragging){pointer.consume();updateAt(pointer.position.x)}
                        if(!pointer.pressed) {
                            if(!dragging)updateAt(pointer.position.x)
                            pointer.consume()
                            break
                        }
                    }
                } finally {
                    held=false
                    // Release feedback only; the edited user value remains the committed value.
                    if(edited)finish?.invoke()
                }
            }
        })
    }
}

/** Native toggle semantics with indication explicitly removed; drag owns the event only after slop. */
@Composable
fun MaterialSwitch(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val backdrop=LocalLiquidBackdrop.current
    if(backdrop==null){Switch(checked=checked,onCheckedChange=onCheckedChange,modifier=modifier,enabled=enabled);return}
    val p=LocalPaper.current
    val material=LocalLiquidMaterial.current
    val latestChecked by rememberUpdatedState(checked)
    val change by rememberUpdatedState(onCheckedChange)
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val source=remember{MutableInteractionSource()}
    var held by remember{mutableStateOf(false)}
    var dragging by remember{mutableStateOf(false)}
    var dragValue by remember{mutableFloatStateOf(if(checked)1f else 0f)}
    val fraction by animateFloatAsState(if(dragging)dragValue else if(checked)1f else 0f,spring(0.8f,600f),label="glass.switch.value")
    val animatedPressure by animateFloatAsState(if(held)1f else 0f,spring(0.72f,500f),label="glass.switch.pressure")
    val pressure=animatedPressure.coerceIn(0f,1f)
    val canToggle=enabled && onCheckedChange!=null
    DisposableEffect(canToggle){onDispose{held=false;dragging=false}}
    val track=rememberLayerBackdrop()
    val combined=rememberCombinedBackdrop(backdrop,track)
    Box(modifier.size(64.dp,48.dp)
        .toggleable(checked,source,null,enabled&&onCheckedChange!=null,Role.Switch){change?.invoke(it)}
        .pointerInput(canToggle,rtl) {
            if(!canToggle)return@pointerInput
            awaitEachGesture {
                val down=awaitFirstDown(false)
                held=true
                dragging=false
                val initial=if(latestChecked)1f else 0f
                var canceled=false
                try {
                    while(true) {
                        val event=awaitPointerEvent()
                        val pointer=event.changes.firstOrNull{it.id==down.id}
                        if(pointer==null){canceled=true;break}
                        val dx=pointer.position.x-down.position.x
                        val dy=pointer.position.y-down.position.y
                        if(!dragging && (pointer.isConsumed || (abs(dy)>viewConfiguration.touchSlop&&abs(dy)>abs(dx)))){canceled=true;break}
                        if(abs(dx)>viewConfiguration.touchSlop)dragging=true
                        if(dragging){dragValue=(initial+dx/20.dp.toPx()*(if(rtl)-1f else 1f)).coerceIn(0f,1f);pointer.consume()}
                        if(!pointer.pressed)break
                    }
                    if(dragging&&!canceled)change?.invoke(dragValue>=0.5f)
                } finally {held=false;dragging=false}
            }
        },contentAlignment=Alignment.CenterStart) {
        Box(Modifier.layerBackdrop(track).drawBehind {
            drawRoundRect(lerpColor(p.ink2.copy(alpha=0.2f),p.accent,fraction).copy(alpha=if(enabled)1f else 0.35f),cornerRadius=CornerRadius(size.height/2))
        }.size(64.dp,28.dp))
        Box(Modifier.graphicsLayer{translationX=(if(rtl)22f-20f*fraction else 2f+20f*fraction).dp.toPx()}
            .drawBackdrop(combined,shape={RoundedCornerShape(50)},
                effects={blur(lerp(6f,1.5f,pressure).dp.toPx());liquidLens(material,lerp(3f,6f,pressure).dp.toPx(),lerp(5f,14f,pressure).dp.toPx(), depthEffect = true, chromaticAberration = true)},
                highlight={if(pressure>0.001f)Highlight.Ambient.copy(alpha=pressure*0.8f)else null},
                shadow={Shadow(radius=3.dp,alpha=0.08f)},
                layerBlock={scaleX=lerp(1f,1.4f,animatedPressure);scaleY=lerp(1f,1.4f,animatedPressure)},
                onDrawSurface={drawRect(Color.White.copy(alpha=material.surfaceAlpha(lerp(0.38f,0.03f,pressure))))})
            .size(40.dp,24.dp))
    }
}
