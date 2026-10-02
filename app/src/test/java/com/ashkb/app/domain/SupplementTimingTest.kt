package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Supplement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.38：B11 补剂与用药「时间错开」提醒（钙等矿物与螯合类药物需间隔 2h 以上）。 */
class SupplementTimingTest {

    private fun sup(name: String = "钙片", category: String = "CALCIUM", times: String? = "08:00") = Supplement(
        id = "sup-$name", name = name, category = category, dose = "500mg", times = times,
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    private fun med(name: String, nameKey: String, takeTimes: String? = "08:00") = Medication(
        id = "med-$name", name = name, nameKey = nameKey, medClass = "OTHER",
        route = "oral", dose = "1片", frequency = "DAILY", takeTimes = takeTimes,
        startDate = "2026-01-01", createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    @Test
    fun `minutes parsing`() {
        assertEquals(0, SupplementTiming.minutesOf("00:00"))
        assertEquals(510, SupplementTiming.minutesOf("08:30"))
        assertNull(SupplementTiming.minutesOf("24:00"))
        assertNull(SupplementTiming.minutesOf("8"))
        assertNull(SupplementTiming.minutesOf("aa:bb"))
    }

    @Test
    fun `mineral and chelating detection`() {
        assertTrue(SupplementTiming.isMineral("CALCIUM"))
        assertTrue(SupplementTiming.isMineral("钙"))
        assertFalse(SupplementTiming.isMineral("OMEGA3"))
        assertTrue(SupplementTiming.isChelatingMed(med("优甲乐", "levothyroxine")))
        assertTrue(SupplementTiming.isChelatingMed(med("硫酸亚铁", "ferrous")))
        assertFalse(SupplementTiming.isChelatingMed(med("甲氨蝶呤", "mtx")))
    }

    /** 同服（间隔 0 分钟）→ 报冲突。 */
    @Test
    fun `same time calcium and levothyroxine conflict`() {
        val c = SupplementTiming.conflicts(
            listOf(sup(times = "08:00")),
            listOf(med("优甲乐", "levothyroxine", takeTimes = "08:00")),
        )
        assertEquals(1, c.size)
        assertEquals("钙片", c.first().supplementName)
        assertEquals("优甲乐", c.first().medName)
        assertEquals(0, c.first().gapMinutes)
    }

    /** 间隔不足 2 小时 → 报冲突；达到 2 小时 → 不报。 */
    @Test
    fun `gap under two hours conflicts and two hours is fine`() {
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = "09:00")),
                listOf(med("优甲乐", "levothyroxine", takeTimes = "08:00")),
            ).isNotEmpty()
        )
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = "10:00")),
                listOf(med("优甲乐", "levothyroxine", takeTimes = "08:00")),
            ).isEmpty()
        )
    }

    /** 非矿物补剂 / 非螯合药物 → 不报冲突。 */
    @Test
    fun `non mineral supplement or non chelating med does not conflict`() {
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(category = "OMEGA3", times = "08:00")),
                listOf(med("优甲乐", "levothyroxine", takeTimes = "08:00")),
            ).isEmpty()
        )
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = "08:00")),
                listOf(med("甲氨蝶呤", "mtx", takeTimes = "08:00")),
            ).isEmpty()
        )
    }

    /** 同名组合去重（多槽位交叉命中只报一条）。 */
    @Test
    fun `conflicts are deduplicated by name pair`() {
        val c = SupplementTiming.conflicts(
            listOf(sup(times = "08:00,08:30")),
            listOf(med("优甲乐", "levothyroxine", takeTimes = "08:00,08:15")),
        )
        assertEquals(1, c.size)
    }

    /** 缺时刻（PRN 药 / 未设时刻的补剂）→ 不报冲突，不能凭空臆测。 */
    @Test
    fun `missing times produce no conflict`() {
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = null)),
                listOf(med("优甲乐", "levothyroxine", takeTimes = "08:00")),
            ).isEmpty()
        )
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = "08:00")),
                listOf(med("优甲乐", "levothyroxine", takeTimes = null)),
            ).isEmpty()
        )
    }

    /**
     * v1.1.1（M2）：**跨午夜按环形距离算**。
     *
     * 左甲状腺素 23:50 + 钙剂 00:15 的真实间隔是 25 分钟（典型螯合冲突），
     * 而旧的 `abs(st - mt)` 给出 1415 分钟 → 120 分钟规则不触发 → **这条提醒在跨零点时静默失效**。
     */
    @Test
    fun `跨午夜的螯合冲突按环形距离报出`() {
        val c = SupplementTiming.conflicts(
            listOf(sup(times = "00:15")),
            listOf(med("优甲乐", "levothyroxine", takeTimes = "23:50")),
        )
        assertEquals(1, c.size)
        assertEquals("23:50 → 00:15 是 25 分钟，不是 1415", 25, c.first().gapMinutes)
    }

    /** 环形距离本身：取值域 [0, 720]，超过半天的那一侧永远不是"最短间隔"。 */
    @Test
    fun `环形距离的边界`() {
        assertEquals(0, SupplementTiming.circularGapMinutes(0, 0))
        assertEquals(25, SupplementTiming.circularGapMinutes(1430, 15))      // 23:50 与 00:15
        assertEquals(720, SupplementTiming.circularGapMinutes(0, 720))       // 恰好半天，两侧一样
        assertEquals(120, SupplementTiming.circularGapMinutes(1380, 60))     // 23:00 与 01:00
        assertEquals(150, SupplementTiming.circularGapMinutes(1320, 30))     // 22:00 与 00:30
    }

    /** 跨午夜但间隔够（≥ 2h）→ 仍然不报，不能把环形距离用成"到处都报"。 */
    @Test
    fun `跨午夜但间隔足够时不报冲突`() {
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = "00:30")),
                listOf(med("优甲乐", "levothyroxine", takeTimes = "22:00")),
            ).isEmpty()
        )
    }
}
