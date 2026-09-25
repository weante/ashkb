package com.ashkb.app.domain

/**
 * 临床阈值集中来源。
 *
 * 改版前这些字面量散落在 3 个 UI 文件里（`4.0` 被重写多遍、依从率 `80/50` 硬编码），
 * UI 只负责展示，阈值一律从这里取。
 */
object ClinicalThresholds {

    /** BASDAI：≥ 4.0 高活动度；2.0–3.9 中度活动；< 2.0 低活动度。 */
    const val BASDAI_HIGH = 4.0f
    const val BASDAI_MODERATE = 2.0f

    /** 疼痛评分（0–10）：≥7 重度；4–6 中度；≤3 轻度。 */
    const val PAIN_SEVERE = 7
    const val PAIN_MODERATE = 4

    /** 30 天依从率（%）：≥90 达标；70–89 待改善；<70 需干预。 */
    const val ADHERENCE_GOOD = 90
    const val ADHERENCE_FAIR = 70

    /** 化验结果距任一参考边界 ≤ 5% 量程视为"临界"。 */
    const val LAB_NEAR_BOUNDARY_RATIO = 0.05f

    /**
     * 炎症指标参考上限**兜底值**（趋势页方案 C）。
     *
     * 仅当化验单没带 `refHigh` 时才用（见 `LabTrend.threshold`）——因为界值随实验室、
     * 性别、甚至检测方法而变（超敏 CRP 的界值远严于常规 CRP），
     * 用死值去判「超标」会把正常结果标成异常。
     */
    const val ESR_HIGH = 20f  // mm/h（男性 0–15、女性 0–20 均以此为常见随访界）
    const val CRP_HIGH = 8f   // mg/L（部分实验室写 <5）
    const val HSCRP_HIGH = 3f // mg/L（高敏检测，界值远严于常规 CRP；不同实验室差异大，仅作兜底）

    /** 复诊：逾期 > 7 天为危险，0–7 天为提醒。 */
    const val FOLLOWUP_OVERDUE_DAYS = 7L

    /** 体温（℃）：≥38.5 触发警报（同 HealthRepository.FEVER_THRESHOLD）；≥37.3 低热。 */
    const val FEVER_ALERT = 38.5
    const val FEVER_LOW = 37.3

    /** 血压：≥140/90 偏高；收缩压 <90 偏低。 */
    const val BP_HIGH_SYS = 140
    const val BP_HIGH_DIA = 90
    const val BP_LOW_SYS = 90

    /** 心率正常范围（次/分）。 */
    const val HR_LOW = 60
    const val HR_HIGH = 100

    /** BASDAI 高活动度判定（0–10 总分）。 */
    fun basdaiHigh(total: Int): Boolean = total >= BASDAI_HIGH

    /** 疼痛三档标签。 */
    fun painLabel(score: Int): String = when {
        score >= PAIN_SEVERE -> "重度"
        score >= PAIN_MODERATE -> "中度"
        else -> "轻度"
    }

    /** 依从率三档标签。 */
    fun adherenceLabel(rate: Int): String = when {
        rate >= ADHERENCE_GOOD -> "达标"
        rate >= ADHERENCE_FAIR -> "待改善"
        else -> "需干预"
    }
}
