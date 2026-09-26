package com.ashkb.app.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
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
 * v1.0.62 C11：测试提醒调度回归（仿 [ReminderSchedulerTest] 模式）。
 *
 * 断言以「闹钟触发时刻」为准——这是 [ReminderTest.schedule] 对外唯一可观察效果。
 * `nowMs` 注入使结果与运行时刻无关。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderTestSchedulerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun alarmTimes(): List<Long> {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Shadows.shadowOf(am).scheduledAlarms.map { it.triggerAtMs }
    }

    @Test
    fun `默认延迟 10 秒 触发时刻为 now 加 10 秒`() {
        val now = 1_700_000_000_000L
        ReminderTest.schedule(context, nowMs = now)
        assertEquals(listOf(now + 10_000L).sorted(), alarmTimes().sorted())
    }

    @Test
    fun `自定义延迟按秒换算为毫秒`() {
        val now = 1_700_000_000_000L
        ReminderTest.schedule(context, delaySeconds = 30L, nowMs = now)
        assertEquals(listOf(now + 30_000L).sorted(), alarmTimes().sorted())
    }

    @Test
    fun `重复排程幂等（requestCode 稳定）`() {
        val now = 1_700_000_000_000L
        ReminderTest.schedule(context, nowMs = now)
        ReminderTest.schedule(context, nowMs = now)
        assertEquals("重复排程不应累积闹钟", 1, alarmTimes().size)
    }

    @Test
    fun `cancel 精确清空测试闹钟`() {
        ReminderTest.schedule(context, nowMs = 1_700_000_000_000L)
        assertTrue("前置条件：应已排上测试闹钟", alarmTimes().isNotEmpty())
        ReminderTest.cancel(context)
        assertTrue("cancel 后应清空", alarmTimes().isEmpty())
    }
}
