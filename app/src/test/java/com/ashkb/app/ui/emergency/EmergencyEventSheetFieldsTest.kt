package com.ashkb.app.ui.emergency

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.10：急诊事件表单的**每一个草稿字段都必须有输入格**。
 *
 * ### 为什么必须有这条测试
 * v1.2.2 把 `EmergencyScreen.kt`（912 行）拆出 [EmergencyEventSheet] 时，是按行号「目测」
 * 截断的（那次提交说明自己记了：`git diff --shortstat` 显示删了 912 行而本意只删 167 行，
 * 靠 diff 自己发现并从 git 恢复重做）。恢复重做之后，表单里那一格「已采取措施」没了，
 * 而「症状描述」变成了两份。
 *
 * 后果不是显示问题：`actions` 成了一个**没有任何输入能写进去**的状态变量，于是
 * `EventDraft.toEvent` 每次都把 `actionsTaken` 写成 null——用户填的现场处置（吃了什么药、
 * 冷敷、去了急诊）在保存时被静默丢掉，界面上不会有任何提示，因为他根本没地方填。
 * 直到 v1.2.10 做审查 §6.9 时才发现，中间隔着 8 个版本。
 *
 * ### 为什么是源码文本扫描
 * 本模块没有 `androidx.compose.ui:ui-test-junit4` 依赖，起不了 `createComposeRule`
 * （同 [com.ashkb.app.ui.today.TodaySupplementCardWiringTest] 与
 * [com.ashkb.app.ui.wellness.SupplementSkipWiringTest] 的理由）。
 * 而这条测试要拦的正是「某一行代码在重构中被删掉」——文本扫描恰好是最直接的工具。
 */
class EmergencyEventSheetFieldsTest {

    private fun readSource(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        checkNotNull(dir) { "找不到仓库根（settings.gradle.kts）" }
        val f = File(dir, relative)
        assertTrue("源文件不存在：${f.absolutePath}", f.exists())
        return f.readText()
    }

    private val sheet by lazy { readSource("app/src/main/java/com/ashkb/app/ui/emergency/EmergencyEventSheet.kt") }

    @Test
    fun `草稿的每个文本字段都有输入格`() {
        // EventDraft 的文本字段：scene 由选择器负责、date 单独有格，其余逐个列出。
        listOf("symptoms", "actions", "hospitalName", "outcome", "notes").forEach { field ->
            assertTrue(
                "草稿字段 `$field` 没有任何输入格能把用户输入写进去——" +
                    "这类字段会在保存时被静默写成 null。",
                sheet.contains("$field, { $field = it }"),
            )
        }
    }

    @Test
    fun `已采取措施这一格不能被删掉`() {
        // 这一格在 v1.2.2 丢过一次（见类注释），单独再锁一条，并检查它绑的是正确的标签。
        assertTrue(
            "「已采取措施」输入格缺失或绑错了标签",
            sheet.contains("actions, { actions = it }") &&
                sheet.contains("R.string.emergency_actions_taken"),
        )
    }

    @Test
    fun `症状描述只出现一次`() {
        // v1.2.2 的事故形态是「症状描述」被复制成两份、真正要的那格没了。
        val n = Regex(Regex.escape("R.string.symptom_description")).findAll(sheet).count()
        assertTrue("症状描述的标签出现了 $n 次，应恰好 1 次", n == 1)
    }

    @Test
    fun `表单字段按顺序收集成草稿`() {
        // 构造点必须把全部字段传进 EventDraft，漏一个就是又一个静默丢字段。
        val call = Regex("EventDraft\\(([^)]*)\\)", RegexOption.DOT_MATCHES_ALL)
            .find(sheet)?.groupValues?.get(1)
        assertTrue("找不到 EventDraft 构造点", call != null)
        listOf("scene", "date", "symptoms", "actions", "hospitalVisit", "hospitalName", "outcome", "resolved", "notes")
            .forEach { field ->
                assertTrue("EventDraft 构造点漏传了 `$field`", call!!.contains(field))
            }
    }
}
