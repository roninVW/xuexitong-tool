package com.xxt.board.ui.glass

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxt.board.data.AppSettings
import com.xxt.board.data.ScheduleEntity
import com.xxt.board.data.SubjectClassifier
import com.xxt.board.data.Task
import com.xxt.board.data.TaskType
import com.xxt.board.data.weekMatches
import com.xxt.board.domain.TaskBuckets
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.board.BoardViewModel
import com.xxt.board.ui.components.AppIcons
import com.xxt.board.ui.components.BackdropState
import com.xxt.board.ui.components.backdropSource
import com.xxt.board.ui.components.blurSupported
import com.xxt.board.ui.components.dueText
import com.xxt.board.ui.components.remainText
import com.xxt.board.ui.components.rememberBackdrop
import com.xxt.board.ui.schedule.ScheduleViewModel
import com.xxt.board.ui.theme.subjectColor

// ---------------------------------------------------------------- 顶栏

@Composable
private fun GlassTitleBar(
    backdrop: BackdropState,
    title: String,
    subtitle: String?,
    onBottom: (Int) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = LocalGlass.current
    GlassPanel(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .shadow(26.dp, RoundedCornerShape(28.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow)
            .onGloballyPositioned { onBottom((it.positionInRoot().y + it.size.height).toInt()) },
        shape = RoundedCornerShape(28.dp),
        radiusDp = 28f,
        blurDp = 24,
        refractDp = 20f,
        tint = if (t.dark) t.tint else Color.White.copy(alpha = 0.26f),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
                Spacer(Modifier.weight(1f))
                actions()
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GlassRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val t = LocalGlass.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Color.White.copy(alpha = if (t.dark) 0.08f else 0.32f) else Color.Transparent)
            .border(1.dp, t.rimDark, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) t.ink2 else t.muted,
            modifier = Modifier.size(17.dp),
        )
    }
}

// ---------------------------------------------------------------- 课表

private val DayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val RowHeight = 54.dp
private val ColWidth = 78.dp
private const val Sections = 12

