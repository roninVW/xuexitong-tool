package com.xxt.board.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxt.board.R
import com.xxt.board.data.Subject

/**
 * 纸感令牌（按设计规格）。
 *
 * 浅纸 #F6F0E2 / 深纸 #151517；卡片 #FFFDF7 / #1E1E21；墨色 #2B2419 / #EDEDEF。
 *
 * **默认唯一基色 #C4763F**：考试色、荧光色、状态色、科目点全部由这一个基色
 * 经 HSV 派生，禁止为每类单独配色。基色与模糊强度都在设置页调整。
 *
 * Material 3 只承担控件与交互（涟漪、弹层、Slider/Switch 等），
 * 颜色值一律由 [paperTokens] 从纸感令牌 + 基色生成，不做动态取色。
 */

val DefaultBaseColor = Color(0xFFC4763F)

/**
 * 界面风格（可切换）。
 *
 * - [PAPER]   原「纸感」：平纸面 + 实线描边 + 直角克制。
 * - [JOURNAL] 手账：米黄点阵纸底、虚线卡片、纸胶带、手绘分隔、留白更大；
 *              只改视觉，不改任何页面结构、文案与交互。
 */
enum class ThemeStyle(val key: String, val label: String) {
    PAPER("paper", "纸感"),
    JOURNAL("journal", "手账");

    companion object {
        fun of(key: String?): ThemeStyle = entries.firstOrNull { it.key == key } ?: PAPER
    }
}

/** HSV 派生：只改色相 / 饱和度倍数 / 明度倍数，绝不引入新的独立色值 */
private fun shift(base: Color, hueDelta: Float, satMul: Float = 1f, valMul: Float = 1f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    hsv[0] = (hsv[0] + hueDelta + 360f) % 360f
    hsv[1] = (hsv[1] * satMul).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * valMul).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 由作业色（基色）派生出来的默认考试色：向红偏 18°，饱和略升、明度压低 */
fun derivedExamColor(base: Color): Color = shift(base, -18f, 1.12f, 0.86f)

@Immutable
data class AccentColors(
    val base: Color = DefaultBaseColor,
    /** 用户单独挑的考试色；null = 按 [derivedExamColor] 从作业色派生 */
    val examOverride: Color? = null,
) {
    /** 作业色 = 基色本身 */
    val work: Color get() = base

    /** 考试色：优先用用户挑的，否则由作业色派生 */
    val exam: Color get() = examOverride ?: derivedExamColor(base)
}

val LocalAccent = staticCompositionLocalOf { AccentColors() }

@Immutable
data class PaperTokens(
    val dark: Boolean,
    val base: Color,
    val paper: Color,
    val card: Color,
    val line: Color,
    val lineSoft: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    /** 荧光笔：常态 / 临近 / 紧急，全部由基色 HSV 派生 */
    val mark: Color,
    val markSoon: Color,
    val markHot: Color,
    val badgeInk: Color,
    val badgeInkSoon: Color,
    val badgeInkHot: Color,
    /** 状态色（同样派生） */
    val statusTodo: Color,
    val statusPending: Color,
    val statusDone: Color,
    val statusExpired: Color,
    val glass: Color,
    val glassAlpha: Float,
    val glassLine: Color,
    val icon: Color,
    /** 同步呼吸点（派生） */
    val pulse: Color,
    /** 当前风格 */
    val style: ThemeStyle = ThemeStyle.PAPER,
    /** 手账：点阵纸底的点色 */
    val grid: Color = Color(0x00000000),
    /** 手账：纸胶带颜色 */
    val tape: Color = Color(0x00000000),
) {
    /** 手账风格开关：组件里直接 `p.journal` */
    val journal: Boolean get() = style == ThemeStyle.JOURNAL
}

/**
 * 从「深色? + 唯一基色」生成整套令牌。
 * 除纸底 / 卡面 / 描边这些中性色外，所有彩色都走 [shift]。
 */
