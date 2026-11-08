package com.xxt.board.data

/** 作业 / 考试 */
enum class TaskType { WORK, EXAM }

/** 学习通原生四态：1 未交 / 2 已过期 / 3 待批阅 / 4 已完成 */
enum class TaskStatus { TODO, SUBMITTED, GRADED, EXPIRED }

enum class Subject(val label: String) {
    MATH("数学"),
    PHYSICS("物理"),
    CHEMISTRY("化学"),
    ENGLISH("英语"),
    POLITICS("思政"),
    CS("计算机"),
    ENGINEERING("工程"),
    BUSINESS("经管"),
    MEDICINE("医学"),
    PE("体育"),
    MILITARY("军事"),
    ART("艺术"),
    GENERAL("通识"),
    UNKNOWN("未分类"),
}

data class Course(
    val courseId: String,
    val classId: String,
    val cpi: String,
    val name: String,
    val subject: Subject = Subject.UNKNOWN,
)

data class Task(
    val key: String,
    val type: TaskType,
    val courseId: String,
    val classId: String,
    val courseName: String,
    val title: String,
    val status: TaskStatus,
    val publishAt: Long?,
    val dueAt: Long?,
    val submittedAt: Long?,
    val gradedAt: Long?,
    val score: String?,
    val url: String,
) {
    val isExam: Boolean get() = type == TaskType.EXAM
}
