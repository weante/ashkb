package com.ashkb.app.domain

/**
 * v1.0.81（批次 7）：删除**整个补剂条目**时的级联范围与确认文案变体（纯函数、无 Android 依赖，可纯单测）。
 *
 * **为什么删补剂档案必须连它的服用记录一起删**：`supplement_logs` 靠 `sup_id` 归属到补剂档案
 * （补剂打卡的 `slot_key` 恒为 NULL，见 `SupplementLogDao.upsert` 的调用点），而列表行上的删除是**真删**。
 * 只删档案行、把记录留下，它们就成了 `sup_id` 指向不存在补剂的**孤儿行**：仍会进入依从率统计的分子分母，
 * 但用户既看不到、也删不掉（补剂详情弹层只能从现存档案打开）——这正是批次 6 在复诊记录上消灭的那类幽灵。
 *
 * **为什么确认框必须报出条数**：与 [MedDeletion] 同款口径（用户 2026-09-27 对药品删除的要求）——
 * 不可逆操作不把「连带删掉多少条」讲清楚，就是在让用户在不知情下删掉自己的记录。
 * 0 条时不显示「共 0 条」这种噪声，故分两个文案变体。
 */
object SupplementDeletion {

    /** 确认框正文变体：CASCADE = 名下还有服用记录（必须报条数）；PLAIN = 还没有任何记录。 */
    enum class Variant { CASCADE, PLAIN }

    fun variant(logCount: Int): Variant =
        if (normalizeCount(logCount) > 0) Variant.CASCADE else Variant.PLAIN

    /** 计数兜底：负数（不应出现）按 0 处理，不要在 UI 上显示「-1 条记录」。 */
    fun normalizeCount(logCount: Int): Int = logCount.coerceAtLeast(0)
}
