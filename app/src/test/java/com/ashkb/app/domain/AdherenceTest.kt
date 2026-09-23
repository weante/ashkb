package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.0.48：依从口径的唯一实现（[AdherenceCalc]）。
 *
 * 这些断言同时是**报表口径的回归锁**——公式此前在 `ReportRepository` 内联 4 遍
 * （概览的用药/补剂、周月报的用药/补剂），本次收拢到 [AdherenceCalc]。
 * 若有人再改动这里，报表与药单「用药记录」会同时变，不会出现两处不一致。
 */
class AdherenceTest {

    // ---- ratePct ----

    @Test
    fun `partial counts as half`() {
        // 8 完成 + 2 部分 → (8 + 1) / 10 = 90%
        assertEquals(90, AdherenceCalc.ratePct(done = 8, partial = 2, total = 10))
    }

    @Test
    fun `no check-in yields zero not hundred`() {
        // 关键语义：没有记录 ≠ 完全依从
        assertEquals(0, AdherenceCalc.ratePct(done = 0, partial = 0, total = 0))
    }

    @Test
    fun `all skipped yields zero`() {
        assertEquals(0, AdherenceCalc.ratePct(done = 0, partial = 0, total = 5))
    }

    @Test
    fun `all done yields hundred`() {
        assertEquals(100, AdherenceCalc.ratePct(done = 7, partial = 0, total = 7))
    }

    /** 与收拢前的内联公式逐例对齐（`((done + partial*0.5) / total * 100).toInt()`） */
    @Test
    fun `matches the previous inline formula`() {
        val cases = listOf(
            Triple(1, 1, 3),
            Triple(2, 1, 3),
            Triple(0, 1, 1),
            Triple(3, 0, 3),
            Triple(1, 0, 3),
            Triple(4, 3, 9),
            Triple(0, 0, 1),
        )
        for ((d, p, t) in cases) {
            val expected = ((d + p * 0.5) / t * 100).toInt()
            assertEquals("done=$d partial=$p total=$t", expected, AdherenceCalc.ratePct(d, p, t))
        }
    }

    // ---- summarize ----

    @Test
    fun `summarize counts each status and derives rate`() {
        val s = AdherenceCalc.summarize(listOf("done", "done", "done", "partial", "skipped"))
        assertEquals(3, s.done)
        assertEquals(1, s.partial)
        assertEquals(1, s.skipped)
        assertEquals(5, s.total)
        assertEquals(70, s.ratePct) // (3 + 0.5) / 5 = 70%
    }

    @Test
    fun `summarize is order independent`() {
        val a = AdherenceCalc.summarize(listOf("done", "skipped", "partial"))
        val b = AdherenceCalc.summarize(listOf("partial", "done", "skipped"))
        assertEquals(a, b)
    }

    @Test
    fun `summarize empty is all zeros`() {
        val s = AdherenceCalc.summarize(emptyList())
        assertEquals(0, s.total)
        assertEquals(0, s.ratePct)
    }

    /**
     * 未知状态**计入 total 但不计完成**——取值偏向保守：既不算依从，也不让比率虚高。
     * 写入侧只产生三态，此项为防御（旧备份 / 未来新增状态）。
     */
    @Test
    fun `unknown status lowers the rate instead of inflating it`() {
        val s = AdherenceCalc.summarize(listOf("done", "something_new"))
        assertEquals(1, s.done)
        assertEquals(0, s.partial)
        assertEquals(0, s.skipped)
        assertEquals(2, s.total)
        assertEquals(50, s.ratePct)
    }

    @Test
    fun `status constants match the stored values`() {
        // 存库值（实体注释：done / partial / skipped）——改了这里就是数据格式变更
        assertEquals("done", AdherenceCalc.DONE)
        assertEquals("partial", AdherenceCalc.PARTIAL)
        assertEquals("skipped", AdherenceCalc.SKIPPED)
    }
}
