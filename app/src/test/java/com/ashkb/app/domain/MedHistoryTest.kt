package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedicationLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.10（审查第二批 M8）：药单详情弹层「用药记录」的取数规则（90 天窗口 / 最多 60 行 / 只截断不重排）。
 *
 * 为什么这些规则要单独锁住：
 *  1. 弹层走 `SheetColumn`（`Column` + `verticalScroll`，**没有虚拟化**），是唯一一处
 *     「数据量随天数线性增长、却要一次性全部组合」的列表。上限一旦被删掉，症状是**卡顿**，
 *     不会报错，也不会被任何编译期或 lint 规则拦住——只能靠本文件钉住。
 *  2. 上限与窗口是**同一条规则的两半**（窗口在 SQL / 仓库层，截断在这个纯函数里）。
 *     任一半单独改动，用户数出来的行数就和他看到的完成度对不上。
 *  3. 最要紧的一条：**截断只作用于列表，绝不能作用于统计**。把被截断的样本喂给
 *     `AdherenceCalc`，用户会看到一个凭空变好的完成度——这比不显示更糟。
 */
class MedHistoryTest {

    @Test
    fun `窗口是 90 天`() {
        // 钉死具体数值而不是「等于 today-90」类的抄实现式断言：仓库层默认窗口就是 90，
        // 这里改了而那边没改，截断上限（按 90 天最坏情况定）就失去依据。
        assertEquals(90, MedHistory.MED_HISTORY_WINDOW_DAYS)
    }

    @Test
    fun `上限是 60 行且确实会截断`() {
        assertEquals(60, MedHistory.MAX_ROWS)
        assertEquals(
            "超过上限时只留上限条",
            MedHistory.MAX_ROWS,
            MedHistory.visible(history(MedHistory.MAX_ROWS + 1)).size,
        )
    }

    @Test
    fun `一天三剂的 90 天最坏情况远低于不设限的行数`() {
        // 真实最坏输入：BID/Q8H 使一天最多 3 剂，90 天 ≈ 270 行。上限必须显著小于它，
        // 否则这个上限就是摆设（这正是 M8 的原始指控：一次组合近 270 行）。
        val worstCase = history(90 * 3)

        assertEquals("90 天 × 一天 3 剂 = 270 条原始记录", 270, worstCase.size)
        assertEquals(60, MedHistory.visible(worstCase).size)
    }

    @Test
    fun `截断只切尾巴且不重排`() {
        val logs = history(MedHistory.MAX_ROWS + 5)

        val visible = MedHistory.visible(logs)

        assertEquals("第一行必须仍是最新的那条（顺序来自仓库层，不在这里再排一次）", logs.first().id, visible.first().id)
        assertEquals("最后一行应是第 60 条", logs[MedHistory.MAX_ROWS - 1].id, visible.last().id)
    }

    @Test
    fun `截断与否看窗口内的真实条数`() {
        assertFalse(MedHistory.isTruncated(MedHistory.MAX_ROWS))
        assertTrue(MedHistory.isTruncated(MedHistory.MAX_ROWS + 1))
        assertFalse("没有记录时不该提示「还有更多」", MedHistory.isTruncated(0))
    }

    @Test
    fun `空列表截断后仍是空`() {
        assertTrue(MedHistory.visible(emptyList()).isEmpty())
    }

    /**
     * **回归锁（本批最重要的一条）**：截断不得改变统计口径。
     *
     * 界面把全量 `logs` 交给 `AdherenceCalc.completion`，只把 [MedHistory.visible] 的结果
     * 交给列表。若有人图省事改成「先截断再传给 AdherenceCalc」，完成度会被截断样本抬高：
     * 下面这组数据里前 60 条全是 done、第 61 条起是 skipped，一旦截断在前，分母就从 70 掉到 60。
     */
    @Test
    fun `截断不参与完成度计算`() {
        val settled = history(60) + (61..70).map { log("mlog-$it", AdherenceCalc.SKIPPED) }

        val onFull = AdherenceCalc.completion(settled)
        val onTruncated = AdherenceCalc.completion(MedHistory.visible(settled))

        assertEquals("全量口径：70 条里 60 条完成", 70, onFull.total)
        assertEquals(60, onFull.done)
        assertEquals(10, onFull.skipped)
        assertTrue(
            "这条断言就是「不许先截断再统计」的证据：被截断的样本会算出更高的完成度",
            onTruncated.total < onFull.total,
        )
        assertEquals(100, onTruncated.ratePct)
        assertEquals(85, onFull.ratePct)
    }

    /** 造 n 条记录：id 递增即可，本测试只关心行数与顺序，不关心日期。 */
    private fun history(n: Int): List<MedicationLog> =
        (1..n).map { i -> log("mlog-$i", AdherenceCalc.DONE) }

    private fun log(id: String, status: String) = MedicationLog(
        id = id, date = "2026-10-01", recordedAt = "2026-10-01T08:00:00",
        medId = "med-1", medKey = "med-1", medName = "测试药物",
        doseSnapshot = "1 片", status = status,
        takenAt = if (status == AdherenceCalc.DONE) "2026-10-01T08:00:00" else null,
        reason = if (status == AdherenceCalc.SKIPPED) "forgot" else null,
    )
}
