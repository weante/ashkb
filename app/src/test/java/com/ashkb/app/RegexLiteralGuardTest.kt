package com.ashkb.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2（v1.0.44）：**正则字面量花括号守卫**——把 v1.0.39–42 那类崩溃变成编译期就能拦下的问题。
 *
 * ### 为什么不用 Robolectric（已实测证伪，勿再尝试）
 * 第三轮审查建议「用 Robolectric 触发含正则的 object 初始化，一行即可暴露 `<clinit>` 崩溃」。
 * 这个建议依赖一个未经证实的前提：Robolectric 里的 `java.util.regex.Pattern` 走 Android ICU 语义。
 * **实测结论：不是。** 用当年的真实缺陷模式做探针，在 Robolectric（sdk=34）下得到
 * `PROBE_RESULT=JVM_LIKE`——同一段含孤立 `}` 的模式**正常编译通过**。
 * 也就是说该方案对本类 bug 的检出率是 **0**，而「JVM 语义全绿」正是当年真机崩溃的根因。
 *
 * ### 因此改为静态守卫
 * 直接扫描主源码里的正则字面量，只允许 ICU 也接受的合法量词 `{\d+}` / `{\d+,\d*}`，
 * 其余花括号一律判失败。本用例随 `testDebugUnitTest` 一起跑，**CI 无需任何改动**即可获得这道防线。
 *
 * ### 覆盖范围与已知局限
 * 只检查 `Regex("字面量")` / `Regex("""字面量""")` 形态；由字符串拼接构造的正则不在覆盖内
 * （当前代码库无此写法）。为防「路径写错 → 一个文件都没扫到 → 静默通过」，
 * 本用例同时断言「确实扫到了文件、确实提取到了正则字面量」。
 */
class RegexLiteralGuardTest {

    // ---- 判定函数本身（纯函数，先钉死语义） ----

    @Test
    fun `extracts plain and raw string literals`() {
        // 普通字符串：源码里的 \\ 表示一个反斜杠，提取时需还原（否则 \\{ 会被误判为未转义花括号）
        assertEquals(listOf("""\d{4}"""), extractRegexLiterals("val r = Regex(\"\\\\d{4}\")"))
        // 原始字符串：不做转义处理
        assertEquals(listOf("""\d{4}"""), extractRegexLiterals("val r = Regex(\"\"\"\\d{4}\"\"\")"))
        assertEquals(emptyList<String>(), extractRegexLiterals("val x = 1"))
    }

    @Test
    fun `legal quantifiers are accepted`() {
        assertNull(firstStrayBrace("""\d{4}"""))
        assertNull(firstStrayBrace("""\d{2,3}"""))
        assertNull(firstStrayBrace("""\d{2,}"""))
        assertNull(firstStrayBrace("""^[a-z]{1,}$"""))
    }

    /** 字符类里的花括号是字面量，不是量词——不能误报。 */
    @Test
    fun `braces inside a character class are literals`() {
        assertNull(firstStrayBrace("""[{}]+"""))
        assertNull(firstStrayBrace("""[\}]"""))
    }

    /** 转义花括号同样是字面量。 */
    @Test
    fun `escaped braces are literals`() {
        assertNull(firstStrayBrace("""\{x\}"""))
    }

    /**
     * 源码里写成 `"\\{"` 的正则，实际语义是 `\{`（转义花括号，合法）。
     * 提取时若不还原 `\\` → `\`，就会把它误判成「未转义 `{`」——这类误报会让守卫失去可信度。
     */
    @Test
    fun `escaped brace written as double backslash is not a false positive`() {
        val lits = extractRegexLiterals("val r = Regex(\"\\\\{\")")
        assertEquals(listOf("""\{"""), lits)
        assertNull(firstStrayBrace(lits[0]))
    }

    /** v1.0.39–42 的真实缺陷：末尾那个未转义的 `}`。 */
    @Test
    fun `the historical buggy pattern is caught`() {
        val weekRe =
            """\{"week":(\d+),"grade":"((?:[^"\\]|\\.)*)","days":(\d+),"note":"((?:[^"\\]|\\.)*)"}"""
        val hit = firstStrayBrace(weekRe)
        assertTrue("应检出末尾孤立 `}`，实际=$hit", hit != null && hit.contains("}"))
    }

    @Test
    fun `stray opening brace is caught`() {
        assertTrue(firstStrayBrace("""a{b""") != null)
        assertTrue(firstStrayBrace("""a{}""") != null)
    }

    // ---- 对真实主源码执行守卫 ----

    @Test
    fun `main sources contain no icu illegal regex brace`() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
        assertTrue("未找到主源码目录（单测工作目录=${File(".").absolutePath}）", root != null)

        val ktFiles = root!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("未扫到任何 .kt 文件，守卫形同虚设", ktFiles.isNotEmpty())

