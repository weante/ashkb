package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.39：B7 周期康复计划模板与 week_structure 编解码。 */
class ExercisePlanTemplatesTest {

    @Test
    fun `json roundtrip preserves spec including quotes and backslashes`() {
        val spec = listOf(
            ExercisePlanTemplates.WeekSpec(1, "L1", 3, "轻柔维持"),
            ExercisePlanTemplates.WeekSpec(2, "L1+L2", 4, "含引号\"与反斜杠\\的说明"),
        )
        val back = ExercisePlanTemplates.parse(ExercisePlanTemplates.toJson(spec))
        assertEquals(spec, back)
    }

    /**
     * v1.0.42：说明里含**花括号 / 换行 / 制表**也必须能往返。
     * 旧正则版正是被末尾未转义的 `}` 拖垮（Android ICU 报 PatternSyntaxException）。
     */
    @Test
    fun `parse handles braces newlines and tabs inside note`() {
        val spec = listOf(
            ExercisePlanTemplates.WeekSpec(1, "L1", 3, "含{花括号}与单独}的说明"),
            ExercisePlanTemplates.WeekSpec(2, "L2", 5, "含换行\n与制表\t的说明"),
        )
        val back = ExercisePlanTemplates.parse(ExercisePlanTemplates.toJson(spec))
        assertEquals(spec, back)
    }

    /** 脏数据不抛异常：整体无法解析 → 空；部分可解析 → 只保留能解析的周。 */
    @Test
    fun `parse tolerates dirty input`() {
        assertTrue(ExercisePlanTemplates.parse(null).isEmpty())
        assertTrue(ExercisePlanTemplates.parse("").isEmpty())
        assertTrue(ExercisePlanTemplates.parse("not json").isEmpty())
        assertEquals(
            1,
            ExercisePlanTemplates.parse(
                "[{\"week\":1,\"grade\":\"L1\",\"days\":3,\"note\":\"x\"},garbage]"
            ).size,
        )
    }

    @Test
    fun `target days lookup`() {
        val spec = ExercisePlanTemplates.parse(
            ExercisePlanTemplates.toJson(ExercisePlanTemplates.ALL.first().spec)
        )
        assertEquals(3, ExercisePlanTemplates.targetDays(spec, 1))
        assertEquals(0, ExercisePlanTemplates.targetDays(spec, 99))
    }

    /** 三个模板分别为 4 / 8 / 12 周，且周结构条数与周数一致。 */
    @Test
    fun `templates are 4 8 12 weeks with stable ids`() {
        assertEquals(listOf(4, 8, 12), ExercisePlanTemplates.ALL.map { it.weeks })
        assertTrue(ExercisePlanTemplates.ALL.all { it.spec.size == it.weeks })
        assertTrue(ExercisePlanTemplates.ALL.all { it.spec.map { w -> w.week } == (1..it.weeks).toList() })
        assertTrue(ExercisePlanTemplates.ALL.all { it.title.isNotBlank() })
    }

    @Test
    fun `pending is idempotent by id`() {
        assertEquals(3, ExercisePlanTemplates.pending(emptySet()).size)
        assertEquals(2, ExercisePlanTemplates.pending(setOf("eplan-seed-4w")).size)
        assertTrue(ExercisePlanTemplates.pending(ExercisePlanTemplates.ALL.map { it.id }.toSet()).isEmpty())
    }
}
