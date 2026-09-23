package com.ashkb.app.domain

import com.ashkb.app.data.entity.InjSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.49：手动修正用药记录的不变量（[MedLogEdit]）与注射部位映射（[InjSite]）。
 *
 * 编辑是最容易破坏数据不变量的入口：用户可以把「跳过」改成「已服」，
 * 若此时不把原因清掉，报表里就会出现「已服 + 原因=遗忘」这种自相矛盾的行；
 * 反之把「已服」改成「跳过」却不填原因，就会出现一条无原因的跳过。
 * 两者都会污染依从率口径——本文件就是这两条红线的回归锁。
 */
class MedLogEditTest {

    // ---- needsReason ----

    @Test
    fun `partial and skipped require a reason`() {
        assertTrue(MedLogEdit.needsReason(AdherenceCalc.PARTIAL))
        assertTrue(MedLogEdit.needsReason(AdherenceCalc.SKIPPED))
    }

    @Test
    fun `done does not require a reason`() {
        assertFalse(MedLogEdit.needsReason(AdherenceCalc.DONE))
    }

    // ---- reasonFor ----

    @Test
    fun `done clears the reason`() {
        // 从「跳过（遗忘）」改成「已服」→ 原因必须消失，否则出现「已服 + 原因」
        assertNull(MedLogEdit.reasonFor(AdherenceCalc.DONE, "FORGOT"))
    }

    @Test
    fun `skipped keeps the reason`() {
        assertEquals("FORGOT", MedLogEdit.reasonFor(AdherenceCalc.SKIPPED, "FORGOT"))
        assertEquals("OUTING", MedLogEdit.reasonFor(AdherenceCalc.PARTIAL, "OUTING"))
    }

    @Test
    fun `blank reason is normalized to null`() {
        assertNull(MedLogEdit.reasonFor(AdherenceCalc.SKIPPED, ""))
        assertNull(MedLogEdit.reasonFor(AdherenceCalc.SKIPPED, "   "))
        assertNull(MedLogEdit.reasonFor(AdherenceCalc.PARTIAL, null))
    }

    // ---- injSiteFor ----

    @Test
    fun `injection site is kept only when done`() {
        assertEquals("thigh_l", MedLogEdit.injSiteFor(AdherenceCalc.DONE, "thigh_l"))
        // 跳过的针次没有部位可言
        assertNull(MedLogEdit.injSiteFor(AdherenceCalc.SKIPPED, "thigh_l"))
        assertNull(MedLogEdit.injSiteFor(AdherenceCalc.PARTIAL, "thigh_l"))
    }

    @Test
    fun `blank injection site is normalized to null`() {
        assertNull(MedLogEdit.injSiteFor(AdherenceCalc.DONE, ""))
        assertNull(MedLogEdit.injSiteFor(AdherenceCalc.DONE, null))
    }

    // ---- canSave ----

    @Test
    fun `done can be saved without a reason`() {
        assertTrue(MedLogEdit.canSave(AdherenceCalc.DONE, null))
    }

    @Test
    fun `skipped cannot be saved without a reason`() {
        // 红线三：跳过必须有原因。UI 的保存按钮据此禁用
        assertFalse(MedLogEdit.canSave(AdherenceCalc.SKIPPED, null))
        assertFalse(MedLogEdit.canSave(AdherenceCalc.SKIPPED, "  "))
        assertFalse(MedLogEdit.canSave(AdherenceCalc.PARTIAL, null))
        assertTrue(MedLogEdit.canSave(AdherenceCalc.SKIPPED, "FORGOT"))
    }

    // ---- InjSite ----

    @Test
    fun `every injection site key resolves back to its label`() {
        for (s in InjSite.entries) {
            assertEquals(s, InjSite.fromKey(s.key))
            assertTrue("label must not be blank: $s", s.label.isNotBlank())
        }
    }

    /**
     * 存库键是**历史数据的一部分**：改了键，历史记录就再也映射不到中文。
     * 这 6 个值来自 v1.0.17 起的打卡写入（今日打卡的部位选择器），不可改。
     */
    @Test
    fun `stored keys are stable`() {
        assertEquals(
            listOf("thigh_l", "thigh_r", "abdomen_l", "abdomen_r", "arm_l", "arm_r"),
            InjSite.entries.map { it.key },
        )
    }

    @Test
    fun `keys are unique`() {
        val keys = InjSite.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `unknown key is not guessed`() {
        // 未知值返回 null（调用方回退显示原始字符串），不兜底成某个部位——
        // 猜错等于替用户改了注射部位
        assertNull(InjSite.fromKey(null))
        assertNull(InjSite.fromKey(""))
        assertNull(InjSite.fromKey("THIGH_L")) // 键是大小写敏感的存库值
        assertNull(InjSite.fromKey("thigh_x"))
    }
}
