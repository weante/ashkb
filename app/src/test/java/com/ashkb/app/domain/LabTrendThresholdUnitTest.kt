package com.ashkb.app.domain

import com.ashkb.app.data.entity.LabResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.1（HIGH-3）：**化验趋势的阈值线必须与数据点用同一个单位系数换算**。
 *
 * 缺陷形态：数据点在 `LabTrends.buildOne` 里按 `indicator.unitFactor(row.unit)` 换算到规范单位，
 * 而参考上限（`refHigh`）被原样 `.toFloat()` 拿去做阈值线（还喂给 `TrendChart` 的
 * `overCount = vals.count { it > th }`）。
 *
 * 失败场景（真实化验单形态）：CRP 0.5 mg/dL、refHigh 0.5 → 点换算成 5.0 mg/L，阈值线仍是 0.5 →
 * 图上画出「一个 5.0 的点配一条 0.5 的线」并报「1 项超标」——**在一条专门告诉 AS 患者炎症指标
 * 有没有升高的图上**。反向（refHigh 存 mg/L、点按 mg/dL）则低报。
 *
 * 纯 JVM：`LabTrends.buildOne` 不碰数据库、不依赖当前时间。
 */
class LabTrendThresholdUnitTest {

    private fun crp(
        date: String,
        value: Double,
        unit: String?,
        refHigh: Double?,
    ) = LabResult(
        id = "lab-$date-$value-$unit",
        date = date, recordedAt = "${date}T08:00:00",
        testName = "CRP", value = value, unit = unit, refHigh = refHigh,
    )

    @Test
    fun `mgdL 的 refHigh 与数据点一起换算成 mgL`() {
        val t = LabTrends.buildOne(LabIndicator.CRP, listOf(crp("2026-06-01", 0.5, "mg/dL", 0.5)))

        assertEquals(listOf(LabPoint("2026-06-01", 5.0f)), t.points)
        assertEquals(
            "阈值线必须与点同单位：旧实现在这里给出 0.5（10 倍差），图上会显示「5.0 超标」",
            5.0f,
            t.threshold,
            1e-6f,
        )
        assertTrue("换算正确后 5.0 不该被判为超过 5.0 的线", t.points.none { it.value > t.threshold })
    }

    @Test
    fun `规范单位的 refHigh 保持不变`() {
        val t = LabTrends.buildOne(LabIndicator.CRP, listOf(crp("2026-06-01", 12.0, "mg/L", 8.0)))

        assertEquals(8.0f, t.threshold, 1e-6f)
        assertTrue("12 > 8：这一条确实超标", t.points.first().value > t.threshold)
    }

    @Test
    fun `单位缺失时 refHigh 按规范单位原样使用`() {
        val t = LabTrends.buildOne(LabIndicator.CRP, listOf(crp("2026-06-01", 12.0, null, 8.0)))

        assertEquals("单位缺失时点按规范单位计，阈值同口径", 8.0f, t.threshold, 1e-6f)
        assertEquals(1, t.unitAssumed)
    }

    @Test
    fun `单位认不出的行连同它的 refHigh 一起被丢弃`() {
        val t = LabTrends.buildOne(LabIndicator.CRP, listOf(crp("2026-06-01", 0.5, "g/L", 0.5)))

        assertTrue("认不出的单位不画点", t.points.isEmpty())
        assertEquals(1, t.unitMismatch)
        assertEquals(
            "没有点就不该出现「自带阈值」——否则图上会有一条来历不明的线",
            LabIndicator.CRP.defaultRefHigh,
            t.threshold,
            1e-6f,
        )
    }

    @Test
    fun `阈值取范围内最新一天的自带值`() {
        val t = LabTrends.buildOne(
            LabIndicator.CRP,
            listOf(
                crp("2026-06-01", 0.5, "mg/dL", 0.5),   // 旧化验单：mg/dL
                crp("2026-06-20", 6.0, "mg/L", 8.0),    // 新化验单：mg/L
            ),
        )

        assertEquals("取最新一天的自带参考上限（已换算）", 8.0f, t.threshold, 1e-6f)
        assertEquals(listOf(5.0f, 6.0f), t.points.map { it.value })
    }
}
