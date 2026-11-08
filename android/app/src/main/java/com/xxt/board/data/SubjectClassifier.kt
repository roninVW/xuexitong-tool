package com.xxt.board.data

/**
 * 科目分类：只做关键词规则，不引入模型。
 * 学习通的课程名本身信息量足够（「高等数学A(上)」「毛泽东思想和中国特色社会主义理论体系概论」）。
 */
object SubjectClassifier {

    private val RULES: List<Pair<Regex, Subject>> = listOf(
        Regex("高等数学|高数|微积分|线性代数|概率论|数理统计|离散数学|复变|数学分析") to Subject.MATH,
        Regex("物理|力学|电磁|光学|热学|量子") to Subject.PHYSICS,
        Regex("化学|有机|无机|分析化学|物理化学") to Subject.CHEMISTRY,
        Regex("英语|大学英语|听说|读写|视听说|四级|六级|雅思|托福") to Subject.ENGLISH,
        Regex("思想道德|马克思主义|马原|毛概|毛泽东|近代史|形势与政策|思政|党史|习近平") to Subject.POLITICS,
        Regex("程序|编程|C语言|Python|Java|数据结构|算法|操作系统|数据库|计算机网络|软件工程|人工智能|机器学习|计算机") to Subject.CS,
        Regex("电路|电子|机械|土木|材料|工程制图|自动控制|信号与系统") to Subject.ENGINEERING,
        Regex("经济学|管理学|会计|金融|市场营销|微观|宏观|统计学原理") to Subject.BUSINESS,
        Regex("医学|解剖|生理|病理|药理|护理|临床") to Subject.MEDICINE,
        Regex("体育|篮球|足球|排球|游泳|健身|体测") to Subject.PE,
        Regex("军事理论|军训|国防") to Subject.MILITARY,
        Regex("美术|音乐|设计|艺术|摄影|书法") to Subject.ART,
        Regex("心理健康|职业生涯|创新创业|劳动教育|安全教育|通识") to Subject.GENERAL,
    )

    fun classify(courseName: String): Subject =
        RULES.firstOrNull { it.first.containsMatchIn(courseName) }?.second ?: Subject.UNKNOWN

    /** 标题兜底：一门课里混着别的科目时用 */
    fun classifyByTitle(title: String, fallback: Subject): Subject =
        if (fallback != Subject.UNKNOWN) fallback
        else RULES.firstOrNull { it.first.containsMatchIn(title) }?.second ?: Subject.UNKNOWN
}
