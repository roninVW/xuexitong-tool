package com.xxt.board.data

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 学习通 H5 页面解析。
 * 选择器与时间格式全部集中在这里：学习通改版时只改这个文件。
 */
object Parsers {

    // ---------------------------------------------------------------- 列表

    fun workList(html: String, course: Course): List<Task> =
        Jsoup.parse(html).select("dt[data]").mapNotNull { el ->
            val rawUrl = el.attr("data").trim()
            val title = el.selectFirst("h3")?.text()?.trim().orEmpty()
            if (rawUrl.isEmpty() || title.isEmpty()) return@mapNotNull null
            val action = el.selectFirst("b")?.text()?.trim().orEmpty()
            val itemText = el.text()
            val status = statusOf(el.attr("status").toIntOrNull(), action)
            Task(
                key = keyOf(TaskType.WORK, course.courseId, title),
                type = TaskType.WORK,
                courseId = course.courseId,
                classId = course.classId,
                courseName = course.name,
                title = title,
                status = status,
                publishAt = parseTime(el.selectFirst("p")?.text()) ?: parseTime(itemText),
                // 少数学校列表页就带截止时间，先抓一次，抓不到再进详情
                dueAt = grabTime(itemText, DUE_KEYS),
                submittedAt = null,
                gradedAt = null,
                // 有些作业在列表页就带「成绩：95」，已批阅才认（避免标题里的数字被当成分数）
                score = if (status == TaskStatus.GRADED) scoreOf(itemText) else null,
                url = normalizeUrl(rawUrl),
            )
        }

    fun examList(html: String, course: Course): List<Task> =
        Jsoup.parse(html).select("tr[data]").mapNotNull { tr ->
            val rawUrl = tr.attr("data").trim()
            val cells = tr.select("td").map { it.text().trim() }
            val title = tr.attr("title").trim().ifEmpty { cells.firstOrNull().orEmpty() }
            if (rawUrl.isEmpty() || title.isEmpty()) return@mapNotNull null
            val statusText = cells.getOrElse(2) { "" }
            val code = tr.attr("status").toIntOrNull()
            val status = statusOf(code, statusText)
            // 分数列的位置各校不一样（有的在「状态」前、有的在后）。
            // 先试第 2 列，再在其余列里找纯数字单元格，避免整列错位就丢分。
            val score = (cells.getOrNull(1)?.takeIf { SCORE_CELL.matches(it.trim()) }
                ?: cells.drop(1).firstOrNull { SCORE_CELL.matches(it.trim()) })
                ?.trim()
                ?.removeSuffix("分")
            Task(
                key = keyOf(TaskType.EXAM, course.courseId, title),
                type = TaskType.EXAM,
                courseId = course.courseId,
                classId = course.classId,
                courseName = course.name,
                title = title,
                status = status,
                publishAt = null,
                dueAt = grabTime(tr.text(), DUE_KEYS),
                submittedAt = null,
                gradedAt = null,
                // 已交 / 已批阅就存分数；未做的行即使有数字也不当成绩
                score = if (status == TaskStatus.GRADED || status == TaskStatus.SUBMITTED) score else null,
                url = normalizeUrl(rawUrl),
            )
        }

    // ---------------------------------------------------------------- 详情

    data class Detail(val startAt: Long?, val dueAt: Long?, val submitAt: Long?, val score: String?)

    fun detail(html: String): Detail {
        val doc = Jsoup.parse(html)
        val text = doc.text()
        // 有些页面把时间塞在 input / data 属性 / JS 变量里，先补一层
        val attrs = doc.select("[value], [data-end], [data-endtime], [data-deadline]")
            .joinToString(" ") { it.attr("value") + " " + it.attr("data-end") + " " + it.attr("data-endtime") + " " + it.attr("data-deadline") }
        val haystack = "$text $attrs ${html.take(20000)}"

        return Detail(
            startAt = grabTime(haystack, START_KEYS) ?: grabJsonTime(html, "start"),
            dueAt = grabTime(haystack, DUE_KEYS) ?: grabJsonTime(html, "end"),
            submitAt = grabTime(haystack, SUBMIT_KEYS) ?: grabJsonTime(html, "submit"),
            score = scoreOf(text),
        )
    }

    /** 详情 / 列表里的成绩：兼容「本次成绩：85」「得分 85.5 分」「卷面成绩:90」 */
    private val SCORE_TEXT = Regex(
        """(?:本次成绩|总成绩|卷面成绩|考试成绩|课程成绩|成绩|得分|分数)\s*[:：]?\s*(\d{1,3}(?:\.\d{1,2})?)""",
    )

