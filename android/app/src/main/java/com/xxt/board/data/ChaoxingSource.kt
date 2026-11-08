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
}
