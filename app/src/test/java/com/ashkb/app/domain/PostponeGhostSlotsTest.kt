package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 判据（`[顺延当天, 生效日)` 半开区间）的纯函数测试。
 *
 * 这条判据决定**删哪些历史计划行**，是本次改动里唯一「删用户数据」的地方，
 * 所以每个边界都单独钉一条：
 * - 顺延「今天 → 明天」：只删今天；
 * - 顺延「今天 → 两周后」：删今天起、生效日前一天止；
 * - 生效日当天**不删**（那是新锚点的领地，物化会写）；
 * - 顺延到当天或更早：区间为空，什么都不删（防脏数据把真实历史抹掉）；
 * - 脏日期串：不动。
 */
class PostponeGhostSlotsTest {

    @Test
    fun `today to tomorrow deletes only today`() {
        val range = PostponeGhostSlots.range(
            recordedAt = "2026-10-15T09:12:00",
            effectiveDate = "2026-10-16",
        )
        assertEquals("2026-10-15" to "2026-10-16", range)
    }

    @Test
    fun `two week postpone covers the whole gap but stops before the effective day`() {
        val (from, to) = PostponeGhostSlots.range(
            recordedAt = "2026-10-01T20:00:00",
            effectiveDate = "2026-10-15",
        )!!
        assertEquals("2026-10-01", from)
        assertEquals("2026-10-15", to)
        // 半开区间：生效日当天不算幽灵（新锚点在那里才该有槽位）。
        // 用 assertTrue 而不是 kotlin 的 assert：后者依赖 -ea 才生效，在 Gradle 默认
        // 测试 JVM 上是静默空操作——一条永远通过的断言比没有断言更危险。
        assertTrue("生效日必须被排除在删除区间之外", "2026-10-15" >= to)
    }

    @Test
    fun `postponing to the same day yields no range`() {
        assertNull(
            PostponeGhostSlots.range(
                recordedAt = "2026-10-15T09:12:00",
                effectiveDate = "2026-10-15",
            ),
        )
    }

    @Test
    fun `postponing to an earlier day yields no range`() {
        assertNull(
            PostponeGhostSlots.range(
                recordedAt = "2026-10-15T09:12:00",
                effectiveDate = "2026-10-01",
            ),
        )
    }

    @Test
    fun `fractional seconds in recordedAt still parse the date`() {
        // nowIso() 用 ISO_LOCAL_DATE_TIME，纳秒非零时会追加小数秒
        val range = PostponeGhostSlots.range(
            recordedAt = "2026-10-15T09:12:00.123456",
            effectiveDate = "2026-10-16",
        )
        assertEquals("2026-10-15" to "2026-10-16", range)
    }

    @Test
    fun `dirty timestamps are left alone`() {
        assertNull(PostponeGhostSlots.range(recordedAt = "", effectiveDate = "2026-10-16"))
        assertNull(PostponeGhostSlots.range(recordedAt = "not-a-date", effectiveDate = "2026-10-16"))
        assertNull(PostponeGhostSlots.range(recordedAt = "2026-10-15T09:12:00", effectiveDate = "x"))
    }
}
