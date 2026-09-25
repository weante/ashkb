package com.ashkb.app.domain

import com.ashkb.app.data.entity.LabResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 化验行 → 炎症指标序列（趋势页方案 C）。
 *
 * 重点覆盖三类**容易静默出错**的口径：单位（差 10 倍会凭空多一次「骤降」）、
 * 窗口（不该画进来的别画）、同日重复（同一天两次抽血只能留一个点）。
 */
class LabTrendTest {

    private fun lab(
        date: String,
        testName: String = "血沉(ESR)",
        value: Double? = 15.0,
        unit: String? = "mm/h",
        refHigh: Double? = null,
        recordedAt: String = date + "T08:00:00",
    ) = LabResult(
        id = "$testName-$date-$recordedAt",
        date = date,
        recordedAt = recordedAt,
        testName = testName,
        value = value,
        unit = unit,
        refHigh = refHigh,
    )

    private val from = "2026-06-01"
    private val to = "2026-06-30"

    private fun esr(rows: List<LabResult>) = LabTrends.buildOne(LabIndicator.ESR, rows, from, to)
    private fun crp(rows: List<LabResult>) = LabTrends.buildOne(LabIndicator.CRP, rows, from, to)

    // ======================= 别名归一到同一序列 =======================

    @Test
    fun `不同写法的指标名会归并到同一条序列`() {
        val t = esr(
            listOf(
                lab("2026-06-01", testName = "血沉(ESR)", value = 10.0),
                lab("2026-06-10", testName = "ESR", value = 20.0),
                lab("2026-06-20", testName = "红细胞沉降率", value = 30.0),
            )
        )
        assertEquals(listOf(10f, 20f, 30f), t.points.map { it.value })
        assertEquals(listOf("2026-06-01", "2026-06-10", "2026-06-20"), t.points.map { it.date })
    }

    @Test
    fun `其它指标不会混进来`() {
        val t = esr(
            listOf(
                lab("2026-06-01", testName = "血沉(ESR)", value = 10.0),
                lab("2026-06-01", testName = "白细胞计数(WBC)", value = 6.1, unit = "10^9/L"),
                lab("2026-06-01", testName = "C反应蛋白(CRP)", value = 5.0, unit = "mg/L"),
            )
        )
        assertEquals(1, t.points.size)
    }

    // ======================= 单位 =======================

    @Test
    fun `CRP 的 mg 每分升换算成 mg 每升后入图`() {
        val t = crp(
            listOf(
                lab("2026-06-01", testName = "C反应蛋白(CRP)", value = 0.5, unit = "mg/dL"),
                lab("2026-06-10", testName = "C反应蛋白(CRP)", value = 5.0, unit = "mg/L"),
            )
        )
        // 若不换算会画成 0.5 → 5.0 的「上升」，而实际是 5.0 → 5.0 持平
        assertEquals(listOf(5f, 5f), t.points.map { it.value })
        assertEquals(0, t.unitMismatch)
        assertEquals(0, t.unitAssumed)
    }

    @Test
    fun `认不出的单位不纳入并记数`() {
        val t = crp(
            listOf(
                lab("2026-06-01", testName = "C反应蛋白(CRP)", value = 5.0, unit = "mg/L"),
                lab("2026-06-10", testName = "C反应蛋白(CRP)", value = 5.0, unit = "g/L"),
                lab("2026-06-20", testName = "C反应蛋白(CRP)", value = 5.0, unit = "IU/mL"),
            )
        )
        assertEquals(1, t.points.size)
        assertEquals("两条认不出的单位都要计数（UI 才会如实说明）", 2, t.unitMismatch)
        assertEquals(0, t.unitAssumed)
        assertTrue(t.hasCaveat)
    }

    @Test
    fun `单位缺失的按规范单位计但单独计数`() {
        val t = crp(
            listOf(
                lab("2026-06-01", testName = "C反应蛋白(CRP)", value = 5.0, unit = null),
                lab("2026-06-10", testName = "C反应蛋白(CRP)", value = 6.0, unit = "  "),
            )
        )
        assertEquals(listOf(5f, 6f), t.points.map { it.value })
        assertEquals(0, t.unitMismatch)
        assertEquals(2, t.unitAssumed)
    }

