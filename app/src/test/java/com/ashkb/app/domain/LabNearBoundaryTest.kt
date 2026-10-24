package com.ashkb.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.10 审查裁决项 5：「临界」判据（[ClinicalThresholds.labNearBoundary]）。
 *
 * 维护者 2026-10-24 裁决的四个口径，逐条钉在这里：
 * 1. 量程基准 = 参考区间宽度 × 5%（只有上限时用上限 × 5%）；
 * 2. 越界优先——已越界又落在容差内时仍算偏高/偏低，临界只在**范围内**的值上判；
 * 3. 临界**不计入**异常计数（本测试只测判据本身，计数口径在 UI 侧）；
 * 4. 脏数据（下限 ≥ 上限）不判临界。
 */
class LabNearBoundaryTest {

    // ---- 口径 1：量程基准 ----

    @Test
    fun `双侧区间用区间宽度的百分之五作为容差`() {
        // 0–20 的区间宽度是 20，5% = 1.0；19.5 距上限 0.5 → 临界
        assertTrue("19.5 距 0–20 的上限只有 0.5，应判临界", ClinicalThresholds.labNearBoundary(19.5, 0.0, 20.0))
        // 19.0 距上限 1.0，正好落在容差边界上（≤ 而非 <）→ 临界
        assertTrue("恰好等于容差边界应算临界", ClinicalThresholds.labNearBoundary(19.0, 0.0, 20.0))
        // 18.9 距上限 1.1 → 超出容差
        assertFalse("18.9 已离开容差带", ClinicalThresholds.labNearBoundary(18.9, 0.0, 20.0))
    }

    @Test
    fun `靠近下限也算临界`() {
        // 0–20 的 5% = 1.0；0.5 距下限 0.5 → 临界
        assertTrue("0.5 距下限只有 0.5", ClinicalThresholds.labNearBoundary(0.5, 0.0, 20.0))
        assertFalse("1.1 已离开下限容差带", ClinicalThresholds.labNearBoundary(1.1, 0.0, 20.0))
    }

    @Test
    fun `只有上限时用上限的百分之五作为容差`() {
        // refHigh = 8（CRP 常见上限），5% = 0.4；7.7 距上限 0.3 → 临界
        assertTrue(ClinicalThresholds.labNearBoundary(7.7, null, 8.0))
        assertFalse("7.5 距上限 0.5，超出 0.4 的容差", ClinicalThresholds.labNearBoundary(7.5, null, 8.0))
    }

    @Test
    fun `只有下限时用下限的百分之五作为容差`() {
        // refLow = 40，5% = 2.0；41.5 距下限 1.5 → 临界
        assertTrue(ClinicalThresholds.labNearBoundary(41.5, 40.0, null))
        assertFalse("42.5 距下限 2.5，超出 2.0 的容差", ClinicalThresholds.labNearBoundary(42.5, 40.0, null))
    }

    @Test
    fun `量纲差异很大时判据依然成立`() {
        // 血糖 mmol/L 区间 4.0–6.0，5% = 0.1：5.95 临界、5.85 不是
        assertTrue(ClinicalThresholds.labNearBoundary(5.95, 4.0, 6.0))
        assertFalse(ClinicalThresholds.labNearBoundary(5.85, 4.0, 6.0))
        // 血小板 100–300（×10⁹/L），5% = 10：295 临界、280 不是
        assertTrue(ClinicalThresholds.labNearBoundary(295.0, 100.0, 300.0))
        assertFalse(ClinicalThresholds.labNearBoundary(280.0, 100.0, 300.0))
    }

    // ---- 口径 2：越界优先 ----

    @Test
    fun `越界的值不算临界`() {
        // 20.2 已越过上限，虽然距上限只有 0.2（在 1.0 容差内），仍应算偏高
        assertFalse("越界优先：已越界就不是临界", ClinicalThresholds.labNearBoundary(20.2, 0.0, 20.0))
        // 下限同理
        assertFalse("低于下限不算临界", ClinicalThresholds.labNearBoundary(-0.2, 0.0, 20.0))
    }

    @Test
    fun `区间正中的值不算临界`() {
        assertFalse(ClinicalThresholds.labNearBoundary(10.0, 0.0, 20.0))
    }

    // ---- 口径 4：脏数据与缺失 ----

    @Test
    fun `没有任何参考范围时判不了临界`() {
        assertFalse("两边都没有范围，本地判不了", ClinicalThresholds.labNearBoundary(5.0, null, null))
    }

    @Test
    fun `数值缺失时判不了临界`() {
        // 只有文字结果（如"阴性"）的行没有数值可比
        assertFalse(ClinicalThresholds.labNearBoundary(null, 0.0, 20.0))
    }

    @Test
    fun `下限不小于上限的脏数据不判临界`() {
        // 区间宽度为 0：若按 5% 算，容差也是 0，会把"恰好等于上限"也算成临界；
        // 这种录入错误本身该由数据校验拦，判据这里一律返回 false，避免整段被标成临界。
        assertFalse(ClinicalThresholds.labNearBoundary(20.0, 20.0, 20.0))
        // 下限 > 上限（录入颠倒）同样不判
        assertFalse(ClinicalThresholds.labNearBoundary(10.0, 20.0, 0.0))
    }
}
