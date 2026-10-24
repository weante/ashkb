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

    /**
     * 发热红旗。**两个来源合并为一条**：症状自评（勾发热 + 体温 ≥ 阈值）与体征录入（体温 ≥ 阈值）。
     *
     * 值沿用历史的 `symptom_abnormal` 不改名——库里存量警报就是这个值，改名等于让它们全部失联
     * （`insertAlertOnce` 的 `(type, ref_date)` 去重失效，同一天会冒出第二条发热警报）。
     */
    const val FEVER_ALERT = "symptom_abnormal"

    /**
     * 眼部症状红旗（`emr-001` 前葡萄膜炎）。
     *
     * v1.2.11（缺陷工单 A02）：**此前与发热共用 `symptom_abnormal`**，而 `insertAlertOnce` 按
     * `(type, ref_date)` 去重——同一天先写发热再写眼部时，眼部那条直接被去重吞掉：用户勾了
     * 「眼红眼痛」却只看到一条发热提醒。眼部症状对 AS 患者是**眼科急症级**信号（前葡萄膜炎
     * 24h 内就诊），被吞等于漏掉安全提示。故拆成独立身份。
     *
     * 存量库里的老警报仍记在 `symptom_abnormal` 下，不清洗也不迁移：它们确实是当天真实发生过的
     * 提醒，用户 ack 过就是留痕；未 ack 的仍会显示（文案由 UI 侧按类型映射，见 `SymptomCards`）。
     */
    const val EYE_RED_FLAG = "eye_red_flag"

    /** 神经红旗（麻木 / 无力 / 大小便控制变化）。 */
    const val NEURO_RED_FLAG = "neuro_red_flag"

    const val BASDAI_HIGH = "basdai_high"
    const val VACCINE_LIVE_PENDING = "vaccine_live_pending"
    const val FLARE_DAY7 = "flare_day7"

    /**
     * v1.2.10（审查 §6.6）：知识库条目到期复核的提醒。
     *
     * 补这个常量是审查里一条**顺带纠正**：报告说「`DerivedAlerts` 定义了 6 种告警」，实际这里
     * 只有 5 个常量，`review_due` 是 `HealthRepository` 里的裸字符串——所以 UI 侧的 `when` 分支
     * 也没法引用常量，只能再抄一遍字面量。两份字面量各自对得上时看不出问题，对不上时是
     * 「警报标题显示成通用的『提醒』」这种谁也不会去查的角落。
     */
    const val REVIEW_DUE = "review_due"

    /**
     * 症状 / 体征记录派生的全部警报类型（删除记录时按它们逐个判定）。
     *
     * v1.1.2（批次 18）起**不再整体照单删除**：发热警报（[FEVER_ALERT]）有第二个来源——
     * 体征录入（`vitals.temperature`），删症状记录时若体征仍发热，那条警报依然成立。
     *
     * v1.2.11（工单 A02）：眼部拆出为 [EYE_RED_FLAG]，不再与发热共用去重键。
     */
    val SYMPTOM_TYPES = listOf(FEVER_ALERT, EYE_RED_FLAG, NEURO_RED_FLAG)

    /**
     * 兼容别名。v1.2.10 及更早的代码与测试用 `SYMPTOM_ABNORMAL` 指代「发热」这一支；
     * 值相同，保留别名只是为了让存量调用点不必一次性全改（改错方向就会造成去重键漂移）。
     */
    const val SYMPTOM_ABNORMAL = FEVER_ALERT

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
