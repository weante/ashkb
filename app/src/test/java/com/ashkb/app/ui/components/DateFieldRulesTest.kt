package com.ashkb.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.78（批次 4 收尾）：表单日期字段的校验规则（[DateFieldRules]）。
 *
 * 这些规则同时决定两件事：**输入框红不红**与**保存按钮能不能点**——两者共用同一判定，
 * 所以「提示说格式不对、按钮却还能点」这类自相矛盾不会出现。锁住它，是因为
 * `2026-13-45` 一旦落库，趋势图 / PDF / 依从统计都会带着一个假日期继续跑，且没人会发现。
 */
class DateFieldRulesTest {

    @Test
    fun `必填日期留空不接受`() {
        // 日期列是 NOT NULL，空串会变成一条「没有日期的记录」
        assertFalse(DateFieldRules.requiredOk(""))
        assertFalse(DateFieldRules.requiredOk("   "))
    }

    @Test
    fun `必填日期非法不接受`() {
        assertFalse(DateFieldRules.requiredOk("2026-13-45"))
        assertFalse(DateFieldRules.requiredOk("2026-02-31"))
        assertFalse(DateFieldRules.requiredOk("2023-02-29")) // 平年没有 2 月 29 日
        assertFalse(DateFieldRules.requiredOk("下周三"))
        assertFalse(DateFieldRules.requiredOk("2026"))
    }

    @Test
    fun `必填日期合法与宽松写法都接受`() {
        assertTrue(DateFieldRules.requiredOk("2026-07-31"))
        assertTrue(DateFieldRules.requiredOk("2026/7/31"))
        assertTrue(DateFieldRules.requiredOk("2024-02-29")) // 闰年
    }

    @Test
    fun `可选日期留空合法`() {
        assertTrue(DateFieldRules.optionalOk(""))
        assertTrue(DateFieldRules.optionalOk("   "))
    }

    @Test
    fun `可选日期一旦填了就必须合法`() {
        assertTrue(DateFieldRules.optionalOk("2026-07-31"))
        assertFalse(DateFieldRules.optionalOk("2026-02-30"))
        assertFalse(DateFieldRules.optionalOk("随便写"))
    }

    @Test
    fun `落库值规范化成 ISO，留空与非法都是 null`() {
        assertEquals("2026-07-31", DateFieldRules.toIsoOrNull("2026/7/31"))
        assertEquals("2026-07-31", DateFieldRules.toIsoOrNull(" 2026-07-31 "))
        assertNull(DateFieldRules.toIsoOrNull(""))
        assertNull(DateFieldRules.toIsoOrNull("2026-13-45"))
    }
}
