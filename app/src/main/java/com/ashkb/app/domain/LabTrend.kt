package com.ashkb.app.domain

import com.ashkb.app.data.entity.LabResult

/** 化验趋势上的一个点（`date` = ISO 日期串 yyyy-MM-dd）。 */
data class LabPoint(val date: String, val value: Float)

/**
 * 单个炎症指标的序列（趋势页方案 C）。
 *
 * @param points 升序、同日已去重
 * @param refHigh 化验单**自带**的参考上限（取范围内最新一条非空的）——为空时回退指标兜底值
 * @param unitMismatch 单位无法换算、**未纳入**的条数
 * @param unitAssumed 单位缺失、按规范单位计的条数
 *
 * ⚠️ 后两个计数不是装饰：单位缺失或陌生时，**既不静默丢弃、也不假装单位正确**——
 * CRP 的 mg/dL 与 mg/L 差 10 倍，悄悄按错单位画会凭空多出一次「骤降」，
 * 比「少画几条」危险得多。UI 需把这两个数字如实显示出来。
 */
data class LabTrend(
    val indicator: LabIndicator,
    val points: List<LabPoint>,
    val refHigh: Float? = null,
    val unitMismatch: Int = 0,
    val unitAssumed: Int = 0,
) {
    /** 画阈值线用的参考上限：优先化验单自带值，其次指标兜底值。 */
    val threshold: Float
        get() = refHigh ?: indicator.defaultRefHigh

    val isEmpty: Boolean get() = points.isEmpty()

    /** 是否需要提示「有数据未纳入」。 */
    val hasCaveat: Boolean get() = unitMismatch > 0 || unitAssumed > 0
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

            // 同一天多条（重复抽血 / 重复导入）：保留 recordedAt 最新的一条
            val prev = byDate[row.date]
            if (prev == null || row.recordedAt > prev.first.recordedAt) {
                byDate[row.date] = row to converted
            }
        }

        val ordered = byDate.entries.sortedBy { it.key }
        return LabTrend(
            indicator = indicator,
            points = ordered.map { LabPoint(it.key, it.value.second.toFloat()) },
            refHigh = ordered.mapNotNull { it.value.first.refHigh }.lastOrNull()?.toFloat(),
            unitMismatch = unitMismatch,
            unitAssumed = unitAssumed,
        )
    }
}
