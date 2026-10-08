package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * v1.0.69 C8b：**晨僵时长驱动的起床热身序列**。
 *
 * 规划 M4 原文要求「晨僵时长驱动起床热身序列」，此前晨僵只作处方页的**判读依据**展示，
 * `ExerciseEngine.todayPlan()` 并不消费它——这里把它接进处方。
 *
 * **热身动作从哪来**：不新造医学建议，直接取当日处方里的 **L1 轻柔项**
 * （R27 矩阵已判定「今日可做」的那批，且已排除 pause / 拦截项），
 * 本对象只负责「按晨僵时长决定要不要提示、提示什么」。
 *
 * **阈值口径**（App 启发式，非指南硬指标）：
 *  - `< 15` 分钟：不作专门提示（避免噪音）
 *  - `15–29` 分钟：起床后先做一轮轻柔项再进入当日处方
 *  - `≥ 30` 分钟：明显延长，**常提示炎症活动**——与既有 `interpretFeedback`
 *    的「晨僵加重——可能提示炎症活动」同一口径，并提示持续如此需复诊时告知医生
 *
 * 纯函数、无 Android 依赖（同 [ExerciseEngine] / [LifestylePrescription]）。
 */
object MorningWarmup {

    /** 低于此值不作提示（分钟）。 */
    const val MIN_REPORTABLE = 15

    /** 达到此值视为「明显延长」（分钟）。 */
    const val PROLONGED = 30

    /** 序列最多列出的动作条数——起床热身不是整套训练。 */
    const val MAX_STEPS = 4

    data class Sequence(
        /** 醒目结论行，如「晨僵 35 分钟（明显延长）」——带分钟数插值，故用 [ResText]。 */
        val headline: ResText,
        /** 来自当日处方的 L1 动作名（可能为空——见 [build]）。 */
        val steps: List<String>,
        /** 解释与行动提示。i18n：文案见 `values/strings_domain.xml` 的 `dom_warm_note*`。 */
        @StringRes
        val noteRes: Int,
    )

    /**
     * @param minutes 昨日记录的晨僵时长（分钟）；null = 未记录 → 不提示
     * @param plan 当日处方中的红榜项（[ExerciseEngine.todayPlan] 的第一个返回值）
     * @return null = 无需展示热身块
     */
    fun build(minutes: Int?, plan: List<ExerciseEngine.ExerciseCard>): Sequence? {
        val m = minutes ?: return null
        if (m < MIN_REPORTABLE) return null
        val steps = plan.filter { it.grade == "L1" }.take(MAX_STEPS).map { it.entry.title }
        val prolonged = m >= PROLONGED
        val headline = ResText(
            res = if (prolonged) R.string.dom_warm_headline_prolonged else R.string.dom_warm_headline,
            args = listOf(m),
        )
        val noteRes = if (prolonged) R.string.dom_warm_note_prolonged else R.string.dom_warm_note
        return Sequence(headline, steps, noteRes)
    }
}