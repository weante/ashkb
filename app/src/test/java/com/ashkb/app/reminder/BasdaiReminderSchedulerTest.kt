package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import com.ashkb.app.data.entity.BasdaiRecord
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
 * v1.0.59 B5：BASDAI 提醒链「取消-重建」语义回归（仿 [ReminderSchedulerTest] 模式）。
 *
 * 断言一律以「闹钟触发时刻集合」为准。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BasdaiReminderSchedulerTest {

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

    private fun at20(date: LocalDate) = LocalDateTime.of(date, java.time.LocalTime.of(20, 0))

    private fun record(date: String, id: String = "bas-1") = BasdaiRecord(
        id = id, date = date, recordedAt = "${date}T00:00:00", backfill = false,
        q1Fatigue = 5, q2SpinePain = 5, q3PeripheralPain = 5, q4TenderPoints = 5,
        q5StiffnessDegree = 5, q6StiffnessDuration = 5, total = 5.0, notes = null,
    )

    @Test
    fun `latest 为空 今日排首次评估 提醒链 3 个升级重查`() {
        // latest == null → dueDate = today → today 20:00 + +1/+2 20:00 共 3 个
        BasdaiReminderScheduler.rescheduleAll(context, null, 28, today, now)
        val times = alarmTimes()
        assertEquals(
            listOf(epoch(at20(today)), epoch(at20(today.plusDays(1))), epoch(at20(today.plusDays(2)))),
            times.sorted(),
        )
    }

    @Test
    fun `latest 已逾期 dueDate 为今日 排 3 个升级重查`() {
        // latest.date = today-30, cycle=28 → dueDate = today（已逾期）
        BasdaiReminderScheduler.rescheduleAll(
            context, record(today.minusDays(30).toString()), 28, today, now,
        )
        assertEquals(3, alarmTimes().size)
    }

    @Test
    fun `latest 未到周期 排到未来 dueDate`() {
        // latest.date = today-25, cycle=28 → dueDate = today+3
        val due = today.plusDays(3)
        BasdaiReminderScheduler.rescheduleAll(
            context, record(today.minusDays(25).toString()), 28, today, now,
        )
        val times = alarmTimes()
        assertEquals(
            listOf(epoch(at20(due)), epoch(at20(due.plusDays(1))), epoch(at20(due.plusDays(2)))),
            times.sorted(),
        )
    }

    @Test
    fun `重复重排幂等（requestCode 稳定）`() {
        val r = record(today.minusDays(30).toString())
        BasdaiReminderScheduler.rescheduleAll(context, r, 28, today, now)
        val first = alarmTimes().size
        BasdaiReminderScheduler.rescheduleAll(context, r, 28, today, now)
        val second = alarmTimes().size
        assertEquals(first, second)
    }

    @Test
    fun `cancelAllFuture 精确清空`() {
        BasdaiReminderScheduler.rescheduleAll(context, null, 28, today, now)
        assertTrue(alarmTimes().isNotEmpty())
        BasdaiReminderScheduler.cancelAllFuture(context, null, 28, today)
        assertTrue(alarmTimes().isEmpty())
    }
}
