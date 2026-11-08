package com.xxt.board.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/** 学习通数据源：课程 → 作业/考试列表 → 详情。全部走已登录的 Cookie。 */
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

class ChaoxingSource(private val http: ChaoxingHttp) {

    suspend fun checkSession(): Boolean = runCatching {
        val text = http.get(
            "${ChaoxingHttp.MOOC}/mycourse/backclazzdata?view=json&getTchClazzType=1&mcode=",
            referer = "https://i.chaoxing.com",
        )
        text.contains("\"result\":1")
    }.getOrDefault(false)

    suspend fun courses(): List<Course> {
        val text = http.get(
            "${ChaoxingHttp.MOOC}/mycourse/backclazzdata?view=json&getTchClazzType=1&mcode=",
            referer = "https://i.chaoxing.com",
        )
        val root = Json.parseToJsonElement(text).jsonObject
        if ((root["result"] as? JsonPrimitive)?.intOrNull != 1) error("登录态失效")

        val out = LinkedHashMap<String, Course>()
        val channels = root["channelList"]?.jsonArray ?: return emptyList()

        for (channelEl in channels) {
            val channel = channelEl.jsonObject
            var contentEl = channel["content"]
            if (contentEl is JsonPrimitive && contentEl.isString) {
                contentEl = runCatching { Json.parseToJsonElement(contentEl.content) }.getOrNull()
            }
            val content = contentEl?.jsonObject ?: continue
            val cpi = content["cpi"].str()
                ?: channel["cpi"].str().orEmpty()
            val name = content["name"].str().orEmpty()

            val data = content["course"]?.jsonObject?.get("data")?.jsonArray
            if (data != null && data.isNotEmpty()) {
                val classId = content["id"].str()
                    ?: channel["key"].str().orEmpty()
                for (d in data) {
                    val o = d.jsonObject
                    val cid = o["id"].str() ?: continue
                    val courseName = o["name"].str().orEmpty()
                    out.putIfAbsent(
                        "$cid|$classId",
                        Course(cid, classId, cpi, courseName, SubjectClassifier.classify(courseName)),
                    )
                }
            } else {
                val cid = content["id"].str() ?: continue
                for (c in content["clazz"]?.jsonArray.orEmpty()) {
                    val classId = c.jsonObject["id"].str() ?: continue
                    out.putIfAbsent(
                        "$cid|$classId",
                        Course(cid, classId, cpi, name, SubjectClassifier.classify(name)),
                    )
                }
            }
        }
        return out.values.toList()
    }

    suspend fun tasks(course: Course): List<Task> {
        val base = "${ChaoxingHttp.MOOC}/phone/moocAnalysis"
        val works = http.get("$base/workList?courseId=${course.courseId}&classId=${course.classId}")
        val exams = http.get("$base/examList?courseId=${course.courseId}&classId=${course.classId}")
        return Parsers.workList(works, course) + Parsers.examList(exams, course)
    }

    suspend fun detail(task: Task): Task {
        if (task.url.isBlank()) return task
        val d = runCatching { Parsers.detail(http.get(task.url)) }.getOrNull() ?: return task
        return task.copy(
            dueAt = d.dueAt ?: task.dueAt,
            submittedAt = d.submitAt ?: task.submittedAt,
            score = d.score ?: task.score,
        )
    }

    // ---------------------------------------------------------------- 打开作业

    /** key → 答题页地址。点一次解析一次太慢，而同一份作业的地址在一次会话里不会变 */
    private val openUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * 把「列表里的入口页地址」换成「真正的答题页/考试页地址」。
     *
     * 入口页 `/work/phone/task-work?...` 是给官方 App 的壳用的，页面上的按钮走 jsBridge，
     * 在纯 WebView 里经常点了没反应，于是永远进不到题目与提交界面。这里用登录 Cookie
     * 取一次入口页 HTML，把里面写着的 doHomeWorkNew / reVersionTestStartNew 地址抠出来。
     *
     * 解析失败（页面改版、Cookie 过期、网络不通）时原样返回入口页，不影响打开。
     */
    suspend fun resolveOpenUrl(key: String, taskUrl: String): String {
        if (taskUrl.isBlank()) return taskUrl
        if (Parsers.isAnswerUrl(taskUrl)) return taskUrl
        openUrlCache[key]?.let { return it }
        val html = runCatching {
            http.get(taskUrl, referer = ChaoxingHttp.REFERER)
        }.getOrNull().orEmpty()
        val answer = Parsers.extractAnswerUrl(html) ?: return taskUrl
        openUrlCache[key] = answer
        return answer
    }

    /** 提交完作业回来，把缓存清掉，下次重新解析（状态可能已经从「未交」变成「待批阅」） */
    fun forgetOpenUrl(key: String) {
        openUrlCache.remove(key)
    }
}
