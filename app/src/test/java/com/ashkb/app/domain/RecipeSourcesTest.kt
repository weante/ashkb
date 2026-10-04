package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.39：B3 食谱出处编号台账（界面只显示编号，题录在详情展开）。
 *
 * v1.1.2：编号由 `S1`…`S9` 改为 `R1`…`R9`——与知识库的 `S1`–`S4` 证据层级语义不同，
 * 同一个应用里都显示裸 `S1` 会让患者分不清「等级」还是「第 1 篇文献」。
 */
class RecipeSourcesTest {

    @Test
    fun `nine sources with unique ids`() {
        assertEquals(9, RecipeSources.ALL.size)
        assertEquals(9, RecipeSources.ALL.map { it.id }.distinct().size)
    }

    /**
     * v1.1.2：编号**必须**是 `R` 前缀，且**不能**出现 `S` 前缀——
     * 后者正是本次要修掉的撞车本身，写成断言是为了让「有人把前缀改回去」变红。
     */
    @Test
    fun `ids are R-prefixed and never collide with the S evidence tiers`() {
        val ids = RecipeSources.ALL.map { it.id }
        assertEquals(listOf("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9"), ids)
        assertTrue("编号不得以 S 开头（那是知识库证据层级的形状）：$ids", ids.none { it.startsWith("S") })
    }

    /** 编号→题录必须按传入顺序返回；未知编号忽略（脏数据不致崩）。 */
    @Test
    fun `of keeps order and ignores unknown ids`() {
        assertEquals(listOf("R2", "R1"), RecipeSources.of(listOf("R2", "R1", "nope")).map { it.id })
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

    /**
     * v1.1.2：**老库里存的仍是 `S1` 这类旧编号**——出处随种子写进 `recipes.sources`，
     * 而 `seedIfMissing` 按固定 id 幂等，已安装用户的 10 条种子食谱不会被重新种一遍。
     * 没有这层归一，他们打开食谱详情会看到出处整节消失（比改前的撞车更严重）。
     */
    @Test
    fun `legacy S-prefixed ids still resolve to the same citations`() {
        RecipeSources.ALL.forEach { source ->
            val legacy = "S" + source.id.removePrefix("R")
            assertEquals(
                "旧编号 $legacy 必须仍解析到 ${source.id} 的同一条题录",
                source.citation,
                RecipeSources.citation(legacy),
            )
            assertEquals(source.note, RecipeSources.note(legacy))
        }
        assertEquals(
            listOf("R2", "R1"),
            RecipeSources.of(listOf("S2", "S1")).map { it.id },
        )
    }

    /** 兼容层只认「单个大写 S + 一位数字」：其它脏数据仍然查不到，不会被洗成某条真实文献。 */
    @Test
    fun `compat layer does not launder junk ids`() {
        assertNull(RecipeSources.citation("S"))
        assertNull(RecipeSources.citation("SX"))
        assertNull(RecipeSources.citation("s1"))
        assertNull(RecipeSources.citation("S10"))
        assertNull(RecipeSources.citation("S99"))
        assertNull(RecipeSources.citation(""))
        assertNull(RecipeSources.citation("SS1"))
        assertTrue(RecipeSources.of(listOf("S10", "S0", "nope")).isEmpty())
    }

    /** 免责声明必须明确「非医疗建议」——这是本功能的合规底线。 */
    @Test
    fun `disclaimer states not medical advice`() {
        assertTrue(RecipeSources.DISCLAIMER.contains("非医疗建议"))
        assertTrue(RecipeSources.DISCLAIMER.contains("不能替代药物"))
    }
}
