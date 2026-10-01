package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.74：跨零点补记的判定（「昨天还有 N 剂未记录」）。
 *
 * 锁三件事：
 *  ① **归属日是槽位那天**（昨天），不是「现在」那天——补记写入的日期由它决定；
 *  ② 已结算（已服 / 已跳过）与「还没到点」都不算未记录——否则卡片天天挂着催用户补一剂他没漏的药；
 *  ③ 按需用药（PRN）没有计划，永远不出现在补记卡里。
 */
class PendingDosesTest {

    private fun med(
        id: String = "med-1",
        name: String = "测试药",
        times: String = """["23:55"]""",
        frequency: String = "DAILY",
        startDate: String = "2026-01-01",
    ) = Medication(
        id = id, name = name, nameKey = "test", medClass = "OTHER",
        route = "oral", dose = "1 片", frequency = frequency,
        takeTimes = times, startDate = startDate,
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    private val slotDate = LocalDate.of(2026, 9, 30)

    @Test
    fun `昨天 23 55 未记录 会在次日被列为待补记 且归属日是昨天`() {
        val pending = PendingDoses.unsettledOn(
            date = slotDate,
            meds = listOf(med()),
            now = LocalDateTime.of(2026, 10, 1, 0, 28),
            isSettled = { _, _ -> false },
        )
        assertEquals(1, pending.size)
        assertEquals("23:55", pending[0].slotTime)
        assertEquals("归属日必须是槽位那天（昨天）", slotDate, pending[0].date)
        assertEquals("med-1", pending[0].medId)
    }

    @Test
    fun `已结算的槽位不再出现在待补记里（已服或已跳过都算）`() {
        val pending = PendingDoses.unsettledOn(
            date = slotDate,
            meds = listOf(med()),
            now = LocalDateTime.of(2026, 10, 1, 0, 28),
            isSettled = { _, _ -> true },
        )
        assertTrue("已结算不该催补记", pending.isEmpty())
    }

    @Test
    fun `还没到点的槽位不算未记录`() {
        // 用「今天」的 23:55 在 00:28 检查：该剂还没到点
        val pending = PendingDoses.unsettledOn(
            date = LocalDate.of(2026, 10, 1),
            meds = listOf(med()),
            now = LocalDateTime.of(2026, 10, 1, 0, 28),
            isSettled = { _, _ -> false },
        )
        assertTrue("未到点的计划不该出现在补记卡", pending.isEmpty())
    }

    @Test
    fun `按需用药 PRN 没有计划 不出现在补记卡`() {
        val pending = PendingDoses.unsettledOn(
            date = slotDate,
            meds = listOf(med(frequency = "PRN")),
            now = LocalDateTime.of(2026, 10, 1, 0, 28),
            isSettled = { _, _ -> false },
        )
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `多剂按计划时刻排序 且只看未结算的那些`() {
        val m = med(times = """["08:00","23:55","12:30"]""")
        val settled = setOf("12:30")
        val pending = PendingDoses.unsettledOn(
            date = slotDate,
            meds = listOf(m),
            now = LocalDateTime.of(2026, 10, 1, 0, 28),
            isSettled = { _, slotKey -> slotKey in settled },
        )
        assertEquals(listOf("08:00", "23:55"), pending.map { it.slotTime })
    }

    @Test
    fun `今天才新建的药 不会把昨天算成漏服（假阳性回归锁）`() {
        // 场景：用户在 10-01 新建一支「每日 23:55」的药。ScheduleCalc.slotsFor 不按日期过滤，
        // 若这里不判 startDate，今日页会立刻冒出「昨天还有 1 剂未记录」——而昨天这支药还不存在。
        val pending = PendingDoses.unsettledOn(
            date = slotDate,                                   // 09-30
            meds = listOf(med(startDate = "2026-10-01")),      // 10-01 才开始
            now = LocalDateTime.of(2026, 10, 1, 0, 28),
            isSettled = { _, _ -> false },
        )
        assertTrue("还没开始的药不该出现在补记卡", pending.isEmpty())
    }

    @Test
    fun `昨天已在用的药 仍会正常出现在补记卡`() {
        val pending = PendingDoses.unsettledOn(
            date = slotDate,
            meds = listOf(med(startDate = "2026-09-30")),      // 昨天开始
            now = LocalDateTime.of(2026, 10, 1, 0, 28),
            isSettled = { _, _ -> false },
        )
        assertEquals(1, pending.size)
    }

    @Test
    fun `非法时刻的脏数据不会让它崩 只跳过该槽位`() {
        val m = med(times = """["25:99","23:55"]""")
        val pending = PendingDoses.unsettledOn(
            date = slotDate,
            meds = listOf(m),
            now = LocalDateTime.of(2026, 10, 1, 0, 28),
            isSettled = { _, _ -> false },
        )
        assertEquals("脏时刻应被过滤，剩下合法的那条", listOf("23:55"), pending.map { it.slotTime })
    }
}
