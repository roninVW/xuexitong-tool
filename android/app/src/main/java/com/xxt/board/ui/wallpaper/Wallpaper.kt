package com.xxt.board.ui.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** 壁纸：本地文件 + 是否视频。path 为空 = 纯纸色背景 */
data class WallpaperSpec(
    val path: String? = null,
    val video: Boolean = false,
) {
    val active: Boolean get() = !path.isNullOrBlank()
}

/**
 * 给卡片用的「背景快照」。
 *
 * 卡片要对壁纸做磨砂，但每张卡片都挂一个模糊 View 的话，每帧就要重绘 N 次
 * 整个内容层（很贵）。所以这里只在**壁纸层**生成一张
 * 很低分辨率的位图（宽 64～72px），卡片把它按自己在屏幕里的位置贴上去再压
 * 一层半透明纸色——视觉效果就是磨砂玻璃，但每张卡片只是画一张小图。
 */
class WallpaperBackdrop {
    var image by mutableStateOf<ImageBitmap?>(null)
    var screen by mutableStateOf(IntSize.Zero)

    /** 是否正在显示壁纸（页面背景要让位给它） */
    var active by mutableStateOf(false)

    /** 卡片是否做磨砂 */
    var cardBlur by mutableStateOf(true)
}

val LocalWallpaper = staticCompositionLocalOf { WallpaperBackdrop() }

/**
 * 铺满窗口的壁纸层。图片 / GIF 用 ImageView（GIF 在 API 28+ 由 AnimatedImageDrawable 自动播放），
 * 视频用 TextureView + MediaPlayer（静音循环）。
 */
@Composable
fun AppWallpaper(spec: WallpaperSpec, backdrop: WallpaperBackdrop, modifier: Modifier = Modifier) {
    val path = spec.path
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scrim = androidx.compose.material3.MaterialTheme.colorScheme.surface

    androidx.compose.runtime.SideEffect { backdrop.active = spec.active && path != null }

    if (!spec.active || path == null) {
        LaunchedEffect(Unit) { backdrop.image = null }
        return
    }

    Box(modifier.fillMaxSize().onSizeChanged { backdrop.screen = it }) {
        if (spec.video) {
            VideoWallpaper(path = path, onFrame = { backdrop.image = it })
        } else {
            ImageWallpaper(path = path, onImage = { backdrop.image = it })
        }
        // 压一层 M3 surface：壁纸再花，正文和分组标题也要读得清
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim.copy(alpha = if (dark) 0.46f else 0.34f)),
        )
    }
}

/**
 * 页面背景：有壁纸时透明（让壁纸透上来），没有壁纸时铺 M3 的 `surface`。
 * 四个一级页面与导航外壳都用它，保证全栈 M3 的底色只有一个来源。
 */
@Composable
fun Modifier.pageBackground(): Modifier {
    val w = LocalWallpaper.current
    return if (w.active) this else this.background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
}

@Composable
private fun ImageWallpaper(path: String, onImage: (ImageBitmap?) -> Unit) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    val uri = remember(path) { Uri.fromFile(file) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
            }
        },
        update = { view ->
            if (view.tag != path) {
                view.tag = path
                view.setImageURI(uri)
            }
        },
    )

    // 低分辨率快照：卡片磨砂用，GIF 取第一帧
    LaunchedEffect(path) {
        onImage(decodeSmall(context, file))
    }
}

@Composable
private fun VideoWallpaper(path: String, onFrame: (ImageBitmap?) -> Unit) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var texture by remember { mutableStateOf<TextureView?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        runCatching {
                            player?.release()
                            player = MediaPlayer().apply {
                                setDataSource(path)
                                setSurface(Surface(surfaceTexture))
                                isLooping = true
                                setVolume(0f, 0f)
                                prepare()
                                start()
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(
                        surfaceTexture: android.graphics.SurfaceTexture,
                    ): Boolean {
                        player?.release()
                        player = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: android.graphics.SurfaceTexture) = Unit
                }
                texture = this
            }
        },
    )

    DisposableEffect(path) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    // 每 700ms 抓一帧做卡片磨砂底图（TextureView.getBitmap 是同步读回，别太频繁）
    LaunchedEffect(path, texture) {
        val view = texture ?: return@LaunchedEffect
        while (true) {
            delay(700)
            val bitmap = runCatching { view.getBitmap(72, 140) }.getOrNull() ?: continue
            onFrame(bitmap.asImageBitmap())
        }
    }
}

/** 解一张很小的位图（宽度 ≤ 72px），放大后天然就是模糊的 */
private suspend fun decodeSmall(context: Context, file: File): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (!file.exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / sample > 720) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return@runCatching null

        val targetW = 72
        val targetH = (decoded.height * (targetW.toFloat() / decoded.width)).toInt().coerceAtLeast(8)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled != decoded) decoded.recycle()
        scaled.asImageBitmap()
    }.getOrNull()
}

/**
 * 把选中的图片/视频拷到私有目录。
 *
 * Photo Picker 给的 URI 只在当前进程有效，直接存 URI 重启就失效了，所以一律复制到
 * `filesDir/wallpaper/`，每次覆盖（同时清掉旧文件，避免堆积）。
 */
suspend fun importWallpaper(context: Context, uri: Uri, video: Boolean): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, if (video) "wallpaper.mp4" else "wallpaper.img")
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (out.length() <= 0L) null else out.absolutePath
        }.getOrNull()
    }
