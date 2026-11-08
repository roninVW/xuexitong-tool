package com.xxt.board.ui.components

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.sin

/** 火焰配色：左深红 → 鲜红 → 橙 → 金黄 */
data class FlameColors(
    val deepRed: Color = Color(0xFF7A0B00),
    val red: Color = Color(0xFFE01B0B),
    val orange: Color = Color(0xFFFF7A18),
    val gold: Color = Color(0xFFFFD24A),
) {
    companion object {
        val Default = FlameColors()
    }
}

/**
 * 全局共享的火焰时钟。
 *
 * - 整个列表只跑一个 30fps 的帧循环，不是每张卡片各跑一个
 * - 关闭时直接返回，不启动协程（不是"画了但不显示"）
 * - 退到后台自动暂停（repeatOnLifecycle RESUMED）
 * - 读它的是 draw 阶段，只触发重绘，不触发重组
 */
@Composable
fun rememberFlameClock(enabled: Boolean, fps: Int = 30): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(enabled, fps) {
        if (!enabled) return@LaunchedEffect
        val intervalNanos = 1_000_000_000L / fps
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val start = withFrameNanos { it }
            var lastEmit = 0L
            while (true) {
                withFrameNanos { now ->
                    if (now - lastEmit >= intervalNanos) {
                        lastEmit = now
                        time.floatValue = (now - start) / 1_000_000_000f
                    }
                }
            }
        }
    }
    return time
}

/**
 * 卡片边缘的动态火焰。
 *
 * 实现：一个 AGSL RuntimeShader，每帧只更新 uniform（时间/尺寸/颜色），
 * 不重建对象、不重绘整卡（只画一层全尺寸半透明矩形，GPU 合成）。
 * API < 33 没有 RuntimeShader，退化成四条边的静态渐变带（仍有呼吸感）。
 *
 * 火焰只出现在距边 [bandDp] 以内，卡片内容区完全不受影响。
 */
fun Modifier.flameEdge(
    enabled: Boolean,
    timeProvider: () -> Float,
    colors: FlameColors = FlameColors.Default,
    intensity: Float = 1f,
    bandDp: Dp = 18.dp,
): Modifier {
    if (!enabled) return this
    return this.drawWithCache {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawWithCache onDrawBehind { }
        val bandPx = bandDp.toPx()
        val shader = createFlameShader()
        val paint = Paint().apply { isAntiAlias = true }
        val fallback = Brush.horizontalGradient(
            colors = listOf(colors.deepRed, colors.red, colors.orange, colors.gold),
            startX = 0f,
            endX = w,
        )

        onDrawBehind {
            val t = timeProvider()
            if (shader != null) {
                shader.setFloatUniform("uSize", w, h)
                shader.setFloatUniform("uTime", t)
                shader.setFloatUniform("uIntensity", intensity)
                shader.setFloatUniform("uBand", bandPx)
                shader.setFloatUniform("c0", colors.deepRed.red, colors.deepRed.green, colors.deepRed.blue)
                shader.setFloatUniform("c1", colors.red.red, colors.red.green, colors.red.blue)
                shader.setFloatUniform("c2", colors.orange.red, colors.orange.green, colors.orange.blue)
                shader.setFloatUniform("c3", colors.gold.red, colors.gold.green, colors.gold.blue)
                paint.shader = shader
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(0f, 0f, w, h, paint)
                }
            } else {
                // 兜底：只画四条边，中心留白
                val breathe = 0.55f + 0.18f * sin(t * 2.6f)
                drawRect(fallback, Offset(0f, 0f), Size(w, bandPx), alpha = breathe)
                drawRect(fallback, Offset(0f, h - bandPx), Size(w, bandPx), alpha = breathe)
                drawRect(fallback, Offset(0f, 0f), Size(bandPx, h), alpha = breathe * 0.85f)
                drawRect(fallback, Offset(w - bandPx, 0f), Size(bandPx, h), alpha = breathe * 0.85f)
            }
        }
    }
}

private fun createFlameShader(): RuntimeShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { RuntimeShader(FLAME_AGSL) }.getOrNull()
}

/**
 * 三层噪声叠加：
 *   n1  低频形态（火焰整体轮廓缓慢变形）
 *   n2  高频细节（内部纹理快速流动）
 *   flick 明暗扰动（呼吸感）
 * 颜色沿 x 从左到右插值，band 只保留边缘一圈。
 */
private const val FLAME_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float uIntensity;
uniform float uBand;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        v += amp * vnoise(p);
        p = p * 2.03 + 17.0;
        amp *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;

    // 到四条边的最小像素距离：只有边缘一圈有火，中心完全透明
    float d = min(min(fragCoord.x, uSize.x - fragCoord.x),
                  min(fragCoord.y, uSize.y - fragCoord.y));
    float band = 1.0 - smoothstep(0.0, uBand, d);

    // 从左向右流动：x 方向负偏移 + 时间
    float2 p = float2(uv.x * 5.0 - uTime * 1.7, uv.y * 9.0 + uTime * 0.6);
    float n1 = fbm(p);
    float n2 = fbm(p * 2.6 + float2(uTime * 2.1, -uTime * 1.4));
    float shape = n1 * 0.62 + n2 * 0.38;

    // 内部明暗扰动
    float flick = 0.72 + 0.28 * sin(uTime * 5.5 + uv.x * 11.0 + n1 * 7.0);

    float heat = band * shape * flick;

    // 颜色沿横向过渡：深红 → 红 → 橙 → 金
    float3 col = mix(c0, c1, smoothstep(0.0, 0.28, uv.x));
    col = mix(col, c2, smoothstep(0.28, 0.66, uv.x));
    col = mix(col, c3, smoothstep(0.66, 1.0, uv.x));

    float a = clamp(heat * uIntensity, 0.0, 1.0);
    return half4(col * (0.55 + 0.75 * heat), a);
}
"""
