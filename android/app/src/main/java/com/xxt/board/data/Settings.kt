package com.xxt.board.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    /** 原生模糊强度 0–40dp，默认 28 */
    val blurRadius: Int = 28,
    /** null = 跟随系统 */
    val darkMode: Boolean? = null,
    /** 作业色（基色）：荧光色、状态色、科目点由它 HSV 派生 */
    val workColor: Long = 0xFFC4763FL,
    /** 考试色；0 = 跟随作业色（由基色派生） */
    val examColor: Long = 0L,
    val showQuote: Boolean = true,
    /** 是否允许发通知（含定时同步） */
    val notifications: Boolean = true,
    /** 作业卡片边缘的动态火焰（默认关闭，省电） */
    val flameEffect: Boolean = false,
    val lastSyncAt: Long = 0L,
    /** 背景壁纸：本地文件绝对路径，null = 纯纸色 */
    val wallpaperPath: String? = null,
    /** 壁纸是视频（否则按图片/GIF 处理） */
    val wallpaperVideo: Boolean = false,
    /** 卡片是否做磨砂（背景壁纸开启时才有意义） */
    val cardBlur: Boolean = true,
    /** 界面风格："paper" 纸感 / "journal" 手账（默认手账） */
    val themeStyle: String = "journal",
    /** Global material, independent of the paper/journal theme. */
    val materialStyle: String = "default",
    val liquidTransparency: Int = 50,
    /** Actual Backdrop lens amount multiplier, independent from opacity and blur. */
    val liquidRefraction: Float = 1f,
    /** 独立的作业截止提醒，默认关闭。 */
    val deadlineReminders: Boolean = false,
    val deadlineLeadMinutes: Int = 60,
    /** 玻璃纸面不透明度 %（0 = 自动，随模糊强度变化） */
    val glassOpacity: Int = 0,
)

class SettingsStore(context: Context, private val preferences: DataStore<Preferences> = context.settingsDataStore) {

    private object K {
        val BLUR = intPreferencesKey("blur")
        val DARK = intPreferencesKey("dark") // -1 跟随 / 0 浅 / 1 深
        val WORK = longPreferencesKey("work_color")
        val EXAM = longPreferencesKey("exam_color")
        val QUOTE = booleanPreferencesKey("quote")
        val NOTIFY = booleanPreferencesKey("notifications")
        val FLAME = booleanPreferencesKey("flame_effect")
        val LAST = longPreferencesKey("last_sync")
        val WALLPAPER = stringPreferencesKey("wallpaper_path")
        val WALLPAPER_VIDEO = booleanPreferencesKey("wallpaper_video")
        val CARD_BLUR = booleanPreferencesKey("card_blur")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val BASE_THEME_STYLE = stringPreferencesKey("base_theme_style")
        val MATERIAL_STYLE = stringPreferencesKey("material_style")
        val LIQUID_TRANSPARENCY = intPreferencesKey("liquid_transparency")
        val LIQUID_REFRACTION = floatPreferencesKey("liquid_refraction_strength")
        val GLASS_OPACITY = intPreferencesKey("glass_opacity")
        val DEADLINE_REMINDERS = booleanPreferencesKey("deadline_reminders")
        val DEADLINE_LEAD = intPreferencesKey("deadline_lead_minutes")
    }

    val flow: Flow<AppSettings> = preferences.data.map { p ->
        AppSettings(
            blurRadius = p[K.BLUR] ?: 28,
            darkMode = when (p[K.DARK] ?: -1) {
                0 -> false
                1 -> true
                else -> null
            },
            workColor = p[K.WORK] ?: 0xFFC4763FL,
            examColor = p[K.EXAM] ?: 0L,
            showQuote = p[K.QUOTE] ?: true,
            notifications = p[K.NOTIFY] ?: true,
            flameEffect = p[K.FLAME] ?: false,
            lastSyncAt = p[K.LAST] ?: 0L,
            wallpaperPath = p[K.WALLPAPER]?.takeIf { it.isNotBlank() },
            wallpaperVideo = p[K.WALLPAPER_VIDEO] ?: false,
            cardBlur = p[K.CARD_BLUR] ?: true,
            themeStyle = resolveThemeStyle(p[K.THEME_STYLE], p[K.BASE_THEME_STYLE]),
            materialStyle = resolveMaterialStyle(p[K.MATERIAL_STYLE], p[K.THEME_STYLE]),
            glassOpacity = p[K.GLASS_OPACITY] ?: 0,
            liquidTransparency = (p[K.LIQUID_TRANSPARENCY] ?: 50).coerceIn(0,100),
            liquidRefraction = (p[K.LIQUID_REFRACTION] ?: 1f).takeIf{it.isFinite()}?.coerceIn(0f,2f)?:1f,
            deadlineReminders = p[K.DEADLINE_REMINDERS] ?: false,
            deadlineLeadMinutes = (p[K.DEADLINE_LEAD] ?: 60).coerceIn(1, 10080),
        )
    }

