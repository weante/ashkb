package com.ashkb.app.domain

import com.ashkb.app.data.entity.LabResult
import kotlin.math.abs

/** 化验趋势上的一个点（`date` = ISO 日期串 yyyy-MM-dd）。 */
data class LabPoint(val date: String, val value: Float)

/**
 * 单个炎症指标的序列（趋势页方案 C）。
 *
 * @param points 升序、同日已去重
 * @param refHigh 化验单**自带**的参考上限（取范围内最新一条非空的）——为空时回退指标兜底值
 * @param unitMismatch 单位无法换算、**未纳入**的条数
 * @param unitAssumed 单位缺失、按规范单位计的条数（这类**会**入图，但需说明）
 * @param sameDateConflict 同一指标同一天出现**不同数值**的条数（已保留最近录入的一条，其余未显示）
 *
 * ⚠️ 后面三个计数不是装饰：单位缺失/陌生、以及同日冲突时，**既不静默丢弃、也不假装没有**——
 * CRP 的 mg/dL 与 mg/L 差 10 倍，悄悄按错单位画会凭空多出一次「骤降」；
 * 同日两个不同数值悄悄取其一，正是 v1.0.55 那次「图是 0.4、化验单是 36.33」的事故形态。
 * UI 需把这三个数字如实显示出来。
 */
data class LabTrend(
    val indicator: LabIndicator,
    val points: List<LabPoint>,
    val refHigh: Float? = null,
    val unitMismatch: Int = 0,
    val unitAssumed: Int = 0,
    val sameDateConflict: Int = 0,
) {
    /** 画阈值线用的参考上限：优先化验单自带值，其次指标兜底值。 */
    val threshold: Float
        get() = refHigh ?: indicator.defaultRefHigh

    val isEmpty: Boolean get() = points.isEmpty()

    /** 是否需要提示「有数据未纳入 / 有冲突」。 */
    val hasCaveat: Boolean get() = unitMismatch > 0 || unitAssumed > 0 || sameDateConflict > 0
}

/**
 * 把 `lab_results` 行按指标归拢成序列。
 *
 * 纯函数：不碰数据库、不依赖当前时间（窗口由调用方传 `from` / `to`），因此可单测。
 */
object LabTrends {

    fun build(
        rows: List<LabResult>,
        from: String? = null,
        to: String? = null,
        indicators: List<LabIndicator> = LabIndicator.entries,
    ): List<LabTrend> = indicators.map { buildOne(it, rows, from, to) }

    /**
     * @param from / @param to 日期窗口（闭区间）；**传 `null` 表示不设界**。
     *   趋势页对化验**不设界**（v1.0.55）：化验是几个月一次的稀疏采样，
     *   套上「近 7/30/90 天」只会几乎永远是空的——用户实测正是如此。
     */
    fun buildOne(
        indicator: LabIndicator,
        rows: List<LabResult>,
        from: String? = null,
        to: String? = null,
    ): LabTrend {
        var unitMismatch = 0
        var unitAssumed = 0
        var sameDateConflict = 0
        // date -> (该日最新的一条, 换算后的值)
        val byDate = LinkedHashMap<String, Pair<LabResult, Double>>()

        for (row in rows) {
            if (!indicator.matches(row.testName)) continue
            val raw = row.value ?: continue                       // 只有文字结果（如「阴性」）画不了折线
            if (from != null && row.date < from) continue          // 窗口外
            if (to != null && row.date > to) continue

            val factor = indicator.unitFactor(row.unit)
            val converted: Double
            if (factor != null) {
                converted = raw * factor
            } else if (row.unit.isNullOrBlank()) {
                // 单位缺失：按规范单位计，但**单独计数**并在 UI 说明
                unitAssumed++
                converted = raw
            } else {
                // 单位存在却认不出：宁可不画，也不要按错单位画
                unitMismatch++
                continue
            }

            // 同一天多条（重复抽血 / 重复导入）：保留 recordedAt 最新的一条。
            // 值相同 = 重复录入（无信息损失，不计）；值不同 = **必须计数**并在 UI 说明，
            // 否则就会出现「图上取了一个值、化验单上写另一个值」这种无提示的矛盾。
            val prev = byDate[row.date]
            if (prev == null) {
                byDate[row.date] = row to converted
            } else {
                if (abs(prev.second - converted) > 1e-6) sameDateConflict++
                if (row.recordedAt > prev.first.recordedAt) byDate[row.date] = row to converted
            }
        }

        val ordered = byDate.entries.sortedBy { it.key }
        return LabTrend(
            indicator = indicator,
            points = ordered.map { LabPoint(it.key, it.value.second.toFloat()) },
            refHigh = ordered.mapNotNull { it.value.first.refHigh }.lastOrNull()?.toFloat(),
            unitMismatch = unitMismatch,
            unitAssumed = unitAssumed,
            sameDateConflict = sameDateConflict,
        )
    }
}