    // ======================= 窗口 / 可绘制性 / 去重 =======================

    @Test
    fun `窗口外的行不纳入（边界为闭区间）`() {
        val t = esr(
            listOf(
                lab("2026-05-31", value = 9.0),
                lab("2026-06-01", value = 10.0),
                lab("2026-06-30", value = 11.0),
                lab("2026-07-01", value = 12.0),
            )
        )
        assertEquals(listOf(10f, 11f), t.points.map { it.value })
    }

    /**
     * 回归锁（v1.0.55）：**不传窗口 = 不设界**。
     *
     * 化验是几个月一次的稀疏采样，套上「近 7/30/90 天」几乎永远是空的——用户实测正是如此
     * （默认 30 天窗口下，季度化验的项目只有一个点甚至零个点）。
     */
    @Test
    fun `不传窗口时取全部记录`() {
        val all = listOf(
            lab("2024-03-01", value = 12.0),
            lab("2024-09-01", value = 18.0),
            lab("2025-06-01", value = 20.0),
            lab("2026-06-01", value = 22.0),
        )
        val t = LabTrends.buildOne(LabIndicator.ESR, all)
        assertEquals(4, t.points.size)
        assertEquals(listOf("2024-03-01", "2024-09-01", "2025-06-01", "2026-06-01"), t.points.map { it.date })
    }

    /**
     * 回归锁（v1.0.55）：真实数据里的名字是**「红细胞沉降率测定」**，
     * v1.0.54 认不出来（用户实测：趋势里 ESR 一格永远是空的）。
     */
    @Test
    fun `红细胞沉降率测定与其它真实写法都能成图`() {
        // 刻意用 buildOne 的默认「不设界」调用（与趋势页一致），否则会被测试窗口挡掉
        val t = LabTrends.buildOne(
            LabIndicator.ESR,
            listOf(
                lab("2026-01-10", testName = "红细胞沉降率测定", value = 12.0, unit = "mm/h"),
                lab("2026-04-10", testName = "红细胞沉降率(ESR)测定", value = 18.0, unit = "mm/h"),
                lab("2026-07-10", testName = "血沉", value = 22.0, unit = "mm/h"),
            ),
        )
        assertEquals("三种写法应归为同一序列", 3, t.points.size)
        assertEquals(listOf(12f, 18f, 22f), t.points.map { it.value })
        assertTrue("不应被当成单位问题", !t.hasCaveat)
    }

    @Test
    fun `只有文字结果（无数值）的行不会变成点`() {
        val t = esr(
            listOf(
                lab("2026-06-01", value = null),
                lab("2026-06-02", value = 12.0),
            )
        )
        assertEquals(1, t.points.size)
        assertEquals("数值缺失不属于单位问题，不计入 caveat", 0, t.unitMismatch)
        assertEquals(0, t.unitAssumed)
    }

    @Test
    fun `同一天多条只留 recordedAt 最新的一条 并如实计数冲突`() {
        val t = esr(
            listOf(
                lab("2026-06-10", value = 10.0, recordedAt = "2026-06-10T07:00:00"),
                lab("2026-06-10", value = 22.0, recordedAt = "2026-06-10T15:00:00"),
                lab("2026-06-10", value = 15.0, recordedAt = "2026-06-10T11:00:00"),
            )
        )
        assertEquals(1, t.points.size)
        assertEquals(22f, t.points.first().value)
        // 被丢掉的两条与保留值都不同 → 计 2 条冲突（UI 会说「同日有 2 条不同数值」）
        assertEquals(2, t.sameDateConflict)
        assertTrue(t.hasCaveat)
    }

    @Test
    fun `同一天重复录入同一个值不算冲突`() {
        val t = esr(
            listOf(
                lab("2026-06-10", value = 15.0, recordedAt = "2026-06-10T07:00:00"),
                lab("2026-06-10", value = 15.0, recordedAt = "2026-06-10T15:00:00"),
            )
        )
        assertEquals(1, t.points.size)
        assertEquals("值相同 = 重复录入，无信息损失", 0, t.sameDateConflict)
        assertTrue(!t.hasCaveat)
    }

