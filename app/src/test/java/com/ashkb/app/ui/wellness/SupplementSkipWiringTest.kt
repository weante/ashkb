package com.ashkb.app.ui.wellness

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.87（批次 12）：补剂「跳过」的**接线守卫**（纯静态扫描，随 `testDebugUnitTest` 一起跑）。
 *
 * ### 为什么是静态扫描而不是 UI 测试
 * 本模块**没有 Compose UI 测试依赖**（无 `androidx.compose.ui:ui-test-junit4`，也没有
 * `createComposeRule` 的可用环境），所以「卡片上有两个按钮、点了跳过写的是 skipped」这类
 * 事实无法用组合测试钉住。退而求其次但不含糊：直接把主源码当文本断言——
 * 这与 `RegexLiteralGuardTest`（ICU 花括号守卫）在本项目里已经用了三个版本的思路一致。
 *
 * ### 它挡的是哪一类错
 * ① **写状态词时手打字符串**：`checkInSupplement(sup, "skip", …)` 不会编译失败，只会让
 *    那一行永远不进依从率的分母（`completionByStatus` 把它归入 unknown：进分母却不计完成），
 *    用户看到的百分比会莫名其妙地掉。故断言调用点必须用 `AdherenceCalc.SKIPPED` / `.DONE`。
 * ② **忘了把跳过接进界面**：两个动作按钮里少一个，功能就等于没做（本轮的全部需求）。
 * ③ **历史行又写死「已服」**：详情弹层必须按状态取文案，否则跳过的记录会被显示成服用。
 * ④ **只在两处之一改口径**：状态判据必须来自 `SupplementLogStatus` 这一处。
 *
 * 局限（明说，不含糊）：文本断言证明的是「代码里怎么写的」，不是「运行时真的这样」。
 * 运行时那一半由 `SupplementLogStatusTest`（判据/文案）、`SupplementHistoryQueryTest`
 * （真库 SQL：跳过查得到、老数据不变）、`SupplementAdherenceParityTest`（口径回归锁）覆盖。
 */
class SupplementSkipWiringTest {

    /** 主源码路径：单测工作目录是模块根（`app/`），两种布局都试一次（与正则守卫同款）。 */
    private val sourceRoot: File =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("未找到主源码目录（工作目录=${File(".").absolutePath}）")

    private fun source(relative: String): String {
        val f = File(sourceRoot, relative)
        assertTrue("主源码不存在：${f.path}", f.isFile)
        return f.readText()
    }

    private val wellnessScreen get() = source("com/ashkb/app/ui/wellness/WellnessScreen.kt")
    private val detailSheet get() = source("com/ashkb/app/ui/wellness/SupplementDetailSheet.kt")

    @Test
    fun `卡片同时提供打卡与跳过两个动作`() {
        val src = wellnessScreen

        assertTrue(
            "必须用 AdherenceCalc.DONE 而不是字面量 \"done\"（写错的状态词不会编译失败，只会静默丢出统计）",
            src.contains("vm.checkInSupplement(sup, AdherenceCalc.DONE, null, null)"),
        )
        assertTrue(
            "必须用 AdherenceCalc.SKIPPED 而不是字面量",
            src.contains("vm.checkInSupplement(sup, AdherenceCalc.SKIPPED, null, null)"),
        )
        assertTrue(
            "跳过不该走任何表单 / 理由弹层（补剂没有「为什么没吃」这一问）",
            !src.contains("checkInSupplement(sup, AdherenceCalc.SKIPPED, \"") &&
                !src.contains("checkInSupplement(sup, AdherenceCalc.SKIPPED, reason"),
        )
    }

    /** 打卡态判据只有一份实现；卡片与详情都不能自己写状态字面量比较。 */
    @Test
    fun `状态判据与文案映射不重复实现`() {
        for (src in listOf(wellnessScreen, detailSheet)) {
            assertTrue(
                "不得内联状态字面量比较——判据与文案必须走 SupplementLogStatus（唯一实现）",
                !src.contains("status == \"done\"") && !src.contains("status == \"skipped\""),
            )
        }
        assertTrue(
            "卡片必须用共享判据算今日打卡态",
            wellnessScreen.contains("SupplementLogStatus.loggedToday(supLogs, sup.id)"),
        )
        assertTrue(
            "详情弹层的胶囊必须按记录状态取文案",
            detailSheet.contains("SupplementLogStatus.labelRes(log.status)"),
        )
        assertTrue(
            "详情弹层的胶囊必须按记录状态取色调",
            detailSheet.contains("SupplementLogStatus.tone(log.status)"),
        )
        assertTrue(
            "详情弹层必须引用状态判据对象（否则「跳过」这一行迟早会退回硬编码）",
            detailSheet.contains("import com.ashkb.app.domain.SupplementLogStatus"),
        )
        assertTrue(
            "色调映射只在 SupplementLogStatus 里定义一次（详情弹层自己映射就会出现第二份口径）",
            !detailSheet.contains("StatusTone."),
        )
    }

    /** 跳过的记录没有服用时刻，不能再用「服用于 …」那句话描述它。 */
    @Test
    fun `跳过的历史行换用跳过文案`() {
        val src = detailSheet

        assertTrue(
            "跳过的行必须换文案（沿用「服用于 …」会把一次跳过说成一次服用）",
            src.contains("R.string.nutrition_supplement_history_skipped_entry"),
        )
        assertTrue(
            "已服的行必须仍用原文案（老数据显示不变）",
            src.contains("R.string.nutrition_supplement_history_entry"),
        )
        assertEquals(
            "两条文案都要被引用，缺一条说明分支写漏了",
            2,
            listOf(
                "R.string.nutrition_supplement_history_skipped_entry",
                "R.string.nutrition_supplement_history_entry",
            ).count { src.contains(it) },
        )
    }
}
