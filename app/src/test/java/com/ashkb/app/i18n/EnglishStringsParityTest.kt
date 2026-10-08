package com.ashkb.app.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.4（i18n）：**英文资源必须与中文资源逐条对齐**。
 *
 * ### 覆盖范围
 * 扫的是**整个 `src/main/res/values/` 目录**，而不是某一个文件：凡是含 `<string>` 的 XML，
 * 都必须在 `src/main/res/values-en/` 里有**同名文件**，且条目名、顺序、占位符、引号包裹全部一致。
 * 这样后续把字符串拆到 `strings_domain.xml` / `strings_backup.xml` 之类的分片文件里时，
 * 守卫会自动跟上，不需要改测试。
 *
 * ### 为什么要有这条守卫
 * `values-en/` 是一份**手工维护**的平行资源。它坏掉的方式都是静默的：
 *  · 漏一条 → 该处回退中文，英文界面里突然冒出中文，但构建照样通过；
 *  · 多一条 / 重名 → `ExtraTranslation` 或 aapt 的重复资源错误；
 *  · 占位符对不上（`%1$d` 写成 `%2$d`、少一个 `%s`）→ 运行时 `String.format` 抛异常或串错数字；
 *  · 首尾空格丢了 → 拼接处「在用 3 种 ·」变成「在用3 种·」，只有肉眼看界面才发现；
 *  · 留了中文 → 半英半中的界面。
 *
 * 这里把上述五条都钉成红灯。它是**静态扫描**（本模块没有 Compose UI 测试依赖，同
 * `KbTierLabelGuardTest` 的说明），证明的是「文件之间是一致的」，不证明排版效果。
 *
 * 另按本仓惯例**断言确实读到了文件**：路径写错时大声失败，而不是悄悄跳过。
 */
class EnglishStringsParityTest {

    /** `values/` 下所有「含 `<string>`」的文件，按文件名排序，与 `values-en/` 里的同名文件配成对。 */
    private fun pairs(): List<Triple<String, List<Pair<String, String>>, List<Pair<String, String>>>> {
        val zhDir = resourceDir("src/main/res/values")
        val enDir = resourceDir("src/main/res/values-en")
        val files = zhDir.listFiles { f -> f.isFile && f.extension == "xml" }?.sortedBy { it.name }.orEmpty()
        val out = mutableListOf<Triple<String, List<Pair<String, String>>, List<Pair<String, String>>>>()
        for (f in files) {
            val zh = parse(f)
            if (zh.isEmpty()) continue // colors.xml / themes.xml 之类没有字符串
            val en = enDir.resolve(f.name)
            assertTrue("英文资源缺少同名文件 values-en/${f.name}（中文有 ${zh.size} 条）", en.isFile)
            out += Triple(f.name, zh, parse(en))
        }
        return out
    }

    private fun resourceDir(relative: String): File =
        listOf(File(relative), File("app/$relative")).firstOrNull { it.isDirectory }
            ?: error("未找到资源目录 $relative（工作目录=${File(".").absolutePath}）")

    private fun parse(f: File): List<Pair<String, String>> =
        f.readLines().mapNotNull { line -> LINE.find(line)?.let { it.groupValues[1] to it.groupValues[2] } }

    private fun values(pairs: List<Pair<String, String>>, where: String): Map<String, String> =
        pairs.toMap().also { m -> assertEquals("$where 里有重名的 <string>", pairs.size, m.size) }

    /** 英文里不得出现的字符：CJK 统一表意文字、CJK 标点、全角形式。 */
    @Test
    fun `english resources contain no Chinese characters`() {
        val bad = mutableListOf<String>()
        for ((name, _, en) in pairs()) {
            en.filter { (_, v) -> CJK.containsMatchIn(v) }.forEach { bad += "$name:${it.first}=${it.second}" }
        }
        assertTrue("英文资源里残留中文（半英半中的界面）: " + bad.take(10), bad.isEmpty())
    }

    /** 每个文件里，条目名集合与顺序必须与中文侧完全一致——顺序一致，diff 才有意义。 */
    @Test
    fun `english resources declare exactly the same names in the same order`() {
        for ((name, zh, en) in pairs()) {
            assertEquals("values-en/$name 条目数不一致", zh.size, en.size)
            val zhNames = zh.map { it.first }
            val enNames = en.map { it.first }
            assertEquals("values-en/$name 条目名或顺序不一致", zhNames, enNames)
        }
    }

