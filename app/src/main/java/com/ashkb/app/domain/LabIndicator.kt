package com.ashkb.app.domain

/**
 * 客观炎症指标目录（趋势页方案 C）。
 *
 * **为什么需要这一层**：`lab_results.test_name` 是**自由文本**——没有指标编码列、没有目录表，
 * 而 `LabResultDao.observeTrend` 是**精确等值**匹配。于是 `"血沉(ESR)"` 与 `"ESR"` 会被当成
 * 两条互不相干的序列；同一个人的化验单从不同医院 / 不同 AI 导入模板出来，名字并不一致。
 * 要「按指标取序列」，就必须先做一次**名称归一**。这里就是唯一的那处归一。
 *
 * **归一规则**（[normalizeName]）：去首尾空白、转小写、全角括号与冒号折半角、去掉所有空白。
 * **刻意不做模糊匹配**（不用 `contains`）：宁可漏配也不要配错——配错的后果是把别的指标
 * 画进来（**静默画错**，正是本项目最忌讳的一类）；漏配只表现为「暂无数据」，可被发现。
 * 需要覆盖新写法时，往 [aliases] 里加，不要改成模糊匹配。
 *
 * **单位**统一到 [canonicalUnit]，[unitFactor] 给出原单位 → 规范单位的乘数。
 * 无法识别的单位**不纳入**（[LabTrend] 会记录条数并由 UI 如实说明），
 * 绝不「大概按规范单位算」：CRP 的 mg/dL 与 mg/L 差 10 倍，混画会凭空多出一次「骤降」。
 */
enum class LabIndicator(
    /** 稳定标识：用于持久化 / 内部比对，**不要跟着文案改** */
    val code: String,
    /** 中文名（与 `SkipReason` / `StopReason` 的枚举文案惯例一致，直接内联中文） */
    val label: String,
    /** 英文缩写（化验单上常见，展示时与中文名并列，便于对上报告） */
    val abbr: String,
    /** 规范单位 */
    val canonicalUnit: String,
    /** 参考上限兜底值（化验单自带 `refHigh` 时优先用它，见 [LabTrend.threshold]） */
    val defaultRefHigh: Float,
) {
    /** 血沉：男性 0–15、女性 0–20 mm/h 为常见界值，随访多以 <20 为「不活动」。 */
    ESR("esr", "血沉", "ESR", "mm/h", ClinicalThresholds.ESR_HIGH),

    /** C 反应蛋白：常规上限多写作 <8 mg/L（部分实验室写 <5）。 */
    CRP("crp", "C反应蛋白", "CRP", "mg/L", ClinicalThresholds.CRP_HIGH);

    /** 该指标接受的指标名写法（**已归一**的形态）。要支持新的化验单写法就往这里加。 */
    val aliases: Set<String>
        get() = when (this) {
            ESR -> setOf(
                "血沉", "esr", "血沉(esr)", "红细胞沉降率", "红细胞沉降率(esr)",
                "血沉测定", "血沉定量", "esr测定",
            )
            CRP -> setOf(
                "c反应蛋白", "crp", "c-反应蛋白",
                "c反应蛋白(crp)", "c-反应蛋白(crp)",
                "c反应蛋白测定", "c反应蛋白定量", "crp测定",
                // 超敏 CRP 是同一蛋白的高敏检测、单位同为 mg/L，故并入同一序列；
                // 其参考上限通常更严，由「最新一条自带 refHigh」承担（见 LabTrend.threshold），
                // 因此不会拿常规界值去判高敏结果。
                "超敏c反应蛋白", "超敏c反应蛋白(crp)", "超敏c反应蛋白(hs-crp)",
                "超敏crp", "hs-crp", "hscrp",
            )
        }

    /** 该化验单上的指标名是否属于本指标。 */
    fun matches(testName: String?): Boolean = normalizeName(testName) in aliases

    /**
     * 原单位 → 规范单位的乘数。
     *
     * @return `null` 表示**无法识别**（含单位缺失）——调用方必须据此把它排除或单独计数，
     *   不得当作 1.0 直接使用。
     */
    fun unitFactor(rawUnit: String?): Double? {
        val u = normalizeUnit(rawUnit)
        if (u.isEmpty()) return null
        return when (this) {
            ESR -> if (u in ESR_ACCEPTED_UNITS) 1.0 else null
            CRP -> when (u) {
                "mg/l" -> 1.0
                "mg/dl" -> 10.0 // 1 mg/dL = 10 mg/L
                else -> null
            }
        }
    }

    companion object {
        private val ESR_ACCEPTED_UNITS = setOf("mm/h", "mm/hr", "mm/1h", "mmh", "mm/小时")

        fun byCode(code: String?): LabIndicator? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        /**
         * 指标名归一：去首尾空白、转小写、全角括号与冒号折半角、**去掉所有空白**。
         * 最后一步同时处理半角与全角空格（`isWhitespace()` 覆盖 `\u3000`）。
         */
        fun normalizeName(raw: String?): String = raw.orEmpty()
            .trim()
            .lowercase()
            .replace('（', '(')
            .replace('）', ')')
            .replace('：', ':')
            .filterNot { it.isWhitespace() }

        /** 单位归一：同 [normalizeName]，并容忍结尾的点与大小写（`mg/L.` / `MG/L`）。 */
        fun normalizeUnit(raw: String?): String = normalizeName(raw).trimEnd('.')
    }
}
