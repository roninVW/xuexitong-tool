package com.xxt.board.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.xxt.board.ui.components.AppIcons

/** 底部四个主入口。路由名同时作为 NavHost 的 destination。 */
enum class AppTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    BOARD("board", "作业", AppIcons.Assignment),
    SCHEDULE("schedule", "课表", AppIcons.Calendar),
    STATS("stats", "统计", AppIcons.BarChart),
    SETTINGS("settings", "设置", AppIcons.Tune),
}
