package com.xxt.board.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xxt.board.ui.theme.M3Easing
import androidx.compose.ui.unit.sp
import com.xxt.board.data.Subject
import com.xxt.board.data.Task
import com.xxt.board.data.TaskStatus
import com.xxt.board.data.TaskType
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.theme.LocalAccent
import com.xxt.board.ui.theme.JournalDivider
import com.xxt.board.ui.theme.JournalSectionLabel
import com.xxt.board.ui.theme.JournalTitle
import com.xxt.board.ui.theme.LocalPaper
import com.xxt.board.ui.theme.journalCard
import com.xxt.board.ui.theme.PaperType
import com.xxt.board.ui.wallpaper.LocalWallpaper
import com.xxt.board.ui.theme.subjectColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------- 工具

private val dueFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

fun dueText(dueAt: Long?): String =
    if (dueAt == null) "无截止时间"
    else Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).format(dueFormatter)

fun remainText(dueAt: Long?, now: Long): String {
    if (dueAt == null) return "无截止"
    val ms = dueAt - now
    if (ms <= 0) return "已过期"
    val days = TimeUnit.MILLISECONDS.toDays(ms)
    val hours = TimeUnit.MILLISECONDS.toHours(ms) % 24
    return if (days > 0) "${days}天${hours}时" else "${TimeUnit.MILLISECONDS.toHours(ms)}时"
}

fun urgencyOf(dueAt: Long?, now: Long): Int = when {
    dueAt == null -> 0
    dueAt - now <= TimeUnit.HOURS.toMillis(24) -> 3
    dueAt - now <= TimeUnit.DAYS.toMillis(3) -> 2
    else -> 1
}

fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.TODO -> "未完成"
    TaskStatus.SUBMITTED -> "待批阅"
    TaskStatus.GRADED -> "已完成"
    TaskStatus.EXPIRED -> "已过期"
}

fun kindLabel(task: Task): String = when {
    task.type == TaskType.EXAM -> "考试"
    task.title.contains("实验") -> "实验"
    else -> "作业"
}

// ---------------------------------------------------------------- 顶栏（全应用统一）

/** 标题行高：所有页面的顶栏标题都放在这条 44dp 的行里，位置完全一致 */
val TopBarRowHeight = 44.dp

/** 标题左内边距：全应用统一，和 M3 TopAppBar 的 16dp 对齐 */
val TopBarStartPadding = 16.dp

/**
 * 统一的页面顶栏（不含背景）。
 *
 * 四个一级页 + 登录页都用它，因此「标题位置 / 字号」只有一个来源：
 * - 标题固定 [PaperType.screenTitle]（宋体 20sp 粗体），左侧固定 [TopBarStartPadding]；
 * - 标题行固定 [TopBarRowHeight]，副标题排在标题行下方同一左边界；
 * - 右侧操作区、标题上的下拉菜单、标题下方的附加行（看板的同步/一言）全部由插槽传入。
 *
 * 玻璃 / 纯色背景由调用方自己套（看板是 GlassSurface，其余页是 Box + GlassSurface）。
 */
@Composable
fun PaperTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleAccent: Boolean = false,
    onSubtitleClick: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    titleOverlay: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val p = LocalPaper.current
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopBarRowHeight)
                .padding(
                    start = if (leading == null) TopBarStartPadding else 4.dp,
                    end = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick)
                        else Modifier,
                    )
                    .padding(end = if (titleTrailing != null) 4.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JournalTitle(text = title, style = PaperType.screenTitle, color = p.ink)
                if (titleTrailing != null) {
                    Spacer(Modifier.width(5.dp))
                    titleTrailing()
                }
                titleOverlay?.invoke()
            }
            Spacer(Modifier.weight(1f))
            actions?.invoke()
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = PaperType.sync,
                color = if (subtitleAccent) p.accent else p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TopBarStartPadding, end = TopBarStartPadding, bottom = 6.dp)
                    .then(
                        if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick)
                        else Modifier,
                    ),
            )
        }
        below()
    }
}

// ---------------------------------------------------------------- 类型光效


/** 与原型 `--c1 / --c2 / --g1 / --g2 / --glow` 对应 */
data class GlowColors(
    val c1: Color,
    val c2: Color,
    val g1: Color,
    val g2: Color,
    val glow: Color,
)

@Composable
fun rememberGlow(isExam: Boolean): GlowColors {
    val accents = LocalAccent.current
    val dark = LocalPaper.current.dark
    return remember(isExam, accents, dark) {
        val c1 = if (isExam) accents.exam else accents.work
        val lightEnd = if (isExam) Color(0xFFFFD2BC) else Color(0xFFFFEDC4)
        val darkEnd = if (isExam) Color(0xFFFFC7AE) else Color(0xFFFFD9A0)
        val c2 = lerp(c1, if (dark) darkEnd else lightEnd, if (dark) 0.45f else 0.50f)
        val g1 = c1.copy(alpha = if (dark) (if (isExam) 0.28f else 0.26f) else (if (isExam) 0.25f else 0.23f))
        val g2 = c2.copy(alpha = if (dark) (if (isExam) 0.10f else 0.09f) else (if (isExam) 0.08f else 0.07f))
        val glow = c1.copy(alpha = if (dark) 0.45f else 0.55f)
        GlowColors(c1, c2, g1, g2, glow)
    }
}

