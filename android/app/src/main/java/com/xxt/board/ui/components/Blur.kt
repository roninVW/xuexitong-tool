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
 * 1. `backdropSource()` 把主内容记录进 [GraphicsLayer]（一页只录一次），并原位绘制。
 * 2. `GlassSurface` 只在**自己的裁剪区域**（顶栏矩形 / Dock 胶囊）内重绘一份
 *    应用了 RenderEffect 的内容副本：`translate(-(G-L)) { drawLayer(source) }`。
 * 3. 半透明纸面 tint 与前景内容画在这份副本**之外**，因此文字图标绝对清晰。
 *
 * 把 RenderEffect 挂在纯色背景上等于无效果——这里挂的层里只有 `drawLayer(source)`，
 * 即栏后的真实内容，不含任何纯色底。
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

/** 被采样的主内容：一块 RenderNode + 它在窗口里的原点 */
class BackdropState internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    /** 是否已经录过至少一帧：没录过时玻璃只画纸面，避免画出上一页的残影 */
    internal var recorded by mutableStateOf(false)
}

/**
 * 页面当前的动画变换（切页时由 [com.xxt.board.ui.theme.M3PageSwitcher] 每帧写入）。
 *
 * 玻璃是**实时遮罩**：采样层里画的内容必须带着页面此刻的位移/缩放，
 * 否则顶栏与 Dock 里会是一张「最终位置」的静止贴图，页面在动、玻璃里的内容不动。
 */
class PageMotion {
    // 用 snapshot state：Dock 的绘制需要读到它们，值变了才会重绘那一帧
    var translationX by mutableStateOf(0f)
    var translationY by mutableStateOf(0f)
    var scale by mutableStateOf(1f)
}

val LocalPageMotion = staticCompositionLocalOf { PageMotion() }

/**
 * 当前这一页是不是「前台页」。
 *
 * 切页动画里新旧两页会同时存在，而 [LocalBackdrop] 只有一份采样层；
 * 如果两页都往里录，后画的会把前一页覆盖掉，玻璃里就会闪出另一页的内容。
 * 所以只有前台页录制，后台页照常画自己（不录）。
 */
val LocalPageFocus = staticCompositionLocalOf { true }

@Composable
fun rememberBackdrop(): BackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropState(layer) }
}

val LocalBackdrop = staticCompositionLocalOf<BackdropState?> { null }

/** 每个一级页面的根容器：建采样层、铺纸色、把 state 放进 [LocalBackdrop] */
@Composable
fun BackdropHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = rememberBackdrop()
    // 页面位移只有一份：切页动画写它，Dock（页面外）读它来补偿采样点
    val motion = remember { PageMotion() }
    // 背景只用纯纸色（不铺点阵/横线纹理）
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBackdrop provides state,
        LocalPageMotion provides motion,
    ) {
        Box(modifier.fillMaxSize().pageBackground(), content = content)
    }
}

/**
 * 标在主内容容器上：把**完整一帧**（页面底色 + 壁纸 + 全部主内容）录进采样层。
 *
 * 之前只录 `drawContent()`，采样层是透明底，壁纸与页面背景没进采样层，
 * 于是这些元素穿过顶栏 / Dock 时看起来「没被模糊掉」。现在 record 里先铺
 * 真实背景再画内容；屏幕上仍走原来的 `drawContent()`（不再画一遍 layer），
 * 所以视频壁纸不会被一帧静态图盖住。
 */
