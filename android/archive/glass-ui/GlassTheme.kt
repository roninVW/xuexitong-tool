package com.xxt.board.ui.glass

import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader as PlatformRuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xxt.board.ui.components.BackdropState
import com.xxt.board.ui.components.blurSupported

/**
 * 液态玻璃皮肤。
 *
 * 做法参照 Kyant0/AndroidLiquidGlass（Apache-2.0）的绘制模型：
 * **折射（lens）+ 模糊 + 色散 + 边缘高光** 全部在一个 AGSL 着色器里完成，
 * 采样源是下层内容的 GraphicsLayer。
 *
 * 为何不直接依赖 `io.github.kyant0:backdrop:2.0.1`：
 * 该库要求 Compose Multiplatform 1.12 / Kotlin 2.4 / compileSdk 37，
 * 本工程是 Compose 1.7.8 / Kotlin 2.1.10 / compileSdk 36，版本差太大，拉进来会连带升级整条工具链。
 * 这里的着色器是同一套原理的独立实现，不包含该库代码。
 *
 * API 33（AGSL）以下或着色器编译失败 → 退化成「模糊 + 半透明 + 边缘高光」。
 */

@Immutable
data class GlassTokens(
    val dark: Boolean,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val accent: Color,
    val work: Color,
    val exam: Color,
    /** 玻璃底（含 alpha） */
    val tint: Color,
    /** 边框高光渐变的亮端 / 暗端 */
    val rimLight: Color,
    val rimDark: Color,
    /** 背景渐变三段 */
    val bg1: Color,
    val bg2: Color,
    val bg3: Color,
    val blobs: List<Color>,
    /** 浮起阴影 */
    val shadow: Color,
)

val LightGlass = GlassTokens(
    dark = false,
    ink = Color(0xFF0E1420),
    ink2 = Color(0xA60E1420),
    muted = Color(0x700E1420),
    accent = Color(0xFF0A6CFF),
    work = Color(0xFFD97B32),
    exam = Color(0xFFD64545),
    tint = Color(0x42FFFFFF),
    rimLight = Color(0xD9FFFFFF),
    rimDark = Color(0x33FFFFFF),
    bg1 = Color(0xFFE9F0FC),
    bg2 = Color(0xFFF7EDFF),
    bg3 = Color(0xFFE7F7FB),
    blobs = listOf(
        Color(0x8CFFB27A),
        Color(0x73FF8FA3),
        Color(0x8C7FD8FF),
        Color(0x80B8A6FF),
    ),
    shadow = Color(0x33101828),
)

