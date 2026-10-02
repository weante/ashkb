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

    // ---- v1.0.87（批次 12）：历史流的状态口径（跳过也是一次交代） ----

    /**
     * 详情弹层的历史流取的是**已结算**状态，不是只取 done。
     *
     * 为什么锁在纯函数而不是继续写死在 SQL 里：批次 12 之前 `observeHistoryFor` 把
     * `status = 'done'` 写死在 SQL 字符串里，卡片一旦能写 skipped，那条记录就会
     * 「记进库了、历史里查无此条」。现在过滤值由调用方传 [AdherenceCalc.SETTLED_STATUSES]，
     * 本用例钉住这份集合就是全部三种结算态。
     */
    @Test
    fun `已结算状态含已服与跳过`() {
        assertEquals(setOf("done", "partial", "skipped"), AdherenceCalc.SETTLED_STATUSES)
    }

    /**
     * **回归锁**：历史里「已服」的条数不能因为新增跳过而变。
     *
     * 现网老数据只有 done 行（跳过是批次 12 才写得出来的）。老数据的显示结果必须与改动前
     * 完全一致——过滤条件从 `status = 'done'` 放宽到三态后，若有人把它改成「含未知状态」，
     * 这里会立刻失败。
     */
    @Test
    fun `老数据只有 done 时历史条数与改动前一致`() {
        val legacy = listOf(
            log("slog-legacy-1", "done"),
            log("slog-legacy-2", "done"),
            log("slog-legacy-3", "done"),
        )

        val kept = legacy.filter { it.status in AdherenceCalc.SETTLED_STATUSES }

        assertEquals("老数据（只有 done）必须一条不少地留下", 3, kept.size)
        assertEquals(legacy.map { it.id }, kept.map { it.id })
    }

    /** 未知 / 空状态既不是 done 也不是跳过：不得混进历史（否则会凭空多出用户没记过的行）。 */
    @Test
    fun `未知状态不进历史流`() {
        val logs = listOf(log("slog-x-1", "done"), log("slog-x-2", "mystery"))

        assertEquals(listOf("slog-x-1"), logs.filter { it.status in AdherenceCalc.SETTLED_STATUSES }.map { it.id })
    }

    /** 造 n 条记录：id 递增即可，本测试只关心行数与顺序，不关心日期。 */
    private fun history(n: Int): List<SupplementLog> = (1..n).map { i -> log("slog-$i", "done") }

    private fun log(id: String, status: String) = SupplementLog(
        id = id, date = "2026-10-01", recordedAt = "2026-10-01T08:00:00",
        supId = "sup-1", supKey = "sup-1", supName = "测试补剂",
        doseSnapshot = "1 粒", status = status,
        takenAt = if (status == "done") "2026-10-01T08:00:00" else null,
    )
}