    /**
     * 占位符必须一致（**允许换序**）。
     *
     * `%1$d ... %2$d` 与 `%2$d ... %1$d` 都是合法的——显式序号的意义正在于此
     * （英文语序不同时本来就要换序，如 `med_history_prn_metric`：中文「近 %1$d 天记录 %2$d 次」
     * → 英文「%2$d times in the past %1$d days」）。所以这里比的是**多重集合**，不是序列。
     */
    @Test
    fun `english resources keep the same format placeholders`() {
        val bad = mutableListOf<String>()
        for ((name, zh, en) in pairs()) {
            val zhMap = values(zh, "values/$name")
            for ((k, v) in values(en, "values-en/$name")) {
                if (placeholders(zhMap[k].orEmpty()).sorted() != placeholders(v).sorted()) {
                    bad += "$name:$k=$v"
                }
            }
        }
        assertTrue("占位符不一致: " + bad.take(10), bad.isEmpty())
    }

    /**
     * 引号包裹必须一致。
     *
     * Android 会裁掉资源正文**未加引号**的首尾空白；`" · 注射"` 这种写法正是为了保住那个空格。
     * 英文侧若丢掉外层引号，拼接处就会挤在一起。
     *
     * @see QUOTE_WRAP_EXCEPTIONS
     */
    @Test
    fun `english resources preserve the whitespace-keeping quote wrapping`() {
        val bad = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for ((name, zh, en) in pairs()) {
            val zhMap = values(zh, "values/$name")
            val enMap = values(en, "values-en/$name")
            for ((k, v) in enMap) {
                if (k in QUOTE_WRAP_EXCEPTIONS) { seen += k; continue }
                if (wrapped(zhMap[k].orEmpty()) != wrapped(v)) bad += "$name:$k=$v"
            }
        }
        // 白名单自身不能腐烂：列进去的名字必须真的还在资源里
        assertTrue("引号白名单里有已不存在的条目，应删除: ${QUOTE_WRAP_EXCEPTIONS - seen}", QUOTE_WRAP_EXCEPTIONS == seen)
        assertTrue("引号包裹不一致（会丢掉首尾空格）: " + bad.take(10), bad.isEmpty())
    }

    /** 防止「守卫本身失效」：至少要扫到上千条，否则说明解析正则被改坏了。 */
    @Test
    fun `parity guard actually parsed the resource files`() {
        val all = pairs()
        assertTrue("没有扫到任何含字符串的资源文件", all.isNotEmpty())
        val total = all.sumOf { it.second.size }
        assertTrue("解析出的条目过少（$total）——解析正则可能已失效", total > 1000)
        assertTrue("应当存在 strings.xml", all.any { it.first == "strings.xml" })
        assertFalse("app_name 应当存在", all.flatMap { it.third }.toMap()["app_name"].isNullOrBlank())
    }

    private companion object {
        /**
         * 允许「中文不加引号包裹、英文加」的**例外清单**。
         *
         * 唯一理由是**全角 vs 半角括号的间隔差异**：中文 `【提示】`/`【高危】` 的 `】` 自带视觉间隔，
         * 英文 `]` 不会，所以英文必须靠外层引号保住一个尾空格，否则渲染处
         * `app/src/main/java/com/ashkb/app/ui/me/MedEditSections.kt` 的 `"· $severityPrefix${e.title}"`
         * 会拼成 `· [High risk]甲氨蝶呤...`。
         *
         * 加新条目时必须逐条写清理由——这是**例外**，不是可以把整组放过的开关。
         */
        val QUOTE_WRAP_EXCEPTIONS = setOf("common_notice_prefix", "knowledge_high_risk_prefix")

        val LINE = Regex("^\\s*<string name=\"([^\"]+)\">(.*)</string>\\s*$")
        val CJK = Regex("[\\u3000-\\u303f\\u4e00-\\u9fff\\uff00-\\uffef]")
        val PLACEHOLDER = Regex("%(?:\\d+\\$)?(?:\\d+\\.\\d+)?[sdfx]|%%")

        fun placeholders(s: String): List<String> = PLACEHOLDER.findAll(s).map { it.value }.toList()

        fun wrapped(s: String): Boolean = s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")
    }
}
