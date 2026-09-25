package com.ashkb.app.domain

import com.ashkb.app.data.entity.LabResult
import kotlin.math.abs

/** 化验趋势上的一个点（`date` = ISO 日期串 yyyy-MM-dd）。 */
data class LabPoint(val date: String, val value: Float)

/**
 * 单个炎症指标的序列（趋势页方案 C）。
 *
 * @param points 升序；**同一天有多个不同数值时会全部列出**（不做「取其一」的取舍）
 * @param refHigh 化验单**自带**的参考上限（取范围内最新一条非空的）——为空时回退指标兜底值
 * @param unitMismatch 单位无法换算、**未纳入**的条数
 * @param unitAssumed 单位缺失、按规范单位计的条数（这类**会**入图，但需说明）
 * @param conflictDates 存在**多个不同数值**的日期数（这些数值**都已画出**，未丢弃任何一个）
 *
 * ⚠️ 这几个计数不是装饰：单位缺失/陌生、以及同日多值时，**既不静默丢弃、也不假装没有**——
 * CRP 的 mg/dL 与 mg/L 差 10 倍，悄悄按错单位画会凭空多出一次「骤降」。
 *
 * ⚠️ **为什么同日多值不再「取一条」**（v1.0.57 定稿）：v1.0.54–v1.0.56 一直按
 * 「同日保留最近录入的一条」去重，结果用户 2026-03-13 有两条 CRP（36.33 与 0.4），
 * 规则选中了 0.4 → **图上显示 0.4、化验单上是 36.33**，连续两版都没修对。
 * 根子在于：**「从多条里挑一条」这个动作本身就是错的**——无论挑哪条，都可能与
 * 用户手上的化验单不一致，而用户无从判断。改为**全部画出**：数值一个不丢，
 * 同一天有两个值就在同一横坐标上表现为一段竖线，配合 [conflictDates] 的说明，
 * 用户能自己看出「这天有两条记录」。要不要清理重复记录是**用户的数据决定**，不由我们替他做。
 */
data class LabTrend(
    val indicator: LabIndicator,
    val points: List<LabPoint>,
    val refHigh: Float? = null,
    val unitMismatch: Int = 0,
    val unitAssumed: Int = 0,
    val conflictDates: Int = 0,
) {
    /** 画阈值线用的参考上限：优先化验单自带值，其次指标兜底值。 */
    val threshold: Float
        get() = refHigh ?: indicator.defaultRefHigh

    val isEmpty: Boolean get() = points.isEmpty()

    /** 是否需要提示「有数据未纳入 / 同日多值」。 */
    val hasCaveat: Boolean get() = unitMismatch > 0 || unitAssumed > 0 || conflictDates > 0
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
        // date -> 该日出现过的**不同**数值（升序）；同一天多个不同值**全部保留**（见 LabTrend 的说明）
        val byDate = LinkedHashMap<String, MutableList<Double>>()
        // date -> 该日 recordedAt 最新的一条（仅用于取参考上限，不参与「取值」）
        val latestRowByDate = LinkedHashMap<String, LabResult>()

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

            val values = byDate.getOrPut(row.date) { mutableListOf() }
            // 完全相同数值只留一次（同 x 同 y，重复画看不出差别，也无信息损失）
            if (values.none { abs(it - converted) <= 1e-6 }) values.add(converted)

            val prev = latestRowByDate[row.date]
            if (prev == null || row.recordedAt > prev.recordedAt) latestRowByDate[row.date] = row
        }

        val orderedDates = byDate.keys.sorted()
        return LabTrend(
            indicator = indicator,
            // 同一天多个不同值都画出来（升序），一个不丢
            points = orderedDates.flatMap { d -> byDate.getValue(d).sorted().map { LabPoint(d, it.toFloat()) } },
            refHigh = orderedDates.mapNotNull { latestRowByDate[it]?.refHigh }.lastOrNull()?.toFloat(),
            unitMismatch = unitMismatch,
            unitAssumed = unitAssumed,
            conflictDates = byDate.count { it.value.size > 1 },
        )
    }
}
