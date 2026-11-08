package com.xxt.board.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.xxt.board.ui.components.LocalPageFocus
import com.xxt.board.ui.components.LocalPageMotion
import com.xxt.board.ui.theme.M3Easing
import kotlinx.coroutines.launch

/**
 * 手驱动的页面切换器（M3 shared-axis X）。
 *
 * 性能要点（对比上一版）：
 * - 不再用 `withFrameNanos` + 每帧 `snapTo` 手动推进。后者每帧强制一次快照提交，
 *   是切页迟滞的主因；现在交给 `Animatable.animateTo` 走 Compose 动画时钟。
 * - 位移/透明度全部在 `graphicsLayer`（绘制期）里算，不触发重组、不重新测量。
 * - `PageMotion` 用 `snapshotFlow` 单向采样，只让 Dock 的玻璃重绘，不碰页面树。
 *
 * 曲线：进场 emphasized-decelerate、出场 emphasized-accelerate，行程 30%。
 */
@Composable
fun PageSwitcher(
    target: AppTab,
    modifier: Modifier = Modifier,
    durationMs: Int = 300,
    content: @Composable (AppTab) -> Unit,
) {
    val motion = LocalPageMotion.current
    val holder = rememberSaveableStateHolder()

    var current by remember { mutableStateOf(target) }
    var outgoing by remember { mutableStateOf<AppTab?>(null) }
    var forward by remember { mutableStateOf(true) }
    var widthPx by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target == current) return@LaunchedEffect
        forward = target.ordinal > current.ordinal
        outgoing = current
        current = target
        progress.snapTo(0f)

        val sign = if (forward) 1f else -1f
        val w = widthPx.toFloat().takeIf { it > 0f } ?: 1080f

        // 只把「当前页面相对静止位置的位移」推给玻璃采样，别的什么都不做
        val sampler = launch {
            snapshotFlow { progress.value }.collect { p ->
                motion.translationX =
                    sign * (1f - M3Easing.EmphasizedDecelerate.transform(p)) * w * 0.30f
            }
        }
        try {
            // 线性推进，曲线在绘制期按各自角色取，一次动画两个缓动
            progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        } finally {
            sampler.cancel()
            motion.translationX = 0f
            outgoing = null
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { widthPx = it.width }) {
        // 进场页
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = progress.value
                val travel = M3Easing.EmphasizedDecelerate.transform(p)
                translationX = (if (forward) 1f else -1f) * (1f - travel) * size.width * 0.30f
                alpha = (p / 0.5f).coerceIn(0f, 1f)
            },
        ) {
            CompositionLocalProvider(LocalPageFocus provides true) {
                holder.SaveableStateProvider(current) { content(current) }
            }
        }

        // 出场页
        outgoing?.let { old ->
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = progress.value
                    val travel = M3Easing.EmphasizedAccelerate.transform(p)
                    translationX = (if (forward) -1f else 1f) * travel * size.width * 0.30f
                    alpha = 1f - (p / 0.4f).coerceIn(0f, 1f)
                },
            ) {
                CompositionLocalProvider(LocalPageFocus provides false) {
                    holder.SaveableStateProvider(old) { content(old) }
                }
            }
        }
    }
}
