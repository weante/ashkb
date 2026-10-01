package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.PlannedSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.77（批次 3b）：**漏服补发**的判定（[MissedDoses.unsettled]）。
 *
 * 锁四件事：
 *  ① done / partial / skipped 都算「已有交代」——口径与今日页「昨天还有 N 剂未记录」补记卡
 *     共用同一份定义（[AdherenceCalc.SETTLED_STATUSES]），两处不可能给出不同条数；
 *  ② 0 条就不提醒（`shouldRemind == false`）——没有漏服时发通知只会训练用户忽略通知；
 *  ③ 药名去重（同一支药漏两剂只报一次名字），条数如实；
 *  ④ 日期 / 槽位不匹配时不误配。
 */
class MissedDosesTest {

    private val yesterday = "2026-09-30"

    private fun slot(
        date: String = yesterday,
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

    private fun log(
        status: String,
        date: String = yesterday,
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

    @Test
    fun `昨天一剂未记录会报出条数与药名`() {
        val s = MissedDoses.unsettled(listOf(slot()), emptyList())
        assertTrue(s.shouldRemind)
        assertEquals(1, s.count)
        assertEquals(listOf("测试药"), s.medNames)
    }

    @Test
    fun `没有任何未记录时不提醒`() {
        val planned = listOf(slot())
        val s = MissedDoses.unsettled(planned, listOf(log(AdherenceCalc.DONE)))
        assertFalse(s.shouldRemind)
        assertEquals(0, s.count)
        assertTrue(s.medNames.isEmpty())
    }

    @Test
    fun `没有计划槽位时也不提醒（例如刚升级、快照还没物化）`() {
        val s = MissedDoses.unsettled(emptyList(), listOf(log(AdherenceCalc.DONE)))
        assertFalse(s.shouldRemind)
        assertEquals(MissedDoses.Summary.EMPTY, s)
    }

    @Test
    fun `已服与部分完成都算已结算`() {
        val planned = listOf(slot(slotKey = "08:00"), slot(slotKey = "20:00", time = "20:00"))
        val s = MissedDoses.unsettled(
            planned,
            listOf(log(AdherenceCalc.DONE, slotKey = "08:00"), log(AdherenceCalc.PARTIAL, slotKey = "20:00")),
        )
        assertFalse("部分完成也是「已有交代」，不该催补记", s.shouldRemind)
    }

    @Test
    fun `明确跳过算已结算（否则会天天催一剂用户明确不想吃的药）`() {
        val s = MissedDoses.unsettled(listOf(slot()), listOf(log(AdherenceCalc.SKIPPED)))
        assertFalse(s.shouldRemind)
    }

    @Test
    fun `未知状态仍算未记录`() {
        val s = MissedDoses.unsettled(listOf(slot()), listOf(log("something_new")))
        assertEquals(1, s.count)
    }

    @Test
    fun `同一支药漏两剂时条数计 2 而药名只报一次`() {
        val planned = listOf(
            slot(slotKey = "08:00"),
            slot(slotKey = "20:00", time = "20:00"),
        )
        val s = MissedDoses.unsettled(planned, emptyList())
        assertEquals(2, s.count)
        assertEquals(listOf("测试药"), s.medNames)
    }

    @Test
    fun `多支药按计划时刻排序 药名去重后按时间先后`() {
        val planned = listOf(
            slot(medId = "med-2", medName = "乙药", slotKey = "20:00", time = "20:00"),
            slot(medId = "med-1", medName = "甲药", slotKey = "08:00"),
        )
        val s = MissedDoses.unsettled(planned, emptyList())
        assertEquals(listOf("甲药", "乙药"), s.medNames)
    }

    @Test
    fun `日期不匹配的日志不给这一天顶账`() {
        val s = MissedDoses.unsettled(listOf(slot(date = yesterday)), listOf(log(AdherenceCalc.DONE, date = "2026-10-01")))
        assertEquals(1, s.count)
    }

    @Test
    fun `按需记录不会顶掉计划剂量`() {
        val s = MissedDoses.unsettled(listOf(slot()), listOf(log(AdherenceCalc.DONE, slotKey = null, prn = true)))
        assertEquals(1, s.count)
    }

    @Test
    fun `别的药的记录不会顶掉这一剂`() {
        val s = MissedDoses.unsettled(listOf(slot(medId = "med-1")), listOf(log(AdherenceCalc.DONE, medId = "med-2")))
        assertEquals(1, s.count)
    }
}
