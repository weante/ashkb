package com.ashkb.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.45 趋势图改造：把「按日期定位 / 自适应刻度 / 读数口径」三件事钉死。
 *
 * 背景：这三条都是**纯函数**，而它们各自对应一个此前真实存在的缺陷——
 *  X 轴等距铺点（时间轴骗人）、刻度固定 4 段（矮画布下标签互压）、图上无读数（必须拖动）。
 * 放在 JVM 单测里可覆盖；Canvas 的像素绘制仍只能靠真机看。
 */
class TrendChartAxisTest {

    private fun pt(date: String, value: Float) = TrendPoint(date, value)

    // ---- dayOffsetsOf：按真实日期间隔定位 ----

    @Test
    fun `day offsets use real calendar gaps`() {
        val points = listOf(
            pt("2026-09-15", 5.4f),
            pt("2026-09-16", 5.0f),   // +1
            pt("2026-09-20", 4.2f),   // +5
            pt("2026-09-21", 3.9f),   // +6
        )
        assertEquals(listOf(0L, 1L, 5L, 6L), dayOffsetsOf(points))
    }

    @Test
    fun `day offsets cross year boundary correctly`() {
        val points = listOf(pt("2025-12-30", 1f), pt("2026-01-02", 2f))
        assertEquals(listOf(0L, 3L), dayOffsetsOf(points))
    }

    @Test
    fun `single point yields zero offset`() {
        assertEquals(listOf(0L), dayOffsetsOf(listOf(pt("2026-09-15", 3f))))
    }

    /** 一行脏数据不能把整张图搞没——回退等距由调用方处理，这里只要求「明确返回 null」。 */
    @Test
    fun `unparseable date falls back to null instead of throwing`() {
        assertNull(dayOffsetsOf(listOf(pt("2026-09-15", 1f), pt("not-a-date", 2f))))
        assertNull(dayOffsetsOf(listOf(pt("2026-9-1", 1f))))
    }

    // ---- midTickIndex：中间刻度取「日期中点」而非「序号中点」 ----

    @Test
    fun `mid tick follows the time midpoint not the index midpoint`() {
        // 日期：09-15, 09-16, 09-17, 09-18, 10-05 → 偏移 [0,1,2,3,20]，跨度 20 天
        // 日期中点是 +10 天，最近的点是 index 3（+3 天）；而序号中点是 index 2。
        val offsets = listOf(0L, 1L, 2L, 3L, 20L)
        assertEquals(3, midTickIndex(5, offsets, 20L))
    }

    @Test
    fun `mid tick falls back to index midpoint without offsets`() {
        assertEquals(2, midTickIndex(5, null, 0L))
        assertEquals(0, midTickIndex(2, null, 0L))
        assertEquals(0, midTickIndex(1, null, 0L))
    }

    // ---- niceScale：间隔数受 maxIntervals 约束 ----

    @Test
    fun `scale keeps interval count within the requested budget`() {
        val cases = listOf(
            listOf(3.8f, 4.2f),
            listOf(1f, 10f),
            listOf(0f, 100f),
            listOf(36.2f, 37.9f),
            listOf(120f, 155f),
            listOf(-3f, 7f),
        )
        for (target in 2..5) {
            for (vals in cases) {
                val (lo, hi, step) = niceScale(vals, null, target)
                val intervals = Math.round((hi - lo) / step)
                assertTrue(
                    "vals=$vals target=$target → 间隔数 $intervals 超出预算（lo=$lo hi=$hi step=$step）",
                    intervals <= target,
                )
                // 必须覆盖全部数据点
                assertTrue("lo 未覆盖最小值：vals=$vals lo=$lo", lo <= vals.min() + 1e-4f)
                assertTrue("hi 未覆盖最大值：vals=$vals hi=$hi", hi >= vals.max() - 1e-4f)
                assertTrue("step 必须为正", step > 0f)
            }
        }
    }

    @Test
    fun `threshold is always included in the scale range`() {
        val (lo, hi, _) = niceScale(listOf(3.2f, 3.4f), 4.0f, 4)
        assertTrue("阈值 4.0 必须落在 [$lo, $hi] 内", lo <= 4.0f && hi >= 4.0f)
    }

    /** 旧实现固定按 4 段取值；显式传入不同预算应得到不同的步长。 */
    @Test
    fun `larger budget yields finer step`() {
        val coarse = niceScale(listOf(0f, 10f), null, 2).third
        val fine = niceScale(listOf(0f, 10f), null, 5).third
        assertTrue("预算越大步长应越小（coarse=$coarse fine=$fine）", fine <= coarse)
    }

    // ---- summarizeValues：读数口径 ----

    @Test
    fun `summary reports last average delta and over-threshold count`() {
        val points = listOf(
            pt("2026-09-15", 5.4f),
            pt("2026-09-16", 5.0f),
            pt("2026-09-20", 4.2f),
            pt("2026-09-21", 3.6f),
        )
        val s = summarizeValues(points, 4.0f)
        assertEquals(3.6f, s.last, 1e-4f)
        assertEquals(4.55f, s.avg, 1e-4f)
        assertEquals(-1.8f, s.delta!!, 1e-4f)
        // 严格大于阈值：5.4 / 5.0 / 4.2 三次；4.0 本身不算
        assertEquals(3, s.overCount)
        assertEquals(4, s.total)
    }

    @Test
    fun `summary on a single point has no delta`() {
        val s = summarizeValues(listOf(pt("2026-09-15", 3f)), null)
        assertNull(s.delta)
        assertEquals(0, s.overCount)
        assertEquals(1, s.total)
    }

    @Test
    fun `over threshold uses strict comparison`() {
        assertEquals(0, summarizeValues(listOf(pt("d", 4.0f)), 4.0f).overCount)
        assertEquals(1, summarizeValues(listOf(pt("d", 4.01f)), 4.0f).overCount)
    }

    // ---- nearestIndex：拖动命中必须与日期定位一致（v1.0.46 修复的缺陷）----

    /**
     * v1.0.45 的缺陷回归：X 轴按日期间隔分布后，按「序号比例」映射会选错点。
     * 偏移 [0,1,2,20]：手指在 60% 宽度处，按日期距离最近的是画在 100% 的末点（距离 0.4）；
     * 旧实现按序号比例选中 index 2——那个点画在 10% 处，气泡会跳到离手指很远的地方。
     */
    @Test
    fun `nearest index follows date positions not index ratio`() {
        val offsets = listOf(0L, 1L, 2L, 20L)
        assertEquals(3, nearestIndex(0.6f, 4, 0f, 1f, offsets, 20L))
    }

    @Test
    fun `nearest index falls back to ratio without offsets`() {
        assertEquals(2, nearestIndex(0.6f, 4, 0f, 1f))
        assertEquals(0, nearestIndex(0f, 4, 0f, 1f))
        assertEquals(3, nearestIndex(1f, 4, 0f, 1f))
        assertEquals(0, nearestIndex(0.5f, 1, 0f, 1f))
    }

    @Test
    fun `nearest index clamps touches outside the plot`() {
        val offsets = listOf(0L, 5L, 10L)
        assertEquals(0, nearestIndex(-5f, 3, 0f, 1f, offsets, 10L))
        assertEquals(2, nearestIndex(99f, 3, 0f, 1f, offsets, 10L))
    }
}