// ---------------------------------------------------------------- 卡片

@Composable
fun TaskCard(
    task: Task,
    now: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onJump: () -> Unit,
) {
    val p = LocalPaper.current
    val glow = rememberGlow(task.type == TaskType.EXAM)
    // 手账把卡片圆角放大一点，并用虚线代替实线，像贴上去的索引卡
    val corner = if (p.journal) 11.dp else 5.dp
    val shape = RoundedCornerShape(corner)
    val level = urgencyOf(task.dueAt, now)
    var cardOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .onGloballyPositioned { cardOrigin = it.positionInRoot() }
            .then(
                if (p.journal) Modifier.journalCard(corner, p.lineSoft)
                else Modifier.border(1.dp, p.line, shape),
            )
            .clickable(onClick = onClick),
    ) {
        // ------------------------------------------------ 背景层（独立玻璃，不含前景）
        // 卡片底 + 静态光效：58% 宽度「从左侧渗入」的横向渐变 + 3dp 圆角亮边。
        // 严禁顶部受光，所以这里没有任何竖向高光渐变。
        Box(Modifier.matchParentSize().background(p.card)) {
            Box(
                Modifier.matchParentSize().drawWithCache {
                    val barWidth = 3.dp.toPx()
                    val barInset = 8.dp.toPx()
                    val sweep = size.width * 0.58f
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = glow.glow.toArgb()
                        maskFilter = BlurMaskFilter(14.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                    val sweepBrush = Brush.horizontalGradient(
                        colorStops = arrayOf(0f to glow.g1, 0.3f to glow.g2, 0.76f to Color.Transparent),
                        startX = 0f,
                        endX = sweep,
                    )
                    val barBrush = Brush.verticalGradient(listOf(glow.c1, glow.c2))

                    onDrawBehind {
                        drawRect(brush = sweepBrush, size = Size(sweep, size.height))
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawRect(
                                0f, barInset, barWidth, size.height - barInset, paint,
                            )
                        }
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(0f, barInset),
                            size = Size(barWidth, size.height - barInset * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth),
                        )
                    }
                },
            )
        }

        // ------------------------------------------------ 前景层（文字 / 图标 / 按钮，完全清晰）
        Row(
            Modifier.padding(start = 16.dp, end = 11.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = PaperType.cardTitle,
                    color = p.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindChip(text = kindLabel(task), glow = glow)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = task.courseName,
                        style = PaperType.meta,
                        color = p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MarkBadge(task = task, level = level, now = now)
                Spacer(Modifier.height(4.dp))
                // 直接跳学习通 App（不再走"复制链接再粘贴"）
                JumpButton(onClick = onJump)
            }
        }
    }
}

@Composable
private fun KindChip(text: String, glow: GlowColors) {
    val p = LocalPaper.current
    val bg = if (p.dark) glow.c1.copy(alpha = 0.20f) else glow.c1.copy(alpha = 0.13f)
    val fg = if (p.dark) glow.c2 else glow.c1
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text = text, style = PaperType.kind, color = fg)
    }
}

/** 荧光笔倒计时：只铺文字下半截，像真的用马克笔划过去 */
@Composable
fun MarkBadge(task: Task, level: Int, now: Long) {
    val p = LocalPaper.current
    // 有分数就展示：有些考试的状态字段不是「已批阅」但页面已经出分，
    // 之前卡 status == GRADED 会把这些分数吞掉
    val score = task.score?.takeIf { it.isNotBlank() }
    val mark = when {
        score != null -> p.accentSoft
        level == 3 -> p.markHot
        level == 2 -> p.markSoon
        else -> p.mark
    }
    val ink = when {
        score != null -> p.accent
        level == 3 -> p.badgeInkHot
        level == 2 -> p.badgeInkSoon
        else -> p.badgeInk
    }
    val text = score?.let { if (it.endsWith("分")) it else "$it 分" } ?: remainText(task.dueAt, now)

    Box(
        Modifier
            .drawWithCache {
                val top = size.height * 0.42f
                val h = size.height * 0.52f
                onDrawBehind {
                    drawRect(color = mark, topLeft = Offset(0f, top), size = Size(size.width, h))
                }
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = PaperType.badge, color = ink)
    }
}


/** 卡片右下角的「学习通」：交给学习通 App 打开这条作业 */
@Composable
private fun JumpButton(onClick: () -> Unit) {
    val p = LocalPaper.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (p.dark) Color(0x0AFFFFFF) else p.card.copy(alpha = 0.55f))
            .border(1.dp, p.lineSoft, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("学习通", style = PaperType.jump, color = p.ink2)
        Spacer(Modifier.width(3.dp))
        Icon(
            imageVector = AppIcons.OpenInNew,
            contentDescription = "在手机上打开学习通",
            tint = p.ink2,
            modifier = Modifier.size(10.dp),
        )
    }
}