    /**
     * **回归锁（v1.0.55 实测事故）**：用户 2026-03-13 的化验单上 C反应蛋白(CRP) = **36.33 mg/L**，
     * 但趋势图把该日画成了 **0.4**——因为同日还有一条 hs-CRP 0.4，而 v1.0.54 把 hs-CRP 并进了 CRP，
     * 去重时选中了小数值。修法：hs-CRP 独立成项。此测锁住「CRP 就是 CRP」。
     */
    @Test
    fun `同日 CRP 与超敏 CRP 各归各的 不会互相顶掉`() {
        val rows = listOf(
            lab("2026-03-13", testName = "C反应蛋白(CRP)", value = 36.33, unit = "mg/L", refHigh = 6.0,
                recordedAt = "2026-03-14T09:00:00"),
            lab("2026-03-13", testName = "超敏C反应蛋白(hs-CRP)", value = 0.4, unit = "mg/L", refHigh = 1.0,
                recordedAt = "2026-03-14T09:00:00"),
        )
        val crp = LabTrends.buildOne(LabIndicator.CRP, rows)
        val hs = LabTrends.buildOne(LabIndicator.HSCRP, rows)

        assertEquals("CRP 必须保留 36.33（而不是被 0.4 顶掉）", listOf(36.33f), crp.points.map { it.value })
        assertEquals("hs-CRP 归自己那一格", listOf(0.4f), hs.points.map { it.value })
        assertEquals("两项互不干扰，都不算同日冲突", 0, crp.sameDateConflict)
        assertEquals(0, hs.sameDateConflict)
        // 阈值各取自己那份化验单的参考上限
        assertEquals(6f, crp.threshold)
        assertEquals(1f, hs.threshold)
    }

    @Test
    fun `输出恒为升序（即使输入乱序）`() {
        val t = esr(
            listOf(
                lab("2026-06-20", value = 30.0),
                lab("2026-06-01", value = 10.0),
                lab("2026-06-10", value = 20.0),
            )
        )
        assertEquals(listOf("2026-06-01", "2026-06-10", "2026-06-20"), t.points.map { it.date })
    }

    // ======================= 阈值 =======================

    @Test
    fun `阈值优先取化验单自带的参考上限（取最新一条非空）`() {
        val t = esr(
            listOf(
                lab("2026-06-01", value = 10.0, refHigh = 15.0),
                lab("2026-06-10", value = 10.0, refHigh = null),
                lab("2026-06-20", value = 10.0, refHigh = 28.0),
            )
        )
        assertEquals(28f, t.refHigh)
        assertEquals(28f, t.threshold)
    }

    @Test
    fun `化验单没带参考上限时回退到指标兜底值`() {
        val t = esr(listOf(lab("2026-06-01", value = 10.0, refHigh = null)))
        assertNull(t.refHigh)
        assertEquals(ClinicalThresholds.ESR_HIGH, t.threshold)
    }

    // ======================= 整体 =======================

    @Test
    fun `build 会为每个指标各出一条序列（没数据也给空序列）`() {
        val trends = LabTrends.build(listOf(lab("2026-06-01", value = 10.0)), from, to)
        assertEquals(LabIndicator.entries.size, trends.size)
        assertEquals(LabIndicator.entries.toList(), trends.map { it.indicator })
        assertTrue("ESR 有数据", !trends[0].isEmpty)
        assertTrue("CRP 无数据也必须返回空序列（UI 才能显示「暂无」而不是整格消失）", trends[1].isEmpty)
        assertTrue("hs-CRP 同理", trends[2].isEmpty)
    }

    @Test
    fun `没有任何化验行时全为空序列`() {
        val trends = LabTrends.build(emptyList(), from, to)
        assertTrue(trends.all { it.isEmpty })
        assertTrue(trends.none { it.hasCaveat })
    }
}
