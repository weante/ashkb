package com.ashkb.app.ui.checkup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.2.11（缺陷工单 A07）：化验表单校验 + 「编辑不清掉异常兜底」。
 *
 * 两类断言：
 *  · `LabDraftRules` 的判据（纯函数，直接测）；
 *  · `LabDraft.toRecord` 是否把旧 `abnormal` 带过去——它是 private data class 里的方法，
 *    JVM 侧够不着，只能读源码断言（项目既有守卫的同一手法，见 `HealthHubVitalsPanelTest`）。
 */
class LabDraftRulesTest {

    // ---- 数字校验 ----

    @Test
    fun `留空合法`() {
        assertFalse("留空 = 未填，不该报错", LabDraftRules.invalidNumber(""))
        assertFalse(LabDraftRules.invalidNumber("   "))
    }

    @Test
    fun `正常数字合法`() {
        assertFalse(LabDraftRules.invalidNumber("5.2"))
        assertFalse(LabDraftRules.invalidNumber("-3"))
    }

    @Test
    fun `非数字被拦下`() {
        assertTrue("此前静默转 null 落库，等于把用户输入丢掉", LabDraftRules.invalidNumber("abc"))
        assertTrue(LabDraftRules.invalidNumber("5.2mg"))
        assertTrue(LabDraftRules.invalidNumber("1,5"))
    }

    // ---- 反向区间 ----

    @Test
    fun `下限大于上限算反向`() {
        assertTrue(LabDraftRules.reversedRange("20", "8"))
    }

    @Test
    fun `单侧范围不算反向`() {
        assertFalse("只有上限 / 只有下限都是合法形态", LabDraftRules.reversedRange("", "8"))
        assertFalse(LabDraftRules.reversedRange("3.5", ""))
        assertFalse(LabDraftRules.reversedRange("", ""))
    }

    @Test
    fun `相等不算反向`() {
        assertFalse(LabDraftRules.reversedRange("5", "5"))
    }

    @Test
    fun `非数字不参与反向判定`() {
        // 非数字已由 invalidNumber 报错；这里不该再重复报「反向」，否则用户看到两条互相矛盾的提示
        assertFalse(LabDraftRules.reversedRange("abc", "8"))
    }

    // ---- 保存按钮 ----

    @Test
    fun `项目名为空不能存`() {
        assertFalse(LabDraftRules.canSave(testName = "", value = "5", refLow = "", refHigh = ""))
    }

    @Test
    fun `合法输入可以存`() {
        assertTrue(LabDraftRules.canSave("CRP", "5.2", "0", "8"))
        assertTrue("留空参考范围也可以存", LabDraftRules.canSave("CRP", "5.2", "", ""))
    }

    @Test
    fun `任一字段填错都不能存`() {
        assertFalse("数值非法却还能存，就是 A07 要堵的静默丢数据", LabDraftRules.canSave("CRP", "abc", "", ""))
        assertFalse(LabDraftRules.canSave("CRP", "5.2", "20", "8"))
        assertFalse(LabDraftRules.canSave("CRP", "5.2", "x", ""))
    }

    // ---- 源码断言：编辑不清掉异常兜底 ----

    @Test
    fun `编辑时把旧 abnormal 带过去当兜底`() {
        val body = functionBody(readSource(FORMS), "private data class LabDraft(")
        assertTrue(
            "A07 的核心：无参考范围时 saveLabResult 算不出本地判读，会原样保留传入的 abnormal——" +
                "若这里不带上旧值，编辑一条无范围记录就会把它的偏高/偏低标记静默清掉",
            body.contains("abnormal = existing?.abnormal"),
        )
        assertTrue(
            "aiAbnormal 是只读留档，同样不该被编辑抹掉",
            body.contains("aiAbnormal = existing?.aiAbnormal"),
        )
    }

    @Test
    fun `保存按钮走统一判据而不是只看项目名`() {
        val src = readSource(FORMS)
        val sheet = functionBody(src, "internal fun LabResultFormSheet(")
        assertTrue("按钮启用条件必须是 canSave", sheet.contains("enabled = canSave"))
        assertFalse(
            "不能退回「只看 testName 非空」——那样非法数字与反向区间仍能保存",
            sheet.contains("enabled = testName.isNotBlank()"),
        )
    }

    // ---- 工具 ----

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return File("../")
    }

    private fun readSource(relative: String): String = File(repoRoot(), relative).readText()

    /** 取从签名起、到下一个顶层 `}` 或下一个 `@Composable` / `private data class` 之前的区间。 */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "找不到签名：$signature" }
        val nextComposable = source.indexOf("\n@Composable", start)
        val nextData = source.indexOf("\nprivate data class", start)
        val candidates = listOfNotNull(
            nextComposable.takeIf { it > 0 },
            nextData.takeIf { it > 0 },
            source.length,
        )
        val end = candidates.min()
        assertEquals(true, end > start)
        return source.substring(start, end)
    }

    private companion object {
        const val FORMS = "app/src/main/java/com/ashkb/app/ui/checkup/CheckupLabForms.kt"
    }
}
