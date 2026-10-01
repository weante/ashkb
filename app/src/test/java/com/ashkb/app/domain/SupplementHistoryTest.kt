package com.ashkb.app.domain

import com.ashkb.app.data.entity.SupplementLog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.81（批次 7）：补剂详情弹层「最近服用记录」的取数规则（近 90 天窗口 / 最多 30 行 / 只截断不重排）。
 *
 * 为什么这些规则要单独锁住：弹层的行数上限与仓库层的查询窗口是**同一条规则的两半**
 * （过滤在 SQL 里、截断在这个纯函数里）。任一半单独改动，用户数出来的行数就和他看到的
 * 「近 90 天已服 N 次」对不上——那个 N 是窗口内的真实条数，不是截断后的行数。
 */
class SupplementHistoryTest {

    @Test
    fun `窗口起点是今天往前 90 天`() {
        // 钉死具体日期而不是「等于 today-90」：后者是把实现抄一遍，改错方向也测不出来
        assertEquals("2026-07-03", SupplementHistory.fromDate(LocalDate.parse("2026-10-01")))
        assertEquals(90L, SupplementHistory.WINDOW_DAYS)
    }

    @Test
    fun `最多只列出 30 行`() {
        assertEquals(30, SupplementHistory.MAX_ROWS)
        assertEquals(
            SupplementHistory.MAX_ROWS,
            SupplementHistory.visible(history(SupplementHistory.MAX_ROWS + 1)).size,
        )
    }

    @Test
    fun `截断只切尾巴且不重排`() {
        val logs = history(SupplementHistory.MAX_ROWS + 5)

        val visible = SupplementHistory.visible(logs)

        assertEquals("第一行必须仍是最新的那条（顺序来自 SQL，不在这里再排一次）", logs.first().id, visible.first().id)
        assertEquals("最后一行应是第 30 条", logs[SupplementHistory.MAX_ROWS - 1].id, visible.last().id)
    }

    @Test
    fun `截断与否看窗口内的真实条数`() {
        assertFalse(SupplementHistory.isTruncated(SupplementHistory.MAX_ROWS))
        assertTrue(SupplementHistory.isTruncated(SupplementHistory.MAX_ROWS + 1))
        assertFalse("没有记录时不该提示「还有更多」", SupplementHistory.isTruncated(0))
    }

    @Test
    fun `空列表截断后仍是空`() {
        assertTrue(SupplementHistory.visible(emptyList()).isEmpty())
    }

    /** 造 n 条记录：id 递增即可，本测试只关心行数与顺序，不关心日期。 */
    private fun history(n: Int): List<SupplementLog> = (1..n).map { i ->
        SupplementLog(
            id = "slog-$i", date = "2026-10-01", recordedAt = "2026-10-01T08:00:00",
            supId = "sup-1", supKey = "sup-1", supName = "测试补剂",
            doseSnapshot = "1 粒", status = "done", takenAt = "2026-10-01T08:00:00",
        )
    }
}
