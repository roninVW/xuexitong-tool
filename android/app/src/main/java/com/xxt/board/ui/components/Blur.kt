package com.xxt.board.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.xxt.board.ui.wallpaper.LocalWallpaper
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xxt.board.ui.wallpaper.pageBackground

/**
 * 原生高斯模糊（按设计规格 §5）。
 *
 * **只用 Android 平台 `RenderEffect.createBlurEffect`**，可链
 * `createColorFilterEffect` 做饱和增强；不用 Haze / 任何第三方模糊库、
 * 不用 AGSL / RuntimeShader、不截屏、不对整个 Activity 设 RenderEffect。
 *
 * 采样链路：
 *
 * 1. `backdropSource()` 把主内容记录进 [GraphicsLayer]（整条页面轨道一帧一录），并原位绘制。
 * 2. `GlassSurface` 只在**自己的裁剪区域**（顶栏矩形 / Dock 胶囊）内重绘一份
 *    应用了 RenderEffect 的内容副本：`translate(-(G-L)) { drawLayer(source) }`。
 * 3. 半透明纸面 tint 与前景内容画在这份副本**之外**，因此文字图标绝对清晰。
 *
 * 采样层是窗口坐标的整条轨道快照，所以玻璃里的内容永远等于"这个位置此刻画着的东西"：
 * 切页时接缝扫过 Dock，玻璃里的内容也跟着扫过，不会提前采到下一页。
 *
 * 强度 0–40dp（默认 28）；玻璃透明度随滑块由 **0.94 线性变化到 0.58**。
 * Android 12+ 用原生效果，旧版本回退成**同布局**的半透明纸面。
 */

val blurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val blurEngineLabel: String
    get() = if (blurSupported) {
        "RenderEffect.createBlurEffect（原生 · API ${Build.VERSION.SDK_INT}）"
    } else {
        "半透明纸面降级（API ${Build.VERSION.SDK_INT} < 31）"
    }

/** 0dp → 0.94，40dp → 0.58，线性（"自动"模式下用） */
fun glassAlpha(radiusDp: Int): Float = 0.94f - (radiusDp.coerceIn(0, 40) / 40f) * 0.36f

/**
 * 用户手动设定的玻璃纸面不透明度（0.15–0.95）。
 * `null` = 自动，回到 [glassAlpha] 随模糊强度线性变化的老行为。
 */
val LocalGlassAlpha = staticCompositionLocalOf<Float?> { null }

/**
 * 被采样的主内容。两块 RenderNode：
 *
 * - [pageLayer]：**单页**（前台页）的完整一帧，给页面里的顶栏玻璃用；
 * - [trackLayer]：**整条页面轨道**的完整一帧，给页面外的 Dock 用。
 *
 * 为什么必须两块：采样层里不能再画"引用这块采样层"的玻璃 —— 否则 HWUI 会把 RenderNode
 * 画进它自己（`RenderNode::prepareTreeImpl` 无限递归，直接栈溢出）。
 * 顶栏玻璃长在页面里，所以它只能取"不含自己"的单页层；
 * 而 Dock 在轨道之外，只有取整条轨道层，才是"此刻真正压在它背后的内容"。
 */
class BackdropState internal constructor(
    internal val pageLayer: GraphicsLayer,
    internal val trackLayer: GraphicsLayer,
) {
    internal var pageOrigin by mutableStateOf(Offset.Zero)
    /** 单页层是否已经录过至少一帧：没录过时玻璃只画纸面，避免画出上一页的残影 */
    internal var pageRecorded by mutableStateOf(false)

    internal var trackOrigin by mutableStateOf(Offset.Zero)
    internal var trackRecorded by mutableStateOf(false)
}

/**
 * 当前这一页是不是「前台页」。
 *
 * 切页时轨道上会有两页同时可见，而单页采样层只有一份：两页都往里录，
 * 后画的会把前一页覆盖掉，顶栏玻璃里就会闪出另一页的内容。所以只有前台页录。
 */
