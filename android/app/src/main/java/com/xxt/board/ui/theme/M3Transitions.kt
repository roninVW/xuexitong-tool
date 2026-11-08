package com.xxt.board.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween

/**
 * Material 3 转场规格（Shared Axis X）。
 *
 * 数值全部取自 M3 motion tokens：
 * - 进场：300ms（medium2）+ emphasized-decelerate，行程 30% 容器宽度
 * - 出场：200ms（short4）+ emphasized-accelerate，行程 30%
 * - 淡入 210ms、淡出 90ms（fade through 的时长分配）
 *
 * 方向由调用方给出：往右切 → 新页从右侧进入；返回 → 镜像。
 */
object M3Transitions {

    private const val ENTER_MS = 300
    private const val EXIT_MS = 200
    private const val FADE_IN_MS = 210
    private const val FADE_OUT_MS = 90
    private const val TRAVEL = 0.30f

    /** 同级切换（底部 Tab）：按方向左右进入 */
    fun tabSlide(forward: Boolean): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(
            animationSpec = tween(ENTER_MS, easing = M3Easing.EmphasizedDecelerate),
        ) { width -> if (forward) (width * TRAVEL).toInt() else (-width * TRAVEL).toInt() } + fadeIn(
            animationSpec = tween(FADE_IN_MS, delayMillis = 30, easing = M3Easing.StandardDecelerate),
        ),
        initialContentExit = slideOutHorizontally(
            animationSpec = tween(EXIT_MS, easing = M3Easing.EmphasizedAccelerate),
        ) { width -> if (forward) (-width * TRAVEL).toInt() else (width * TRAVEL).toInt() } + fadeOut(
            animationSpec = tween(FADE_OUT_MS, easing = M3Easing.StandardAccelerate),
        ),
        sizeTransform = null,
    )

    /** 层级前进（首页 → 子页） */
    fun sharedAxisEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ENTER_MS, easing = M3Easing.EmphasizedDecelerate),
        ) { width -> if (forward) (width * TRAVEL).toInt() else (-width * TRAVEL).toInt() } + fadeIn(
            animationSpec = tween(FADE_IN_MS, delayMillis = 30, easing = M3Easing.StandardDecelerate),
        )

    /** 层级退出 */
    fun sharedAxisExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(EXIT_MS, easing = M3Easing.EmphasizedAccelerate),
        ) { width -> if (forward) (-width * TRAVEL).toInt() else (width * TRAVEL).toInt() } + fadeOut(
            animationSpec = tween(FADE_OUT_MS, easing = M3Easing.StandardAccelerate),
        )

    /**
     * WebView 页：只平移、不做整页 alpha。
     * WebView 在带 alpha 的图层里绘制，部分 ROM 会退化成软件合成。
     */
    fun webEnter(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ENTER_MS, easing = M3Easing.EmphasizedDecelerate),
        ) { width -> (width * TRAVEL).toInt() }

    fun webExit(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(EXIT_MS, easing = M3Easing.EmphasizedAccelerate),
        ) { width -> (width * TRAVEL).toInt() }

    fun webPopEnter(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ENTER_MS, easing = M3Easing.EmphasizedDecelerate),
        ) { width -> (-width * TRAVEL).toInt() }

    fun webPopExit(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(EXIT_MS, easing = M3Easing.EmphasizedAccelerate),
        ) { width -> (width * TRAVEL).toInt() }
}