    /** 表格里「纯分数」的单元格：85 / 85.5 / 85分 */
    private val SCORE_CELL = Regex("""^\d{1,3}(?:\.\d{1,2})?\s*分?$""")

    fun scoreOf(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return SCORE_TEXT.find(text)?.groupValues?.get(1)
    }

    private val START_KEYS = listOf("开始时间", "考试时间", "开始日期", "startTime", "start_time")
    private val DUE_KEYS = listOf(
        "截止时间", "结束时间", "提交截止", "作业截止", "答题截止", "有效期至", "截止日期",
        "endTime", "end_time", "deadline", "dueTime",
    )
    private val SUBMIT_KEYS = listOf("提交时间", "提交日期", "submitTime", "submit_time")

    // ---------------------------------------------------------------- 时间

    private val FULL_DATE = Regex(
        """(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?""" +
            """(?:[ T]*(\d{1,2})\s*[:：]\s*(\d{1,2})(?:\s*[:：]\s*(\d{1,2}))?)?""",
    )
    private val SHORT_DATE = Regex(
        """(?<!\d)(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?""" +
            """(?:[ T]*(\d{1,2})\s*[:：]\s*(\d{1,2}))?""",
    )

    /**
     * 学习通给的是无时区本地时间。这里要兼容四种写法：
     * 2026-11-15 23:59 / 2026/11/15 / 2026年11月15日 / 11-15 23:59（无年份）
     */
    fun parseTime(text: String?): Long? {
        if (text.isNullOrBlank()) return null

        FULL_DATE.find(text)?.let { m ->
            val date = runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull() ?: return@let
            return toMillis(date, m.groupValues[4], m.groupValues[5], m.groupValues[6])
        }

        SHORT_DATE.find(text)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            val now = LocalDate.now()
            var date = runCatching { LocalDate.of(now.year, month, day) }.getOrNull() ?: return@let
            // 无年份时：如果算出来比今天早 30 天以上，说明是明年的
            if (date.isBefore(now.minusDays(30))) {
                date = runCatching { LocalDate.of(now.year + 1, month, day) }.getOrNull() ?: date
            }
            return toMillis(date, m.groupValues[3], m.groupValues[4], "")
        }
        return null
    }

    private fun toMillis(date: LocalDate, h: String, m: String, s: String): Long {
        val hour = h.toIntOrNull() ?: 23
        val minute = m.toIntOrNull() ?: 59
        val second = s.toIntOrNull() ?: 0
        val time = runCatching { LocalTime.of(hour, minute, second) }.getOrElse { LocalTime.of(23, 59) }
        return LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** 在关键字附近找时间；找不到就返回 null */
    private fun grabTime(text: String, keywords: List<String>): Long? {
        for (key in keywords) {
            var index = text.indexOf(key, ignoreCase = true)
            var guard = 0
            while (index >= 0 && guard < 3) {
                val window = text.substring(index, minOf(text.length, index + 60))
                parseTime(window)?.let { return it }
                index = text.indexOf(key, index + key.length, ignoreCase = true)
                guard++
            }
        }
        return null
    }

    /** 兜底：从 JSON 片段里抠 endTime / deadline */
    private fun grabJsonTime(html: String, prefix: String): Long? {
        val regex = Regex(
            """"(?:${prefix}Time|${prefix}_time|${prefix}Date)"\s*:\s*"?([0-9]{4}[-/]\d{1,2}[-/]\d{1,2}[^",}]*)"?""",
            RegexOption.IGNORE_CASE,
        )
        return regex.find(html)?.groupValues?.get(1)?.let { parseTime(it) }
    }

    // ---------------------------------------------------------------- 状态

    private fun statusOf(code: Int?, actionText: String): TaskStatus = when (code) {
        1 -> TaskStatus.TODO
        2 -> TaskStatus.EXPIRED
        3 -> TaskStatus.SUBMITTED
        4 -> TaskStatus.GRADED
        else -> inferStatus(actionText)
    }

    private fun inferStatus(text: String): TaskStatus = when {
        text.contains("已完成") || text.contains("已批阅") -> TaskStatus.GRADED
        text.contains("待批阅") || (text.contains("批阅") && text.contains("待")) -> TaskStatus.SUBMITTED
        text.contains("过期") -> TaskStatus.EXPIRED
        else -> TaskStatus.TODO
    }

    fun keyOf(type: TaskType, courseId: String, title: String): String =
        "${type.name}|$courseId|${title.trim()}"

    fun normalizeUrl(raw: String): String {
        val path = raw.trim()
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("//") -> "https:$path"
            path.startsWith("/") -> ChaoxingHttp.MOOC + path
            else -> ChaoxingHttp.MOOC + "/" + path
        }
    }

    // ---------------------------------------------------------------- 答题页地址

    /*
     * 列表里的 url 只是「入口页」（/work/phone/task-work?...），它是给官方 App 的壳用的：
     * 页面本身只有课程名、时间、状态，点「查看作业要求 / 开始答题」走的是 App 的 jsBridge。
     * 在纯 WebView 里这个按钮经常点了没反应，于是停在入口页，永远进不到题目和提交界面。
     *
     * 真正的答题页写在入口页的 HTML 里，常见三种写法：
     *   var url = location.origin + _CP_ + '/work/doHomeWorkNew?...'
     *   <a href="/mooc-ans/work/doHomeWorkNew?...">开始答题</a>
     *   window.location.href = '/mooc-ans/exam/test/reVersionTestStartNew?...'
     * 这里把地址抠出来，直接打开答题页，绕开那个失效的按钮。
     */

    private const val ANSWER_PATH = "doHomeWorkNew|selectWorkQuestion|reVersionTestStartNew|work/dowork|exam/test"

    private val CP_URL = Regex(
        """(?:var|let|const)?\s*url\s*=\s*location\.origin\s*\+\s*_CP_\s*\+\s*['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE,
    )
    private val CP_ONLY = Regex("""_CP_\s*\+\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
    private val HREF_ANSWER = Regex(
        """href\s*=\s*["']([^"']*(?:$ANSWER_PATH)[^"']*)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val MOOC_ANS_HREF = Regex("""href\s*=\s*["']([^"']*mooc-ans[^"']*)["']""", RegexOption.IGNORE_CASE)
    private val GENERIC_ANSWER = Regex(
        """(?:url|href|src)\s*[=:]\s*['"]([^'"]*(?:$ANSWER_PATH)[^"']*)['"]""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 从入口页 HTML 里取出答题页绝对地址（保持接口域名，WebView 打开时会换成网页域名）。
     * 找不到返回 null，调用方仍按入口页打开。
     */
    fun extractAnswerUrl(html: String): String? {
        val text = html.replace("&amp;", "&").replace("&#38;", "&")

        CP_URL.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return absoluteAnswer(it)
        }
        CP_ONLY.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return absoluteAnswer(it)
        }
        for (regex in listOf(HREF_ANSWER, MOOC_ANS_HREF, GENERIC_ANSWER)) {
            val raw = regex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
            if (raw.isNotEmpty() && !raw.startsWith("javascript:", ignoreCase = true)) {
                return absoluteAnswer(raw)
            }
        }
        return null
    }

    /** 已经是答题页地址（不需要再解析一次） */
    fun isAnswerUrl(url: String): Boolean =
        url.contains("/mooc-ans/") || Regex(ANSWER_PATH).containsMatchIn(url)

    /**
     * `_CP_` 在学习通里就是 `/mooc-ans`，只会出现在接口域名下；
     * 直接写成 `/mooc-ans/...` 的也按接口域名补全。
     */
    private fun absoluteAnswer(raw: String): String {
        val p = raw.trim()
        return when {
            p.startsWith("http://") || p.startsWith("https://") -> p
            p.startsWith("//") -> "https:$p"
            p.startsWith("/mooc-ans/") -> ChaoxingHttp.MOOC + p
            p.startsWith("/work/") || p.startsWith("/exam/") -> ChaoxingHttp.MOOC + "/mooc-ans" + p
            p.startsWith("/") -> ChaoxingHttp.MOOC + p
            else -> ChaoxingHttp.MOOC + "/mooc-ans/" + p
        }
    }

    /** 接口域名 → 网页域名（WebView / 学习通 App 里只认网页域名） */
    fun toWebHost(url: String): String =
        url.replace("mooc1-api.chaoxing.com", "mooc1.chaoxing.com")

    /** 网页域名 → 接口域名（解析 HTML 时用接口域名更稳） */
    fun toApiHost(url: String): String =
        url.replace("://mooc1.chaoxing.com", "://mooc1-api.chaoxing.com")
}