@Composable
fun Modifier.backdropSource(): Modifier {
    val state = LocalBackdrop.current ?: return this
    if (!blurSupported) return this
    val focus = LocalPageFocus.current
    val surface = MaterialTheme.colorScheme.surface
    val wallpaper = LocalWallpaper.current
    return this
        .onGloballyPositioned { state.origin = it.positionInRoot() }
        .drawWithContent {
            val content = this
            if (focus) {
                runCatching {
                    state.layer.record {
                        // 采样层统一按**窗口坐标**录制。
                        // 之前直接录 drawContent()，一旦容器带 padding（设置/统计/课表的
                        // `.padding(inner)`），层的原点就被顶栏顶下去了；玻璃按原点差平移时
                        // 整页内容会整体错位，看起来就是「玻璃里显示的是别处的内容」。
                        // 这里先把画布移到窗口原点，再铺底色 / 壁纸 / 内容。
                        translate(-state.origin.x, -state.origin.y) {
                            // 录制层外扩一圈纯背景色：切页时采样区会被推到内容之外，
                            // 那里没有像素就会糊成黑块，先垫一层同色底再画内容。
                            val pad = 600f
                            drawRect(
                                color = surface,
                                topLeft = Offset(-pad, -pad),
                                size = Size(
                                    size.width + state.origin.x + pad * 2,
                                    size.height + state.origin.y + pad * 2,
                                ),
                            )
                            val image = wallpaper.image
                            val screen = wallpaper.screen
                            if (wallpaper.active && image != null && screen.width > 0) {
                                drawImage(
                                    image = image,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(image.width, image.height),
                                    dstSize = screen,
                                    filterQuality = FilterQuality.Low,
                                )
                            }
                            content.drawContent()
                        }
                    }
                    state.recorded = true
                }
            }
            content.drawContent()
        }
}

/** 两级高斯（平台原生） + ColorMatrix 饱和增强 */
private fun platformBlurEffect(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect? =
    runCatching {
        val r = radiusPx.coerceAtLeast(1f)
        val blur = PlatformRenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
        val saturation = ColorMatrix().apply { setSaturation(1.18f) }
        val vibrancy = PlatformRenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(saturation),
        )
        // outer 后执行：先模糊，再补回被模糊洗掉的饱和度
        PlatformRenderEffect.createChainEffect(vibrancy, blur).asComposeRenderEffect()
    }.getOrNull()

/**
 * 顶栏 / Dock 的玻璃面板。
 *
 * @param radiusDp 0–40dp，0 = 关闭模糊（仍保留同布局半透明纸面）
 * @param tint     玻璃底色（一般传 card）
 */
@Composable
fun GlassSurface(
    radiusDp: Int,
    tint: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    hairlineColor: Color? = null,
    /**
     * 这块玻璃是否画在**页面之外**（Dock 就是）。
     *
     * 页面里的顶栏跟着页面一起动，采样层坐标和它一致，不需要补偿；
     * Dock 是静止的，页面在切页时整体位移了 m，所以它要采样 `dockOrigin − m`，
     * 玻璃里的内容才会跟着动画一起滑 —— 这样遮罩是"实时"的，不是一张定格的贴图。
     */
    outsidePage: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = LocalBackdrop.current
    val motion = LocalPageMotion.current
    val radius = radiusDp.coerceIn(0, 40)
    val active = blurSupported && radius > 0 && state != null
    // 手动透明度优先；没设过就还是跟着模糊强度走
    val alpha = LocalGlassAlpha.current?.coerceIn(0.15f, 0.95f) ?: glassAlpha(radius)

    var glassOrigin by remember { mutableStateOf(Offset.Zero) }
    val radiusPx = with(LocalDensity.current) { radius.dp.toPx() }
    // 半径不变就复用同一个 RenderEffect，不每帧重建 Shader
    val effect = remember(radiusPx, active) {
        if (active && radiusPx > 0.5f) platformBlurEffect(radiusPx) else null
    }

    Box(modifier = modifier.clip(shape)) {
        if (active && effect != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { glassOrigin = it.positionInRoot() }
                    .graphicsLayer {
                        // 离屏合成：效果只作用于本层绘制的「栏后内容副本」
                        compositingStrategy = CompositingStrategy.Offscreen
                        renderEffect = effect
                    }
                    .drawBehind {
                        if (!state.recorded) return@drawBehind
                        // 页面外的玻璃（Dock）：采样点要减去页面当前位移，
                        // 玻璃里看到的就是"此刻"页面背后的内容（跟着动画走）
                        // 录制层已经外扩了纯色底，采样区不会落到空像素上，
                        // 所以这里恢复完整补偿：玻璃真正跟着页面走。
                        val mx = if (outsidePage) motion.translationX else 0f
                        val my = if (outsidePage) motion.translationY else 0f
                        val dx = glassOrigin.x - state.origin.x - mx
                        val dy = glassOrigin.y - state.origin.y - my
                        // 负号：玻璃局部 (x,y) 要显示同一窗口坐标上的主内容
                        runCatching {
                            translate(-dx, -dy) { drawLayer(state.layer) }
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
