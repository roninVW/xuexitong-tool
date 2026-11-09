package com.xxt.board.ui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.xxt.board.ui.theme.SerifSc
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxt.board.data.BoardDao
import com.xxt.board.data.Subject
import com.xxt.board.data.SubjectClassifier
import com.xxt.board.data.Task
import com.xxt.board.data.TaskEntity
import com.xxt.board.data.TaskStatus
import com.xxt.board.data.TaskType
import com.xxt.board.data.toDomain
import com.xxt.board.domain.SyncEngine
import com.xxt.board.domain.TaskBuckets
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.components.backdropSource
import com.xxt.board.ui.components.AppIcons
import com.xxt.board.ui.components.rememberFlameClock
import com.xxt.board.ui.components.EmptyState
import com.xxt.board.ui.components.GlassSurface
import com.xxt.board.ui.components.GroupHeader
import com.xxt.board.ui.components.PaperPill
import com.xxt.board.ui.components.PaperTopBar
import com.xxt.board.ui.components.SyncLine
import com.xxt.board.ui.components.TaskCard
import com.xxt.board.ui.components.statusLabel
import com.xxt.board.ui.theme.JournalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.xxt.board.ui.theme.LocalPaper
import com.xxt.board.ui.theme.PaperType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class BoardViewModel(
    dao: BoardDao,
    private val sync: SyncEngine,
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = dao.observeTasks()
        .map { list -> list.map(TaskEntity::toDomain) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _quote = MutableStateFlow("保持热爱，奔赴山海")
    val quote = _quote.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        refreshQuote()
        refresh()
    }

    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            val report = sync.sync()
            _syncing.value = false
            _message.value = when {
                report.needLogin -> "登录已失效，请重新登录"
                report.error != null -> "同步失败：${report.error}"
                else -> null
            }
        }
    }

    fun refreshQuote() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { fetchHitokoto() }.getOrNull() }
            if (!text.isNullOrBlank()) _quote.value = text
        }
    }

    private fun fetchHitokoto(): String? {
        val url = URL("https://v1.hitokoto.cn/?c=i&c=k&encode=json&max_length=16")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
        }
        return try {
            conn.inputStream.bufferedReader().readText()
                .substringAfter("\"hitokoto\":\"", "")
                .substringBefore("\"")
                .takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    vm: BoardViewModel,
    blurRadius: Int,
    flameEnabled: Boolean = false,
    showQuote: Boolean,
    onOpenWeb: (Task) -> Unit,
    onJump: (Task) -> Unit,
    onNeedLogin: () -> Unit,
    onNavigate: (AppTab) -> Unit,
) {
    val p = LocalPaper.current
    val tasks by vm.tasks.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val message by vm.message.collectAsState()
    val quote by vm.quote.collectAsState()

    var statusName by rememberSaveable { mutableStateOf(TaskStatus.TODO.name) }
    var subjectName by rememberSaveable { mutableStateOf<String?>(null) }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    var typeMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
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
    val subject = subjectName?.let { name -> Subject.entries.firstOrNull { it.name == name } }
    val type = typeName?.let { name -> TaskType.entries.firstOrNull { it.name == name } }

    val typeFiltered = tasks.filter { t -> type == null || t.type == type }
    val filtered = typeFiltered.filter { t ->
        subject == null || SubjectClassifier.classify(t.courseName) == subject
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
    val workCount = tasks.count { it.type == TaskType.WORK }
    val examCount = tasks.count { it.type == TaskType.EXAM }
    // 科目 chip 上的数量：类型筛选后、属于当前状态的数量（与列表里的数字对得上）
    val subjectCounts = remember(typeFiltered, type, status, now) {
        val all = TaskBuckets.split(typeFiltered, now)
        val byKey = buildMap {
            all.todo.forEach { put(it.key, TaskStatus.TODO) }
            all.pending.forEach { put(it.key, TaskStatus.SUBMITTED) }
            all.done.forEach { put(it.key, TaskStatus.GRADED) }
            all.expired.forEach { put(it.key, TaskStatus.EXPIRED) }
        }
        typeFiltered.groupBy { SubjectClassifier.classify(it.courseName) }
            .mapValues { (_, list) -> list.count { byKey[it.key] == status } }
    }
    val filterActive = subject != null || type != null || status != TaskStatus.TODO
    val relogin = message?.contains("登录") == true

    // 顶栏副标题 = 同步状态行。原来它是标题下面单独一行（SyncLine），
    // 让看板的顶栏比其它三页高一截；现在收进统一的副标题行，四个页面顶栏同高。
    val statusLine = when {
        syncing -> "正在同步…"
        message != null -> message.orEmpty()
        tasks.isEmpty() -> "还没有数据 · 点这里同步"
        else -> "已同步 ${subjects.size} 门课 · ${tasks.size} 项"
    }

    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentTop = with(density) { barBottom.toDp() } + 4.dp

    val listState = rememberLazyListState()
    // LazyColumn 固定预留 Dock(66) + Dock 底边距(12) + 导航栏 + 页面间距(20)。
    // 一言是否能放大只看卡片末尾之后的真实剩余空间，不再要求内容低于某个屏幕比例。
    val dockContentReserve = 66.dp + 12.dp + navBottom + 20.dp + 28.dp
    val quoteSpace by remember(density, dockContentReserve) {
        derivedStateOf {
            val info = listState.layoutInfo
            val contentEnd = info.visibleItemsInfo
                .filter { it.key != "quote" }
                .maxOfOrNull { it.offset + it.size } ?: 0
            with(density) {
                (info.viewportSize.height - contentEnd - dockContentReserve.toPx())
                    .coerceAtLeast(0f)
                    .toDp()
            }
        }
    }
    val bigQuote = showQuote && !relogin && quoteSpace >= 56.dp

    // 放大态锁定高度：只在「进入放大」那一刻快照一次，之后布局怎么变都不再重算，
    // 避免滚动/刷新时一言反复跳尺寸。退出放大时清空，下次进入重新测量。
    var lockedQuoteHeight by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }
    LaunchedEffect(bigQuote) {
        lockedQuoteHeight = if (bigQuote) quoteSpace else null
    }
    val quoteHeight = lockedQuoteHeight ?: quoteSpace


    Box(Modifier.fillMaxSize()) {
        // 下拉刷新：手势区就是任务列表，指示器避开顶栏落到内容区顶部
        val refreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { vm.refresh() },
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = syncing,
                    containerColor = p.card,
                    color = p.accent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentTop),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                .fillMaxSize()
                .backdropSource(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = contentTop,
                    bottom = dockContentReserve,
                ),
                verticalArrangement = Arrangement.spacedBy(if (p.journal) 9.dp else 7.dp),
            ) {
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = when {
                                relogin -> AppIcons.Info
                                tasks.isEmpty() -> AppIcons.Assignment
                                else -> AppIcons.Check
                            },
                            title = when {
                                relogin -> "登录已失效"
                                tasks.isEmpty() -> "还没有同步到作业"
                                else -> "这一栏是空的"
                            },
                            body = when {
                                relogin -> "学习通 Cookie 过期了，重新登录一次就能继续同步。"
                                tasks.isEmpty() -> "点一下顶部的同步行，或者下拉重新同步。"
                                else -> "换个状态或科目看看。"
                            },
                            actionLabel = when {
                                relogin -> "重新登录"
                                tasks.isEmpty() -> "立即同步"
                                else -> null
                            },
                            onAction = when {
                                relogin -> onNeedLogin
                                tasks.isEmpty() -> ({ vm.refresh() })
                                else -> null
                            },
                        )
                    }
                }

                visible.groupBy { SubjectClassifier.classify(it.courseName) }
                    .entries
                    .sortedBy { it.key.ordinal }
                    .forEach { (subj, list) ->
                        item(key = "h-${subj.name}") {
                            GroupHeader(
                                subject = subj,
                                count = list.size,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        items(list, key = { it.key }) { task ->
                            TaskCard(
                                task = task,
                                now = now,
                                modifier = Modifier.animateItem(),
                                onClick = { onOpenWeb(task) },
                                onJump = { onJump(task) },
                            )
                        }
                    }

                // 始终保留同一个 item，用可见性动画完成放大/恢复，避免增删 item 时跳变。
                // 一言不再占顶栏（顶栏高度要统一）：空间够时在这里放大，空间不够时以小字排在列表末尾。
                item(key = "quote") {
                    AnimatedVisibility(
                        visible = bigQuote,
                        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            expandVertically(tween(280, easing = FastOutSlowInEasing)) +
                            scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.97f),
                        exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                            shrinkVertically(tween(240, easing = FastOutSlowInEasing)) +
                            scaleOut(tween(240, easing = FastOutSlowInEasing), targetScale = 0.98f),
                    ) {
                        BigQuote(
                            text = quote,
                            availableHeight = quoteHeight,
                            onClick = { vm.refreshQuote() },
                        )
                    }
                    AnimatedVisibility(
                        visible = showQuote && !relogin && !bigQuote,
                        enter = fadeIn(tween(200)) + expandVertically(tween(240)),
                        exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
                    ) {
                        Text(
                            text = "「$quote」",
                            style = PaperType.quote,
                            color = p.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .clickable { vm.refreshQuote() },
                        )
                    }
                }
            }
        }


        // ---------------------------------------------------------- 顶栏
        GlassSurface(
            radiusDp = blurRadius,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(0.dp),
            hairlineColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onGloballyPositioned {
                    barBottom = (it.positionInRoot().y + it.size.height).toInt()
                },
        ) {
            // 统一顶栏：标题位置/字号由 PaperTopBar 决定；类型菜单挂在标题上，
            // 右侧只保留「状态 + 科目」，两个入口不再重复
            PaperTopBar(
                title = when (type) {
                    TaskType.EXAM -> "考试"
                    TaskType.WORK -> "作业"
                    null -> "全部"
                },
                onTitleClick = { typeMenu = true },
                titleTrailing = {
                    Icon(
                        imageVector = AppIcons.ChevronDown,
                        contentDescription = null,
                        tint = p.ink.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(15.dp)
                            .rotate(if (typeMenu) 180f else 0f),
                    )
                },
                titleOverlay = {
                    DropdownMenu(
                        expanded = typeMenu,
                        onDismissRequest = { typeMenu = false },
                    ) {
                        listOf(
                            Triple("全部", null, workCount + examCount),
                            Triple("作业", TaskType.WORK, workCount),
                            Triple("考试", TaskType.EXAM, examCount),
                        ).forEach { (label, value, count) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .alpha(if (type == value) 1f else 0f),
                                    )
                                },
                                trailingIcon = {
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    typeName = value?.name
                                    typeMenu = false
                                },
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (filterMenu || filterActive) p.accentSoft else Color.Transparent,
                            )
                            .clickable { filterMenu = true }
                            .padding(start = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = statusLabel(status),
                            style = PaperType.groupName,
                            color = if (filterActive) p.accent else p.ink2,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = AppIcons.ChevronDown,
                            contentDescription = "状态与科目筛选",
                            tint = p.icon,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(if (filterMenu) 180f else 0f),
                        )
                    }
                },
                subtitle = statusLine,
                subtitleAccent = relogin || message != null,
                onSubtitleClick = { if (relogin) onNeedLogin() else vm.refresh() },
            )
        }

    }

        // ---------------------------------------------------------- 底栏


    // 右上二级菜单：Material 3 的 ModalBottomSheet + FilterChip，
    // 但容器/文字/选中色全部沿用纸感令牌（颜色样式不变）
    if (filterMenu) {
        ModalBottomSheet(
            onDismissRequest = { filterMenu = false },
            containerColor = p.card,
            contentColor = p.ink,
            scrimColor = Color.Black.copy(alpha = if (p.dark) 0.50f else 0.32f),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            ) {
                Text("筛选", style = MaterialTheme.typography.titleLarge, color = p.ink)
                Spacer(Modifier.height(16.dp))

                Text("状态", style = PaperType.tiny, color = p.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TaskStatus.entries.forEach { item ->
                        CountChip(
                            label = statusLabel(item),
                            count = counts[item] ?: 0,
                            selected = status == item,
                        ) { statusName = item.name }
                    }
                }

                Spacer(Modifier.height(16.dp))
                JournalDivider(color = p.lineSoft)
                Spacer(Modifier.height(16.dp))

                Text("科目", style = PaperType.tiny, color = p.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CountChip(
                        label = "全部",
                        count = subjectCounts.values.sum(),
                        selected = subject == null,
                    ) { subjectName = null }
                    subjects.forEach { sub ->
                        CountChip(
                            label = sub.label,
                            count = subjectCounts[sub] ?: 0,
                            selected = subject == sub,
                        ) { subjectName = if (subject == sub) null else sub.name }
                    }
                }

                Spacer(Modifier.height(16.dp))
                JournalDivider(color = p.lineSoft)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        statusName = TaskStatus.TODO.name
                        subjectName = null
                        typeName = null
                    }) { Text("重置筛选", color = p.accent) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "当前 ${visible.size} 项",
                        style = PaperType.count,
                        color = p.muted,
                    )
                }
            }
        }
    }

}


