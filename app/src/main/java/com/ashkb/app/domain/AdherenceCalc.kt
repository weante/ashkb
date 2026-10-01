package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedicationLog

/**
 * 服药 / 补剂「记录内完成度」口径——**唯一实现**。
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
 * v1.0.76（批次 3a）——**改名 + 无数据表达 + PRN 移出**，三件事都是同一个原因：
 * 这个比率的分母是**已记录的打卡条数**，不是计划剂量数，所以「完全没打卡（漏记）」不会让它下降，
 * 它既不等于「按时服药的比例」，也不能当依从率读。旧实现叫它「服药依从」、界面只显示一个百分比，
 * 零分母还给出 0% + 90/70 三档判定，等于把「一条记录都没有」说成「需干预」。现在：
 * ① 面向用户的名字是「记录内完成度」，且界面必须把分母（共 N 条记录）显示出来；
 * ② 零分母用 [Completion.ratePct] 的 `null` 表达，[ClinicalThresholds.completionLabel] 同步不给判定；
 * ③ **按需（PRN）记录整体移出本指标**——它没有计划剂量、也没有「漏服」，由 [prnCount] 单独报数。
 *
 * 口径（与报表一致）：**只在「已打卡」的槽位上计算**——未打卡的槽位既不计完成、也不计未依从，
 * 即不惩罚漏记；部分完成按 0.5 计（与收拢前的内联公式逐位一致，本次不改医学口径）。
 */
object AdherenceCalc {

    /** 状态词（存库值，与实体注释 `done / partial / skipped` 一致） */
    const val DONE = "done"
    const val PARTIAL = "partial"
    const val SKIPPED = "skipped"

    /**
     * 区间内**已记录**打卡的汇总。
     *
     * `total` = 记录条数 = 百分比的分母（**不是**区间内的计划剂量数）——这是本指标的全部含义，
     * 也是它不能叫「依从率」的原因：漏记的剂量根本不进分母。
     */
    data class Completion(
        val done: Int,
        val partial: Int,
        val skipped: Int,
        val total: Int,
    ) {
        /** 有记录才算得出完成度；无记录时界面必须显示「—（暂无记录）」。 */
        val hasRecords: Boolean get() = total > 0

        /**
         * 完成度百分比（0–100 取整，部分完成按 0.5 计）。
         *
         * **无记录时为 `null`，不是 0 也不是 100**：0% 会被读成「一条都没完成」，
         * 100% 会被读成「全都完成」，两者都是从「没有数据」里编出来的结论。
         * 需要「无判定」的调用方请看 [ClinicalThresholds.completionLabel]。
         */
        val ratePct: Int?
            get() = if (hasRecords) AdherenceCalc.ratePct(done, partial, total) else null
    }

    /**
     * 百分比（0–100 取整）。部分完成按 0.5 计。
     *
     * 留给**没有 PRN 语义**的调用方（补剂）与纯数值计算：无记录时仍返回 0（旧行为，报表的
     * 补剂卡片自己在 `total == 0` 时走「暂无」分支）。要区分「0 分」与「没有记录」，
     * 请用 [Completion.ratePct]（无记录为 `null`）。
     */
    fun ratePct(done: Int, partial: Int, total: Int): Int =
        if (total <= 0) 0 else ((done + partial * 0.5) / total * 100).toInt()

    /**
     * 按状态词表汇总（顺序无关）——纯状态口径，不含 PRN 语义（补剂走这条）。
     *
     * 无法识别的状态**计入 total 但不计完成**——取值偏向保守（未知既不算依从，
     * 也不会让比率虚高）。当前写入侧只产生三态，此项为防御性处理。
     */
    fun completionByStatus(statuses: List<String>): Completion {
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
        return Completion(done, partial, skipped, total)
    }

    /**
     * 用药记录的完成度：**按需（PRN）记录整体排除在分母之外**。
     *
     * 为什么在领域层排除、而不是要求调用方自己过滤：按需药一天可以打卡多次（`slot_key` 为 NULL，
     * 幂等键不生效），这些记录既没有计划剂量、也没有「漏服」这回事——放进分母只会抬高或稀释完成度
     * （用户 2026-09-27 拍板：PRN 单独统计）。排除依据是记录自带的 [MedicationLog.prnFlag]
     * （写入侧 `slotKey == null` 即为按需），所以调用方传全量记录即可，不必也不该自己过滤——
     * 过滤规则只有这一处，界面上的「共 N 条记录」与这里的分母永远同源。
     */
    fun completion(logs: List<MedicationLog>): Completion =
        completionByStatus(logs.filterNot { it.prnFlag }.map { it.status })

    /** 被排除在上述完成度之外的按需（PRN）打卡条数——单独报数，与完成度互不混算。 */
    fun prnCount(logs: List<MedicationLog>): Int = logs.count { it.prnFlag }
}
