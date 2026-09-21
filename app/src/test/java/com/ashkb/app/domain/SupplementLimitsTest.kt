package com.ashkb.app.domain

import com.ashkb.app.data.entity.Supplement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.38：B11 营养素每日上限警示（不内置医学上限，只做用户设定值的算术比较）。 */
class SupplementLimitsTest {

    private fun sup(
        category: String = "CALCIUM",
        dose: String = "500mg",
        frequency: String = "daily",
        times: String? = null,
        doseAmount: Double? = null,
        doseUnit: String? = null,
        dailyMax: Double? = null,
    ) = Supplement(
        id = "sup-1", name = "钙片", category = category, dose = dose,
        frequency = frequency, times = times,
        doseAmount = doseAmount, doseUnit = doseUnit, dailyMax = dailyMax,
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    @Test
    fun `doses per day prefers explicit time slots`() {
        assertEquals(2, SupplementLimits.dosesPerDay(sup(times = "08:00,20:00")))
        assertEquals(3, SupplementLimits.dosesPerDay(sup(times = "08:00, 12:00 ,20:00")))
        assertEquals(1, SupplementLimits.dosesPerDay(sup(times = "")))
    }

    @Test
    fun `doses per day falls back to frequency`() {
        assertEquals(1, SupplementLimits.dosesPerDay(sup(frequency = "daily")))
        assertEquals(2, SupplementLimits.dosesPerDay(sup(frequency = "bid")))
        assertEquals(3, SupplementLimits.dosesPerDay(sup(frequency = "tid")))
    }

    @Test
    fun `daily total multiplies amount by doses`() {
        assertEquals(1000.0, SupplementLimits.dailyTotal(sup(doseAmount = 500.0, times = "08:00,20:00"))!!, 0.001)
        assertEquals(500.0, SupplementLimits.dailyTotal(sup(doseAmount = 500.0, frequency = "daily"))!!, 0.001)
    }

    /** 未量化 / 未设上限 / 上限非正 → 一律不警示（null）。 */
    @Test
    fun `cannot judge returns null`() {
        assertNull(SupplementLimits.dailyTotal(sup(doseAmount = null)))
        assertNull(SupplementLimits.dailyTotal(sup(doseAmount = 0.0)))
        assertNull(SupplementLimits.exceedsDailyMax(sup(doseAmount = 500.0, dailyMax = null)))
        assertNull(SupplementLimits.exceedsDailyMax(sup(doseAmount = null, dailyMax = 1000.0)))
        assertNull(SupplementLimits.exceedsDailyMax(sup(doseAmount = 500.0, dailyMax = 0.0)))
    }

    @Test
    fun `exceeds only when total is above the user limit`() {
        assertFalse(SupplementLimits.exceedsDailyMax(sup(doseAmount = 500.0, times = "08:00,20:00", dailyMax = 1000.0))!!)
        assertTrue(SupplementLimits.exceedsDailyMax(sup(doseAmount = 500.0, times = "08:00,20:00", dailyMax = 800.0))!!)
        assertTrue(SupplementLimits.exceedsDailyMax(sup(doseAmount = 600.0, frequency = "daily", dailyMax = 500.0))!!)
    }

    @Test
    fun `unit label trims and blanks to empty`() {
        assertEquals("mg", SupplementLimits.unitLabel(sup(doseUnit = " mg ")))
        assertEquals("", SupplementLimits.unitLabel(sup(doseUnit = "   ")))
        assertEquals("", SupplementLimits.unitLabel(sup(doseUnit = null)))
    }
}
