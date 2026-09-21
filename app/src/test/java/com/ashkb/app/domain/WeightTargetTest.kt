package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v10（C9）体重目标区间判定——纯函数单测。 */
class WeightTargetTest {

    @Test
    fun `both null is no target`() {
        assertEquals(WeightTarget.Status.NO_TARGET, WeightTarget.status(70.0, null, null))
        assertEquals(WeightTarget.Status.NO_TARGET, WeightTarget.status(null, null, null))
    }

    @Test
    fun `only one side set is incomplete`() {
        assertEquals(WeightTarget.Status.INCOMPLETE, WeightTarget.status(70.0, 65.0, null))
        assertEquals(WeightTarget.Status.INCOMPLETE, WeightTarget.status(70.0, null, 75.0))
    }

    @Test
    fun `weight missing is incomplete even with full range`() {
        assertEquals(WeightTarget.Status.INCOMPLETE, WeightTarget.status(null, 65.0, 75.0))
    }

    @Test
    fun `inside range inclusive on both boundaries`() {
        assertEquals(WeightTarget.Status.IN_RANGE, WeightTarget.status(70.0, 65.0, 75.0))
        assertEquals(WeightTarget.Status.IN_RANGE, WeightTarget.status(65.0, 65.0, 75.0))
        assertEquals(WeightTarget.Status.IN_RANGE, WeightTarget.status(75.0, 65.0, 75.0))
    }

    @Test
    fun `below and above`() {
        assertEquals(WeightTarget.Status.BELOW, WeightTarget.status(64.9, 65.0, 75.0))
        assertEquals(WeightTarget.Status.ABOVE, WeightTarget.status(75.1, 65.0, 75.0))
    }

    /** 用户把上下限填反时应自动交换，而不是判成 BELOW/ABOVE 全错。 */
    @Test
    fun `reversed bounds are normalized`() {
        assertEquals(WeightTarget.Status.IN_RANGE, WeightTarget.status(70.0, 75.0, 65.0))
        assertEquals(WeightTarget.Status.BELOW, WeightTarget.status(60.0, 75.0, 65.0))
        assertEquals(65.0 to 75.0, WeightTarget.normalize(75.0, 65.0))
    }

    @Test
    fun `normalize returns null when a side is missing`() {
        assertNull(WeightTarget.normalize(null, 75.0))
        assertNull(WeightTarget.normalize(65.0, null))
        assertNull(WeightTarget.normalize(null, null))
    }

    /** 偏差符号：偏低为负（还需增重多少），偏高为正（超出多少）。 */
    @Test
    fun `deviation sign`() {
        assertEquals(-5.0, WeightTarget.deviation(60.0, 65.0, 75.0)!!, 1e-6)
        assertEquals(3.0, WeightTarget.deviation(78.0, 65.0, 75.0)!!, 1e-6)
        assertEquals(0.0, WeightTarget.deviation(70.0, 65.0, 75.0)!!, 1e-6)
        assertNull(WeightTarget.deviation(null, 65.0, 75.0))
    }
}
