package com.ashkb.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 批次 4（V-1 / V-3）的源码守卫。
 *
 * 为什么用源码断言而不是行为断言：
 *  - V-1 是 `Scaffold` 的一个形参，JVM 上无法观测「深色下没显式着色的文本用什么颜色」；
 *  - V-3 是「图上有没有逐日列表」，同理要真机渲染。
 * 但**退化**是可以在源码层面钉住的：删掉 `contentColor`、把列表从某个调用点摘掉、
 * 或者有人图省事给 `TrendChart` 加形参把列表塞进去——这三种都会让下面的断言变红。
 *
 * 对 [TrendChart] 加形参这条特别说明：`config/detekt/baseline.xml` 里
 * `TrendChart` 的 `LongMethod` / `CyclomaticComplexMethod` 是**按完整签名**登记的，
 * 加一个形参就会让基线失配、旧问题重新冒出来（批次 3 的 `OverviewPage` 已经踩过一次）。
 */
class UiUxBatch4GuardTest {

    private fun readSource(relative: String): String {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("找不到仓库根目录（settings.gradle.kts）")
        return File(root, relative).readText()
    }

    private fun norm(text: String): String =
        text.replace("\r\n", "\n").replace(Regex("[ \t]+"), " ")

    /** 去掉纯注释行：改动的注释里会引用旧写法，负向断言必须只看代码。 */
    private fun code(text: String): String =
        norm(text).lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    /**
     * 取一段源码片段用于断言。
     *
     * `flat` 把**换行也压成单空格**：Compose 的调用点几乎都是多行写的，
     * 用带 `\n` 的字面锚点在不同文件里必然 0 命中（这个坑在批次 3 的负向脚本里踩过）。
     */
    private fun body(text: String, signature: String, take: Int = 400): String {
        val flat = text.replace(Regex("\\s+"), " ")
        val needle = signature.replace(Regex("\\s+"), " ")
        val at = flat.indexOf(needle)
        require(at >= 0) { "找不到签名：$signature" }
        return flat.substring(at).take(take)
    }

    // ---------------- V-1 ----------------

    @Test
    fun `根 Scaffold 必须显式声明内容前景色，否则透明容器下的默认前景来自另一处`() {
        val shell = code(readSource("app/src/main/java/com/ashkb/app/ui/AppShell.kt"))
        assertTrue(
            "根 Scaffold 缺少 contentColor = MaterialTheme.colorScheme.onBackground",
            shell.contains("contentColor = MaterialTheme.colorScheme.onBackground"),
        )
        // 容器透明这条前提还在（页面自己画底）；哪天改回不透明，这条注释的前提就不成立了
        assertTrue(
            "containerColor 不再是 Color.Transparent —— V-1 的推理前提变了，需要重新评估",
            shell.contains("containerColor = Color.Transparent"),
        )
    }

    // ---------------- V-3 ----------------

    @Test
    fun `报告页展开图必须有逐日数值列表，且与图表同数据源同单位`() {
        val screen = code(readSource("app/src/main/java/com/ashkb/app/ui/report/ReportScreen.kt"))
        val sheet = body(screen, "TrendChart( points = s.points, unit = s.unit", 400)
        assertTrue(
            "展开 sheet 里 TrendChart 之后没有 DailyValuesList",
            sheet.contains("DailyValuesList(points = s.points, unit = s.unit)"),
        )
    }

    @Test
    fun `体重卡也必须有逐日数值列表`() {
        val vitals = code(readSource("app/src/main/java/com/ashkb/app/ui/wellness/WellnessVitalsSections.kt"))
        val card = body(vitals, "TrendChart(points = points, unit = \"kg\"", 400)
        assertTrue(
            "体重卡 TrendChart 之后没有 DailyValuesList",
            card.contains("DailyValuesList(points = points, unit = \"kg\")"),
        )
    }

    @Test
    fun `不许把逐日列表塞进 TrendChart，那会让 detekt 基线失配`() {
        val chart = code(readSource("app/src/main/java/com/ashkb/app/ui/components/TrendChart.kt"))
        assertFalse(
            "TrendChart 的签名被改了（新增形参），detekt baseline 会失配",
            chart.contains("dailyValues: Boolean"),
        )
        assertFalse(
            "TrendChart 内部直接调用了 DailyValuesList —— 应该由调用点各取所需",
            chart.contains("DailyValuesList("),
        )
    }

    @Test
    fun `逐日列表必须默认收起，否则几十天的化验数据会把图挤出屏幕`() {
        val list = code(readSource("app/src/main/java/com/ashkb/app/ui/components/DailyValuesList.kt"))
        assertTrue(
            "默认必须是收起的",
            list.contains("var expanded by rememberSaveable { mutableStateOf(false) }"),
        )
        // 展开态要能跨旋转保留：收到一半转屏又收起来，等于白点
        assertTrue(
            "折叠状态没有用 rememberSaveable，旋转会丢失",
            list.contains("rememberSaveable"),
        )
        // 展开后自身限高并滚动，否则 sheet 顶出屏幕、连收起按钮都够不到
        assertTrue(
            "展开后的列表没有限高滚动",
            list.contains(".heightIn(max = LIST_MAX_HEIGHT).verticalScroll(rememberScrollState())"),
        )
    }
}
