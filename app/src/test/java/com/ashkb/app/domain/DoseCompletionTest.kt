package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.PlannedSlot
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.77（批次 3b）：**计划剂量口径**的完成度（[AdherenceCalc.DoseCompletion] / [AdherenceCalc.doseCompletion]）。
 *
 * 这套口径存在的全部意义是「**漏记会掉分**」——记录口径（[AdherenceCalc.completion]）的分母是已记录条数，
 * 一剂都没打卡时它反而给不出任何百分比。这里锁五件事：
 *  ① 零计划**不给百分比**（`null`，不是 0%）；
 *  ② 计划了却完全没记录 = 0%（这才是真漏服）；
 *  ③ 完成 / 部分 / 跳过 / 未记录四段能对上计划总数；
 *  ④ 跳过**不计漏服**（用户明确写过原因的剂量不该被当成漏服反复催）；
 *  ⑤ 日期不匹配时**不误配**（跨零点记错日的日志不能给隔壁那天顶账）。
 */
class DoseCompletionTest {

    /** 一条计划槽位：默认 `2026-09-30 08:00`、药 `med-1`、槽位键与时刻同值（口服口径）。 */
    private fun slot(
        date: String = "2026-09-30",
        medId: String = "med-1",
        medName: String = "测试药",
        slotKey: String = "08:00",
        time: String = "08:00",
    ) = PlannedSlot(
        id = "pslot-$date-$medId-$slotKey",
        date = date,
        medId = medId,
        medKey = "test",
        medName = medName,
        slotKey = slotKey,
        slotTime = time,
        doseSnapshot = "1 片",
        createdAt = "2026-09-29T00:00:00",
    )

    /** 一条用药记录：默认写在与 [slot] 同一天、同一槽位。 */
    private fun log(
        status: String,
        date: String = "2026-09-30",
        medId: String? = "med-1",
        slotKey: String? = "08:00",
        prn: Boolean = false,
    ) = MedicationLog(
        id = "mlog-$date-$medId-$slotKey-$status",
        date = date,
        recordedAt = "${date}T08:00:00",
        medId = medId,
        medKey = "test",
        medName = "测试药",
        doseSnapshot = "1 片",
        slotKey = slotKey,
        status = status,
        prnFlag = prn,
    )

    // ---- 零计划 vs 全漏服 ----

    @Test
    fun `零计划不给百分比（不是 0 分）`() {
        val d = AdherenceCalc.doseCompletion(emptyList(), listOf(log(AdherenceCalc.DONE)))
        assertEquals(0, d.planned)
        assertFalse(d.hasPlan)
        // null 而不是 0：没有计划快照可算，与「计划了却一剂没吃」是两件事
        assertNull(d.ratePct)
    }

    @Test
    fun `有记录但没有计划时也拿不到计划口径的百分比`() {
        // 记录口径（分母 = 记录条数）在这种情况下会给 100%，计划口径必须仍是无数据
        val logs = listOf(log(AdherenceCalc.DONE), log(AdherenceCalc.DONE))
        assertEquals(100, AdherenceCalc.completion(logs).ratePct)
        assertNull(AdherenceCalc.doseCompletion(emptyList(), logs).ratePct)
    }

    @Test
    fun `全漏服是 0 分且逐条计进 missed`() {
        val d = AdherenceCalc.doseCompletion(listOf(slot(slotKey = "08:00"), slot(slotKey = "20:00", time = "20:00")), emptyList())
        assertEquals(2, d.missed)
        assertEquals(0, d.done)
        assertEquals(2, d.planned)
        assertEquals(0, d.ratePct)
    }

    // ---- 混合 ----

    @Test
    fun `完成部分跳过未记录四段之和等于计划数`() {
        val planned = listOf(
            slot(slotKey = "08:00"), slot(slotKey = "12:00", time = "12:00"),
            slot(slotKey = "16:00", time = "16:00"), slot(slotKey = "20:00", time = "20:00"),
        )
        val logs = listOf(
            log(AdherenceCalc.DONE, slotKey = "08:00"),
            log(AdherenceCalc.PARTIAL, slotKey = "12:00"),
            log(AdherenceCalc.SKIPPED, slotKey = "16:00"),
            // 20:00 没有记录 → 未记录
        )
        val d = AdherenceCalc.doseCompletion(planned, logs)
        assertEquals(1, d.done)
        assertEquals(1, d.partial)
        assertEquals(1, d.skipped)
        assertEquals(1, d.missed)
        assertEquals(4, d.planned)
        assertEquals("(1 + 0.5) / 4 = 37%", 37, d.ratePct)
    }

    @Test
    fun `部分完成按半剂计入分子`() {
        // 一剂计划、只完成一半 → 0.5 / 1 = 50%
        val one = AdherenceCalc.doseCompletion(listOf(slot()), listOf(log(AdherenceCalc.PARTIAL)))
        assertEquals(50, one.ratePct)
        // 两剂计划、其中一剂只完成一半 → 0.5 / 2 = 25%（部分完成不等于「算一剂」）
        val two = AdherenceCalc.doseCompletion(
            listOf(slot(slotKey = "08:00"), slot(slotKey = "20:00", time = "20:00")),
            listOf(log(AdherenceCalc.PARTIAL, slotKey = "08:00")),
        )
        assertEquals(25, two.ratePct)
    }

    @Test
    fun `同日同药多剂按槽位键分别配对`() {
        val planned = listOf(slot(slotKey = "08:00"), slot(slotKey = "20:00", time = "20:00"))
        // 只有 20:00 那一剂有记录：不能因为「同一天有记录」就把两剂都算完成
        val d = AdherenceCalc.doseCompletion(planned, listOf(log(AdherenceCalc.DONE, slotKey = "20:00")))
        assertEquals(1, d.done)
        assertEquals(1, d.missed)
        assertEquals(50, d.ratePct)
    }

