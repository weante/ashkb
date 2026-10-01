package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.PlannedSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
 *
 * v1.0.77（批次 3b）——**新增第二套口径，不动上面这套**：
 * 计划槽位快照落库后，「漏记」终于可以算进分母了（[DoseCompletion] / [doseCompletion]）。
 * 两套口径并存是刻意的：计划口径回答「开出去的剂量吃了多少」，记录口径回答「记下的那些完成得怎样」，
 * 覆盖范围也不同（快照只有 v1.0.77 起的历史）。界面必须把两者的分母差别写清楚，
 * 否则用户会以为两个百分比在打架。
 */
object AdherenceCalc {

    /** 状态词（存库值，与实体注释 `done / partial / skipped` 一致） */
    const val DONE = "done"
    const val PARTIAL = "partial"
    const val SKIPPED = "skipped"

    /**
     * v1.0.77（批次 3b）：**已结算**的三个状态——「这条剂量已经有了交代」。
     *
     * 唯一来源：今日页的「昨天还有 N 剂未记录」补记卡（`MedicationRepository.settledSlotRefs`）
     * 与漏服补发通知的纯函数（[com.ashkb.app.domain.MissedDoses]）都用它。
     * 此前两者各写各的（补记卡只认 done / skipped，漏服判定却要认 done / partial / skipped），
     * 同一剂药会出现「卡片说漏了、通知说没漏」——所以口径必须只有这一份。
     *
     * 注意 [PARTIAL] 与 [SKIPPED] 的**差别只在完成度分子**，不在这里：
     * 部分完成算「已有交代」，跳过也算「已有交代」（用户明确跳过，不该被反复催补记）。
     */
    val SETTLED_STATUSES: Set<String> = setOf(DONE, PARTIAL, SKIPPED)

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

    // =======================================================================
    // v1.0.77（批次 3b）：**计划剂量口径**的用药完成度
    // =======================================================================

    /**
     * 计划剂量口径的汇总：分母是**计划剂量数**（`planned`），不是已记录条数。
     *
     * 为什么需要第二套口径（与 [Completion] 并存而不是替换它）：
     * [Completion] 的分母是「已记录条数」，**完全漏记的剂量根本不进分母**，所以「一个月 30 剂
     * 只记了 3 剂」也算 100%——它只能读作「记录内完成度」，不能当依从率。计划口径则相反：
     * 有了 `planned_slots` 快照，分母是真正开出去的剂量数，漏记会明确落进 [missed]。
     *
     * 两套口径**同时保留**：计划快照自 v1.0.77（批次 3b）起才有，更早的历史没有计划剂量可比，
     * 只能用记录口径回看（界面因此必须把两者并列说明，不能只显示一个百分比让用户猜）。
     */
    data class DoseCompletion(
        val done: Int,
        val partial: Int,
        /** 计划里有、日志里没有——真漏服（含完全没记录） */
        val missed: Int,
        /** 计划剂量数（分母）。含被用户跳过的那些：它们确实被计划过 */
        val planned: Int,
    ) {
        /** 有计划快照才算得出计划口径的完成度；没有时界面必须显示「—（暂无计划快照）」。 */
        val hasPlan: Boolean get() = planned > 0

        /**
         * 用户**明确跳过**的剂数（已写原因的那种）。
         *
         * 由 [doseCompletion] 的三态判定反推（`planned - done - partial - missed`），
         * 不单独存字段：它是判定结果的余数，单独存一份迟早会与那三个数对不上。
         * 界面必须把它列出来——跳过既不进分子也不进 [missed]，不讲清楚用户会怀疑
         * 「计划 10 剂、完成 8 剂，还有一个去哪了」。
         */
        val skipped: Int get() = (planned - done - partial - missed).coerceAtLeast(0)

        /**
         * 完成度百分比（0–100 取整，部分完成按 0.5 计）——沿用 [ratePct] 的既有公式。
         *
         * **无计划快照时为 `null`，不是 0**：0% 会被读成「计划了却一剂没吃」，
         * 而真相是「这段时间根本没有计划快照可算」（快照是 v1.0.77 才有的）。
         */
        val ratePct: Int?
            get() = if (hasPlan) AdherenceCalc.ratePct(done, partial, planned) else null
    }

    /**
     * 按 `(date, medId, slotKey)` 把**计划槽位**与**用药日志**配对，算出计划口径的完成度。
     *
     * 配对键必须三者齐备：`date` 让「同一天的同一槽位」对上（跨日补记写的是槽位所属日，
     * 与计划行的 `date` 天然一致）；`medId` + `slotKey` 区分同一天的多剂。
     * **日期不匹配绝不误配**——日志记在次日（跨零点打卡）时那条剂量仍算漏服，
     * 不会去给它昨天/今天的邻居顶账。
     *
     * 状态判定（存库值，见 [DONE] / [PARTIAL] / [SKIPPED]）：
     *  · `done` → 完成；`partial` → 部分（分子按 0.5 计）；
     *  · `skipped` → **既不算完成、也不算漏服**。用户明确跳过（并写了原因）这件事本身
     *    已经是一次交代，把它归进 [DoseCompletion.missed] 会变成「跳过＝漏服」，进而在
     *    漏服补发里反复催他补一剂他明确不想吃的药；但它仍留在 [DoseCompletion.planned]
     *    里（那剂确实被计划过），所以「跳过」会拉低完成度——界面必须把跳过数一并列出，
     *    让这个差值是可见的（见 `dose_completion_plan_breakdown` 文案）。
     *  · 其它/未知状态 → 记为**漏服**（保守：既没被确认为完成，也不该从分母里消失）。
     */
    fun doseCompletion(planned: List<PlannedSlot>, logs: List<MedicationLog>): DoseCompletion {
        val bySlot = logs.associateBy { LogKey(it.date, it.medId, it.slotKey) }
        var done = 0
        var partial = 0
        var missed = 0
        for (slot in planned) {
            when (bySlot[LogKey(slot.date, slot.medId, slot.slotKey)]?.status) {
                DONE -> done++
                PARTIAL -> partial++
                SKIPPED -> Unit // 已交代，不计完成也不计漏服
                else -> missed++
            }
        }
        return DoseCompletion(done = done, partial = partial, missed = missed, planned = planned.size)
    }

    /**
     * 该计划槽位是否**已到点**（`date` + `slot_time` 早于 [now]）。
     *
     * 调用方（报表 / 药单）在统计前必须先滤掉没到点的槽位：否则**今天还没到点的剂量会被算成漏服**，
     * 每天早上完成度都会假性掉一截，随当天陆续打卡再爬回来——那是噪声，不是依从率。
     * 时刻解析不出来（脏数据）按「已到点」处理：宁可多算一条，也不静默藏掉一剂可能真漏的药。
     */
    fun isDue(slot: PlannedSlot, now: LocalDateTime): Boolean {
        val at = runCatching { LocalDateTime.of(LocalDate.parse(slot.date), LocalTime.parse(slot.slotTime)) }
            .getOrNull() ?: return true
        return !at.isAfter(now)
    }

    /** 配对键（`(date, medId, slotKey)`）——两个列表必须用同一种键才不会误配。 */
    private data class LogKey(val date: String, val medId: String?, val slotKey: String?)
}
