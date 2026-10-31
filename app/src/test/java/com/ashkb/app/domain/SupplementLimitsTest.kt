package com.ashkb.app.domain

import com.ashkb.app.data.entity.Supplement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.38：B11 营养素每日上限警示（不内置医学上限，只做用户设定值的算术比较）。
 *
 * ⚠️ v1.2.10（审查 CRITICAL-1）：时刻夹具由 CSV（`"08:00,20:00"`）改为**真格式 JSON**。
 * 旧实现的 `split(',')` 让 CSV 夹具恰好也数出 2 段，于是测试全绿、格式始终是假的；
 * 更隐蔽的是 `"[]"` 也切出 1 段 → 「设了频次、没设时刻」被算成每天 1 次 →
 * 当日累计剂量低估一半 → **超上限警示该响时不响**。`[]` 的回归见下。
 */
class SupplementLimitsTest {

    /** 与写入侧 `JSONArray(list).toString()` 同形。 */
    private fun timesJson(vararg t: String) = t.joinToString(",", "[", "]") { "\"$it\"" }

    private fun sup(
        category: String = "CALCIUM",
        dose: String = "500mg",
        frequency: String = "daily",
        times: String? = null,
        doseAmount: Double? = null,
        doseUnit: String? = null,
        dailyMax: Double? = null,
        dailyMaxUnit: String? = null,
    ) = Supplement(
        id = "sup-1", name = "钙片", category = category, dose = dose,
        frequency = frequency, times = times,
        doseAmount = doseAmount, doseUnit = doseUnit, dailyMax = dailyMax,
        dailyMaxUnit = dailyMaxUnit,
        createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00",
    )

    @Test
    fun `doses per day prefers explicit time slots`() {
        assertEquals(2, SupplementLimits.dosesPerDay(sup(times = timesJson("08:00", "20:00"))))
        assertEquals(3, SupplementLimits.dosesPerDay(sup(times = timesJson("08:00", "12:00", "20:00"))))
        assertEquals(1, SupplementLimits.dosesPerDay(sup(times = "")))
    }

    /**
     * CRITICAL-1 定点回归：**空数组不是「一个槽位」**。
     *
     * 旧实现 `times?.split(',')?.count { isNotBlank() }` 对 `"[]"` 返回 **1**（`"[]"` 自身非空），
     * 于是 frequency=bid 的补剂被算成每天 1 次，而它明明该按频次算 2 次。
     */
    @Test
    fun `empty JSON array falls back to frequency instead of counting as one slot`() {
        assertEquals(2, SupplementLimits.dosesPerDay(sup(frequency = "bid", times = "[]")))
        assertEquals(3, SupplementLimits.dosesPerDay(sup(frequency = "tid", times = "[]")))
        // 非法 JSON（历史脏值）同样走频次兜底，而不是被算成 1 次
        assertEquals(2, SupplementLimits.dosesPerDay(sup(frequency = "bid", times = "08:00,20:00")))
    }

    @Test
    fun `doses per day falls back to frequency`() {
        assertEquals(1, SupplementLimits.dosesPerDay(sup(frequency = "daily")))
        assertEquals(2, SupplementLimits.dosesPerDay(sup(frequency = "bid")))
        assertEquals(3, SupplementLimits.dosesPerDay(sup(frequency = "tid")))
    }

    @Test
    fun `daily total multiplies amount by doses`() {
        assertEquals(
            1000.0,
            SupplementLimits.dailyTotal(sup(doseAmount = 500.0, times = timesJson("08:00", "20:00")))!!,
            0.001,
        )
        assertEquals(500.0, SupplementLimits.dailyTotal(sup(doseAmount = 500.0, frequency = "daily"))!!, 0.001)
    }

    /** 未量化 / 未设上限 / 上限非正 → 一律不警示（null）。 */
    @Test
    fun `cannot judge returns null`() {
        assertNull(SupplementLimits.dailyTotal(sup(doseAmount = null)))
        assertNull(SupplementLimits.dailyTotal(sup(doseAmount = 0.0)))
        assertNull(SupplementLimits.exceedsDailyMax(sup(doseAmount = 500.0, dailyMax = null)))
        assertNull(SupplementLimits.exceedsDailyMax(sup(doseAmount = null, dailyMax = 1000.0)))
        assertNull(SupplementLimits.exceedsDailyMax(sup(doseAmount = 500.0, dailyMax = 0.0)))
    }

    @Test
    fun `exceeds only when total is above the user limit`() {
        assertFalse(
            SupplementLimits.exceedsDailyMax(
                sup(doseAmount = 500.0, times = timesJson("08:00", "20:00"), dailyMax = 1000.0)
            )!!
        )
        assertTrue(
            SupplementLimits.exceedsDailyMax(
                sup(doseAmount = 500.0, times = timesJson("08:00", "20:00"), dailyMax = 800.0)
            )!!
        )
        assertTrue(SupplementLimits.exceedsDailyMax(sup(doseAmount = 600.0, frequency = "daily", dailyMax = 500.0))!!)
    }