val LocalPageFocus = staticCompositionLocalOf { true }

@Composable
fun rememberBackdrop(): BackdropState {
    val page = rememberGraphicsLayer()
    val track = rememberGraphicsLayer()
    return remember(page, track) { BackdropState(page, track) }
}

val LocalBackdrop = staticCompositionLocalOf<BackdropState?> { null }

/** 每个一级页面的根容器：建采样层、铺纸色、把 state 放进 [LocalBackdrop] */
@Composable
fun BackdropHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = rememberBackdrop()
    // 背景只用纯纸色（不铺点阵/横线纹理）
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBackdrop provides state,
    ) {
        Box(modifier.fillMaxSize().pageBackground(), content = content)
    }
}

/**
 * 把**完整一帧**（页面底色 + 壁纸 + 主内容）录进采样层，并原位绘制。
 *
 * @param track true = 录进整条轨道层（Dock 用）；false = 录进单页层（顶栏用）
 *
 * 每页在自己的局部坐标记录一次，壁纸按窗口坐标对齐。
 * 玻璃通过窗口位置之差映射采样，不把页面位移重复计算。
 * 同一记录层直接显示，避免再绘制一次整个页面。
 */
@Composable
private fun Modifier.backdropRecord(track: Boolean): Modifier {
    val state = LocalBackdrop.current ?: return this
    if (!blurSupported) return this
    val surface = MaterialTheme.colorScheme.surface
    val wallpaper = LocalWallpaper.current
    return this
        .onGloballyPositioned {
            val at = it.positionInRoot()
            if (track) state.trackOrigin = at else state.pageOrigin = at
        }
        .drawWithContent {
            val content = this
            val layer = if (track) state.trackLayer else state.pageLayer
            val origin = if (track) state.trackOrigin else state.pageOrigin
            val recorded = runCatching {
                layer.record {
                    drawRect(surface)
                    val image = wallpaper.image
                    val screen = wallpaper.screen
                    if (wallpaper.active && image != null && screen.width > 0) {
                        translate(-origin.x, -origin.y) {
                            drawImage(image = image, srcOffset = IntOffset.Zero,
                                srcSize = IntSize(image.width, image.height), dstSize = screen,
                                filterQuality = FilterQuality.Low)
                        }
                    }
                    // Source pixels remain in source-local coordinates. GlassSurface maps
                    // glassOrigin - sourceOrigin once, including moving pages and system insets.
                    content.drawContent()
                }
                if (track) state.trackRecorded = true else state.pageRecorded = true
            }.isSuccess
            if (recorded) drawLayer(layer) else content.drawContent()
        }
}

/**
 * 单页采样层：标在一级页面的**内容根**上（不含顶栏，顶栏玻璃要取这份采样）。
 */
@Composable
fun Modifier.backdropSource(): Modifier =
    backdropRecord(track = false)

/**
 * 整条轨道采样层：标在 `PageSwitcher` 的轨道容器上，给页面外的 Dock 用。
 */
@Composable
fun Modifier.trackBackdropSource(): Modifier = backdropRecord(track = true)

/** 两级高斯（平台原生） + ColorMatrix 饱和增强 */
internal fun platformTopBarBlur(radiusPx: Float): PlatformRenderEffect? =
    runCatching {
        val r = radiusPx.coerceAtLeast(1f)
        val blur = PlatformRenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
        val saturation = ColorMatrix().apply { setSaturation(1.18f) }
        val vibrancy = PlatformRenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(saturation),
        )
        // outer 后执行：先模糊，再补回被模糊洗掉的饱和度
        PlatformRenderEffect.createChainEffect(vibrancy, blur)
    }.getOrNull()