        var literalCount = 0
        val offenders = mutableListOf<String>()
        val uncoveredForms = mutableListOf<String>()
        for (f in ktFiles) {
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            // 守卫目前只认 Regex("字面量") 形态。别的正则入口一旦出现就**大声失败**，
            // 提示先扩展提取器——比静默漏检好（v1.0.46 加固）。
            if (text.contains(".toRegex()") || text.contains("Pattern.compile")) uncoveredForms += f.path
            for (lit in extractRegexLiterals(text)) {
                literalCount++
                val hit = firstStrayBrace(lit)
                if (hit != null) offenders += "${f.path}: $hit\n    pattern=$lit"
            }
        }
        assertTrue("提取到的正则字面量过少（$literalCount），守卫可能失效", literalCount >= 1)
        assertTrue(
            "主源码出现 .toRegex() / Pattern.compile：正则守卫尚未覆盖该形态，" +
                "请先扩展 extractRegexLiterals 再继续（文件：${uncoveredForms.joinToString()}）",
            uncoveredForms.isEmpty(),
        )
        assertTrue(
            "以下正则字面量含 ICU 不接受的孤立花括号（真机抛 PatternSyntaxException，JVM 单测无法发现）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}

/**
 * 提取源码里 `Regex(<字符串字面量>)` 的字面量文本。
 * 支持 `"..."`（含 `\"` / `\\` 转义）与 `"""..."""` 两种形态。
 */
internal fun extractRegexLiterals(src: String): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (true) {
        val at = src.indexOf("Regex(", i)
        if (at < 0) break
        // `.toRegex()` 也含子串 "Regex("，但其字面量在调用**之前**——继续向前抓会错拿
        // 下一个无关字符串。当前主源码零使用（由上方断言保证），出现时先扩展再放行。
        if (at > 0 && src[at - 1] == 'o') { i = at + 1; continue }
        var j = at + "Regex(".length
        while (j < src.length && src[j].isWhitespace()) j++
        if (j >= src.length || src[j] != '"') { i = at + 1; continue }

        if (src.startsWith("\"\"\"", j)) {
            val end = src.indexOf("\"\"\"", j + 3)
            if (end < 0) { i = at + 1; continue }
            out += src.substring(j + 3, end)
            i = end + 3
        } else {
            val sb = StringBuilder()
            var k = j + 1
            var closed = false
            while (k < src.length) {
                val c = src[k]
                when {
                    // 还原 Kotlin 字符串转义：`\\` 要变成单个反斜杠，否则 `\\{` 会被误判成
                    // 「未转义花括号」（它其实等价于正则里的 `\{`，是合法的）。
                    c == '\\' && k + 1 < src.length -> {
                        when (val n = src[k + 1]) {
                            '\\', '"', '$' -> sb.append(n)
                            else -> sb.append(c).append(n)
                        }
                        k += 2
                    }
                    c == '"' -> { closed = true; break }
                    else -> { sb.append(c); k++ }
                }
            }
            if (!closed) { i = at + 1; continue }
            out += sb.toString()
            i = k + 1
        }
    }
    return out
}

/**
 * 返回第一个「ICU 不接受」的花括号说明；全部合法返回 null。
 *
 * 规则（对齐 ICU 而非 Java 的宽松语义）：
 *  - 字符类 `[...]` 内的花括号是字面量，跳过；
 *  - `\X` 转义对整体跳过；
 *  - 其余位置的 `{` 必须是合法量词 `{\d+}` / `{\d+,\d*}`，`}` 必须作为量词结尾被消费，
 *    否则判为孤立花括号（Java 当字面量、ICU 抛 `PatternSyntaxException`）。
 */
internal fun firstStrayBrace(regex: String): String? {
    var i = 0
    var inClass = false
    while (i < regex.length) {
        when {
            regex[i] == '\\' -> i += 2
            regex[i] == '[' && !inClass -> { inClass = true; i++ }
            regex[i] == ']' && inClass -> { inClass = false; i++ }
            inClass -> i++
            regex[i] == '{' -> {
                val len = quantifierLength(regex, i)
                if (len < 0) return "index $i 处的 `{` 不是合法量词"
                i += len
            }
            regex[i] == '}' -> return "index $i 处出现孤立 `}`（Java 视为字面量，ICU 报 PatternSyntaxException）"
            else -> i++
        }
    }
    return null
}

/** 从 `start` 处的 `{` 尝试匹配量词，返回其长度；不合法返回 -1。刻意不用正则（避免自伤）。 */
private fun quantifierLength(regex: String, start: Int): Int {
    var j = start + 1
    val digitsStart = j
    while (j < regex.length && regex[j].isDigit()) j++
    if (j == digitsStart) return -1
    if (j < regex.length && regex[j] == ',') {
        j++
        while (j < regex.length && regex[j].isDigit()) j++
    }
    if (j >= regex.length || regex[j] != '}') return -1
    return j - start + 1
}
