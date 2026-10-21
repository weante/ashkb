package com.ashkb.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI 2.0 §06 ④⑤ 的两处「结论徽章」要求：
 *
 * - ④ 报表 BASDAI 的「稳定期」结论胶囊（设计稿 `.st-ok`）。**它挂在全宽 BASDAI 概览卡
 *   的标题行右侧，不在两列小多图的格子里**——后者单格只有约 170dp，标题 + 末值 + 胶囊
 *   三段挤不下（v1.2.10 发布后真机实测「BASDAI 总…」截断，随即回退）；
 * - ⑤ 知识库条目**行右侧**的类别徽章（设计稿 `.tier`），底部「S1 · 来源名」证据等级行保留。
 *
 * 为什么用源码静态断言而不是 Compose 测试：本仓没有 Compose UI 测试基础设施，
 * 而这两条要求是**结构性**的（槽位在不在、胶囊挂在哪一格、类别胶囊有没有从左上挪走），
 * 静态扫描足以钉住——与 `ui/HealthHubVitalsPanelTest.kt`、`ui/ViewModelInitOrderGuardTest.kt`
 * 同一套做法。这些是**防漂移**断言，不是行为断言。
 *
 * ⚠️ **静态断言的边界**（v1.2.10 的教训）：它能证明「槽位存在、顺序正确」，**证明不了
 * 「放得下」**——④ 最初挂在窄格里的版本 6 条断言全绿，真机上标题却已截断。凡是新增
 * 「标题 + 数值 + 徽章」三段式布局，必须真机看一眼，别把绿灯当验收。
 */
class Ui2ConclusionBadgesTest {

    private val chartSource: String =
        readSource("app/src/main/java/com/ashkb/app/ui/components/SmallTrendChart.kt")

    private val reportSource: String =
        readSource("app/src/main/java/com/ashkb/app/ui/report/ReportScreen.kt")

    private val knowledgeSource: String =
        readSource("app/src/main/java/com/ashkb/app/ui/knowledge/KnowledgeScreen.kt")

