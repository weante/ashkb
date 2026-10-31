package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.73：活疫苗安全警报判定（第三份审查报告 P0-2 的回归网）。
 *
 * 这三件事必须锁死：
 *  ① **小写 `"pending"` 也要触发警报**——实体默认值曾是小写，而判定按大写比较，
 *     导致走默认值的路径静默跳过 high 级安全警报（本次修复的核心回归点）；
 *  ② **未知/脏取值往保守方向落**——`null`、空串、乱码一律按「待确认」处理，
 *     绝不能被当成「医生已同意」；
 *  ③ **非活疫苗不报警**（避免噪声淹没真警报），灭活/不详都不得触发。
 */
class VaccineSafetyTest {

    @Test
    fun `小写 pending 也触发警报（修复前会静默跳过）`() {
        assertTrue(VaccineSafety.needsLiveVaccineAlert("LIVE", "pending"))
    }

    @Test
    fun `大写 PENDING 触发警报`() {
        assertTrue(VaccineSafety.needsLiveVaccineAlert("LIVE", "PENDING"))
    }

    @Test
    fun `大小写混写也触发警报`() {
        assertTrue(VaccineSafety.needsLiveVaccineAlert("live", "Pending"))
    }

    @Test
    fun `确认状态未知或为空时按待确认处理（保守方向）`() {
        assertTrue(VaccineSafety.needsLiveVaccineAlert("LIVE", null))
        assertTrue(VaccineSafety.needsLiveVaccineAlert("LIVE", ""))
        assertTrue(VaccineSafety.needsLiveVaccineAlert("LIVE", "  "))
        assertTrue(VaccineSafety.needsLiveVaccineAlert("LIVE", "something-else"))
    }

    @Test
    fun `医生同意或医生不建议时不报警`() {
        assertFalse(VaccineSafety.needsLiveVaccineAlert("LIVE", "CONFIRMED"))
        assertFalse(VaccineSafety.needsLiveVaccineAlert("LIVE", "confirmed"))
        assertFalse(VaccineSafety.needsLiveVaccineAlert("LIVE", "DECLINED"))
    }

    @Test
    fun `非活疫苗一律不报警`() {
        assertFalse(VaccineSafety.needsLiveVaccineAlert("INACTIVATED", "PENDING"))
        assertFalse(VaccineSafety.needsLiveVaccineAlert("UNKNOWN", "PENDING"))
        assertFalse(VaccineSafety.needsLiveVaccineAlert(null, "PENDING"))
        assertFalse(VaccineSafety.needsLiveVaccineAlert("", "PENDING"))
        assertFalse(VaccineSafety.needsLiveVaccineAlert("not-a-type", "PENDING"))
    }

    @Test
    fun `两侧都未知时不报警（类型未知不算活疫苗）`() {
        assertFalse(VaccineSafety.needsLiveVaccineAlert(null, null))
    }

    // =========================================================================
    // v1.2.13（审查报告 §十 第 2 条 + P0-2 第 14 条）：当前是否在用免疫抑制药物
    // =========================================================================

    private fun drug(name: String, cls: String?) = VaccineSafety.ActiveDrug(name, cls)

    @Test
    fun `生物制剂与 JAK 与 csDMARD 都算免疫抑制药物`() {
        val hit = VaccineSafety.immunosuppressantDrugs(
            listOf(drug("依那西普", "BIOLOGIC"), drug("托法替布", "JAK"), drug("甲氨蝶呤", "CSDMARD"))
        )
        assertEquals(listOf("依那西普", "托法替布", "甲氨蝶呤"), hit.map { it.name })
    }

    @Test
    fun `非免疫抑制类别不算（含糖皮质激素，刻意排除）`() {
        val hit = VaccineSafety.immunosuppressantDrugs(
            listOf(
                drug("塞来昔布", "NSAID"),
                drug("对乙酰氨基酚", "ANALGESIC"),
                drug("泼尼松", "GLUCOCORTICOID"),
                drug("钙片", "OTHER"),
            )
        )
        assertTrue("糖皮质激素刻意不计入：拿不到可靠剂量判定，误报会淹没真正的警示", hit.isEmpty())
    }

    @Test
    fun `分类脏值按 OTHER 处理而不是猜`() {
        val hit = VaccineSafety.immunosuppressantDrugs(
            listOf(drug("来源不明的药", null), drug("乱码分类", "not-a-class"))
        )
        assertTrue(hit.isEmpty())
    }

    @Test
    fun `分类比较大小写无关（存库值可能被手工改过）`() {
        val hit = VaccineSafety.immunosuppressantDrugs(listOf(drug("阿达木单抗", "biologic")))
        assertEquals(listOf("阿达木单抗"), hit.map { it.name })
    }

    @Test
    fun `点名的药最多三支（再多就说不出重点）`() {
        val many = (1..6).map { drug("生物制剂$it", "BIOLOGIC") }
        val hit = VaccineSafety.immunosuppressantDrugs(many)
        assertEquals(VaccineSafety.MAX_NAMED_DRUGS, hit.size)
        assertEquals(listOf("生物制剂1", "生物制剂2", "生物制剂3"), hit.map { it.name })
    }

    @Test
    fun `空名字的条目被丢掉（文案里不能出现空药名）`() {
        val hit = VaccineSafety.immunosuppressantDrugs(
            listOf(drug("", "BIOLOGIC"), drug("   ", "JAK"), drug("依那西普", "BIOLOGIC"))
        )
        assertEquals(listOf("依那西普"), hit.map { it.name })
    }

    @Test
    fun `同名药只点名一次`() {
        val hit = VaccineSafety.immunosuppressantDrugs(
            listOf(drug("依那西普", "BIOLOGIC"), drug("依那西普 ", "BIOLOGIC"))
        )
        assertEquals(listOf("依那西普"), hit.map { it.name })
    }

    @Test
    fun `没在用药单时返回空但仍按活疫苗待确认报警`() {
        assertTrue("判据不含药单：停了药也不该让警报消失", VaccineSafety.needsLiveVaccineAlert("LIVE", "PENDING"))
        assertTrue(VaccineSafety.immunosuppressantDrugs(emptyList()).isEmpty())
    }
}
