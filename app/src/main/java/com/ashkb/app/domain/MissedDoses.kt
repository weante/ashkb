package com.ashkb.app.domain

import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.PlannedSlot

/**
 * v1.0.77（批次 3b）：**漏服补发**的判定核心（纯函数，无 Android 依赖，可单测）。
 *
 * 场景（2026-10-01 真机实测暴露的缺口）：昨天那一剂既没点「已服用」也没点「跳过」时，
 * 应用里只有今日页那张卡会提一句；用户当天若没进今日页（或干脆没打开应用），
 * 这一剂就**彻底沉底**——没有任何一条通知会告诉他「昨天有几剂没记」。
 *
 * 与今日页补记卡（`ui/today` 的「昨天还有 N 剂未记录」，v1.0.74）**口径完全一致**，
 * 靠的是共用同一份「已结算」定义（[AdherenceCalc.SETTLED_STATUSES]）：
 * done / partial / skipped 都算已有交代，其余（无行、未知状态）才算未记录。
 * 若这里另起一套口径，用户就会看到「卡片说漏了 1 剂、通知说漏了 2 剂」这种自相矛盾的提示。
 *
 * 数据来源是 `planned_slots` 快照而不是现算的药档：昨天该吃几剂是**昨天**的事实，
 * 不该因为今天停药 / 改方案而改变（快照语义见 `data/entity/PlannedSlot`）。
 */
object MissedDoses {

    /**
     * 汇总结果。
     *
     * [medNames] 去重且保持「计划时刻」顺序（与 [PendingDoses] 的排序同源），
     * 通知正文直接用它拼药名——同一支药一天漏两剂时只报一次名字，条数仍如实统计在 [count]。
     */
    data class Summary(val count: Int, val medNames: List<String>) {
        /** 0 条就不提醒：没有未记录的剂量时发通知只会训练用户忽略通知。 */
        val shouldRemind: Boolean get() = count > 0

        companion object {
            val EMPTY = Summary(0, emptyList())
        }
    }

    /**
     * 某日「计划里有、日志里没有交代」的剂量条数与药名。
     *
     * 配对键 `(date, medId, slotKey)` 与 [AdherenceCalc.doseCompletion] 完全一致：
     *  · 日志记在别的日期（跨零点打卡写错日）时**不误配**——那剂仍算未记录；
     *  · 按需（PRN）日志的 `slotKey` 为 NULL，而计划槽位的 slotKey 恒非空，两者不会互相顶账。
     *
     * @param planned 该日的计划槽位（来自 `planned_slots`）
     * @param logs 该日的用药日志（来自 `medication_logs`）
     */
    fun unsettled(planned: List<PlannedSlot>, logs: List<MedicationLog>): Summary {
        if (planned.isEmpty()) return Summary.EMPTY
        val settled = logs.mapNotNull { log ->
            if (log.status in AdherenceCalc.SETTLED_STATUSES) LogKey(log.date, log.medId, log.slotKey) else null
        }.toSet()

        val missed = planned.filter { LogKey(it.date, it.medId, it.slotKey) !in settled }
        return Summary(
            count = missed.size,
            // 同一支药漏两剂只报一次名字（通知正文里名字是给人看的，条数已经在标题里）
            medNames = missed.sortedWith(compareBy({ it.slotTime }, { it.medName }))
                .map { it.medName }
                .distinct(),
        )
    }

    /** 配对键（`(date, medId, slotKey)`）——与 [AdherenceCalc.doseCompletion] 同一种键。 */
    private data class LogKey(val date: String, val medId: String?, val slotKey: String?)
}
