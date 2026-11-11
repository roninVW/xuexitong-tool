package com.xxt.board.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.xxt.board.BuildConfig
import com.xxt.board.domain.UpdateChecker
import com.xxt.board.ui.liquid.LocalLiquidMaterial
import com.xxt.board.ui.liquid.MaterialAlertDialog
import com.xxt.board.ui.liquid.liquidLens
import com.xxt.board.ui.liquid.vendor.InteractiveHighlight
import com.xxt.board.ui.theme.LocalPaper
import java.io.File
import kotlin.math.tanh

internal sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: UpdateChecker.Release) : UpdateUiState
    data class Downloading(val progress: Int) : UpdateUiState
    data class InstallPermission(val release: UpdateChecker.Release, val apk: File) : UpdateUiState
    data object UpToDate : UpdateUiState
    data class NoApk(val release: UpdateChecker.Release) : UpdateUiState
    data class Error(
        val message: String,
        val title: String = "检查更新失败",
    ) : UpdateUiState
}

internal fun updateSubtitle(state: UpdateUiState): String = when (state) {
    UpdateUiState.Idle -> "当前版本 v${BuildConfig.VERSION_NAME} · GitHub Release"
    UpdateUiState.Checking -> "正在检查更新…"
    is UpdateUiState.Available -> "发现新版本 ${state.release.tagName}，点击查看"
    is UpdateUiState.Downloading -> "下载更新包中 ${state.progress}%"
    is UpdateUiState.InstallPermission -> "更新包已校验，等待安装权限"
    UpdateUiState.UpToDate -> "当前已是最新版本"
    is UpdateUiState.NoApk -> "最新 Release 未附带 APK"
    is UpdateUiState.Error -> state.message
}

@Composable
internal fun UpdateDialogs(
    state: UpdateUiState,
    backdrop: Backdrop?,
    onStateChange: (UpdateUiState) -> Unit,
    onDownload: (UpdateChecker.Release) -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: (UpdateChecker.Release, File) -> Unit,
) {
    val liquid = backdrop != null
    if (!liquid) {
        UpdateDialogContent(
            state,
            backdrop,
            onStateChange,
            onDownload,
            onCancelDownload,
            onRetry,
            onInstall,
        )
        return
    }

    // Material dialogs already own their transition. Liquid keeps the previous content
    // while 0→1 animates out, so dismissal is never an abrupt disappearance.
    val transition = remember { Animatable(0f) }
    var heldState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var dialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state.hasDialog) {
            heldState = state
            dialogVisible = true
        } else {
            dialogVisible = false
        }
    }
    LaunchedEffect(dialogVisible) {
        if (dialogVisible) {
            transition.animateTo(1f, spring(0.86f, 420f, 0.001f))
        } else {
            transition.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            if (!dialogVisible) heldState = UpdateUiState.Idle
        }
    }
    if (heldState.hasDialog && (dialogVisible || transition.value > 0.0001f)) {
        CompositionLocalProvider(LocalUpdateDialogProgress provides transition.value) {
            UpdateDialogContent(
                heldState,
                backdrop,
                onStateChange,
                onDownload,
                onCancelDownload,
                onRetry,
                onInstall,
            )
        }
    }
}

/** Current glass transition progress; default material never composes this branch. */
private val LocalUpdateDialogProgress = staticCompositionLocalOf { 1f }

private val UpdateUiState.hasDialog: Boolean
    get() = this !is UpdateUiState.Idle && this !is UpdateUiState.Checking

