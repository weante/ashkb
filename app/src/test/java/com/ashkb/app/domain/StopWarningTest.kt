package com.ashkb.app.domain

import com.ashkb.app.data.entity.DoseState
import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.SkipReason
import com.ashkb.app.data.entity.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.37：C6 服药三态与停药警示豁免、C5 原因枚举对齐。 */
class StopWarningTest {

    private fun med(
        medClass: String = MedClass.CSDMARD.name,
        doseState: String? = null,
        frequency: String = "DAILY",
    ) = Medication(
        id = "med-1", name = "甲氨蝶呤", nameKey = "mtx", medClass = medClass,
        route = "oral", dose = "10mg", frequency = frequency,
        doseState = doseState, startDate = "2026-01-01",
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    @Test
    fun `self stop on normal med keeps the warning`() {
        assertEquals(StopReason.SELF_STOPPED.warning, StopWarning.forStop(StopReason.SELF_STOPPED, med()))
    }

    /** C6 核心：医生批准的减量方案下，自行停药警示豁免。 */
    @Test
    fun `tapering exempts self-stop warning`() {
        assertNull(StopWarning.forStop(StopReason.SELF_STOPPED, med(doseState = DoseState.TAPERING.name)))
    }

    /** 豁免只针对「自行停药」；副作用等提示与减量状态无关，照常展示。 */
    @Test
    fun `tapering does not suppress unrelated warnings`() {
        val m = med(doseState = DoseState.TAPERING.name)
        assertEquals(StopReason.SIDE_EFFECT.warning, StopWarning.forStop(StopReason.SIDE_EFFECT, m))
        assertEquals(StopReason.INFECTION.warning, StopWarning.forStop(StopReason.INFECTION, m))
    }

    @Test
    fun `biologic self-stop warning shows for biologics but not tapering`() {
        assertTrue(StopWarning.showBiologicStopWarning(StopReason.SELF_STOPPED, med(MedClass.BIOLOGIC.name)))
        assertFalse(
            StopWarning.showBiologicStopWarning(
                StopReason.SELF_STOPPED, med(MedClass.BIOLOGIC.name, DoseState.TAPERING.name),
            )
        )
        assertFalse(StopWarning.showBiologicStopWarning(StopReason.SELF_STOPPED, med(MedClass.CSDMARD.name)))
        assertFalse(StopWarning.showBiologicStopWarning(StopReason.SIDE_EFFECT, med(MedClass.BIOLOGIC.name)))
    }

    /** 未显式设置三态时按 frequency 推断（旧数据 / 旧备份恢复后仍可用）。 */
    @Test
    fun `dose state falls back to frequency when unset`() {
        assertEquals(DoseState.FIXED, DoseState.of(med(frequency = "DAILY")))
        assertEquals(DoseState.PRN, DoseState.of(med(frequency = "PRN")))
        assertEquals(DoseState.TAPERING, DoseState.of(med(doseState = DoseState.TAPERING.name)))
        assertNull(DoseState.fromKey("nope"))
    }

    /** C5：新增的停药原因（感染发热 / 准备手术 / 经济原因）都带警示。 */
    @Test
    fun `c5 stop reasons carry warnings`() {
        assertTrue(StopReason.INFECTION.warning!!.isNotBlank())
        assertTrue(StopReason.SURGERY.warning!!.isNotBlank())
        assertTrue(StopReason.FINANCIAL.warning!!.isNotBlank())
        assertEquals(StopReason.INFECTION, StopReason.fromKey("infection"))
        assertEquals(StopReason.OTHER, StopReason.fromKey("nope"))
    }

    /** C5：新增漏服原因已入枚举，且旧 key 全部保留（历史日志向后兼容）。 */
    @Test
    fun `c5 skip reasons include planning items and keep legacy keys`() {
        val keys = SkipReason.entries.map { it.name }
        assertTrue(keys.containsAll(listOf("FORGOT", "OUTING", "RUN_OUT")))
        assertTrue(keys.containsAll(listOf("TOO_BUSY", "UNWELL", "HOSPITALIZED", "SIDE_EFFECT", "OTHER")))
    }
}
