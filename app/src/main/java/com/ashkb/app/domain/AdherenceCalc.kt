package com.ashkb.app.domain

/**
 * 服药 / 补剂依从口径——**唯一实现**。
 *
 * 抽取原因：「依从率 =（完成 + 部分×0.5）÷ 已打卡数」这一句此前在
 * [com.ashkb.app.data.repo.ReportRepository] 内联了 **4 遍**（概览的用药与补剂、周月报的用药与补剂），
 * v1.0.48 药单「用药记录」又需要第 5 遍——同一指标写在多处必然漂移（改了一处忘了另一处，
 * 两个页面给出不同的百分比），故收拢到这里。
 *
 * 命名带 `Calc` 后缀是**必要的**：`ReportRepository` 内部已有一个嵌套 `data class Adherence`
 * （汇总 DTO），同名的顶层对象会被它遮蔽，导致 `Adherence.ratePct` 在仓库里解析失败。
 * 后缀风格与既有 `ScheduleCalc` 一致。
 *
 * 口径（与报表一致）：**只在「已打卡」的槽位上计算**——未打卡的槽位既不计完成、也不计未依从，
 * 即不惩罚漏记；部分完成按 0.5 计。
 */
object AdherenceCalc {

    /** 状态词（存库值，与实体注释 `done / partial / skipped` 一致） */
    const val DONE = "done"
    const val PARTIAL = "partial"
    const val SKIPPED = "skipped"

    /** 区间内已打卡汇总 */
    data class Summary(
        val done: Int,
        val partial: Int,
        val skipped: Int,
        val total: Int,
        val ratePct: Int,
    )

    /**
     * 依从率（0–100 取整）。部分完成按 0.5 计。
     *
     * 无打卡时返回 **0 而非 100**：「没有记录」不能被显示成「完全依从」。
     */
    fun ratePct(done: Int, partial: Int, total: Int): Int =
        if (total <= 0) 0 else ((done + partial * 0.5) / total * 100).toInt()

    /**
     * 按状态词表汇总（顺序无关）。
     *
     * 无法识别的状态**计入 total 但不计完成**——取值偏向保守（未知既不算依从，
     * 也不会让比率虚高）。当前写入侧只产生三态，此项为防御性处理。
     */
    fun summarize(statuses: List<String>): Summary {
        var done = 0
        var partial = 0
        var skipped = 0
        var unknown = 0
        for (s in statuses) {
            when (s) {
                DONE -> done++
                PARTIAL -> partial++
                SKIPPED -> skipped++
                else -> unknown++
            }
        }
        val total = done + partial + skipped + unknown
        return Summary(done, partial, skipped, total, ratePct(done, partial, total))
    }
}