fun paperTokens(
    dark: Boolean,
    base: Color = DefaultBaseColor,
    style: ThemeStyle = ThemeStyle.PAPER,
    exam: Color = derivedExamColor(base),
): PaperTokens {
    val journal = style == ThemeStyle.JOURNAL

    // 荧光三档：同一色相族，靠明度/饱和度倍数拉开
    val mark = shift(base, 20f, 0.52f, 1.55f)
    val markSoon = shift(base, 6f, 0.78f, 1.30f)
    // 「紧急」荧光仍从作业色派生：否则把考试色改成绿色后，作业的紧急标记也会变绿
    val markHot = shift(base, -6f, 0.72f, 1.35f)

    fun inkOf(c: Color) = shift(c, 0f, 1.5f, if (dark) 1.55f else 0.34f)

    // 手账把纸面往暖里再推一点（米黄），并给点阵/胶带两个专属色；
    // 其余彩色仍全部来自同一个基色。
    val paper = when {
        dark && journal -> Color(0xFF16150F)
        dark -> Color(0xFF151517)
        journal -> Color(0xFFFAF3E1)
        else -> Color(0xFFF6F0E2)
    }
    val card = when {
        dark && journal -> Color(0xFF211E17)
        dark -> Color(0xFF1E1E21)
        journal -> Color(0xFFFFFDF5)
        else -> Color(0xFFFFFDF7)
    }
    val line = when {
        dark && journal -> Color(0xFF332E22)
        dark -> Color(0xFF2B2B2F)
        journal -> Color(0xFFE7D9B8)
        else -> Color(0xFFE6DAC0)
    }
    val lineSoft = when {
        dark && journal -> Color(0xFF413A2A)
        dark -> Color(0xFF35353A)
        journal -> Color(0xFFD5C4A0)
        else -> Color(0xFFD9CCAF)
    }
    val ink = when {
        dark && journal -> Color(0xFFEFE7D4)
        dark -> Color(0xFFEDEDEF)
        journal -> Color(0xFF32281A)
        else -> Color(0xFF2B2419)
    }
    val ink2 = when {
        dark && journal -> Color(0xFFBFB49C)
        dark -> Color(0xFFB5B5B9)
        journal -> Color(0xFF6B5B3E)
        else -> Color(0xFF6F6146)
    }
    val muted = when {
        dark && journal -> Color(0xFF8E846C)
        dark -> Color(0xFF86868C)
        journal -> Color(0xFF9A8760)
        else -> Color(0xFF9C8B67)
    }

    return PaperTokens(
        dark = dark,
        base = base,
        paper = paper,
        card = card,
        line = line,
        lineSoft = lineSoft,
        ink = ink,
        ink2 = ink2,
        muted = muted,
        accent = if (dark) shift(base, 0f, 0.92f, 1.30f) else base,
        accentSoft = base.copy(alpha = if (dark) 0.16f else 0.12f),
        mark = if (dark) mark.copy(alpha = 0.26f) else mark,
        markSoon = if (dark) markSoon.copy(alpha = 0.26f) else markSoon,
        markHot = if (dark) markHot.copy(alpha = 0.28f) else markHot,
        badgeInk = inkOf(mark),
        badgeInkSoon = inkOf(markSoon),
        badgeInkHot = inkOf(markHot),
        statusTodo = if (dark) shift(base, 0f, 0.9f, 1.25f) else base,
        statusPending = shift(base, 42f, if (dark) 0.8f else 0.85f, if (dark) 1.2f else 0.92f),
        statusDone = shift(base, 138f, if (dark) 0.65f else 0.70f, if (dark) 1.15f else 0.78f),
        statusExpired = if (dark) shift(exam, 0f, 1.0f, 1.2f) else exam,
        glass = if (dark) Color(0xFF1E1E22) else card,
        glassAlpha = if (dark) 0.60f else 0.55f,
        glassLine = if (dark) Color(0x14FFFFFF) else Color(0xBFD2C4A6),
        icon = if (dark) Color(0xFFB5B5B9) else ink2,
        pulse = shift(base, 138f, if (dark) 0.60f else 0.62f, if (dark) 1.10f else 0.82f),
        style = style,
        grid = Color(0x00000000),
        tape = base.copy(alpha = if (dark) 0.34f else 0.40f),
    )
}

