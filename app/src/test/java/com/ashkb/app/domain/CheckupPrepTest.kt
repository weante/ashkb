package com.ashkb.app.domain

import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v10（C4）复诊前准备清单——纯函数单测。 */
class CheckupPrepTest {

    private val today = LocalDate.of(2026, 9, 21)

    private fun item(name: String, type: String, active: Boolean = true) = CheckupItem(
        id = "ci-$name",
        name = name,
        checkType = type,
        isActive = active,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = "2026-01-01T00:00:00",
    )

    private fun record(nextDate: String?) = CheckupRecord(
        id = "cr-1",
        date = "2026-09-01",
        recordedAt = "2026-09-01T09:00:00",
        itemName = "风湿科门诊",
        checkType = "CONSULT",
        nextDate = nextDate,
    )

    @Test
    fun `no records means no plan`() {
        val p = CheckupPrep.plan(emptyList(), emptyList(), today)
        assertFalse(p.hasPlan)
        assertNull(p.nextDate)
        assertNull(p.daysLeft)
    }

    @Test
    fun `future next date drives countdown`() {
        val p = CheckupPrep.plan(emptyList(), listOf(record("2026-09-28")), today)
        assertTrue(p.hasPlan)
        assertEquals("2026-09-28", p.nextDate)
        assertEquals(7L, p.daysLeft)
    }

    /** 已过期的 next_date 不算「下次复诊」，否则用户会看到负数倒计时。 */
    @Test
    fun `past next date is ignored`() {
        val p = CheckupPrep.plan(emptyList(), listOf(record("2026-09-01")), today)
        assertFalse(p.hasPlan)
    }

    @Test
    fun `earliest future date wins`() {
        val p = CheckupPrep.plan(
            emptyList(),
            listOf(record("2026-10-30"), record("2026-09-25"), record("2026-09-30")),
            today,
        )
        assertEquals("2026-09-25", p.nextDate)
    }

    @Test
    fun `same day is zero days left and is soon`() {
        val p = CheckupPrep.plan(emptyList(), listOf(record("2026-09-21")), today)
        assertEquals(0L, p.daysLeft)
        assertTrue(p.isSoon)
    }

    @Test
    fun `lab item requires fasting`() {
        val p = CheckupPrep.plan(listOf(item("血常规", "LAB")), emptyList(), today)
        assertTrue(p.fasting)
        assertTrue(p.bringItems.any { it.contains("空腹") })
    }

    @Test
    fun `non-lab item does not require fasting`() {
        val p = CheckupPrep.plan(listOf(item("眼科检查", "EYE")), emptyList(), today)
        assertFalse(p.fasting)
        assertFalse(p.bringItems.any { it.contains("空腹") })
    }

    @Test
    fun `image item adds imaging prep note`() {
        val p = CheckupPrep.plan(listOf(item("骶髂关节 MRI", "IMAGE")), emptyList(), today)
        assertTrue(p.bringItems.any { it.contains("影像") })
    }

    @Test
    fun `inactive items are excluded from checklist`() {
        val p = CheckupPrep.plan(
            listOf(item("血常规", "LAB"), item("已停用项目", "LAB", active = false)),
            emptyList(),
            today,
        )
        assertEquals(listOf("血常规"), p.checkItems)
    }

    @Test
    fun `base bring items always present`() {
        val p = CheckupPrep.plan(emptyList(), emptyList(), today)
        assertEquals(3, p.bringItems.size)
        assertTrue(p.bringItems.any { it.contains("医保") })
    }
}
