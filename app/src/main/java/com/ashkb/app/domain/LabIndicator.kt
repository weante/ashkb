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
    /** 是否**无数据也占一格**（一级指标）。二级指标没数据就不占位，避免纯粹的空格子。 */
    val alwaysShow: Boolean = true,
) {
    /** 血沉：男性 0–15、女性 0–20 mm/h 为常见界值，随访多以 <20 为「不活动」。 */
    ESR("esr", "血沉", "ESR", "mm/h", ClinicalThresholds.ESR_HIGH),

    /** C 反应蛋白：常规上限多写作 <8 mg/L（部分实验室写 <5）。 */
    CRP("crp", "C反应蛋白", "CRP", "mg/L", ClinicalThresholds.CRP_HIGH),

    /**
     * 超敏 C 反应蛋白（hs-CRP）。
     *
     * **必须是独立指标，不能并入 [CRP]**（v1.0.55 实测事故）：同一份化验单常同时报 CRP 与 hs-CRP，
     * 而两者量级差一个数量级（炎症期 CRP 可 36.33 mg/L，hs-CRP 0.4 mg/L 仍属正常）。
     * v1.0.54 把 hs-CRP 的写法并进了 CRP 序列，于是按日期去重时**常常选中 hs-CRP 的小数值**——
     * 用户看到的 CRP 曲线是 0.4，而化验单上明明是 36.33（**图与单据直接矛盾，且毫无提示**）。
     * 「同一个蛋白」不等于「同一个检测」；量级不同的两项**永远不要合并成一条序列**。
     */
    HSCRP("hscrp", "超敏C反应蛋白", "hs-CRP", "mg/L", ClinicalThresholds.HSCRP_HIGH, alwaysShow = false);

    /** 该指标接受的指标名写法（**已归一**的形态）。要支持新的化验单写法就往这里加。 */
    val aliases: Set<String>
        get() = when (this) {
            ESR -> setOf(
                "血沉", "esr", "血沉(esr)", "红细胞沉降率", "红细胞沉降率(esr)",
                // v1.0.55：真实数据里最常见的写法就是「红细胞沉降率测定」（用户实测漏配）
                "红细胞沉降率测定", "红细胞沉降率(esr)测定",
                "血沉测定", "血沉定量", "esr测定",
            )
            CRP -> setOf(
                "c反应蛋白", "crp", "c-反应蛋白",
                "c反应蛋白(crp)", "c-反应蛋白(crp)",
                "c反应蛋白测定", "c反应蛋白定量", "crp测定",
                // ⚠️ 这里**刻意不含**任何「超敏 / hs-CRP」写法：hs-CRP 是独立检测、量级不同，
                // 并入会把 0.4 mg/L 画成 CRP 的 36.33（v1.0.55 实测事故，见 HSCRP 的说明）。
            )
            HSCRP -> setOf(
                "超敏c反应蛋白", "超敏crp", "hs-crp", "hscrp", "highsensitivitycrp",
                "超敏c反应蛋白(hs-crp)", "超敏c反应蛋白(crp)", "超敏c反应蛋白(hscrp)",
                "超敏c反应蛋白测定", "超敏c反应蛋白定量",
            )
        }

    /**
     * 该化验单上的指标名是否属于本指标。
     *
     * 判据是「归一 + 有限变形后**命中别名表**」，见 [nameVariants]。
     */
    fun matches(testName: String?): Boolean = nameVariants(testName).any { it in aliases }

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
            CRP, HSCRP -> when (u) {
                "mg/l" -> 1.0
                "mg/dl" -> 10.0 // 1 mg/dL = 10 mg/L
                else -> null
            }
        }
    }

    companion object {
        private val ESR_ACCEPTED_UNITS = setOf("mm/h", "mm/hr", "mm/1h", "mmh", "mm/小时")

        /**
         * 检验项目名常见的**尾限定词**。化验单上「红细胞沉降率」常写成「红细胞沉降率测定」、
         * 「C反应蛋白」常写成「C反应蛋白定量」。
         *
         * v1.0.55 教训：v1.0.54 只枚举了「血沉测定」这类写法，用户真实数据是
         * **「红细胞沉降率测定」**（复诊管理→化验里显示的正是这个名字），于是**一个点都认不出来**。
         * 靠枚举永远会漏，故改为「剥掉已知尾限定词后再比对别名表」——
         * 仍然是**有界规则 + 命中显式别名表**，不是自由 `contains` 匹配。
         */
        private val QUALIFIERS = listOf("测定", "定量", "检测", "检验", "检查", "试验", "法")

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

        /**
         * 归一后的**有限变形集合**，逐个拿去比别名表即可，不做模糊匹配。
         *
         * 变形只有两种，都是有界的：① 逐层剥掉尾限定词（`…测定` → `…` → 再剥）；
         * ② 去掉括号及其内容（`c反应蛋白(crp)` → `c反应蛋白`）。两种可叠加。
         */
        internal fun nameVariants(raw: String?): List<String> {
            val normalized = normalizeName(raw)
            if (normalized.isEmpty()) return emptyList()
            val out = LinkedHashSet<String>()
            // 原形 + 去括号形，各自再逐层剥尾限定词
            for (base in listOf(normalized, normalized.substringBefore('('))) {
                out += base
                var v = base
                while (true) {
                    val q = QUALIFIERS.firstOrNull { v.endsWith(it) && v.length > it.length } ?: break
                    v = v.removeSuffix(q)
                    out += v
                }
            }
            return out.toList()
        }
    }
}
