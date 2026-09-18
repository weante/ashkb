package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedClass
import com.ashkb.app.data.entity.MedFrequency
import com.ashkb.app.data.entity.Medication

/**
 * 紧急卡「当前用药」汇总（v1.0.26）。
 *
 * 背景：v3 M7 要求紧急信息卡含「过敏史 / 血型 / **用药** / 诊断」，但此前紧急卡页面与打印版
 * 都不显示任何用药信息——`Profile.emergency_med_summary` 字段虽有定义却无任何读写。
 * 急救场景最需要知道的是「患者是否正在使用免疫抑制剂 / 生物制剂」（感染风险、伤口愈合、
 * 不可骤停判断），因此这里自动从药单汇总，不依赖用户额外维护。
 *
 * 口径（用户拍板：自动汇总，不手工填写）：
 *  - 只收在用药物（未归档 + 未设结束日期或结束日期未过）
 *  - 免疫抑制相关类别置顶并标注
 *  - 总条数封顶，保住紧急卡「打印一页可读」的设计前提
 *
 * 纯函数、无 Android 依赖，可单测（与 `ExerciseEngine` / `KbSearch` 同一层）。
 */
object EmergencyMeds {

    /** 紧急卡最多列出的药条数——超出只报数量，避免打印超页。 */
    const val MAX_LINES = 12

    /**
     * 免疫抑制相关类别。含糖皮质激素：急救场景关注的是感染风险、伤口愈合与
     * 「不可骤停」判断，四类均适用（NSAIDs / 其他不标注，避免稀释信号）。
     */
    private val IMMUNOSUPPRESSANT = setOf(
        MedClass.BIOLOGIC, MedClass.JAK, MedClass.CSDMARD, MedClass.GLUCOCORTICOID,
    )

    fun isImmunosuppressant(medClass: String): Boolean = MedClass.fromKey(medClass) in IMMUNOSUPPRESSANT

    /** 在用药物：未归档，且未设结束日期或结束日期不早于今天（结束日当天仍算在用）。 */
    fun isActive(med: Medication, today: String): Boolean =
        !med.isArchived && (med.endDate.isNullOrBlank() || med.endDate >= today)

    /** 一行用药摘要：name 含商品名，detail = 剂量 · 频次（注射药附注射周期）。 */
    data class Entry(val name: String, val detail: String, val immunosuppressant: Boolean)

    /**
     * 汇总结果。`immunosuppressants` 与 `others` 分开，便于 UI 用不同语气呈现、
     * PDF 分节打印；`hiddenCount` 为因封顶未列出的条数。
     */
    data class Summary(
        val immunosuppressants: List<Entry>,
        val others: List<Entry>,
        val hiddenCount: Int,
    ) {
        val isEmpty: Boolean get() = immunosuppressants.isEmpty() && others.isEmpty()

        /** 免疫抑制在前，UI / PDF 需要顺序遍历时用这个。 */
        val ordered: List<Entry> get() = immunosuppressants + others

        /** 是否含免疫抑制类用药——决定要不要显示感染风险提示。 */
        val hasImmunosuppressant: Boolean get() = immunosuppressants.isNotEmpty()
    }

    /**
     * 汇总：免疫抑制类置顶，其余保持传入顺序（DAO 已按 created_at 排序，结果稳定可复现）。
     * 截断按「置顶优先」——先保证免疫抑制类完整列出，再补其余。
     */
    fun summarize(meds: List<Medication>, today: String, maxLines: Int = MAX_LINES): Summary {
        val active = meds.filter { isActive(it, today) }
        val (immuno, rest) = active.partition { isImmunosuppressant(it.medClass) }
        val head = immuno.map { entry(it) }
        val tail = rest.map { entry(it) }
        val kept = (head + tail).take(maxLines.coerceAtLeast(0))
        val keptHead = kept.count { it.immunosuppressant }
        return Summary(
            immunosuppressants = kept.take(keptHead),
            others = kept.drop(keptHead),
            hiddenCount = head.size + tail.size - kept.size,
        )
    }

    private fun entry(m: Medication): Entry {
        val name = m.brandName?.takeIf { it.isNotBlank() }?.let { "${m.name}（$it）" } ?: m.name
        val freq = MedFrequency.fromKey(m.frequency).label
        val cycle = m.injCycleDays?.let { " · 每 $it 天" } ?: ""
        return Entry(
            name = name,
            detail = "${m.dose} · $freq$cycle",
            immunosuppressant = isImmunosuppressant(m.medClass),
        )
    }
}
