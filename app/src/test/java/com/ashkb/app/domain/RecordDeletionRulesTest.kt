package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.80（批次 6）：删除记录时的**级联范围判定**与**派生警报归属判定**（纯函数）。
 *
 * 这两件事都属于「不可逆操作前的判定」：判错方向只有两种，都很糟——
 *  · 该报的没报（用户以为只删一条记录，实际连带删掉一份化验单）；
 *  · 不该删的删了（把别次发作的、用户还没看到的警报一起清掉）。
 * 故把它们从仓库层抽出来单测，而不是靠「读一遍 SQL 觉得对」。
 */
class RecordDeletionRulesTest {

    // ---- CheckupDeletion：确认框变体 ----

    @Test
    fun `名下无派生数据时走 PLAIN 变体`() {
        val counts = CheckupDeletion.Counts(labs = 0, imaging = 0, attachments = 0)
        assertEquals(CheckupDeletion.Variant.PLAIN, CheckupDeletion.variant(counts))
        assertFalse(counts.hasDerived)
    }

    @Test
    fun `任一类有条数就走 CASCADE 变体`() {
        assertEquals(
            CheckupDeletion.Variant.CASCADE,
            CheckupDeletion.variant(CheckupDeletion.Counts(labs = 1)),
        )
        assertEquals(
            CheckupDeletion.Variant.CASCADE,
            CheckupDeletion.variant(CheckupDeletion.Counts(imaging = 3)),
        )
        assertEquals(
            CheckupDeletion.Variant.CASCADE,
            CheckupDeletion.variant(CheckupDeletion.Counts(attachments = 2)),
        )
    }

    @Test
    fun `负数计数按 0 处理且不触发级联文案`() {
        // 计数来自三条独立查询，任一条算错都不该在界面上显示「-1 条化验结果」
        assertEquals(0, CheckupDeletion.normalize(-3))
        val counts = CheckupDeletion.Counts(labs = -1, imaging = 0, attachments = 0)
        assertFalse("负数不该被当成「有派生数据」", counts.hasDerived)
        assertEquals(CheckupDeletion.Variant.PLAIN, CheckupDeletion.variant(counts))
    }

    // ---- DerivedAlerts：发作窗口 ----

    @Test
    fun `窗口覆盖闭区间两端`() {
        assertTrue(DerivedAlerts.flareWindowCovers("2026-03-01", "2026-03-10", "2026-03-01", "2026-03-20"))
        assertTrue(DerivedAlerts.flareWindowCovers("2026-03-01", "2026-03-10", "2026-03-10", "2026-03-20"))
        assertTrue(DerivedAlerts.flareWindowCovers("2026-03-01", "2026-03-10", "2026-03-05", "2026-03-20"))
    }

    @Test
    fun `窗口外的报警日不算派生`() {
        assertFalse(DerivedAlerts.flareWindowCovers("2026-03-01", "2026-03-10", "2026-02-28", "2026-03-20"))
        assertFalse(DerivedAlerts.flareWindowCovers("2026-03-01", "2026-03-10", "2026-03-11", "2026-03-20"))
    }

    @Test
    fun `未缓解的发作窗口右端以今天兜底`() {
        // 仍在发作：报警日落在开始日之后、今天之前都算它派生的
        assertTrue(DerivedAlerts.flareWindowCovers("2026-03-01", null, "2026-03-08", "2026-03-09"))
        assertFalse(DerivedAlerts.flareWindowCovers("2026-03-01", null, "2026-03-10", "2026-03-09"))
    }

    @Test
    fun `日期解析失败一律不覆盖`() {
        // 宁可留下一条可能过期的警报（用户能看到、能自己确认），也不能误删别次发作的提醒
        assertFalse(DerivedAlerts.flareWindowCovers("2026-13-45", null, "2026-03-08", "2026-03-09"))
        assertFalse(DerivedAlerts.flareWindowCovers("2026-03-01", "2026-03-10", "脏数据", "2026-03-09"))
        assertFalse(DerivedAlerts.flareWindowCovers("2026-03-01", "2026-03-10", null, "2026-03-09"))
    }

    @Test
    fun `结束日早于开始日的脏窗口不覆盖任何日期`() {
        assertFalse(DerivedAlerts.flareWindowCovers("2026-03-10", "2026-03-01", "2026-03-05", "2026-03-20"))
    }

    // ---- DerivedAlerts：孤儿判定 ----

    @Test
    fun `报警日仍被别的发作覆盖时不算孤儿`() {
        val covered = DerivedAlerts.stillCoveredByOthers(
            orphans = listOf("2026-03-08"),
            remaining = listOf(DerivedAlerts.FlareWindow("2026-03-05", "2026-03-15")),
            today = "2026-03-20",
        )
        assertEquals(setOf("2026-03-08"), covered)
    }

    @Test
    fun `没有任何发作覆盖的报警日是孤儿`() {
        val covered = DerivedAlerts.stillCoveredByOthers(
            orphans = listOf("2026-03-08"),
            remaining = listOf(DerivedAlerts.FlareWindow("2026-04-01", "2026-04-05")),
            today = "2026-04-10",
        )
        assertTrue("该警报已无任何发作作为依据，应当清理", covered.isEmpty())
    }

    @Test
    fun `剩余发作里的脏窗口不参与覆盖判定`() {
        val covered = DerivedAlerts.stillCoveredByOthers(
            orphans = listOf("2026-03-08"),
            remaining = listOf(
                DerivedAlerts.FlareWindow("坏日期", "2026-03-15"),
                DerivedAlerts.FlareWindow("2026-03-01", "坏日期"),
            ),
            today = "2026-03-20",
        )
        assertTrue("解析不出来的窗口既不能证明覆盖，也不能证明不覆盖——按不覆盖处理（保留警报）", covered.isEmpty())
    }

    @Test
    fun `null 报警日被忽略而不是抛异常`() {
        val covered = DerivedAlerts.stillCoveredByOthers(
            orphans = listOf(null, "2026-03-08"),
            remaining = listOf(DerivedAlerts.FlareWindow("2026-03-01", "2026-03-10")),
            today = "2026-03-20",
        )
        assertEquals(setOf("2026-03-08"), covered)
    }
}
