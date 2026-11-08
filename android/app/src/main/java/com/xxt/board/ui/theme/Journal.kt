package com.xxt.board.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 手账风格的视觉零件（只在 [ThemeStyle.JOURNAL] 下生效）。
 *
 * 这些零件都只画东西，不持有业务状态，也不改变任何布局尺寸的上限，
 * 所以切回「纸感」时页面结构与交互完全不变。
 */

/**
 * 虚线卡片装饰：沿圆角描一圈虚线（画在内容之上，所以不会被卡片底色盖住）。
 * 半径默认与 M3 `shapes.medium` 一致，卡片自身的裁剪不受影响。
 */
@Composable
fun Modifier.journalCard(radius: Dp = 9.dp, color: Color? = null, width: Dp = 1.dp): Modifier {
    val p = LocalPaper.current
    if (!p.journal) return this
    val c = color ?: p.lineSoft
    return this.drawWithContent {
        drawContent()
        val w = width.toPx()
        val dash = 5.dp.toPx()
        val gap = 4.dp.toPx()
        drawRoundRect(
            color = c,
            topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = w,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f),
            ),
        )
    }
}

/** 手绘分隔线：一条轻轻起伏的波浪线，代替直尺画的 HorizontalDivider */
@Composable
fun JournalDivider(
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val p = LocalPaper.current
    val c = color ?: p.lineSoft
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        if (!p.journal) {
            drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
            return@Canvas
        }
        val path = Path()
        val amp = 1.5.dp.toPx()
        val seg = 16.dp.toPx()
        val mid = size.height / 2
        path.moveTo(0f, mid)
        var x = 0f
        var up = true
        while (x < size.width) {
            val next = (x + seg).coerceAtMost(size.width)
            path.quadraticTo((x + next) / 2f, mid + if (up) -amp else amp, next, mid)
            x = next
            up = !up
        }
        drawPath(path, c, style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * 纸胶带：一小段带斜纹的半透明色带，贴在标题或卡片角落。
 * [angle] 是斜贴角度，默认 −7°。
 */
@Composable
fun WashiTape(
    modifier: Modifier = Modifier,
    color: Color = LocalPaper.current.tape,
    angle: Float = -7f,
) {
    val p = LocalPaper.current
    Box(
        modifier
            .rotate(angle)
            .size(width = 30.dp, height = 11.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(color)
            .drawWithCache {
                val stripe = Color.White.copy(alpha = if (p.dark) 0.10f else 0.28f)
                val gap = 5.dp.toPx()
                onDrawBehind {
                    var x = -size.height
                    while (x < size.width + size.height) {
                        drawLine(
                            stripe,
                            Offset(x, size.height),
                            Offset(x + size.height, 0f),
                            1.2.dp.toPx(),
                        )
                        x += gap
                    }
                }
            },
    )
}

/**
 * 手写感标题：宋体 + 稍宽字距 + 一条手绘短线压在下面。
 * 只替换"看起来像标题"的那一层，字号仍跟 [style] 走。
 */
@Composable
fun JournalTitle(
    text: String,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = LocalPaper.current.ink,
    modifier: Modifier = Modifier,
) {
    val p = LocalPaper.current
    Box(
        modifier.drawWithCache {
            val c = p.accent.copy(alpha = 0.55f)
            val w = 3.dp.toPx()
            onDrawBehind {
                if (!p.journal) return@onDrawBehind
                val y = size.height - w
                val path = Path()
                path.moveTo(0f, y)
                path.quadraticTo(size.width * 0.45f, y - w * 2.1f, size.width * 0.86f, y)
                drawPath(path, c, style = Stroke(width = w, cap = StrokeCap.Round))
            }
        },
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 手账的小节标题：文字背后贴一小段胶带，像手账上贴的标签 */
@Composable
fun JournalSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPaper.current.accent,
) {
    val p = LocalPaper.current
    if (!p.journal) {
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = color, modifier = modifier)
        return
    }
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .padding(start = 0.dp, end = 14.dp)
                .rotate(-2f)
                .clip(RoundedCornerShape(1.dp))
                .background(p.tape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = p.ink2,
            modifier = Modifier.padding(start = 7.dp, end = 15.dp, top = 2.dp, bottom = 2.dp),
        )
    }
}
