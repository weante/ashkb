package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import com.ashkb.app.data.entity.CheckupRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * v1.0.59 B5：复诊提醒链「取消-重建」语义回归（仿 [ReminderSchedulerTest] 模式）。
 *
 * 断言一律以「闹钟触发时刻集合」为准——触发时刻是 rescheduleAll 对外唯一可观察效果。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CheckupReminderSchedulerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val today = LocalDate.of(2026, 6, 15)
    private val now = LocalDateTime.of(2026, 6, 15, 12, 0)

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun alarmTimes(): List<Long> {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Shadows.shadowOf(am).scheduledAlarms.map { it.triggerAtMs }
    }

    private fun epoch(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun at09(date: LocalDate) = LocalDateTime.of(date, java.time.LocalTime.of(9, 0))

    private fun record(nextDate: String?, id: String = "crec-1") = CheckupRecord(
        id = id, date = "2026-01-01", recordedAt = "2026-01-01T00:00:00", backfill = false,
        itemId = null, itemName = "测试复诊项目", checkType = "LAB", status = "done",
        hospital = null, doctor = null, nextDate = nextDate, conclusion = null, notes = null,
    )

    @Test
    fun `nextDate 在 7 天内 排提前 1 天与当日各 1 个提醒`() {
        val next = today.plusDays(3)
        CheckupReminderScheduler.rescheduleAll(context, listOf(record(next.toString())), today, now)
        val times = alarmTimes()
        // today+2 09:00 prep + today+3 09:00 day
        assertEquals(
            listOf(epoch(at09(today.plusDays(2))), epoch(at09(next))),
            times.sorted(),
        )
    }

    @Test
    fun `nextDate 是今天 仅排当日 0900（prep 已过点）`() {
        // now = 08:00，09:00 还没过点
        val morningNow = LocalDateTime.of(2026, 6, 15, 8, 0)
        CheckupReminderScheduler.rescheduleAll(
            context, listOf(record(today.toString())), today, morningNow,
        )
        val times = alarmTimes()
        assertEquals(listOf(epoch(at09(today))), times.sorted())
    }

    @Test
    fun `nextDate 在今天之前 不排提醒`() {
        CheckupReminderScheduler.rescheduleAll(
            context, listOf(record(today.minusDays(1).toString())), today, now,
        )
        assertTrue("已过期的 nextDate 不应排提醒", alarmTimes().isEmpty())
    }

    @Test
    fun `nextDate 超过 7 天窗口 不排提醒`() {
        CheckupReminderScheduler.rescheduleAll(
            context, listOf(record(today.plusDays(8).toString())), today, now,
        )
        assertTrue("超出 7 天窗口的 nextDate 不应排提醒", alarmTimes().isEmpty())
    }

    @Test
    fun `重复重排幂等（requestCode 稳定）`() {
        val r = listOf(record(today.plusDays(3).toString()))
        CheckupReminderScheduler.rescheduleAll(context, r, today, now)
        val first = alarmTimes().size
        CheckupReminderScheduler.rescheduleAll(context, r, today, now)
        val second = alarmTimes().size
        assertEquals("重排后闹钟数应不变（requestCode 稳定 + 先 cancelAllFuture）", first, second)
    }

    @Test
    fun `cancelAllFuture 精确清空 rescheduleAll 排出的闹钟`() {
        val r = listOf(record(today.plusDays(3).toString()))
        CheckupReminderScheduler.rescheduleAll(context, r, today, now)
        assertTrue("rescheduleAll 后应有闹钟", alarmTimes().isNotEmpty())
        CheckupReminderScheduler.cancelAllFuture(context, r)
        assertTrue("cancelAllFuture 后应清空", alarmTimes().isEmpty())
    }
}
