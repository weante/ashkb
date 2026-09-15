package com.ashkb.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M6 AI 导入解析器单测：格式参照真实病历（南方医院放射/磁共振报告、深圳宝安中医院生化检验单）。
 */
class ReportImportParserTest {

    // ---- 化验单 ----

    @Test
    fun parseLab_fullSample() {
        val text = """
            【化验单整理】
            日期: 2026-08-02
            医院: 深圳宝安中医院
            项目, 结果, 单位, 参考范围, 标记
            血沉(ESR), 15, mm/h, 0-20, 正常
            C-反应蛋白(CRP), 10.8, mg/L, 0-8, 偏高
            白细胞计数(WBC), 6.1, 10^9/L, 3.5-9.5, 正常
            血红蛋白(HGB), 132, g/L, 115-150, 正常
        """.trimIndent()
        val result = ReportImportParser.parseLab(text)!!
        assertEquals("2026-08-02", result.date)
        assertEquals("深圳宝安中医院", result.hospital)
        assertEquals(4, result.rows.size)

        val esr = result.rows[0]
        assertEquals("血沉(ESR)", esr.testName)
        assertEquals(15.0, esr.value!!, 0.001)
        assertEquals("mm/h", esr.unit)
        assertEquals(0.0, esr.refLow!!, 0.001)
        assertEquals(20.0, esr.refHigh!!, 0.001)
        assertEquals("normal", esr.abnormal)

        assertEquals("high", result.rows[1].abnormal)
    }

    @Test
    fun parseLab_skipsTemplateEcho() {
        // AI 复读模板说明 / 表头时不应产生数据行
        val text = """
            日期: 2026-08-02
            项目, 结果, 单位, 参考范围, 标记
            血沉(ESR), 15, mm/h, 0-20, 正常
            说明：-「项目」用报告上的中文名，可带英文缩写
            - 「结果」只填数字，不要带单位
            备注：空腹采血
        """.trimIndent()
        val result = ReportImportParser.parseLab(text)!!
        assertEquals(1, result.rows.size)
        assertEquals("血沉(ESR)", result.rows[0].testName)
        assertEquals("空腹采血", result.note)
    }

    @Test
    fun parseLab_textValuesAndMarks() {
        val text = """
            日期: 2026-08-02
            类风湿因子(RF), ↑20, IU/mL, 0-20, 偏高↑
            乙肝表面抗原(HBsAg), 阴性, -, 阴性, 正常
        """.trimIndent()
        val result = ReportImportParser.parseLab(text)!!
        val rf = result.rows[0]
        assertNull(rf.value)
        assertEquals("↑20", rf.valueText)
        assertEquals("high", rf.abnormal)
        // 阴性为非数值结果
        assertEquals("阴性", result.rows[1].valueText)
        assertNull(result.rows[1].value)
    }

    @Test
    fun parseLab_dateVariants() {
        listOf(
            "日期: 2026/7/31" to "2026-07-31",
            "日期: 2026.07.31" to "2026-07-31",
            "日期: 2026年7月31日" to "2026-07-31",
            "日期：2026-07-31 14:30" to "2026-07-31",
        ).forEach { (raw, expect) ->
            val text = "$raw\n血沉(ESR), 15, mm/h, 0-20, 正常"
            val parsed = ReportImportParser.parseLab(text)!!.date
            assertEquals("failed for: $raw", expect, parsed)
        }
    }

    @Test
    fun parseLab_emptyInput() {
        assertNull(ReportImportParser.parseLab(""))
        assertNull(ReportImportParser.parseLab("   \n  "))
        assertNull(ReportImportParser.parseLab("随手打的文字没有逗号"))
    }

    // ---- 参考范围 ----

