package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.39：B3 食谱种子（10 条、固定 id、必须带可解析的出处编号）。 */
class RecipeSeedsTest {

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
            assertTrue("${s.id} title", s.title.isNotBlank())
            assertTrue("${s.id} ingredients", s.ingredients.isNotBlank())
            assertTrue("${s.id} steps", s.steps.isNotBlank())
            assertTrue("${s.id} sources", s.sources.isNotEmpty())
            s.sources.forEach { assertNotNull("${s.id} 的出处 $it 必须在台账里", RecipeSources.citation(it)) }
        }
    }

    /** 第 6 条按用户要求弱化：不得出现「低淀粉」字样（S2 指出 AS 证据极为有限且不确定）。 */
    @Test
    fun `low starch wording is softened`() {
        val s6 = RecipeSeeds.ALL.first { it.id == "rec-s06" }
        assertFalse(s6.title.contains("低淀粉"))
        assertFalse(s6.steps.contains("低淀粉"))
        assertTrue(s6.title.contains("减少精制淀粉"))
    }

    @Test
    fun `pending is idempotent by id`() {
        assertEquals(10, RecipeSeeds.pending(emptySet()).size)
        assertEquals(9, RecipeSeeds.pending(setOf("rec-s01")).size)
        assertTrue(RecipeSeeds.pending(RecipeSeeds.ALL.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `tag labels and tag set`() {
        assertEquals("抗炎", RecipeSeeds.tagLabel(RecipeSeeds.TAG_ANTI))
        assertEquals("胃肠友好", RecipeSeeds.tagLabel(RecipeSeeds.TAG_GUT))
        assertEquals("控热量", RecipeSeeds.tagLabel(RecipeSeeds.TAG_CALORIE))
        assertEquals(3, RecipeSeeds.ALL_TAGS.size)
        // 每条种子用的标签都必须是已知标签
        RecipeSeeds.ALL.flatMap { it.tags }.distinct().forEach {
            assertTrue("未知标签 $it", it in RecipeSeeds.ALL_TAGS)
        }
    }
}