@Composable
fun GlassScheduleScreen(
    vm: ScheduleViewModel,
    blurRadius: Int,
    onNavigate: (AppTab) -> Unit,
) {
    val t = LocalGlass.current
    val entries by vm.entries.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val backdrop = rememberBackdrop()

    var week by rememberSaveable { mutableIntStateOf(1) }
    var barBottom by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(context, uri)
    }

    val maxWeek = remember(entries) {
        var m = 1
        entries.forEach { e ->
            Regex("\\d+").findAll(e.weeks).forEach { g ->
                g.value.toIntOrNull()?.let { if (it > m) m = it }
            }
        }
        m
    }
    val shown = entries.filter { weekMatches(it.weeks, week) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPad = with(LocalDensity.current) { barBottom.toDp() } + 14.dp

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            GlassBackdrop()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = topPad, start = 16.dp, end = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 68.dp + 14.dp + navBottom + 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassRoundButton(AppIcons.ChevronLeft, "上一周", { week = (week - 1).coerceAtLeast(1) }, week > 1)
                    Spacer(Modifier.width(10.dp))
                    Text("第 $week 周", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
                    Spacer(Modifier.width(8.dp))
                    Text("/ $maxWeek · ${shown.size} 节", fontSize = 11.sp, color = t.muted)
                    Spacer(Modifier.weight(1f))
                    GlassRoundButton(AppIcons.ChevronRight, "下一周", { week = (week + 1).coerceAtMost(maxWeek) }, week < maxWeek)
                }
                Spacer(Modifier.height(12.dp))

                if (entries.isEmpty()) {
                    GlassEmptyBlock(
                        title = "还没有课表",
                        body = "支持 .ics 日历或 CSV/TSV。\n点右上角菜单导入，或选择文件。",
                        action = "选择文件导入",
                        onAction = {
                            picker.launch(arrayOf("text/*", "application/ics", "text/calendar", "application/octet-stream"))
                        },
                    )
                } else {
                    GlassTimetable(shown)
                }
            }
        }

        GlassTitleBar(
            backdrop = backdrop,
            title = "课表",
            subtitle = message ?: "第 $week 周 · 共 ${entries.size} 条",
            onBottom = { barBottom = it },
        ) {
            GlassRoundButton(AppIcons.MoreVert, "更多", onClick = { menuOpen = true })
        }

        GlassDock(
            selected = AppTab.SCHEDULE,
            onSelect = onNavigate,
            backdrop = backdrop,
            blurRadius = blurRadius,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (t.dark) 0.24f else 0.10f))
                    .clickable { menuOpen = false },
            )
            GlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        end = 16.dp,
                        top = with(LocalDensity.current) { (barBottom + 8).toDp() },
                    )
                    .width(190.dp)
                    .shadow(24.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow),
                shape = RoundedCornerShape(22.dp),
                radiusDp = 22f,
                blurDp = 20,
                refractDp = 16f,
                tint = if (t.dark) Color(0xCC0B101C) else Color(0xE6FFFFFF),
            ) {
                Column(Modifier.padding(6.dp)) {
                    MenuItem("导入文件") {
                        menuOpen = false
                        picker.launch(arrayOf("text/*", "application/ics", "text/calendar", "application/octet-stream"))
                    }
                    MenuItem("粘贴文本") {
                        menuOpen = false
                        pasteOpen = true
                    }
                    MenuItem("清空课表") {
                        menuOpen = false
                        vm.clear()
                    }
                }
            }
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            containerColor = if (t.dark) Color(0xFF12161F) else Color(0xFFF7F9FD),
            titleContentColor = t.ink,
            textContentColor = t.ink2,
            title = { Text("粘贴课表", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "CSV 列序：课程名, 星期, 开始节, 结束节, 周次, 地点, 教师；也可直接粘贴 .ics 内容。",
                        fontSize = 11.sp,
                        color = t.muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pasteOpen = false
                    vm.importText("paste.csv", pasted)
                    pasted = ""
                }) { Text("导入", color = t.accent) }
            },
            dismissButton = {
                TextButton(onClick = { pasteOpen = false }) { Text("取消", color = t.muted) }
            },
        )
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    val t = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, color = t.ink)
    }
}

@Composable
private fun GlassEmptyBlock(title: String, body: String, action: String, onAction: () -> Unit) {
    val t = LocalGlass.current
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Calendar, null, tint = t.muted, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 14.sp, color = t.ink)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 12.sp, color = t.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(t.accent.copy(alpha = 0.16f))
                .border(1.dp, t.rimDark, RoundedCornerShape(14.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(action, fontSize = 12.sp, color = t.accent)
        }
    }
}

