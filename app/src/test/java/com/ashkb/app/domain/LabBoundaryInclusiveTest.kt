package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.11（缺陷工单 A06）：**参考边界是否包含**决定判读用 `>` 还是 `>=`。
 *
 * 缺陷原状：化验单写 `<5` 时解析器只存了 `refHigh = 5`，判读一律 `value > refHigh`，
 * 于是 5 这个**真实的偏高值**被判成正常——它从趋势图与 PDF 的异常列表里消失。
 * `>10` 与 `≥10` 同理（`>10` 表示 10 本身已偏低，却被放宽成 `≥10`）。
 *
 * 判读口径必须与 `HealthRepository.saveLabResult` 完全一致：那边用
 * `if (refHighInclusive) value > refHigh else value >= refHigh`，本测试逐条对拍。
 *
 * 为什么不测 `saveLabResult` 本身：它要 Room + Android Context（Robolectric 起库）。
 * 判读表达式抽在同一个纯函数口径上，这里钉住「严格/包含」两支的边界行为，
 * `RecordDeletionCascadeTest` 那边再验整条写库链路。
 */
class LabBoundaryInclusiveTest {

    /**
     * 判读口径（与 `saveLabResult` 同源）。
     *
     * 这里刻意**复刻**而不是调用仓库私有逻辑：`saveLabResult` 的判读内联在一个 suspend 函数里，
     * 抽成公共函数会改动它的事务边界。复刻的风险由 `LabNearBoundaryWiringTest` 的源码断言兜住
     * （它检查 UI 与 PDF 都走同一函数、不自造比较）。
     */
    private fun judge(value: Double, refLow: Double?, refHigh: Double?, lowInc: Boolean, highInc: Boolean): String {
        val overHigh = refHigh != null && if (highInc) value > refHigh else value >= refHigh
        val underLow = refLow != null && if (lowInc) value < refLow else value <= refLow
        return when {
            overHigh -> "high"
            underLow -> "low"
            refHigh != null || refLow != null -> "normal"
            else -> "null"
        }
    }

    @Test
    fun `严格上界下 值等于上界即偏高`() {
        // `<5`：5 本身已偏高
        assertEquals("high", judge(5.0, null, 5.0, lowInc = true, highInc = false))
        // 4.99 仍正常（没到边界）
        assertEquals("normal", judge(4.99, null, 5.0, lowInc = true, highInc = false))
    }

    @Test
    fun `包含上界下 值等于上界仍正常`() {
        // `≤5`：5 正常
        assertEquals("normal", judge(5.0, null, 5.0, lowInc = true, highInc = true))
        // 5.01 才算偏高
        assertEquals("high", judge(5.01, null, 5.0, lowInc = true, highInc = true))
    }

    @Test
    fun `严格下界下 值等于下界即偏低`() {
        // `>10`：10 本身已偏低
        assertEquals("low", judge(10.0, 10.0, null, lowInc = false, highInc = true))
        assertEquals("normal", judge(10.01, 10.0, null, lowInc = false, highInc = true))
    }

    @Test
    fun `包含下界下 值等于下界仍正常`() {
        // `≥10`：10 正常
        assertEquals("normal", judge(10.0, 10.0, null, lowInc = true, highInc = true))
        assertEquals("low", judge(9.99, 10.0, null, lowInc = true, highInc = true))
    }

    @Test
    fun `旧数据默认包含边界 行为与 v1_2_10 一致`() {
        // 存量行 / 旧备份没有新列 → 迁移 DEFAULT 1 → 包含边界，语义不倒退
        assertEquals("normal", judge(5.0, null, 5.0, lowInc = true, highInc = true))
        assertEquals("high", judge(6.0, null, 5.0, lowInc = true, highInc = true))
    }

    @Test
    fun `临界判据与越界判据同源 不会自相矛盾`() {
        // `<5` 时 5 是越界（high）→ 不能再被判成「临界」
        assertTrue(
            "严格上界下 5 必须算越界",
            ClinicalThresholds.labNearBoundary(5.0, null, 5.0, refLowInclusive = true, refHighInclusive = false).not(),
        )
        // `≤5` 时 5 在范围内、且距边界 0 → 应判临界
        assertTrue(
            "包含上界下 5 在范围内且贴边，应判临界",
            ClinicalThresholds.labNearBoundary(5.0, null, 5.0, refLowInclusive = true, refHighInclusive = true),
        )
    }

    @Test
    fun `临界判据在严格下界下同样不把越界值当临界`() {
        // `>10` 时 10 越界（low）
        assertTrue(
            "严格下界下 10 越界，不能判临界",
            ClinicalThresholds.labNearBoundary(10.0, 10.0, null, refLowInclusive = false, refHighInclusive = true).not(),
        )
        // `≥10` 时 10 在范围内且贴边 → 临界
        assertTrue(
            "包含下界下 10 贴边，应判临界",
            ClinicalThresholds.labNearBoundary(10.0, 10.0, null, refLowInclusive = true, refHighInclusive = true),
        )
    }
}
