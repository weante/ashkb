package com.ashkb.app.domain

/**
 * v1.0.64 B13：生活方式画像 → 运动处方**个性化提示**。
 *
 * **边界（重要）**：本对象只产出「给用户的通用建议」，
 * **不参与** R27 矩阵的红黑榜过滤——过滤依据是疾病分期与脊柱活动度（医学判据），
 * 吸烟 / 久坐 / 睡眠本身不构成排除某项运动的依据。硬把它们塞进过滤器会是无依据的医学决策。
 * 因此个性化体现在**随处方一起展示的提示**，而不是改处方本身。
 *
 * 文案硬编码：domain 层纯 JVM 无 Context（与 [MissedDose] / [StopWarning] 同理）。
 * 统一免责前缀由 UI 层 [com.ashkb.app.ui.components.DisclaimerNote] 负责。
 */
object LifestylePrescription {

    /** 久坐提示阈值（小时）——低于此值不提示。 */
    const val SEDENTARY_THRESHOLD_HOURS = 6

    /** 睡眠不足阈值（小时）。 */
    const val SLEEP_SHORT_HOURS = 7

    /**
     * @param l 生活方式画像
     * @return 按优先级排序的个性化提示（可能为空 = 无可提示项）
     */
    fun advice(l: Lifestyle): List<String> = buildList {
        when (l.smoking) {
            Lifestyle.SMOKING_CURRENT ->
                add(
                    "吸烟是 axSpA 脊柱炎症与疾病进展的风险因素，建议戒烟（可请医生协助制定戒烟方案）。" +
                        "诚实口径：指南明确戒烟是基于吸烟的已知健康风险推荐，其对 axSpA 结局的专项获益尚无正式研究。",
                )
            Lifestyle.SMOKING_FORMER ->
                add("已戒烟是明确的加分项——继续保持，戒烟同时降低心血管风险（AS 患者心血管风险本身偏高）。")
        }

        l.sedentaryHours?.let { h ->
            if (h >= SEDENTARY_THRESHOLD_HOURS) {
                add("每日久坐约 $h 小时：建议每 30–45 分钟起身活动 2–3 分钟，比一次性长时间运动更有效对抗僵硬。")
            }
        }

        l.sleepHours?.let { h ->
            if (h < SLEEP_SHORT_HOURS) {
                add("平均睡眠约 $h 小时偏少：睡眠不足会放大疼痛与晨僵，今日强度不宜过量，以规律为先。")
            }
        }

        when (l.exerciseHabit) {
            Lifestyle.HABIT_NONE ->
                add("当前无运动习惯：从 L1 轻柔项起步，先建立「每周 3–5 次」的频率，再谈时长与强度。")
            Lifestyle.HABIT_REGULAR ->
                add("已有规律运动习惯：可按进展原则稳步加量——先时长（每 1–2 周 +5–10 分钟）→ 再频率 → 最后强度。")
        }
    }

    /** 画像里是否有任何已登记项（决定 UI 是否展示这一块）。 */
    fun hasContent(l: Lifestyle): Boolean =
        l.smoking != Lifestyle.SMOKING_UNKNOWN ||
            l.exerciseHabit != Lifestyle.HABIT_UNKNOWN ||
            l.sedentaryHours != null ||
            l.sleepHours != null
}