/**
 * 顶栏 / Dock 的玻璃面板。
 *
 * 采样层是局部坐标的页面帧，采样点按窗口位置之差映射：
 * `translate(-(G - L))`。不涉及页面位移补偿 —— 页面在轨道上怎么动，
 * 采样层里就是怎么动的，玻璃里永远是这个位置此刻真正画着的内容。
 *
 * @param radiusDp 0–40dp，0 = 关闭模糊（仍保留同布局半透明纸面）
 * @param tint     玻璃底色（一般传 card）
 * @param track    true = 取整条轨道层（Dock）；false = 取单页层（页面内的顶栏）
 */
@Composable
fun GlassSurface(
    radiusDp: Int,
    tint: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    hairlineColor: Color? = null,
    track: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = LocalBackdrop.current
    val radius = radiusDp.coerceIn(0, 40)
    val active = blurSupported && radius > 0 && state != null
    // 手动透明度优先；没设过就还是跟着模糊强度走
    val alpha = LocalGlassAlpha.current?.coerceIn(0.15f, 0.95f) ?: glassAlpha(radius)

    var glassOrigin by remember { mutableStateOf(Offset.Zero) }
    val radiusPx = with(LocalDensity.current) { radius.dp.toPx() }
    // 半径不变就复用同一个 RenderEffect，不每帧重建 Shader
    val effect = remember(radiusPx, active) {
        if (active && radiusPx > 0.5f) platformTopBarBlur(radiusPx)?.asComposeRenderEffect() else null
    }

    Box(modifier = modifier.clip(shape)) {
        if (active && effect != null && state != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val outset = kotlin.math.ceil(radiusPx * 2f).toInt()
                        val child = measurable.measure(Constraints.fixed(
                            constraints.maxWidth + outset * 2, constraints.maxHeight + outset * 2))
                        layout(constraints.maxWidth, constraints.maxHeight) { child.place(-outset, -outset) }
                    }
                    .onGloballyPositioned { glassOrigin = it.positionInRoot() }
                    .graphicsLayer {
                        // 离屏合成：效果只作用于本层绘制的「栏后内容副本」
                        compositingStrategy = CompositingStrategy.Offscreen
                        renderEffect = effect
                    }
                    .drawBehind {
                        val layer = if (track) state.trackLayer else state.pageLayer
                        val origin = if (track) state.trackOrigin else state.pageOrigin
                        val recorded = if (track) state.trackRecorded else state.pageRecorded
                        if (!recorded) return@drawBehind
                        val dx = glassOrigin.x - origin.x
                        val dy = glassOrigin.y - origin.y
                        // 负号：玻璃局部 (x,y) 要显示同一窗口坐标上的主内容
                        runCatching {
                            translate(-dx, -dy) { drawLayer(layer) }
                        }
                    },
            )
        }

        // 半透明纸面：API < 31 或半径 0 时就是这一层，布局完全一致
        Box(Modifier.matchParentSize().background(tint.copy(alpha = alpha)))

        if (borderColor != null) {
            Box(Modifier.matchParentSize().border(1.dp, borderColor, shape))
        }
        if (hairlineColor != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(hairlineColor),
            )
        }
        content()
    }
}


/** A page owns its header capture; the outer track stays available to the default Dock. */
@Composable
fun PageBackdropHost(content: @Composable () -> Unit) {
    val parent = LocalBackdrop.current
    val page = rememberGraphicsLayer()
    val state = remember(page, parent) { BackdropState(page, parent?.trackLayer ?: page) }
    androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides state) { content() }
}

/** Shared Gaussian-only top background. Foreground title, icons and menus are outside
 * the filtered copy. Material/liquid selection never changes this blur pipeline. */
@Composable
fun GaussianTopBar(radiusDp: Int, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier) {
        GlassSurface(radiusDp = radiusDp, tint = cs.surfaceContainerHigh,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            hairlineColor = cs.outlineVariant.copy(alpha = 0.55f),
            modifier = Modifier.matchParentSize(), content = {})
        content()
    }
}
