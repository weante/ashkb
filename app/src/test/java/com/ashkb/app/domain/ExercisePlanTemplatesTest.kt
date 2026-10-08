package com.ashkb.app.domain

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.39：B7 周期康复计划模板与 week_structure 编解码。
 *
 * v1.2.6：模板定义改用 [ExercisePlanTemplates.WeekSpecRes]（文案是资源 id），
 * 落库形态 [ExercisePlanTemplates.WeekSpec]（文案是文本）不变，两者由
 * [ExercisePlanTemplates.materialize] 连接。编解码测试仍针对后者。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class ExercisePlanTemplatesTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    /** 把模板展开成落库形态（中文）。 */
    private fun materialize(t: ExercisePlanTemplates.Template): List<ExercisePlanTemplates.WeekSpec> =
        ExercisePlanTemplates.materialize(t) { ctx.getString(it) }


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
            ExercisePlanTemplates.toJson(materialize(ExercisePlanTemplates.ALL.first()))
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
        assertTrue(ExercisePlanTemplates.ALL.all { ctx.getString(it.titleRes).isNotBlank() })
    }

    /** ⭐ 展开后每周的说明都必须非空——资源 id 写错成 0 或未定义会让模板缺提示。 */
    @Test
    fun `materialize fills every week note`() {
        ExercisePlanTemplates.ALL.forEach { t ->
            val spec = materialize(t)
            assertEquals("${t.id} 周数", t.weeks, spec.size)
            assertTrue("${t.id} 每周说明都要有内容", spec.all { it.note.isNotBlank() })
            assertTrue("${t.id} 强度级别不得为空", spec.all { it.grade.isNotBlank() && it.days > 0 })
        }
    }

    /**
     * ⭐ 标题必须互不相同（复制模板时最容易漏改），周说明合计必须正好 18 条。
     *
     * 注意**周说明是有意复用的**：12 周模板里第 3–4 周同一条、5–8 周同一条、9–11 周同一条
     * （那几周处方本来就一样），所以这里比的是「去重后的条数」而不是「逐周互不相同」。
     */
    @Test
    fun `template resources are distinct where they must be`() {
        val titles = ExercisePlanTemplates.ALL.map { it.titleRes }
        assertEquals("三个模板标题不得重复", titles.size, titles.toSet().size)
        val notes = ExercisePlanTemplates.ALL.flatMap { it.spec.map { w -> w.noteRes } }
        assertEquals("三个模板合计用到 18 条周说明", 18, notes.toSet().size)
    }

    @Test
    fun `pending is idempotent by id`() {
        assertEquals(3, ExercisePlanTemplates.pending(emptySet()).size)
        assertEquals(2, ExercisePlanTemplates.pending(setOf("eplan-seed-4w")).size)
        assertTrue(ExercisePlanTemplates.pending(ExercisePlanTemplates.ALL.map { it.id }.toSet()).isEmpty())
    }
}