    @Test
    fun parseRange_variants() {
        assertEquals(2.9 to 8.2, ReportImportParser.parseRange("2.9-8.2"))
        assertEquals(9.0 to 50.0, ReportImportParser.parseRange("9–50"))
        assertEquals(65.0 to 85.0, ReportImportParser.parseRange("65~85"))
        assertEquals(65.0 to 85.0, ReportImportParser.parseRange("65～85"))
        assertEquals(65.0 to 85.0, ReportImportParser.parseRange("65—85"))
        assertEquals(null to 5.0, ReportImportParser.parseRange("≤5"))
        assertEquals(null to 5.0, ReportImportParser.parseRange("<5"))
        assertEquals(null to 40.0, ReportImportParser.parseRange("＜40"))
        assertEquals(3.5 to null, ReportImportParser.parseRange("≥3.5"))
        assertEquals(null to null, ReportImportParser.parseRange("阴性"))
        assertEquals(null to null, ReportImportParser.parseRange(null))
    }

    // ---- 影像报告 ----

    @Test
    fun parseImaging_mriSample() {
        val text = """
            【影像报告整理】
            类型: MRI
            日期: 2026-07-31
            医院: 南方医院
            部位: 骶髂关节
            所见: 骶髂关节面骨质信号不均，双侧骶髂关节面下见斑片状炎性水肿信号，
            关节间隙未见明显狭窄。
            结论: 双侧骶髂关节炎性改变（符合axSpA表现）。
            对比: 较2025-07-30前片，炎症范围略有扩大。
        """.trimIndent()
        val imp = ReportImportParser.parseImaging(text)!!
        assertEquals("MRI", imp.modality)
        assertEquals("2026-07-31", imp.date)
        assertEquals("南方医院", imp.hospital)
        assertEquals("骶髂关节", imp.bodyPart)
        assertTrue(imp.findings!!.contains("炎性水肿信号"))
        assertTrue(imp.findings!!.contains("关节间隙未见明显狭窄"))
        assertTrue(imp.conclusion!!.contains("axSpA"))
        // norm() 会剥掉行尾句号
        assertEquals("较2025-07-30前片，炎症范围略有扩大", imp.compare)
    }

    @Test
    fun parseImaging_ctXray() {
        val ct = "类型: CT\n日期: 2026-08-05\n部位: 髋关节\n所见: 双侧髋关节未见明显异常。\n结论: 未见明显异常。"
        val impCt = ReportImportParser.parseImaging(ct)!!
        assertEquals("CT", impCt.modality)
        assertNull(impCt.compare)

        val xray = "类型: X线\n日期: 2026/1/15\n医院: 宝安中医院\n部位: 腰椎正侧位\n结论: 腰椎生理曲度存在。"
        val impX = ReportImportParser.parseImaging(xray)!!
        assertEquals("XRAY", impX.modality)
        assertEquals("2026-01-15", impX.date)
    }

    @Test
    fun parseImaging_missingModalityReturnsNull() {
        assertNull(ReportImportParser.parseImaging("日期: 2026-07-31\n部位: 骶髂关节\n结论: 无类型行"))
        assertNull(ReportImportParser.parseImaging(""))
    }

    @Test
    fun parseImaging_compareNoneTreatedAsBlank() {
        val text = "类型: MRI\n日期: 2026-07-31\n部位: 骶髂关节\n所见: 所见文本。\n结论: 结论文本。\n对比: 无"
        val imp = ReportImportParser.parseImaging(text)!!
        assertEquals("无", imp.compare) // 原样保留，入库时由仓库过滤「无」
    }

    // ---- 模态归一 ----

    @Test
    fun modalityOf_variants() {
        assertEquals("MRI", ReportImportParser.modalityOf("MRI（磁共振）"))
        assertEquals("MRI", ReportImportParser.modalityOf("磁共振"))
        assertEquals("MRI", ReportImportParser.modalityOf("MR"))
        assertEquals("CT", ReportImportParser.modalityOf("CT"))
        assertEquals("XRAY", ReportImportParser.modalityOf("X线"))
        assertEquals("XRAY", ReportImportParser.modalityOf("X光"))
        assertEquals("XRAY", ReportImportParser.modalityOf("DR"))
        assertEquals("XRAY", ReportImportParser.modalityOf("放射"))
        assertNull(ReportImportParser.modalityOf("B超"))
        assertNull(ReportImportParser.modalityOf(""))
    }
}