/**
 * 筛选 chip：文字后面跟一个略小的数量。
 * 选中时数量跟文字同色（浅一档），未选中时用 muted，避免和标签抢。
 */
/** 空白处的「一言」：所有尺寸都来自实际可用高度，空间变化时平滑调整字号。 */
@Composable
private fun BigQuote(
    text: String,
    availableHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val p = LocalPaper.current
    val height = availableHeight.coerceAtLeast(0.dp)
    // 长句自动降一档字号：避免因为换行过多被 maxLines 截断（显示不全）
    val lengthScale = when {
        text.length > 64 -> 0.76f
        text.length > 42 -> 0.86f
        else -> 1f
    }
    val targetFontSize = (height.value * 0.115f * lengthScale).coerceIn(15f, 28f)
    val fontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "quote.fontSize",
    )
    val markSize by animateFloatAsState(
        targetValue = targetFontSize * 1.45f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "quote.markSize",
    )
    val maxLines = when {
        height < 132.dp -> 3
        height < 220.dp -> 4
        else -> 5
    }
    val verticalPadding = (height.value * 0.04f).coerceIn(6f, 12f).dp
    val dividerGap = (height.value * 0.04f).coerceIn(5f, 12f).dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height),
        // 内容贴顶：一言紧跟在最后一张卡片下方，空白留在它和 Dock 之间
        contentAlignment = Alignment.TopCenter,
    ) {
        val textWidthFraction = if (height >= 180.dp) 0.86f else 0.94f
        Column(
            modifier = Modifier
                .fillMaxWidth(textWidthFraction)
                // 触控范围只包住文字本身，并且是圆角，不再铺满整片空白。
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "\u201c",
                style = TextStyle(
                    fontFamily = SerifSc,
                    fontSize = markSize.sp,
                    lineHeight = markSize.sp,
                ),
                color = p.accent.copy(alpha = 0.55f),
            )
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = SerifSc,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5f).sp,
                    letterSpacing = 0.8.sp,
                ),
                color = p.ink2,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(dividerGap))
            JournalDivider(
                modifier = Modifier.width(58.dp),
                color = p.lineSoft,
            )
        }
    }
}

@Composable
private fun CountChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalPaper.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "$count",
                    style = PaperType.count,
                    color = if (selected) p.card.copy(alpha = 0.82f) else p.muted,
                )
            }
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = p.card,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        colors = chipColors(p.card, p.ink2, p.accent),
    )
}

@Composable
private fun chipColors(
    container: Color,
    label: Color,
    selected: Color,
) = FilterChipDefaults.filterChipColors(
    containerColor = container,
    labelColor = label,
    selectedContainerColor = selected,
    selectedLabelColor = container,
)
