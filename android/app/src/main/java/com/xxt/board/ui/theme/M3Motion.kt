package com.xxt.board.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Material 3 motion token —— 按源码 / 设计规范的精确数值重写。
 *
 * 来源：
 * - Material Design 3 motion tokens（material-web `_easing.scss` / `_duration.scss`）
 *   https://github.com/material/material-web
 * - androidx.compose.material3.tokens.MotionTokens（同名 token）
 * - https://github.com/rshzrh/ui-design-skills/blob/main/skills/ui-android/motion.md
 *
 * 之前只写了两条 `fadeIn/fadeOut`，既没有完整时长体系，也没有 emphasized 曲线族，
 * 所以「M3 动效没完全实现」。这一版把 token 表和四种转场原语全部按源码补齐。
 */
object M3Duration {
    const val SHORT_1 = 50
    const val SHORT_2 = 100
    const val SHORT_3 = 150
    const val SHORT_4 = 200
    const val MEDIUM_1 = 250
    const val MEDIUM_2 = 300
    const val MEDIUM_3 = 350
    const val MEDIUM_4 = 400
    const val LONG_1 = 450
    const val LONG_2 = 500
    const val LONG_3 = 550
    const val LONG_4 = 600
    const val EXTRA_LONG_1 = 700
    const val EXTRA_LONG_2 = 800
    const val EXTRA_LONG_3 = 900
    const val EXTRA_LONG_4 = 1000
}

object M3Easing {
    /** md.sys.motion.easing.standard = cubic-bezier(0.2, 0, 0, 1) */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** standard-decelerate = cubic-bezier(0, 0, 0, 1) */
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** standard-accelerate = cubic-bezier(0.3, 0, 1, 1) */
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** emphasized = cubic-bezier(0.2, 0, 0, 1) */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** emphasized-decelerate = cubic-bezier(0.05, 0.7, 0.1, 1) —— 进场 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** emphasized-accelerate = cubic-bezier(0.3, 0, 0.8, 0.15) —— 出场 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * 页面切换专用 = cubic-bezier(0.22, 1, 0.36, 1)。
     *
     * 为什么不用 emphasized 系列：那两条是给「带缩放 / 淡入淡出」的转场配的，
     * 纯位移用 emphasized-decelerate 会前段太软、后段收不住（看起来"发飘"）。
     * 这条曲线前段推进快、后段长尾减速，位移类转场收尾最自然。
     * 进、出共用一条，两页节奏才一致（见 [PageTransitionSpec]）。
     */
    val PageSwitch: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}

// 注：这里原来还有一个 `M3FixedMotionScale`（强制 1× 时长缩放，让 ROM 把动画关掉时
// 也能播完）。它和"尊重系统减少动效"直接冲突，且已无引用，已删除 ——
// 现在由 `rememberPageTransitionSpec()` 读系统开关并主动降级。
