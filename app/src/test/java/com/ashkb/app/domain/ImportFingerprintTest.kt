package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.11（缺陷工单 B01）：AI 报告导入的**稳定指纹**。
 *
 * 缺陷原状：同一张报告粘两次，旧代码每次都生成新 id、没有任何批次身份，化验单整份翻倍，
 * 「化验 171 项」这类计数与趋势图被污染，而用户看不出是哪一次重复。
 *
 * 指纹要同时满足两件相反的事，这两件事就是本测试的全部：
 *  · **同内容必同指纹** —— 否则挡不住重复导入；
 *  · **内容有任何实质差异必不同指纹** —— 否则用户修正报告后重新导入会被误判「已导入」，
 *    改动静默丢失（这比重复导入更糟：用户以为修好了）。
 */
class ImportFingerprintTest {

    private fun lab(
        date: String? = "2026-08-02",
        hospital: String? = "深圳宝安中医院",
        note: String? = null,
        rows: List<LabImportRow> = listOf(
            LabImportRow(testName = "血沉(ESR)", value = 15.0, valueText = "15", unit = "mm/h", refLow = 0.0, refHigh = 20.0),
        ),
    ) = LabImport(date = date, hospital = hospital, note = note, rows = rows, skippedLines = emptyList())

    @Test
    fun `同一份化验报告算出同一指纹`() {
        assertEquals(ImportFingerprint.lab(lab()), ImportFingerprint.lab(lab()))
    }

    @Test
    fun `指纹是 64 位十六进制`() {
        val fp = ImportFingerprint.lab(lab())
        assertEquals(64, fp.length)
        assertTrue("必须全为小写十六进制：$fp", Regex("^[0-9a-f]{64}$").matches(fp))
    }

    @Test
    fun `改了数值就是另一份报告`() {
        val a = lab()
        val b = lab(
            rows = listOf(
                LabImportRow(testName = "血沉(ESR)", value = 15.1, valueText = "15.1", unit = "mm/h", refLow = 0.0, refHigh = 20.0),
            ),
        )
        assertNotEquals(ImportFingerprint.lab(a), ImportFingerprint.lab(b))
    }

    @Test
    fun `改了参考边界语义就是另一份报告`() {
        // `<5` 与 `≤5` 是不同内容——判读结论完全不同，不能算同一份
        val strict = lab(rows = listOf(
            LabImportRow(testName = "CRP", value = 5.0, valueText = "5", unit = "mg/L", refHigh = 5.0, refHighInclusive = false),
        ))
        val inclusive = lab(rows = listOf(
            LabImportRow(testName = "CRP", value = 5.0, valueText = "5", unit = "mg/L", refHigh = 5.0, refHighInclusive = true),
        ))
        assertNotEquals(ImportFingerprint.lab(strict), ImportFingerprint.lab(inclusive))
    }

    @Test
    fun `改了医院或日期就是另一份报告`() {
        assertNotEquals(ImportFingerprint.lab(lab()), ImportFingerprint.lab(lab(hospital = "南方医院")))
        assertNotEquals(ImportFingerprint.lab(lab()), ImportFingerprint.lab(lab(date = "2026-08-03")))
    }

    @Test
    fun `行的顺序不同即不同指纹`() {
        // 顺序不同意味着报告本身不同（两行同名指标调换位置在真实报告里几乎不会出现，
        // 但**同一天测两次同一指标**是很常见的——那两行数值不同，指纹必须区分）
        val a = lab(rows = listOf(
            LabImportRow(testName = "CRP", value = 5.0, valueText = "5"),
            LabImportRow(testName = "ESR", value = 15.0, valueText = "15"),
        ))
        val b = lab(rows = listOf(
            LabImportRow(testName = "ESR", value = 15.0, valueText = "15"),
            LabImportRow(testName = "CRP", value = 5.0, valueText = "5"),
        ))
        assertNotEquals(ImportFingerprint.lab(a), ImportFingerprint.lab(b))
    }

    @Test
    fun `null 与空串视为同一件事`() {
        val a = lab(note = null)
        val b = lab(note = "")
        assertEquals(ImportFingerprint.lab(a), ImportFingerprint.lab(b))
    }

    @Test
    fun `空白差异不算新内容`() {
        // 报告文本常有首尾空格；归一化后同内容不应被当成新报告（否则每次粘贴都算新的一份）
        val a = lab(hospital = "南方医院")
        val b = lab(hospital = "  南方医院  ")
        assertEquals(ImportFingerprint.lab(a), ImportFingerprint.lab(b))
    }

    @Test
    fun `化验与影像的指纹域互不干扰`() {
        val imaging = ImagingImport(
            date = "2026-08-02", modality = "MRI", bodyPart = "骶髂关节",
            hospital = null, findings = null, conclusion = null, compare = null, skippedLines = emptyList(),
        )
        val imagingFp = ImportFingerprint.imaging(imaging)
        assertEquals(64, imagingFp.length)
        // 影像指纹带 kind 前缀，同一段文本按两种模板解析也不会撞
        assertNotEquals(ImportFingerprint.lab(lab(date = "2026-08-02")), imagingFp)
    }

    @Test
    fun `同一份影像报告算出同一指纹`() {
        fun imp() = ImagingImport(
            date = "2026-07-31", modality = "MRI", bodyPart = "骶髂关节",
            hospital = "南方医院", findings = "炎性水肿信号", conclusion = "符合axSpA", compare = null,
            skippedLines = listOf("噪声行"),
        )
        // skippedLines **不参与**指纹：它是解析器的诊断信息，不是报告内容。
        // 同一份报告重导时未识别行的措辞可能因模板微调而变，那不该让用户看到两份"不同"的报告。
        assertEquals(ImportFingerprint.imaging(imp()), ImportFingerprint.imaging(imp()))
    }
}
