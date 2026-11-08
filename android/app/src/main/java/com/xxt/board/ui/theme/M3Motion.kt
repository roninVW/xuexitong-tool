package com.xxt.board.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.MotionDurationScale

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
}

/**
 * 强制 1× 的动画时长缩放。
 *
 * OriginOS 等 ROM 允许把开发者选项里的「动画程序时长缩放」设成 0，
 * 此时 Compose 所有基于 `AnimationSpec` 的动画会被瞬间完成。M3 的转场时长
 * 是规范的一部分，不该被系统开关吞掉，所以底层驱动时显式覆盖。
 */
object M3FixedMotionScale : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

/** fade through 的两段时长（M3: 出 90ms，进 210ms，不重叠） */
object M3FadeThrough {
    const val OUT_MS = 90
    const val IN_MS = 210
    val TOTAL_MS = OUT_MS + IN_MS
    const val IN_SCALE = 0.92f
}
