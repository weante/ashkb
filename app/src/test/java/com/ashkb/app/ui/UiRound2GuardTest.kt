package com.ashkb.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 审核卡 C2-13：UI 第二轮审核（C2-01 ~ C2-12，C2-11 为修订版 R1 + R2）的结构守卫。
 *
 * 与 [UiUxBatch3GuardTest] 同一种做法：本模块没有 Compose UI 测试依赖，能守的只有「写法还在不在」。
 * 守的是**回退**——有人把周月报失败态删掉、把场景卡改回一次性查询、把评分端点改回 Text + clickable。
 * 运行时是否真的显示正确，仍要按审核文档的真机清单验收。
 */
class UiRound2GuardTest {

    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    private fun src(relative: String): String =
        File(sourceRoot, relative).readText().replace("\r\n", "\n").replace(Regex("[ \t]+"), " ")

    /**
     * 去掉整行注释后再断言「某个写法不存在」。
     *
     * 与 `UiUxBatch3GuardTest.code()` 同一个理由：改动说明里经常引用**旧写法**原文
     * （例如 C2-12 的 KDoc 写着「`cards = vm.emergencyCards()` 有两个问题」），直接在全文里找
     * 负向断言会命中注释而不是代码。负向断言必须是"代码里没有"，不能是"全文没有"。
     */
    private fun code(text: String): String =
        text.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    private fun slice(text: String, from: String, length: Int): String {
        val start = text.indexOf(from)
        assertTrue("找不到定位串：$from", start >= 0)
        return text.substring(start, minOf(text.length, start + length))
    }

    @Test
    fun `周月报失败必须单独建模并在页面上可见`() {
        val vm = src("com/ashkb/app/ui/report/ReportViewModel.kt")
        assertTrue("周月报失败态", vm.contains("val periodicFailed: StateFlow<String?>"))
        val load = slice(vm, "fun loadPeriodic(", 1800)
        assertTrue("成功路径要清掉失败态", load.contains("_periodicFailed.value = null"))
        assertTrue("失败路径要置上失败态", load.contains("_periodicFailed.value = text"))

        val screen = src("com/ashkb/app/ui/report/ReportScreen.kt")
        val page = slice(screen, "private fun PeriodicPage(", 4000)
        assertTrue(
            "周月报页必须把失败态交给三态块",
            page.contains("PeriodicStatusBlock(hasData = p != null, failed = periodicFailed"),
        )
        val status = slice(screen, "internal fun PeriodicStatusBlock(", 900)
        assertTrue("首次失败走错误块而不是骨架屏", status.contains("StatsErrorBlock(message = failed, onRetry = onRetry)"))
        assertTrue("有旧数据时标注可能过期", status.contains("StatsStaleNote(onRetry = onRetry)"))
    }

    @Test
    fun `应急场景卡必须订阅数据流而不是一次性查询`() {
        val screen = src("com/ashkb/app/ui/emergency/EmergencyScreen.kt")
        val cards = slice(screen, "private fun EmergencySceneCards(", 2400)
        assertTrue("订阅可观察数据源", cards.contains("vm.emergencyCardsFlow.collectAsStateWithLifecycle()"))
        // 用 code() 剥掉整行注释再判——C2-12 的 KDoc 原文引用了旧写法 `cards = vm.emergencyCards()`，
        // 对全文（含注释）做 contains 会被这句说明文字误判为回退。
        assertFalse("一次性查询的旧写法不得回来", code(cards).contains("cards = vm.emergencyCards()"))
        assertTrue("空状态", cards.contains("loaded.isEmpty() -> EmptyState("))
    }

    @Test
    fun `评分输入两端标签必须是按钮`() {
        val forms = src("com/ashkb/app/ui/components/Forms.kt")
        val score = slice(forms, "fun ScoreInput(", 9000)
        assertTrue("「无」端", score.contains("TextButton(onClick = { onValueChange(range.first) })"))
        assertTrue("「最重」端", score.contains("TextButton(onClick = { onValueChange(range.last) })"))
        assertFalse(
            "Text + clickable 的旧写法触控区不足 48dp，且没有按钮语义",
            score.contains(".clickable { onValueChange(range.first) }"),
        )
    }

    @Test
    fun `中性胶囊的底色不能退回对比度不足的 surfaceContainerHighest`() {
        val tone = src("com/ashkb/app/ui/theme/Tone.kt")
        assertTrue(
            "onSurfaceVariant 压在 surfaceContainerHighest 上浅色主题仅 4.46:1，低于 AA 4.5:1",
            tone.contains("StatusTone.Neutral -> cs.surfaceContainerHigh to cs.onSurfaceVariant"),
        )
    }
}
