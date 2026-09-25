package com.ashkb.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 小多图的**共享时间轴**口径（趋势页方案 C）。
 *
 * 这是方案 C 里唯一「错了也不报错、只是让人看错」的地方：若各格按自己的数据范围铺横轴，
 * 各格 0%–100% 对应的日期就不同，「同一时间点上下对齐着看」这个前提直接失效——
 * 而图画出来依然「很好看」。故对窗口与相对坐标单独设测。
 */
class SmallTrendChartAxisTest {

    // ======================= 共享窗口 =======================

    @Test
    fun `窗口取全部序列日期的并集`() {
        val w = sharedWindow(
            listOf(
                listOf("2026-06-10", "2026-06-20"),
                listOf("2026-06-05"),
                listOf("2026-06-28", "2026-06-15"),
            )
        )
        assertEquals("2026-06-05" to "2026-06-28", w)
    }

    @Test
    fun `窗口忽略解析不了的日期`() {
        val w = sharedWindow(listOf(listOf("2026-06-10", "?", "", "2026-06-05"), listOf("不是日期")))
        assertEquals("2026-06-05" to "2026-06-10", w)
    }

    @Test
    fun `全部日期都不可用或没有数据时返回 null`() {
        assertNull(sharedWindow(emptyList()))
        assertNull(sharedWindow(listOf(emptyList(), emptyList())))
        assertNull(sharedWindow(listOf(listOf("?"), listOf("2026-6-1"))))
    }

    @Test
    fun `只有一条序列一个点时窗口退化为同日`() {
        val w = sharedWindow(listOf(listOf("2026-06-10")))
        assertEquals("2026-06-10" to "2026-06-10", w)
    }

    // ======================= 窗口内相对坐标 =======================

    @Test
    fun `起止与中点分别落在 0 与 1 与 0-5`() {
        assertEquals(0f, xFraction("2026-06-01", "2026-06-01", "2026-06-11")!!, 1e-6f)
        assertEquals(0.5f, xFraction("2026-06-06", "2026-06-01", "2026-06-11")!!, 1e-6f)
        assertEquals(1f, xFraction("2026-06-11", "2026-06-01", "2026-06-11")!!, 1e-6f)
    }

    @Test
    fun `按天数的真实比例定位（不是按点数）`() {
        // 3 天跨度（06-01 → 06-04）：第 1 天在 1/3、第 2 天在 2/3。
        // 若错按「序号比例」实现，两个点会落在 0.5 与 1.0——这正是要防的错法。
        assertEquals(1f / 3f, xFraction("2026-06-02", "2026-06-01", "2026-06-04")!!, 1e-6f)
        assertEquals(2f / 3f, xFraction("2026-06-03", "2026-06-01", "2026-06-04")!!, 1e-6f)
        // 跨月也要正确
        assertEquals(0.1f, xFraction("2026-06-30", "2026-06-29", "2026-07-09")!!, 1e-6f)
    }

    @Test
    fun `单日窗口居中而不是除零`() {
        assertEquals(0.5f, xFraction("2026-06-01", "2026-06-01", "2026-06-01")!!, 1e-6f)
    }

    @Test
    fun `窗口外的日期被夹到边界（不丢点也不画出界）`() {
        assertEquals(0f, xFraction("2026-05-01", "2026-06-01", "2026-06-11")!!, 1e-6f)
        assertEquals(1f, xFraction("2026-07-01", "2026-06-01", "2026-06-11")!!, 1e-6f)
    }

    @Test
    fun `任一日解析不了就返回 null（跳过该点而不是画到假位置）`() {
        assertNull(xFraction("?", "2026-06-01", "2026-06-11"))
        assertNull(xFraction("2026-06-05", "?", "2026-06-11"))
        assertNull(xFraction("2026-06-05", "2026-06-01", ""))
    }
}
