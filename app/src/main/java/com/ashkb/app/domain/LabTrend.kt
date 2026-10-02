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
 *
 * ⚠️ 这两个计数不是装饰：单位缺失/陌生时，**既不静默丢弃、也不假装没有**——
 * CRP 的 mg/dL 与 mg/L 差 10 倍，悄悄按错单位画会凭空多出一次「骤降」。
 *
 * ⚠️ **为什么同日多值既不「取一条」、也不再挂文字说明**（v1.0.58 定稿）：
 * v1.0.54–v1.0.56 一直按「同日保留最近录入的一条」去重，结果用户 2026-03-13 有两条 CRP
 * （36.33 与 0.4），规则选中了 0.4 → **图上显示 0.4、化验单上是 36.33**，连续两版都没修对。
 * 根子在于：**「从多条里挑一条」这个动作本身就是错的**——无论挑哪条，都可能与
 * 用户手上的化验单不一致，而用户无从判断。v1.0.57 改为**全部画出**：数值一个不丢，
 * 同一天有两个值就表现为同一横坐标上的一段竖线。
 * v1.0.58 进一步撤掉当时加的那行「有 N 个日期存在多条不同数值」——**图本身已经把这件事说清楚了**，
 * 再挂一行字只会把 2 列小多图的格子撑高、破坏整齐（用户实测反馈）。
 * 要不要清理重复记录是**用户的数据决定**，不由我们替他做。
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

    /**
     * 是否有**未纳入**的数据需要说明。
     *
     * 同日多值不算：那些值**都已经画出来了**，图上一段竖线一目了然，不需要额外文字。
     */
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
        // date -> 该日出现过的**不同**数值（升序）；同一天多个不同值**全部保留**（见 LabTrend 的说明）
        val byDate = LinkedHashMap<String, MutableList<Double>>()
        // date -> (recordedAt, 该行 refHigh **按同一 factor 换算后**的值)
        // 只记**被采纳**的行（单位认不出的行不画点，它的参考上限自然也不该拿来画阈值线）。
        // v1.1.1：此前这里只存整行、取 refHigh 时原样 `.toFloat()`，与数据点差一个 factor。
        val latestRefByDate = LinkedHashMap<String, Pair<String, Float?>>()

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

            // v1.1.1（HIGH-3）：参考上限必须与数据点用**同一个 factor** 换算。
            // 此前 `refHigh` 原样入图：一张「CRP 0.5 mg/dL、refHigh 0.5」的化验单会画出
            // **5.0 的点配 0.5 的阈值线**，还报「1 项超标」——在一条专门回答"炎症有没有升高"的图上。
            // 反向（refHigh 存 mg/L、点按 mg/dL）则低报。factor 为 null 只可能是"单位缺失"那一支
            // （认不出的单位已在上面 continue），此时点也按规范单位计，故乘 1.0 与点同口径。
            val convertedRef = row.refHigh?.let { (it * (factor ?: 1.0)).toFloat() }
            val prev = latestRefByDate[row.date]
            if (prev == null || row.recordedAt > prev.first) {
                latestRefByDate[row.date] = row.recordedAt to convertedRef
            }
        }

        val orderedDates = byDate.keys.sorted()
        return LabTrend(
            indicator = indicator,
            // 同一天多个不同值都画出来（升序），一个不丢
            points = orderedDates.flatMap { d -> byDate.getValue(d).sorted().map { LabPoint(d, it.toFloat()) } },
            // v1.1.1：取的是换算后的参考上限（换算见上面的 convertedRef）。
            // 残余（明说）：跨单位变更的序列仍只有**一条**阈值线（取范围内最新一条非空值），
            // 早于那次变更的点会与这条线不同源。要按点各自成线需要把阈值下移到每个点上，
            // 属于图表语义改动，本批次不动。
            refHigh = orderedDates.mapNotNull { latestRefByDate[it]?.second }.lastOrNull(),
            unitMismatch = unitMismatch,
            unitAssumed = unitAssumed,
        )
    }
}
