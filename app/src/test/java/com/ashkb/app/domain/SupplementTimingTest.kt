package com.ashkb.app.domain

import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.Supplement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.38：B11 补剂与用药「时间错开」提醒（钙等矿物与螯合类药物需间隔 2h 以上）。
 *
 * ⚠️ v1.2.10（审查 CRITICAL-1）：**本文件的所有时刻夹具此前写的是 CSV**（`"08:00"`、
 * `"08:00,08:30"`），而库里存的是 **JSON 数组**（`["08:00"]`）。单值 CSV 在旧的
 * `split(',')` 实现下恰好能跑通，所以这些测试**八年全绿却从未覆盖过生产格式**——
 * 真正跑生产数据时 `["08:00"]` 切出 `["[\"08:00\"]"]`，`minutesOf` 判非法 → 空表 →
 * `conflicts()` 恒空。夹具现已一律改用真格式。
 *
 * 教训：**夹具必须走写入侧的同一条路径产出的形状**，否则测试只是在自我确认。
 */
class SupplementTimingTest {

    /** 与写入侧 `JSONArray(list).toString()` 同形。 */
    private fun timesJson(vararg t: String) = t.joinToString(",", "[", "]") { "\"$it\"" }

    private fun sup(name: String = "钙片", category: String = "CALCIUM", times: String? = """["08:00"]""") =
        Supplement(
            id = "sup-$name", name = name, category = category, dose = "500mg", times = times,
            createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
        )

    private fun med(name: String, nameKey: String, takeTimes: String? = """["08:00"]""") = Medication(
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

    /**
     * CRITICAL-1 的定点回归：**真格式 JSON 必须被解析出槽位**。
     *
     * 旧实现在这里返回空表，于是「钙片与优甲乐同服」这条提醒在生产中一次都没响过。
     */
    @Test
    fun `JSON 时刻被解析而不是被当成 CSV`() {
        assertEquals(listOf("08:00", "20:00"), ScheduleCalc.parseTimes("""["08:00","20:00"]"""))
        assertEquals(listOf("08:00"), ScheduleCalc.parseTimes("""["08:00"]"""))
        // 空数组 → 空表。旧实现 `split(',')` 会切出 1 段（"[]" 本身非空），这是个更隐蔽的洞
        assertTrue(ScheduleCalc.parseTimes("[]").isEmpty())
        // 脏数据丢弃而不抛
        assertTrue(ScheduleCalc.parseTimes("08:00,20:00").isEmpty())
        assertTrue(ScheduleCalc.parseTimes("not-json").isEmpty())
        assertTrue(ScheduleCalc.parseTimes(null).isEmpty())
        assertTrue(ScheduleCalc.parseTimes("").isEmpty())
        // 非法时刻被过滤（防 LocalTime.parse 崩溃）
        assertEquals(listOf("08:00"), ScheduleCalc.parseTimes("""["08:00","25:99"]"""))
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
            listOf(sup(times = timesJson("08:00"))),
            listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("08:00"))),
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
                listOf(sup(times = timesJson("09:00"))),
                listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("08:00"))),
            ).isNotEmpty()
        )
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = timesJson("10:00"))),
                listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("08:00"))),
            ).isEmpty()
        )
    }

    /** 非矿物补剂 / 非螯合药物 → 不报冲突。 */
    @Test
    fun `non mineral supplement or non chelating med does not conflict`() {
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(category = "OMEGA3", times = timesJson("08:00"))),
                listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("08:00"))),
            ).isEmpty()
        )
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = timesJson("08:00"))),
                listOf(med("甲氨蝶呤", "mtx", takeTimes = timesJson("08:00"))),
            ).isEmpty()
        )
    }

    /** 同名组合去重（多槽位交叉命中只报一条）。 */
    @Test
    fun `conflicts are deduplicated by name pair`() {
        val c = SupplementTiming.conflicts(
            listOf(sup(times = timesJson("08:00", "08:30"))),
            listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("08:00", "08:15"))),
        )
        assertEquals(1, c.size)
    }

    /**
     * v1.2.10（审查 L4）：去重时**保留最短间隔**，不是遍历顺序里的第一条。
     *
     * 药时刻在外层、补剂时刻在内层，于是「药 07:30 / 09:00 × 钙 08:00 / 20:00」的命中顺序是
     * (08:00,07:30)=30 → (08:00,09:00)=60 → (20:00,07:30)=690 … 旧实现报的是 **30**，
     * 这里把顺序倒过来（药 08:00 × 钙 07:30,20:00：先 30 后 690）也一样；
     * 关键是当**第一条不是最短**时必须取最短：药 07:00 / 09:00、钙 07:50 / 10:30
     * → 命中 (07:50,07:00)=50、(07:50,09:00)=70、(10:30,09:00)=90，(10:30,07:00)=210 超阈值
     * → 第一条就是 50（最短），所以换个构造：药 09:00 / 07:00、钙 07:50
     * → 顺序 (07:50,09:00)=70、(07:50,07:00)=50 —— 旧实现给 70，正确答案是 50。
     */
    @Test
    fun `同名组合取最短间隔而不是第一条`() {
        val c = SupplementTiming.conflicts(
            listOf(sup(times = timesJson("07:50"))),
            listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("09:00", "07:00"))),
        )
        assertEquals(1, c.size)
        assertEquals("应在 70 与 50 中取 50", 50, c.first().gapMinutes)
    }

    /** 多对组合各自取自己的最短间隔，且顺序按首次出现稳定。 */
    @Test
    fun `多对组合各自取最短且顺序稳定`() {
        val c = SupplementTiming.conflicts(
            listOf(
                sup(name = "钙片", times = timesJson("07:50", "20:00")),
                sup(name = "镁片", category = "镁", times = timesJson("12:00")),
            ),
            listOf(
                med("优甲乐", "levothyroxine", takeTimes = timesJson("09:00", "07:00")),
                med("硫酸亚铁", "ferrous", takeTimes = timesJson("12:30")),
            ),
        )
        // 钙片 × 硫酸亚铁（280 / 450 分钟）与镁片 × 优甲乐（180 / 300 分钟）都够 2 小时，不报
        assertEquals(
            listOf("钙片" to "优甲乐", "镁片" to "硫酸亚铁"),
            c.map { it.supplementName to it.medName },
        )
        assertEquals(listOf(50, 30), c.map { it.gapMinutes })
    }

    /** 缺时刻（PRN 药 / 未设时刻的补剂）→ 不报冲突，不能凭空臆测。 */
    @Test
    fun `missing times produce no conflict`() {
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = null)),
                listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("08:00"))),
            ).isEmpty()
        )
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = timesJson("08:00"))),
                listOf(med("优甲乐", "levothyroxine", takeTimes = null)),
            ).isEmpty()
        )
        // 空数组 = 没设时刻，同样不报——不能把「没填」当成「00:00」
        assertTrue(
            SupplementTiming.conflicts(
                listOf(sup(times = "[]")),
                listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("08:00"))),
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
            listOf(sup(times = timesJson("00:15"))),
            listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("23:50"))),
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
                listOf(sup(times = timesJson("00:30"))),
                listOf(med("优甲乐", "levothyroxine", takeTimes = timesJson("22:00"))),
            ).isEmpty()
        )
    }
}
