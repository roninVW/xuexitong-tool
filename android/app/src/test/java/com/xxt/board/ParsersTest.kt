package com.xxt.board

import com.xxt.board.data.Course
import com.xxt.board.data.Parsers
import com.xxt.board.data.Subject
import com.xxt.board.data.TaskStatus
import com.xxt.board.data.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析器回归：学习通改版时，先抓真实 HTML 存成 fixture，再改 Parsers，
 * 这里的断言会立刻告诉你哪一条挂了。
 */
class ParsersTest {

    private val course = Course("1001", "2001", "555", "高等数学A(上)", Subject.MATH)

    @Test
    fun `作业列表解析出四态`() {
        val html = """
            <dl>
              <dt data="/work/phone/task-work?workId=1" status="1">
                <h3>第七章 微分方程</h3><p>发布时间：2026-11-01 08:00</p><b>未交</b>
              </dt>
              <dt data="/work/phone/task-work?workId=2" status="3">
                <h3>第六章 定积分</h3><p>发布时间：2026-10-20 09:30</p><b>待批阅</b>
              </dt>
              <dt data="/work/phone/task-work?workId=3" status="2">
                <h3>第五章 不定积分</h3><p>发布时间：2026-10-01 10:00</p><b>已过期</b>
              </dt>
              <dt data="/work/phone/task-work?workId=4" status="4">
                <h3>第四章 中值定理</h3><p>发布时间：2026-09-15 14:00</p><b>已完成</b>
              </dt>
            </dl>
        """.trimIndent()

        val list = Parsers.workList(html, course)
        assertEquals(4, list.size)
        assertEquals(
            listOf(TaskStatus.TODO, TaskStatus.SUBMITTED, TaskStatus.EXPIRED, TaskStatus.GRADED),
            list.map { it.status },
        )
        assertEquals("第七章 微分方程", list[0].title)
        assertTrue(list[0].url.startsWith("https://mooc1-api.chaoxing.com/work/"))
        assertNotNull(list[0].publishAt)
    }

    @Test
    fun `考试列表带分数`() {
        val html = """
            <table><tbody>
              <tr data="/exam/phone/task-exam?examId=1" status="4" title="期中考试">
                <td>期中考试</td><td>92</td><td>已完成</td>
              </tr>
              <tr data="/exam/phone/task-exam?examId=2" status="1" title="单元测验">
                <td>单元测验</td><td>—</td><td>未交</td>
              </tr>
            </tbody></table>
        """.trimIndent()

        val list = Parsers.examList(html, course)
        assertEquals(2, list.size)
        assertEquals("92", list[0].score)
        assertEquals(null, list[1].score)
    }

    @Test
    fun `详情页抓开始截止与成绩`() {
        val html = """
            <div>
              <p>开始时间：2026-11-01 08:00</p>
              <p>截止时间：2026-11-15 23:59</p>
              <p>本次成绩：88 分</p>
            </div>
        """.trimIndent()

        val detail = Parsers.detail(html)
        assertNotNull(detail.startAt)
        assertNotNull(detail.dueAt)
        assertEquals("88", detail.score)
        assertTrue(detail.dueAt!! > detail.startAt!!)
    }

    @Test
    fun `状态属性缺失时用文案兜底`() {
        val html = """
            <dl>
              <dt data="/work/phone/task-work?workId=9">
                <h3>等待批阅的作业</h3><p>发布时间：2026-11-02 10:00</p><b>等待教师批阅</b>
              </dt>
            </dl>
        """.trimIndent()

        val list = Parsers.workList(html, course)
        assertEquals(TaskStatus.SUBMITTED, list.single().status)
    }

    // ---------------------------------------------------------------- 答题页地址

    @Test
    fun `从 _CP_ 里取答题页地址`() {
        val html = """
            <script>
              var url = location.origin + _CP_ + '/work/doHomeWorkNew?courseId=1&classId=2';
            </script>
        """.trimIndent()

        assertEquals(
            "https://mooc1-api.chaoxing.com/mooc-ans/work/doHomeWorkNew?courseId=1&classId=2",
            Parsers.extractAnswerUrl(html),
        )
    }

    @Test
    fun `从 href 取答题页并还原 HTML 实体`() {
        val html = """<a href="/mooc-ans/work/doHomeWorkNew?courseId=1&amp;classId=2">开始答题</a>"""

        assertEquals(
            "https://mooc1-api.chaoxing.com/mooc-ans/work/doHomeWorkNew?courseId=1&classId=2",
            Parsers.extractAnswerUrl(html),
        )
    }

