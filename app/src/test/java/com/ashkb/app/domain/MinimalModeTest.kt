package com.ashkb.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.65 B12：极简模式状态机回归（红线三 e2）。
 */
class MinimalModeTest {

    private val today = LocalDate.of(2026, 9, 26)

    @Test
    fun `今天已记录 缺失为 0`() {
        assertEquals(0, MinimalMode.missingStreakDays(setOf("2026-09-26"), today))
    }

    @Test
    fun `连续缺失两天 未达阈值`() {
        val recorded = setOf("2026-09-23") // 24/25/26 缺 → 恰好 3 天
        // 24 缺、25 缺、26 缺 → 3
        assertEquals(3, MinimalMode.missingStreakDays(recorded, today))
    }

    @Test
    fun `刚好达到阈值即触发`() {
        // 23 有记录 → 24/25/26 连续缺 3 天
        assertTrue(MinimalMode.shouldPrompt(setOf("2026-09-23"), today))
        // 24 有记录 → 25/26 只缺 2 天
        assertFalse(MinimalMode.shouldPrompt(setOf("2026-09-24"), today))
    }

    @Test
    fun `缺失计数在命中第一个有记录的日期时停止`() {
        // 25 有记录 → 只有 26 缺
        assertEquals(1, MinimalMode.missingStreakDays(setOf("2026-09-25"), today))
    }

    @Test
    fun `中间有记录则不算连续`() {
        // 25 有记录 → 断链，只数到今天为止的 1 天
        assertEquals(1, MinimalMode.missingStreakDays(setOf("2026-09-25", "2026-09-20"), today))
    }

    @Test
    fun `缺失计数封顶为阈值`() {
        // 完全无记录也应只返回阈值，不随「缺了多久」增长
        assertEquals(MinimalMode.THRESHOLD_DAYS, MinimalMode.missingStreakDays(emptySet(), today))
    }

    @Test
    fun `身体不适与住院进入极简 其他原因不进入`() {
        assertTrue(MinimalMode.entersMinimal(MinimalMode.REASON_ILLNESS))
        assertTrue(MinimalMode.entersMinimal(MinimalMode.REASON_HOSPITAL))
        assertFalse(MinimalMode.entersMinimal(MinimalMode.REASON_OTHER))
        assertFalse("未知原因不该改界面", MinimalMode.entersMinimal("whatever"))
    }

    @Test
    fun `状态成对不变式`() {
        assertTrue(MinimalMode.isConsistent(MinimalMode.MODE_MINIMAL, "2026-09-26T10:00:00"))
        assertFalse("极简态缺进入时刻 → 不一致", MinimalMode.isConsistent(MinimalMode.MODE_MINIMAL, null))
        assertTrue(MinimalMode.isConsistent(MinimalMode.MODE_NORMAL, null))
        assertFalse("普通态不该有进入时刻", MinimalMode.isConsistent(MinimalMode.MODE_NORMAL, "2026-09-26T10:00:00"))
    }
}
