package com.ashkb.app.domain

import java.time.LocalDate

/**
 * v1.0.77（批次 4）：**日期输入的唯一边界校验**（纯函数，可单测）。
 *
 * 背景（第三份审查报告 §六）：表单里的日期是自由文本，导入路径的 `normalizeDate` 只做正则匹配
 * 后直接 `%02d` 拼接——**`2026-13-45`、`2026-02-31` 这类不存在的日期会被原样写库**，
 * 之后趋势图 / PDF / 依从统计都会带着一个假日期继续跑，且没人会发现。
 *
 * 本对象把两件事分开：
 *  · [normalizeOrNull]：把宽松写法（`2026/7/31`、`2026.07.31`、`2026年7月31日`、`2026-7-1`）
 *    规范成 ISO，**并且**用 `LocalDate.of` 做真正的存在性校验（含闰年与月长）；
 *  · [parseOrNull]：只认严格 ISO，用于表单回填已有值。
 *
 * 两者失败一律返回 **null**——调用方负责给用户提示，**不得静默接受**。
 * 刻意不在这里拒绝「未来日期」：复诊计划本就可以在未来，是否合理由调用方按场景判断。
 */
object DateInput {

    /** 宽松写法：分隔符可为 `-` `.` `/` `年/月`，末尾可带「日」。 */
    private val LOOSE = Regex("^(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?$")

    // 刻意写成常量：detekt 的 MagicNumber 规则对散落字面量报错，而这几条边界本身需要被解释
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2999
    private const val MONTHS_IN_YEAR = 12
    private const val MAX_DAY_OF_MONTH = 31

    /** 严格 ISO（`YYYY-MM-DD`）→ [LocalDate]；空串、格式不符、**年份越界**、日期不存在都返回 null。 */
    fun parseOrNull(raw: String?): LocalDate? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val d = runCatching { LocalDate.parse(s) }.getOrNull() ?: return null
        // 年份范围对严格 ISO 同样适用：`LocalDate` 接受 0000 与 999999，
        // 而那种值出现在体检/化验日期里一定是脏数据（占位符、误输），不应入库
        return if (d.year in MIN_YEAR..MAX_YEAR) d else null
    }

    /** 宽松写法 → ISO 字符串；**校验不过返回 null**（不是"尽力而为地拼一个"）。 */
    fun normalizeOrNull(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        // 先试严格 ISO：表单与库里既有值走这条，最快也最严
        parseOrNull(s)?.let { return it.toString() }
        looseToIsoOrNull(s)?.let { return it }
        // v1.0.77（批次 4）：AI 常把日期与时刻写在一起（`2026-07-31 14:30` / `2026-07-31T14:30`）。
        // 旧实现用正则取**前缀**，所以能取到日期；改为严格解析后必须显式处理这种写法，
        // 否则「日期：2026-07-31 14:30」会整条被丢掉（真实回归，由 ReportImportParserTest 抓到）。
        // 只按空白或 `T` 截断，不接受其它杂质（例如「下周三 14:30」仍为 null）。
        val head = s.split(' ', 'T').firstOrNull()?.trim().orEmpty()
        if (head.isEmpty() || head == s) return null
        return parseOrNull(head)?.toString() ?: looseToIsoOrNull(head)
    }

    /** 宽松写法的解析与**存在性**校验（闰年 / 月长交给 `LocalDate.of`）。 */
    private fun looseToIsoOrNull(s: String): String? {
        val m = LOOSE.find(s) ?: return null
        val (y, mo, d) = m.destructured
        val year = y.toIntOrNull() ?: return null
        val month = mo.toIntOrNull() ?: return null
        val day = d.toIntOrNull() ?: return null
        val inRange = year in MIN_YEAR..MAX_YEAR &&
            month in 1..MONTHS_IN_YEAR &&
            day in 1..MAX_DAY_OF_MONTH
        if (!inRange) return null
        // 真正的存在性校验：LocalDate.of 对不存在的日期抛异常（如 2026-02-31、平年 2-29）
        return runCatching { LocalDate.of(year, month, day).toString() }.getOrNull()
    }
}
