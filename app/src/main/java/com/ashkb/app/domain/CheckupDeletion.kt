package com.ashkb.app.domain

/**
 * v1.0.80（批次 6）：删除复诊记录时的**级联范围**与确认文案变体（纯函数、无 Android 依赖，可纯单测）。
 *
 * **为什么删复诊记录必须级联**：化验结果 / 影像记录 / 检查附件都靠 `checkup_id` 归属到某一次就诊
 * （v11 起由用户手动归属）。只删记录本身，它们就变成 `checkup_id` 指向不存在记录的**孤儿行**——
 * 界面上它们仍在「化验」「影像」「附件」三个列表里，但用户再也看不出「它们属于哪次就诊」，
 * 也就永远清不掉（想删还得逐条去别的 Tab 里找）。这正是「记录没了、派生数据还在」的典型幽灵。
 *
 * **为什么确认框必须逐类报数**：这是不可逆操作，而用户在记录卡上看不到「这一次就诊名下挂了多少东西」。
 * 不把条数讲清楚就让他点「删除」，等于让他在不知情下删掉一份化验单——
 * 与 [MedDeletion] 同款口径（用户 2026-09-27 对药品删除的要求）。
 */
object CheckupDeletion {

    /**
     * 级联范围内的条数（三类各自独立，0 = 该类无内容）。
     *
     * 用数据类而不是 `Triple`：三个 `Int` 的元组在调用点极易写反，而写反没有编译错误——
     * 只会让确认框把「12 条化验」说成「12 个附件」。
     */
    data class Counts(
        val labs: Int = 0,
        val imaging: Int = 0,
        val attachments: Int = 0,
    ) {
        /** 是否真的会连带删掉别的东西——无派生时不该吓唬用户（见 [variant]）。 */
        val hasDerived: Boolean
            get() = normalize(labs) + normalize(imaging) + normalize(attachments) > 0
    }

    /** 确认框正文变体：CASCADE = 有派生数据（必须逐类报数）；PLAIN = 仅删记录本身。 */
    enum class Variant { CASCADE, PLAIN }

    fun variant(counts: Counts): Variant = if (counts.hasDerived) Variant.CASCADE else Variant.PLAIN

    /** 计数兜底：负数（不应出现）按 0 处理，不要在 UI 上显示「-1 条化验结果」。 */
    fun normalize(count: Int): Int = count.coerceAtLeast(0)
}
