package com.ashkb.app.domain

import com.ashkb.app.data.entity.Supplement

/**
 * B11（v1.0.38）：营养素「每日上限警示」。
 *
 * ⚠️ **刻意不内置任何医学上限数值**——上限由用户在补剂里自行填写（医嘱 / 营养师给的值），
 * App 只做「当日累计 vs 上限」的算术比较与提示，不代替专业判断。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object SupplementLimits {

    /**
     * 单次剂量单位与上限单位的关系（v1.2.13，审查报告 §十 第 2 条）。
     *
     * 三态的划分依据是「能不能拿到证据」，不是「像不像」：
     *  · [SAME] 两边都有单位且归一化后相等——可以直接比；
     *  · [ASSUMED] 上限没填单位——按「与单次剂量同单位」处理，这是修复前的隐含假设，
     *    保留它是为了不让存量补剂的上限警示一朝消失；界面必须把这层假设说出来（见 [UnitAssumption]）；
     *  · [MISMATCH] 两边都有单位但不相等——**判不了**，不是「没超」。
     *
     * 归一化只做 trim + 忽略大小写（`mg` / `MG` / ` mg ` 视为同一）。**刻意不做同义词映射**
     * （`mg` ↔ `毫克`、`IU` ↔ `国际单位`）：本地没有权威换算表，猜错一次就是在剂量上给错结论，
     * 与 `ui/checkup/LabDraftRules.kt` 里「单位与参考范围明显不一致不判」同一取舍。
     */
    enum class UnitMatch { SAME, ASSUMED, MISMATCH }

    /**
     * 每日服用次数：优先按 `times` 的**槽位数**（真实格式是 JSON 数组，如 `["08:00","20:00"]`），
     * 否则按 frequency 粗判（daily=1 / bid=2 / tid=3）。
     *
     * v1.2.10（审查 CRITICAL-1 / §5-MEDIUM-4）两处修正：
     * ① KDoc 此前写 `"08:00,20:00"`（CSV）—— **格式是假的**，下一个维护者会照抄，
     *    这正是 CRITICAL-1 的成因（写入侧 JSON、读取侧 CSV）；
     * ② 实现此前是 `times?.split(',')?.count { isNotBlank() }`。它**看起来**对——`["08:00","20:00"]`
     *    切出 2 段恰等于长度——但空数组 `"[]"` 切出的是 1 段（`"[]"` 本身非空），
     *    于是「设了频次 bid、没设具体时刻」的补剂会被算成**每天 1 次**而不是 2 次，
     *    当日累计剂量被低估一半 → 超上限警示该响的时候不响。现在走 [ScheduleCalc.parseTimes]。
     */
    fun dosesPerDay(sup: Supplement): Int {
        val slots = ScheduleCalc.parseTimes(sup.times).size
        if (slots > 0) return slots
        return when (sup.frequency.lowercase()) {
            "bid", "twice_daily" -> 2
            "tid" -> 3
            else -> 1
        }
    }

    /** 当日累计剂量 = 单次剂量 × 每日次数；缺任一要素返回 null（表示无法判断，不警示）。 */
    fun dailyTotal(sup: Supplement): Double? {
        val per = sup.doseAmount ?: return null
        if (per <= 0) return null
        return per * dosesPerDay(sup)
    }

    /**
     * 单次剂量单位与上限单位的关系；两边都没填时不作判断（返回 null）。
     *
     * 只在**上限已填且为正**时才有意义——上限没填就不存在比较，也就没有单位问题。
     */
    fun unitMatch(sup: Supplement): UnitMatch? {
        if (sup.dailyMax?.takeIf { it > 0 } == null) return null
        val doseUnit = normalizedUnit(sup.doseUnit) ?: return null
        val maxUnit = normalizedUnit(sup.dailyMaxUnit) ?: return UnitMatch.ASSUMED
        return if (doseUnit == maxUnit) UnitMatch.SAME else UnitMatch.MISMATCH
    }

    /**
     * 是否超上限。
     *
     * @return null = 无法判断（未填上限 / 未填单次剂量 / 上限非正 / **单次剂量与上限单位不同**）；
     *         true/false = 是否超限
     *
     * v1.2.13：单位不同时返回 **null 而不是 false**。false 的含义是「比过了，没超」，
     * 而单位不同根本没比——把它当 false 会让 `1 粒 > 1000 mg` 这种写法永远静默通过，
     * 用户在界面上看到的正是「没警告 = 安全」。
     */
    fun exceedsDailyMax(sup: Supplement): Boolean? {
        if (unitMatch(sup) == UnitMatch.MISMATCH) return null
        val max = sup.dailyMax?.takeIf { it > 0 } ?: return null
        val total = dailyTotal(sup) ?: return null
        return total > max
    }

    /**
     * 单次剂量的单位（展示用）；缺失时返回空串。
     *
     * 返回**原样大小写**：比较走 [normalizedUnit]，展示走这里。用户填 `IU` 就该显示 `IU`，
     * 把它降成 `iu` 是把归一化的实现细节漏到界面上。
     */
    fun unitLabel(sup: Supplement): String = trimmedUnit(sup.doseUnit) ?: ""

    /**
     * 上限的单位（展示用）：填了就用填的，没填就沿用单次剂量单位——**与 [exceedsDailyMax] 的
     * [UnitMatch.ASSUMED] 分支必须是同一口径**，否则界面写的单位和算出来的结果会互相矛盾。
     */
    fun limitUnitLabel(sup: Supplement): String =
        trimmedUnit(sup.dailyMaxUnit) ?: trimmedUnit(sup.doseUnit) ?: ""

    /** 展示用：只 trim，不改大小写。 */
    private fun trimmedUnit(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /** 比较用：trim + 忽略大小写；空串与纯空白视为未填。 */
    private fun normalizedUnit(raw: String?): String? = trimmedUnit(raw)?.lowercase()
}
