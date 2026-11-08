package com.xxt.board.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xxt.board.ui.components.GlassSurface
import com.xxt.board.ui.components.backdropSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xxt.board.data.AppSettings
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.components.AppIcons
import com.xxt.board.ui.components.PaperTopBar
import com.xxt.board.ui.components.blurEngineLabel
import com.xxt.board.ui.components.blurSupported
import com.xxt.board.ui.theme.JournalDivider
import com.xxt.board.ui.theme.JournalSectionLabel
import com.xxt.board.ui.theme.JournalTitle
import com.xxt.board.ui.theme.LocalPaper
import com.xxt.board.ui.theme.ThemeStyle
import com.xxt.board.ui.theme.derivedExamColor
import com.xxt.board.ui.theme.journalCard
import com.xxt.board.ui.wallpaper.importWallpaper
import kotlinx.coroutines.launch

/** 考试色候选：和作业色拉开色相，一眼能区分「作业 / 考试」 */
private val ExamSwatches = listOf(
    0xFFB4552D, 0xFF9C3550, 0xFF7A5EA8,
    0xFF3E6E8E, 0xFF3E7C6A, 0xFF8A6A33,
)

private val BaseSwatches = listOf(
    0xFFC4763F, 0xFFB4552D, 0xFFA9702F, 0xFF8A6A33,
    0xFF3E7C6A, 0xFF3E6E8E, 0xFF7A5EA8, 0xFF9C5A74,
)

