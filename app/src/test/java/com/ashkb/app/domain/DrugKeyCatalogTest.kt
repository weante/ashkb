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
}