val LocalPaper = staticCompositionLocalOf { paperTokens(false) }

/**
 * 科目点：按科目序号在色环上等分派生，不写死 13 个颜色。
 * 全部共享同一个基色的饱和度 / 明度基调。
 */
fun subjectColor(subject: Subject, base: Color, dark: Boolean): Color {
    val step = 360f / Subject.entries.size
    val hue = subject.ordinal * step
    return shift(base, hue, if (dark) 0.72f else 0.92f, if (dark) 1.25f else 0.86f)
}

// ---------------------------------------------------------------- 字体

/**
 * 标题用思源宋体（SIL OFL 1.1，随包分发）。
 * 11MB 的 OTF 用 Async 加载：首帧先用系统字体渲染，字体到位后自动刷新，
 * 不会让冷启动卡在解析字体上。
 */
private fun serifFont(weight: FontWeight) = Font(
    resId = R.font.noto_serif_sc_semibold,
    weight = weight,
    loadingStrategy = FontLoadingStrategy.Async,
)

val SerifSc = FontFamily(
    serifFont(FontWeight.Normal),
    serifFont(FontWeight.Medium),
    serifFont(FontWeight.SemiBold),
    serifFont(FontWeight.Bold),
)

private val tightLine = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** 原型字号写死，不走 M3 字阶 */
object PaperType {
    /**
     * 全应用唯一的「页面标题」字号：所有一级页顶栏左侧标题都用它，
     * 位置也统一由 [com.xxt.board.ui.components.PaperTopBar] 决定（44dp 行高 / 16dp 左内边距）。
     */
    val screenTitle = TextStyle(
        fontFamily = SerifSc, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp, lineHeight = 26.sp,
    )
    val typeButton = TextStyle(
        fontFamily = SerifSc, fontSize = 21.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp, lineHeight = 26.sp,
    )
    val menuItem = TextStyle(
        fontFamily = SerifSc, fontSize = 15.sp, letterSpacing = 0.45.sp,
    )
    val groupName = TextStyle(
        fontFamily = SerifSc, fontSize = 12.5.sp, letterSpacing = 0.75.sp,
    )
    val quote = TextStyle(
        fontFamily = SerifSc, fontSize = 10.sp, letterSpacing = 0.2.sp,
    )
    val sync = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp)
    val cardTitle = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.5.sp,
        lineHeightStyle = tightLine,
    )
    val meta = TextStyle(fontSize = 10.sp, lineHeight = 13.sp)
    val kind = TextStyle(fontSize = 9.5.sp, lineHeight = 12.sp)
    val badge = TextStyle(
        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum",
    )
    val jump = TextStyle(fontSize = 9.5.sp, letterSpacing = 0.2.sp)
    val navLabel = TextStyle(fontSize = 10.sp, lineHeight = 10.sp)
    val count = TextStyle(fontSize = 11.5.sp, fontFeatureSettings = "tnum")
    val tiny = TextStyle(fontSize = 10.sp, letterSpacing = 1.6.sp)
    val body = TextStyle(fontSize = 13.sp, lineHeight = 19.sp)
    val bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
    val sectionTitle = TextStyle(
        fontFamily = SerifSc, fontSize = 15.sp, letterSpacing = 0.6.sp,
    )
}

// ---------------------------------------------------------------- 统一字阶

/**
 * 所有文字共用一套体系：标题宋体、正文系统 sans，字号全部用 sp
 * （随系统字体缩放自动适配）。M3 官方控件的 Text 也走这里，不再与
 * [PaperType] 各说各话。
 */
private fun style(
    size: TextUnit,
    family: FontFamily,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size,
    fontWeight = weight,
    lineHeight = size * 1.35f,
    letterSpacing = 0.sp,
)

