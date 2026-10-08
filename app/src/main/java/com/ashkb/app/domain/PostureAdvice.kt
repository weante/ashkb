package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * v1.0.70 C8c：姿势 / 睡姿建议（对应知识库条目 `edu-005`）。
 *
 * **内容出处**：本条目的正文与依据落在 `assets/kb_seed_edu.json` 的 `edu-005`
 * （`payload.key = posture_sleep_advice`，来源 NASS axSpA 体位教育）。
 * 本对象**不新造医学建议**——它只把 `edu-005` 的要点按「日常姿势 / 睡姿」两个
 * 展示位拆成短句，供运动处方页的提示块渲染；完整正文与来源由知识库条目承载。
 *
 * 与 [LifestylePrescription] 同一层：纯函数、无 Android 依赖、可单测。
 * 统一免责前缀由 UI 层 [com.ashkb.app.ui.components.DisclaimerNote] 负责。
 */
object PostureAdvice {

    /** 对应的知识库条目 id（见 kb_seed_edu.json）。 */
    const val KB_POSTURE = "edu-005"

    /** 日常姿势要点（逐条短句，UI 逐行渲染）。i18n：文案见 `values/strings_domain.xml` 的 `dom_posture_daily_*`。 */
    @StringRes
    val DAILY: List<Int> = listOf(
        R.string.dom_posture_daily_1,
        R.string.dom_posture_daily_2,
    )

    /** 睡姿 / 卧具要点。 */
    @StringRes
    val SLEEP: List<Int> = listOf(
        R.string.dom_posture_sleep_1,
        R.string.dom_posture_sleep_2,
        R.string.dom_posture_sleep_3,
    )

    /** 全部要点（日常 + 睡姿），顺序稳定，供概览展示。 */
    @StringRes
    val ALL: List<Int> = DAILY + SLEEP

    /** 是否应展示姿势 / 睡姿提示块——运动处方有效即展示（与分期无关，属通用体位建议）。 */
    fun shouldShow(plan: List<ExerciseEngine.ExerciseCard>): Boolean = plan.isNotEmpty()
}
