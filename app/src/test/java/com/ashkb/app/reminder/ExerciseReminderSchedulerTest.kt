package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import com.ashkb.app.data.entity.ExercisePlan
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
 * v1.0.59 B5：运动提醒链「取消-重建」语义回归（仿 [ReminderSchedulerTest] 模式）。
 *
 * 断言一律以「闹钟触发时刻集合」为准。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExerciseReminderSchedulerTest {

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

    private fun at(date: LocalDate, h: Int) = LocalDateTime.of(date, java.time.LocalTime.of(h, 0))

    private fun plan(active: Boolean = true, id: String = "eplan-1") = ExercisePlan(
        id = id, title = "测试计划", weeks = 4, stageMode = "any", weekStructure = "{}",
        isActive = active, isSeed = false, startDate = "2026-01-01",
        notes = null, createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    @Test
    fun `今日有处方未完成 排 18-20-22 三个提醒`() {
        ExerciseReminderScheduler.rescheduleAll(context, plan(), false, today, now)
        val times = alarmTimes()
        assertEquals(
            listOf(epoch(at(today, 18)), epoch(at(today, 20)), epoch(at(today, 22))),
            times.sorted(),
        )
    }

    @Test
    fun `今日已完成 不排提醒`() {
        ExerciseReminderScheduler.rescheduleAll(context, plan(), true, today, now)
        assertTrue("今日已 ExerciseLog 不应再提醒", alarmTimes().isEmpty())
    }

    @Test
    fun `plan 未启用 不排提醒`() {
        ExerciseReminderScheduler.rescheduleAll(context, plan(active = false), false, today, now)
        assertTrue("plan.isActive=false 不应排提醒", alarmTimes().isEmpty())
    }

    @Test
    fun `plan 为空 不排提醒`() {
        ExerciseReminderScheduler.rescheduleAll(context, null, false, today, now)
        assertTrue("无启用计划不应排提醒", alarmTimes().isEmpty())
    }

    @Test
    fun `傍晚后调度 首个提醒已过点 排剩余升级重查`() {
        // now = 19:00，18:00 已过点；只剩 20:00、22:00
        val evening = LocalDateTime.of(2026, 6, 15, 19, 0)
        ExerciseReminderScheduler.rescheduleAll(context, plan(), false, today, evening)
        val times = alarmTimes()
        assertEquals(
            listOf(epoch(at(today, 20)), epoch(at(today, 22))),
            times.sorted(),
        )
    }

    @Test
    fun `重复重排幂等（requestCode 稳定）`() {
        ExerciseReminderScheduler.rescheduleAll(context, plan(), false, today, now)
        val first = alarmTimes().size
        ExerciseReminderScheduler.rescheduleAll(context, plan(), false, today, now)
        val second = alarmTimes().size
        assertEquals(first, second)
    }

    @Test
    fun `cancelAllFuture 精确清空`() {
        ExerciseReminderScheduler.rescheduleAll(context, plan(), false, today, now)
        assertTrue(alarmTimes().isNotEmpty())
        ExerciseReminderScheduler.cancelAllFuture(context, today)
        assertTrue(alarmTimes().isEmpty())
    }
}
