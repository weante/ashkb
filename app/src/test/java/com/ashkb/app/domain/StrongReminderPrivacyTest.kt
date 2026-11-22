package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.22（独立审查 F2）：末级强提醒的**药名隐私口径**。
 *
 * 这组只钉纯函数，不做 UI 断言：真正的界面行为（锁屏上到底画不画那一行）
 * 需要真机验证，JVM 层能保证的是「开关 → 掩码判定」与「文本拼接」这两处不会被改错。
 */
class StrongReminderPrivacyTest {

    @Test
    fun `默认显示药名时不掩码`() {
        assertFalse(StrongReminderPrivacy.maskMedication(showMedication = true))
    }

    @Test
    fun `用户关闭显示后必须掩码`() {
        assertTrue(StrongReminderPrivacy.maskMedication(showMedication = false))
    }

    @Test
    fun `药名与剂量都非空时用空格拼接`() {
        assertEquals(
            "依那西普 25 mg",
            StrongReminderPrivacy.medicationLine("依那西普", "25 mg"),
        )
    }

    @Test
    fun `剂量为空时只给药名且不留尾部空格`() {
        assertEquals("依那西普", StrongReminderPrivacy.medicationLine("依那西普", ""))
        assertEquals("依那西普", StrongReminderPrivacy.medicationLine("依那西普", "   "))
    }

    @Test
    fun `药名为空时只给剂量`() {
        assertEquals("25 mg", StrongReminderPrivacy.medicationLine("", "25 mg"))
    }

    @Test
    fun `两者都空时返回空串`() {
        assertEquals("", StrongReminderPrivacy.medicationLine("", ""))
        assertEquals("", StrongReminderPrivacy.medicationLine("  ", "  "))
    }
}