    suspend fun setBlurRadius(dp: Int) = preferences.edit { it[K.BLUR] = dp }

    suspend fun setDarkMode(value: Boolean?) = preferences.edit {
        it[K.DARK] = when (value) {
            true -> 1
            false -> 0
            null -> -1
        }
    }

    suspend fun setWorkColor(argb: Long) = preferences.edit { it[K.WORK] = argb }

    /** 0 = 恢复「跟随作业色」 */
    suspend fun setExamColor(argb: Long) = preferences.edit { it[K.EXAM] = argb }

    suspend fun setShowQuote(value: Boolean) = preferences.edit { it[K.QUOTE] = value }

    suspend fun setNotifications(value: Boolean) = preferences.edit { it[K.NOTIFY] = value }

    suspend fun setFlameEffect(value: Boolean) = preferences.edit { it[K.FLAME] = value }

    suspend fun markSynced(at: Long) = preferences.edit { it[K.LAST] = at }

    suspend fun setWallpaper(path: String?, video: Boolean) = preferences.edit {
        if (path.isNullOrBlank()) {
            it.remove(K.WALLPAPER)
            it[K.WALLPAPER_VIDEO] = false
        } else {
            it[K.WALLPAPER] = path
            it[K.WALLPAPER_VIDEO] = video
        }
    }

    suspend fun setCardBlur(value: Boolean) = preferences.edit { it[K.CARD_BLUR] = value }

    suspend fun setThemeStyle(value: String) = preferences.edit {
        // Persist the legacy material before removing the old liquid theme marker.
        it[K.MATERIAL_STYLE] = resolveMaterialStyle(it[K.MATERIAL_STYLE], it[K.THEME_STYLE])
        it[K.THEME_STYLE] = value.takeIf { key -> key == "paper" || key == "journal" } ?: "journal"
    }

    suspend fun setMaterialStyle(value: String) = preferences.edit {
        it[K.THEME_STYLE] = resolveThemeStyle(it[K.THEME_STYLE], it[K.BASE_THEME_STYLE])
        it[K.MATERIAL_STYLE] = if (value == "liquid") "liquid" else "default"
    }

    suspend fun setLiquidParameters(transparency:Int,refraction:Float)=preferences.edit {
        it[K.LIQUID_TRANSPARENCY]=transparency.coerceIn(0,100)
        it[K.LIQUID_REFRACTION]=refraction.takeIf{it.isFinite()}?.coerceIn(0f,2f)?:1f
    }
    suspend fun setLiquidTransparency(value:Int)=preferences.edit {it[K.LIQUID_TRANSPARENCY]=value.coerceIn(0,100)}
    suspend fun setLiquidRefraction(value:Float)=preferences.edit {it[K.LIQUID_REFRACTION]=value.takeIf{it.isFinite()}?.coerceIn(0f,2f)?:1f}
    suspend fun setGlassOpacity(value: Int) = preferences.edit { it[K.GLASS_OPACITY] = value }
    suspend fun setDeadlineReminders(value: Boolean) = preferences.edit { it[K.DEADLINE_REMINDERS] = value }
    suspend fun setDeadlineLeadMinutes(value: Int) = preferences.edit { it[K.DEADLINE_LEAD] = value.coerceIn(1, 10080) }

}

internal fun resolveThemeStyle(stored: String?, legacyBase: String?): String =
    (if (stored == "liquid") legacyBase else stored)
        .takeIf { it == "paper" || it == "journal" } ?: "journal"

internal fun resolveMaterialStyle(stored: String?, legacyTheme: String?): String =
    if (stored == "liquid" || (stored == null && legacyTheme == "liquid")) "liquid" else "default"