    /**
     * 定位仓库根：测试的工作目录是 `app/`（Gradle 单测的默认 cwd），源码在 `../` 之外。
     * 逐级向上找一个含 `settings.gradle.kts` 的目录，避免把 `../../..` 的层数写死。
     * （与 `ui/me/MedRouteFrequencyGuardTest.kt:102-111` 同一份理由。）
     */
    private fun readSource(relative: String): String {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            val candidate = java.io.File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            if (java.io.File(dir, "settings.gradle.kts").isFile) break
            dir = dir.parentFile
        }
        return java.io.File("../$relative").absoluteFile.readText()
    }

    /** 取区间 `[from, to)`；两端任一找不到就返回空串（断言会给出「必须还有 …」的失败信息）。 */
    private fun slice(source: String, from: String, to: String): String {
        val start = source.indexOf(from)
        if (start < 0) return ""
        val end = source.indexOf(to, start)
        return source.substring(start, if (end > start) end else source.length)
    }

    // ---------------------------------------------------------------- ④ 报表

    @Test
    fun `稳定期胶囊挂在全宽 BASDAI 卡的标题行右侧`() {
        // 胶囊的宿主必须是**概览页那张全宽卡**的 action 槽，而不是趋势页的迷你格。
        val overview = slice(reportSource, "private fun OverviewPage(", "private fun PeriodicPage(")
        assertTrue("OverviewPage 区间必须能定位到", overview.isNotEmpty())
        val basdaiCard = slice(
            overview,
            "SectionCard(",
            "if (ClinicalThresholds.basdaiHigh(o.basdaiLatest.total))",
        )
        assertTrue("BASDAI 概览卡区间必须能定位到", basdaiCard.isNotEmpty())
        assertTrue(
            "「稳定期」胶囊必须挂在这张全宽卡的 action 槽上（标题行右侧）",
            basdaiCard.contains("action = if (basdaiStable)") &&
                basdaiCard.contains("R.string.report_basdai_stable"),
        )
        assertTrue(
            "判据必须走 ClinicalThresholds.basdaiHigh（Double 重载，避免各自 toInt() 截断），" +
                "稳定期 = 最新一次 BASDAI 低于高活动度阈值",
            basdaiCard.contains("!ClinicalThresholds.basdaiHigh(o.basdaiLatest.total)"),
        )
        assertTrue(
            "结论胶囊走 Success 色调，对应设计稿 `.st-ok`（软底 + 状态点 + 文字）",
            basdaiCard.contains("tone = StatusTone.Success"),
        )
    }

    @Test
    fun `迷你图卡不再承载结论胶囊`() {
        assertFalse(
            "SmallTrendChart 曾有的 trailing 槽已在 v1.2.10 发布后回退——" +
                "两列小多图的单格约 170dp，标题 + 末值 + 胶囊三段挤不下（真机截断实证）",
            chartSource.contains("trailing: (@Composable () -> Unit)? = null"),
        )
        assertFalse(
            "MiniSeries 不再有 badge 字段（胶囊已改挂全宽卡）",
            reportSource.contains("val badge: String? = null"),
        )
        assertFalse(
            "MiniSeriesCell 不得再往 SmallTrendChart 传结论槽",
            reportSource.contains("trailing = if (s.badge != null)"),
        )
        val titleRow = slice(
            chartSource,
            "Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom)",
            "if (points.isEmpty())",
        )
        assertTrue("标题行区间必须能定位到（SmallTrendChart 结构变了？）", titleRow.isNotEmpty())
        assertTrue(
            "标题列仍需 weight(1f) 收紧，末值作为非权重子项排在之后——顺序反了会把末值压成零宽（v1.0.90 教训）",
            titleRow.indexOf("Modifier.weight(1f)") in 1 until titleRow.indexOf("Text(lastLabel"),
        )
    }

    @Test
    fun `BASDAI 高危胶囊的判据已收敛到阈值常量`() {
        val overview = slice(
            reportSource,
            "private fun OverviewPage(",
            "private fun PeriodicPage(",
        )
        assertTrue("OverviewPage 区间必须能定位到", overview.isNotEmpty())
        assertTrue(
            "高危胶囊判据必须用 ClinicalThresholds.basdaiHigh，不要再写裸字面量 4.0",
            overview.contains("if (ClinicalThresholds.basdaiHigh(o.basdaiLatest.total))"),
        )
        assertFalse(
            "裸字面量 4.0 是本次一并收敛掉的旧写法，不得回潮",
            overview.contains("total >= 4.0"),
        )
    }

    // ------------------------------------------------------------ ⑤ 知识库

    /** 取 `KbListCard` 函数体（它是本文件最后一个函数）。 */
    private fun kbListCardBody(): String {
        val start = knowledgeSource.indexOf("private fun KbListCard(")
        assertTrue("KnowledgeScreen.kt 里必须还有 KbListCard（设计稿 §06 ⑤ 的宿主）", start > 0)
        return knowledgeSource.substring(start)
    }

    @Test
    fun `知识库类别徽章移到了标题行右侧`() {
        val body = kbListCardBody()
        val titleAt = body.indexOf("entry.title")
        assertTrue("KbListCard 里必须有标题 Text", titleAt > 0)
        // 整个标题行：从承载标题的 `Row(Modifier.fillMaxWidth()` 到其后的摘要 Text。
        val rowStart = body.lastIndexOf("Row(Modifier.fillMaxWidth()", titleAt)
        assertTrue("标题必须包在标题行 Row 里", rowStart in 0 until titleAt)
        val summaryAt = body.indexOf("entry.summary", titleAt)
        assertTrue("标题行之后必须是摘要 Text", summaryAt > titleAt)
        val titleRow = body.substring(rowStart, summaryAt)
        assertTrue(
            "标题行必须挂上 CategoryBadge(entry.category)——这是 §06 ⑤ 的「行右侧徽章」",
            titleRow.contains("CategoryBadge(entry.category)"),
        )
        assertTrue(
            "徽章必须排在标题之后（右贴行尾），不能放在标题前面",
            titleRow.indexOf("entry.title") < titleRow.indexOf("CategoryBadge(entry.category)"),
        )
        assertTrue(
            "标题列用 weight(1f) 收紧、徽章非权重排在之后；顺序反了会挤成零宽",
            titleRow.contains("Modifier.weight(1f)"),
        )
        val chips = body.substring(body.indexOf("FlowRow("), titleAt)
        assertFalse(
            "类别胶囊必须从卡片内左上的胶囊组里移除（否则同一个类别显示两次）",
            chips.contains("categoryLabel(entry.category)"),
        )
        assertTrue(
            "其余三枚状态胶囊（高风险 / 有备注 / 已过复核日）仍留在胶囊组里",
            chips.contains("R.string.knowledge_high_risk") &&
                chips.contains("R.string.kb_note_has_tag") &&
                chips.contains("R.string.checkup_overdue_short"),
        )
    }

    @Test
    fun `类别徽章走设计稿 tier 形态与统一色调解析`() {
        val badge = slice(knowledgeSource, "private fun CategoryBadge(", "private fun KbListCard(")
        assertTrue("CategoryBadge 必须能定位到", badge.isNotEmpty())
        assertTrue(
            "圆角取 shapes.extraSmall（6dp，设计稿 `.tier` 的 border-radius:6px）",
            badge.contains("MaterialTheme.shapes.extraSmall"),
        )
        assertTrue(
            "字号取 labelSmall（12sp 是项目硬下限，设计稿的 10px 会糊）并加粗",
            badge.contains("labelSmall") && badge.contains("FontWeight.Bold"),
        )
        assertTrue(
            "颜色一律走 StatusTone.colors()，禁止屏幕自己写 when (tone)",
            badge.contains("categoryTone(category).colors()"),
        )
        assertFalse("徽章不得复用 StatusChip 的胶囊形态（CircleShape + 28dp 下限会顶高行）", badge.contains("StatusChip("))
    }

    @Test
    fun `知识库条目的证据等级行原样保留`() {
        val body = kbListCardBody()
        assertTrue(
            "底部「S1 · 来源名」证据等级行是 §06 ⑤ 明确要求保留的（徽章是新增、不是替换）",
            body.contains("tierShortLabel(entry.sourceTier)") && body.contains("entry.sourceName"),
        )
    }
}
