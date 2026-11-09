package com.xxt.board.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 页面切换（转场）动效 —— **全应用唯一的一套参数和实现**。
 *
 * ## 运动模型：一条水平轨道
 *
 * 所有页面在同一个平面、同一 Z 层，横向相邻排开，靠整体平移"轨道"完成切换：
 * 新页从旧页**旁边**进入，旧页从另一侧离开，两页首尾相接、互不覆盖。
 * 底部四页由 `PageSwitcher` 用自定义 Layout 实现这条轨道；
 * 层级导航（登录 ↔ 主页）用同一组参数 —— `AnimatedContent` 里新旧两页各走**整屏**，
 * 位移量相同、方向相反，空间上同样是首尾相接（不重叠），只是容器不同。
 *
 * ## 参数
 *
 * - 轨迹：纯水平直线位移（只有 `translationX`），没有缩放 / 旋转 / 弹跳；
 * - 曲线：`cubic-bezier(0.22, 1, 0.36, 1)` —— 前段推进快、后段长尾减速，
 *   位移类转场收尾自然（Material 的 emphasized 系列是配"缩放 + 淡入"用的，纯位移会发飘）；
 * - 时长：380ms（要求 350–450ms）；进、出共用**同一个进度**，所以节奏完全一致；
 * - 前进 / 后退只差方向，没有第二套参数；
 * - 不做整页 alpha：整页 alpha 会强制离屏合成，而每页里还有一层 `GlassSurface`
 *   （RenderEffect + 采样层），一帧两次离屏必掉帧（v0.25 已经踩过一次）。
 *   单轨道结构本身不需要淡入淡出 —— 两页始终首尾相接，不存在"露底"。
 *
 * ## 减少动效
 *
 * 系统"移除动画"（无障碍 / 开发者选项时长缩放 = 0）时用 [PageTransition.Reduced]：
 * 零位移、零时长，直接换页。见 [rememberPageTransitionSpec]。
 */
@Immutable
data class PageTransitionSpec(
    /** 单程时长（ms）。0 = 系统关掉了动画，直接切页。 */
    val durationMs: Int = PageTransition.DURATION_MS,
    /** 进、出共用的缓动曲线 */
    val easing: Easing = M3Easing.PageSwitch,
) {
    /** 系统要求减少动效：不做位移，直接换页 */
    val reducedMotion: Boolean get() = durationMs <= 0

    internal fun <T> spec(): TweenSpec<T> = tween(durationMs, easing = easing)
}

object PageTransition {
    /** 380ms：350–450 区间内偏稳，够快也够连贯 */
    const val DURATION_MS = 380

    val Standard = PageTransitionSpec()

    /** 降级：不位移、不做动画（系统"移除动画"/时长缩放 = 0） */
    val Reduced = PageTransitionSpec(durationMs = 0, easing = LinearEasing)

    // ------------------------------------------------------------ 转场原语

    /** 进场：从整屏外滑到 0。forward = 从右侧进来。 */
    fun enter(forward: Boolean, spec: PageTransitionSpec = Standard): EnterTransition {
        val sign = if (forward) 1f else -1f
        return slideInHorizontally(spec.spec()) { full -> (full * sign).toInt() }
    }

    /** 出场：从 0 滑到整屏外。forward = 往左侧出去。 */
    fun exit(forward: Boolean, spec: PageTransitionSpec = Standard): ExitTransition {
        val sign = if (forward) 1f else -1f
        return slideOutHorizontally(spec.spec()) { full -> (-full * sign).toInt() }
    }

    // ------------------------------------------------------------ NavHost 用

    /**
     * 层级导航（登录 ↔ 主页）。进、出都走整屏、时长曲线相同，
     * 等于把两页放在同一条轨道上：`enter` 的位移量与 `exit` 的位移量相等、方向相反，
     * 任意时刻两页都首尾相接，不重叠、不露底。
     */
    fun navEnter(spec: PageTransitionSpec, forward: Boolean): EnterTransition =
        enter(forward = forward, spec = spec)

    fun navExit(spec: PageTransitionSpec, forward: Boolean): ExitTransition =
        exit(forward = forward, spec = spec)
}

/** 当前生效的转场参数。由 `MainActivity` 用 [rememberPageTransitionSpec] 提供，全局只读这一份。 */
val LocalPageTransition = staticCompositionLocalOf { PageTransition.Standard }

/**
 * 读取并监听系统的动画时长缩放。
 *
 * Android 上的 `prefers-reduced-motion` 就是这个开关：
 * 无障碍「移除动画」和开发者选项「动画程序时长缩放 = 0」都会把它置 0。
 * Compose 的 `tween` 本身会乘上这个缩放，但乘 0 之后仍会跑一帧位移；
 * 而且部分 ROM 只改这个值、不改 `MotionDurationScale`，所以这里显式读一次：
 * 为 0 时改用 [PageTransition.Reduced]（零位移、直接切页）。
 *
 * 监听而不是只在组合时读一次：用户可能在设置里改完动画再切回来。
 */
@Composable
fun rememberPageTransitionSpec(): PageTransitionSpec {
    val context = LocalContext.current.applicationContext
    var scale by remember(context) { mutableFloatStateOf(animatorScale(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = animatorScale(context)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }
    return if (scale <= 0f) PageTransition.Reduced else PageTransition.Standard
}

private fun animatorScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}.getOrDefault(1f)
