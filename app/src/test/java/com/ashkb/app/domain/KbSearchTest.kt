package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.21 知识库检索（KbSearch）单测。
 *
 * 背景：代码审查建议迁 Room FTS4，但实证 FTS4 内置分词器（simple / unicode61）
 * 对中文子串零命中——中文无空格分隔，整句会成为一个 token。故保留 LIKE 子串语义，
 * 改为「单列检索文本 + 查询防抖 + 结果上限」。本测试锁定检索文本口径与匹配语义。
 */
class KbSearchTest {

    private val title = "依那西普注射部位轮换"
    private val summary = "生物制剂建议每次更换注射部位"
    private val payload = """{"sites":["左大腿","右大腿","左上臂"]}"""

    @Test
    fun `检索文本按换行分隔拼接标题摘要与 payload`() {
        // 与迁移 v8→v9 / 恢复回填的 SQL 表达式
        // title || char(10) || summary || char(10) || payload 逐字对应
        assertEquals(
            "$title\n$summary\n$payload",
            KbSearch.searchText(title, summary, payload),
        )
    }

    @Test
    fun `中文子串可命中（FTS4 分词器做不到、LIKE 语义必须保住）`() {
        val text = KbSearch.searchText(title, summary, payload)
        assertTrue(KbSearch.matches(text, "依那西普"))
        assertTrue(KbSearch.matches(text, "注射部位"))
        assertTrue(KbSearch.matches(text, "左上臂"))
        // 反向：无关词不命中
        assertFalse(KbSearch.matches(text, "甲氨蝶呤"))
    }

    @Test
    fun `ASCII 大小写不敏感（与 SQLite LIKE 默认行为一致）`() {
        val text = KbSearch.searchText("CRP", "hs-CRP 偏高", """{"unit":"mg/L"}""")
        assertTrue(KbSearch.matches(text, "crp"))
        assertTrue(KbSearch.matches(text, "HS-CRP"))
        assertTrue(KbSearch.matches(text, "mg/l"))
    }

    @Test
    fun `查询两侧空白被忽略、空查询不命中`() {
        val text = KbSearch.searchText(title, summary, payload)
        assertTrue(KbSearch.matches(text, "  注射部位  "))
        assertFalse(KbSearch.matches(text, ""))
        assertFalse(KbSearch.matches(text, "   "))
    }

    @Test
    fun `检索文本为空（旧备份恢复后未回填）不命中`() {
        assertFalse(KbSearch.matches(null, "依那西普"))
        assertFalse(KbSearch.matches("", "依那西普"))
    }

    @Test
    fun `换行分隔避免跨字段拼出假匹配`() {
        val text = KbSearch.searchText("注射部位轮换", "生物制剂建议每日", "{}")
        // 若用空格分隔，标题尾「轮换」+ 摘要头「生物」会拼出「轮换 生物」造成假命中
        assertFalse(KbSearch.matches(text, "轮换 生物"))
        assertFalse(KbSearch.matches(text, "每日 {}"))
        // 同一字段内的连续文本照常命中
        assertTrue(KbSearch.matches(text, "注射部位轮换"))
        assertTrue(KbSearch.matches(text, "生物制剂建议每日"))
    }

    @Test
    fun `上限与防抖常量取值合理`() {
        // 知识库为固定 47 条种子：上限须远大于该规模（纯安全边界，不截断正常结果）
        assertTrue("MAX_RESULTS 应留足余量", KbSearch.MAX_RESULTS >= 100)
        // 防抖窗口过短起不到合并按键的作用，过长则手感发滞
        assertTrue("DEBOUNCE_MS 应在 100~500ms", KbSearch.DEBOUNCE_MS in 100L..500L)
        assertEquals("search_text", KbSearch.COLUMN)
    }
}
