package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * M6 检查数据「AI 导入」：
 * 用户把【模板】发给任意 AI 助手并附报告照片，AI 按模板格式输出，
 * 粘贴回 App 由本解析器转成结构化数据。纯函数、无 Android 依赖。
 *
 * v1.2.6（i18n）：[ImportTemplates] 改为返回资源 id（患者可见文案）；
 * 解析键保留中文原值，并**增量**接受英文键——英文模板回填的报告同样能解析。
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
    /**
     * v1.0.78（批次 4 收尾）：**本地判读结果或兜底值**（high / low / normal）。
     * 解析阶段它取 AI 给的标记，入库时由 `HealthRepository.saveLabResult` 按参考范围**本地判读覆盖**；
     * 只有没有参考范围、本地判不了时才沿用这个 AI 值兜底（本地优先 + AI 兜底）。
     */
    val abnormal: String? = null,
    /**
     * v1.0.78（批次 4 收尾）：AI 的**原始**标记，原样留档、**不参与判定**，与 [abnormal] 并列展示。
     * 与 [abnormal] 分开存的原因见 `LabResult.aiAbnormal` 的注释。
     */
    val aiAbnormal: String? = null,
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
                l.startsWith("日期") || l.startsWith("Date") -> date = afterColon(l)
                l.startsWith("医院") || l.startsWith("Hospital") -> hospital = afterColon(l)
                l.startsWith("备注") || l.startsWith("Note") -> note = afterColon(l)
                else -> {
                    val row = parseLabRow(l)
                    if (row != null) rows.add(row) else skipped.add(line)
                }
            }
        }
        if (date == null && rows.isEmpty()) return null
        return LabImport(normalizeDate(date), hospital?.takeIf { it.isNotBlank() }, note?.takeIf { it.isNotBlank() }, rows, skipped)
    }

    /** 表头/说明行的整词（中英文键都算，AI 可能复读模板说明）。 */
    private val HEADER_WORDS = listOf("项目", "Item")

    /** 表头/说明行/项目符号的前缀。 */
    private val HEADER_PREFIXES = listOf("#", "例", "-", "—", "·", "说明", "备注", "Note")

    /** 该行是否明显不是数据行（表头 / 说明 / 项目符号）。 */
    private fun isHeaderLikeName(name: String): Boolean =
        name in HEADER_WORDS || HEADER_PREFIXES.any { name.startsWith(it) }

    private fun parseLabRow(line: String): LabImportRow? {
        val parts = FIELD_SPLIT.split(norm(line)).map { it.trim() }
        if (parts.size < 2) return null
        val name = parts[0]
        // 指标名与结果都为空、或明显是表头/说明/项目符号的行跳过（AI 可能复读模板说明）
        if (name.isBlank() || isHeaderLikeName(name)) return null
        val valueText = parts[1]
        if (valueText.isBlank() || valueText == "结果" || valueText == "Result") return null
        val unit = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        val ref = parts.getOrNull(3)?.takeIf { it.isNotBlank() && it != "参考范围" && it != "Reference range" }
        val mark = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
        val (refLow, refHigh) = parseRange(ref)
        // v1.0.78（批次 4 收尾）：AI 标记**同时写两处**——
        // `aiAbnormal` 原样留档（与本地判读并列展示用），`abnormal` 作为「本地判读结果或兜底值」的初值
        // （有参考范围时会被仓库的本地判读覆盖，没有参考范围时它就是最终入库的兜底值）。
        val aiMark = markAbnormal(mark)
        return LabImportRow(
            testName = name,
            value = valueText.toDoubleOrNull(),
            valueText = valueText,
            unit = unit,
            refLow = refLow,
            refHigh = refHigh,
            abnormal = aiMark,
            aiAbnormal = aiMark,
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
        if (mark.isNullOrBlank()) return null
        // v1.2.6（i18n）：英文模板回填的标记（Normal / High / Low）与中文标记等价
        if (mark == "正常" || mark.equals("Normal", true)) return "normal"
        return when {
            mark.contains("高") || mark.contains("↑") || mark.equals("H", true) || mark.contains("High", true) -> "high"
            mark.contains("低") || mark.contains("↓") || mark.equals("L", true) || mark.contains("Low", true) -> "low"
            else -> null
        }
    }

    /**
     * AI 常输出 2026/7/31 或 2026.07.31，统一成 YYYY-MM-DD。
     *
     * v1.0.77（批次 4）：改为走 [DateInput]——原实现正则取到年月日后直接 `%02d` 拼接，
     * **`2026-13-45` / `2026-02-31` 会被原样写库**（第三份审查报告 §六）。
     * 校验不过返回 null，由调用方决定如何提示（不静默接受）。
     */
    private fun normalizeDate(raw: String?): String? = DateInput.normalizeOrNull(raw)

    /** 中文键 → 英文别名（v1.2.6：英文模板回填的报告同样能解析；canonical 仍取中文键） */
    private val IMAGING_KEYS = listOf(
        "类型" to listOf("Type"),
        "日期" to listOf("Date"),
        "医院" to listOf("Hospital"),
        "部位" to listOf("Site"),
        "所见" to listOf("Findings"),
        "结论" to listOf("Conclusion"),
        "对比" to listOf("Comparison"),
        "备注" to listOf("Note"),
    )

    /** 命中的字段键（返回 canonical 中文键）；中英文键都要求紧跟半角/全角冒号。 */
    private fun imagingKeyOf(l: String): String? = IMAGING_KEYS.firstNotNullOfOrNull { (canonical, aliases) ->
        (listOf(canonical) + aliases).firstOrNull { name ->
            l.startsWith(name) && (l.getOrNull(name.length) == ':' || l.getOrNull(name.length) == '：')
        }?.let { canonical }
    }

    /**
     * R4：首个键值行之前无法归入任何字段的行不再静默丢弃——原文进 [ImagingImport.skippedLines]。
     *
     * v1.2.6（i18n）：[unsetBodyPart] 由调用方传入（资源 `ui_imaging_bodypart_unset`），
     * [bodyPartSeparator] 由调用方传入（资源 `ui_list_separator`，中文「、」/英文「, 」），
     * domain 层不再硬编码中文。
     */
    fun parseImaging(text: String, unsetBodyPart: String, bodyPartSeparator: String): ImagingImport? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val fields = linkedMapOf<String, MutableList<String>>()
        val skipped = mutableListOf<String>()
        var current: String? = null
        for (line in lines) {
            val l = norm(line)
            val key = imagingKeyOf(l)
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
        val bodyPart = fields["部位"]?.joinToString(bodyPartSeparator)?.takeIf { it.isNotBlank() }
            ?: unsetBodyPart
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

    /** MRI/磁共振/MR → MRI；CT → CT；X线/X光/X光片/DR/放射/X-ray → XRAY */
    fun modalityOf(raw: String): String? {
        val s = raw.trim().uppercase()
        return when {
            s.contains("MRI") || s.contains("MR") || raw.contains("磁共振") || raw.contains("核磁") -> "MRI"
            s.contains("CT") -> "CT"
            s.contains("X-RAY") || raw.contains("X线") || raw.contains("X光") ||
                s.contains("XR") || raw.contains("DR") || raw.contains("放射") || raw.contains("平片") -> "XRAY"
            else -> null
        }
    }

    private fun afterColon(line: String): String {
        val idx = line.indexOfFirst { it == ':' || it == '：' }
        return if (idx >= 0) line.substring(idx + 1).trim() else ""
    }
}

/**
 * 发给 AI 的模板（复制到剪贴板，连同报告照片一起发给任意 AI 助手）。
 *
 * v1.2.6（i18n）：改为返回**资源 id**（`ui_import_template_lab` / `_imaging`），
 * 模板正文按当前语言渲染；[ReportImportParser] 同时接受中英文键，故英文模板回填也能解析。
 */
object ImportTemplates {

    @StringRes
    val LAB = R.string.ui_import_template_lab

    @StringRes
    val IMAGING = R.string.ui_import_template_imaging
}
