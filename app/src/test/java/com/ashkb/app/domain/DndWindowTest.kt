package com.ashkb.app.domain

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.60 B8：免打扰时段判断回归。
 */
class DndWindowTest {

    @Test
    fun `跨午夜窗口 晚间落入免打扰`() {
        // 22:00-07:00，23:00 在窗口内
        val fire = LocalDateTime.of(2026, 9, 26, 23, 0)
        assertTrue(DndWindow.isInDnd(fire, "22:00", "07:00"))
    }

    @Test
    fun `跨午夜窗口 凌晨落入免打扰`() {
        // 22:00-07:00，03:00 在窗口内
        val fire = LocalDateTime.of(2026, 9, 27, 3, 0)
        assertTrue(DndWindow.isInDnd(fire, "22:00", "07:00"))
    }

    @Test
    fun `跨午夜窗口 白天不落入免打扰`() {
        val fire = LocalDateTime.of(2026, 9, 26, 12, 0)
        assertFalse(DndWindow.isInDnd(fire, "22:00", "07:00"))
    }

    @Test
    fun `跨午夜窗口 左边界 start 算在内`() {
        // 22:00 整算在内
        val fire = LocalDateTime.of(2026, 9, 26, 22, 0)
        assertTrue(DndWindow.isInDnd(fire, "22:00", "07:00"))
    }

    @Test
    fun `跨午夜窗口 右边界 end 不算在内`() {
        // 07:00 整不算在内（左闭右开）
        val fire = LocalDateTime.of(2026, 9, 27, 7, 0)
        assertFalse(DndWindow.isInDnd(fire, "22:00", "07:00"))
    }

    @Test
    fun `同日窗口 落入`() {
        val fire = LocalDateTime.of(2026, 9, 26, 13, 0)
        assertTrue(DndWindow.isInDnd(fire, "09:00", "17:00"))
    }

    @Test
    fun `同日窗口 早于 start 不落入`() {
        val fire = LocalDateTime.of(2026, 9, 26, 8, 59)
        assertFalse(DndWindow.isInDnd(fire, "09:00", "17:00"))
    }

    @Test
    fun `同日窗口 晚于等于 end 不落入`() {
        val fire = LocalDateTime.of(2026, 9, 26, 17, 0)
        assertFalse(DndWindow.isInDnd(fire, "09:00", "17:00"))
    }

    @Test
    fun `start 等于 end 表示全天免打扰`() {
        val fire = LocalDateTime.of(2026, 9, 26, 15, 30)
        assertTrue(DndWindow.isInDnd(fire, "00:00", "00:00"))
    }

    @Test
    fun `跨午夜窗口 2359 落入`() {
        val fire = LocalDateTime.of(2026, 9, 26, 23, 59)
        assertTrue(DndWindow.isInDnd(fire, "22:00", "07:00"))
    }

    @Test
    fun `跨午夜窗口 0000 落入`() {
        val fire = LocalDateTime.of(2026, 9, 27, 0, 0)
        assertTrue(DndWindow.isInDnd(fire, "22:00", "07:00"))
    }

    @Test
    fun `跨午夜窗口 0659 落入 0700 不落入`() {
        val inside = LocalDateTime.of(2026, 9, 27, 6, 59)
        assertTrue(DndWindow.isInDnd(inside, "22:00", "07:00"))
        val outside = LocalDateTime.of(2026, 9, 27, 7, 0)
        assertFalse(DndWindow.isInDnd(outside, "22:00", "07:00"))
    }
}
