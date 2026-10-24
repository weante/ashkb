package com.ashkb.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.2.11：补剂打卡的「部分」入口必须在**首页与营养页两处同时存在**。
 *
 * ### 为什么这需要守卫
 * 数据层早就认三态：`AdherenceCalc.SETTLED_STATUSES` = done / partial / skipped，
 * `SupplementLogStatus.loggedToday` 也按这三态判定「今天有没有交代」，
 * 依从率（`ReportRepository`）按 0.5 计 partial。
 * 但 UI 一直只给「已服 / 跳过」两个入口——**用户无法产生 partial 记录**，
 * 于是「部分完成按 0.5 计」这条口径在真实数据里永远触发不到。
 *
 * 两处入口分别长在：
 *  · `ui/today/TodayScreen.kt` 的 `TodaySupplementRow`（首页补剂卡）
 *  · `ui/wellness/WellnessScreen.kt` 的 `SupplementCardRow`（营养页补剂档案）
 * 只补一处，两处就会漂移：用户在首页只能二选一、翻到营养页却有三个按钮。
 * 这正是本项目反复踩过的「同一指标写在多处必然漂移」老问题，故用静态断言钉住两处。
 *
 * ### 静态断言的边界（照实说）
 * 它只证明「代码里存在这条路径」，**不证明按钮在真机上看得见、点得到**。
 * 真机验收仍是唯一终审（见 NEXT-SESSION.md §五「静态断言 ≠ 验收」）。
 */
class SupplementPartialEntryTest {

    private val repoRoot: File by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return@lazy dir
            dir = dir.parentFile
        }
        File("../")
    }

    private fun readSource(relative: String): String {
        val file = File(repoRoot, relative)
        assertTrue("找不到源文件：${file.absolutePath}", file.isFile)
        return file.readText()
    }

    /** 取 `private fun <name>(` 起到该函数之后的第一个 `\n@Composable` 前的区间（够用即可）。 */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("找不到函数签名：$signature", start >= 0)
        val end = source.indexOf("\n@Composable", start + signature.length)
        return source.substring(start, if (end >= 0) end else source.length)
    }

    @Test
    fun `首页补剂行提供部分入口`() {
        val source = readSource("app/src/main/java/com/ashkb/app/ui/today/TodayScreen.kt")
        // 回调收在 data class 里（detekt LongParameterList 阈值 8，加「部分」正好踩线）。
        // 注意取到「换行 + 单个右括号」为止——形参类型里也带 `()`，按第一个 `)` 截会切掉一半。
        val actions = source.substringAfter("private data class SupplementCheckInActions(", "")
            .substringBefore("\n)")
        assertTrue(
            "SupplementCheckInActions 必须有 onPartial——否则首页产生不了 partial 记录",
            actions.contains("val onPartial: () -> Unit"),
        )
        val body = functionBody(source, "private fun TodaySupplementRow(")
        assertTrue(
            "「部分」按钮的文案要复用 med_history_status_partial，" +
                "与药品历史、补剂详情弹层同源（禁止另起一个同义词串）",
            body.contains("R.string.med_history_status_partial"),
        )
        assertTrue("「部分」按钮必须真的接到 actions.onPartial", body.contains("onClick = actions.onPartial"))
    }

    @Test
    fun `营养页补剂行提供部分入口`() {
        val body = functionBody(
            readSource("app/src/main/java/com/ashkb/app/ui/wellness/WellnessScreen.kt"),
            "private fun SupplementCardRow(",
        )
        assertTrue(
            "SupplementCardRow 必须有 onPartial 形参——否则营养页产生不了 partial 记录",
            body.contains("onPartial: () -> Unit"),
        )
        assertTrue(
            "营养页的「部分」按钮同样要复用 med_history_status_partial",
            body.contains("R.string.med_history_status_partial"),
        )
        assertTrue("「部分」按钮必须真的接到 onPartial", body.contains("onClick = onPartial"))
    }

    @Test
    fun `两处入口都真的写入 PARTIAL 状态`() {
        val today = readSource("app/src/main/java/com/ashkb/app/ui/today/TodayScreen.kt")
        assertTrue(
            "首页必须把 onPartial 接到 checkInSupplement(sup, PARTIAL)",
            today.contains("onPartial = { vm.checkInSupplement(sup, AdherenceCalc.PARTIAL) }"),
        )
        val wellness = readSource("app/src/main/java/com/ashkb/app/ui/wellness/WellnessScreen.kt")
        assertTrue(
            "营养页必须把 onPartial 接到 checkInSupplement(sup, PARTIAL, null, null)",
            wellness.contains(
                "onPartial = { vm.checkInSupplement(sup, AdherenceCalc.PARTIAL, null, null) }",
            ),
        )
    }
    @Test
    fun `三个打卡按钮不再挤在名称右侧的同一行`() {
        // 名称列用 weight(1f) 吃掉剩余宽度；若三个按钮仍在同一个 Row 里与它并列，
        // 窄屏 / 大字号下名称会被压成零宽（营养页 v1.0.88 真机踩过，见其 KDoc）。
        val today = readSource("app/src/main/java/com/ashkb/app/ui/today/TodayScreen.kt")
        val body = functionBody(today, "private fun TodaySupplementRow(")
        assertTrue(
            "TodaySupplementRow 的动作行必须用 FlowRow：窄屏下三个按钮整体折行，" +
                "而不是把末尾那个挤出屏幕",
            body.contains("FlowRow("),
        )
    }

    @Test
    fun `补剂状态文案与色调仍走唯一实现`() {
        // 三态一旦在 UI 各处自己写 when，文案/色调必然漂移（SupplementLogStatus 的 KDoc 已论证）。
        val today = readSource("app/src/main/java/com/ashkb/app/ui/today/TodayScreen.kt")
        val body = functionBody(today, "private fun TodaySupplementRow(")
        assertTrue(
            "已记录态的文字必须走 SupplementLogStatus.labelRes",
            body.contains("SupplementLogStatus.labelRes(status)"),
        )
        assertTrue(
            "已记录态的色调必须走 SupplementLogStatus.tone",
            body.contains("SupplementLogStatus.tone(status)"),
        )
        assertEquals(
            "补剂行不得自己写 when(status)——状态映射只允许在 SupplementLogStatus 里实现",
            0,
            Regex("""when\s*\(\s*status\s*\)""").findAll(body).count(),
        )
    }
}
