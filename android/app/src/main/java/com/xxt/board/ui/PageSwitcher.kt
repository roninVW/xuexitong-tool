package com.xxt.board.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.xxt.board.ui.components.LocalPageFocus
import com.xxt.board.ui.components.trackBackdropSource
import com.xxt.board.ui.theme.LocalPageTransition
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 单轨道页面切换器（底部四页）。
 *
 * ## 结构
 *
 * 一个视口（`clipToBounds`）+ 一条横向轨道：四个页面**始终并排**放在同一条轨道上，
 * 第 i 页的左边缘固定在轨道坐标 `i × 屏宽`，整条轨道靠一个位移量 `track` 平移：
 *
 * ```
 * 视口：[ 0 ................. 1080 ]
 * 轨道：      [ 作业 ][ 课表 ][ 统计 ][ 设置 ]      ← 同一平面、同一 Z、互不覆盖
 *              ↑ track = -i × w 时，第 i 页正好落在视口里
 * ```
 *
 * 前进（下标变大）时 `track` 变小 → 轨道整体左移：当前页从左边离开、下一页从右边进入，
 * 两页共用**同一个位移量、同一条时间轴**，边缘始终首尾相接（中间没有缝、也没有重叠）。
 * 后退就是同一个量往相反方向走，不需要额外的方向分支。
 *
 * ## 玻璃采样
 *
 * 采样层就挂在这个 Layout 上（[backdropSource]）：录的是**整条轨道**，坐标就是窗口坐标。
 * 所以顶栏 / Dock 的玻璃在自己所在位置取样，拿到的永远是"此刻真正画在这一列上的内容"——
 * 接缝扫到哪，玻璃里的内容就跟到哪，不会提前采到还没进视口的下一页，也不需要位移补偿。
 *
 * ## 中断
 *
 * `track` 是 [Animatable]：新目标到来时 `LaunchedEffect` 被取消，动画从**当前值**继续，
 * 不会回到起点、不会跳帧；轨道本身就是页面顺序，所以无论连点哪个 Tab，
 * 轨道都是沿同一条线继续走或折返，不会出现页面堆叠 / 残影。
 *
 * ## 性能
 *
 * - 位移只写进布局的 `place`（读 [Animatable.value]），不重组、不重新测量；
 * - 完全落在视口外的页不 `place` → 不参与绘制（目标页除外，它要留在采样层里）；
 * - 不做整页 alpha（离屏合成会拖帧），也没有缩放 / 旋转 / 弹跳。
 */
@Composable
fun PageSwitcher(
    target: AppTab,
    modifier: Modifier = Modifier,
    content: @Composable (AppTab) -> Unit,
) {
    val spec = LocalPageTransition.current
    val holder = rememberSaveableStateHolder()
    var widthPx by remember { mutableIntStateOf(0) }
    // 首次布局前 onSizeChanged 还没回调：用屏幕宽度兜底，保证第一帧轨道就在正确位置
    val density = LocalDensity.current
    val fallbackWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val pageWidth = widthPx.toFloat().takeIf { it > 0f } ?: fallbackWidth

    // 轨道位移：第 i 页停稳时 track = -i * w
    val track = remember { Animatable(-target.ordinal * pageWidth) }
    var placed by remember { mutableStateOf(false) }

    LaunchedEffect(target, pageWidth) {
        val goal = -target.ordinal * pageWidth
        // 首帧 / 尺寸刚确定：直接就位，不做动画（避免启动时"滑一下"）
        if (!placed || abs(goal - track.value) < 1f) {
            track.snapTo(goal)
            placed = true
            return@LaunchedEffect
        }
        // 被打断时这里会被取消，track 停在当前位置，下一次切换从这里继续
        track.animateTo(goal, spec.spec())
    }

    Layout(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            // 整条轨道录一份采样（窗口坐标），Dock 玻璃就地取样就是它背后的真实内容
            .trackBackdropSource()
            .onSizeChanged { widthPx = it.width },
        content = {
            // 四页都在轨道上、都保持组合：切页时不需要临时组合（不会因为首帧组合卡一下），
            // 各页滚动位置 / 状态也一直保留（SaveableStateHolder 只是多一层保险）。
            AppTab.entries.forEach { tab ->
                key(tab) {
                    // 单页采样层只有一份：只让目标页录，否则两页互覆盖、顶栏玻璃会闪
                    CompositionLocalProvider(LocalPageFocus provides (tab == target)) {
                        holder.SaveableStateProvider(tab.name) { content(tab) }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        // 每页都按视口尺寸测量：轨道上相邻两页正好首尾相接，不会有缝
        val placeables = measurables.map { it.measure(Constraints.fixed(w, h)) }
        val focusIndex = target.ordinal
        layout(w, h) {
            val base = track.value
            placeables.forEachIndexed { index, placeable ->
                val x = (base + index * w).roundToInt()
                // 完全在视口外：不放置 = 不绘制（静止时只有当前页参与绘制）。
                // 目标页例外：它要在采样层里存在，否则刚起步时 Dock 玻璃会采样到空处。
                if ((x >= w || x + w <= 0) && index != focusIndex) return@forEachIndexed
                placeable.place(x, 0)
            }
        }
    }
}
