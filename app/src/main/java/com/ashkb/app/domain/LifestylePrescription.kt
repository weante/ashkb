package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * v1.0.64 B13：生活方式画像 → 运动处方**个性化提示**。
 *
 * **边界（重要）**：本对象只产出「给用户的通用建议」，
 * **不参与** R27 矩阵的红黑榜过滤——过滤依据是疾病分期与脊柱活动度（医学判据），
 * 吸烟 / 久坐 / 睡眠本身不构成排除某项运动的依据。硬把它们塞进过滤器会是无依据的医学决策。
 * 因此个性化体现在**随处方一起展示的提示**，而不是改处方本身。
 *
 * 文案已资源化（i18n）：文案见 `values/strings_domain.xml` 的 `dom_life_*`，本对象只给资源 id。
 * 统一免责前缀由 UI 层 [com.ashkb.app.ui.components.DisclaimerNote] 负责。
 */
object LifestylePrescription {

    /** 久坐提示阈值（小时）——低于此值不提示。 */
    const val SEDENTARY_THRESHOLD_HOURS = 6

    /** 睡眠不足阈值（小时）。 */
    const val SLEEP_SHORT_HOURS = 7

    /**
     * @param l 生活方式画像
     * @return 按优先级排序的个性化提示（可能为空 = 无可提示项）；带插值者用 [ResText]
     */
    fun advice(l: Lifestyle): List<ResText> = buildList {
        when (l.smoking) {
            Lifestyle.SMOKING_CURRENT ->
                add(ResText(R.string.dom_life_smoking_current))
            Lifestyle.SMOKING_FORMER ->
                add(ResText(R.string.dom_life_smoking_former))
        }

        l.sedentaryHours?.let { h ->
            if (h >= SEDENTARY_THRESHOLD_HOURS) {
                add(ResText(R.string.dom_life_sedentary, listOf(h)))
            }
        }

        l.sleepHours?.let { h ->
            if (h < SLEEP_SHORT_HOURS) {
                add(ResText(R.string.dom_life_sleep, listOf(h)))
            }
        }

        when (l.exerciseHabit) {
            Lifestyle.HABIT_NONE ->
                add(ResText(R.string.dom_life_habit_none))
            Lifestyle.HABIT_REGULAR ->
                add(ResText(R.string.dom_life_habit_regular))
        }
    }

    /** 画像里是否有任何已登记项（决定 UI 是否展示这一块）。 */
    fun hasContent(l: Lifestyle): Boolean =
        l.smoking != Lifestyle.SMOKING_UNKNOWN ||
            l.exerciseHabit != Lifestyle.HABIT_UNKNOWN ||
            l.sedentaryHours != null ||
            l.sleepHours != null
}