@Composable
private fun GlassTimetable(entries: List<ScheduleEntity>) {
    val t = LocalGlass.current
    val hScroll = rememberScrollState()

    Column(Modifier.fillMaxWidth()) {
        Row {
            Spacer(Modifier.width(30.dp))
            Row(Modifier.horizontalScroll(hScroll)) {
                DayLabels.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = t.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(ColWidth).padding(vertical = 4.dp),
                    )
                }
            }
        }
        Row {
            Column(Modifier.width(30.dp)) {
                repeat(Sections) { index ->
                    Box(Modifier.height(RowHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 9.sp,
                            color = t.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Row(Modifier.horizontalScroll(hScroll)) {
                DayLabels.forEachIndexed { dayIndex, _ ->
                    val weekday = dayIndex + 1
                    Box(Modifier.width(ColWidth).height(RowHeight * Sections)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val dash = 3.dp.toPx()
                            val gap = 5.dp.toPx()
                            for (i in 0..Sections) {
                                val y = i * RowHeight.toPx()
                                drawLine(
                                    color = t.ink.copy(alpha = 0.10f),
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap)),
                                )
                            }
                        }
                        entries.filter { it.weekday == weekday }.forEach { entry ->
                            val span = (entry.endSection - entry.startSection + 1).coerceAtLeast(1)
                            GlassCourseBlock(
                                entry = entry,
                                modifier = Modifier
                                    .offset(y = RowHeight * (entry.startSection - 1))
                                    .height(RowHeight * span)
                                    .fillMaxWidth()
                                    .padding(1.5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCourseBlock(entry: ScheduleEntity, modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    val color = subjectColor(SubjectClassifier.classify(entry.name), t.dark)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = if (t.dark) 0.30f else 0.22f),
                        color.copy(alpha = if (t.dark) 0.18f else 0.12f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = if (t.dark) 0.10f else 0.45f), shape),
    ) {
        Column(Modifier.padding(start = 7.dp, end = 4.dp, top = 5.dp)) {
            Text(
                text = entry.name,
                fontSize = 10.sp,
                lineHeight = 12.5.sp,
                color = t.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.location.isNotBlank()) {
                Text(
                    text = entry.location,
                    fontSize = 8.5.sp,
                    color = t.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 统计

@Composable
fun GlassStatsScreen(
    vm: BoardViewModel,
    blurRadius: Int,
    onNavigate: (AppTab) -> Unit,
) {
    val t = LocalGlass.current
    val tasks by vm.tasks.collectAsState()
    val backdrop = rememberBackdrop()
    var barBottom by remember { mutableIntStateOf(0) }

    val now = remember(tasks) { System.currentTimeMillis() }
    val buckets = remember(tasks, now) { TaskBuckets.split(tasks, now) }
    val bySubject = remember(tasks) {
        tasks.groupBy { SubjectClassifier.classify(it.courseName) }.toList().sortedByDescending { it.second.size }
    }
    val works = tasks.count { it.type == TaskType.WORK }
    val exams = tasks.count { it.type == TaskType.EXAM }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            GlassBackdrop()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = with(LocalDensity.current) { barBottom.toDp() } + 14.dp,
                    bottom = 68.dp + 14.dp + navBottom + 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassTile("未完成", buckets.todo.size, t.accent, Modifier.weight(1f), backdrop)
                        GlassTile("待批阅", buckets.pending.size, t.work, Modifier.weight(1f), backdrop)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassTile("已完成", buckets.done.size, t.ink2, Modifier.weight(1f), backdrop)
                        GlassTile("已过期", buckets.expired.size, t.exam, Modifier.weight(1f), backdrop)
                    }
                }
                item { SectionLabel("类型构成") }
                item {
                    GlassGroup(backdrop) {
                        RatioRow("作业", works, tasks.size, t.work)
                        Spacer(Modifier.height(12.dp))
                        RatioRow("考试", exams, tasks.size, t.exam)
                    }
                }
                item { SectionLabel("各科目") }
                item {
                    GlassGroup(backdrop) {
                        bySubject.forEachIndexed { index, (subject, list) ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            SubjectBar(subject.label, list.size, tasks.size, subjectColor(subject, t.dark))
                        }
                    }
                }
                if (buckets.todo.isNotEmpty()) {
                    item { SectionLabel("最近截止") }
                    items(buckets.todo.take(3)) { task ->
                        GlassUpcoming(task, now, backdrop)
                    }
                }
            }
        }

        GlassTitleBar(
            backdrop = backdrop,
            title = "统计",
            subtitle = "共 ${tasks.size} 项 · ${bySubject.size} 个科目",
            onBottom = { barBottom = it },
        )

        GlassDock(
            selected = AppTab.STATS,
            onSelect = onNavigate,
            backdrop = backdrop,
            blurRadius = blurRadius,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun GlassTile(label: String, value: Int, accent: Color, modifier: Modifier, backdrop: BackdropState) {
    val t = LocalGlass.current
    GlassPanel(
        backdrop = backdrop,
        modifier = modifier.shadow(18.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow),
        shape = RoundedCornerShape(24.dp),
        radiusDp = 24f,
        blurDp = 14,
        refractDp = 14f,
        tint = if (t.dark) Color(0x590B101C) else Color.White.copy(alpha = 0.20f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(accent, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(label, fontSize = 11.sp, color = t.muted)
            }
            Spacer(Modifier.height(8.dp))
            Text("$value", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
        }
    }
}

@Composable
private fun GlassGroup(backdrop: BackdropState, content: @Composable () -> Unit) {
    val t = LocalGlass.current
    GlassPanel(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        radiusDp = 24f,
        blurDp = 14,
        refractDp = 14f,
        tint = if (t.dark) Color(0x590B101C) else Color.White.copy(alpha = 0.20f),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun RatioRow(label: String, count: Int, total: Int, color: Color) {
    val t = LocalGlass.current
    val fraction = if (total == 0) 0f else count.toFloat() / total
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = t.ink2, modifier = Modifier.width(44.dp))
        GlassTrack(fraction, color, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text("$count", fontSize = 11.sp, color = t.muted)
    }
}

@Composable
private fun SubjectBar(name: String, count: Int, total: Int, color: Color) {
    val t = LocalGlass.current
    val fraction = if (total == 0) 0f else count.toFloat() / total
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 11.sp, color = t.ink, modifier = Modifier.weight(1f))
            Text("$count 项", fontSize = 10.sp, color = t.muted)
        }
        Spacer(Modifier.height(5.dp))
        GlassTrack(fraction, color, Modifier.fillMaxWidth())
    }
}

@Composable
private fun GlassTrack(fraction: Float, color: Color, modifier: Modifier) {
    val t = LocalGlass.current
    Box(
        modifier
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = if (t.dark) 0.08f else 0.35f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))),
        )
    }
}

@Composable
private fun GlassUpcoming(task: Task, now: Long, backdrop: BackdropState) {
    val t = LocalGlass.current
    val accent = if (task.type == TaskType.EXAM) t.exam else t.work
    GlassPanel(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        radiusDp = 20f,
        blurDp = 12,
        refractDp = 12f,
        tint = if (t.dark) Color(0x4D0B101C) else Color.White.copy(alpha = 0.18f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = t.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${task.courseName} · ${dueText(task.dueAt)}",
                    fontSize = 10.sp,
                    color = t.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(remainText(task.dueAt, now), fontSize = 11.sp, color = accent)
        }
    }
}

// ---------------------------------------------------------------- 设置

@Composable
fun GlassSettingsScreen(
    settings: AppSettings,
    blurRadius: Int,
    onDarkMode: (Boolean?) -> Unit,
    onBlur: (Int) -> Unit,
    onWorkColor: (Long) -> Unit,
    onExamColor: (Long) -> Unit,
    onShowQuote: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onNavigate: (AppTab) -> Unit,
) {
    val t = LocalGlass.current
    val backdrop = rememberBackdrop()
    var barBottom by remember { mutableIntStateOf(0) }
    var confirmLogout by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            GlassBackdrop()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = with(LocalDensity.current) { barBottom.toDp() } + 14.dp,
                    bottom = 68.dp + 14.dp + navBottom + 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { SectionLabel("外观") }
                item {
                    GlassGroup(backdrop) {
                        Text("深浅色", fontSize = 13.sp, color = t.ink)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf<Boolean?>(null, false, true).forEach { v ->
                                GlassPill(
                                    text = when (v) {
                                        null -> "跟随系统"
                                        false -> "浅色"
                                        true -> "深色"
                                    },
                                    active = settings.darkMode == v,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onDarkMode(v) },
                                )
                            }
                        }
                    }
                }
                item {
                    GlassGroup(backdrop) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("玻璃厚度", fontSize = 13.sp, color = t.ink)
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (blurSupported) "${settings.blurRadius} dp" else "不支持",
                                fontSize = 12.sp,
                                color = t.accent,
                            )
                        }
                        Slider(
                            value = settings.blurRadius.toFloat(),
                            onValueChange = { onBlur(it.toInt()) },
                            valueRange = 0f..40f,
                            steps = 19,
                            enabled = blurSupported,
                            colors = SliderDefaults.colors(
                                thumbColor = t.accent,
                                activeTrackColor = t.accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent,
                            ),
                        )
                        Text(
                            "折射、模糊与边缘高光都由 AGSL 实时计算；Android 12 以下退化为模糊 + 半透明。",
                            fontSize = 10.5.sp,
                            color = t.muted,
                        )
                    }
                }
                item {
                    GlassGroup(backdrop) {
                        Text("类型色", fontSize = 13.sp, color = t.ink)
                        Spacer(Modifier.height(10.dp))
                        Text("作业", fontSize = 10.5.sp, color = t.muted)
                        Spacer(Modifier.height(6.dp))
                        SwatchRow(listOf(0xFFD97B32, 0xFFC4763F, 0xFF8A6A33, 0xFF7A5EA8, 0xFF3E7C6A), settings.workColor, onWorkColor)
                        Spacer(Modifier.height(10.dp))
                        Text("考试", fontSize = 10.5.sp, color = t.muted)
                        Spacer(Modifier.height(6.dp))
                        SwatchRow(listOf(0xFFD64545, 0xFF9E3B31, 0xFFB3261E, 0xFF9C5A74, 0xFF44474F), settings.examColor, onExamColor)
                    }
                }
                item { SectionLabel("内容") }
                item {
                    GlassGroup(backdrop) {
                        SwitchRow("顶栏一言", "来自 hitokoto，点顶栏那句话可换一句", settings.showQuote, onShowQuote)
                        Spacer(Modifier.height(12.dp))
                        SwitchRow("通知提醒", "关闭后不再后台同步", settings.notifications, onNotifications)
                    }
                }
                item { SectionLabel("账号") }
                item {
                    GlassGroup(backdrop) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("退出登录", fontSize = 13.sp, color = t.ink)
                                Text("清掉本机 Cookie，缓存保留", fontSize = 10.5.sp, color = t.muted)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(t.exam.copy(alpha = 0.16f))
                                    .clickable { confirmLogout = true }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Text("退出", fontSize = 12.sp, color = t.exam)
                            }
                        }
                    }
                }
                item { SectionLabel("关于") }
                item {
                    GlassGroup(backdrop) {
                        Text("作业板 · 玻璃 v0.4.0", fontSize = 13.sp, color = t.ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "液态玻璃的绘制思路参考 Kyant0/AndroidLiquidGlass（Apache-2.0）：" +
                                "折射 + 模糊 + 色散 + 边缘高光。本工程为独立 AGSL 实现，未包含该库代码。",
                            fontSize = 10.5.sp,
                            color = t.muted,
                        )
                    }
                }
            }
        }

        GlassTitleBar(
            backdrop = backdrop,
            title = "设置",
            subtitle = "液态玻璃 · AGSL",
            onBottom = { barBottom = it },
        )

        GlassDock(
            selected = AppTab.SETTINGS,
            onSelect = onNavigate,
            backdrop = backdrop,
            blurRadius = blurRadius,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = if (t.dark) Color(0xFF12161F) else Color(0xFFF7F9FD),
            titleContentColor = t.ink,
            textContentColor = t.ink2,
            title = { Text("退出登录？", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("会清掉本机保存的登录 Cookie，已同步的缓存会保留。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("退出", color = t.exam) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消", color = t.muted) }
            },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = t.ink)
            Text(subtitle, fontSize = 10.5.sp, color = t.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = t.accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.28f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
private fun SwatchRow(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    val t = LocalGlass.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { argb ->
            val color = Color(argb)
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (argb == selected) 2.dp else 0.5.dp,
                        color = if (argb == selected) t.ink else Color.White.copy(alpha = 0.5f),
                        shape = CircleShape,
                    )
                    .clickable { onPick(argb) },
            )
        }
    }
}
