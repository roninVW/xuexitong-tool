package com.xxt.board.ui.schedule

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.xxt.board.ui.components.GlassSurface
import com.xxt.board.ui.components.backdropSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxt.board.data.ScheduleDao
import com.xxt.board.data.ScheduleEntity
import com.xxt.board.data.SubjectClassifier
import com.xxt.board.data.parseScheduleFile
import com.xxt.board.data.weekMatches
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.components.AppIcons
import com.xxt.board.ui.components.EmptyState
import com.xxt.board.ui.components.PaperTopBar
import com.xxt.board.ui.theme.JournalTitle
import com.xxt.board.ui.theme.LocalAccent
import com.xxt.board.ui.theme.journalCard
import com.xxt.board.ui.theme.LocalPaper
import com.xxt.board.ui.theme.PaperType
import com.xxt.board.ui.wallpaper.pageBackground
import com.xxt.board.ui.theme.subjectColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleViewModel(private val dao: ScheduleDao) : ViewModel() {

    val entries: StateFlow<List<ScheduleEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun importText(fileName: String, text: String) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.Default) { parseScheduleFile(fileName, text) }
            if (list.isEmpty()) {
                _message.value = "没解析出课程，检查一下表头或 ics 内容"
                return@launch
            }
            dao.clear()
            dao.upsert(list)
            _message.value = "已导入 ${list.size} 条课程"
        }
    }

    fun importUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    val name = queryName(context, uri) ?: "timetable.csv"
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        .orEmpty()
                    name to text
                }.getOrNull()
            }
            if (payload == null || payload.second.isBlank()) {
                _message.value = "读取文件失败"
                return@launch
            }
            importText(payload.first, payload.second)
        }
    }

    fun clear() {
        viewModelScope.launch {
            dao.clear()
            _message.value = "课表已清空"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun queryName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()
}

private val DayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val RowHeight = 54.dp
private val ColWidth = 78.dp
private const val Sections = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    vm: ScheduleViewModel,
    blurRadius: Int,
    onNavigate: (AppTab) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val entries by vm.entries.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current

    var week by rememberSaveable { mutableIntStateOf(1) }
    var menuOpen by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(context, uri)
    }

    val maxWeek = remember(entries) {
        var m = 1
        entries.forEach { e ->
            Regex("\\d+").findAll(e.weeks).forEach { g -> g.value.toIntOrNull()?.let { if (it > m) m = it } }
        }
        m
    }
    val shown = entries.filter { weekMatches(it.weeks, week) }
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
                PaperTopBar(
                    title = "课表",
                    subtitle = message ?: "第 $week 周 · ${shown.size} 节 · 共 ${entries.size} 条",
                    subtitleAccent = message != null,
                    onSubtitleClick = { vm.consumeMessage() },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    imageVector = AppIcons.MoreVert,
                                    contentDescription = "更多",
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导入文件") },
                                    leadingIcon = { Icon(AppIcons.Add, null, Modifier.size(20.dp)) },
                                    onClick = {
                                        menuOpen = false
                                        picker.launch(
                                            arrayOf(
                                                "text/*",
                                                "application/ics",
                                                "text/calendar",
                                                "application/octet-stream",
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("粘贴文本") },
                                    leadingIcon = { Icon(AppIcons.Info, null, Modifier.size(20.dp)) },
                                    onClick = {
                                        menuOpen = false
                                        pasteOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("清空课表") },
                                    leadingIcon = { Icon(AppIcons.Delete, null, Modifier.size(20.dp)) },
                                    onClick = {
                                        menuOpen = false
                                        vm.clear()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { inner ->
        // 采样层挂在整页根容器（窗口原点）上，而不是 padding 之内
        Box(Modifier.fillMaxSize().backdropSource()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                if (entries.isEmpty()) {
                    EmptyState(
                        icon = AppIcons.Calendar,
                        title = "还没有课表",
                        body = "支持导入 .ics 日历（教务系统导出）或 CSV/TSV 表格：\n" +
                            "课程名, 星期, 开始节, 结束节, 周次, 地点, 教师",
                        actionLabel = "选择文件导入",
                        onAction = {
                            picker.launch(
                                arrayOf("text/*", "application/ics", "text/calendar", "application/octet-stream"),
                            )
                        },
                        modifier = Modifier.padding(top = 24.dp),
                    )
                } else {
                    WeekSwitcher(
                        week = week,
                        maxWeek = maxWeek,
                        count = shown.size,
                        onChange = { week = it.coerceIn(1, maxWeek) },
                    )
                    Timetable(entries = shown, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(80.dp + 12.dp + navBottom))
            }

}
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text("粘贴课表") },
            text = {
                Column {
                    Text(
                        "支持 CSV/TSV 或 .ics 内容。CSV 列顺序：课程名, 星期, 开始节, 结束节, 周次, 地点, 教师",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pasteOpen = false
                        vm.importText("paste.csv", pasted)
                        pasted = ""
                    },
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { pasteOpen = false }) { Text("取消") }
            },
        )
    }

}

@Composable
private fun WeekSwitcher(week: Int, maxWeek: Int, count: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(week - 1) }, enabled = week > 1) {
            Icon(AppIcons.ChevronLeft, contentDescription = "上一周")
        }
        Text(
            text = "第 $week 周",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Text(
            text = " / $maxWeek  ·  $count 节",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onChange(week + 1) }, enabled = week < maxWeek) {
            Icon(AppIcons.ChevronRight, contentDescription = "下一周")
        }
    }
}

@Composable
private fun Timetable(entries: List<ScheduleEntity>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp)) {
            Spacer(Modifier.width(30.dp))
            Row(Modifier.horizontalScroll(hScroll)) {
                DayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier
                            .width(ColWidth)
                            .padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(vScroll)
                .padding(start = 16.dp),
        ) {
            Column(Modifier.width(30.dp)) {
                repeat(Sections) { index ->
                    Box(
                        Modifier
                            .height(RowHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Row(Modifier.horizontalScroll(hScroll)) {
                DayLabels.forEachIndexed { dayIndex, _ ->
                    val weekday = dayIndex + 1
                    Box(
                        Modifier
                            .width(ColWidth)
                            .height(RowHeight * Sections),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val dash = 3.dp.toPx()
                            val gap = 4.dp.toPx()
                            val stroke = 1.dp.toPx()
                            for (i in 0..Sections) {
                                val y = i * RowHeight.toPx()
                                drawLine(
                                    color = cs.outlineVariant,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = stroke,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap)),
                                )
                            }
                            drawLine(
                                color = cs.outlineVariant,
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                strokeWidth = stroke,
                            )
                        }

                        entries.filter { it.weekday == weekday }.forEach { entry ->
                            val span = (entry.endSection - entry.startSection + 1).coerceAtLeast(1)
                            CourseBlock(
                                entry = entry,
                                modifier = Modifier
                                    .offset(y = RowHeight * (entry.startSection - 1))
                                    .height(RowHeight * span)
                                    .fillMaxWidth()
                                    .padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseBlock(entry: ScheduleEntity, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val color = subjectColor(SubjectClassifier.classify(entry.name), LocalAccent.current.base, false)
    val p = LocalPaper.current
    Card(
        modifier = modifier.journalCard(5.dp, color.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.16f),
            contentColor = cs.onSurface,
        ),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(color),
            )
            Column(Modifier.padding(start = 7.dp, end = 4.dp, top = 5.dp, bottom = 4.dp)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.location.isNotBlank()) {
                    Text(
                        text = entry.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