val DarkGlass = GlassTokens(
    dark = true,
    ink = Color(0xFFF2F5FA),
    ink2 = Color(0xB3F2F5FA),
    muted = Color(0x73F2F5FA),
    accent = Color(0xFF6FD0FF),
    work = Color(0xFFFFB067),
    exam = Color(0xFFFF7A7A),
    tint = Color(0x6B0B101C),
    rimLight = Color(0x4DFFFFFF),
    rimDark = Color(0x0DFFFFFF),
    bg1 = Color(0xFF080A10),
    bg2 = Color(0xFF101528),
    bg3 = Color(0xFF0A111C),
    blobs = listOf(
        Color(0x59FF7A3D),
        Color(0x4DFF4D6D),
        Color(0x592FA8FF),
        Color(0x597C5CFF),
    ),
    shadow = Color(0x66000000),
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

// ---------------------------------------------------------------- 排版

private val GlassType = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun GlassTheme(dark: Boolean, content: @Composable () -> Unit) {
    val t = if (dark) DarkGlass else LightGlass
    val scheme = if (dark) darkColorScheme(
        primary = t.accent,
        onPrimary = Color(0xFF00131F),
        background = t.bg1,
        onBackground = t.ink,
        surface = Color(0xFF121722),
        onSurface = t.ink,
        surfaceVariant = Color(0xFF1A2130),
        onSurfaceVariant = t.ink2,
        outline = Color(0x33FFFFFF),
        error = t.exam,
        scrim = Color(0xB3000000),
    ) else lightColorScheme(
        primary = t.accent,
        onPrimary = Color.White,
        background = t.bg1,
        onBackground = t.ink,
        surface = Color(0xFFF7F9FD),
        onSurface = t.ink,
        surfaceVariant = Color(0xFFEDF1F8),
        onSurfaceVariant = t.ink2,
        outline = Color(0x33000000),
        error = t.exam,
        scrim = Color(0x66000000),
    )

    CompositionLocalProvider(LocalGlass provides t) {
        MaterialTheme(colorScheme = scheme, typography = GlassType, content = content)
    }
}

// ---------------------------------------------------------------- 背景

/**
 * 液态玻璃的"壁纸"：渐变 + 四团柔光 + 细点阵。
 * 点阵不是为了装饰——边缘折射需要有细节可弯折，否则玻璃看起来只是蒙了一层白。
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val minSide = size.minDimension
                val stepPx = 26.dp.toPx()
                val dots = ArrayList<Offset>(512)
                var y = stepPx / 2f
                while (y < size.height) {
                    var x = stepPx / 2f
                    while (x < size.width) {
                        dots += Offset(x, y)
                        x += stepPx
                    }
                    y += stepPx
                }
                val bg = Brush.linearGradient(listOf(t.bg1, t.bg2, t.bg3))
                fun blob(cx: Float, cy: Float, rr: Float, color: Color) =
                    Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = rr)

                onDrawBehind {
                    drawRect(bg)
                    drawCircle(blob(size.width * 0.16f, size.height * 0.12f, minSide * 0.85f, t.blobs[0]), minSide * 0.85f, Offset(size.width * 0.16f, size.height * 0.12f))
                    drawCircle(blob(size.width * 0.92f, size.height * 0.30f, minSide * 0.75f, t.blobs[1]), minSide * 0.75f, Offset(size.width * 0.92f, size.height * 0.30f))
                    drawCircle(blob(size.width * 0.10f, size.height * 0.86f, minSide * 0.95f, t.blobs[2]), minSide * 0.95f, Offset(size.width * 0.10f, size.height * 0.86f))
                    drawCircle(blob(size.width * 0.86f, size.height * 0.92f, minSide * 0.80f, t.blobs[3]), minSide * 0.80f, Offset(size.width * 0.86f, size.height * 0.92f))
                    drawPoints(
                        points = dots,
                        pointMode = PointMode.Points,
                        color = t.ink.copy(alpha = if (t.dark) 0.10f else 0.05f),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
    )
}

// ---------------------------------------------------------------- 着色器

private const val LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uRadius;
uniform float uBlur;
uniform float uRefract;
uniform float4 uTint;
uniform float uHighlight;

half4 main(float2 xy) {
    float2 halfSize = uSize * 0.5;
    float2 hs = halfSize - float2(uRadius, uRadius);
    float2 p = abs(xy - halfSize) - hs;
    float sd = length(max(p, float2(0.0, 0.0))) + min(max(p.x, p.y), 0.0) - uRadius;
    float depth = -sd;

    float band = max(uBlur * 1.5, 1.0);
    float t = clamp(depth / band, 0.0, 1.0);

    float2 dir = normalize(xy - halfSize + float2(0.0001, 0.0001));
    float bend = (1.0 - t) * (1.0 - t) * uRefract;
    float2 offset = dir * bend;
    float r = uBlur;

    half4 c = content.eval(xy + offset) * 0.28;
    c += content.eval(xy + offset + float2(r, 0.0)) * 0.09;
    c += content.eval(xy + offset + float2(-r, 0.0)) * 0.09;
    c += content.eval(xy + offset + float2(0.0, r)) * 0.09;
    c += content.eval(xy + offset + float2(0.0, -r)) * 0.09;
    c += content.eval(xy + offset + float2(r * 0.7, r * 0.7)) * 0.09;
    c += content.eval(xy + offset + float2(-r * 0.7, r * 0.7)) * 0.09;
    c += content.eval(xy + offset + float2(r * 0.7, -r * 0.7)) * 0.09;
    c += content.eval(xy + offset + float2(-r * 0.7, -r * 0.7)) * 0.09;

    half4 cr = content.eval(xy + offset * 1.08);
    half4 cb = content.eval(xy + offset * 0.90);
    c.r = mix(c.r, cr.r, 0.35);
    c.b = mix(c.b, cb.b, 0.35);

    float rim = 1.0 - smoothstep(0.0, 2.0, depth);
    float2 n = normalize(xy - halfSize + float2(0.0001, 0.0001));
    float lit = clamp(dot(n, normalize(float2(-0.75, -0.75))) * 0.5 + 0.5, 0.0, 1.0);
    c.rgb += rim * lit * uHighlight;
    c.rgb -= rim * clamp(dot(n, normalize(float2(0.75, 0.75))), 0.0, 1.0) * 0.12;

    c.rgb = mix(c.rgb, uTint.rgb, uTint.a);
    return c;
}
"""

@Composable
private fun rememberLiquidShader(): android.graphics.RuntimeShader? = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { PlatformRuntimeShader(LIQUID_GLASS_AGSL) }.getOrNull()
    } else {
        null
    }
}

/**
 * 液态玻璃面板：采样 [backdrop] 的下层内容，做折射 + 模糊 + 边缘高光。
 *
 * @param radiusDp 圆角半径，着色器要按它算 SDF
 * @param blurDp   模糊半径（着色器里做九抽样高斯，不再额外挂 BlurEffect）
 * @param refractDp 边缘折射强度
 */
@Composable
fun GlassPanel(
    backdrop: BackdropState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    radiusDp: Float = 26f,
    blurDp: Int = 18,
    refractDp: Float = 16f,
    tint: Color = LocalGlass.current.tint,
    highlight: Float = 0.55f,
    borderWidth: Int = 1,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalGlass.current
    val shader = rememberLiquidShader()
    val shaderEffect = remember(shader) {
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                PlatformRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }.getOrNull()
        } else {
            null
        }
    }
    val blurPx = with(LocalDensity.current) { blurDp.dp.toPx() }
    val refractPx = with(LocalDensity.current) { refractDp.dp.toPx() }
    val radiusPx = with(LocalDensity.current) { radiusDp.dp.toPx() }
    val fallbackEffect = remember(blurPx) {
        if (shaderEffect == null && blurSupported && blurPx > 0.5f) {
            BlurEffect(blurPx, blurPx, TileMode.Clamp)
        } else {
            null
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val effect = shaderEffect ?: fallbackEffect
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    renderEffect = effect
                    if (shader != null && shaderEffect != null) {
                        shader.setFloatUniform("uSize", size.width, size.height)
                        shader.setFloatUniform("uRadius", radiusPx)
                        shader.setFloatUniform("uBlur", blurPx)
                        shader.setFloatUniform("uRefract", refractPx)
                        shader.setFloatUniform(
                            "uTint",
                            tint.red, tint.green, tint.blue, tint.alpha,
                        )
                        shader.setFloatUniform("uHighlight", highlight)
                    }
                }
                .drawBehind {
                    translate(origin.x - backdrop.sourceOrigin.x, origin.y - backdrop.sourceOrigin.y) {
                        drawLayer(backdrop.source)
                    }
                },
        )

        // 降级路径：着色器不可用时用半透明底 + 高光边
        if (shaderEffect == null) {
            Box(Modifier.matchParentSize().background(tint))
        }
        if (borderWidth > 0) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        width = borderWidth.dp,
                        brush = Brush.verticalGradient(listOf(t.rimLight, t.rimDark)),
                        shape = shape,
                    ),
            )
        }
        content()
    }
}

/** 整屏/整条用的无缝玻璃（顶栏、dock 用圆角面板，不用它） */
@Composable
fun GlassBar(
    backdrop: BackdropState,
    modifier: Modifier = Modifier,
    blurDp: Int = 24,
    tint: Color = LocalGlass.current.tint,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassPanel(
        backdrop = backdrop,
        modifier = modifier,
        shape = RectangleShape,
        radiusDp = 0f,
        blurDp = blurDp,
        refractDp = 10f,
        tint = tint,
        highlight = 0.35f,
        borderWidth = 0,
        content = content,
    )
}
