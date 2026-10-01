package com.ashkb.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ============ v1.0.84（批次 9）：坐标换算提到组合期，口径必须逐点等价 ============

    @Test
    fun `组合期坐标与旧的逐帧 xFraction 逐点一致（视觉等价的回归线）`() {
        val pts = listOf(
            TrendPoint("2026-06-01", 2f),
            TrendPoint("2026-06-04", 8f),
            TrendPoint("?", 5f), // 脏数据：跳过该点，不让一行脏数据把整图搞没
            TrendPoint("2026-06-11", 0f),
        )
        // 量程取 0..10（远大于量程下限，coerce 不生效）：yFraction = 1 - (v - 0) / 10
        val got = miniPointsOf(pts, "2026-06-01", "2026-06-11", 0f, 10f)
        // 旧实现（draw lambda 内）：coords = points.mapNotNull { xFraction(it.date, from, to)?.let { f -> Offset(f*w, padY + (1-(v-vMin)/range)*plotH) } }
        val old = pts.mapNotNull { p ->
            xFraction(p.date, "2026-06-01", "2026-06-11")?.let { it to (1f - p.value / 10f) }
        }

        assertEquals(old.size, got.size)
        old.forEachIndexed { i, (fx, fy) ->
            assertEquals(fx, got[i].xFraction, 1e-6f)
            assertEquals(fy, got[i].yFraction, 1e-6f)
        }
        // 具体落点（画布映射 x = fraction * w、y = padY + fraction * plotH 与旧实现同式）
        assertEquals(0f, got[0].xFraction, 1e-6f)
        assertEquals(0.3f, got[1].xFraction, 1e-6f)
        assertEquals(1f, got[2].xFraction, 1e-6f)
        assertEquals(0.8f, got[0].yFraction, 1e-6f)
        assertEquals(0.2f, got[1].yFraction, 1e-6f)
        assertEquals(1f, got[2].yFraction, 1e-6f)
    }

    @Test
    fun `起点或终点日期解析不了时整条序列都没有横坐标（与旧实现一致）`() {
        val pts = listOf(TrendPoint("2026-06-01", 1f), TrendPoint("2026-06-04", 2f))
        assertTrue(miniPointsOf(pts, "?", "2026-06-11", 0f, 10f).isEmpty())
        assertTrue(miniPointsOf(pts, "2026-06-01", "", 0f, 10f).isEmpty())
    }

    @Test
    fun `只有单点日期脏时只跳过该点、顺序不变`() {
        val got = miniPointsOf(
            listOf(TrendPoint("×", 1f), TrendPoint("2026-06-11", 5f)),
            "2026-06-01", "2026-06-11", 0f, 10f,
        )
        assertEquals(1, got.size)
        assertEquals(1f, got[0].xFraction, 1e-6f)
        assertEquals(0.5f, got[0].yFraction, 1e-6f)
    }

    @Test
    fun `窗口外的点夹到边界而不是画出界`() {
        val got = miniPointsOf(
            listOf(TrendPoint("2026-05-01", 5f), TrendPoint("2026-07-01", 5f)),
            "2026-06-01", "2026-06-11", 0f, 10f,
        )
        assertEquals(0f, got[0].xFraction, 1e-6f)
        assertEquals(1f, got[1].xFraction, 1e-6f)
    }

    @Test
    fun `y 轴 0 在顶部（值越大越靠上）`() {
        val got = miniPointsOf(
            listOf(TrendPoint("2026-06-01", 10f), TrendPoint("2026-06-11", 0f)),
            "2026-06-01", "2026-06-11", 0f, 10f,
        )
        assertEquals(0f, got[0].yFraction, 1e-6f)
        assertEquals(1f, got[1].yFraction, 1e-6f)
    }

    @Test
    fun `量程退化为 0（单点或全同值）也不产生 NaN`() {
        val got = miniPointsOf(
            listOf(TrendPoint("2026-06-01", 5f)),
            "2026-06-01", "2026-06-11", 5f, 5f,
        )
        assertEquals(1, got.size)
        assertFalse(got[0].yFraction.isNaN())
        assertEquals(1f, got[0].yFraction, 1e-6f)
    }
}
