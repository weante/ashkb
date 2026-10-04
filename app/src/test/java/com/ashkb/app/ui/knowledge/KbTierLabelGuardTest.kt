package com.ashkb.app.ui.knowledge

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.2：**证据层级（`S1`–`S4`）必须有患者能读懂的释义**——界面不再只显示裸字母。
 *
 * ### 为什么是静态扫描
 * 释义本身在 `strings.xml` 里、渲染发生在 `@Composable` 里，而本模块**没有 Compose UI 测试
 * 依赖**（同 `WellnessScreenStateScopeTest` 的说明）。这里能钉住的是三件**结构事实**：
 *  · 列表那行走的是**短版**（`tierShortLabel`），且**不再**直接把 `entry.sourceTier` 拼进字符串；
 *  · 详情那行走的是**完整版**（`tierFullLabel`）；
 *  · `S1`–`S4` 四档的短版与完整版释义都在 `strings.xml` 里，且都有内容。
 *
 * 任一条不成立时「层级对患者无解释」就会以**静默**的方式回来（编译得过、界面照常渲染），
 * 所以用红灯钉住；同时按本仓惯例**同时断言「确实扫到了源文件」**，路径写错时大声失败。
 *
 * ### 它不能证明什么
 * 它证明的是「代码里怎么写的」：不证明 `stringResource` 真的返回了那段文案，也不证明
 * 排版效果（列表那行 `maxLines = 1`，来源名很长时短版释义会连同来源名一起被省略号截断——
 * 这属于排版取舍，见 `KbSourceTiers` 的类注释）。
 */
class KbTierLabelGuardTest {

    /** 主源码目录：单测工作目录是模块根（`app/`），两种布局都试一次。 */
    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    private fun source(relative: String): String {
        val f = File(sourceRoot, relative)
        assertTrue("主源码不存在：${f.path}", f.isFile)
        return f.readText()
    }

    private val tiers get() = source("com/ashkb/app/ui/knowledge/KbSourceTiers.kt")
    private val listScreen get() = source("com/ashkb/app/ui/knowledge/KnowledgeScreen.kt")
    private val detail get() = source("com/ashkb/app/ui/knowledge/KbDetailDialog.kt")

    private val stringsXml: String by lazy {
        listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml"))
            .firstOrNull { it.isFile }?.readText()
            ?: error("未找到 strings.xml（工作目录=${File(".").absolutePath}）")
    }

    /** 去掉行注释——类注释里会引用这些函数名，按原文扫会把注释当成代码。 */
    private fun code(src: String): String = src.lines().joinToString("\n") { it.substringBefore("//") }

    /** `strings.xml` 里某个 name 的正文（`null` = 没有这条）。 */
    private fun stringValue(name: String): String? =
        Regex("<string name=\"" + Regex.escape(name) + "\">([^<]*)</string>")
            .find(stringsXml)?.groupValues?.get(1)

    /** 列表那行：短版释义在用，且不再把裸层级拼进去。 */
    @Test
    fun `knowledge list shows the short tier label instead of the bare letter`() {
        val body = code(listScreen)
        assertTrue(
            "知识列表必须用 tierShortLabel 渲染层级（守卫形同虚设或已回退）",
            body.contains("tierShortLabel(entry.sourceTier)"),
        )
        assertFalse(
            "列表不得再把裸层级拼进展示字符串（这正是本次要修的「无解释的裸字母」）",
            body.contains("\${entry.sourceTier}"),
        )
    }

    /** 详情那行：完整版释义在用。 */
    @Test
    fun `knowledge detail shows the full tier label`() {
        val body = code(detail)
        assertTrue(
            "知识详情必须用 tierFullLabel 渲染层级（只显示裸字母等于没解释）",
            body.contains("tierFullLabel(entry.sourceTier)"),
        )
    }

    /** 四档都有短版与完整版，且都**有内容**（空字符串的释义等于没写）。 */
    @Test
    fun `all four tiers have a non-empty short and full explanation`() {
        for (tier in listOf("S1", "S2", "S3", "S4")) {
            val short = stringValue("kb_tier_${tier.lowercase()}_short")
            val full = stringValue("kb_tier_${tier.lowercase()}_full")
            assertTrue("$tier 缺短版释义", short != null && short.isNotBlank())
            assertTrue("$tier 缺完整释义", full != null && full.isNotBlank())
            // 短版必须仍然以层级字母打头：列表那行是「字母 + 释义」，字母是患者扫列表时唯一认得出的锚
            assertTrue("$tier 的短版释义没有以层级字母开头：$short", short!!.startsWith(tier))
        }
    }

    /** `S4` 是商业平台、**仅备用**：这条限定必须出现在两种长度里（原文规范里的要求）。 */
    @Test
    fun `commercial tier S4 keeps the backup-only caveat`() {
        assertTrue("S4 短版必须写明「商业」", stringValue("kb_tier_s4_short")!!.contains("商业"))
        assertTrue("S4 短版必须写明「仅备用」", stringValue("kb_tier_s4_short")!!.contains("仅备用"))
        assertTrue("S4 完整版必须写明「仅作备用」", stringValue("kb_tier_s4_full")!!.contains("仅作备用"))
    }

    /** 映射表覆盖 S1–S4，且未知层级回落到原值（**不显示空白**——那等于抹掉一条信息）。 */
    @Test
    fun `tier mapping covers S1 to S4 and falls back to the raw value`() {
        val body = code(tiers)
        for (tier in listOf("S1", "S2", "S3", "S4")) {
            assertTrue("$tier 没有出现在层级映射里", body.contains("\"$tier\" ->"))
        }
        for (funName in listOf("tierShortLabel", "tierFullLabel")) {
            assertTrue("$funName 缺失", body.contains("fun $funName(tier: String)"))
            // 一行式声明（`stringResource(lookup(tier) ?: return tier)`）：用整行正则把「回落」绑到函数上
            assertTrue(
                "$funName 必须在未知层级时回落到原值（显示空白等于抹掉「内容来自哪一档」）",
                // v1.1.2：原先把"一行式声明"写进正则，但 Kotlin **不允许表达式体里写 `return`**——
        // 那条正则锁的是一个**编译不过**的写法，害得实现只能绕开。改为锁定**行为**：
        // 函数体里必须出现「查不到映射就 return 原值」这条回落，且取文案走 stringResource。
        Regex(
            "fun " + funName + """\(tier: String\): String \{.*\?: return tier.*stringResource""",
            RegexOption.DOT_MATCHES_ALL,
        )
                    .containsMatchIn(body),
            )
        }
    }
}
