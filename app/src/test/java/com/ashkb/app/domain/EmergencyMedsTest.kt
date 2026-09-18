package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.26 紧急卡「当前用药」汇总（EmergencyMeds）单测。
 *
 * 背景：急救场景最需要知道患者是否正在使用免疫抑制剂 / 生物制剂（感染风险、伤口愈合、
 * 不可骤停判断），故从药单自动汇总。本测试锁定在用口径、免疫抑制置顶标注、
 * 截断「置顶优先」与一页可读前提，以及展示文案（通用名（商品名）、剂量 · 频次）。
 */
class EmergencyMedsTest {

    private val created = "2026-01-01T00:00:00"
    private val today = "2026-08-30"

    private fun med(
        id: String = "med-test",
        name: String = "测试药",
        brandName: String? = null,
        medClass: String = "OTHER",
        route: String = "oral",
        dose: String = "1 片",
        frequency: String = "DAILY",
        startDate: String = "2026-08-03",
        endDate: String? = null,
        injCycleDays: Int? = null,
        isArchived: Boolean = false,
    ) = Medication(
        id = id, name = name, brandName = brandName, nameKey = name.lowercase(),
        medClass = medClass, route = route, dose = dose, frequency = frequency,
        startDate = startDate, endDate = endDate, injCycleDays = injCycleDays,
        isArchived = isArchived, createdAt = created, updatedAt = created,
    )

    // ======================= 在用口径 =======================

    @Test
    fun `已归档药物被排除`() {
        val archived = med(id = "med-a", medClass = "BIOLOGIC", isArchived = true)
        assertFalse(EmergencyMeds.isActive(archived, today))
        val s = EmergencyMeds.summarize(listOf(archived), today)
        assertTrue(s.isEmpty)
        assertEquals(0, s.hiddenCount)
    }

    @Test
    fun `结束日期早于今天被排除`() {
        val ended = med(id = "med-e", endDate = "2026-08-29")
        assertFalse(EmergencyMeds.isActive(ended, today))
        assertTrue(EmergencyMeds.summarize(listOf(ended), today).isEmpty)
    }

    @Test
    fun `结束日期等于今天仍算在用`() {
        val lastDay = med(id = "med-e", endDate = "2026-08-30")
        assertTrue(EmergencyMeds.isActive(lastDay, today))
        assertEquals(1, EmergencyMeds.summarize(listOf(lastDay), today).ordered.size)
    }

    @Test
    fun `结束日期为空保留、空白视为未设结束日期`() {
        assertTrue(EmergencyMeds.isActive(med(endDate = null), today))
        assertTrue(EmergencyMeds.isActive(med(endDate = "  "), today))
    }

    // ======================= 免疫抑制标注与置顶 =======================

    @Test
    fun `四类免疫抑制药物均被标注`() {
        assertTrue(EmergencyMeds.isImmunosuppressant("BIOLOGIC"))
        assertTrue(EmergencyMeds.isImmunosuppressant("JAK"))
        assertTrue(EmergencyMeds.isImmunosuppressant("CSDMARD"))
        assertTrue(EmergencyMeds.isImmunosuppressant("GLUCOCORTICOID"))
    }

    @Test
    fun `NSAID 与 OTHER 不标注为免疫抑制`() {
        assertFalse(EmergencyMeds.isImmunosuppressant("NSAID"))
        assertFalse(EmergencyMeds.isImmunosuppressant("OTHER"))
    }

    @Test
    fun `免疫抑制类置顶且其余保持传入顺序`() {
        val nsaid = med(id = "med-1", name = "塞来昔布", medClass = "NSAID")
        val bio = med(id = "med-2", name = "依那西普", medClass = "BIOLOGIC")
        val other = med(id = "med-3", name = "钙片", medClass = "OTHER")
        val jak = med(id = "med-4", name = "托法替布", medClass = "JAK")
        val s = EmergencyMeds.summarize(listOf(nsaid, bio, other, jak), today)
        assertEquals(listOf("依那西普", "托法替布"), s.immunosuppressants.map { it.name })
        assertEquals(listOf("塞来昔布", "钙片"), s.others.map { it.name })
        assertEquals(listOf("依那西普", "托法替布", "塞来昔布", "钙片"), s.ordered.map { it.name })
        assertTrue(s.hasImmunosuppressant)
        assertTrue(s.immunosuppressants.all { it.immunosuppressant })
        assertTrue(s.others.none { it.immunosuppressant })
    }

