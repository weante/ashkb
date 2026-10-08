package com.ashkb.app.domain

import androidx.annotation.StringRes
import com.ashkb.app.R

/**
 * 枚举 key → 展示标签的统一映射（此前在 MeScreen / EmergencyScreen / ReportPdfWriter
 * / WellnessScreen 各写一份）。枚举 key 绝不出现在 UI（改版方案 §11）。
 *
 * i18n：标签统一返回 `@StringRes Int`，由调用方用 `stringResource(id)` /
 * `context.getString(id)` 落地——与 `data/entity/Entities.kt`、[SupplementLogStatus] 同范式。
 * 枚举 key 本身（`"positive"` / `"mediterranean"` / …）是**存储与匹配用的稳定标识**，
 * 不进资源文件、更不翻译。
 *
 * ⚠️ [foodAvoidCategory] / [foodAvoidSeverity] 返回 `Int?`：`null` = 这个 key 没有译文
 * （忌口条目的分类 / 等级允许出现本对象没枚举的值）。调用方**必须**在该情形回退到 key 原文，
 * 否则界面上这一栏会凭空消失。
 */
object Labels {

    fun hlaB27(key: String?): Int = when (key) {
        "positive" -> R.string.dom_lbl_hla_positive
        "negative" -> R.string.dom_lbl_hla_negative
        else -> R.string.dom_lbl_hla_unknown
    }

    fun diseaseStage(key: String?): Int = when (key) {
        "stable" -> R.string.dom_lbl_stage_stable
        "controlled" -> R.string.dom_lbl_stage_controlled
        "flare" -> R.string.dom_lbl_stage_flare
        else -> R.string.dom_lbl_stage_unknown
    }

    /**
     * v1.0.67 C1：骶髂关节影像分期的可选值（改良纽约标准 mNY，X 线 0–IV）。
     * 单独暴露 key 清单，UI 不必自己写一遍字面量。
     */
    val SACROILIITIS_KEYS = listOf("0", "1", "2", "3", "4")

    /** 骶髂关节影像分期 → 展示文本（含罗马数字，key 不出现在 UI）。 */
    fun sacroiliitisGrade(key: String?): Int = when (key) {
        "0" -> R.string.dom_lbl_sacro_0
        "1" -> R.string.dom_lbl_sacro_1
        "2" -> R.string.dom_lbl_sacro_2
        "3" -> R.string.dom_lbl_sacro_3
        "4" -> R.string.dom_lbl_sacro_4
        else -> R.string.dom_lbl_sacro_unknown
    }

    fun dietPattern(key: String?): Int = when (key) {
        "mediterranean" -> R.string.dom_lbl_diet_mediterranean
        "paleo" -> R.string.dom_lbl_diet_paleo
        "vegan" -> R.string.dom_lbl_diet_vegan
        "vegetarian" -> R.string.dom_lbl_diet_vegetarian
        "low_starch" -> R.string.dom_lbl_diet_low_starch
        else -> R.string.dom_lbl_diet_omnivore
    }

    fun seafoodFreq(key: String?): Int = when (key) {
        "never" -> R.string.dom_lbl_seafood_never
        "rare" -> R.string.dom_lbl_seafood_rare
        "weekly" -> R.string.dom_lbl_seafood_weekly
        "frequent" -> R.string.dom_lbl_seafood_frequent
        else -> R.string.dom_lbl_seafood_unset
    }

    fun dairyTolerance(key: String?): Int = when (key) {
        "yes" -> R.string.dom_lbl_dairy_yes
        "no" -> R.string.dom_lbl_dairy_no
        "lactose_free_only" -> R.string.dom_lbl_dairy_lactose_free
        else -> R.string.dom_lbl_dairy_unset
    }

    /** 忌口分类 key → 标签；未知 key 返回 `null`（调用方回退原文，见对象 KDoc）。 */
    fun foodAvoidCategory(key: String?): Int? = when (key) {
        "allergy" -> R.string.dom_lbl_avoid_allergy
        "intolerance" -> R.string.dom_lbl_avoid_intolerance
        "doctor_advice" -> R.string.dom_lbl_avoid_doctor_advice
        "personal_experience" -> R.string.dom_lbl_avoid_personal
        "drug_interaction" -> R.string.dom_lbl_avoid_drug_interaction
        else -> null
    }

    /** 忌口风险等级 key → 标签；未知 key 返回 `null`（调用方回退原文）。 */
    fun foodAvoidSeverity(key: String?): Int? = when (key) {
        "high" -> R.string.dom_lbl_severity_high
        "medium" -> R.string.dom_lbl_severity_medium
        "low" -> R.string.dom_lbl_severity_low
        else -> null
    }
}

/**
 * 一条**待本地化**的文本：资源 id + 格式化参数。
 *
 * 为什么需要它：有几处 domain 文案自带插值（「每日久坐约 6 小时…」「距计划时间约 20 分钟…」），
 * 只返回资源 id 会把参数丢掉。约定：
 *  - **无插值**的文案直接返回 `@StringRes Int`（[Labels] / [Disclaimer] / [PostureAdvice] / …）；
 *  - **有插值**、或需要成组传递时才包成 [ResText]（[MissedDose.Guidance.steps] /
 *    [MorningWarmup.Sequence.headline] / [EmergencyLockscreen.Content.lines] / [LifestylePrescription.advice]）。
 *
 * 参数只放**原始值**（Int / Long / String），不放嵌套的、本身还要翻译的文字——
 * 需要嵌一段那样的话时，为整句各写一条资源（成例见 `dom_missed_oral_soon_1_min` /
 * `dom_missed_oral_soon_1_hour`：中英占位符集合必须一致，语序可换）。
 *
 * 落地：Composable 用 `stringResource(res, *args.toTypedArray())`，
 * 非 Composable（PDF / 通知）用 `context.getString(res, *args.toTypedArray())`。
 */
data class ResText(@StringRes val res: Int, val args: List<Any> = emptyList())
