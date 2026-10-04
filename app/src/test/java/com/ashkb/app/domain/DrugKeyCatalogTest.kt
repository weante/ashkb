package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P5 R8：通用名键自动匹配目录单测 */
class DrugKeyCatalogTest {

    @Test
    fun `中文商品名命中——恩利 命中 etanercept`() {
        val s = DrugKeyCatalog.suggest("恩利")
        assertTrue(s.isNotEmpty())
        assertEquals("etanercept", s[0].key)
        assertEquals("依那西普", s[0].display)
    }

    @Test
    fun `中文通用名命中——依那西普`() {
        val s = DrugKeyCatalog.suggest("依那西普")
        assertEquals("etanercept", s[0].key)
    }

    @Test
    fun `英文键前缀优先于包含`() {
        val s = DrugKeyCatalog.suggest("et")
        // etanercept / etoricoxib 均前缀命中；methotrexate（含 et）排后
        val keys = s.map { it.key }
        assertTrue(keys.indexOf("etanercept") < keys.indexOf("methotrexate"))
    }

    @Test
    fun `别名命中——mtx 命中甲氨蝶呤`() {
        val s = DrugKeyCatalog.suggest("mtx")
        assertEquals("methotrexate", s[0].key)
    }

    @Test
    fun `别名命中——阿达 命中 adalimumab`() {
        val s = DrugKeyCatalog.suggest("阿达")
        assertEquals("adalimumab", s[0].key)
    }

    @Test
    fun `大小写不敏感`() {
        assertEquals("etanercept", DrugKeyCatalog.suggest("Etanercept")[0].key)
        assertEquals("methotrexate", DrugKeyCatalog.suggest("MTX")[0].key)
    }

    @Test
    fun `空查询返回空建议`() {
        assertTrue(DrugKeyCatalog.suggest("").isEmpty())
        assertTrue(DrugKeyCatalog.suggest("   ").isEmpty())
    }

    @Test
    fun `未收录词返回空建议（不瞎猜）`() {
        assertTrue(DrugKeyCatalog.suggest("zzzz不存在的药").isEmpty())
    }

    @Test
    fun `类别键可检索`() {
        assertEquals("nsaid", DrugKeyCatalog.suggest("nsaid")[0].key)
        val s = DrugKeyCatalog.suggest("生物")
        assertTrue(s.any { it.key == "biologic" })
    }

    @Test
    fun `建议数量受上限约束`() {
        assertTrue(DrugKeyCatalog.suggest("a").size <= 6)
    }

    @Test
    fun `精确键判定`() {
        assertTrue(DrugKeyCatalog.isExactKey("etanercept"))
        assertTrue(DrugKeyCatalog.isExactKey("Etanercept"))
        assertFalse(DrugKeyCatalog.isExactKey("etanercep"))
        assertFalse(DrugKeyCatalog.isExactKey(""))
    }

    @Test
    fun `建议携带回填信息（类别与商品名）`() {
        val e = DrugKeyCatalog.suggest("恩利")[0]
        assertEquals(com.ashkb.app.data.entity.MedClass.BIOLOGIC, e.medClass)
        assertEquals("恩利", e.brand)
    }

    // ------------------------------------------------------------------
    // v1.1.3（批次 19 · J-7）：IL-23 类药的「未检索到 AS 适应症」标注
    // ------------------------------------------------------------------

    /**
     * 三支 IL-12/23 · IL-23 抑制剂**留在目录里**（维护者裁决 (b)），但带中性提示。
     *
     * ⚠️ 锁的是「三支都在」这一点：若将来有人按「无 AS 适应症」把它们从目录删掉，
     * AS 患者合并 PsA / IBD 时就**搜不到自己的药**了——那比多一条提示危险得多。
     */
    @Test
    fun `IL-23 类药保留在目录并带 AS 适应症提示`() {
        for (key in listOf("ustekinumab", "guselkumab", "risankizumab")) {
            val e = DrugKeyCatalog.entries.firstOrNull { it.key == key }
                ?: error("批次 19 裁决 (b)：$key 必须保留在目录中（AS 患者合并 PsA/IBD 时可能使用）")
            val note = e.note
                ?: error("$key 应当带 note 提示「未检索到 AS 适应症」")
            assertTrue("$key 的提示应说明未检索到 AS 适应症，实际：$note", note.contains("未检索到"))
            assertTrue("$key 的提示应点明是 AS（axSpA），实际：$note", note.contains("AS"))
        }
    }

    /**
     * 提示文案必须是**中性**的：只陈述检索结果 + 把决定权交给医生。
     *
     * ⚠️ 这条断言防的是「措辞悄悄变强」：写成「你不该用」「禁用于 AS」就是替医生下了
     * 用药结论——那既超出我们的依据，也可能直接挡住一个合并用药的合法场景。
     */
    @Test
    fun `提示文案保持中性不越界`() {
        val note = DrugKeyCatalog.entries.first { it.key == "ustekinumab" }.note!!
        for (forbidden in listOf("不该用", "禁用于 AS", "禁止使用", "不能吃")) {
            assertFalse("提示文案不得出现用药结论性措辞「$forbidden」，实际：$note", note.contains(forbidden))
        }
        assertTrue("应把判断权交回医生", note.contains("医生"))
    }

    /** 其余药物**不得**被这条 J-7 波及：只有那三支 IL-12/23 · IL-23 抑制剂带 note。 */
    @Test
    fun `只有三支 IL-23 类药带 note`() {
        val annotated = DrugKeyCatalog.entries.filter { it.note != null }.map { it.key }.sorted()
        assertEquals(
            "带 note 的应当只有三支 IL-12/23 · IL-23 抑制剂",
            listOf("guselkumab", "risankizumab", "ustekinumab"),
            annotated,
        )
    }
}