// ---------------------------------------------------------------- 分组头（M3）

@Composable
fun GroupHeader(subject: Subject, count: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val p = LocalPaper.current
    val dot = subjectColor(subject, LocalAccent.current.base, p.dark)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (p.journal) 20.dp else 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (p.journal) {
            // 手账：科目名贴在一小段纸胶带上，前面不再用实心圆点
            JournalSectionLabel(text = subject.label, color = cs.primary)
        } else {
            Surface(
                color = dot,
                shape = CircleShape,
                modifier = Modifier.size(8.dp),
                content = {},
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = subject.label,
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        JournalDivider(modifier = Modifier.weight(1f), color = cs.outlineVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (p.journal) "· $count ·" else "$count",
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- 筛选 chip（M3 FilterChip）

/**
 * 对外签名保持不变，内部换成 M3 官方 [FilterChip]：
 * 选中态的 secondaryContainer、8dp 形状、state layer 涟漪、勾选图标全由 M3 提供。
 */
@Composable
fun PaperPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    count: Int? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (active) {
            {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        trailingIcon = if (count != null) {
            {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            null
        },
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- 空态（M3）

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = cs.secondaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cs.onSecondaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onAction) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

// ---------------------------------------------------------------- 底部 dock（M3 视觉 + 胶囊外形）

/**
 * 唯一保留的外形：左右 14 / 底 12 / 高 66 / 圆角 33 的悬浮胶囊。
 * 内部全按 M3 NavigationBar 规范重写：
 * 64×32 药丸 indicator、24dp 图标、labelMedium 标签、secondaryContainer 选中态，
 * 颜色过渡用 M3 short4 + standard easing，涟漪来自 M3 Surface 的 state layer。
 */
@Composable
fun PaperDock(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    blurRadius: Int,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val p = LocalPaper.current
    val dockHeight = 66.dp
    val dockRadius = dockHeight / 2
    val dockShape = RoundedCornerShape(dockRadius)
    val pillInset = 7.dp
    val pillRadius = dockRadius - pillInset

    // 点谁谁弹一下：每个项目一个缩放值，只做「按下缩 → 弹簧回弹」
    val bounce = remember { AppTab.entries.associateWith { Animatable(1f) } }
    // 点击当下立刻起动画：不再等 selected 变化 → 重组 → LaunchedEffect，少一帧延迟
    val dockScope = rememberCoroutineScope()
    fun bounceOn(tab: AppTab) {
        dockScope.launch {
            val anim = bounce.getValue(tab)
            anim.stop()
            anim.snapTo(1f)
            anim.animateTo(1.14f, tween(130, easing = FastOutSlowInEasing))
            anim.animateTo(1f, spring(dampingRatio = 0.46f, stiffness = Spring.StiffnessLow))
        }
    }

    GlassSurface(
        radiusDp = blurRadius,
        tint = cs.surfaceContainerHigh,
        shape = dockShape,
        borderColor = cs.outlineVariant,
        outsidePage = true,
        modifier = modifier
            .height(dockHeight)
            .shadow(8.dp, dockShape, clip = false),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            AppTab.entries.forEach { tab ->
                val active = tab == selected
                val indicator by animateColorAsState(
                    targetValue = if (active) cs.secondaryContainer else Color.Transparent,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "m3.dock.indicator",
                )
                val itemTint by animateColorAsState(
                    targetValue = if (active) cs.onSecondaryContainer else cs.onSurfaceVariant,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "m3.dock.tint",
                )
                val bounceScale = bounce.getValue(tab)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = pillInset, horizontal = 5.dp)
                        .clip(RoundedCornerShape(pillRadius))
                        .background(indicator)
                        .then(
                            if (p.journal && active) {
                                Modifier.journalCard(pillRadius, p.accent.copy(alpha = 0.40f))
                            } else {
                                Modifier
                            },
                        )
                        .clickable {
                            onSelect(tab)
                            bounceOn(tab)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 形变只作用于图标 + 文字：不会被 Dock 的圆角裁剪，
                    // 也不会影响点击热区（graphicsLayer 只改变绘制，不改布局）。
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = bounceScale.value
                                scaleY = bounceScale.value
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = itemTint,
                            modifier = Modifier.size(23.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = itemTint,
                        )
                    }
                }
            }
        }
    }
}
// ---------------------------------------------------------------- 同步行

@Composable
fun SyncLine(
    syncing: Boolean,
    message: String?,
    summary: String,
    onRelogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = LocalPaper.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .background(
                    when {
                        syncing -> p.accent
                        message != null -> p.markHot
                        else -> p.pulse
                    },
                    RoundedCornerShape(3.dp),
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = when {
                syncing -> "正在同步…"
                message != null -> message
                else -> summary
            },
            style = PaperType.sync,
            color = if (message != null) p.accent else p.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRelogin != null && message?.contains("登录") == true) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "重新登录",
                style = PaperType.sync,
                color = p.accent,
                modifier = Modifier.clickable(onClick = onRelogin),
            )
        }
    }
}
