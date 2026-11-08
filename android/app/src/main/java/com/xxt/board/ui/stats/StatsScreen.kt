package com.xxt.board.ui.stats

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xxt.board.ui.components.GlassSurface
import com.xxt.board.ui.components.backdropSource
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xxt.board.data.SubjectClassifier
import com.xxt.board.data.Task
import com.xxt.board.data.TaskType
import com.xxt.board.domain.TaskBuckets
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.board.BoardViewModel
import com.xxt.board.ui.components.AppIcons
import com.xxt.board.ui.components.EmptyState
import com.xxt.board.ui.components.PaperTopBar
import com.xxt.board.ui.components.dueText
import com.xxt.board.ui.components.remainText
import com.xxt.board.ui.theme.JournalDivider
import com.xxt.board.ui.theme.JournalSectionLabel
import com.xxt.board.ui.theme.JournalTitle
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.xxt.board.ui.theme.LocalAccent
import com.xxt.board.ui.theme.LocalPaper
import com.xxt.board.ui.theme.journalCard
import com.xxt.board.ui.theme.subjectColor

/** 统计页：全栈 M3（TopAppBar / Card / LinearProgressIndicator / ListItem） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    vm: BoardViewModel,
    blurRadius: Int,
    onNavigate: (AppTab) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tasks by vm.tasks.collectAsState()
    val syncing by vm.syncing.collectAsState()

    val now = remember(tasks) { System.currentTimeMillis() }
    val buckets = remember(tasks, now) { TaskBuckets.split(tasks, now) }
    val bySubject = remember(tasks) {
        tasks.groupBy { SubjectClassifier.classify(it.courseName) }
            .toList()
            .sortedByDescending { it.second.size }
    }
    val works = tasks.count { it.type == TaskType.WORK }
    val exams = tasks.count { it.type == TaskType.EXAM }
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
                    title = "统计",
                    subtitle = "共 ${tasks.size} 项 · ${bySubject.size} 个科目",
                )
            }
        },
    ) { inner ->
        // 采样层挂在整页根容器（窗口原点）上，而不是 padding 之内
        Box(Modifier.fillMaxSize().backdropSource()) {
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
                        containerColor = cs.surfaceContainerLow,
                        color = cs.primary,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = inner.calculateTopPadding()),
                    )
                },
            ) {
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
                if (tasks.isEmpty()) {
                    item {
                        EmptyState(
                            icon = AppIcons.BarChart,
                            title = "还没有数据",
                            body = "同步一次之后，这里会按状态和科目统计。",
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile("未完成", buckets.todo.size, cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f))
                            StatTile("待批阅", buckets.pending.size, cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile("已完成", buckets.done.size, cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.weight(1f))
                            StatTile("已过期", buckets.expired.size, cs.errorContainer, cs.onErrorContainer, Modifier.weight(1f))
                        }
                    }

                    item { SectionLabel("类型构成") }
                    item {
                        M3Card {
                            Column(Modifier.padding(16.dp)) {
                                RatioRow("作业", works, tasks.size, cs.primary)
                                Spacer(Modifier.height(16.dp))
                                RatioRow("考试", exams, tasks.size, cs.error)
                            }
                        }
                    }

                    item { SectionLabel("各科目") }
                    item {
                        M3Card {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                bySubject.forEachIndexed { index, (subject, list) ->
                                    if (index > 0) {
                                        JournalDivider(color = cs.outlineVariant)
                                    }
                                    SubjectRow(
                                        name = subject.label,
                                        count = list.size,
                                        total = tasks.size,
                                        color = subjectColor(subject, LocalAccent.current.base, false),
                                    )
                                }
                            }
                        }
                    }

                    if (buckets.todo.isNotEmpty()) {
                        item { SectionLabel("最近截止") }
                        items(buckets.todo.take(3).size) { index ->
                            UpcomingRow(buckets.todo[index], now)
                        }
                    }
                }
            }
            }


}
    }

}

@Composable
private fun StatTile(
    label: String,
    value: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    val p = LocalPaper.current
    Card(
        modifier = modifier.journalCard(9.dp, p.lineSoft),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            Text("$value", style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun M3Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = LocalPaper.current
    Card(
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
private fun RatioRow(label: String, count: Int, total: Int, color: Color) {
    val cs = MaterialTheme.colorScheme
    val fraction = if (total == 0) 0f else count.toFloat() / total
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = color,
            trackColor = cs.surfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
    }
}

@Composable
private fun SubjectRow(name: String, count: Int, total: Int, color: Color) {
    val cs = MaterialTheme.colorScheme
    val fraction = if (total == 0) 0f else count.toFloat() / total
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text("$count 项", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = cs.surfaceVariant,
        )
    }
}

@Composable
private fun UpcomingRow(task: Task, now: Long) {
    val cs = MaterialTheme.colorScheme
    val p = LocalPaper.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .journalCard(9.dp, p.lineSoft),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    "${task.courseName} · ${dueText(task.dueAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Text(
                    remainText(task.dueAt, now),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    JournalSectionLabel(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}
