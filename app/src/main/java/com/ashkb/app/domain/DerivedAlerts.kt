package com.ashkb.app.domain

import java.time.LocalDate

/**
 * v1.0.80（批次 6）：**记录 → 警报**的派生关系（纯函数，可纯单测）。
 *
 * 为什么要有这个清单：警报是「记录的派生数据」，写记录时自动生成、按 `(type, ref_date)` 去重
 * （见 `HealthRepository.insertAlertOnce`）。此前**没有任何一条路径会删警报**——于是删掉一条
 * 记错的症状 / 疫苗 / 发作记录后，那条 high 级警报还挂在症状页顶部，用户点进去看到的是一个
 * 已经不存在的记录（幽灵）。删除记录时必须顺着这张表把派生警报一起清掉。
 *
 * 这里只放**类型常量**与**发作窗口判定**：真正决定「警报该不该留」的判据（例如活疫苗是否仍需
 * 提示）属于各自的安全规则（见 [VaccineSafety]），不在这里重复一份。
 */
object DerivedAlerts {

    /** 发热 / 眼部症状（`HealthRepository.evaluateSymptomAlerts` 的两个分支共用此类型）。 */
    const val SYMPTOM_ABNORMAL = "symptom_abnormal"

    /** 神经红旗（麻木 / 无力 / 大小便控制变化）。 */
    const val NEURO_RED_FLAG = "neuro_red_flag"

    const val BASDAI_HIGH = "basdai_high"
    const val VACCINE_LIVE_PENDING = "vaccine_live_pending"
    const val FLARE_DAY7 = "flare_day7"

    /**
     * 症状记录派生的全部警报类型。
     *
     * v1.1.2（批次 18）起**不再整体照单删除**：发热警报（[SYMPTOM_ABNORMAL]）有第二个来源——
     * 体征录入（`vitals.temperature`，见第四份审查报告 §六），删症状记录时若体征仍发热，
     * 那条警报依然成立。见 `HealthRepository.deleteSymptom`。
     */
    val SYMPTOM_TYPES = listOf(SYMPTOM_ABNORMAL, NEURO_RED_FLAG)

    /**
     * 某次发作的日期窗口是否覆盖 [refDate]。
     *
     * `endDate` 为空 = 仍在发作中，右端以 [today] 兜底（窗口必须闭到下一天才会覆盖「今天报的警」）。
     *
     * **解析失败一律返回 false**：宁可留下一条可能过期的警报（用户能看到、能自行确认），
     * 也不能把别次发作的警报误删——后者是静默丢失安全提示，不可接受。
     */
    fun flareWindowCovers(startDate: String, endDate: String?, refDate: String?, today: String): Boolean {
        val start = parse(startDate) ?: return false
        val ref = parse(refDate) ?: return false
        val end = when {
            endDate == null -> parse(today) ?: return false
            else -> parse(endDate) ?: return false
        }
        if (end.isBefore(start)) return false
        return !ref.isBefore(start) && !ref.isAfter(end)
    }

    /**
     * 已被删 / 已改窗口的那次发作**曾经**派生出的「第 7 天」警报，在剩下的发作里是否仍站得住。
     *
     * @param orphans 待判定的警报报警日（`alerts.ref_date`）
     * @param remaining 删除 / 编辑之后库里仍存在的发作窗口
     * @return 仍然站得住的报警日集合——**不在**其中的才允许清理
     */
    fun stillCoveredByOthers(
        orphans: List<String?>,
        remaining: List<FlareWindow>,
        today: String,
    ): Set<String> {
        val rest = remaining.mapNotNull { w ->
            val s = parse(w.startDate) ?: return@mapNotNull null
            val e = parse(w.endDate ?: today) ?: return@mapNotNull null
            if (e.isBefore(s)) null else s to e
        }
        return orphans.filterNotNull()
            .filterTo(mutableSetOf()) { ref ->
                val d = parse(ref) ?: return@filterTo false
                rest.any { (s, e) -> !d.isBefore(s) && !d.isAfter(e) }
            }
    }

    /** 一次发作的日期窗口（[DerivedAlerts.stillCoveredByOthers] 的输入）。 */
    data class FlareWindow(val startDate: String, val endDate: String?)

    private fun parse(date: String?): LocalDate? =
        date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
