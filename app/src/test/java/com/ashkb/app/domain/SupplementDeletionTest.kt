package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.0.81（批次 7）：删除**整个补剂条目**的确认文案变体。
 *
 * 锁两件事：① 名下还有服用记录时必须走「报条数」的文案（否则用户是在不知情下删掉一批记录）；
 * ② 计数兜底——负数不该在 UI 上显示成「共 -1 条」。
 * 与 [MedDeletionTest] 同款口径（用户 2026-09-27 对药品删除的要求）。
 */
class SupplementDeletionTest {

    @Test
    fun `有服用记录时用报条数的文案变体`() {
        assertEquals(SupplementDeletion.Variant.CASCADE, SupplementDeletion.variant(1))
        assertEquals(SupplementDeletion.Variant.CASCADE, SupplementDeletion.variant(42))
    }

    @Test
    fun `没有任何记录时用不报条数的文案变体`() {
        assertEquals(SupplementDeletion.Variant.PLAIN, SupplementDeletion.variant(0))
    }

    @Test
    fun `负数计数按 0 处理且不进入报条数分支`() {
        assertEquals(0, SupplementDeletion.normalizeCount(-3))
        assertEquals(SupplementDeletion.Variant.PLAIN, SupplementDeletion.variant(-3))
        assertEquals(0, SupplementDeletion.normalizeCount(Int.MIN_VALUE))
    }

    @Test
    fun `正数计数原样返回`() {
        assertEquals(7, SupplementDeletion.normalizeCount(7))
    }
}
