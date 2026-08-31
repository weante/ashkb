package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * P5 用药计划槽位单测：注射周期锚点、口服时刻解析、WEEKLY 星期匹配、PRN 豁免、late 容差。
 * 这些直接决定提醒闹钟是否在对的时刻响起（M10 可靠性专项的代码层前置验证）。
 */
class ScheduleCalcTest {

    private val created = "2026-01-01T00:00:00"

    private fun med(
        route: String = "oral",
        frequency: String = "DAILY",
        takeTimes: String? = """["08:00","20:00"]""",
        startDate: String = "2026-08-03",
        injCycleDays: Int? = null,
        weeklyWeekday: Int? = null,
        takeWithFood: String? = null,
    ) = Medication(
        id = "med-test", name = "测试药", nameKey = "test",
        medClass = "OTHER", route = route, dose = "1 片", frequency = frequency,
        takeTimes = takeTimes, weeklyWeekday = weeklyWeekday,
        startDate = startDate, injCycleDays = injCycleDays,
        takeWithFood = takeWithFood, createdAt = created, updatedAt = created,
    )

    // ======================= 注射周期 =======================

    @Test
    fun `注射日 锚点当日即注射日`() {
        val m = med(route = "injection", frequency = "Q2W", injCycleDays = 14)
        assertTrue(ScheduleCalc.isInjectionDay(m, LocalDate.parse("2026-08-03")))
    }

    @Test
    fun `注射日 锚点加整周期为注射日`() {
        val m = med(route = "injection", frequency = "Q2W", injCycleDays = 14)
        assertTrue(ScheduleCalc.isInjectionDay(m, LocalDate.parse("2026-08-17")))
        assertTrue(ScheduleCalc.isInjectionDay(m, LocalDate.parse("2026-08-31")))
    }

    @Test
    fun `非注射日 锚点加非整周期`() {
        val m = med(route = "injection", frequency = "Q2W", injCycleDays = 14)
        assertFalse(ScheduleCalc.isInjectionDay(m, LocalDate.parse("2026-08-04")))
        assertFalse(ScheduleCalc.isInjectionDay(m, LocalDate.parse("2026-08-16")))
    }

    @Test
    fun `锚点之前不算注射日（负差值保护）`() {
        val m = med(route = "injection", frequency = "Q2W", injCycleDays = 14)
        assertFalse(ScheduleCalc.isInjectionDay(m, LocalDate.parse("2026-08-02")))
        assertFalse(ScheduleCalc.isInjectionDay(m, LocalDate.parse("2025-08-03")))
    }

    @Test
    fun `无周期或周期非法 不算注射日`() {
        assertFalse(ScheduleCalc.isInjectionDay(med(), LocalDate.parse("2026-08-03")))
        assertFalse(ScheduleCalc.isInjectionDay(
            med(route = "injection", frequency = "Q2W", injCycleDays = 0),
            LocalDate.parse("2026-08-03")))
    }

    // ======================= 槽位 =======================

    @Test
    fun `口服每日两次生成两槽位`() {
        val slots = ScheduleCalc.slotsFor(med(), LocalDate.parse("2026-08-30"))
        assertEquals(2, slots.size)
        assertEquals("08:00", slots[0].key)
        assertEquals("20:00", slots[1].key)
    }

    @Test
    fun `PRN 无计划槽位（使用不受计划约束）`() {
        val slots = ScheduleCalc.slotsFor(med(frequency = "PRN", takeTimes = null), LocalDate.parse("2026-08-30"))
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `WEEKLY 仅指定星期出槽位`() {
        val m = med(frequency = "WEEKLY", weeklyWeekday = 1) // 周一
        // 2026-08-31 是周一，2026-08-30 是周日
        assertEquals(2, ScheduleCalc.slotsFor(m, LocalDate.parse("2026-08-31")).size)
        assertTrue(ScheduleCalc.slotsFor(m, LocalDate.parse("2026-08-30")).isEmpty())
    }

    @Test
    fun `WEEKLY 无星期配置不出槽位（防呆）`() {
        val m = med(frequency = "WEEKLY", weeklyWeekday = null)
        assertTrue(ScheduleCalc.slotsFor(m, LocalDate.parse("2026-08-31")).isEmpty())
    }

    @Test
    fun `注射日无时刻配置时默认 0900`() {
        val m = med(route = "injection", frequency = "Q2W", injCycleDays = 14, takeTimes = null)
        val slots = ScheduleCalc.slotsFor(m, LocalDate.parse("2026-08-03"))
        assertEquals(1, slots.size)
        assertEquals("inj", slots[0].key)
        assertEquals("09:00", slots[0].time)
    }

    @Test
    fun `非注射日注射药无槽位`() {
        val m = med(route = "injection", frequency = "Q2W", injCycleDays = 14)
        assertTrue(ScheduleCalc.slotsFor(m, LocalDate.parse("2026-08-04")).isEmpty())
    }

    @Test
    fun `空腹药槽位标签带晨起空腹提示`() {
        val m = med(takeWithFood = "empty_stomach")
        val slots = ScheduleCalc.slotsFor(m, LocalDate.parse("2026-08-30"))
        assertTrue(slots[0].label.contains("晨起空腹"))
    }

    @Test
    fun `takeTimes 非法格式被过滤`() {
        val m = med(takeTimes = """["25:99","08:00","abc"]""")
        val slots = ScheduleCalc.slotsFor(m, LocalDate.parse("2026-08-30"))
        assertEquals(1, slots.size)
        assertEquals("08:00", slots[0].key)
    }

    @Test
    fun `takeTimes 非法 JSON 安全降级为空`() {
        val m = med(takeTimes = "not-json")
        assertTrue(ScheduleCalc.slotsFor(m, LocalDate.parse("2026-08-30")).isEmpty())
    }

    // ======================= late 判定 =======================

    private val date = LocalDate.parse("2026-08-30")

    @Test
    fun `准点打卡不 late`() {
        assertFalse(ScheduleCalc.isLate("08:00", "2026-08-30T08:00:00", date))
    }

    @Test
    fun `容差 30 分钟内不 late（含边界）`() {
        assertFalse(ScheduleCalc.isLate("08:00", "2026-08-30T08:30:00", date))
    }

    @Test
    fun `超过容差 31 分钟判 late`() {
        assertTrue(ScheduleCalc.isLate("08:00", "2026-08-30T08:31:00", date))
    }

    @Test
    fun `次日补打卡判 late`() {
        assertTrue(ScheduleCalc.isLate("08:00", "2026-08-31T08:00:00", date))
    }

    @Test
    fun `空输入不 late（保守不误报）`() {
        assertFalse(ScheduleCalc.isLate(null, "2026-08-30T09:00:00", date))
        assertFalse(ScheduleCalc.isLate("08:00", null, date))
        assertFalse(ScheduleCalc.isLate("08:00", "garbage", date))
    }
}
