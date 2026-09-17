package com.ashkb.app.domain

/**
 * M6 检查数据「AI 导入」：
 * 用户把【模板】发给任意 AI 助手并附报告照片，AI 按模板格式输出，
 * 粘贴回 App 由本解析器转成结构化数据。纯函数、无 Android 依赖。
 *
 * 报告格式参照用户真实病历（南方医院放射/磁共振报告单、深圳宝安中医院生化检验单）。
 */

data class LabImportRow(
    val testName: String,
    val value: Double? = null,
    val valueText: String,
    val unit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val abnormal: String? = null, // high / low / null=交由仓库按参考范围判读
)

data class LabImport(
    val date: String?, // YYYY-MM-DD
    val hospital: String?,
    val note: String?,
    val rows: List<LabImportRow>,
    /** R4：未能解析成数据行的原文（模板复读 / 表头 / 格式漂移 / 空结果行），供确认页提示手补 */
    val skippedLines: List<String> = emptyList(),
)

data class ImagingImport(
    val date: String?, // YYYY-MM-DD
    val modality: String, // MRI / CT / XRAY
    val bodyPart: String,
    val hospital: String? = null,
    val findings: String? = null,
    val conclusion: String? = null,
    val compare: String? = null, // 对比前片的变化描述
    /** R4：首个键值行之前、无法归入任何字段的原文行（键行之后视为多行字段值，不计入） */
    val skippedLines: List<String> = emptyList(),
)

object ReportImportParser {

    /** 中文冒号/多余空格归一；逗号（含全角）与分号都算字段分隔 */
    private fun norm(s: String) = s.trim().trimStart('【', '[').trimEnd('】', ']', '。', '.')

    private val FIELD_SPLIT = Regex("[,，;；\\t]")

