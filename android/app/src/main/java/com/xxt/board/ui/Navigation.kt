package com.xxt.board.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.xxt.board.ui.components.AppIcons

// Re-enable the existing schedule entry here; its route, screen and saved data remain intact.
internal const val ScheduleEntryEnabled = false

/** 底部主入口。隐藏入口不占 Dock 空间，页面路由仍保留。 */
enum class AppTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    BOARD("board", "作业", AppIcons.Assignment),
    SCHEDULE("schedule", "课表", AppIcons.Calendar),
    STATS("stats", "统计", AppIcons.BarChart),
    SETTINGS("settings", "设置", AppIcons.Tune),

    ;

    val mainEntryVisible: Boolean get() = this != SCHEDULE || ScheduleEntryEnabled
}

internal val MainDockTabs: List<AppTab> = AppTab.entries.filter { it.mainEntryVisible }
