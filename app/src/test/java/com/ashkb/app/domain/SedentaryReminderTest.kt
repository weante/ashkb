package com.ashkb.app.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.68 C8a：久坐起身提醒的时刻计算回归。
 */
class SedentaryReminderTest {

    private fun at(h: Int, m: Int = 0, day: Int = 26) = LocalDateTime.of(2026, 9, day, h, m)

    @Test
    fun `窗口开始前 首次提醒落在窗口起点`() {
        assertEquals(at(9), SedentaryReminder.nextFire(at(8), 9, 18, 45))
    }

    @Test
    fun `恰在网格点上 推进到下一个网格点`() {
        // 严格晚于 now：9:00 触发后应排 9:45，而不是立刻重排 9:00（防自激）
        assertEquals(at(9, 45), SedentaryReminder.nextFire(at(9), 9, 18, 45))
    }

    @Test
    fun `网格间隙中 落到下一个网格点`() {
        assertEquals(at(9, 45), SedentaryReminder.nextFire(at(9, 10), 9, 18, 45))
    }

    @Test
    fun `最后一个网格点之后 落到次日窗口起点`() {
        // 9–18 + 45 的网格最后一个是 17:30，其后下一次应在次日 9:00
        assertEquals(at(9, 0, day = 27), SedentaryReminder.nextFire(at(17, 45), 9, 18, 45))
    }

    @Test
    fun `窗口结束之后 也落到次日`() {
        assertEquals(at(9, 0, day = 27), SedentaryReminder.nextFire(at(22), 9, 18, 45))
    }

    @Test
    fun `30 分钟间隔生成更密的网格`() {
        assertEquals(at(9, 30), SedentaryReminder.nextFire(at(9, 5), 9, 18, 30))
        assertEquals(at(17, 30), SedentaryReminder.nextFire(at(17, 25), 9, 18, 30))
    }

    @Test
    fun `网格点严格小于窗口结束时刻`() {
        // 9–10 + 30 → 9:00 / 9:30（10:00 不属于窗口）
        assertEquals(at(9, 30), SedentaryReminder.nextFire(at(9), 9, 10, 30))
        assertEquals(at(9, 0, day = 27), SedentaryReminder.nextFire(at(9, 30), 9, 10, 30))
    }

    @Test
    fun `非法窗口返回 null 等同功能关闭`() {
        assertNull(SedentaryReminder.nextFire(at(9), 18, 9, 45))  // start > end
        assertNull(SedentaryReminder.nextFire(at(9), 9, 9, 45))   // start == end
        assertNull(SedentaryReminder.nextFire(at(9), 24, 25, 45)) // 越界
        assertNull(SedentaryReminder.nextFire(at(9), 9, 18, 0))   // 间隔非正
    }

    @Test
    fun `窗口合法性判定`() {
        assertTrue(SedentaryReminder.isValidWindow(9, 18))
        assertTrue(SedentaryReminder.isValidWindow(0, 24))
        assertTrue(!SedentaryReminder.isValidWindow(18, 9))
        assertTrue(!SedentaryReminder.isValidWindow(9, 9))
    }
}
