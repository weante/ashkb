package com.ashkb.app.domain

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.0.39：B3 食谱种子（10 条、固定 id、必须带可解析的出处编号）。
 *
 * v1.2.6：种子文案改成 `@StringRes`，断言的是**渲染后的中文文本**（Robolectric 读 `values/`）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class RecipeSeedsTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun title(s: RecipeSeeds.Seed): String = ctx.getString(s.titleRes)
    private fun ingredients(s: RecipeSeeds.Seed): String = ctx.getString(s.ingredientsRes)
    private fun steps(s: RecipeSeeds.Seed): String = ctx.getString(s.stepsRes)

    @Test
    fun `ten seeds with stable unique ids`() {
        assertEquals(10, RecipeSeeds.ALL.size)
        assertEquals(10, RecipeSeeds.ALL.map { it.id }.distinct().size)
        assertTrue(RecipeSeeds.ALL.all { it.id.startsWith("rec-s") })
    }

    /** 每条都必须有内容与**可解析**的出处编号——出处是本功能的前提。 */
    @Test
    fun `every seed has content and resolvable sources`() {
        RecipeSeeds.ALL.forEach { s ->
            assertTrue("${s.id} title", title(s).isNotBlank())
            assertTrue("${s.id} ingredients", ingredients(s).isNotBlank())
            assertTrue("${s.id} steps", steps(s).isNotBlank())
            assertTrue("${s.id} sources", s.sources.isNotEmpty())
            s.sources.forEach { assertNotNull("${s.id} 的出处 $it 必须在台账里", RecipeSources.citation(it)) }
        }
    }

    /** ⭐ 每条种子的三个文案字段必须指向三条不同资源（误指向同一条会让标题=做法，编译器不报错）。 */
    @Test
    fun `every seed points at distinct resources`() {
        val ids = RecipeSeeds.ALL.flatMap { listOf(it.titleRes, it.ingredientsRes, it.stepsRes) }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** 第 6 条按用户要求弱化：不得出现「低淀粉」字样（S2 指出 AS 证据极为有限且不确定）。 */
    @Test
    fun `low starch wording is softened`() {
        val s6 = RecipeSeeds.ALL.first { it.id == "rec-s06" }
        assertFalse(title(s6).contains("低淀粉"))
        assertFalse(steps(s6).contains("低淀粉"))
        assertTrue(title(s6).contains("减少精制淀粉"))
    }

    @Test
    fun `pending is idempotent by id`() {
        assertEquals(10, RecipeSeeds.pending(emptySet()).size)
        assertEquals(9, RecipeSeeds.pending(setOf("rec-s01")).size)
        assertTrue(RecipeSeeds.pending(RecipeSeeds.ALL.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `tag labels and tag set`() {
        assertEquals("抗炎", ctx.getString(RecipeSeeds.tagLabelRes(RecipeSeeds.TAG_ANTI)!!))
        assertEquals("胃肠友好", ctx.getString(RecipeSeeds.tagLabelRes(RecipeSeeds.TAG_GUT)!!))
        assertEquals("控热量", ctx.getString(RecipeSeeds.tagLabelRes(RecipeSeeds.TAG_CALORIE)!!))
        // 未知标签返回 null（调用方回退成原始 tag），不是抛异常也不是返回空串
        assertNull(RecipeSeeds.tagLabelRes("bogus"))
        assertEquals(3, RecipeSeeds.ALL_TAGS.size)
        // 每条种子用的标签都必须是已知标签
        RecipeSeeds.ALL.flatMap { it.tags }.distinct().forEach {
            assertTrue("未知标签 $it", it in RecipeSeeds.ALL_TAGS)
        }
    }
}
