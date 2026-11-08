package com.xxt.board.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.xxt.board.data.AppSettings
import com.xxt.board.data.Subject
import com.xxt.board.data.SubjectClassifier
import com.xxt.board.data.Task
import com.xxt.board.data.TaskStatus
import com.xxt.board.data.TaskType
import com.xxt.board.data.weekMatches
import com.xxt.board.domain.TaskBuckets
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.board.BoardViewModel
import com.xxt.board.ui.components.AppIcons
import com.xxt.board.ui.components.backdropSource
import com.xxt.board.ui.components.dueText
import com.xxt.board.ui.components.kindLabel
import com.xxt.board.ui.components.remainText
import com.xxt.board.ui.components.flyInFromLeft
import com.xxt.board.ui.components.rememberBackdrop
import com.xxt.board.ui.components.statusLabel
import com.xxt.board.ui.components.urgencyOf
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- 公共小件

@Composable
internal fun GlassPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    count: Int? = null,
    onClick: () -> Unit,
) {
    val t = LocalGlass.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (active) t.ink else Color.White.copy(alpha = if (t.dark) 0.06f else 0.22f))
            .border(1.dp, if (active) Color.Transparent else t.rimDark, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (active) (if (t.dark) Color(0xFF0B101C) else Color.White) else t.ink2,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count",
                fontSize = 11.sp,
                color = if (active) (if (t.dark) Color(0x990B101C) else Color(0xCCFFFFFF)) else t.muted,
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    Row(
        modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.ink2)
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Text(trailing, fontSize = 11.sp, color = t.muted)
        }
    }
}

