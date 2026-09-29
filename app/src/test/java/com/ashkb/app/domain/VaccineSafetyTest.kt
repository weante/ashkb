package com.ashkb.app.domain

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
}
