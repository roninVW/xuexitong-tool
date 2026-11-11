package com.xxt.board.ui.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.xxt.board.ui.components.AppIcons
import com.xxt.board.ui.theme.LocalPaper
import com.xxt.board.ui.theme.PaperType
import kotlin.math.roundToInt

enum class GlassMenu { TYPE, FILTER }
data class GlassMenuChoice(val key: String?, val label: String, val count: Int, val selected: Boolean)
data class GlassBoardModel(
    val types: List<GlassMenuChoice>, val statuses: List<GlassMenuChoice>, val subjects: List<GlassMenuChoice>,
    val visibleCount: Int, val onType: (String?) -> Unit, val onStatus: (String) -> Unit,
    val onSubject: (String?) -> Unit, val onReset: () -> Unit,
)

@Stable
class LiquidBoardMenuState {
    var active by mutableStateOf<GlassMenu?>(null)
        private set
    var displayed by mutableStateOf(GlassMenu.TYPE)
        private set
    val progress = Animatable(0f)
    var typeAnchor by mutableStateOf(Rect.Zero)
    var filterAnchor by mutableStateOf(Rect.Zero)
    var sourceAnchor by mutableStateOf(Rect.Zero)
        private set
    var model by mutableStateOf<GlassBoardModel?>(null)
    fun open(menu: GlassMenu, visualAnchor: Rect? = null) {
        if(progress.value>0f && displayed!=menu)return
        displayed = menu
        sourceAnchor = visualAnchor ?: if (menu == GlassMenu.TYPE) typeAnchor else filterAnchor
        active = menu
    }
    fun ownsEntry(menu: GlassMenu): Boolean = displayed == menu && (active != null || progress.value > 0f)
    fun dismiss() { active = null }
}

internal fun glassMenuBounds(
    anchor: Rect, viewport: Size, panel: Size, edge: Float, safeTop: Float, safeBottom: Float, rightAligned: Boolean,
): Rect {
    val left = (if (rightAligned) anchor.right-panel.width else anchor.left)
        .coerceIn(edge,(viewport.width-edge-panel.width).coerceAtLeast(edge))
    val top = (anchor.bottom+edge/2).coerceIn(safeTop,(viewport.height-safeBottom-panel.height).coerceAtLeast(safeTop))
    return Rect(left,top,left+panel.width,top+panel.height)
}

/** Absolute physical bounds, not a scaled popup. Its capsule continuously becomes a panel. */
internal fun morphGlassBounds(entry: Rect, panel: Rect, progress: Float): Rect {
    val t=progress.coerceIn(0f,1f)
    return Rect(lerp(entry.left,panel.left,t),lerp(entry.top,panel.top,t),
        lerp(entry.right,panel.right,t),lerp(entry.bottom,panel.bottom,t))
}
private fun reveal(value: Float, start: Float, end: Float): Float {
    val t=((value-start)/(end-start)).coerceIn(0f,1f)
    return t*t*(3f-2f*t)
}