/** dock：悬浮玻璃胶囊 */
@Composable
internal fun GlassDock(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    backdrop: com.xxt.board.ui.components.BackdropState,
    blurRadius: Int,
    modifier: Modifier = Modifier,
) {
    val t = LocalGlass.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    GlassPanel(
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp + navBottom)
            .height(68.dp)
            .shadow(26.dp, RoundedCornerShape(34.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow),
        shape = RoundedCornerShape(34.dp),
        radiusDp = 34f,
        blurDp = 22,
        refractDp = 18f,
        tint = if (t.dark) t.tint else Color.White.copy(alpha = 0.30f),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AppTab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(46.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (active) {
                                    if (t.dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.5f)
                                } else {
                                    Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (active) t.accent else t.ink2,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 9.5.sp,
                        color = if (active) t.accent else t.muted,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 看板

@Composable
fun GlassBoardScreen(
    vm: BoardViewModel,
    blurRadius: Int,
    showQuote: Boolean,
    onOpenWeb: (Task) -> Unit,
    onJump: (Task) -> Unit,
    onNeedLogin: () -> Unit,
    onNavigate: (AppTab) -> Unit,
) {
    val t = LocalGlass.current
    val tasks by vm.tasks.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val message by vm.message.collectAsState()
    val quote by vm.quote.collectAsState()

    var statusName by rememberSaveable { mutableStateOf(TaskStatus.TODO.name) }
    var subjectName by rememberSaveable { mutableStateOf<String?>(null) }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    var typeMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Task?>(null) }
    var barBottom by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        vm.ensureLoaded()
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val status = TaskStatus.entries.firstOrNull { it.name == statusName } ?: TaskStatus.TODO
    val subject = subjectName?.let { n -> Subject.entries.firstOrNull { it.name == n } }
    val type = typeName?.let { n -> TaskType.entries.firstOrNull { it.name == n } }
    val filtered = tasks.filter {
        (type == null || it.type == type) &&
            (subject == null || SubjectClassifier.classify(it.courseName) == subject)
    }
    val buckets = TaskBuckets.split(filtered, now)
    val visible = when (status) {
        TaskStatus.TODO -> buckets.todo
        TaskStatus.SUBMITTED -> buckets.pending
        TaskStatus.GRADED -> buckets.done
        TaskStatus.EXPIRED -> buckets.expired
    }
    val counts = mapOf(
        TaskStatus.TODO to buckets.todo.size,
        TaskStatus.SUBMITTED to buckets.pending.size,
        TaskStatus.GRADED to buckets.done.size,
        TaskStatus.EXPIRED to buckets.expired.size,
    )
    val subjects = remember(tasks) {
        tasks.map { SubjectClassifier.classify(it.courseName) }.distinct().sortedBy { it.ordinal }
    }
    val backdrop = rememberBackdrop()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val played = remember { mutableSetOf<String>() }

    Box(Modifier.fillMaxSize()) {
        // 渐变壁纸也进采样层，否则玻璃只糊到卡片、糊不到背景
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop),
        ) {
            GlassBackdrop()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = with(androidx.compose.ui.platform.LocalDensity.current) { barBottom.toDp() } + 14.dp,
                    bottom = 68.dp + 14.dp + navBottom + 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (visible.isEmpty()) {
                    item {
                        GlassEmpty(
                            title = "这一栏是空的",
                            body = if (message?.contains("登录") == true) {
                                "登录已失效，点右上角重新登录。"
                            } else {
                                "换个状态或科目，或者下拉同步。"
                            },
                        )
                    }
                }
                var order = 0
                visible.groupBy { SubjectClassifier.classify(it.courseName) }
                    .entries
                    .sortedBy { it.key.ordinal }
                    .forEach { (subj, list) ->
                        val start = order
                        item(key = "h-${subj.name}") {
                            SectionLabel(
                                subj.label,
                                "${list.size}",
                                modifier = Modifier.flyInFromLeft(
                                    order = start,
                                    itemKey = "h-${subj.name}",
                                    played = played,
                                    distance = 26.dp,
                                    durationMillis = 380,
                                ),
                            )
                        }
                        itemsIndexed(list, key = { _, task -> task.key }) { index, task ->
                            GlassTaskCard(
                                task = task,
                                now = now,
                                modifier = Modifier
                                    .animateItem()
                                    .flyInFromLeft(order = start + 1 + index, itemKey = task.key, played = played),
                                onClick = { detail = task },
                                onJump = { onJump(task) },
                            )
                        }
                        order = start + 1 + list.size
                    }
            }
        }

        // 顶栏
        GlassPanel(
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .shadow(26.dp, RoundedCornerShape(28.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow)
                .onGloballyPositioned {
                    barBottom = (it.positionInRoot().y + it.size.height).toInt()
                },
            shape = RoundedCornerShape(28.dp),
            radiusDp = 28f,
            blurDp = 24,
            refractDp = 20f,
            tint = if (t.dark) t.tint else Color.White.copy(alpha = 0.26f),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { typeMenu = true }
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (type) {
                                null -> "作业"
                                TaskType.WORK -> "作业"
                                TaskType.EXAM -> "考试"
                            },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = t.ink,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = AppIcons.ChevronDown,
                            contentDescription = null,
                            tint = t.muted,
                            modifier = Modifier.size(16.dp).rotate(if (typeMenu) 180f else 0f),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(
                        icon = AppIcons.Tune,
                        description = "筛选",
                        active = status != TaskStatus.TODO || subject != null,
                        onClick = { filterMenu = true },
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (syncing) t.accent else t.work, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            syncing -> "正在同步…"
                            message != null -> message!!
                            else -> "已同步 ${subjects.size} 门课 · ${tasks.size} 项"
                        },
                        fontSize = 11.sp,
                        color = if (message != null) t.exam else t.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { vm.refresh() },
                    )
                    if (showQuote && message == null) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "「$quote」",
                            fontSize = 10.5.sp,
                            color = t.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { vm.refreshQuote() },
                        )
                    }
                }
            }
        }

        GlassDock(
            selected = AppTab.BOARD,
            onSelect = onNavigate,
            backdrop = backdrop,
            blurRadius = blurRadius,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (typeMenu || filterMenu) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (t.dark) 0.24f else 0.10f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        typeMenu = false
                        filterMenu = false
                    },
            )
        }

        if (typeMenu) {
            GlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 16.dp,
                        top = with(androidx.compose.ui.platform.LocalDensity.current) {
                            (barBottom + 8).toDp()
                        },
                    )
                    .width(196.dp)
                    .shadow(24.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow),
                shape = RoundedCornerShape(22.dp),
                radiusDp = 22f,
                blurDp = 20,
                refractDp = 16f,
                tint = if (t.dark) Color(0xCC0B101C) else Color(0xE6FFFFFF),
            ) {
                Column(Modifier.padding(6.dp)) {
                    listOf<Pair<String, TaskType?>>(
                        "全部" to null,
                        "作业" to TaskType.WORK,
                        "考试" to TaskType.EXAM,
                    ).forEach { (label, value) ->
                        val selected = type == value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Color.White.copy(alpha = if (t.dark) 0.10f else 0.55f) else Color.Transparent)
                                .clickable {
                                    typeName = value?.name
                                    typeMenu = false
                                }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected) {
                                Icon(AppIcons.Check, null, tint = t.accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(label, fontSize = 15.sp, color = if (selected) t.accent else t.ink)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${tasks.count { value == null || it.type == value }}",
                                fontSize = 12.sp,
                                color = t.muted,
                            )
                        }
                    }
                }
            }
        }

        if (filterMenu) {
            GlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        end = 16.dp,
                        top = with(androidx.compose.ui.platform.LocalDensity.current) {
                            (barBottom + 8).toDp()
                        },
                    )
                    .width(300.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow),
                shape = RoundedCornerShape(24.dp),
                radiusDp = 24f,
                blurDp = 22,
                refractDp = 18f,
                tint = if (t.dark) Color(0xD90B101C) else Color(0xF2FFFFFF),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("状态", fontSize = 11.sp, color = t.muted)
                    Spacer(Modifier.height(8.dp))
                    TaskStatus.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { s ->
                                GlassPill(
                                    text = statusLabel(s),
                                    active = status == s,
                                    count = counts[s] ?: 0,
                                    modifier = Modifier.weight(1f),
                                    onClick = { statusName = s.name },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("科目", fontSize = 11.sp, color = t.muted)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassPill("全部", subject == null, onClick = { subjectName = null })
                        subjects.forEach { s ->
                            GlassPill(s.label, subject == s, onClick = {
                                subjectName = if (subject == s) null else s.name
                            })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "重置",
                            fontSize = 12.sp,
                            color = t.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    statusName = TaskStatus.TODO.name
                                    subjectName = null
                                    typeName = null
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text("当前 ${visible.size} 项", fontSize = 11.sp, color = t.muted)
                    }
                }
            }
        }
    }

    detail?.let { task ->
        GlassDetailSheet(
            task = task,
            now = now,
            onDismiss = { detail = null },
            onOpenWeb = {
                detail = null
                onOpenWeb(task)
            },
            onJump = {
                detail = null
                onJump(task)
            },
        )
    }
}

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalGlass.current
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) t.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) t.accent else t.ink2, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun GlassEmpty(title: String, body: String) {
    val t = LocalGlass.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Assignment, null, tint = t.muted, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 14.sp, color = t.ink)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 12.sp, color = t.muted)
    }
}