/**
 * 设置页：全栈 M3。
 *
 * TopAppBar / Card / ListItem / SegmentedButton / Slider / Switch / AlertDialog /
 * HorizontalDivider / FilledTonalButton 全部官方组件，颜色与字阶只从 MaterialTheme 取。
 * 动效也全部由这些组件自带（开关滑块、state layer 涟漪、弹窗出入场）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    blurRadius: Int,
    onDarkMode: (Boolean?) -> Unit,
    onBlur: (Int) -> Unit,
    onGlassOpacity: (Int) -> Unit,
    onThemeStyle: (String) -> Unit,
    onWorkColor: (Long) -> Unit,
    onExamColor: (Long) -> Unit,
    onShowQuote: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onFlame: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onNavigate: (AppTab) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box {
                GlassSurface(
                    radiusDp = blurRadius,
                    tint = cs.surfaceContainerHigh,
                    shape = RoundedCornerShape(0.dp),
                    hairlineColor = cs.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.matchParentSize(),
                    content = {},
                )
                PaperTopBar(title = "设置")
            }
        },
    ) { inner ->
        // 采样层挂在整页根容器（窗口原点）上，而不是 padding 之内
        Box(Modifier.fillMaxSize().backdropSource()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 80.dp + 12.dp + navBottom + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SectionLabel("外观") }
                item {
                    M3Card {
                        Text(
                            "界面风格",
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "手账：点阵纸底 + 虚线卡片 + 纸胶带 + 手绘分隔；纸感保持原来的平纸面。",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            val styles = ThemeStyle.entries
                            val index = styles.indexOf(ThemeStyle.of(settings.themeStyle)).coerceAtLeast(0)
                            styles.forEachIndexed { i, item ->
                                SegmentedButton(
                                    selected = index == i,
                                    onClick = { onThemeStyle(item.key) },
                                    shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                                ) { Text(item.label) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        JournalDivider(color = cs.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "深浅色",
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            val options = listOf<Boolean?>(null, false, true)
                            val labels = listOf("跟随系统", "浅色", "深色")
                            val index = options.indexOf(settings.darkMode).coerceAtLeast(0)
                            labels.forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = index == i,
                                    onClick = { onDarkMode(options[i]) },
                                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                                ) { Text(label) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                item {
                    M3Card {
                        Text(
                            "模糊强度",
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Text(
                                text = if (blurSupported) "${settings.blurRadius} dp" else "不支持",
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.primary,
                            )
                        }
                        Slider(
                            value = settings.blurRadius.toFloat(),
                            onValueChange = { onBlur(it.toInt()) },
                            valueRange = 0f..40f,
                            steps = 19,
                            enabled = blurSupported,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Text(
                            text = "顶栏矩形与 Dock 胶囊内的原生高斯模糊；0 = 关闭（半透明纸面）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        JournalDivider(color = cs.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Text("玻璃透明度", style = MaterialTheme.typography.titleSmall, color = cs.primary)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (settings.glassOpacity == 0) "自动" else "${settings.glassOpacity}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.primary,
                            )
                        }
                        Slider(
                            value = settings.glassOpacity.toFloat(),
                            onValueChange = { onGlassOpacity(it.toInt()) },
                            valueRange = 0f..95f,
                            steps = 18,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Text(
                            text = "自动 = 跟着模糊强度走（0.94 → 0.58 线性）；拉到 25% 以下玻璃更透，文字会更受背景影响。",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "引擎：$blurEngineLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.outline,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(16.dp))

                        JournalDivider(color = cs.outlineVariant)
                        ColorSection("作业色（基色）", BaseSwatches, settings.workColor, onWorkColor)
                        Text(
                            "荧光色、状态色、科目点由作业色经 HSV 派生。",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        JournalDivider(color = cs.outlineVariant)
                        ExamColorSection(
                            base = Color(settings.workColor),
                            examArgb = settings.examColor,
                            onPick = onExamColor,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                item { SectionLabel("内容") }
                item {
                    M3Card {
                        SwitchItem(
                            icon = AppIcons.Quote,
                            title = "顶栏一言",
                            subtitle = "来自 hitokoto 在线接口，点顶栏那句话可换一句",
                            checked = settings.showQuote,
                            onCheckedChange = onShowQuote,
                        )
                        JournalDivider(color = cs.outlineVariant)
                        SwitchItem(
                            icon = AppIcons.Notifications,
                            title = "通知提醒",
                            subtitle = "关闭后不再后台同步，也不会发通知",
                            checked = settings.notifications,
                            onCheckedChange = onNotifications,
                        )
                    }
                }

                item { SectionLabel("账号") }
                item {
                    M3Card {
                        ListItem(
                            headlineContent = { Text("退出登录") },
                            supportingContent = { Text("清掉本机保存的 Cookie，缓存数据保留") },
                            trailingContent = {
                                FilledTonalButton(onClick = { confirmLogout = true }) {
                                    Text("退出")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }

                item { SectionLabel("关于") }
                item {
                    M3Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "作业板 v0.40.1",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "不是超星 / 学习通官方产品，只读取当前账号本人的课程与作业，" +
                                    "不提供代做、代交、刷课功能。数据全部留在本机。",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "标题字体：Noto Serif SC（SIL OFL 1.1）",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

}
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？") },
            text = { Text("会清掉本机保存的登录 Cookie，已同步的作业缓存会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }

}

// ---------------------------------------------------------------- M3 小组件

@Composable
private fun M3Card(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPaper.current
    Card(
        // 手账下换成虚线描边的卡片；纸感下是空 Modifier，外观不变
        modifier = Modifier
            .fillMaxWidth()
            .journalCard(9.dp, p.lineSoft),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    JournalSectionLabel(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * 考试色：可以单独挑一个和作业不同的颜色。
 * 「自动」= 由作业色派生（原来的行为），点已选中的色块也回到自动。
 */
@Composable
private fun ExamColorSection(base: Color, examArgb: Long, onPick: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val auto = examArgb == 0L
    val preview = if (auto) derivedExamColor(base) else Color(examArgb)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Text("考试色", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Surface(color = preview, shape = CircleShape, modifier = Modifier.size(18.dp), content = {})
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onPick(0L) }, enabled = !auto) {
            Text(if (auto) "自动" else "恢复自动")
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        ExamSwatches.forEach { argb ->
            val color = Color(argb)
            val picked = !auto && argb == examArgb
            Surface(
                color = color,
                shape = CircleShape,
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        width = if (picked) 2.dp else 0.dp,
                        color = if (picked) cs.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onPick(if (picked) 0L else argb) },
                content = {},
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "考试卡片的光效、考试徽标、已过期状态用这个颜色；点已选色块可回到「自动」。",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ColorSection(title: String, colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        colors.forEach { argb ->
            val color = Color(argb)
            Surface(
                color = color,
                shape = CircleShape,
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        width = if (argb == selected) 2.dp else 0.dp,
                        color = if (argb == selected) cs.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onPick(argb) },
                content = {},
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
