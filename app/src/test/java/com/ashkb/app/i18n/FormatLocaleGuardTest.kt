package com.ashkb.app.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.10（审查 HIGH-2）：**不得再用隐式 locale 的 `"…".format(…)`**。
 *
 * ### 缺陷
 * `"%.1f".format(x)` 等价于 `String.format(Locale.getDefault(), "%.1f", x)`。在德语/法语/越南语等
 * 系统语言下小数点会变成逗号（`71,2`），阿拉伯语等还会换成阿拉伯-印度数字；`%d` 还会按 locale
 * **加千位分隔符**（`1234` → `1.234`）。这些字符串有的进了 PDF、有的进了对照表，都是给医生看的。
 *
 * ### 为什么是这条守卫，而不是 detekt 规则
 * 加 detekt 自定义规则要改 `config/detekt/detekt.yml`（属配置变更，答复 §4 第 7 项明确写了需维护者
 * 点头）。这条守卫不碰配置，作用相同：它扫主源码里的**字面量格式串**，逼出「新的隐式 locale 调用」。
 *
 * ### 例外
 * 只允许**十六进制**（`%x`）：AOSP 的 `Formatter` 十六进制分支不做本地化（不进 `%02x` 换组分隔符）。
 * `data/backup/BackupEngine.kt:66` 的 SHA-256 摘要用它，刻意不改。
 *
 * 与其它静态守卫一样，本模块没有 Compose UI 测试依赖，所以用「读源码 + 断言」而不是跑界面。
 */
class FormatLocaleGuardTest {

    private val implicitFormat = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"\\.format\\(")

    private fun sourceDir(): File =
        listOf(File("app/src/main/java"), File("src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    private fun ktFiles(): List<File> = sourceDir().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `主源码不使用隐式 locale 的字符串格式化`() {
        val bad = mutableListOf<String>()
        for (f in ktFiles()) {
            f.readLines().forEachIndexed { i, line ->
                val m = implicitFormat.find(line) ?: return@forEachIndexed
                val fmt = m.groupValues[1]
                if (isHexOnly(fmt)) return@forEachIndexed
                bad += "${f.name}:${i + 1}: \"$fmt\".format("
            }
        }
        assertTrue(
            "改用 String.format(Locale.US, …)，否则小数点/千位分隔符会跟着系统语言变：\n" + bad.take(20).joinToString("\n"),
            bad.isEmpty()
        )
    }

    /** 守卫自身不能失效：确认真的扫到了源码，且「十六进制例外」的写法确实还在。 */
    @Test
    fun `守卫确实扫到了主源码`() {
        val files = ktFiles()
        assertTrue("扫到的 .kt 文件过少（${files.size}）——路径可能已失效", files.size > 100)
        assertTrue("应当扫到 BackupEngine.kt", files.any { it.name == "BackupEngine.kt" })
    }

    /** `%02x` 之类纯十六进制转换不本地化；只要出现别的转换符（f/e/g/d/s）就不算例外。 */
    private fun isHexOnly(fmt: String): Boolean {
        val conversions = Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")
            .findAll(fmt)
            .map { it.groupValues[1] }
            .filter { it != "%" }
            .toList()
        return conversions.isNotEmpty() && conversions.all { it == "x" || it == "X" }
    }

    /** 例外清单就是「十六进制」，写死一条断言防止有人把例外放宽成「整个文件放过」。 */
    @Test
    fun `例外只覆盖十六进制转换`() {
        assertEquals(true, isHexOnly("%02x"))
        assertEquals(false, isHexOnly("%.1f"))
        assertEquals(false, isHexOnly("%d"))
        assertEquals(false, isHexOnly("%02x %.1f"))
    }

    /**
     * **资源里不得出现浮点占位符**（`%1$.1f` / `%1$.2f`）。
     *
     * `Resources.getString(id, args…)` 内部就是 `String.format(config.locales[0], …)`，也就是说
     * XML 里的 `%.1f` **在 Kotlin 侧无论怎么写都锁不住 locale**。唯一可行的修法是：占位符改成 `%s`，
     * 由调用方先 `String.format(Locale.US, "%.1f", v)` 收敛好再传进来。
     *
     * 这条守卫拦的是「以后又有人顺手写 `%1$.1f`」——那会把已经修好的地方重新打开。
     */
    @Test
    fun `资源不使用会跟随系统语言的浮点占位符`() {
        val resDir = listOf(File("app/src/main/res"), File("src/main/res")).firstOrNull { it.isDirectory }
            ?: error("未找到资源目录（工作目录=${File(".").absolutePath}）")
        // 只看 values* 目录（字符串资源），drawable/xml 里没有格式化占位符
        val files = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }
            .orEmpty()
            .flatMap { dir -> dir.listFiles { f -> f.isFile && f.extension == "xml" }.orEmpty().toList() }
        assertTrue("没扫到 values* 下的 XML（${files.size}）", files.size >= 3)

        val bad = mutableListOf<String>()
        for (f in files) {
            f.readLines().forEachIndexed { i, line ->
                FLOAT_PLACEHOLDER.findAll(line).forEach { m ->
                    bad += "${f.parentFile?.name}/${f.name}:${i + 1}: ${m.value}"
                }
            }
        }
        assertTrue(
            "资源里的浮点占位符会跟随系统语言（德语/法语下小数点变逗号）。改成 %s，由调用方 String.format(Locale.US, …)：\n" +
                bad.take(20).joinToString("\n"),
            bad.isEmpty()
        )
    }

    private companion object {
        /** `%1$.1f` / `%2$.2f` / `%$.0f` 之类：显式序号 + 精度 + 浮点转换符。 */
        val FLOAT_PLACEHOLDER = Regex("%(?:\\d+\\$)?\\.\\d+[feg]")
    }
}