@Composable
private fun GlassTaskCard(
    task: Task,
    now: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onJump: () -> Unit,
) {
    val t = LocalGlass.current
    val isExam = task.type == TaskType.EXAM
    val accent = if (isExam) t.exam else t.work
    val level = urgencyOf(task.dueAt, now)
    val backdrop = rememberBackdrop()

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, spring(0.6f, 900f), label = "cardScale")

    GlassPanel(
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayerScale(scale)
            .shadow(18.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = t.shadow, spotColor = t.shadow)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(24.dp),
        radiusDp = 24f,
        blurDp = 14,
        refractDp = 14f,
        tint = if (t.dark) Color(0x590B101C) else Color.White.copy(alpha = 0.20f),
        highlight = if (t.dark) 0.30f else 0.55f,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (t.dark) 0.24f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isExam) AppIcons.Info else AppIcons.Assignment,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = t.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = if (t.dark) 0.22f else 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(kindLabel(task), fontSize = 10.sp, color = accent)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = task.courseName,
                        fontSize = 11.sp,
                        color = t.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            when {
                                level == 3 -> t.exam.copy(alpha = if (t.dark) 0.26f else 0.18f)
                                level == 2 -> accent.copy(alpha = if (t.dark) 0.22f else 0.14f)
                                else -> Color.White.copy(alpha = if (t.dark) 0.08f else 0.35f)
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = task.score?.takeIf { task.status == TaskStatus.GRADED && it.isNotBlank() }
                            ?.let { if (it.endsWith("分")) it else "$it 分" }
                            ?: remainText(task.dueAt, now),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            level == 3 -> t.exam
                            level == 2 -> accent
                            else -> t.ink2
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, t.rimDark, RoundedCornerShape(10.dp))
                        .clickable(onClick = onJump)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("打开", fontSize = 10.sp, color = t.ink2)
                    Spacer(Modifier.width(3.dp))
                    Icon(AppIcons.OpenInNew, null, tint = t.ink2, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

private fun Modifier.graphicsLayerScale(scale: Float) =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

// ---------------------------------------------------------------- 详情弹层

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassDetailSheet(
    task: Task,
    now: Long,
    onDismiss: () -> Unit,
    onOpenWeb: () -> Unit,
    onJump: () -> Unit,
) {
    val t = LocalGlass.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = if (t.dark) Color(0xFF12161F) else Color(0xFFF7F9FD),
        contentColor = t.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 26.dp)) {
            Text(task.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
            Spacer(Modifier.height(4.dp))
            Text("${kindLabel(task)} · ${task.courseName}", fontSize = 12.sp, color = t.muted)
            Spacer(Modifier.height(16.dp))
            GlassRow("状态", statusLabel(task.status))
            GlassRow("截止时间", dueText(task.dueAt))
            if (task.status == TaskStatus.TODO || task.status == TaskStatus.EXPIRED) {
                GlassRow("剩余", remainText(task.dueAt, now), highlight = true)
            }
            task.score?.takeIf { it.isNotBlank() }?.let { GlassRow("成绩", it) }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("在应用内打开", true, Modifier.weight(1f), onOpenWeb)
                GlassButton("学习通 App", false, Modifier.weight(1f), onJump)
            }
        }
    }
}

@Composable
private fun GlassRow(label: String, value: String, highlight: Boolean = false) {
    val t = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = t.muted)
        Text(value, fontSize = 13.sp, color = if (highlight) t.accent else t.ink)
    }
}

@Composable
private fun GlassButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalGlass.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (filled) t.accent else Color.Transparent)
            .border(1.dp, if (filled) Color.Transparent else t.rimDark, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (filled) (if (t.dark) Color(0xFF00131F) else Color.White) else t.ink2,
        )
    }
}