    @Test
    fun `考试页同样能取到地址`() {
        val html = """<a href="/mooc-ans/exam/test/reVersionTestStartNew?courseId=7&examId=8">开始考试</a>"""

        assertEquals(
            "https://mooc1-api.chaoxing.com/mooc-ans/exam/test/reVersionTestStartNew?courseId=7&examId=8",
            Parsers.extractAnswerUrl(html),
        )
    }

    @Test
    fun `只有入口页时返回 null`() {
        assertNull(Parsers.extractAnswerUrl("<html><body>作业详情：第七章</body></html>"))
    }

    @Test
    fun `答题页地址判定与域名互换`() {
        val answer = "https://mooc1-api.chaoxing.com/mooc-ans/work/doHomeWorkNew?workId=1"
        assertTrue(Parsers.isAnswerUrl(answer))
        assertTrue(!Parsers.isAnswerUrl("https://mooc1-api.chaoxing.com/work/phone/task-work?workId=1"))

        assertEquals(
            "https://mooc1.chaoxing.com/mooc-ans/work/doHomeWorkNew?workId=1",
            Parsers.toWebHost(answer),
        )
        assertEquals(answer, Parsers.toApiHost(Parsers.toWebHost(answer)))
    }

    // ---------------------------------------------------------------- 考试分数

    @Test
    fun `考试分数认正文标签的几种写法`() {
        assertEquals("85", Parsers.scoreOf("本次成绩：85"))
        assertEquals("90.5", Parsers.scoreOf("得分 90.5 分"))
        assertEquals("88", Parsers.scoreOf("总评 88"))
        assertEquals("92", Parsers.scoreOf("卷面成绩:92"))
        assertNull(Parsers.scoreOf("尚未批阅"))
    }

    @Test
    fun `考试分数能从页面 JSON 里取到`() {
        val html = """<script>var data = {"examId":7,"score":86.5,"status":4};</script>"""
        assertEquals("86.5", Parsers.scoreFromJson(html))
        assertEquals("86.5", Parsers.detail(html).score)
    }

    @Test
    fun `未批阅的 0 分不当作成绩`() {
        assertNull(Parsers.scoreFromJson("""{"score":0,"status":3}"""))
    }

    @Test
    fun `考试列表分数列错位时按标签兜底`() {
        val html = """
            <table>
              <tr data="/exam/phone/task-exam?examId=8" status="4" title="期末考试">
                <td>期末考试</td><td>已批阅</td><td>成绩：92</td>
              </tr>
            </table>
        """.trimIndent()

        val list = Parsers.examList(html, course)
        assertEquals("92", list.single().score)
    }

    // ---------------------------------------------------------------- 主键

    @Test
    fun `同名作业按 workId 区分主键`() {
        val a = Parsers.keyOf(TaskType.WORK, "1", "第 3 章作业", "/work/phone/task-work?courseId=1&workId=9001")
        val b = Parsers.keyOf(TaskType.WORK, "1", "第 3 章作业", "/work/phone/task-work?courseId=1&workId=9002")
        assertTrue(a != b)
        assertEquals("WORK|1|#9001", a)
    }

    @Test
    fun `取不到编号时退回标题且保持稳定`() {
        val a = Parsers.keyOf(TaskType.WORK, "1", "章节测验", "/work/phone/task-work?courseId=1")
        val b = Parsers.keyOf(TaskType.WORK, "1", "章节测验", "")
        assertEquals(a, b)
        assertEquals("WORK|1|章节测验", a)
    }

    @Test
    fun `列表里同名的两条作业都会保留`() {
        val html = """
            <dl>
              <dt data="/work/phone/task-work?workId=1" status="1">
                <h3>章节测验</h3><p>发布时间：2026-10-01 08:00</p><b>未交</b>
              </dt>
              <dt data="/work/phone/task-work?workId=2" status="4">
                <h3>章节测验</h3><p>发布时间：2026-09-01 08:00</p><b>已完成</b>
              </dt>
            </dl>
        """.trimIndent()

        val list = Parsers.workList(html, course)
        assertEquals(2, list.size)
        assertEquals(2, list.map { it.key }.distinct().size)
    }
}
