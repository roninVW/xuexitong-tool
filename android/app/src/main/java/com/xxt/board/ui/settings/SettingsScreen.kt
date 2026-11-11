package com.xxt.board.ui.settings

import android.content.Intent
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
import com.xxt.board.ui.liquid.MaterialAlertDialog as AlertDialog
import com.xxt.board.ui.components.backdropSource
import com.xxt.board.ui.components.GaussianTopBar
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
import androidx.compose.material3.Surface
import com.xxt.board.ui.liquid.LocalLiquidBackdrop
import com.xxt.board.ui.liquid.MaterialSlider
import com.xxt.board.ui.liquid.MaterialSwitch
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.Manifest
import android.os.Build
import com.xxt.board.work.DeadlineScheduler
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
import com.xxt.board.BuildConfig
import com.xxt.board.data.AppSettings
import com.xxt.board.domain.UpdateChecker
import com.xxt.board.domain.UpdateException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onMaterialStyle: (String) -> Unit,
    onLiquidTransparency:(Int)->Unit,
    onLiquidRefraction:(Float)->Unit,
    onLiquidParametersFinished:()->Unit,
    onDeadlineReminders: (Boolean) -> Unit,
    onDeadlineLeadMinutes: (Int) -> Unit,
    onNotificationPermissionChanged: () -> Unit,
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
    var notificationGranted by remember { mutableStateOf(DeadlineScheduler.canNotify(context)) }
    var permissionForDeadline by remember { mutableStateOf(false) }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted=DeadlineScheduler.canNotify(context)
        if(granted && permissionForDeadline) onDeadlineReminders(true)
        permissionForDeadline=false
        onNotificationPermissionChanged()
    }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _, event -> if(event==Lifecycle.Event.ON_RESUME) {
            notificationGranted=DeadlineScheduler.canNotify(context)
            onNotificationPermissionChanged()
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var editLead by remember { mutableStateOf(false) }
    var leadText by remember(settings.deadlineLeadMinutes) { mutableStateOf(settings.deadlineLeadMinutes.toString()) }
    var confirmLogout by remember { mutableStateOf(false) }

    // ================================================================ 检查更新
    // 状态机：Idle → Checking → { UpToDate | Available | Error }
    //   Available →（立即更新）→ Downloading → 系统安装界面（用户确认）
    // 网络与下载全部在 Dispatchers.IO，失败只更新状态，绝不影响其它功能
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var updateJob by remember { mutableStateOf<Job?>(null) }

    fun startUpdateCheck() {
        updateJob?.cancel()
        updateJob = scope.launch {
            updateState = UpdateUiState.Checking
            try {
                val release = withContext(Dispatchers.IO) { UpdateChecker.fetchLatestRelease() }
                val newer = UpdateChecker.compareVersions(
                    BuildConfig.VERSION_NAME, release.version,
                ) > 0
                updateState = if (newer) {
                    UpdateUiState.Available(release)
                } else {
                    UpdateUiState.UpToDate
                }
            } catch (e: UpdateException) {
                updateState = UpdateUiState.Error(e.message ?: "检查失败")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                updateState = UpdateUiState.Error("检查失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun startInstall(release: UpdateChecker.Release, apk: java.io.File) {
        when (UpdateChecker.installApk(context, apk)) {
            UpdateChecker.InstallResult.LAUNCHED -> updateState = UpdateUiState.Idle
            UpdateChecker.InstallResult.PERMISSION_REQUIRED -> {
                updateState = UpdateUiState.InstallPermission(release, apk)
                if (!UpdateChecker.openUnknownAppSources(context)) {
                    updateState = UpdateUiState.Error(
                        "无法打开「安装未知应用」设置，请在系统应用设置中手动允许",
                        title = "无法安装更新",
                    )
                }
            }
            UpdateChecker.InstallResult.NO_INSTALLER -> updateState = UpdateUiState.Error(
                "系统中没有可用的软件包安装器",
                title = "无法安装更新",
            )
        }
    }

    fun startDownload(release: UpdateChecker.Release) {
        val asset = UpdateChecker.findApkAsset(release)
        if (asset == null) {
            updateState = UpdateUiState.NoApk(release)
            return
        }
        updateJob?.cancel()
        updateJob = scope.launch {
            updateState = UpdateUiState.Downloading(0)
            var failureTitle = "下载更新失败"
            try {
                val apk = UpdateChecker.downloadApk(context, asset) { pct ->
                    updateState = UpdateUiState.Downloading(pct)
                }
                failureTitle = "更新包校验失败"
                withContext(Dispatchers.IO) {
                    UpdateChecker.validateApk(context, apk, release.version)
                }
                startInstall(release, apk)
            } catch (e: UpdateException) {
                updateState = UpdateUiState.Error(e.message ?: failureTitle, title = failureTitle)
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateState = UpdateUiState.Idle
                throw e
            } catch (e: Exception) {
                updateState = UpdateUiState.Error(
                    e.message ?: "未知错误",
                    title = failureTitle,
                )
            }
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 弹窗与设置页同窗口；录制层只包含设置页，不包含弹窗自身，避免递归采样。
    val updateSource = rememberLayerBackdrop()
    val updateBackdrop = LocalLiquidBackdrop.current?.let { wallpaper ->
        rememberCombinedBackdrop(wallpaper, updateSource)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = if (updateBackdrop != null) Modifier.layerBackdrop(updateSource) else Modifier,
        containerColor = Color.Transparent,
        topBar = {
            GaussianTopBar(blurRadius) {
                // 副标题行和另外三页保持一致（顶栏同高），内容就是四段分区名
                PaperTopBar(title = "设置", subtitle = "外观 · 内容 · 账号 · 关于")
            }
        },
    ) { inner ->
        // 采样层挂在整页根容器（窗口原点）上，而不是 padding 之内
        Box(Modifier.fillMaxSize().backdropSource()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 12.dp,
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
                                ) { Text(item.label, maxLines = 1) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        JournalDivider(color = cs.outlineVariant)
                        Text("全局材质", style = MaterialTheme.typography.titleSmall, color = cs.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                        Text("统一用于 Dock、作业页菜单、所有滑动条与开关，主题和配色独立保留。",
                            color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            listOf("default" to "默认材质", "liquid" to "液态玻璃材质").forEachIndexed { i, (key, label) ->
                                SegmentedButton(
                                    selected = settings.materialStyle == key,
                                    enabled = key != "liquid" || android.os.Build.VERSION.SDK_INT >= 33,
                                    onClick = { onMaterialStyle(key) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                ) { Text(label, maxLines = 1) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (android.os.Build.VERSION.SDK_INT < 33) {
                            Text(
                                "液态玻璃需要 Android 13 或更新版本。",
                                color = cs.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
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
                        MaterialSlider(
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
                        if(settings.materialStyle=="liquid" && Build.VERSION.SDK_INT>=33) {
                            Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("玻璃透明度",style=MaterialTheme.typography.titleSmall,color=cs.primary)
                                Spacer(Modifier.weight(1f))
                                Text(settings.liquidTransparency.toString()+"%",style=MaterialTheme.typography.labelLarge,color=cs.primary)
                            }
                            MaterialSlider(settings.liquidTransparency.toFloat(),{onLiquidTransparency(it.toInt())},
                                valueRange=0f..100f,onValueChangeFinished=onLiquidParametersFinished,
                                modifier=Modifier.padding(horizontal=16.dp))
                            Text("仅控制液态玻璃表面透明程度，折射强度独立保留。",style=MaterialTheme.typography.bodySmall,
                                color=cs.onSurfaceVariant,modifier=Modifier.padding(horizontal=16.dp))
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("玻璃折射度",style=MaterialTheme.typography.titleSmall,color=cs.primary)
                                Spacer(Modifier.weight(1f))
                                Text(String.format(java.util.Locale.ROOT,"%.2f×",settings.liquidRefraction),style=MaterialTheme.typography.labelLarge,color=cs.primary)
                            }
                            MaterialSlider(settings.liquidRefraction,onLiquidRefraction,valueRange=0f..2f,
                                onValueChangeFinished=onLiquidParametersFinished,modifier=Modifier.padding(horizontal=16.dp))
                            Text("调节真实背景折射；0× 关闭折射，1× 为原始强度。",style=MaterialTheme.typography.bodySmall,
                                color=cs.onSurfaceVariant,modifier=Modifier.padding(horizontal=16.dp))
                        } else {
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
                        MaterialSlider(
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
                        }
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
                            subtitle = "后台同步与同步结果通知；作业截止提醒单独设置",
                            checked = settings.notifications,
                            onCheckedChange = { enabled ->
                                onNotifications(enabled)
                                if(enabled && Build.VERSION.SDK_INT>=33 && !notificationGranted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        )
                    }
                }

                item { SectionLabel("作业截止提醒") }
                item {
                    M3Card {
                        SwitchItem(AppIcons.Notifications,"作业截止提醒","仅提醒未完成且未截止的作业",settings.deadlineReminders) { enabled ->
                            if(enabled && Build.VERSION.SDK_INT>=33 && !notificationGranted) {
                                permissionForDeadline=true
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else onDeadlineReminders(enabled)
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("提前提醒",style=MaterialTheme.typography.titleSmall)
                                Text("截止前 ${settings.deadlineLeadMinutes} 分钟",style=MaterialTheme.typography.bodySmall,color=cs.onSurfaceVariant)
                            }
                            TextButton(onClick={leadText=settings.deadlineLeadMinutes.toString();editLead=true}) {Text("修改时间")}
                        }
                        Text("退出应用后仍由系统调度；省电或强制停止可能延迟提醒。已完成和已过期作业不会提醒。",
                            modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall,color=cs.onSurfaceVariant)
                        if(!notificationGranted) {
                            Text("系统通知权限尚未开启。",modifier=Modifier.padding(horizontal=16.dp),color=cs.error,style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))},modifier=Modifier.padding(horizontal=8.dp)) {Text("打开系统通知设置")}
                        }
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
                                "作业板 v${BuildConfig.VERSION_NAME}",
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
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                        // 检查更新：GitHub Releases（最新正式版）为更新来源
                        ListItem(
                            headlineContent = { Text("检查更新") },
                            supportingContent = {
                                Text(updateSubtitle(updateState))
                            },
                            trailingContent = {
                                FilledTonalButton(
                                    onClick = {
                                        if (updateState is UpdateUiState.Idle ||
                                            updateState is UpdateUiState.Error
                                        ) startUpdateCheck()
                                    },
                                    enabled = updateState is UpdateUiState.Idle ||
                                        updateState is UpdateUiState.Error,
                                ) {
                                    Text(if (updateState is UpdateUiState.Checking) "…" else "检查")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

}
    }

    if(editLead) {
        val minutes=leadText.toIntOrNull()
        AlertDialog(onDismissRequest={editLead=false},title={Text("提前多久提醒")},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("输入 1 至 10080 分钟（最多 7 天）。修改后自动更新所有未完成作业的提醒。")
                OutlinedTextField(value=leadText,onValueChange={leadText=it.filter(Char::isDigit).take(5)},
                    singleLine=true,label={Text("提前分钟数")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                    isError=minutes==null || minutes !in 1..10080)
            }},confirmButton={TextButton(onClick={minutes?.let(onDeadlineLeadMinutes);editLead=false},enabled=minutes!=null && minutes in 1..10080){Text("保存")}},
            dismissButton={TextButton(onClick={editLead=false}){Text("取消")}})
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

    UpdateDialogs(
        state = updateState,
        backdrop = updateBackdrop,
        onStateChange = { updateState = it },
        onDownload = ::startDownload,
        onCancelDownload = {
            updateJob?.cancel()
            updateState = UpdateUiState.Idle
        },
        onRetry = ::startUpdateCheck,
        onInstall = ::startInstall,
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
            MaterialSwitch(checked = checked, onCheckedChange = onCheckedChange)
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
