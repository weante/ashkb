package com.ashkb.app.ui.checkup

import com.ashkb.app.domain.ClinicalThresholds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.2.10 审查裁决项 5：「临界」态在**展示层**的接线守卫。
 *
 * 为什么要静态断言而不是 Compose 测试：项目没有 Compose UI 测试基建（见
 * `HANDOFF-STATUS.md` 的「三处我没能验证」），而这一改动的风险恰恰是
 * 「判据写好了、但某一处渲染没接上」——那类漏接在真机上表现为「同一个值，
 * 列表里没标、PDF 里标了」，只有对着看才发现。静态断言能把四个渲染点钉死。
 *
 * 判据本身（含 5% 量程、越界优先、脏数据）由 `LabNearBoundaryTest` 覆盖，这里不重复测。
 */
class LabNearBoundaryWiringTest {

    private val imaging = readSource("app/src/main/java/com/ashkb/app/ui/checkup/CheckupLabImaging.kt")
    private val pdf = readSource("app/src/main/java/com/ashkb/app/ui/report/ReportPdfWriter.kt")

    @Test
    fun `列表行的数值胶囊接了临界态`() {
        assertTrue(
            "LabRow 必须把 isNearBoundary() 传给 LabValue",
            imaging.contains("near = lab.isNearBoundary()"),
        )
    }

    @Test
    fun `导入预览也按同一判据标临界`() {
        assertTrue(
            "导入预览行也要标临界，否则保存前后观感不一致",
            imaging.contains("near = r.abnormal != \"high\" && r.abnormal != \"low\" &&"),
        )
        assertTrue(
            "预览必须走 ClinicalThresholds.labNearBoundary，不能自己再写一套比较",
            imaging.contains("ClinicalThresholds.labNearBoundary(r.value, r.refLow, r.refHigh)"),
        )
    }

    @Test
    fun `LabValue 的临界分支排在越界分支之后`() {
        val body = functionBody(imaging, "private fun LabValue(")
        val high = body.indexOf("abnormal == \"high\"")
        val low = body.indexOf("abnormal == \"low\"")
        val near = body.indexOf("near ->")
        assertTrue("LabValue 必须处理 high", high >= 0)
        assertTrue("LabValue 必须处理 low", low >= 0)
        assertTrue("LabValue 必须处理临界（near ->）", near >= 0)
        assertTrue(
            "越界优先：near 分支必须在 high/low 之后，否则已越界的值会被标成临界",
            near > high && near > low,
        )
        assertTrue(
            "临界胶囊用 Warning 色调（维护者裁决项 2）",
            body.substring(near).contains("tone = StatusTone.Warning"),
        )
        assertTrue(
            "临界胶囊不带趋势箭头——临界不是方向性结论",
            !body.substring(near, minOf(body.length, near + 200)).contains("icon ="),
        )
    }

    @Test
    fun `临界单独计数 不进异常计数`() {
        assertTrue(
            "分组副标题必须单独统计临界（维护者裁决项 3）",
            imaging.contains("val nearCount = rows.count { it.isNearBoundary() }"),
        )
        val body = functionBody(imaging, "private fun LabDateGroup(")
        assertTrue(
            "有临界时副标题要走 lab_count_near / lab_count_abnormal_near",
            body.contains("R.string.lab_count_near") && body.contains("R.string.lab_count_abnormal_near"),
        )
        assertTrue(
            "异常计数仍只数 isAbnormal()，不得把临界混进去",
            imaging.contains("val abnormalCount = rows.count { it.isAbnormal() }"),
        )
    }

    @Test
    fun `PDF 里临界单独成段`() {
        assertTrue(
            "PDF 必须新增临界段",
            pdf.contains("R.string.pdf_section_labs_near"),
        )
        val nearBlock = pdf.substringAfter("val near = r.labs.filter {")
        assertTrue(
            "PDF 的临界判据也要走 ClinicalThresholds.labNearBoundary",
            nearBlock.contains("ClinicalThresholds.labNearBoundary(it.value, it.refLow, it.refHigh)"),
        )
        assertTrue(
            "PDF 的临界段必须排除已越界的行",
            nearBlock.contains("(ab == null || ab == \"normal\")"),
        )
        assertTrue(
            "PDF 异常段的过滤条件不能把临界值算成异常（abnormal 列只有 high/low/normal 三态）",
            pdf.contains("val abnormal = r.labs.filter { !it.abnormal.isNullOrBlank() && it.abnormal != \"normal\" }"),
        )
    }

    // ---- 辅助 ----

    private fun readSource(relative: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            if (File(dir, "settings.gradle.kts").isFile && File(dir, relative).isFile) {
                return File(dir, relative).readText()
            }
            dir = dir.parentFile
        }
        return File("../$relative").readText()
    }

    /** 取某个函数声明起、到下一个顶层声明前的源码片段（够覆盖函数体即可）。 */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val next = source.indexOf("\n@Composable", start + signature.length)
        val nextFun = source.indexOf("\nprivate fun ", start + signature.length)
        val nextInt = source.indexOf("\ninternal fun ", start + signature.length)
        val candidates = listOf(next, nextFun, nextInt).filter { it > start }
        val end = candidates.minOrNull() ?: source.length
        return source.substring(start, end)
    }
}
