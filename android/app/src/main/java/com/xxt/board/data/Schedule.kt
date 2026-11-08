package com.xxt.board.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 课表条目（导入而来，不依赖学习通接口）。
 * weekday: 1=周一 … 7=周日；section 从 1 开始。
 */
@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    /** 周次原始表达式：`1-16`、`1-16单`、`1,3,5-8` */
    val weeks: String,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule ORDER BY weekday, startSection")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT COUNT(*) FROM schedule")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(items: List<ScheduleEntity>)

    @Query("DELETE FROM schedule")
    suspend fun clear()
}

// ---------------------------------------------------------------- 周次

/** `1-16单` / `1,3,5-8` / `2-16双` → 该周是否有课 */
fun weekMatches(expr: String, week: Int): Boolean {
    if (expr.isBlank()) return true
    val oddOnly = expr.contains("单")
    val evenOnly = expr.contains("双")
    var hit = false
    Regex("(\\d+)\\s*(?:-\\s*(\\d+))?").findAll(expr).forEach { m ->
        val a = m.groupValues[1].toIntOrNull() ?: return@forEach
        val b = m.groupValues[2].toIntOrNull() ?: a
        if (week in a..b) hit = true
    }
    if (!hit) return false
    return when {
        oddOnly -> week % 2 == 1
        evenOnly -> week % 2 == 0
        else -> true
    }
}

fun normalizeWeeks(raw: String): String {
    val text = raw.trim()
        .replace("第", "")
        .replace("周", "")
        .replace("（", "")
        .replace("(", "")
        .replace("）", "")
        .replace(")", "")
        .replace(" ", "")
    return text.ifBlank { "1-16" }
}

// ---------------------------------------------------------------- 节次时间

/** 常见高校作息，用于把 ICS 的起止时间映射成节次 */
private val sectionStartMinutes = listOf(
    8 * 60, 8 * 60 + 55, 10 * 60, 10 * 60 + 55,
    14 * 60, 14 * 60 + 55, 16 * 60, 16 * 60 + 55,
    19 * 60, 19 * 60 + 55, 20 * 60 + 50, 21 * 60 + 45,
)

val sectionTimeLabels: List<String> = listOf(
    "08:00", "08:55", "10:00", "10:55",
    "14:00", "14:55", "16:00", "16:55",
    "19:00", "19:55", "20:50", "21:45",
)

fun sectionForTime(time: LocalTime): Int {
    val m = time.hour * 60 + time.minute
    var best = 1
    var bestGap = Int.MAX_VALUE
    sectionStartMinutes.forEachIndexed { index, start ->
        val gap = kotlin.math.abs(start - m)
        if (gap < bestGap) {
            bestGap = gap
            best = index + 1
        }
    }
    return best
}

// ---------------------------------------------------------------- 导入解析

private val WEEKDAY_WORDS = mapOf(
    "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7,
    "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7,
)

private fun parseWeekday(raw: String): Int? {
    val t = raw.trim().lowercase()
        .replace("星期", "").replace("周", "").replace("礼拜", "")
    t.toIntOrNull()?.let { if (it in 1..7) return it }
    WEEKDAY_WORDS[t]?.let { return it }
    WEEKDAY_WORDS[t.take(3)]?.let { return it }
    return null
}

private fun parseSections(raw: String): Pair<Int, Int>? {
    val nums = Regex("\\d+").findAll(raw).mapNotNull { it.value.toIntOrNull() }.toList()
    if (nums.isEmpty()) return null
    val start = nums.first().coerceIn(1, 12)
    val end = (nums.getOrNull(1) ?: start).coerceIn(start, 12)
    return start to end
}

private fun splitCells(line: String, delimiter: Char): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var inQuote = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (inQuote && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"'); i++
                } else {
                    inQuote = !inQuote
                }
            }
            c == delimiter && !inQuote -> {
                out += sb.toString().trim(); sb.clear()
            }
            else -> sb.append(c)
        }
        i++
    }
    out += sb.toString().trim()
    return out
}

private fun guessDelimiter(line: String): Char {
    val tabs = line.count { it == '\t' }
    val commas = line.count { it == ',' }
    val semis = line.count { it == ';' }
    return when {
        tabs >= commas && tabs >= semis && tabs > 0 -> '\t'
        semis > commas -> ';'
        else -> ','
    }
}

/**
 * CSV / TSV 课表导入。
 *
 * 支持两种写法：
 * 1. 带表头：`课程名,星期,开始节,结束节,周次,地点,教师`（列顺序随意，按关键字识别）
 * 2. 不带表头：按上面的列顺序。
 */
