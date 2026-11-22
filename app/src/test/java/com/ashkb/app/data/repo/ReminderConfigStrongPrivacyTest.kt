package com.ashkb.app.data.repo

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.22（独立审查 F2）：强提醒「显示药名」开关的持久化。
 *
 * 口径与 `EmergencyLockscreenStore` 刻意不同：那个是**默认关**（默认不把紧急信息推上锁屏），
 * 这个是**默认开**（保持强提醒原有的可读性），只有用户显式关闭才掩码。
 * 这组测试钉死默认值与往返，防止以后被「顺手统一成默认关」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReminderConfigStrongPrivacyTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("reminder_config", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `默认显示药名`() {
        assertTrue(ReminderConfigRepository(context).strongReminderShowMedication())
    }

    @Test
    fun `关闭后新实例仍读到关闭`() {
        ReminderConfigRepository(context).setStrongReminderShowMedication(false)
        assertFalse(ReminderConfigRepository(context).strongReminderShowMedication())
    }

    @Test
    fun `重新打开后恢复显示`() {
        val repo = ReminderConfigRepository(context)
        repo.setStrongReminderShowMedication(false)
        repo.setStrongReminderShowMedication(true)
        assertTrue(ReminderConfigRepository(context).strongReminderShowMedication())
    }

    @Test
    fun `该开关不影响其它提醒配置`() {
        val repo = ReminderConfigRepository(context)
        val cycleBefore = repo.basdaiCycleDays()
        val checkupBefore = repo.checkupEnabled()
        repo.setStrongReminderShowMedication(false)
        assertTrue(cycleBefore == repo.basdaiCycleDays())
        assertTrue(checkupBefore == repo.checkupEnabled())
    }
}
