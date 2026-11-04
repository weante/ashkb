package com.ashkb.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V-3（外部 UI 审查）：逐日数值列表的两处**纯文本规则**。
 *
 * 为什么只测这两个函数：`DailyValuesList` 本身是 Compose UI，JVM 上渲染不了；
 * 但它「怎么把一天的日期和数值写成一行」是纯函数，而且正是最容易退化的地方——
 * 单位是 `/10` 时贴成 `7/10`、是 `kg` 时留空格、整数不写 `.0`。
 * 走回旧写法（`" 分"` 那种自带前导空格、数值一股脑 `toString()`）测试会变红。
 */
class DailyValuesListTest {

    // ---- dailyValueText：数值 + 单位 ----

    @Test
    fun `整数不补小数点`() {
        assertEquals("7", dailyValueText(7f, ""))
        assertEquals("65", dailyValueText(65f, ""))
    }

    @Test
    fun `小数保留一位`() {
        assertEquals("7.5", dailyValueText(7.5f, ""))
        assertEquals("65.2", dailyValueText(65.2f, ""))
    }

    @Test
    fun `后缀式单位直接贴上去`() {
        // 疼痛评分用 "/10"：写成 "7 /10" 或 "7.0/10" 都不对
        assertEquals("7/10", dailyValueText(7f, "/10"))
        assertEquals("7.5/10", dailyValueText(7.5f, "/10"))
    }

    @Test
    fun `词式单位留一个空格`() {
        assertEquals("65.2 kg", dailyValueText(65.2f, "kg"))
        assertEquals("120 mmHg", dailyValueText(120f, " mmHg"))
    }

    @Test
    fun `没有单位就不留尾巴`() {
        // 空串与空白串都必须退化成"只有数字"，不能留下孤立空格
        assertEquals("5", dailyValueText(5f, ""))
        assertEquals("5", dailyValueText(5f, "   "))
    }

    // ---- dailyDateText：与坐标轴同一口径 ----

    @Test
    fun `完整日期取月日`() {
        assertEquals("09-15", dailyDateText("2026-09-15"))
    }

    @Test
    fun `跨年也一样只取月日`() {
        // 与 TrendChart 的 dateTick 同口径：短日期为主，完整年份留给语义描述
        assertEquals("12-30", dailyDateText("2025-12-30"))
        assertEquals("01-02", dailyDateText("2026-01-02"))
    }

    @Test
    fun `非标准长度原样返回不崩`() {
        // 历史脏数据（导入时被截断的日期）不能让列表整块消失
        assertEquals("9-15", dailyDateText("9-15"))
        assertEquals("", dailyDateText(""))
    }
}