fun parseCsvSchedule(text: String): List<ScheduleEntity> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    if (lines.isEmpty()) return emptyList()

    val delimiter = guessDelimiter(lines.first())
    val rows = lines.map { splitCells(it, delimiter) }
    val header = rows.first()
    val hasHeader = header.any { cell ->
        val c = cell.replace(" ", "")
        listOf("课程", "名称", "星期", "周几", "节次", "周次", "地点", "教室", "教师").any { c.contains(it) }
    }

    val body = if (hasHeader) rows.drop(1) else rows
    val indexOf = { keys: List<String>, default: Int ->
        if (!hasHeader) default else header.indexOfFirst { h ->
            val c = h.replace(" ", "").lowercase()
            keys.any { c.contains(it) }
        }.takeIf { it >= 0 } ?: default
    }

    val iName = indexOf(listOf("课程", "名称", "科目"), 0)
    val iWeekday = indexOf(listOf("星期", "周几", "day"), 1)
    val iStart = indexOf(listOf("开始节", "起始节", "节次"), 2)
    val iEnd = indexOf(listOf("结束节", "终止节"), 3)
    val iWeeks = indexOf(listOf("周次", "周数", "week"), 4)
    val iLocation = indexOf(listOf("地点", "教室", "location"), 5)
    val iTeacher = indexOf(listOf("教师", "老师", "teacher"), 6)

    val out = ArrayList<ScheduleEntity>()
    body.forEach { cells ->
        val name = cells.getOrNull(iName)?.trim().orEmpty()
        if (name.isEmpty()) return@forEach
        val weekday = parseWeekday(cells.getOrNull(iWeekday).orEmpty()) ?: return@forEach
        val range = parseSections(
            listOfNotNull(cells.getOrNull(iStart), cells.getOrNull(iEnd)).joinToString("-"),
        ) ?: return@forEach
        val weeks = normalizeWeeks(cells.getOrNull(iWeeks).orEmpty())
        out += ScheduleEntity(
            id = "$name|$weekday|${range.first}|$weeks".hashCode().toString(),
            name = name,
            teacher = cells.getOrNull(iTeacher)?.trim().orEmpty(),
            location = cells.getOrNull(iLocation)?.trim().orEmpty(),
            weekday = weekday,
            startSection = range.first,
            endSection = range.second,
            weeks = weeks,
        )
    }
    return out.distinctBy { it.id }
}

/**
 * ICS 日历导入（学习通 / 教务系统导出的课程表通常就是 ics）。
 * 只取 VEVENT 的 SUMMARY / LOCATION / DESCRIPTION / DTSTART / DTEND / RRULE。
 */
fun parseIcsSchedule(text: String): List<ScheduleEntity> {
    val unfolded = StringBuilder()
    text.lines().forEach { line ->
        if (line.startsWith(" ") || line.startsWith("\t")) {
            unfolded.append(line.trimStart())
        } else {
            unfolded.append('\n').append(line)
        }
    }

    val out = ArrayList<ScheduleEntity>()
    val dtf = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    var inEvent = false
    var summary = ""
    var location = ""
    var description = ""
    var start: LocalDateTime? = null
    var end: LocalDateTime? = null
    var count = 0
    var until: LocalDate? = null

    fun flush() {
        val s = start ?: return
        val e = end ?: s.plusHours(1)
        val name = summary.trim()
        if (name.isEmpty()) return
        val startSection = sectionForTime(s.toLocalTime())
        val endSection = sectionForTime(e.toLocalTime()).coerceAtLeast(startSection)
        val weeks = when {
            count > 0 -> "1-$count"
            until != null -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(s.toLocalDate(), until).coerceAtLeast(0)
                "1-${(days / 7 + 1).toInt()}"
            }
            else -> "1"
        }
        out += ScheduleEntity(
            id = "$name|${s.dayOfWeek.value}|$startSection|$weeks".hashCode().toString(),
            name = name,
            teacher = description.lineSequence().firstOrNull { it.contains("教师") || it.contains("老师") }
                ?.substringAfter("：", "")?.trim().orEmpty(),
            location = location.trim(),
            weekday = s.dayOfWeek.value,
            startSection = startSection,
            endSection = endSection,
            weeks = weeks,
        )
    }

    unfolded.lineSequence().forEach { raw ->
        val line = raw.trim()
        val colon = line.indexOf(':')
        val key = if (colon > 0) line.substring(0, colon) else line
        val value = if (colon > 0) line.substring(colon + 1) else ""
        when {
            line == "BEGIN:VEVENT" -> {
                inEvent = true; summary = ""; location = ""; description = ""
                start = null; end = null; count = 0; until = null
            }
            line == "END:VEVENT" -> {
                if (inEvent) flush()
                inEvent = false
            }
            !inEvent -> Unit
            key.startsWith("SUMMARY") -> summary = value.replace("\\,", ",").replace("\\;", ";")
            key.startsWith("LOCATION") -> location = value.replace("\\,", ",")
            key.startsWith("DESCRIPTION") -> description = value.replace("\\n", "\n")
            key.startsWith("DTSTART") -> start = parseIcsTime(value, dtf)
            key.startsWith("DTEND") -> end = parseIcsTime(value, dtf)
            key.startsWith("RRULE") -> {
                Regex("COUNT=(\\d+)").find(value)?.groupValues?.get(1)?.toIntOrNull()?.let { count = it }
                Regex("UNTIL=(\\d{8})").find(value)?.groupValues?.get(1)?.let {
                    until = runCatching { LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyyMMdd")) }.getOrNull()
                }
            }
        }
    }
    return out.distinctBy { it.id }
}

private fun parseIcsTime(raw: String, dtf: DateTimeFormatter): LocalDateTime? {
    val clean = raw.trim().removeSuffix("Z")
    if (clean.length < 15) return null
    return runCatching { LocalDateTime.parse(clean.take(15), dtf) }.getOrNull()
}

/** 自动识别格式 */
fun parseScheduleFile(name: String, text: String): List<ScheduleEntity> =
    if (name.endsWith(".ics", true) || text.contains("BEGIN:VCALENDAR")) {
        parseIcsSchedule(text)
    } else {
        parseCsvSchedule(text)
    }
