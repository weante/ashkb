package com.ashkb.app.domain

import java.security.MessageDigest

/**
 * v1.2.11（缺陷工单 B01）：**AI 报告导入的稳定指纹**。
 *
 * 问题：把同一张化验单粘两次、或把上个月导入过的报告再导一次，旧代码每次都
 * `Ids.new("lab")` 生成新 id、没有任何批次身份，于是化验单整份翻倍——「化验 171 项」
 * 这类计数与趋势图全被污染，而用户看不出是哪一次重复。
 *
 * 指纹的定义是「报告类型 + **规范化后的完整导入内容**」：
 *  · 同内容必同指纹（字段顺序固定、分隔符固定、不做 locale 相关的格式化）；
 *  · 改一个数值就是另一份报告（这正是我们想要的：用户修正后重新导入应当生效）。
 *
 * **刻意不做**「按日期 + 指标 + 数值」的全局去重：真实报告里同一天测两次同一指标
 * 完全正常（早晚各一次血压、复查前后各一次 CRP），全局去重会把它们当成重复删掉。
 * 批次身份来自「一次性粘贴的一整份报告」，不是单行。
 *
 * 哈希用 SHA-256 而不是 `String.hashCode()`：后者只有 32 位，几万条记录后碰撞概率
 * 足以造成「明明是新报告却被判重复」——那是静默丢数据的错误，代价远高于多算一次摘要。
 *
 * 纯 JVM（只依赖 `java.security`），不碰 Android，故可在普通单测里直接验证。
 */
internal object ImportFingerprint {

    /** 报告类型：同一份文本按化验/影像两种模板解析出完全不同的结果，指纹不能混用。 */
    const val KIND_LAB = "lab"
    const val KIND_IMAGING = "imaging"

    /** 字段分隔（`\u001F` 单元分隔符）与行分隔（`\u001E` 记录分隔符）——不会出现在报告文本里。 */
    private const val FIELD = "\u001F"
    private const val ROW = "\u001E"

    /** 化验报告指纹：日期 + 医院 + 备注 + 逐行的完整字段。 */
    fun lab(import: LabImport): String {
        val head = listOf(KIND_LAB, n(import.date), n(import.hospital), n(import.note))
        val rows = import.rows.map { row ->
            listOf(
                n(row.testName),
                // `valueText` 是报告上的原始写法（"5"、"5.0"、"阴性"），比 `value` 这个派生的 Double 稳定；
                // 两者都拼进去，于是「文本相同但数值解析不同」也会被当成不同内容（宁可重导，不可误并）。
                n(row.valueText),
                row.value?.toString().orEmpty(),
                n(row.unit),
                row.refLow?.toString().orEmpty(),
                row.refHigh?.toString().orEmpty(),
                row.refLowInclusive.toString(),
                row.refHighInclusive.toString(),
                n(row.abnormal),
                n(row.aiAbnormal),
            ).joinToString(FIELD)
        }
        return digest((head + rows).joinToString(ROW))
    }

    /** 影像报告指纹：日期 + 检查类型/部位 + 医院 + 所见 + 结论 + 对比。 */
    fun imaging(import: ImagingImport): String {
        val parts = listOf(
            KIND_IMAGING,
            n(import.date),
            n(import.modality),
            n(import.bodyPart),
            n(import.hospital),
            n(import.findings),
            n(import.conclusion),
            n(import.compare),
        )
        return digest(parts.joinToString(ROW))
    }

    /** 归一化空值：`null` 与 `""` 视为同一件事（解析器对两者不区分，指纹也不该区分）。 */
    private fun n(value: String?): String = value?.trim().orEmpty()

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
