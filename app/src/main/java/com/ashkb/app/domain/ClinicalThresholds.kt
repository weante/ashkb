package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * 临床阈值集中来源。
 *
 * 改版前这些字面量散落在 3 个 UI 文件里（`4.0` 被重写多遍、依从率 `80/50` 硬编码），
 * UI 只负责展示，阈值一律从这里取。
 *
 * i18n：三档标签返回 `@StringRes Int`，由调用方 `stringResource` / `context.getString` 落地。
 * 阈值常量本身与语言无关，一律不动。
 */
object ClinicalThresholds {

    /** BASDAI：≥ 4.0 高活动度；2.0–3.9 中度活动；< 2.0 低活动度。 */
    const val BASDAI_HIGH = 4.0f
    const val BASDAI_MODERATE = 2.0f

    /** 疼痛评分（0–10）：≥7 重度；4–6 中度；≤3 轻度。 */
    const val PAIN_SEVERE = 7
    const val PAIN_MODERATE = 4

    /**
     * 「记录内完成度」（v1.0.76（批次 3a）前的名字是「服药依从」）三档界值（%）：
     * ≥90 达标；70–89 待改善；<70 需干预。
     *
     * ⚠️ 只对**有记录**的区间有意义：零分母下没有任何可判定的东西，见 [completionLabel]。
     */
    const val ADHERENCE_GOOD = 90
    const val ADHERENCE_FAIR = 70

    /**
     * 化验结果距任一参考边界 ≤ 5% 量程视为"临界"。
     *
     * v1.2.10 审查裁决项 5（维护者 2026-10-24 裁决 A「实现临界显示」）：
     * 这条 KDoc 从 v1.0.75 起就写着，但常量**零引用**——"临界"态从未实现，等于一张空头支票。
     * 判据落在 [labNearBoundary]：**越界优先**，只有"在参考范围内"的值才可能被判成临界。
     */
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

    /** BASDAI 高活动度判定（0–10 总分，整数口径）。 */
    fun basdaiHigh(total: Int): Boolean = total >= BASDAI_HIGH

    /**
     * v1.0.77（批次 4）：**Double 口径重载**。
     *
     * BASDAI 自评总分在实体里是浮点（`(Q1+Q2+Q3+Q4+(Q5+Q6)/2)/5` 带 .5 的可能），
     * 而散落各处的硬编码比较（`total >= 4.0`）本来就是 Double 比较。
     * 收敛到本函数时若只有 Int 版本，调用点就得各自写 `toInt()`——那会**悄悄截断**
     * （4.5 → 4 仍在阈值上，但 3.9 → 3 就会把「接近阈值」判成「正常」），
     * 所以这里补一个 Double 重载，让调用点原样传值、不做有损转换。
     */
    fun basdaiHigh(total: Double): Boolean = total >= BASDAI_HIGH

    /**
     * 化验结果的"临界"判定（v1.2.10 审查裁决项 5，维护者 2026-10-24 裁决）。
     *
     * **只在参考范围内的值上判**：越界优先（已越界又落在容差内时仍算偏高/偏低），
     * 因为「偏高」本身已经比「临界」更值得医生注意，两者叠加只会稀释信号。
     *
     * 量程基准（维护者裁决项 1）：两侧都有范围时用区间宽度 `(refHigh - refLow) × 5%`；
     * 只有上限时用 `refHigh × 5%`。下限单独存在（`refLow` 有、`refHigh` 无）时同样按 `refLow × 5%`。
     *
     * 为什么不用固定绝对值：不同化验的量纲差几个数量级（ESR 是 mm/h、CRP 是 mg/L、
     * 血糖是 mmol/L），一个写死的 `±0.5` 要么把 ESR 判得满屏临界、要么对血糖毫无意义。
     *
     * @param value 本次数值；为 null（只有文字结果）时无从判定，返回 false
     * @param refLow 参考下限，缺失传 null
     * @param refHigh 参考上限，缺失传 null
     * @return 值在参考范围内、且距最近边界不超过 5% 量程时为 true
     */
    fun labNearBoundary(
        value: Double?,
        refLow: Double?,
        refHigh: Double?,
        /**
         * v1.2.11（缺陷工单 A06）：边界是否包含。默认 true＝旧行为，既有调用点不受影响。
         *
         * 必须与 [HealthRepository.saveLabResult] 的判读同源：若「越界」按严格边界判、
         * 「临界」按包含边界判，`<5` 的报告里 5 这个值会同时满足「越界」（5 >= 5）与
         * 「在范围内」（5 > 5 为假）——两套判据互相打架，界面上会出现自相矛盾的两枚胶囊。
         */
        refLowInclusive: Boolean = true,
        refHighInclusive: Boolean = true,
    ): Boolean {
        if (value == null) return false
        // 没有任何参考范围：本地判不了，交给 AI 标记兜底（与 saveLabResult 的口径一致）
        if (refLow == null && refHigh == null) return false
        // 越界优先：出了范围就不算"临界"。严格边界（`<` / `>`）下值本身即越界。
        val overHigh = refHigh != null && if (refHighInclusive) value > refHigh else value >= refHigh
        val underLow = refLow != null && if (refLowInclusive) value < refLow else value <= refLow
        if (overHigh || underLow) return false
        val span = labSpan(refLow, refHigh) ?: return false
        // 量程为 0 或负数（脏数据：下限≥上限）时不判临界，避免把整段都算成临界
        if (span <= 0.0) return false
        return labBoundaryDistance(value, refLow, refHigh) <= span * LAB_NEAR_BOUNDARY_RATIO
    }

    /**
     * 「临界」的量程基准（维护者裁决项 1）：两侧都有范围时取区间宽度，只有一侧时取那一侧的值。
     * 拆成独立函数是为了让 [labNearBoundary] 的圈复杂度留在 detekt 阈值内。
     */
    private fun labSpan(refLow: Double?, refHigh: Double?): Double? = when {
        refLow != null && refHigh != null -> refHigh - refLow
        refHigh != null -> refHigh
        refLow != null -> refLow
        else -> null
    }

    /** 值距最近参考边界的距离；两侧都在时取更近的那一侧。 */
    private fun labBoundaryDistance(value: Double, refLow: Double?, refHigh: Double?): Double =
        listOfNotNull(
            refHigh?.let { it - value },
            refLow?.let { value - it },
        ).min()

    /** 疼痛三档标签。 */
    fun painLabel(score: Int): Int = when {
        score >= PAIN_SEVERE -> R.string.dom_thr_pain_severe
        score >= PAIN_MODERATE -> R.string.dom_thr_pain_moderate
        else -> R.string.dom_thr_pain_mild
    }

    /** 依从率三档标签（补剂仍用这条：它有记录才显示，无记录由界面走「暂无」分支）。 */
    fun adherenceLabel(rate: Int): Int = when {
        rate >= ADHERENCE_GOOD -> R.string.dom_thr_adherence_good
        rate >= ADHERENCE_FAIR -> R.string.dom_thr_adherence_fair
        else -> R.string.dom_thr_adherence_poor
    }

    /**
     * v1.0.76（批次 3a）：「记录内完成度」的三档标签；**无记录（`ratePct == null`）返回 `null`**。
     *
     * 为什么用可空入参而不是让调用方自己判空：零分母时旧实现把 `0` 送进 [adherenceLabel]，
     * 于是「一条记录都没有」被标成「需干预」；把「无数据」编码进类型，调用方**无法**顺手给出
     * 达标 / 需关注这类判定，只能显式去显示「—（暂无记录）」。
     */
    fun completionLabel(ratePct: Int?): Int? = ratePct?.let { adherenceLabel(it) }
}
