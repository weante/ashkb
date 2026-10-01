package com.ashkb.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.0.77（批次 4）：日期输入的边界校验（第三份审查报告 §六的回归网）。
 *
 * 锁的核心是**不存在的日期必须被拒绝**——旧实现用正则取到年月日后直接 `%02d` 拼接，
 * `2026-13-45`、`2026-02-31`、平年 2 月 29 日都会原样写库。
 */
class DateInputTest {

    @Test
    fun `不存在的月份与日期一律拒绝`() {
        assertNull(DateInput.normalizeOrNull("2026-13-45"))
        assertNull(DateInput.normalizeOrNull("2026-00-10"))
        assertNull(DateInput.normalizeOrNull("2026-01-00"))
        assertNull(DateInput.normalizeOrNull("2026-02-31"))
        assertNull(DateInput.normalizeOrNull("2026-04-31"))
        assertNull(DateInput.normalizeOrNull("2026-01-32"))
    }

    @Test
    fun `闰年规则按真实日历判定`() {
        assertEquals("2024-02-29", DateInput.normalizeOrNull("2024-02-29"))
        assertNull("2023 不是闰年", DateInput.normalizeOrNull("2023-02-29"))
        assertEquals("2000-02-29", DateInput.normalizeOrNull("2000-02-29"))
        assertNull("1900 不是闰年（百年不闰）", DateInput.normalizeOrNull("1900-02-29"))
    }

    @Test
    fun `宽松写法能规范化`() {
        assertEquals("2026-07-31", DateInput.normalizeOrNull("2026/7/31"))
        assertEquals("2026-07-31", DateInput.normalizeOrNull("2026.07.31"))
        assertEquals("2026-07-31", DateInput.normalizeOrNull("2026年7月31日"))
        assertEquals("2026-07-01", DateInput.normalizeOrNull("2026-7-1"))
        assertEquals("2026-07-31", DateInput.normalizeOrNull("  2026-07-31  "))
    }

    @Test
    fun `严格 ISO 原样通过`() {
        assertEquals("2026-07-31", DateInput.normalizeOrNull("2026-07-31"))
        assertEquals(LocalDate.of(2026, 7, 31), DateInput.parseOrNull("2026-07-31"))
    }

    @Test
    fun `空值与胡写一律 null`() {
        assertNull(DateInput.normalizeOrNull(null))
        assertNull(DateInput.normalizeOrNull(""))
        assertNull(DateInput.normalizeOrNull("   "))
        assertNull(DateInput.normalizeOrNull("下周三"))
        assertNull(DateInput.normalizeOrNull("2026"))
        assertNull(DateInput.normalizeOrNull("31/07/2026"))
        assertNull(DateInput.parseOrNull("2026-13-45"))
    }

    @Test
    fun `年份范围兜住脏数据`() {
        assertNull(DateInput.normalizeOrNull("0000-01-01"))
        assertNull(DateInput.normalizeOrNull("99999-01-01"))
    }
}
