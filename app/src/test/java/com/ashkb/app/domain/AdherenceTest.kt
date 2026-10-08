package com.ashkb.app.domain

import android.content.Context
import com.ashkb.app.data.entity.MedicationLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.48：依从口径的唯一实现（[AdherenceCalc]）。
 *
 * 这些断言同时是**报表口径的回归锁**——公式此前在 `ReportRepository` 内联 4 遍
 * （概览的用药/补剂、周月报的用药/补剂），本次收拢到 [AdherenceCalc]。
 * 若有人再改动这里，报表与药单「用药记录」会同时变，不会出现两处不一致。
 *
 * v1.0.76（批次 3a）：指标改称「记录内完成度」，这里锁三件事——① 分母是**已记录条数**
 * （不是计划剂量数）；② 零分母**没有**百分比、也没有 90/70 判定；③ 按需（PRN）记录
 * 整体移出该指标，只单独计数。
 *
 * i18n（v1.2.6）：`ClinicalThresholds.completionLabel` 改为返回 `@StringRes Int?`，
 * 三档断言改为**渲染后的中文文本**，故整类走 Robolectric。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class AdherenceTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    /** 与界面同口径：先取资源 id，再渲染成当前语言文本。 */
    private fun label(ratePct: Int?): String? =
        ClinicalThresholds.completionLabel(ratePct)?.let { ctx.getString(it) }

    /** 造一条用药记录：默认是计划打卡（`prnFlag = false`），`prn = true` 即按需用药的打卡。 */
    private fun log(status: String, prn: Boolean = false, id: String = "mlog-test") = MedicationLog(
        id = id,
        date = "2026-09-01",
        recordedAt = "2026-09-01T08:00:00",
        medId = "med-test",
        medKey = "测试药",
        medName = "测试药",
        doseSnapshot = "1 片",
        status = status,
        prnFlag = prn,
    )

    // ---- ratePct（纯数值口径，补剂仍走这条） ----

    @Test
    fun `partial counts as half`() {
        // 8 完成 + 2 部分 → (8 + 1) / 10 = 90%
        assertEquals(90, AdherenceCalc.ratePct(done = 8, partial = 2, total = 10))
    }

    @Test
    fun `no check-in yields zero not hundred`() {
        // 旧数值口径：分母为 0 时给 0（界面要区分「没有记录」请用 Completion.ratePct 的 null）
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

    // ---- 记录内完成度：分母 = 已记录条数（v1.0.76（批次 3a）） ----

    @Test
    fun `零分母时不给百分比`() {
        val c = AdherenceCalc.completion(emptyList())
        assertEquals(0, c.total)
        assertFalse(c.hasRecords)
        // null 而不是 0 或 100：从「没有数据」里编出任何百分比都是假的
        assertNull(c.ratePct)
    }

    @Test
    fun `零分母时不给判定`() {
        // 90/70 的判定必须由「有记录」打底，没有数据就没有达标 / 需关注可谈
        assertNull(label(AdherenceCalc.completion(emptyList()).ratePct))
        assertNull(label(null))
    }

    @Test
    fun `零分母不等于全跳过`() {
        // 两者算出来都是 0 分，但一个是「没有数据」、一个是「记了，都跳过」——界面必须能区分
        assertNull(AdherenceCalc.completion(emptyList()).ratePct)
        assertEquals(0, AdherenceCalc.completion(listOf(log(AdherenceCalc.SKIPPED))).ratePct)
    }

    @Test
    fun `PRN 记录不计入完成度`() {
        val logs = listOf(
            log(AdherenceCalc.DONE, prn = true),
            log(AdherenceCalc.SKIPPED, prn = true),
        )
        val c = AdherenceCalc.completion(logs)
        // 按需药没有「计划剂量」，一条都不该进分母（否则一天多次打卡会把完成度抬高 / 稀释）
        assertEquals(0, c.total)
        assertNull(c.ratePct)
        assertEquals(2, AdherenceCalc.prnCount(logs))
    }

    @Test
    fun `PRN 与计划打卡混合时只统计计划打卡`() {
        val logs = listOf(
            log(AdherenceCalc.DONE), log(AdherenceCalc.PARTIAL),
            log(AdherenceCalc.DONE, prn = true), log(AdherenceCalc.SKIPPED, prn = true),
        )
        val c = AdherenceCalc.completion(logs)
        assertEquals(1, c.done)
        assertEquals(1, c.partial)
        assertEquals(0, c.skipped)
        assertEquals(2, c.total)
        assertEquals(75, c.ratePct) // (1 + 0.5) / 2
        assertEquals(2, AdherenceCalc.prnCount(logs))
    }

    @Test
    fun `分母是记录条数而不是计划剂量数`() {
        // 这就是指标改名的全部理由：漏记（完全没打卡）不进分母，所以「3 条全完成」与
        // 「一个月 30 剂只记了 3 剂」都算出 100%——它只能读作「记录内完成度」
        val c = AdherenceCalc.completion(
            listOf(log(AdherenceCalc.DONE), log(AdherenceCalc.DONE), log(AdherenceCalc.DONE)),
        )
        assertEquals(3, c.total)
        assertEquals(100, c.ratePct)
    }

    @Test
    fun `完成部分跳过混合按现有公式取整`() {
        val c = AdherenceCalc.completion(
            listOf(
                log(AdherenceCalc.DONE), log(AdherenceCalc.DONE), log(AdherenceCalc.DONE),
                log(AdherenceCalc.PARTIAL), log(AdherenceCalc.SKIPPED),
            ),
        )
        assertEquals(3, c.done)
        assertEquals(1, c.partial)
        assertEquals(1, c.skipped)
        assertEquals(5, c.total)
        assertEquals(70, c.ratePct) // (3 + 0.5) / 5
    }

    /**
     * 未知状态**计入 total 但不计完成**——取值偏向保守：既不算依从，也不让比率虚高。
     * 写入侧只产生三态，此项为防御（旧备份 / 未来新增状态）。
     */
    @Test
    fun `未知状态计入分母但不计完成`() {
        val c = AdherenceCalc.completionByStatus(listOf(AdherenceCalc.DONE, "something_new"))
        assertEquals(1, c.done)
        assertEquals(0, c.partial)
        assertEquals(0, c.skipped)
        assertEquals(2, c.total)
        assertEquals(50, c.ratePct)
    }

    @Test
    fun `完成度汇总与顺序无关`() {
        val a = AdherenceCalc.completionByStatus(listOf("done", "skipped", "partial"))
        val b = AdherenceCalc.completionByStatus(listOf("partial", "done", "skipped"))
        assertEquals(a, b)
    }

    // ---- 阈值边界（v1.0.76（批次 3a）：只有有记录时才有档位） ----

    @Test
    fun `刚好 90 分判达标`() {
        assertEquals("达标", label(90))
    }

    @Test
    fun `刚好 70 分判待改善`() {
        assertEquals("待改善", label(70))
    }

    @Test
    fun `89 分与 69 分各降一档`() {
        assertEquals("待改善", label(89))
        assertEquals("需干预", label(69))
        assertEquals("需干预", label(0))
    }

    /**
     * i18n 新增守卫：三档必须落在**三条不同**的资源上。
     *
     * 改成资源 id 后把三档误指向同一条资源编译器不报错，界面会静默退化成「怎么算都是同一句话」。
     */
    @Test
    fun `三档判定指向三条不同资源`() {
        val resIds = listOf(90, 70, 0).map { ClinicalThresholds.completionLabel(it) }
        assertEquals("三档不得重复指向同一资源", resIds.size, resIds.toSet().size)
    }

    @Test
    fun `状态常量与存库值一致`() {
        // 存库值（实体注释：done / partial / skipped）——改了这里就是数据格式变更
        assertEquals("done", AdherenceCalc.DONE)
        assertEquals("partial", AdherenceCalc.PARTIAL)
        assertEquals("skipped", AdherenceCalc.SKIPPED)
    }
}