private fun appTypography(): Typography {
    val title = SerifSc
    val body = FontFamily.Default
    return Typography(
        displayLarge = style(36.sp, title),
        displayMedium = style(30.sp, title),
        displaySmall = style(26.sp, title),
        headlineLarge = style(24.sp, title),
        headlineMedium = style(21.sp, title),
        headlineSmall = style(18.sp, title),
        titleLarge = style(17.sp, title, FontWeight.SemiBold),
        titleMedium = style(14.sp, title, FontWeight.SemiBold),
        titleSmall = style(12.5.sp, title, FontWeight.Medium),
        bodyLarge = style(13.sp, body),
        bodyMedium = style(11.5.sp, body),
        bodySmall = style(10.sp, body),
        labelLarge = style(12.sp, body, FontWeight.Medium),
        labelMedium = style(10.5.sp, body, FontWeight.Medium),
        labelSmall = style(9.5.sp, body),
    )
}

// ---------------------------------------------------------------- 主题

/** 把纸感令牌映射到 M3 colorScheme，供官方控件取色 */
private fun paperScheme(t: PaperTokens) = if (t.dark) darkColorScheme(
    primary = t.accent,
    onPrimary = t.paper,
    primaryContainer = t.accentSoft,
    onPrimaryContainer = t.accent,
    secondary = t.ink2,
    onSecondary = t.paper,
    secondaryContainer = t.card,
    onSecondaryContainer = t.ink,
    tertiary = t.pulse,
    onTertiary = t.paper,
    error = t.statusExpired,
    onError = t.paper,
    errorContainer = t.card,
    onErrorContainer = t.badgeInkHot,
    background = t.paper,
    onBackground = t.ink,
    surface = t.card,
    onSurface = t.ink,
    surfaceVariant = t.card,
    onSurfaceVariant = t.ink2,
    outline = t.lineSoft,
    outlineVariant = t.line,
    surfaceContainerLowest = t.paper,
    surfaceContainerLow = t.card,
    surfaceContainer = t.card,
    surfaceContainerHigh = t.card,
    surfaceContainerHighest = t.line,
    inverseSurface = t.ink,
    inverseOnSurface = t.paper,
    inversePrimary = t.paper,
    scrim = Color(0x99000000),
) else lightColorScheme(
    primary = t.accent,
    onPrimary = t.card,
    primaryContainer = t.accentSoft,
    onPrimaryContainer = t.accent,
    secondary = t.ink2,
    onSecondary = t.card,
    secondaryContainer = t.accentSoft,
    onSecondaryContainer = t.accent,
    tertiary = t.pulse,
    onTertiary = t.card,
    error = t.statusExpired,
    onError = t.card,
    errorContainer = t.markHot,
    onErrorContainer = t.badgeInkHot,
    background = t.paper,
    onBackground = t.ink,
    surface = t.card,
    onSurface = t.ink,
    surfaceVariant = t.card,
    onSurfaceVariant = t.ink2,
    outline = t.lineSoft,
    outlineVariant = t.line,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = t.card,
    surfaceContainer = t.card,
    surfaceContainerHigh = t.card,
    surfaceContainerHighest = t.line,
    inverseSurface = t.ink,
    inverseOnSurface = t.card,
    inversePrimary = t.card,
    scrim = Color(0x66000000),
)

/** 纸感形状（3/5/9/14/16），不是 M3 的默认圆角 */
private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(9.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun XxtTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accents: AccentColors = AccentColors(),
    style: ThemeStyle = ThemeStyle.PAPER,
    content: @Composable () -> Unit,
) {
    // 作业色与考试色是两套可以分别挑的颜色：考试色只影响考试类型
    val tokens = paperTokens(dark, accents.base, style, accents.exam)
    CompositionLocalProvider(
        LocalPaper provides tokens,
        LocalAccent provides accents,
    ) {
        MaterialTheme(
            colorScheme = paperScheme(tokens),
            shapes = PaperShapes,
            typography = appTypography(),
            content = content,
        )
    }
}
