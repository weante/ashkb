package com.ashkb.app.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `values/strings_seed.xml` 与 `values-en/strings_seed.xml` 的对等守卫。
 *
 * 为什么单独一对文件还要再写一遍守卫：种子内容是**写进数据库**的（食谱标题 / 筛查项名称 /
 * 计划周说明），漏翻一条不会像界面文案那样被用户立刻发现——它只会让英文用户看到一句中文，
 * 或者让 `pending` 判重认不出旧行而重复种入。lint 的 `MissingTranslation` 只拦「少了一条」，
 * 拦不住「这条还是中文」与「顺序错位」。
 *
 * 与 `EnglishStringsParityTest`（守 `values/strings.xml`）同一套做法：静态扫文件，不依赖 Compose。
 */
class SeedStringsParityTest {

    private val zh = resources("src/main/res/values/strings_seed.xml")
    private val en = resources("src/main/res/values-en/strings_seed.xml")

    /** 逐行取 `<string name="…">…</string>`；与 `EnglishStringsParityTest` 同一口径。 */
    private val line = Regex("^\\s*<string name=\"([^\"]+)\">(.*)</string>\\s*$")

    private fun entries(f: File): List<Pair<String, String>> =
        f.readLines().mapNotNull { l -> line.matchEntire(l)?.let { it.groupValues[1] to it.groupValues[2] } }

    private fun resources(relative: String): File =
        listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("找不到资源文件 $relative（工作目录可能不是模块根）")

    @Test
    fun `both files actually parsed`() {
        // 防解析正则失效后「零条 == 零条」的假绿
        assertTrue("中文种子资源应当有 72 条，实际 ${entries(zh).size}", entries(zh).size >= 70)
        assertTrue("英文种子资源应当有 72 条，实际 ${entries(en).size}", entries(en).size >= 70)
    }

    @Test
    fun `english seed resources declare the same names in the same order`() {
        val z = entries(zh)
        val e = entries(en)
        assertEquals("条数不一致", z.map { it.first }, e.map { it.first })
        assertEquals("中文侧有重名条目", z.size, z.map { it.first }.toSet().size)
    }

    @Test
    fun `english seed resources contain no Chinese characters`() {
        val cjk = Regex("[\\u3000-\\u303f\\u4e00-\\u9fff\\uff00-\\uffef]")
        val leftovers = entries(en).filter { (_, v) -> cjk.containsMatchIn(v) }.map { it.first }
        assertEquals("英文种子资源里仍有中文字符", emptyList<String>(), leftovers)
    }
}