/** One live glass surface owns the entry during the complete reversible morph. */
@Composable
fun LiquidBoardMenus(state: LiquidBoardMenuState, backdrop: Backdrop, modifier: Modifier=Modifier) {
    val p=LocalPaper.current
    val material=LocalLiquidMaterial.current
    val density=LocalDensity.current
    LaunchedEffect(state.active) {
        state.progress.animateTo(if(state.active!=null)1f else 0f,spring(0.95f,420f,0.0001f))
    }
    BackHandler(enabled=state.active!=null) { state.dismiss() }
    val model=state.model ?: return
    val progress=state.progress.value.coerceIn(0f,1f)
    if(state.active==null && progress==0f) return
    val displayed=state.displayed
    val scope=rememberCoroutineScope()
    val drag=remember(state,displayed,density) {
        MenuDragFeedback(scope,with(density){9.dp.toPx()}) {
            state.active==displayed && state.progress.value>0.9f
        }
    }
    DisposableEffect(drag) { onDispose { drag.reset() } }
    LaunchedEffect(state.active,drag) { if(state.active==null)drag.finish(cancelled=true) }
    val safeTop=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+8.dp
    val safeBottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+12.dp
    var origin by remember { mutableStateOf(Offset.Zero) }
    var target by remember { mutableStateOf(Rect.Zero) }
    val entryAnchor = if (state.active == null) {
        if (displayed == GlassMenu.TYPE) state.typeAnchor else state.filterAnchor
    } else state.sourceAnchor
    val anchor=entryAnchor.translate(-origin)
    val morph=morphGlassBounds(anchor,target.takeIf{it.width>0}?:anchor,progress)
    val radiusPx=lerp(anchor.height/2,with(density){24.dp.toPx()},progress)
    val shape=RoundedCornerShape(with(density){radiusPx.toDp()})
    val entryLabel=if(displayed==GlassMenu.TYPE) model.types.firstOrNull{it.selected}?.label?:"全部"
        else model.statuses.firstOrNull{it.selected}?.label?:"未完成"
    Box(modifier.fillMaxSize().onGloballyPositioned{origin=it.positionInRoot()}) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=(if(p.dark)0.24f else 0.12f)*progress))
            .clickable(interactionSource=null,indication=null){state.dismiss()})
        Layout(modifier=Modifier.fillMaxSize(),content={
            // The surface is measured at its changing physical size; sample coordinates stay fixed.
            Box(Modifier.drawBackdrop(
                backdrop=backdrop,shape={shape},
                effects={vibrancy();blur(lerp(4f,16f,progress).dp.toPx());
                    liquidLens(material,lerp(6f,24f,progress).dp.toPx(),lerp(7f,48f,progress).dp.toPx(), depthEffect = true, chromaticAberration = true)},
                highlight={Highlight.Default.copy(alpha=lerp(0.18f,if(p.dark)0.65f else 0.75f,progress))},
                shadow={Shadow(radius=lerp(2f,12f,progress).dp,alpha=lerp(0.04f,0.25f,progress))},
                onDrawSurface={drawRect(p.card.copy(alpha=material.surfaceAlpha(lerp(if(p.dark)0.34f else 0.30f,if(p.dark)0.76f else 0.68f,progress))))},
            ))
            // Full-size menu typography never undergoes nonuniform scaling.
            MorphMenuColumn(progress,anchor.center-target.topLeft-Offset(with(density){16.dp.toPx()},with(density){16.dp.toPx()}),Modifier
                .then(drag.modifier)
                .drawWithContent {
                    val local=morph.translate(-target.topLeft)
                    val path=Path().apply{addRoundRect(RoundRect(local,CornerRadius(radiusPx)))}
                    clipPath(path){this@drawWithContent.drawContent()}
                }
                .semantics{paneTitle=if(displayed==GlassMenu.TYPE)"作业与考试" else "状态与科目筛选"}
                .verticalScroll(rememberScrollState()).padding(16.dp)) {
                if(displayed==GlassMenu.TYPE) {
                    Text("显示类型",color=p.ink,style=MaterialTheme.typography.titleMedium)
                    LiquidChoiceGroup(model.types,Modifier.fillMaxWidth(),singleColumn=true) {
                        model.onType(it);state.dismiss()
                    }
                } else FilterContents(model){state.dismiss()}
            }
            // The original label continues inside the same glass during fusion.
            Row(Modifier.graphicsLayer{alpha=1f-reveal(progress,0.12f,0.45f)}
                .clearAndSetSemantics{}.padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(entryLabel,color=p.ink,style=if(displayed==GlassMenu.TYPE)PaperType.screenTitle else PaperType.groupName,maxLines=1)
                Icon(AppIcons.ChevronDown,null,tint=p.ink2,modifier=Modifier.size(14.dp))
            }
        }) { measurables,constraints ->
            val edge=with(density){16.dp.toPx()}
            val top=with(density){safeTop.toPx()}
            val bottom=with(density){safeBottom.toPx()}
            val desired=with(density){(if(displayed==GlassMenu.TYPE)240.dp else 344.dp).roundToPx()}
            val width=desired.coerceAtMost((constraints.maxWidth-edge*2).roundToInt()).coerceAtLeast(1)
            val content=measurables[1].measure(Constraints(minWidth=width,maxWidth=width,
                maxHeight=(constraints.maxHeight-top-bottom).roundToInt().coerceAtLeast(1)))
            val bounds=glassMenuBounds(anchor,Size(constraints.maxWidth.toFloat(),constraints.maxHeight.toFloat()),
                Size(content.width.toFloat(),content.height.toFloat()),edge,top,bottom,displayed==GlassMenu.FILTER)
            if(target!=bounds)target=bounds
            val current=morphGlassBounds(anchor,bounds,progress)
            val surface=measurables[0].measure(Constraints.fixed(current.width.roundToInt().coerceAtLeast(1),current.height.roundToInt().coerceAtLeast(1)))
            val entry=measurables[2].measure(Constraints(maxWidth=anchor.width.roundToInt().coerceAtLeast(1),maxHeight=anchor.height.roundToInt().coerceAtLeast(1)))
            layout(constraints.maxWidth,constraints.maxHeight) {
                val displacement=drag.offset
                surface.place((current.left+displacement.x).roundToInt(),(current.top+displacement.y).roundToInt())
                content.place((bounds.left+displacement.x).roundToInt(),(bounds.top+displacement.y).roundToInt())
                entry.place((current.left+displacement.x).roundToInt(),(current.top+displacement.y+(anchor.height-entry.height)/2).roundToInt())
            }
        }
    }
}

@Composable
private fun FilterContents(model:GlassBoardModel,onDismiss:()->Unit) {
    val p=LocalPaper.current
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text("筛选",color=p.ink,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
        MenuAction(onDismiss,modifier=Modifier.semantics{contentDescription="关闭筛选"}) {
            Text("完成",color=p.ink,style=MaterialTheme.typography.labelLarge)
        }
    }
    Text("状态",color=p.ink2,style=MaterialTheme.typography.labelLarge)
    LiquidChoiceGroup(model.statuses,Modifier.fillMaxWidth()){it?.let(model.onStatus)}
    HorizontalDivider(color=p.lineSoft.copy(alpha=0.5f),modifier=Modifier.padding(vertical=4.dp))
    Text("科目",color=p.ink2,style=MaterialTheme.typography.labelLarge)
    LiquidChoiceGroup(model.subjects,Modifier.fillMaxWidth()){model.onSubject(it)}
    HorizontalDivider(color=p.lineSoft.copy(alpha=0.5f),modifier=Modifier.padding(vertical=4.dp))
    Row(verticalAlignment=Alignment.CenterVertically) {
        MenuAction(model.onReset){Text("重置筛选",color=p.ink,style=MaterialTheme.typography.labelLarge)}
        Spacer(Modifier.weight(1f))
        Text("当前 ${model.visibleCount} 项",color=p.ink2,style=MaterialTheme.typography.bodySmall)
    }
}

/** Actions belong to the same glass sheet and add only touch light, never another surface. */
@Composable
private fun MenuAction(onClick:()->Unit,modifier:Modifier=Modifier,content:@Composable RowScope.()->Unit) {
    Row(modifier
        .clip(RoundedCornerShape(12.dp))
        .clickable(interactionSource=null,indication=null,role=Role.Button,onClick=onClick)
        .materialPressFeedback()
        .heightIn(min=44.dp).padding(horizontal=12.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,content=content)
}