@Composable
private fun UpdateDialogContent(
    state: UpdateUiState,
    backdrop: Backdrop?,
    onStateChange: (UpdateUiState) -> Unit,
    onDownload: (UpdateChecker.Release) -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: (UpdateChecker.Release, File) -> Unit,
) {
    val context = LocalContext.current
    val liquid = backdrop != null

    fun openRelease(release: UpdateChecker.Release) {
        onStateChange(UpdateUiState.Idle)
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
        }
    }

    when (state) {
        is UpdateUiState.Available -> {
            val release = state.release
            val apkAsset = UpdateChecker.findApkAsset(release)
            UpdateAlertDialog(
                backdrop = backdrop,
                onDismissRequest = { onStateChange(UpdateUiState.Idle) },
                title = { Text("发现新版本") },
                text = {
                    Column {
                        Text(
                            "当前版本 v${BuildConfig.VERSION_NAME}　→　最新版本 ${release.tagName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        release.title.takeIf { it.isNotBlank() && it != release.tagName }?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        release.publishedText?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "发布时间：$it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (release.changelog.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "更新日志",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                release.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                        if (apkAsset == null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "这个 Release 没有附带适用于当前设备的 APK 安装包，可到 Release 页面手动下载。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    UpdateActionButton(liquid, onClick = {
                        if (apkAsset != null) {
                            onStateChange(UpdateUiState.Idle)
                            onDownload(release)
                        } else {
                            openRelease(release)
                        }
                    }) { Text(if (apkAsset != null) "立即更新" else "打开 Release 页面") }
                },
                dismissButton = {
                    UpdateActionButton(liquid, onClick = { onStateChange(UpdateUiState.Idle) }) {
                        Text("暂不更新")
                    }
                },
            )
        }

        is UpdateUiState.Downloading -> UpdateAlertDialog(
            backdrop = backdrop,
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.progress}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                UpdateActionButton(liquid, onClick = onCancelDownload) { Text("取消") }
            },
        )

        is UpdateUiState.InstallPermission -> UpdateAlertDialog(
            backdrop = backdrop,
            onDismissRequest = { onStateChange(UpdateUiState.Idle) },
            title = { Text("允许安装更新") },
            text = {
                Text("APK 已通过包名、签名和版本校验。请在系统设置中允许本应用安装未知应用，然后继续安装。")
            },
            confirmButton = {
                UpdateActionButton(liquid, onClick = { onInstall(state.release, state.apk) }) {
                    Text("继续安装")
                }
            },
            dismissButton = {
                UpdateActionButton(liquid, onClick = { onStateChange(UpdateUiState.Idle) }) {
                    Text("取消")
                }
            },
        )

        UpdateUiState.UpToDate -> UpdateAlertDialog(
            backdrop = backdrop,
            onDismissRequest = { onStateChange(UpdateUiState.Idle) },
            title = { Text("当前已是最新版本") },
            text = { Text("当前版本 v${BuildConfig.VERSION_NAME} 就是 GitHub 上的最新正式版。") },
            confirmButton = {
                UpdateActionButton(liquid, onClick = { onStateChange(UpdateUiState.Idle) }) {
                    Text("好的")
                }
            },
        )

        is UpdateUiState.Error -> UpdateAlertDialog(
            backdrop = backdrop,
            onDismissRequest = { onStateChange(UpdateUiState.Idle) },
            title = { Text(state.title) },
            text = { Text(state.message) },
            confirmButton = {
                UpdateActionButton(liquid, onClick = onRetry) { Text("重试") }
            },
            dismissButton = {
                UpdateActionButton(liquid, onClick = { onStateChange(UpdateUiState.Idle) }) {
                    Text("关闭")
                }
            },
        )

        is UpdateUiState.NoApk -> UpdateAlertDialog(
            backdrop = backdrop,
            onDismissRequest = { onStateChange(UpdateUiState.Idle) },
            title = { Text("没有可安装的 APK") },
            text = { Text("最新 Release 没有适用于当前设备的 APK 资产，可到 GitHub Release 页面查看和手动下载。") },
            confirmButton = {
                UpdateActionButton(liquid, onClick = { openRelease(state.release) }) {
                    Text("打开 Release 页面")
                }
            },
            dismissButton = {
                UpdateActionButton(liquid, onClick = { onStateChange(UpdateUiState.Idle) }) {
                    Text("暂不")
                }
            },
        )

        UpdateUiState.Checking,
        UpdateUiState.Idle,
        -> Unit
    }
}

@Composable
private fun UpdateActionButton(
    liquid: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    if (!liquid) {
        TextButton(onClick = onClick, content = content)
    } else {
        LiquidUpdateAction(onClick = onClick, content = content)
    }
}

@Composable
private fun UpdateAlertDialog(
    backdrop: Backdrop?,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    if (backdrop == null) {
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    } else {
        LiquidUpdateDialog(backdrop, onDismissRequest, title, text, confirmButton, dismissButton)
    }
}

/** Page-space overlay: its source layer excludes this surface, avoiding cross-window/self sampling. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiquidUpdateDialog(
    backdrop: Backdrop,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
) {
    val paper = LocalPaper.current
    val material = LocalLiquidMaterial.current
    val progress = LocalUpdateDialogProgress.current.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(28.dp)
    BackHandler(onBack = onDismissRequest)
    Box(
        Modifier.fillMaxSize().zIndex(20f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Color.Black.copy(
                        alpha = (if (paper.dark) 0.36f else 0.24f) * progress,
                    ),
                )
                .clickable(interactionSource = null, indication = null, onClick = onDismissRequest),
        )
        Column(
            Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
                .widthIn(min = 280.dp, max = 560.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(16.dp.toPx() * progress)
                        liquidLens(
                            material,
                            (6.dp + 18.dp * progress).toPx(),
                            48.dp.toPx() * progress,
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(
                            alpha = (if (paper.dark) 0.65f else 0.75f) * progress,
                        )
                    },
                    shadow = { Shadow(radius = 12.dp, alpha = 0.25f * progress) },
                    layerBlock = {
                        alpha = progress
                        val scale = lerp(0.94f, 1f, progress)
                        scaleX = scale
                        scaleY = scale
                        translationY = lerp(20.dp.toPx(), 0f, progress)
                    },
                    onDrawSurface = {
                        drawRect(
                            paper.card.copy(
                                alpha = material.surfaceAlpha(if (paper.dark) 0.76f else 0.68f) * progress,
                            ),
                        )
                    },
                )
                .clip(shape)
                .clickable(interactionSource = null, indication = null) {}
                .padding(24.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides paper.ink) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                Spacer(Modifier.height(16.dp))
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                Spacer(Modifier.height(24.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** Touch light and spring deformation only; the dialog owns the single glass surface. */
@Composable
private fun LiquidUpdateAction(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val light = remember(scope) { InteractiveHighlight(scope) }
    Row(
        Modifier.graphicsLayer {
            val progress = light.pressProgress
            val scale = lerp(1f, 1f + 4.dp.toPx() / size.height.coerceAtLeast(1f), progress)
            val maxOffset = size.minDimension.coerceAtLeast(1f)
            translationX = maxOffset * tanh(0.05f * light.offset.x / maxOffset)
            translationY = maxOffset * tanh(0.05f * light.offset.y / maxOffset)
            scaleX = scale
            scaleY = scale
        }
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .then(light.modifier)
            .then(light.gestureModifier)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides paper.accent) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content = { content() })
        }
    }
}
