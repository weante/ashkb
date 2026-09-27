package com.ashkb.app.domain

/**
 * v1.0.71：删除已停用药品的判定与文案变体（纯函数、无 Android 依赖，可纯单测）。
 *
 * **为什么只允许删「已停用」的药**：在用药品必须先走「停用」这一步（留下停药原因与生效日），
 * 一条 DELETE 抹掉在服医嘱是不可接受的；入口也只出现在「已停用药品」折叠区里。
 *
 * **为什么确认框必须报出条数**：删除会**连带**删掉该药名下的打卡记录（用户 2026-09-27 拍板），
 * 历史依从率因此会变。不可逆操作若不把「删掉多少」说清楚，就是在骗用户。
 */
object MedDeletion {

    /** 只有已停用（归档）的药品可删。 */
    fun canDelete(isArchived: Boolean): Boolean = isArchived

    /** 确认框正文变体。 */
    enum class Variant { WITH_LOGS, NO_LOGS }

    /** 有可删的打卡记录 → 报条数；没有也仍要提示不可恢复。 */
    fun variant(logCount: Int): Variant =
        if (normalizeCount(logCount) > 0) Variant.WITH_LOGS else Variant.NO_LOGS

    /** 计数兜底：负数（不应出现）按 0 处理，不要在 UI 上显示「-1 条记录」。 */
    fun normalizeCount(logCount: Int): Int = logCount.coerceAtLeast(0)
}