    // ======================= 截断（置顶优先） =======================

    @Test
    fun `截断时优先保住免疫抑制类并给出 hiddenCount`() {
        val bio = med(id = "med-b", name = "依那西普", medClass = "BIOLOGIC")
        val gluco = med(id = "med-g", name = "泼尼松", medClass = "GLUCOCORTICOID")
        val o1 = med(id = "med-1", name = "塞来昔布", medClass = "NSAID")
        val o2 = med(id = "med-2", name = "钙片", medClass = "OTHER")
        val o3 = med(id = "med-3", name = "叶酸", medClass = "OTHER")
        val s = EmergencyMeds.summarize(listOf(o1, bio, o2, gluco, o3), today, maxLines = 3)
        assertEquals(listOf("依那西普", "泼尼松"), s.immunosuppressants.map { it.name })
        assertEquals(listOf("塞来昔布"), s.others.map { it.name })
        assertEquals(2, s.hiddenCount)
    }

    @Test
    fun `maxLines 小于免疫抑制条数时只列前 N 条免疫抑制`() {
        val bio = med(id = "med-b", name = "依那西普", medClass = "BIOLOGIC")
        val jak = med(id = "med-j", name = "托法替布", medClass = "JAK")
        val gluco = med(id = "med-g", name = "泼尼松", medClass = "GLUCOCORTICOID")
        val nsaid = med(id = "med-n", name = "塞来昔布", medClass = "NSAID")
        val s = EmergencyMeds.summarize(listOf(bio, jak, gluco, nsaid), today, maxLines = 2)
        assertEquals(listOf("依那西普", "托法替布"), s.immunosuppressants.map { it.name })
        assertTrue(s.others.isEmpty())
        assertEquals(2, s.hiddenCount)
    }

    @Test
    fun `未超上限时 hiddenCount 为零`() {
        val s = EmergencyMeds.summarize(
            listOf(med(id = "med-1"), med(id = "med-2")), today,
        )
        assertEquals(2, s.ordered.size)
        assertEquals(0, s.hiddenCount)
    }

    // ======================= 展示文案 =======================

    @Test
    fun `有商品名时拼接为通用名括号商品名`() {
        val m = med(name = "依那西普", brandName = "恩利", medClass = "BIOLOGIC")
        assertEquals("依那西普（恩利）", EmergencyMeds.summarize(listOf(m), today).ordered.single().name)
    }

    @Test
    fun `无商品名时只用通用名`() {
        val m = med(name = "甲氨蝶呤", brandName = null, medClass = "CSDMARD")
        assertEquals("甲氨蝶呤", EmergencyMeds.summarize(listOf(m), today).ordered.single().name)
    }

    @Test
    fun `商品名为空白时只用通用名`() {
        val m = med(name = "甲氨蝶呤", brandName = " ", medClass = "CSDMARD")
        assertEquals("甲氨蝶呤", EmergencyMeds.summarize(listOf(m), today).ordered.single().name)
    }

    @Test
    fun `detail 含剂量与频次中文标签`() {
        val m = med(dose = "1 片", frequency = "DAILY")
        assertEquals("1 片 · 每日", EmergencyMeds.summarize(listOf(m), today).ordered.single().detail)
    }

    @Test
    fun `注射药 detail 附注射周期`() {
        val m = med(dose = "25 mg", frequency = "Q2W", route = "injection", injCycleDays = 14)
        val detail = EmergencyMeds.summarize(listOf(m), today).ordered.single().detail
        assertTrue(detail.contains("25 mg"))
        assertTrue(detail.contains("· 每 14 天"))
    }

    // ======================= 空药单 =======================

    @Test
    fun `空药单 isEmpty 且 hiddenCount 为零`() {
        val s = EmergencyMeds.summarize(emptyList(), today)
        assertTrue(s.isEmpty)
        assertEquals(0, s.hiddenCount)
        assertTrue(s.ordered.isEmpty())
        assertFalse(s.hasImmunosuppressant)
    }
}
