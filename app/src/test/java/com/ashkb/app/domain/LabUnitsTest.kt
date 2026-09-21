package com.ashkb.app.domain

import com.ashkb.app.data.entity.LabResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.37：C3 化验按单位分组 + 跨院（多单位）识别。 */
class LabUnitsTest {

    private fun lab(
        id: String,
        test: String,
        unit: String?,
        date: String = "2026-09-01",
        value: Double = 1.0,
    ) = LabResult(
        id = id, date = date, recordedAt = "${date}T08:00:00",
        testName = test, value = value, unit = unit,
    )

    @Test
    fun `groups by test name and unit`() {
        val g = LabUnits.groupByUnit(
            listOf(
                lab("a", "CRP", "mg/L"),
                lab("b", "CRP", "mg/dL"),
                lab("c", "ESR", "mm/h"),
            )
        )
        assertEquals(3, g.size)
        assertEquals(listOf("CRP", "CRP", "ESR"), g.map { it.testName })
        // 组间按 unit 字典序（'L' < 'd'，故 mg/L 在 mg/dL 之前）
        assertEquals(listOf("mg/L", "mg/dL", "mm/h"), g.map { it.unit })
    }

    /** 空白单位归一为 null，且空 / 纯空白 / null 三种输入会落进同一组。 */
    @Test
    fun `blank unit normalises to null and groups together`() {
        val g = LabUnits.groupByUnit(
            listOf(lab("a", "X", ""), lab("b", "X", "   "), lab("c", "X", null))
        )
        assertEquals(1, g.size)
        assertNull(g.single().unit)
        assertEquals(3, g.single().results.size)
    }

    /** 同一项目跨单位 → 必须能被识别出来（用于「跨院数据仅供参考」提示）。 */
    @Test
    fun `mixed units are detected per test`() {
        val rows = listOf(
            lab("a", "CRP", "mg/L"), lab("b", "CRP", "mg/dL"), lab("c", "ESR", "mm/h"),
        )
        assertEquals(listOf("CRP"), LabUnits.mixedUnitTests(rows))
        assertTrue(LabUnits.isMixedUnit(rows.filter { it.testName == "CRP" }))
        assertFalse(LabUnits.isMixedUnit(rows.filter { it.testName == "ESR" }))
    }

    @Test
    fun `no mixed when single unit`() {
        assertTrue(LabUnits.mixedUnitTests(listOf(lab("a", "CRP", "mg/L"))).isEmpty())
    }

    @Test
    fun `group is ordered by date desc`() {
        val g = LabUnits.groupByUnit(
            listOf(
                lab("a", "CRP", "mg/L", "2026-09-01"),
                lab("b", "CRP", "mg/L", "2026-09-10"),
            )
        ).single()
        assertEquals(listOf("2026-09-10", "2026-09-01"), g.results.map { it.date })
    }
}
