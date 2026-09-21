package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v10（C7）漏服 / 延迟处理指引——纯函数单测。 */
class MissedDoseTest {

    private fun med(route: String, medClass: MedClass = MedClass.NSAID) = Medication(
        id = "med-test",
        name = "测试药",
        nameKey = "test",
        medClass = medClass.name,
        route = route,
        dose = "1 片",
        frequency = "DAILY",
        takeTimes = "[\"08:00\"]",
        startDate = "2026-01-01",
        createdAt = "2026-01-01T08:00:00",
        updatedAt = "2026-01-01T08:00:00",
    )

    @Test
    fun `prn has no guidance`() {
        assertNull(MissedDose.guidance("oral", 600L, isPrn = true))
    }

    @Test
    fun `not late has no guidance`() {
        assertNull(MissedDose.guidance("oral", 0L, isPrn = false))
        assertNull(MissedDose.guidance("oral", -30L, isPrn = false))
    }

    @Test
    fun `oral within catch-up window suggests taking soon`() {
        val g = MissedDose.guidance("oral", 60L, isPrn = false)!!
        assertTrue(g.headline.contains("补服"))
        assertFalse(g.contactDoctor)
    }

    /** 边界：120 分钟（含）仍属「尽快补服」。 */
    @Test
    fun `oral at exactly the catch-up boundary still catches up`() {
        val g = MissedDose.guidance("oral", MissedDose.ORAL_CATCH_UP_MIN, isPrn = false)!!
        assertTrue(g.headline.contains("补服"))
    }

    @Test
    fun `oral beyond window suggests skipping and never doubling`() {
        val g = MissedDose.guidance("oral", MissedDose.ORAL_CATCH_UP_MIN + 1, isPrn = false)!!
        assertTrue(g.headline.contains("跳过"))
        // 「切勿加倍剂量」是这条指引存在的核心理由
        assertTrue(g.steps.any { it.contains("加倍") })
        assertFalse(g.contactDoctor)
    }

    @Test
    fun `injection within 48h window suggests catching up`() {
        val g = MissedDose.guidance("injection", 24L * 60, isPrn = false)!!
        assertTrue(g.headline.contains("补注"))
        assertFalse(g.contactDoctor)
    }

    /** 边界：48 小时（含）仍在窗口内。 */
    @Test
    fun `injection at exactly 48h is still in window`() {
        val g = MissedDose.guidance("injection", MissedDose.INJECTION_WINDOW_HOURS * 60, isPrn = false)!!
        assertTrue(g.headline.contains("补注"))
        assertFalse(g.contactDoctor)
    }

    @Test
    fun `injection beyond window requires contacting doctor`() {
        val g = MissedDose.guidance("injection", MissedDose.INJECTION_WINDOW_HOURS * 60 + 1, isPrn = false)!!
        assertTrue(g.headline.contains("联系医生"))
        assertTrue(g.contactDoctor)
    }

    /** 免疫抑制类在窗口内也提示咨询医生（漏注风险更高）。 */
    @Test
    fun `immunosuppressant injection in window flags doctor contact`() {
        val g = MissedDose.guidanceFor(
            med("injection", MedClass.BIOLOGIC), 24L * 60, isPrn = false,
        )!!
        assertTrue(g.contactDoctor)
    }

    @Test
    fun `oral is never flagged as contact doctor`() {
        val g = MissedDose.guidanceFor(
            med("oral", MedClass.BIOLOGIC), 60L, isPrn = false,
        )!!
        assertFalse(g.contactDoctor)
    }

    @Test
    fun `minutesLate parses valid time`() {
        // 08:00 计划，现在 09:30（570 分钟）→ 晚 90 分钟
        assertEquals(90L, MissedDose.minutesLate("08:00", 570))
    }

    @Test
    fun `minutesLate returns zero when not yet due`() {
        assertEquals(0L, MissedDose.minutesLate("20:00", 480))
    }

    @Test
    fun `minutesLate tolerates junk input`() {
        assertEquals(0L, MissedDose.minutesLate(null, 600))
        assertEquals(0L, MissedDose.minutesLate("", 600))
        assertEquals(0L, MissedDose.minutesLate("8", 600))
        assertEquals(0L, MissedDose.minutesLate("25:99", 600))
        assertEquals(0L, MissedDose.minutesLate("ab:cd", 600))
    }
}