    /** R4：解析失败的行不再静默丢弃——原文进 [LabImport.skippedLines]，确认页提示「M 行未识别」。 */
    fun parseLab(text: String): LabImport? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        var date: String? = null
        var hospital: String? = null
        var note: String? = null
        val rows = mutableListOf<LabImportRow>()
        val skipped = mutableListOf<String>()
        for (line in lines) {
            val l = norm(line)
            when {
                l.startsWith("日期") -> date = afterColon(l)
                l.startsWith("医院") -> hospital = afterColon(l)
                l.startsWith("备注") -> note = afterColon(l)
                else -> {
                    val row = parseLabRow(l)
                    if (row != null) rows.add(row) else skipped.add(line)
                }
            }
        }
        if (date == null && rows.isEmpty()) return null
        return LabImport(normalizeDate(date), hospital?.takeIf { it.isNotBlank() }, note?.takeIf { it.isNotBlank() }, rows, skipped)
    }

    private fun parseLabRow(line: String): LabImportRow? {
        val parts = FIELD_SPLIT.split(norm(line)).map { it.trim() }
        if (parts.size < 2) return null
        val name = parts[0]
        // 指标名与结果都为空、或明显是表头/说明/项目符号的行跳过（AI 可能复读模板说明）
        if (name.isBlank() || name == "项目" || name.startsWith("#") || name.startsWith("例") ||
            name.startsWith("-") || name.startsWith("—") || name.startsWith("·") ||
            name.startsWith("说明") || name.startsWith("备注")
        ) return null
        val valueText = parts[1]
        if (valueText.isBlank() || valueText == "结果") return null
        val unit = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        val ref = parts.getOrNull(3)?.takeIf { it.isNotBlank() && it != "参考范围" }
        val mark = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
        val (refLow, refHigh) = parseRange(ref)
        return LabImportRow(
            testName = name,
            value = valueText.toDoubleOrNull(),
            valueText = valueText,
            unit = unit,
            refLow = refLow,
            refHigh = refHigh,
            abnormal = markAbnormal(mark),
        )
    }

    /** "2.9-8.2" / "9–50" / "65~85" / "≤5" / "＜40" → 上下界（容忍全/半角破折号） */
    fun parseRange(ref: String?): Pair<Double?, Double?> {
        if (ref.isNullOrBlank()) return null to null
        val s = ref.trim().replace("–", "-").replace("—", "-").replace("－", "-")
            .replace("~", "-").replace("～", "-")
        val m = Regex("^([0-9.]+)\\s*-\\s*([0-9.]+)$").find(s)
        if (m != null) return m.groupValues[1].toDoubleOrNull() to m.groupValues[2].toDoubleOrNull()
        val upper = Regex("^[≤<＜]\\s*([0-9.]+)$").find(s)
        if (upper != null) return null to upper.groupValues[1].toDoubleOrNull()
        val lower = Regex("^[≥>＞]\\s*([0-9.]+)$").find(s)
        if (lower != null) return lower.groupValues[1].toDoubleOrNull() to null
        return null to null
    }

    private fun markAbnormal(mark: String?): String? {
        if (mark.isNullOrBlank() || mark == "正常") return if (mark == "正常") "normal" else null
        return when {
            mark.contains("高") || mark.contains("↑") || mark.equals("H", true) -> "high"
            mark.contains("低") || mark.contains("↓") || mark.equals("L", true) -> "low"
            else -> null
        }
    }

    /** AI 常输出 2026/7/31 或 2026.07.31，统一成 YYYY-MM-DD */
    private fun normalizeDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().replace(".", "-").replace("/", "-").replace("年", "-").replace("月", "-").replace("日", "")
        val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(s) ?: return null
        val (y, mo, d) = m.destructured
        return "%s-%02d-%02d".format(y, mo.toInt(), d.toInt())
    }

    private val IMAGING_KEYS = listOf("类型", "日期", "医院", "部位", "所见", "结论", "对比", "备注")

    /** R4：首个键值行之前无法归入任何字段的行不再静默丢弃——原文进 [ImagingImport.skippedLines]。 */
    fun parseImaging(text: String): ImagingImport? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val fields = linkedMapOf<String, MutableList<String>>()
        val skipped = mutableListOf<String>()
        var current: String? = null
        for (line in lines) {
            val l = norm(line)
            val key = IMAGING_KEYS.firstOrNull { k -> l.startsWith(k) && (l.getOrNull(k.length) == ':' || l.getOrNull(k.length) == '：') }
            if (key != null) {
                current = key
                val v = afterColon(l)
                if (v.isNotBlank()) fields.getOrPut(key) { mutableListOf() }.add(v)
            } else if (current != null) {
                fields.getOrPut(current) { mutableListOf() }.add(l)
            } else {
                skipped.add(line)
            }
        }
        val modality = modalityOf(fields["类型"]?.joinToString(" ") ?: "") ?: return null
        val date = normalizeDate(fields["日期"]?.joinToString(" "))
        val bodyPart = fields["部位"]?.joinToString("、")?.takeIf { it.isNotBlank() } ?: "未注明部位"
        if (date == null && fields.isEmpty()) return null
        return ImagingImport(
            date = date,
            modality = modality,
            bodyPart = bodyPart,
            hospital = fields["医院"]?.joinToString(" ")?.takeIf { it.isNotBlank() },
            findings = fields["所见"]?.joinToString("\n")?.takeIf { it.isNotBlank() },
            conclusion = fields["结论"]?.joinToString("\n")?.takeIf { it.isNotBlank() },
            compare = fields["对比"]?.joinToString("\n")?.takeIf { it.isNotBlank() },
            skippedLines = skipped,
        )
    }

    /** MRI/磁共振/MR → MRI；CT → CT；X线/X光/X光片/DR/放射 → XRAY */
    fun modalityOf(raw: String): String? {
        val s = raw.trim().uppercase()
        return when {
            s.contains("MRI") || s.contains("MR") || raw.contains("磁共振") || raw.contains("核磁") -> "MRI"
            s.contains("CT") -> "CT"
            raw.contains("X线") || raw.contains("X光") || raw.contains("X线") || s.contains("XR") || raw.contains("DR") || raw.contains("放射") || raw.contains("平片") -> "XRAY"
            else -> null
        }
    }

    private fun afterColon(line: String): String {
        val idx = line.indexOfFirst { it == ':' || it == '：' }
        return if (idx >= 0) line.substring(idx + 1).trim() else ""
    }
}

/** 发给 AI 的模板（复制到剪贴板，连同报告照片一起发给任意 AI 助手） */
object ImportTemplates {

    const val LAB = """【化验单整理】
请根据我发送的检验报告照片，逐项提取数据并严格按以下格式输出（每项一行，报告里有多少项就输出多少项，没有的不要编造，看不清的用?代替）：

日期: 2026-08-02
医院: 医院名称
项目, 结果, 单位, 参考范围, 标记
血沉(ESR), 15, mm/h, 0-20, 正常
C-反应蛋白(CRP), 5.2, mg/L, 0-8, 偏高
白细胞计数(WBC), 6.1, 10^9/L, 3.5-9.5, 正常

说明：
- 「项目」用报告上的中文名，可带英文缩写
- 「结果」只填数字（或 阴性/阳性/↑/↓ 等原文），不要带单位
- 「参考范围」照抄报告原文（如 2.9-8.2 或 ≤5）
- 「标记」按报告原标注：偏高/偏高↑、偏低、正常"""

    const val IMAGING = """【影像报告整理】
请根据我发送的检查报告照片（MRI/CT/X线），提取信息并严格按以下格式输出：

类型: MRI（按报告实际类型填 MRI / CT / X线）
日期: 2026-07-31
医院: 医院名称
部位: 骶髂关节
所见: 图像所见/检查所见的完整原文（可多行）
结论: 诊断与印象的完整原文（可多行）
对比: 报告中「对比前片」的变化描述（没有则填 无）"""
}
