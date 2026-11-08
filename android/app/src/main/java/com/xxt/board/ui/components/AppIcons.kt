package com.xxt.board.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 手绘线性图标（24 网格、1.7 线宽、圆头端点）。
 * 不用字体图标，避免基线偏移和豆腐块。
 */
object AppIcons {

    private fun line(name: String, vararg paths: String, width: Float = 1.7f): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    private const val RRECT_DOC =
        "M7.1,3.5 L16.9,3.5 A2.6,2.6 0,0 1,19.5 6.1 L19.5,17.9 A2.6,2.6 0,0 1,16.9 20.5 " +
            "L7.1,20.5 A2.6,2.6 0,0 1,4.5 17.9 L4.5,6.1 A2.6,2.6 0,0 1,7.1 3.5 Z"

    /** 作业：文档 + 三条横线 */
    val Assignment: ImageVector by lazy {
        line("Assignment", RRECT_DOC, "M8.5,8.5 L15.5,8.5 M8.5,12 L15.5,12 M8.5,15.5 L13,15.5")
    }

    /** 课表：日历 */
    val Calendar: ImageVector by lazy {
        line(
            "Calendar",
            "M6.1,5.5 L17.9,5.5 A2.6,2.6 0,0 1,20.5 8.1 L20.5,17.9 A2.6,2.6 0,0 1,17.9 20.5 " +
                "L6.1,20.5 A2.6,2.6 0,0 1,3.5 17.9 L3.5,8.1 A2.6,2.6 0,0 1,6.1 5.5 Z",
            "M3.5,10.2 L20.5,10.2",
            "M8.2,3.4 L8.2,7.6",
            "M15.8,3.4 L15.8,7.6",
        )
    }

    /** 统计：三根竖条 */
    val BarChart: ImageVector by lazy {
        line("BarChart", "M6,19 L6,13.5 M12,19 L12,7 M18,19 L18,10.5", width = 2.2f)
    }

    /** 设置 / 筛选：两条滑杆 + 两个旋钮 */
    val Tune: ImageVector by lazy {
        line(
            "Tune",
            "M4,8.5 L20,8.5",
            "M4,15.5 L20,15.5",
            "M11.4,8.5 A2.2,2.2 0,1 1,7 8.5 A2.2,2.2 0,1 1,11.4 8.5 Z",
            "M17.2,15.5 A2.2,2.2 0,1 1,12.8 15.5 A2.2,2.2 0,1 1,17.2 15.5 Z",
        )
    }

    /** 下拉箭头 */
    val ChevronDown: ImageVector by lazy {
        line("ChevronDown", "M6,9.5 L12,15.5 L18,9.5", width = 2.2f)
    }

    val ChevronLeft: ImageVector by lazy {
        line("ChevronLeft", "M15,5.5 L8.5,12 L15,18.5", width = 2f)
    }

    val ChevronRight: ImageVector by lazy {
        line("ChevronRight", "M9,5.5 L15.5,12 L9,18.5", width = 2f)
    }

    /** 勾选 */
    val Check: ImageVector by lazy {
        line("Check", "M5,12.5 L9.5,17 L19,7.5", width = 2.4f)
    }

    /** 一键跳转（打开 ↗） */
    val OpenInNew: ImageVector by lazy {
        line("OpenInNew", "M8.5,15.5 L15.5,8.5 M9.9,8.5 L15.5,8.5 L15.5,14.1", width = 2.2f)
    }

    /** 火焰：外轮廓 + 内焰 */
    val Fire: ImageVector by lazy {
        line(
            "Fire",
            "M12,3 C9,7 6,9.5 6,13 C6,16.3 8.7,19 12,19 C15.3,19 18,16.3 18,13 " +
                "C18,10.5 16.5,8.5 15,7 C15,8.6 14,9.8 12.6,9.8 C11.2,9.8 10.4,8.8 10.4,7.4 " +
                "C10.4,6 11.2,4.2 12,3 Z",
            "M12,12.5 C10.8,14 10,15 10,16 C10,17.1 10.9,18 12,18 C13.1,18 14,17.1 14,16 " +
                "C14,15 13.2,14 12,12.5 Z",
        )
    }

    /** 返回 */
    val ArrowBack: ImageVector by lazy {
        line("ArrowBack", "M20,12 L4.5,12 M11,5.5 L4.5,12 L11,18.5")
    }

    /** 刷新 / 同步 */
    val Sync: ImageVector by lazy {
        line(
            "Sync",
            "M20,12 A8,8 0,0 1,6.2 17.4",
            "M4,12 A8,8 0 0,1 17.8,6.6",
            "M17.8,2.9 L17.8,6.9 L13.8,6.9",
            "M6.2,21.1 L6.2,17.1 L10.2,17.1",
        )
    }

    /** 更多 */
    val MoreVert: ImageVector by lazy {
        line(
            "MoreVert",
            "M12,5.6 A0.9,0.9 0,1 1,11.99 5.6",
            "M12,12 A0.9,0.9 0,1 1,11.99 12",
            "M12,18.4 A0.9,0.9 0,1 1,11.99 18.4",
            width = 2.2f,
        )
    }

    /** 关闭 */
    val Close: ImageVector by lazy {
        line("Close", "M6.5,6.5 L17.5,17.5 M17.5,6.5 L6.5,17.5", width = 2f)
    }

    /** 导入 */
    val Add: ImageVector by lazy {
        line("Add", "M12,5 L12,19 M5,12 L19,12", width = 2f)
    }

    /** 删除 */
    val Delete: ImageVector by lazy {
        line(
            "Delete",
            "M4.5,7 L19.5,7",
            "M9.5,7 L9.5,4.5 L14.5,4.5 L14.5,7",
            "M6.5,7 L7.5,20 L16.5,20 L17.5,7",
        )
    }

    /** 通知 */
    val Notifications: ImageVector by lazy {
        line(
            "Notifications",
            "M18.5,16.5 L5.5,16.5 L7.2,13.8 L7.2,10.2 A4.8,4.8 0,0 1,16.8 10.2 L16.8,13.8 Z",
            "M9.9,19.4 A2.2,2.2 0,0 0,14.1 19.4",
        )
    }

    /** 深色模式 */
    val DarkMode: ImageVector by lazy {
        line("DarkMode", "M20,14.6 A8.6,8.6 0,0 1,9.4 4 A8.6,8.6 0,1 0,20 14.6 Z")
    }

    /** 一言 */
    val Quote: ImageVector by lazy {
        line(
            "Quote",
            "M9.5,6.5 L9.5,10.5 A4,4 0,0 1,5.5 14.5",
            "M17,6.5 L17,10.5 A4,4 0,0 1,13 14.5",
        )
    }

    /** 信息 */
    val Info: ImageVector by lazy {
        line(
            "Info",
            "M12,21 A9,9 0,1 1,12 3 A9,9 0,1 1,12 21 Z",
            "M12,11 L12,16.5",
            "M12,7.8 L12,7.9",
        )
    }

    /** 退出登录 */
    val Logout: ImageVector by lazy {
        line("Logout", "M15,8 L19,12 L15,16", "M19,12 L9.5,12", "M12.5,4 L5,4 L5,20 L12.5,20")
    }
}