    @Test
    fun `unit label trims and blanks to empty`() {
        assertEquals("mg", SupplementLimits.unitLabel(sup(doseUnit = " mg ")))
        assertEquals("", SupplementLimits.unitLabel(sup(doseUnit = "   ")))
        assertEquals("", SupplementLimits.unitLabel(sup(doseUnit = null)))
    }

    // ---- v1.2.13（审查报告 §十 第 2 条）：上限有了自己的单位 ----

    /**
     * 这一批的核心：**单位不同不是「没超」，是「判不了」**。
     *
     * 修复前 UI 把同一个 `doseUnit` 同时贴给「当日累计」与「上限」两处，于是
     * 「每次 1 粒 / 每日上限 1000 mg」被算成 `1 > 1000` → false → 永远静默，
     * 而用户看到的是「没警告 = 安全」。这里把三态钉死。
     */
    @Test
    fun `unit match distinguishes same assumed and mismatch`() {
        assertEquals(
            SupplementLimits.UnitMatch.SAME,
            SupplementLimits.unitMatch(sup(doseUnit = "mg", dailyMax = 1000.0, dailyMaxUnit = "mg")),
        )
        // 归一化只做 trim + 忽略大小写：用户手打 `MG` 不该被判成另一种单位
        assertEquals(
            SupplementLimits.UnitMatch.SAME,
            SupplementLimits.unitMatch(sup(doseUnit = " mg ", dailyMax = 1000.0, dailyMaxUnit = "MG")),
        )
        // 上限没填单位 = 沿用单次剂量单位（存量数据的口径），但要能被界面识别出来是「假设」
        assertEquals(
            SupplementLimits.UnitMatch.ASSUMED,
            SupplementLimits.unitMatch(sup(doseUnit = "mg", dailyMax = 1000.0, dailyMaxUnit = null)),
        )
        assertEquals(
            SupplementLimits.UnitMatch.ASSUMED,
            SupplementLimits.unitMatch(sup(doseUnit = "mg", dailyMax = 1000.0, dailyMaxUnit = "  ")),
        )
        assertEquals(
            SupplementLimits.UnitMatch.MISMATCH,
            SupplementLimits.unitMatch(sup(doseUnit = "粒", dailyMax = 1000.0, dailyMaxUnit = "mg")),
        )
        // 上限没填 → 根本不存在比较，也就没有单位问题
        assertNull(SupplementLimits.unitMatch(sup(doseUnit = "mg", dailyMax = null, dailyMaxUnit = "mg")))
        // 单次剂量没填单位 → 连「同单位」这个前提都没有，判不了
        assertNull(SupplementLimits.unitMatch(sup(doseUnit = null, dailyMax = 1000.0, dailyMaxUnit = "mg")))
    }

    /**
     * 单位不同时 `exceedsDailyMax` 必须是 **null**，不能是 false。
     *
     * 断言 `assertNull` 而不是 `assertFalse` 是这条测试的全部意义：返回值里 false 与 null 的
     * 区别，正好就是「比过了没超」与「没法比」的区别，而 UI 对后者的处理是**明说判不了**。
     */
    @Test
    fun `mismatched units cannot be judged instead of silently passing`() {
        assertNull(
            SupplementLimits.exceedsDailyMax(
                sup(doseAmount = 1.0, doseUnit = "粒", frequency = "daily", dailyMax = 1000.0, dailyMaxUnit = "mg")
            ),
        )
        // 对照：把上限单位改成「粒」之后，同一个 1 粒就有了意义 → 会判 false（1 粒没超 1000 粒）
        assertFalse(
            SupplementLimits.exceedsDailyMax(
                sup(doseAmount = 1.0, doseUnit = "粒", frequency = "daily", dailyMax = 1000.0, dailyMaxUnit = "粒")
            )!!,
        )
    }

    /**
     * 上限展示单位必须与比较口径一致。
     *
     * 若 `limitUnitLabel` 在没填时返回空串，界面就会写成「上限 1000 」（数字后一个空格），
     * 而实际比较用的是 mg——用户看到的口径和算出来的结论互相矛盾。
     */
    @Test
    fun `limit unit label falls back to dose unit`() {
        assertEquals(
            "mg",
            SupplementLimits.limitUnitLabel(sup(doseUnit = "mg", dailyMax = 1000.0, dailyMaxUnit = null)),
        )
        assertEquals(
            "IU",
            SupplementLimits.limitUnitLabel(sup(doseUnit = "mg", dailyMax = 1000.0, dailyMaxUnit = " IU ")),
        )
        assertEquals("", SupplementLimits.limitUnitLabel(sup(doseUnit = null, dailyMax = 1000.0, dailyMaxUnit = null)))
    }
}
