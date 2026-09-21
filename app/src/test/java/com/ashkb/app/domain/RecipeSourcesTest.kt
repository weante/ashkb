package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.0.39：B3 食谱出处编号台账（界面只显示编号，题录在详情展开）。 */
class RecipeSourcesTest {

    @Test
    fun `nine sources with unique ids`() {
        assertEquals(9, RecipeSources.ALL.size)
        assertEquals(9, RecipeSources.ALL.map { it.id }.distinct().size)
    }

    /** 编号→题录必须按传入顺序返回；未知编号忽略（脏数据不致崩）。 */
    @Test
    fun `of keeps order and ignores unknown ids`() {
        assertEquals(listOf("S2", "S1"), RecipeSources.of(listOf("S2", "S1", "nope")).map { it.id })
        assertTrue(RecipeSources.of(emptyList()).isEmpty())
        assertTrue(RecipeSources.of(listOf("bogus")).isEmpty())
    }

    @Test
    fun `citation and note resolve per id`() {
        RecipeSources.ALL.forEach {
            assertNotNull(RecipeSources.citation(it.id))
            assertNotNull(RecipeSources.note(it.id))
        }
        assertNull(RecipeSources.citation("nope"))
        assertNull(RecipeSources.note("nope"))
    }

    /** 免责声明必须明确「非医疗建议」——这是本功能的合规底线。 */
    @Test
    fun `disclaimer states not medical advice`() {
        assertTrue(RecipeSources.DISCLAIMER.contains("非医疗建议"))
        assertTrue(RecipeSources.DISCLAIMER.contains("不能替代药物"))
    }
}