    @Test
    fun `不同药同一天互不顶账`() {
        val planned = listOf(slot(medId = "med-1"), slot(medId = "med-2", medName = "另一种药"))
        val d = AdherenceCalc.doseCompletion(planned, listOf(log(AdherenceCalc.DONE, medId = "med-2")))
        assertEquals(1, d.done)
        assertEquals(1, d.missed)
    }

    // ---- 跳过：不计完成、也不计漏服 ----

    @Test
    fun `跳过不计漏服但仍在分母里`() {
        val planned = listOf(slot(slotKey = "08:00"), slot(slotKey = "20:00", time = "20:00"))
        val logs = listOf(
            log(AdherenceCalc.SKIPPED, slotKey = "08:00"),
            log(AdherenceCalc.DONE, slotKey = "20:00"),
        )
        val d = AdherenceCalc.doseCompletion(planned, logs)
        assertEquals("跳过不是漏服", 0, d.missed)
        assertEquals(1, d.done)
        assertEquals("跳过也不是完成", 0, d.partial)
        assertEquals(1, d.skipped)
        // 分母仍是计划剂量数（那剂确实被计划过）：若跳过不计入分母，这里会是 100%
        assertEquals(2, d.planned)
        assertEquals(50, d.ratePct)
    }

    @Test
    fun `全部跳过时未记录为 0`() {
        val planned = listOf(slot(slotKey = "08:00"))
        val d = AdherenceCalc.doseCompletion(planned, listOf(log(AdherenceCalc.SKIPPED)))
        assertEquals(0, d.missed)
        assertEquals(1, d.skipped)
    }

    @Test
    fun `未知状态记为漏服（保守）`() {
        // 既没被确认为完成，也不该从分母里消失——宁可算成未记录让用户看到
        val d = AdherenceCalc.doseCompletion(listOf(slot()), listOf(log("something_new")))
        assertEquals(1, d.missed)
        assertEquals(0, d.done)
    }

    // ---- 日期不匹配：绝不误配 ----

    @Test
    fun `日志记在别的日期时不误配（仍算未记录）`() {
        val planned = listOf(slot(date = "2026-09-30"))
        // 跨零点打卡把日志写到次日：那剂昨天仍然没人记录
        val d = AdherenceCalc.doseCompletion(planned, listOf(log(AdherenceCalc.DONE, date = "2026-10-01")))
        assertEquals(1, d.missed)
        assertEquals(0, d.done)
    }

    @Test
    fun `前一天已完成的记录不会给今天顶账`() {
        val planned = listOf(slot(date = "2026-10-01"))
        val d = AdherenceCalc.doseCompletion(planned, listOf(log(AdherenceCalc.DONE, date = "2026-09-30")))
        assertEquals(1, d.missed)
    }

    @Test
    fun `按需记录（slot_key 为空）不会与计划槽位配对`() {
        val planned = listOf(slot())
        val prn = log(AdherenceCalc.DONE, slotKey = null, prn = true)
        val d = AdherenceCalc.doseCompletion(planned, listOf(prn))
        assertEquals("PRN 记录没有计划槽位可配，计划剂量仍算未记录", 1, d.missed)
    }

    // ---- 到点判定（调用方据此滤掉「今天还没到点」的剂量） ----

    @Test
    fun `未到点的剂量不算未记录（由 isDue 滤掉）`() {
        val now = LocalDateTime.of(2026, 9, 30, 10, 0)
        val due = slot(slotKey = "08:00")
        val notDue = slot(slotKey = "20:00", time = "20:00")
        assertTrue(AdherenceCalc.isDue(due, now))
        assertFalse(AdherenceCalc.isDue(notDue, now))
        val d = AdherenceCalc.doseCompletion(listOf(due, notDue).filter { AdherenceCalc.isDue(it, now) }, emptyList())
        assertEquals("只算已到点的那一剂", 1, d.planned)
    }

    @Test
    fun `脏时刻按已到点处理（宁可多算一条）`() {
        val dirty = slot(slotKey = "25:99", time = "25:99")
        assertTrue(AdherenceCalc.isDue(dirty, LocalDateTime.of(2026, 9, 30, 10, 0)))
    }

    @Test
    fun `跳过数由三态反推且不为负`() {
        val planned = listOf(slot(slotKey = "08:00"))
        // 日志多了一条本不该存在的完成记录（例如计划行缺失）时，反推值兜底为 0 而不是负数
        val logs = listOf(log(AdherenceCalc.DONE), log(AdherenceCalc.DONE, slotKey = "09:00"))
        val d = AdherenceCalc.doseCompletion(planned, logs)
        assertEquals(0, d.skipped)
    }

    @Test
    fun `已有口径不受影响（回归锁）`() {
        // 批次 3b 只**新增**口径：记录内完成度与 PRN 计数的语义一字未动
        val logs = listOf(log(AdherenceCalc.DONE), log(AdherenceCalc.SKIPPED), log(AdherenceCalc.DONE, prn = true, slotKey = null))
        val c = AdherenceCalc.completion(logs)
        assertEquals(2, c.total)
        assertEquals(1, c.done)
        assertEquals(1, c.skipped)
        assertEquals(50, c.ratePct)
        assertEquals(1, AdherenceCalc.prnCount(logs))
    }

    @Test
    fun `已结算状态集合就是三态`() {
        assertEquals(
            setOf(AdherenceCalc.DONE, AdherenceCalc.PARTIAL, AdherenceCalc.SKIPPED),
            AdherenceCalc.SETTLED_STATUSES,
        )
    }
}
