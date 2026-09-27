package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.71：删除已停用药品的判定与确认文案变体。
 *
 * 锁三件事：① 在用药品绝不能进删除路径；② 有打卡记录时必须走「报条数」的文案；
 * ③ 计数兜底（负数不显示成「-1 条记录」）。
 */
class MedDeletionTest {

    @Test
    fun `在用药品不可删除`() {
        assertFalse(MedDeletion.canDelete(isArchived = false))
    }

    @Test
    fun `已停用药品可删除`() {
        assertTrue(MedDeletion.canDelete(isArchived = true))
    }

    @Test
    fun `有打卡记录时用报条数的文案变体`() {
        assertEquals(MedDeletion.Variant.WITH_LOGS, MedDeletion.variant(1))
        assertEquals(MedDeletion.Variant.WITH_LOGS, MedDeletion.variant(42))
    }

    @Test
    fun `无打卡记录时用不含条数的文案变体`() {
        assertEquals(MedDeletion.Variant.NO_LOGS, MedDeletion.variant(0))
    }

    @Test
    fun `负数计数按 0 处理且不进入报条数分支`() {
        assertEquals(0, MedDeletion.normalizeCount(-3))
        assertEquals(MedDeletion.Variant.NO_LOGS, MedDeletion.variant(-3))
        assertEquals(0, MedDeletion.normalizeCount(Int.MIN_VALUE))
    }

    @Test
    fun `正数计数原样返回`() {
        assertEquals(7, MedDeletion.normalizeCount(7))
    }
}
