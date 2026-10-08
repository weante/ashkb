package com.ashkb.app.reminder

import com.ashkb.app.data.entity.Supplement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.4 补剂提醒调度。
 *
 * 只测**纯函数**（`slotsFor` / `parseTimes` / `reqCode`）：闹钟本身由系统持有，
 * 单测里断言不了；真正会悄悄坏掉的是「哪些时刻该排、哪些不该排」这条判据，
 * 以及「两支补剂同一时刻会不会互相覆盖」这条身份不变量。
 */
class SupplementReminderSchedulerTest {
    private val today = LocalDate.parse("2026-10-08")

    private fun sup(
        id: String = "sup-1",
        times: String? = """["08:00","20:00"]""",
        frequency: String = "daily",
        isArchived: Boolean = false,
    ) = Supplement(
        id = id,
        name = "维生素 D3",
        category = "VITAMIN_D",
        dose = "4000IU",
        frequency = frequency,
        times = times,
        isArchived = isArchived,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = "2026-01-01T00:00:00",
    )

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(today, LocalTime.of(hour, minute))

    @Test
    fun `每日补剂 按填写的时刻逐个出槽位`() {
        val slots = SupplementReminderScheduler.slotsFor(sup(), today, loggedToday = false, now = at(7))
        assertEquals(listOf("08:00", "20:00"), slots.map { it.time })
        assertEquals(listOf(today, today), slots.map { it.date })
        assertEquals(listOf("sup-1", "sup-1"), slots.map { it.supId })
    }

    @Test
    fun `已过点的时刻不再出槽位`() {
        // 09:00 时：08:00 已过（且超过 1 分钟容差），只剩 20:00
        val slots = SupplementReminderScheduler.slotsFor(sup(), today, loggedToday = false, now = at(9))
        assertEquals(listOf("20:00"), slots.map { it.time })
    }

    @Test
    fun `刚过 1 分钟内的时刻仍出槽位`() {
        // 08:00 那一刻前后 1 分钟是容差窗口：闹钟可能刚好在边界触发，不该被当成已过点丢掉
        val slots = SupplementReminderScheduler.slotsFor(sup(), today, loggedToday = false, now = at(8, 0))
        assertTrue(slots.any { it.time == "08:00" })
    }

    @Test
    fun `今日已打卡 该补剂整天不再出槽位`() {
        val slots = SupplementReminderScheduler.slotsFor(sup(), today, loggedToday = true, now = at(7))
        assertEquals(emptyList<String>(), slots.map { it.time })
    }

    @Test
    fun `已归档的补剂不出槽位`() {
        val slots = SupplementReminderScheduler.slotsFor(
            sup(isArchived = true), today, loggedToday = false, now = at(7),
        )
        assertEquals(emptyList<String>(), slots.map { it.time })
    }

    @Test
    fun `非每日频次不出槽位`() {
        // 按需 / 每周都没有「每天几点」的语义——猜一个时刻比不提醒更糟
        for (freq in listOf("prn", "weekly", "asneeded")) {
            val slots = SupplementReminderScheduler.slotsFor(
                sup(frequency = freq), today, loggedToday = false, now = at(7),
            )
            assertEquals("频次 $freq 不该排出槽位", emptyList<String>(), slots.map { it.time })
        }
    }

    @Test
    fun `没填时刻不出槽位`() {
        for (raw in listOf(null, "", "[]", "[  ]")) {
            val slots = SupplementReminderScheduler.slotsFor(
                sup(times = raw), today, loggedToday = false, now = at(7),
            )
            assertEquals("times=$raw 不该排出槽位", emptyList<String>(), slots.map { it.time })
        }
    }

    @Test
    fun `脏时刻被丢弃而不是崩溃`() {
        // 补剂的 times 会走备份恢复路径，历史脏值完全可能进来（25:99 会让 LocalTime.parse 抛异常）
        assertEquals(listOf("08:00"), SupplementReminderScheduler.parseTimes("""["25:99","08:00","abc"]"""))
        assertEquals(emptyList<String>(), SupplementReminderScheduler.parseTimes("not-json"))
        assertEquals(emptyList<String>(), SupplementReminderScheduler.parseTimes(null))
        assertEquals(emptyList<String>(), SupplementReminderScheduler.parseTimes("[]"))
    }

    @Test
    fun `槽位身份含补剂 id 两支同刻补剂不互相覆盖`() {
        val a = SupplementReminderScheduler.reqCode("sup-a", today, "08:00")
        val b = SupplementReminderScheduler.reqCode("sup-b", today, "08:00")
        assertNotEquals("同一天同一时刻的两支补剂必须有不同 requestCode", a, b)
        // 同一身份重复调用必须稳定（重排幂等、取消算得出同一个码）
        assertEquals(a, SupplementReminderScheduler.reqCode("sup-a", today, "08:00"))
        // 日期与时刻也都是身份的一部分
        assertNotEquals(a, SupplementReminderScheduler.reqCode("sup-a", today.plusDays(1), "08:00"))
        assertNotEquals(a, SupplementReminderScheduler.reqCode("sup-a", today, "09:00"))
    }
}
